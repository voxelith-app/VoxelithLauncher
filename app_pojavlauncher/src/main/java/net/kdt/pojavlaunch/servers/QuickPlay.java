package net.kdt.pojavlaunch.servers;

import android.util.Log;

import net.kdt.pojavlaunch.JVersionList;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.DateUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Hands a server address or a world folder from the launcher process to the game process,
 * which runs separately, through a one-shot file in the game home.
 */
public final class QuickPlay {
    private QuickPlay() {}

    private static File file() {
        return new File(Tools.DIR_GAME_HOME, "voxelith_quickplay.txt");
    }

    private static final String WORLD_PREFIX = "world:";

    public static void request(String address) {
        save(address.trim());
    }

    /** Opens a singleplayer world by its folder name in saves/. Needs 1.20 (23w14a) or newer. */
    public static void requestWorld(String folderName) {
        save(WORLD_PREFIX + folderName);
    }

    public static void cancel() {
        boolean ignored = file().delete();
    }

    private static void save(String request) {
        try {
            Tools.write(file().getAbsolutePath(), request);
        }catch (Exception e) {
            Log.w("QuickPlay", "Failed to save quick play request", e);
        }
    }

    /** Arguments that make the game join the requested server, or nothing. The request is used once. */
    public static List<String> consumeArgs(JVersionList.Version versionInfo) {
        List<String> args = new ArrayList<>();
        File file = file();
        if(!file.isFile()) return args;
        String address;
        // A request left behind by a launch that never happened should not hijack a later one
        boolean fresh = System.currentTimeMillis() - file.lastModified() < 10 * 60 * 1000;
        try {
            address = fresh ? Tools.read(file).trim() : "";
        }catch (Exception e) {
            return args;
        }finally {
            boolean ignored = file.delete();
        }
        if(address.isEmpty()) return args;
        if(address.startsWith(WORLD_PREFIX)) {
            if(supportsQuickPlay(versionInfo)) {
                args.add("--quickPlaySingleplayer");
                args.add(address.substring(WORLD_PREFIX.length()));
            }
            return args;
        }
        if(supportsQuickPlay(versionInfo)) {
            args.add("--quickPlayMultiplayer");
            args.add(address);
            return args;
        }
        String host = address;
        String port = "25565";
        int colon = address.lastIndexOf(':');
        if(colon > 0) {
            host = address.substring(0, colon);
            port = address.substring(colon + 1);
        }
        args.add("--server");
        args.add(host);
        args.add("--port");
        args.add(port);
        return args;
    }

    /** --quickPlayMultiplayer and --quickPlaySingleplayer exist since 23w14a (5 April 2023). */
    public static boolean supportsQuickPlay(JVersionList.Version versionInfo) {
        try {
            Date date = DateUtils.getOriginalReleaseDate(versionInfo);
            return date != null && !DateUtils.dateBefore(date, 2023, 3, 5);
        }catch (Exception e) {
            return false;
        }
    }
}
