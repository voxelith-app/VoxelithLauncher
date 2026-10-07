package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import git.artdeell.mojo.R;

/**
 * Checks the fixed GitHub release for a build newer than the one installed.
 * The release body carries the commit it was built from, which is compared with our own.
 */
public final class UpdateChecker {
    private static final String RELEASE_URL = "https://api.github.com/repos/voxelith-app/VoxelithLauncher/releases/tags/voxelith-nightly";
    private static final String APK_NAME = "voxelith.apk";
    private static final String PREF_IGNORED_COMMIT = "voxelithIgnoredUpdate";
    private static final Pattern COMMIT_PATTERN = Pattern.compile("commit:\\s*([0-9a-f]{40})");

    private UpdateChecker() {}

    public static final String PREF_CHECK_UPDATES = "checkUpdates";

    public static void checkAsync(Activity activity) {
        SharedPreferences prefs = LauncherPreferences.DEFAULT_PREF;
        if(prefs != null && !prefs.getBoolean(PREF_CHECK_UPDATES, true)) return;
        check(activity, false);
    }

    /** Checks right away and always tells the result, even when there is nothing new. */
    public static void checkNow(Activity activity) {
        check(activity, true);
    }

    private static void check(Activity activity, boolean manual) {
        String installedCommit = activity.getString(R.string.voxelith_commit);
        if(installedCommit.isEmpty()) {
            if(manual) toast(activity, R.string.update_check_local_build);
            return;
        }
        PojavApplication.sExecutorService.execute(() -> {
            try {
                JsonObject release = fetchRelease();
                if(release == null) throw new Exception("no release");
                String body = release.has("body") && !release.get("body").isJsonNull() ? release.get("body").getAsString() : "";
                Matcher matcher = COMMIT_PATTERN.matcher(body);
                String downloadUrl = findApkUrl(release);
                if(!matcher.find() || downloadUrl == null) throw new Exception("release without build");
                String latestCommit = matcher.group(1);
                if(latestCommit.equals(installedCommit)) {
                    if(manual) toast(activity, R.string.update_check_latest);
                    return;
                }
                SharedPreferences prefs = LauncherPreferences.DEFAULT_PREF;
                if(!manual && prefs != null && latestCommit.equals(prefs.getString(PREF_IGNORED_COMMIT, null))) return;
                Tools.runOnUiThread(() -> showDialog(activity, latestCommit, downloadUrl));
            }catch (Exception e) {
                // No internet or GitHub unavailable: try again on the next start
                if(manual) toast(activity, R.string.update_check_failed);
            }
        });
    }

    private static void toast(Activity activity, int message) {
        Tools.runOnUiThread(() -> {
            if(!activity.isFinishing()) Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
        });
    }

    private static void showDialog(Activity activity, String latestCommit, String downloadUrl) {
        if(activity.isFinishing() || activity.isDestroyed()) return;
        new AlertDialog.Builder(activity)
                .setTitle(R.string.update_available_title)
                .setMessage(R.string.update_available_message)
                .setPositiveButton(R.string.update_available_download, (d, w) -> Tools.openURL(activity, downloadUrl))
                .setNegativeButton(R.string.update_available_later, null)
                .setNeutralButton(R.string.update_available_ignore, (d, w) -> {
                    if(LauncherPreferences.DEFAULT_PREF != null) {
                        LauncherPreferences.DEFAULT_PREF.edit().putString(PREF_IGNORED_COMMIT, latestCommit).apply();
                    }
                })
                .show();
    }

    private static String findApkUrl(JsonObject release) {
        JsonArray assets = release.getAsJsonArray("assets");
        if(assets == null) return null;
        for(JsonElement element : assets) {
            JsonObject asset = element.getAsJsonObject();
            if(APK_NAME.equals(asset.get("name").getAsString())) return asset.get("browser_download_url").getAsString();
        }
        return null;
    }

    private static JsonObject fetchRelease() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(RELEASE_URL).openConnection();
        try {
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "voxelith-app/VoxelithLauncher");
            if(connection.getResponseCode() != 200) return null;
            try(InputStream inputStream = connection.getInputStream()) {
                return JsonParser.parseString(Tools.read(inputStream)).getAsJsonObject();
            }
        }finally {
            connection.disconnect();
        }
    }
}
