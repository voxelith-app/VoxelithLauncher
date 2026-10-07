package net.kdt.pojavlaunch.perf;

import android.content.Context;

import net.kdt.pojavlaunch.Architecture;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.mods.ContentTarget;
import net.kdt.pojavlaunch.mods.ContentType;
import net.kdt.pojavlaunch.mods.VersionMatcher;

import java.io.File;

/**
 * Picks how much memory the game gets from the phone's RAM, the Minecraft version and how many
 * mods the instance has. Kept on the low side: on 4 GB phones Android kills a game that asks
 * for too much, and with the performance mods 1.21 runs fine with a little over 1 GB.
 */
public final class MemoryAdvisor {
    private static final int MB_PER_MOD = 8;
    private static final int SHADERS_EXTRA = 256;

    private MemoryAdvisor() {}

    public static int recommend(Context context, Instance instance) {
        return recommend(context, ContentTarget.fromInstance(instance));
    }

    public static int recommend(Context context, ContentTarget target) {
        int need = baseFor(target.gameVersion);
        need += countFiles(target.getFolder(ContentType.MOD), ".jar") * MB_PER_MOD;
        if(countFiles(target.getFolder(ContentType.SHADER), ".zip") > 0) need += SHADERS_EXTRA;
        int max = maxSafe(context);
        int result = Math.max(512, Math.min(need, max));
        return result / 64 * 64;
    }

    /** The most the phone can give without Android closing the game or other apps. */
    public static int maxSafe(Context context) {
        int deviceRam = Tools.getTotalDeviceMemory(context);
        if(Architecture.is32BitsDevice() || deviceRam < 2048) return Math.min(1024, deviceRam / 2);
        return Math.min(deviceRam / 2, deviceRam - 1024);
    }

    private static int baseFor(String gameVersion) {
        if(gameVersion == null) return 1152;
        if(VersionMatcher.matches("<1.13", false, gameVersion)) return 640;
        if(VersionMatcher.matches("<1.17", false, gameVersion)) return 896;
        if(VersionMatcher.matches("<1.20.5", false, gameVersion)) return 1024;
        return 1152;
    }

    private static int countFiles(File folder, String suffix) {
        String[] names = folder.list();
        if(names == null) return 0;
        int count = 0;
        for(String name : names) if(name.endsWith(suffix)) count++;
        return count;
    }
}
