package net.kdt.pojavlaunch.stats;

import android.util.Log;

import android.content.Context;

import androidx.annotation.Nullable;

import com.google.gson.reflect.TypeToken;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.utils.JSONUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import git.artdeell.mojo.R;

/**
 * Play time per instance. The game process writes a session file when it starts and touches it
 * every minute, so a session cut short (game killed, phone rebooted) still counts up to the last touch.
 */
public final class PlayTime {
    private static final long HEARTBEAT = 60 * 1000;
    private static Timer sHeartbeat;

    public static class Stats {
        public long totalMillis;
        public long lastPlayed;
        public int sessions;
    }

    public static class Session {
        public String instanceId;
        public String instanceName;
        public String gameDirectory;
        public long start;
    }

    private PlayTime() {}

    private static File statsFile() {
        return new File(Tools.DIR_GAME_HOME, "voxelith_playtime.json");
    }

    private static File sessionFile() {
        return new File(Tools.DIR_GAME_HOME, "voxelith_session.json");
    }

    /** Called in the game process right before Minecraft starts. */
    public static synchronized void start(Instance instance) {
        recoverStale(0);
        Session session = new Session();
        session.instanceId = instance.getId();
        session.instanceName = instance.name;
        session.gameDirectory = instance.getGameDirectory().getAbsolutePath();
        session.start = System.currentTimeMillis();
        try {
            JSONUtils.writeToFile(sessionFile(), session);
        }catch (Exception e) {
            Log.w("PlayTime", "Could not start the session", e);
            return;
        }
        if(sHeartbeat != null) sHeartbeat.cancel();
        sHeartbeat = new Timer("PlayTimeHeartbeat", true);
        sHeartbeat.schedule(new TimerTask() {
            @Override
            public void run() {
                boolean ignored = sessionFile().setLastModified(System.currentTimeMillis());
            }
        }, HEARTBEAT, HEARTBEAT);
    }

    /** Called in the game process when the game exits. Returns the session that just ended. */
    @Nullable
    public static synchronized Session finish() {
        if(sHeartbeat != null) {
            sHeartbeat.cancel();
            sHeartbeat = null;
        }
        Session session = readSession();
        if(session == null) return null;
        add(session, System.currentTimeMillis());
        boolean ignored = sessionFile().delete();
        return session;
    }

    /**
     * Counts a session left behind by a game that did not exit cleanly.
     * @param minAge only sessions untouched for this long are considered dead
     */
    public static synchronized void recoverStale(long minAge) {
        File file = sessionFile();
        if(!file.isFile()) return;
        long lastTouch = file.lastModified();
        if(System.currentTimeMillis() - lastTouch < minAge) return;
        Session session = readSession();
        if(session != null) add(session, lastTouch);
        boolean ignored = file.delete();
    }

    /** For the launcher: a session not touched for a few minutes belongs to a game that is gone. */
    public static void recoverStale() {
        recoverStale(3 * HEARTBEAT);
    }

    @Nullable
    private static Session readSession() {
        try {
            return JSONUtils.readFromFile(sessionFile(), Session.class);
        }catch (Exception e) {
            return null;
        }
    }

    private static void add(Session session, long end) {
        if(session.instanceId == null || end <= session.start) return;
        Map<String, Stats> all = loadAll();
        Stats stats = all.get(session.instanceId);
        if(stats == null) all.put(session.instanceId, stats = new Stats());
        stats.totalMillis += end - session.start;
        stats.lastPlayed = end;
        stats.sessions++;
        try {
            Tools.write(statsFile(), Tools.GLOBAL_GSON.toJson(all));
        }catch (Exception e) {
            Log.w("PlayTime", "Could not save play time", e);
        }
    }

    public static Map<String, Stats> loadAll() {
        try {
            File file = statsFile();
            if(!file.isFile()) return new HashMap<>();
            Map<String, Stats> all = Tools.GLOBAL_GSON.fromJson(Tools.read(file), new TypeToken<Map<String, Stats>>(){}.getType());
            return all != null ? all : new HashMap<>();
        }catch (Exception e) {
            return new HashMap<>();
        }
    }

    @Nullable
    public static Stats get(Instance instance) {
        return loadAll().get(instance.getId());
    }

    public static String format(Context context, long millis) {
        long minutes = millis / 60000;
        if(minutes < 1) return context.getString(R.string.playtime_under_minute);
        long hours = minutes / 60;
        if(hours == 0) return context.getString(R.string.playtime_minutes, minutes);
        return context.getString(R.string.playtime_hours, hours, minutes % 60);
    }
}
