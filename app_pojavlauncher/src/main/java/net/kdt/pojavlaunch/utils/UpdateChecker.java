package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.content.SharedPreferences;

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

    public static void checkAsync(Activity activity) {
        String installedCommit = activity.getString(R.string.voxelith_commit);
        if(installedCommit.isEmpty()) return;
        PojavApplication.sExecutorService.execute(() -> {
            try {
                JsonObject release = fetchRelease();
                if(release == null) return;
                String body = release.has("body") && !release.get("body").isJsonNull() ? release.get("body").getAsString() : "";
                Matcher matcher = COMMIT_PATTERN.matcher(body);
                if(!matcher.find()) return;
                String latestCommit = matcher.group(1);
                if(latestCommit.equals(installedCommit)) return;
                SharedPreferences prefs = LauncherPreferences.DEFAULT_PREF;
                if(prefs != null && latestCommit.equals(prefs.getString(PREF_IGNORED_COMMIT, null))) return;
                String downloadUrl = findApkUrl(release);
                if(downloadUrl == null) return;
                Tools.runOnUiThread(() -> showDialog(activity, latestCommit, downloadUrl));
            }catch (Exception ignored) {
                // No internet or GitHub unavailable: try again on the next start
            }
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
