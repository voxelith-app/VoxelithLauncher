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
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.content_install_resolving);
        try {
            List<ModrinthModels.Version> versions = resolve(projectId, type, installedProjectIds);
            ArrayList<TaskMetadata> downloads = new ArrayList<>(versions.size());
            File folder = mTarget.getFolder(type);
            for(ModrinthModels.Version version : versions) downloads.add(toTask(version, folder));
            runDownloads(downloads);
            return versions;
        }finally {
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
        }
    }

    /** Replaces an installed file with its newer version, keeping it disabled if it was. */
    public void update(InstalledContent content, ContentType type) throws IOException, InterruptedException {
        if(content.update == null) return;
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.content_install_resolving);
        try {
            ArrayList<TaskMetadata> downloads = new ArrayList<>(1);
            TaskMetadata task = toTask(content.update, mTarget.getFolder(type));
            downloads.add(task);
            runDownloads(downloads);
            boolean wasEnabled = content.isEnabled();
            File oldFile = content.file;
            if(!oldFile.equals(task.path) && !oldFile.delete()) throw new IOException("Failed to remove " + oldFile.getName());
            if(!wasEnabled && !task.path.renameTo(new File(task.path.getParentFile(), task.path.getName() + ".disabled"))) {
                throw new IOException("Failed to disable " + task.path.getName());
            }
        }finally {
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
        }
    }

    private List<ModrinthModels.Version> resolve(String projectId, ContentType type, Set<String> installedProjectIds) throws IOException {
        List<ModrinthModels.Version> result = new ArrayList<>();
        Set<String> seen = new HashSet<>(installedProjectIds);
        seen.remove(projectId);
        Deque<String[]> queue = new ArrayDeque<>();
        queue.add(new String[]{projectId, null});
        while(!queue.isEmpty() && result.size() < MAX_DEPENDENCIES) {
            String[] entry = queue.poll();
            String currentProject = entry[0];
            if(currentProject != null && !seen.add(currentProject)) continue;
            ModrinthModels.Version version = entry[1] != null
                    ? mClient.getVersion(entry[1])
                    : mClient.getCompatibleVersion(currentProject, type, mTarget);
            if(version == null) {
                if(result.isEmpty()) throw new IOException(new NoCompatibleVersionException());
                continue;
            }
            if(currentProject == null && !seen.add(version.projectId)) continue;
            result.add(version);
            if(type != ContentType.MOD || version.dependencies == null) continue;
            for(ModrinthModels.Dependency dependency : version.dependencies) {
                if(!"required".equals(dependency.dependencyType)) continue;
                if(dependency.projectId != null && seen.contains(dependency.projectId)) continue;
                // A pinned version may target another game version, so only trust it without a project id
                if(dependency.projectId != null) queue.add(new String[]{dependency.projectId, null});
                else if(dependency.versionId != null) queue.add(new String[]{null, dependency.versionId});
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
