package net.kdt.pojavlaunch.skins;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.AuthType;
import net.kdt.pojavlaunch.authenticator.accounts.Account;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Reads and changes the skin of the current account.
 * Microsoft accounts use the official profile API, Ely.by skins are changed on the site and
 * local accounts keep the skin on the device, shown in game through CustomSkinLoader.
 */
public class SkinService {
    private static final String PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";
    private static final String ELY_TEXTURES_URL = "https://skinsystem.ely.by/textures/%s";
    public static final String CUSTOM_SKIN_LOADER = "customskinloader";

    public static class Skin {
        @Nullable public final Bitmap bitmap;
        public final boolean slim;

        public Skin(@Nullable Bitmap bitmap, boolean slim) {
            this.bitmap = bitmap;
            this.slim = slim;
        }
    }

    public static Skin load(Account account) throws IOException {
        if(account.authType == AuthType.MICROSOFT && !account.isLocal()) return loadMicrosoft(account);
        if(account.authType == AuthType.ELY_BY) return loadElyBy(account);
        return loadLocal(account);
    }

    private static Skin loadMicrosoft(Account account) throws IOException {
        JsonObject profile = JsonParser.parseString(request("GET", PROFILE_URL, account.accessToken)).getAsJsonObject();
        JsonArray skins = profile.getAsJsonArray("skins");
        if(skins == null) return new Skin(null, false);
        for(JsonElement element : skins) {
            JsonObject skin = element.getAsJsonObject();
            if(!"ACTIVE".equals(string(skin, "state"))) continue;
            String url = string(skin, "url");
            boolean slim = "SLIM".equalsIgnoreCase(string(skin, "variant"));
            return new Skin(url == null ? null : download(url), slim);
        }
        return new Skin(null, false);
    }

    private static Skin loadElyBy(Account account) throws IOException {
        String body = request("GET", String.format(ELY_TEXTURES_URL, account.username), null);
        JsonElement root = JsonParser.parseString(body);
        if(!root.isJsonObject() || !root.getAsJsonObject().has("SKIN")) return new Skin(null, false);
        JsonObject skin = root.getAsJsonObject().getAsJsonObject("SKIN");
        boolean slim = false;
        if(skin.has("metadata") && skin.get("metadata").isJsonObject()) {
            slim = "slim".equals(string(skin.getAsJsonObject("metadata"), "model"));
        }
        String url = string(skin, "url");
        return new Skin(url == null ? null : download(url), slim);
    }

    private static Skin loadLocal(Account account) {
        File file = localSkinFile(account.username);
        if(!file.isFile()) return new Skin(null, false);
        return new Skin(BitmapFactory.decodeFile(file.getAbsolutePath()), localSlimMarker(account.username).exists());
    }

    /** Throws if the bytes are not a 64x64 or 64x32 PNG skin (or an HD multiple). */
    public static Bitmap validate(byte[] png) throws IOException {
        Bitmap bitmap = BitmapFactory.decodeByteArray(png, 0, png.length);
        if(bitmap == null) throw new IOException("not an image");
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if(width < 64 || width % 64 != 0 || (height != width && height * 2 != width)) {
            bitmap.recycle();
            throw new InvalidSkinException(width, height);
        }
        return bitmap;
    }

    public static void uploadMicrosoft(Account account, byte[] png, boolean slim) throws IOException {
        String boundary = "voxelith" + System.currentTimeMillis();
        HttpURLConnection connection = open("POST", PROFILE_URL + "/skins", account.accessToken);
        try {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            try(OutputStream out = connection.getOutputStream()) {
                String head = "--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"variant\"\r\n\r\n"
                        + (slim ? "slim" : "classic") + "\r\n"
                        + "--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\n"
                        + "Content-Type: image/png\r\n\r\n";
                out.write(head.getBytes(StandardCharsets.UTF_8));
                out.write(png);
                out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            }
            check(connection);
        }finally {
            connection.disconnect();
        }
    }

    public static void resetMicrosoft(Account account) throws IOException {
        request("DELETE", PROFILE_URL + "/skins/active", account.accessToken);
    }

    public static void saveLocal(Account account, byte[] png, boolean slim) throws IOException {
        File file = localSkinFile(account.username);
        FileUtils.writeByteArrayToFile(file, png);
        File marker = localSlimMarker(account.username);
        if(slim) FileUtils.touch(marker);
        else FileUtils.deleteQuietly(marker);
        try {
            for(Instance instance : Instances.loadAllInstances()) syncLocal(account, instance.getGameDirectory());
        }catch (IOException e) {
            Log.w("SkinService", "Could not copy the skin to every instance", e);
        }
    }

    public static void removeLocal(Account account) {
        FileUtils.deleteQuietly(localSkinFile(account.username));
        FileUtils.deleteQuietly(localSlimMarker(account.username));
        try {
            for(Instance instance : Instances.loadAllInstances()) {
                FileUtils.deleteQuietly(customSkinLoaderFile(instance.getGameDirectory(), account.username));
            }
        }catch (IOException ignored) {}
    }

    /** Copies the local skin into the folder CustomSkinLoader reads. Safe to call on every launch. */
    public static void syncLocal(Account account, File gameDirectory) {
        if(account == null || account.authType != AuthType.LOCAL) return;
        File source = localSkinFile(account.username);
        if(!source.isFile()) return;
        File target = customSkinLoaderFile(gameDirectory, account.username);
        try {
            if(target.isFile() && target.length() == source.length() && target.lastModified() >= source.lastModified()) return;
            FileUtils.copyFile(source, target);
        }catch (IOException e) {
            Log.w("SkinService", "Could not sync the local skin", e);
        }
    }

    public static boolean hasCustomSkinLoader(File gameDirectory) {
        File[] mods = new File(gameDirectory, "mods").listFiles();
        if(mods == null) return false;
        for(File mod : mods) {
            String name = mod.getName().toLowerCase(Locale.ROOT);
            if(name.contains("customskinloader") && name.endsWith(".jar")) return true;
        }
        return false;
    }

    private static File localSkinFile(String username) {
        return new File(Tools.DIR_GAME_HOME, "voxelith_skins/" + username + ".png");
    }

    private static File localSlimMarker(String username) {
        return new File(Tools.DIR_GAME_HOME, "voxelith_skins/" + username + ".slim");
    }

    private static File customSkinLoaderFile(File gameDirectory, String username) {
        return new File(gameDirectory, "CustomSkinLoader/LocalSkin/skins/" + username + ".png");
    }

    @Nullable
    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    @Nullable
    private static Bitmap download(String url) throws IOException {
        // Mojang still hands out http texture links
        if(url.startsWith("http://textures.minecraft.net")) url = "https" + url.substring(4);
        HttpURLConnection connection = open("GET", url, null);
        try {
            check(connection);
            byte[] bytes;
            try(InputStream in = connection.getInputStream()) {
                bytes = IOUtils.toByteArray(in);
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        }finally {
            connection.disconnect();
        }
    }

    private static String request(String method, String url, @Nullable String token) throws IOException {
        HttpURLConnection connection = open(method, url, token);
        try {
            check(connection);
            try(InputStream in = connection.getInputStream()) {
                return Tools.read(in);
            }
        }finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(String method, String url, @Nullable String token) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("User-Agent", "voxelith-app/VoxelithLauncher");
        if(token != null) connection.setRequestProperty("Authorization", "Bearer " + token);
        return connection;
    }

    private static void check(HttpURLConnection connection) throws IOException {
        int code = connection.getResponseCode();
        if(code == 401) throw new SessionExpiredException();
        if(code < 200 || code >= 300) throw new IOException("HTTP " + code + " (" + connection.getURL().getHost() + ")");
    }

    public static class InvalidSkinException extends IOException {
        public final int width;
        public final int height;

        public InvalidSkinException(int width, int height) {
            super("invalid skin size " + width + "x" + height);
            this.width = width;
            this.height = height;
        }
    }

    public static class SessionExpiredException extends IOException {
        public SessionExpiredException() {
            super("session expired");
        }
    }
}
