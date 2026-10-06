package net.kdt.pojavlaunch.mods;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.downloader.Downloader;
import net.kdt.pojavlaunch.downloader.TaskMetadata;
import net.kdt.pojavlaunch.mirrors.DownloadMirror;
import net.kdt.pojavlaunch.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import git.artdeell.mojo.R;

/**
 * Installs Modrinth content into an instance. For mods, required dependencies are installed too,
 * like the PC launcher does.
 */
public class ContentInstaller extends Downloader {
    private static final int MAX_DEPENDENCIES = 64;

    private final ModrinthClient mClient;
    private final ContentTarget mTarget;

    public ContentInstaller(ModrinthClient client, ContentTarget target) {
        super(ProgressLayout.INSTALL_MODPACK);
        mClient = client;
        mTarget = target;
    }

    /**
     * @param installedProjectIds projects already present in the instance, they are not downloaded again
     * @return the versions that were installed, the requested project first
     */
    public List<ModrinthModels.Version> install(String projectId, ContentType type, Set<String> installedProjectIds) throws IOException, InterruptedException {
        ModrinthModels.Version version = mClient.getCompatibleVersion(projectId, type, mTarget);
        if(version == null) throw new IOException(new NoCompatibleVersionException());
        return installVersion(version, type, installedProjectIds);
    }

    /** Installs a specific version picked by the user, plus its required dependencies. */
    public List<ModrinthModels.Version> installVersion(ModrinthModels.Version version, ContentType type, Set<String> installedProjectIds) throws IOException, InterruptedException {
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.content_install_resolving);
        try {
            List<ModrinthModels.Version> roots = new ArrayList<>(1);
            roots.add(version);
            List<ModrinthModels.Version> versions = resolve(roots, type, installedProjectIds);
            download(versions, type);
            return versions;
        }finally {
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
        }
    }

    /**
     * Installs several projects at once. Each group lists alternatives in order of preference
     * (e.g. Sodium, then Embeddium); the first one with a compatible version is used and
     * groups without any compatible project are skipped.
     * @return the versions that were installed, dependencies included
     */
    public List<ModrinthModels.Version> installGroups(List<String[]> groups, ContentType type, Set<String> installedProjectIds) throws IOException, InterruptedException {
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.content_install_resolving);
        try {
            List<ModrinthModels.Version> roots = new ArrayList<>();
            for(String[] alternatives : groups) {
                for(String project : alternatives) {
                    ModrinthModels.Version version = mClient.getCompatibleVersion(project, type, mTarget);
                    if(version == null) continue;
                    roots.add(version);
                    break;
                }
            }
            List<ModrinthModels.Version> versions = resolve(roots, type, installedProjectIds);
            download(versions, type);
            return versions;
        }finally {
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
        }
    }

    private void download(List<ModrinthModels.Version> versions, ContentType type) throws IOException, InterruptedException {
        if(versions.isEmpty()) return;
        ArrayList<TaskMetadata> downloads = new ArrayList<>(versions.size());
        File folder = mTarget.getFolder(type);
        for(ModrinthModels.Version version : versions) downloads.add(toTask(version, folder));
        runDownloads(downloads);
    }

    /** Replaces an installed file with its newer version, keeping it disabled if it was. */
    public void update(InstalledContent content, ContentType type) throws IOException, InterruptedException {
        List<InstalledContent> single = new ArrayList<>(1);
        single.add(content);
        updateAll(single, type);
    }

    /**
     * Downloads every available update in one go, then swaps the old files out.
     * @return how many files were updated
     */
    public int updateAll(List<InstalledContent> contents, ContentType type) throws IOException, InterruptedException {
        List<InstalledContent> outdated = new ArrayList<>();
        for(InstalledContent content : contents) if(content.update != null) outdated.add(content);
        if(outdated.isEmpty()) return 0;
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.content_install_resolving);
        try {
            File folder = mTarget.getFolder(type);
            ArrayList<TaskMetadata> downloads = new ArrayList<>(outdated.size());
            for(InstalledContent content : outdated) downloads.add(toTask(content.update, folder));
            runDownloads(downloads);
            for(int i = 0; i < outdated.size(); i++) {
                InstalledContent content = outdated.get(i);
                File newFile = downloads.get(i).path;
                File oldFile = content.file;
                if(!oldFile.equals(newFile) && !oldFile.delete()) throw new IOException("Failed to remove " + oldFile.getName());
                if(!content.isEnabled() && !newFile.renameTo(new File(newFile.getParentFile(), newFile.getName() + ".disabled"))) {
                    throw new IOException("Failed to disable " + newFile.getName());
                }
            }
            return outdated.size();
        }finally {
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
        }
    }

    /** Adds required dependencies and drops anything the instance already has. */
    private List<ModrinthModels.Version> resolve(List<ModrinthModels.Version> roots, ContentType type, Set<String> installedProjectIds) throws IOException {
        List<ModrinthModels.Version> result = new ArrayList<>();
        Set<String> seen = new HashSet<>(installedProjectIds);
        Deque<ModrinthModels.Version> pending = new ArrayDeque<>(roots);
        while(!pending.isEmpty() && result.size() < MAX_DEPENDENCIES) {
            ModrinthModels.Version version = pending.poll();
            if(!seen.add(version.projectId)) continue;
            result.add(version);
            if(type != ContentType.MOD || version.dependencies == null) continue;
            for(ModrinthModels.Dependency dependency : version.dependencies) {
                if(!"required".equals(dependency.dependencyType)) continue;
                if(dependency.projectId != null) {
                    if(seen.contains(dependency.projectId)) continue;
                    // A pinned version may target another game version, so look up a compatible one
                    ModrinthModels.Version dependencyVersion = mClient.getCompatibleVersion(dependency.projectId, type, mTarget);
                    if(dependencyVersion != null) pending.add(dependencyVersion);
                } else if(dependency.versionId != null) {
                    pending.add(mClient.getVersion(dependency.versionId));
                }
            }
        }
        return result;
    }

    private static TaskMetadata toTask(ModrinthModels.Version version, File folder) throws IOException {
        ModrinthModels.VersionFile file = version.primaryFile();
        if(file == null) throw new IOException("Version " + version.id + " has no files");
        String fileName = new File(file.filename).getName();
        File destination = new File(folder, fileName);
        FileUtils.ensureParentDirectory(destination);
        return new TaskMetadata(destination, new URL(file.url), file.size,
                file.hashes == null ? null : file.hashes.sha1, DownloadMirror.DOWNLOAD_CLASS_NONE);
    }

    public static class NoCompatibleVersionException extends Exception {
        public NoCompatibleVersionException() {
            super("No version compatible with this instance");
        }
    }
}
