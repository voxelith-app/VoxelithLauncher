package net.kdt.pojavlaunch.perf;

import android.util.Log;

import net.kdt.pojavlaunch.Tools;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Battery saver for the game: caps FPS at 30 and lowers the heaviest video options. The player's
 * own values are saved next to options.txt and put back when the saver is turned off.
 */
public final class BatterySaver {
    private static final String BACKUP_NAME = "voxelith_battery_backup.txt";
    private static final String MISSING = "\u0000";
    private static final String[][] SAVER_OPTIONS = {
            {"maxFps", "30"},
            {"enableVsync", "false"},
            {"renderDistance", "5"},
            {"simulationDistance", "5"},
            {"particles", "2"},
            {"entityShadows", "false"},
            {"graphicsMode", "0"},
            {"fancyGraphics", "false"},
            {"ao", "false"},
            {"inactivityFpsLimit", "\"afk\""},
    };

    private BatterySaver() {}

    /** Called before every launch with the current setting. */
    public static void sync(File gameDirectory, boolean enabled) {
        File options = new File(gameDirectory, "options.txt");
        File backup = new File(gameDirectory, BACKUP_NAME);
        try {
            if(enabled) apply(options, backup);
            else if(backup.isFile()) restore(options, backup);
        }catch (IOException e) {
            Log.w("BatterySaver", "Could not update options.txt", e);
        }
    }

    private static void apply(File optionsFile, File backupFile) throws IOException {
        Map<String, String> options = read(optionsFile);
        if(!backupFile.isFile()) {
            Map<String, String> backup = new LinkedHashMap<>();
            for(String[] option : SAVER_OPTIONS) {
                String value = options.get(option[0]);
                backup.put(option[0], value == null ? MISSING : value);
            }
            write(backupFile, backup);
        }
        for(String[] option : SAVER_OPTIONS) options.put(option[0], option[1]);
        write(optionsFile, options);
    }

    private static void restore(File optionsFile, File backupFile) throws IOException {
        Map<String, String> options = read(optionsFile);
        for(Map.Entry<String, String> entry : read(backupFile).entrySet()) {
            if(MISSING.equals(entry.getValue())) options.remove(entry.getKey());
            else options.put(entry.getKey(), entry.getValue());
        }
        write(optionsFile, options);
        boolean ignored = backupFile.delete();
    }

    private static Map<String, String> read(File file) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        if(!file.isFile()) return values;
        for(String line : Tools.read(file).split("\n")) {
            int colon = line.indexOf(':');
            if(colon <= 0) continue;
            values.put(line.substring(0, colon), line.substring(colon + 1).trim());
        }
        return values;
    }

    private static void write(File file, Map<String, String> values) throws IOException {
        StringBuilder builder = new StringBuilder();
        for(Map.Entry<String, String> entry : values.entrySet()) {
            builder.append(entry.getKey()).append(':').append(entry.getValue()).append('\n');
        }
        Tools.write(file, builder.toString());
    }
}
