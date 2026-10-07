package net.kdt.pojavlaunch.worlds;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.servers.NbtReader;

import org.apache.commons.io.FileUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Singleplayer worlds of an instance: listing, backups to zip, restore and import. */
public final class WorldStore {
    public static class World {
        public final File folder;
        public String name;
        public int gameType;
        public boolean hardcore;
        public long lastPlayed;
        @Nullable public String version;

        World(File folder) {
            this.folder = folder;
            this.name = folder.getName();
        }

        @Nullable
        public File icon() {
            File icon = new File(folder, "icon.png");
            return icon.isFile() ? icon : null;
        }
    }

    private WorldStore() {}

    public static File savesFolder(File gameDirectory) {
        return new File(gameDirectory, "saves");
    }

    /** Most recently played first. */
    public static List<World> list(File gameDirectory) {
        List<World> worlds = new ArrayList<>();
        File[] folders = savesFolder(gameDirectory).listFiles();
        if(folders == null) return worlds;
        for(File folder : folders) {
            if(!new File(folder, "level.dat").isFile()) continue;
            worlds.add(read(folder));
        }
        Collections.sort(worlds, (a, b) -> {
            if(a.lastPlayed != b.lastPlayed) return a.lastPlayed > b.lastPlayed ? -1 : 1;
            return a.name.compareToIgnoreCase(b.name);
        });
        return worlds;
    }

    @SuppressWarnings("unchecked")
    private static World read(File folder) {
        World world = new World(folder);
        world.lastPlayed = new File(folder, "level.dat").lastModified();
        try(InputStream in = new GZIPInputStream(new BufferedInputStream(new FileInputStream(new File(folder, "level.dat"))))) {
            Map<String, Object> root = NbtReader.readRoot(in);
            Object data = root.get("Data");
            if(!(data instanceof Map)) return world;
            Map<String, Object> level = (Map<String, Object>) data;
            if(level.get("LevelName") instanceof String) world.name = (String) level.get("LevelName");
            if(level.get("GameType") instanceof Integer) world.gameType = (Integer) level.get("GameType");
            if(level.get("hardcore") instanceof Byte) world.hardcore = (Byte) level.get("hardcore") != 0;
            if(level.get("LastPlayed") instanceof Long) world.lastPlayed = (Long) level.get("LastPlayed");
            Object version = level.get("Version");
            if(version instanceof Map && ((Map<String, Object>) version).get("Name") instanceof String) {
                world.version = (String) ((Map<String, Object>) version).get("Name");
            }
        }catch (IOException | RuntimeException ignored) {
            // Damaged level.dat: the folder name is still shown so the world can be backed up or deleted
        }
        return world;
    }

    public static File backupsFolder(String instanceId) {
        return new File(Tools.DIR_GAME_HOME, "backups/" + instanceId);
    }

    public static File backup(World world, String instanceId) throws IOException {
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(new Date());
        File target = new File(backupsFolder(instanceId), safeName(world.folder.getName()) + "_" + stamp + ".zip");
        FileUtils.forceMkdir(target.getParentFile());
        File partial = new File(target.getPath() + ".part");
        try(ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(partial)))) {
            addFolder(zip, world.folder, world.folder.getName() + "/");
        }catch (IOException e) {
            FileUtils.deleteQuietly(partial);
            throw e;
        }
        if(!partial.renameTo(target)) throw new IOException("Could not finish the backup");
        return target;
    }

    private static void addFolder(ZipOutputStream zip, File folder, String prefix) throws IOException {
        File[] files = folder.listFiles();
        if(files == null) return;
        byte[] buffer = new byte[64 * 1024];
        for(File file : files) {
            if(file.isDirectory()) {
                addFolder(zip, file, prefix + file.getName() + "/");
                continue;
            }
            // The lock is held by a running game and is useless in a backup
            if(file.getName().equals("session.lock")) continue;
            zip.putNextEntry(new ZipEntry(prefix + file.getName()));
            try(InputStream in = new FileInputStream(file)) {
                int read;
                while((read = in.read(buffer)) != -1) zip.write(buffer, 0, read);
            }
            zip.closeEntry();
        }
    }

    /** Newest first. */
    public static List<File> listBackups(String instanceId) {
        File[] files = backupsFolder(instanceId).listFiles((dir, name) -> name.endsWith(".zip"));
        if(files == null) return new ArrayList<>();
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return new ArrayList<>(Arrays.asList(files));
    }

    /**
     * Extracts a world zip into saves/. Works with zips that have the world inside a folder
     * (like the backups) or with level.dat at the root. Never overwrites an existing world.
     * @return the folder of the new world
     */
    public static File importZip(InputStream source, String fallbackName, File gameDirectory) throws IOException {
        File saves = savesFolder(gameDirectory);
        File staging = new File(saves, ".import-" + System.currentTimeMillis());
        try {
            FileUtils.forceMkdir(staging);
            extract(source, staging);
            File worldRoot = findWorldRoot(staging);
            if(worldRoot == null) throw new IOException("level.dat not found in the zip");
            String name = worldRoot.equals(staging) ? safeName(fallbackName) : worldRoot.getName();
            File target = uniqueFolder(saves, name);
            if(!worldRoot.renameTo(target)) throw new IOException("Could not move the world into saves");
            return target;
        }finally {
            FileUtils.deleteQuietly(staging);
        }
    }

    public static File restore(File backup, File gameDirectory) throws IOException {
        String name = backup.getName().replaceAll("_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}\\.zip$", "");
        try(InputStream in = new FileInputStream(backup)) {
            return importZip(in, name, gameDirectory);
        }
    }

    public static void delete(World world) throws IOException {
        FileUtils.deleteDirectory(world.folder);
    }

    @Nullable
    private static File findWorldRoot(File staging) {
        if(new File(staging, "level.dat").isFile()) return staging;
        File[] children = staging.listFiles();
        if(children == null) return null;
        for(File child : children) {
            if(child.isDirectory() && new File(child, "level.dat").isFile()) return child;
        }
        return null;
    }

    private static void extract(InputStream source, File destination) throws IOException {
        String root = destination.getCanonicalPath() + File.separator;
        byte[] buffer = new byte[64 * 1024];
        try(ZipInputStream zip = new ZipInputStream(new BufferedInputStream(source))) {
            ZipEntry entry;
            while((entry = zip.getNextEntry()) != null) {
                File out = new File(destination, entry.getName());
                if(!out.getCanonicalPath().startsWith(root)) throw new IOException("Bad zip entry " + entry.getName());
                if(entry.isDirectory()) {
                    FileUtils.forceMkdir(out);
                    continue;
                }
                FileUtils.forceMkdir(out.getParentFile());
                try(OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                    int read;
                    while((read = zip.read(buffer)) != -1) os.write(buffer, 0, read);
                }
            }
        }
    }

    private static File uniqueFolder(File parent, String name) {
        File candidate = new File(parent, name);
        int index = 2;
        while(candidate.exists()) candidate = new File(parent, name + " (" + index++ + ")");
        return candidate;
    }

    private static String safeName(String name) {
        String clean = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return clean.isEmpty() ? "Mundo" : clean;
    }
}
