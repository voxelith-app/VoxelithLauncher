package net.kdt.pojavlaunch.instances;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.JSONUtils;

import org.apache.commons.io.FileUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Packs a whole instance (settings, mods, configs, worlds) in one zip, to move to another phone
 * or give to a friend. The version json goes along so Fabric and Quilt instances start right away.
 */
public final class InstanceBackup {
    private static final String INSTANCE_PREFIX = "instance/";
    private static final String VERSIONS_PREFIX = "versions/";
    // Rebuilt by the game, only make the file bigger
    private static final Set<String> SKIPPED = new HashSet<>(Arrays.asList("logs", "crash-reports", ".cache", "natives"));

    private InstanceBackup() {}

    public static void export(Instance instance, OutputStream destination) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        try(ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(destination))) {
            addFolder(zip, instance.mInstanceRoot, INSTANCE_PREFIX, buffer, true);
            if(instance.sharedData) addFolder(zip, Instances.SHARED_DATA_DIRECTORY, INSTANCE_PREFIX, buffer, true);
            String versionId = instance.versionId;
            if(Tools.isValidString(versionId)) {
                File versionJson = new File(Tools.DIR_HOME_VERSION, versionId + "/" + versionId + ".json");
                if(versionJson.isFile()) addFile(zip, versionJson, VERSIONS_PREFIX + versionId + "/" + versionJson.getName(), buffer);
            }
        }
    }

    private static void addFolder(ZipOutputStream zip, File folder, String prefix, byte[] buffer, boolean top) throws IOException {
        File[] files = folder.listFiles();
        if(files == null) return;
        for(File file : files) {
            if(top && SKIPPED.contains(file.getName())) continue;
            if(file.isDirectory()) addFolder(zip, file, prefix + file.getName() + "/", buffer, false);
            else if(!file.getName().equals("session.lock")) addFile(zip, file, prefix + file.getName(), buffer);
        }
    }

    private static void addFile(ZipOutputStream zip, File file, String name, byte[] buffer) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        try(InputStream in = new FileInputStream(file)) {
            int read;
            while((read = in.read(buffer)) != -1) zip.write(buffer, 0, read);
        }
        zip.closeEntry();
    }

    /** Creates a new instance from a backup and selects it. Never touches existing instances. */
    public static Instance importBackup(InputStream source) throws IOException {
        File root = Instances.newImportRoot();
        File versions = new File(Tools.DIR_HOME_VERSION);
        String rootPath = root.getCanonicalPath() + File.separator;
        String versionsPath = versions.getCanonicalPath() + File.separator;
        byte[] buffer = new byte[64 * 1024];
        try {
            FileUtils.forceMkdir(root);
            try(ZipInputStream zip = new ZipInputStream(new BufferedInputStream(source))) {
                ZipEntry entry;
                while((entry = zip.getNextEntry()) != null) {
                    if(entry.isDirectory()) continue;
                    String name = entry.getName();
                    File out;
                    if(name.startsWith(INSTANCE_PREFIX)) {
                        out = new File(root, name.substring(INSTANCE_PREFIX.length()));
                        if(!out.getCanonicalPath().startsWith(rootPath)) throw new IOException("Bad zip entry " + name);
                    }else if(name.startsWith(VERSIONS_PREFIX)) {
                        out = new File(versions, name.substring(VERSIONS_PREFIX.length()));
                        if(!out.getCanonicalPath().startsWith(versionsPath)) throw new IOException("Bad zip entry " + name);
                        // A version already on the phone may have been patched by an installer, keep it
                        if(out.exists()) continue;
                    }else {
                        continue;
                    }
                    FileUtils.forceMkdir(out.getParentFile());
                    try(OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                        int read;
                        while((read = zip.read(buffer)) != -1) os.write(buffer, 0, read);
                    }
                }
            }
            File metadata = Instances.metadataLocation(root);
            if(!metadata.isFile()) throw new NotABackupException();
            Instance instance = JSONUtils.readFromFile(metadata, Instance.class);
            if(instance == null) throw new NotABackupException();
            instance.mInstanceRoot = root;
            // The shared folder was copied into the backup, so the instance now owns its data
            instance.sharedData = false;
            instance.installer = null;
            instance.write();
            Instances.setSelectedInstance(instance);
            return instance;
        }catch (IOException | RuntimeException e) {
            FileUtils.deleteQuietly(root);
            throw e;
        }
    }

    public static class NotABackupException extends IOException {
        public NotABackupException() {
            super("not an instance backup");
        }
    }
}
