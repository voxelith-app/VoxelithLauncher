package net.kdt.pojavlaunch.mods;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.JVersionList;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.Instance;

import java.io.File;

/**
 * Where content gets installed and which Minecraft version and mod loader it has to match.
 */
public class ContentTarget {
    public final File gameDirectory;
    @Nullable public final String gameVersion;
    @Nullable public final String loader;

    public ContentTarget(File gameDirectory, @Nullable String gameVersion, @Nullable String loader) {
        this.gameDirectory = gameDirectory;
        this.gameVersion = gameVersion;
        this.loader = loader;
    }

    public File getFolder(ContentType type) {
        return new File(gameDirectory, type.folderName);
    }

    public boolean supportsMods() {
        return loader != null;
    }

    public static ContentTarget fromInstance(Instance instance) {
        String versionId = instance.versionId;
        String gameVersion = null;
        String loader = null;
        if(Tools.isValidString(versionId)
                && !Instance.VERSION_LATEST_RELEASE.equals(versionId)
                && !Instance.VERSION_LATEST_SNAPSHOT.equals(versionId)) {
            loader = detectLoader(versionId);
            gameVersion = readGameVersion(versionId);
        }
        return new ContentTarget(instance.getGameDirectory(), gameVersion, loader);
    }

    @Nullable
    private static String detectLoader(String versionId) {
        String id = versionId.toLowerCase();
        if(id.contains("neoforge")) return "neoforge";
        if(id.contains("forge")) return "forge";
        if(id.contains("quilt-loader")) return "quilt";
        if(id.contains("fabric-loader")) return "fabric";
        return null;
    }

    @Nullable
    private static String readGameVersion(String versionId) {
        File versionJson = new File(Tools.DIR_HOME_VERSION + "/" + versionId + "/" + versionId + ".json");
        if(!versionJson.isFile()) return guessGameVersion(versionId);
        try {
            JVersionList.Version version = Tools.GLOBAL_GSON.fromJson(Tools.read(versionJson.getAbsolutePath()), JVersionList.Version.class);
            if(version != null && Tools.isValidString(version.inheritsFrom)) return version.inheritsFrom;
        }catch (Exception ignored) {}
        return guessGameVersion(versionId);
    }

    /** Without the version JSON only vanilla ids can be trusted to be the game version. */
    @Nullable
    private static String guessGameVersion(String versionId) {
        return detectLoader(versionId) == null ? versionId : null;
    }
}
