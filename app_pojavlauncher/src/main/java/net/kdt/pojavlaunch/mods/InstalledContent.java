package net.kdt.pojavlaunch.mods;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.utils.HashUtils;

import org.apache.commons.codec.binary.Hex;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A file inside mods/, resourcepacks/ or shaderpacks/, optionally matched to a Modrinth project.
 */
public class InstalledContent {
    private static final String DISABLED_SUFFIX = ".disabled";
    /** Hashing every jar again on each visit is slow on weak phones, so keep them per file state. */
    private static final Map<String, String> sHashCache = Collections.synchronizedMap(new HashMap<>());

    public File file;
    public final String sha1;
    @Nullable public String projectId;
    @Nullable public String title;
    @Nullable public String iconUrl;
    @Nullable public String versionName;
    @Nullable public ModrinthModels.Version update;

    private InstalledContent(File file, String sha1) {
        this.file = file;
        this.sha1 = sha1;
    }

    public boolean isEnabled() {
        return !file.getName().endsWith(DISABLED_SUFFIX);
    }

    public String displayName() {
        if(title != null) return title;
        String name = file.getName();
        if(name.endsWith(DISABLED_SUFFIX)) name = name.substring(0, name.length() - DISABLED_SUFFIX.length());
        return name;
    }

    /** Enables or disables the file the same way the PC launcher does, by adding or removing ".disabled". */
    public boolean setEnabled(boolean enabled) {
        if(enabled == isEnabled()) return true;
        String name = file.getName();
        String newName = enabled ? name.substring(0, name.length() - DISABLED_SUFFIX.length()) : name + DISABLED_SUFFIX;
        File newFile = new File(file.getParentFile(), newName);
        if(!file.renameTo(newFile)) return false;
        file = newFile;
        return true;
    }

    public static List<InstalledContent> scan(File folder, ContentType type) throws IOException {
        File[] files = folder.listFiles();
        if(files == null) return new ArrayList<>();
        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        List<InstalledContent> result = new ArrayList<>(files.length);
        for(File file : files) {
            if(!file.isFile() || !type.isContentFile(file.getName())) continue;
            result.add(new InstalledContent(file, sha1(file)));
        }
        return result;
    }

    /** Fills title, icon, version and available update from Modrinth. Files not on Modrinth keep their file name. */
    public static void identify(ModrinthClient client, List<InstalledContent> contents, ContentType type, ContentTarget target) throws IOException {
        if(contents.isEmpty()) return;
        Map<String, InstalledContent> byHash = new HashMap<>();
        for(InstalledContent content : contents) byHash.put(content.sha1, content);

        Map<String, ModrinthModels.Version> versions = client.identify(byHash.keySet());
        Set<String> projectIds = new HashSet<>();
        for(Map.Entry<String, ModrinthModels.Version> entry : versions.entrySet()) {
            InstalledContent content = byHash.get(entry.getKey());
            if(content == null || entry.getValue() == null) continue;
            content.projectId = entry.getValue().projectId;
            content.versionName = entry.getValue().versionNumber;
            projectIds.add(content.projectId);
        }

        Map<String, ModrinthModels.Project> projects = new HashMap<>();
        for(ModrinthModels.Project project : client.getProjects(projectIds)) projects.put(project.id, project);
        for(InstalledContent content : contents) {
            ModrinthModels.Project project = content.projectId == null ? null : projects.get(content.projectId);
            if(project == null) continue;
            content.title = project.title;
            content.iconUrl = project.iconUrl;
        }

        Map<String, ModrinthModels.Version> updates = client.checkUpdates(versions.keySet(), type, target);
        for(Map.Entry<String, ModrinthModels.Version> entry : updates.entrySet()) {
            InstalledContent content = byHash.get(entry.getKey());
            ModrinthModels.Version latest = entry.getValue();
            if(content == null || latest == null) continue;
            ModrinthModels.VersionFile file = latest.primaryFile();
            if(file == null || file.hashes == null || content.sha1.equalsIgnoreCase(file.hashes.sha1)) continue;
            content.update = latest;
        }
    }

    public static Set<String> installedProjectIds(List<InstalledContent> contents) {
        Set<String> ids = new HashSet<>();
        for(InstalledContent content : contents) if(content.projectId != null) ids.add(content.projectId);
        return ids;
    }

    static String sha1(File file) throws IOException {
        String key = file.getAbsolutePath() + ":" + file.length() + ":" + file.lastModified();
        String cached = sHashCache.get(key);
        if(cached != null) return cached;
        try {
            byte[] hash = HashUtils.fileHash(MessageDigest.getInstance("SHA-1"), file);
            String hex = new String(Hex.encodeHex(hash)).toLowerCase(Locale.ROOT);
            sHashCache.put(key, hex);
            return hex;
        }catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
