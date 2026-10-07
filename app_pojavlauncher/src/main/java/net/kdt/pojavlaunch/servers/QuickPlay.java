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
 * Hands a server address from the launcher process to the game process, which runs separately,
 * through a one-shot file in the game home.
 */
public final class QuickPlay {
    private QuickPlay() {}

    private static File file() {
        return new File(Tools.DIR_GAME_HOME, "voxelith_quickplay.txt");
    }

    public static void request(String address) {
        try {
            Tools.write(file().getAbsolutePath(), address.trim());
        }catch (Exception e) {
            Log.w("QuickPlay", "Failed to save server address", e);
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

    /** --quickPlayMultiplayer exists since 23w14a (5 April 2023). */
    private static boolean supportsQuickPlay(JVersionList.Version versionInfo) {
        try {
            Date date = DateUtils.getOriginalReleaseDate(versionInfo);
            return date != null && !DateUtils.dateBefore(date, 2023, 3, 5);
        }catch (Exception e) {
            return false;
        }
    }
}
