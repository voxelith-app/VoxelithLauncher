package net.kdt.pojavlaunch.servers;

import com.google.gson.reflect.TypeToken;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Servers from every instance's servers.dat plus the ones played or added through the launcher.
 * Launcher-side data (last played, manual entries) lives in voxelith_servers.json.
 */
public final class ServerStore {
    private ServerStore() {}

    private static File storeFile() {
        return new File(Tools.DIR_GAME_HOME, "voxelith_servers.json");
    }

    public static synchronized List<SavedServer> loadLauncherServers() {
        File file = storeFile();
        if(!file.isFile()) return new ArrayList<>();
        try {
            List<SavedServer> servers = Tools.GLOBAL_GSON.fromJson(Tools.read(file), new TypeToken<List<SavedServer>>(){}.getType());
            return servers == null ? new ArrayList<>() : servers;
        }catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static synchronized void saveLauncherServers(List<SavedServer> servers) {
        try {
            Tools.write(storeFile().getAbsolutePath(), Tools.GLOBAL_GSON.toJson(servers));
        }catch (Exception ignored) {}
    }

    /** Recently played first, then the rest by name. */
    public static List<SavedServer> loadAll() {
        Map<String, SavedServer> merged = new LinkedHashMap<>();
        for(SavedServer server : loadLauncherServers()) merged.put(server.key(), server);
        try {
            for(Instance instance : Instances.loadAllInstances()) {
                File serversDat = new File(instance.getGameDirectory(), "servers.dat");
                if(!serversDat.isFile()) continue;
                for(SavedServer server : readServersDat(serversDat)) {
                    SavedServer existing = merged.get(server.key());
                    if(existing == null) {
                        server.instanceName = instance.name;
                        merged.put(server.key(), server);
                    }else if(existing.name == null || existing.name.isEmpty()) {
                        existing.name = server.name;
                    }
                }
            }
        }catch (Exception ignored) {}
        List<SavedServer> result = new ArrayList<>(merged.values());
        Collections.sort(result, (a, b) -> {
            if(a.lastPlayed != b.lastPlayed) return Long.compare(b.lastPlayed, a.lastPlayed);
            return String.valueOf(a.name).compareToIgnoreCase(String.valueOf(b.name));
        });
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<SavedServer> readServersDat(File file) {
        List<SavedServer> servers = new ArrayList<>();
        try(FileInputStream inputStream = new FileInputStream(file)) {
            Map<String, Object> root = NbtReader.readRoot(inputStream);
            Object list = root.get("servers");
            if(!(list instanceof List)) return servers;
            for(Object entry : (List<Object>) list) {
                if(!(entry instanceof Map)) continue;
                Map<String, Object> compound = (Map<String, Object>) entry;
                Object ip = compound.get("ip");
                if(!(ip instanceof String) || ((String) ip).trim().isEmpty()) continue;
                Object name = compound.get("name");
                servers.add(new SavedServer(name instanceof String ? (String) name : (String) ip, (String) ip));
            }
        }catch (Exception ignored) {}
        return servers;
    }

    public static synchronized void markPlayed(SavedServer server, String instanceName) {
        List<SavedServer> servers = loadLauncherServers();
        SavedServer stored = null;
        for(SavedServer candidate : servers) if(candidate.key().equals(server.key())) stored = candidate;
        if(stored == null) {
            stored = new SavedServer(server.name, server.address);
            servers.add(stored);
        }
        stored.lastPlayed = System.currentTimeMillis();
        stored.instanceName = instanceName;
        saveLauncherServers(servers);
    }

    public static synchronized void add(String name, String address) {
        List<SavedServer> servers = loadLauncherServers();
        for(SavedServer candidate : servers) if(candidate.key().equals(address.trim().toLowerCase())) return;
        servers.add(new SavedServer(name, address.trim()));
        saveLauncherServers(servers);
    }

    public static synchronized void remove(SavedServer server) {
        List<SavedServer> servers = loadLauncherServers();
        Iterator<SavedServer> iterator = servers.iterator();
        while(iterator.hasNext()) if(iterator.next().key().equals(server.key())) iterator.remove();
        saveLauncherServers(servers);
    }
}
