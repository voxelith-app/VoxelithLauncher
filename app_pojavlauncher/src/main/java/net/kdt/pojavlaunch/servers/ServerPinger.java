package net.kdt.pojavlaunch.servers;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.kdt.pojavlaunch.Tools;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Server List Ping (1.7+): MOTD, players, version, icon and latency. */
public final class ServerPinger {
    private static final int TIMEOUT_MS = 5000;

    public static class Status {
        public String motd = "";
        public int online;
        public int max;
        public String version = "";
        public long latency;
        @Nullable public Bitmap icon;
    }

    private ServerPinger() {}

    public static Status ping(String address) throws IOException {
        String[] target = resolve(address);
        String host = target[0];
        int port = Integer.parseInt(target[1]);
        try(Socket socket = new Socket()) {
            long start = System.currentTimeMillis();
            socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            ByteArrayOutputStream handshake = new ByteArrayOutputStream();
            DataOutputStream handshakeData = new DataOutputStream(handshake);
            writeVarInt(handshakeData, 0x00);
            writeVarInt(handshakeData, -1);
            writeString(handshakeData, host);
            handshakeData.writeShort(port);
            writeVarInt(handshakeData, 1);
            writePacket(out, handshake.toByteArray());
            writePacket(out, new byte[]{0x00});

            readVarInt(in);
            int packetId = readVarInt(in);
            if(packetId != 0x00) throw new IOException("Unexpected packet " + packetId);
            int length = readVarInt(in);
            if(length < 0 || length > 1024 * 1024) throw new IOException("Bad status length");
            byte[] json = new byte[length];
            in.readFully(json);
            long latency = System.currentTimeMillis() - start;
            Status status = parse(new String(json, StandardCharsets.UTF_8));
            status.latency = latency;
            return status;
        }
    }

    /** Uses the _minecraft._tcp SRV record when the address has no port, like the game does. */
    private static String[] resolve(String address) {
        String trimmed = address.trim();
        int colon = trimmed.lastIndexOf(':');
        if(colon > 0 && trimmed.indexOf(':') == colon) {
            return new String[]{trimmed.substring(0, colon), trimmed.substring(colon + 1)};
        }
        try {
            String url = "https://dns.google/resolve?type=SRV&name=" + URLEncoder.encode("_minecraft._tcp." + trimmed, "UTF-8");
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            try(InputStream inputStream = connection.getInputStream()) {
                JsonObject response = JsonParser.parseString(Tools.read(inputStream)).getAsJsonObject();
                JsonArray answers = response.getAsJsonArray("Answer");
                if(answers != null && answers.size() > 0) {
                    String[] parts = answers.get(0).getAsJsonObject().get("data").getAsString().trim().split("\\s+");
                    if(parts.length == 4) {
                        String srvHost = parts[3].endsWith(".") ? parts[3].substring(0, parts[3].length() - 1) : parts[3];
                        return new String[]{srvHost, parts[2]};
                    }
                }
            }finally {
                connection.disconnect();
            }
        }catch (Exception ignored) {
            // No SRV record: use the default port
        }
        return new String[]{trimmed, "25565"};
    }

    private static Status parse(String json) {
        Status status = new Status();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if(root.has("description")) status.motd = stripFormatting(flattenText(root.get("description"))).trim();
        if(root.has("players")) {
            JsonObject players = root.getAsJsonObject("players");
            status.online = players.has("online") ? players.get("online").getAsInt() : 0;
            status.max = players.has("max") ? players.get("max").getAsInt() : 0;
        }
        if(root.has("version")) status.version = stripFormatting(root.getAsJsonObject("version").get("name").getAsString());
        if(root.has("favicon")) {
            String favicon = root.get("favicon").getAsString();
            int comma = favicon.indexOf(',');
            if(comma > 0) {
                byte[] bytes = Base64.decode(favicon.substring(comma + 1), Base64.DEFAULT);
                status.icon = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            }
        }
        return status;
    }

    private static String flattenText(JsonElement element) {
        if(element == null || element.isJsonNull()) return "";
        if(element.isJsonPrimitive()) return element.getAsString();
        if(element.isJsonArray()) {
            StringBuilder builder = new StringBuilder();
            for(JsonElement child : element.getAsJsonArray()) builder.append(flattenText(child));
            return builder.toString();
        }
        JsonObject object = element.getAsJsonObject();
        StringBuilder builder = new StringBuilder();
        if(object.has("text")) builder.append(object.get("text").getAsString());
        if(object.has("extra")) builder.append(flattenText(object.get("extra")));
        return builder.toString();
    }

    public static String stripFormatting(String text) {
        return text.replaceAll("§.", "");
    }

    private static void writePacket(DataOutputStream out, byte[] data) throws IOException {
        writeVarInt(out, data.length);
        out.write(data);
        out.flush();
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int position = 0;
        while(true) {
            byte current = in.readByte();
            value |= (current & 0x7F) << position;
            if((current & 0x80) == 0) return value;
            position += 7;
            if(position >= 32) throw new IOException("VarInt too big");
        }
    }
}
