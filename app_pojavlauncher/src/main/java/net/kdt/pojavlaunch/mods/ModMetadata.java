package net.kdt.pojavlaunch.mods;

import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/** What a mod jar says about itself: id, loader, dependencies. Read from fabric.mod.json, quilt.mod.json or mods.toml. */
public class ModMetadata {
    public static final String LOADER_FABRIC = "fabric";
    public static final String LOADER_QUILT = "quilt";
    public static final String LOADER_FORGE = "forge";
    public static final String LOADER_NEOFORGE = "neoforge";

    private static final int MAX_ENTRY_BYTES = 512 * 1024;
    private static final Map<String, ModMetadata> sCache = new HashMap<>();

    public final File file;
    public final boolean enabled;
    @Nullable public String id;
    @Nullable public String name;
    @Nullable public String version;
    /** Ids this jar answers to: its own, "provides" aliases and every jar nested inside it. */
    public final Set<String> provides = new HashSet<>();
    public final Set<String> loaders = new HashSet<>();
    public final List<Dependency> dependencies = new ArrayList<>();

    public static class Dependency {
        public final String id;
        /** Fabric predicate (alternatives joined by "||") or Forge maven range; null means any version. */
        @Nullable public final String versionRange;
        public final boolean forgeStyle;

        Dependency(String id, @Nullable String versionRange, boolean forgeStyle) {
            this.id = id;
            this.versionRange = versionRange;
            this.forgeStyle = forgeStyle;
        }
    }

    private ModMetadata(File file) {
        this.file = file;
        this.enabled = !file.getName().endsWith(".disabled");
    }

    public String displayName() {
        if(name != null && !name.isEmpty()) return name;
        if(id != null) return id;
        String fileName = file.getName();
        return fileName.endsWith(".disabled") ? fileName.substring(0, fileName.length() - 9) : fileName;
    }

    public boolean hasMetadata() {
        return !loaders.isEmpty();
    }

    /** Reads every .jar and .jar.disabled in the folder. Results are cached by path, size and date. */
    public static List<ModMetadata> scan(File modsFolder) {
        List<ModMetadata> result = new ArrayList<>();
        File[] files = modsFolder.listFiles();
        if(files == null) return result;
        for(File file : files) {
            String fileName = file.getName().toLowerCase(Locale.ROOT);
            if(!file.isFile() || !(fileName.endsWith(".jar") || fileName.endsWith(".jar.disabled"))) continue;
            result.add(read(file));
        }
        return result;
    }

    public static ModMetadata read(File file) {
        String key = file.getAbsolutePath() + ":" + file.length() + ":" + file.lastModified();
        synchronized (sCache) {
            ModMetadata cached = sCache.get(key);
            if(cached != null) return cached;
        }
        ModMetadata metadata = new ModMetadata(file);
        try(ZipFile zip = new ZipFile(file)) {
            String fabric = entry(zip, "fabric.mod.json");
            if(fabric != null) metadata.readFabric(fabric, zip);
            String quilt = entry(zip, "quilt.mod.json");
            if(quilt != null) metadata.readQuilt(quilt);
            String neoforge = entry(zip, "META-INF/neoforge.mods.toml");
            if(neoforge != null) metadata.readToml(neoforge, LOADER_NEOFORGE);
            String forge = entry(zip, "META-INF/mods.toml");
            if(forge != null) metadata.readToml(forge, LOADER_FORGE);
        }catch (IOException | RuntimeException ignored) {
            // Broken or non-mod jar: it shows up without metadata
        }
        if(metadata.id != null) metadata.provides.add(metadata.id);
        synchronized (sCache) {
            sCache.put(key, metadata);
        }
        return metadata;
    }

    private void readFabric(String json, ZipFile zip) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        loaders.add(LOADER_FABRIC);
        loaders.add(LOADER_QUILT);
        if(id == null) id = string(root, "id");
        if(name == null) name = string(root, "name");
        if(version == null) version = string(root, "version");
        addAll(provides, root.get("provides"));
        JsonElement depends = root.get("depends");
        if(depends != null && depends.isJsonObject()) {
            for(Map.Entry<String, JsonElement> entry : depends.getAsJsonObject().entrySet()) {
                dependencies.add(new Dependency(entry.getKey(), fabricPredicate(entry.getValue()), false));
            }
        }
        JsonElement jars = root.get("jars");
        if(jars != null && jars.isJsonArray()) {
            for(JsonElement jar : jars.getAsJsonArray()) {
                if(!jar.isJsonObject()) continue;
                String path = string(jar.getAsJsonObject(), "file");
                if(path != null) readNestedFabric(zip, path);
            }
        }
    }

    private void readNestedFabric(ZipFile zip, String path) {
        ZipEntry entry = zip.getEntry(path);
        if(entry == null) return;
        try(ZipInputStream nested = new ZipInputStream(zip.getInputStream(entry))) {
            ZipEntry inner;
            while((inner = nested.getNextEntry()) != null) {
                if(!"fabric.mod.json".equals(inner.getName())) continue;
                JsonObject root = JsonParser.parseString(readLimited(nested)).getAsJsonObject();
                String nestedId = string(root, "id");
                if(nestedId != null) provides.add(nestedId);
                addAll(provides, root.get("provides"));
                return;
            }
        }catch (IOException | RuntimeException ignored) {}
    }

    private void readQuilt(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        loaders.add(LOADER_QUILT);
        JsonElement loader = root.get("quilt_loader");
        if(loader == null || !loader.isJsonObject()) return;
        JsonObject quilt = loader.getAsJsonObject();
        if(id == null) id = string(quilt, "id");
        if(version == null) version = string(quilt, "version");
        JsonElement metadata = quilt.get("metadata");
        if(name == null && metadata != null && metadata.isJsonObject()) name = string(metadata.getAsJsonObject(), "name");
        JsonElement provided = quilt.get("provides");
        if(provided != null && provided.isJsonArray()) {
            for(JsonElement element : provided.getAsJsonArray()) {
                if(element.isJsonPrimitive()) provides.add(element.getAsString());
                else if(element.isJsonObject() && string(element.getAsJsonObject(), "id") != null) provides.add(string(element.getAsJsonObject(), "id"));
            }
        }
    }

    /** Minimal TOML: only [[mods]] and [[dependencies.x]] tables with simple key = value lines. */
    private void readToml(String toml, String loader) {
        loaders.add(loader);
        String table = "";
        Map<String, String> current = new HashMap<>();
        List<Map<String, String>> mods = new ArrayList<>();
        List<Map<String, String>> deps = new ArrayList<>();
        for(String rawLine : toml.split("\n")) {
            String line = rawLine.trim();
            if(line.isEmpty() || line.startsWith("#")) continue;
            if(line.startsWith("[")) {
                table = line;
                current = new HashMap<>();
                if(line.startsWith("[[mods]]")) mods.add(current);
                else if(line.startsWith("[[dependencies")) deps.add(current);
                continue;
            }
            if(!table.startsWith("[[mods]]") && !table.startsWith("[[dependencies")) continue;
            int equals = line.indexOf('=');
            if(equals <= 0) continue;
            String key = line.substring(0, equals).trim();
            String value = line.substring(equals + 1).trim();
            int comment = value.indexOf(" #");
            if(comment > 0 && !value.startsWith("\"")) value = value.substring(0, comment).trim();
            if(value.startsWith("\"") && value.lastIndexOf('"') > 0) value = value.substring(1, value.lastIndexOf('"'));
            else if(value.startsWith("'") && value.lastIndexOf('\'') > 0) value = value.substring(1, value.lastIndexOf('\''));
            current.put(key, value);
        }
        for(Map<String, String> mod : mods) {
            String modId = mod.get("modId");
            if(modId == null) continue;
            if(id == null) {
                id = modId;
                name = mod.get("displayName");
                version = mod.get("version");
            }
            provides.add(modId);
        }
        for(Map<String, String> dep : deps) {
            String depId = dep.get("modId");
            if(depId == null) continue;
            String type = dep.get("type");
            boolean required = type != null ? "required".equalsIgnoreCase(type) : "true".equals(dep.get("mandatory"));
            if(!required) continue;
            String side = dep.get("side");
            if("SERVER".equalsIgnoreCase(side)) continue;
            dependencies.add(new Dependency(depId, dep.get("versionRange"), true));
        }
        // Before 1.20.5 NeoForge mods also used mods.toml; they are told apart by depending on neoforge
        if(LOADER_FORGE.equals(loader)) {
            for(Dependency dependency : dependencies) {
                if(!LOADER_NEOFORGE.equals(dependency.id)) continue;
                loaders.remove(LOADER_FORGE);
                loaders.add(LOADER_NEOFORGE);
                break;
            }
        }
    }

    @Nullable
    private static String fabricPredicate(JsonElement value) {
        if(value == null || value.isJsonNull()) return null;
        if(value.isJsonPrimitive()) return value.getAsString();
        if(value.isJsonArray()) {
            StringBuilder builder = new StringBuilder();
            for(JsonElement element : value.getAsJsonArray()) {
                if(!element.isJsonPrimitive()) continue;
                if(builder.length() > 0) builder.append("||");
                builder.append(element.getAsString());
            }
            return builder.toString();
        }
        return null;
    }

    private static void addAll(Set<String> target, @Nullable JsonElement element) {
        if(element == null || !element.isJsonArray()) return;
        JsonArray array = element.getAsJsonArray();
        for(JsonElement item : array) if(item.isJsonPrimitive()) target.add(item.getAsString());
    }

    @Nullable
    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    @Nullable
    private static String entry(ZipFile zip, String path) throws IOException {
        ZipEntry entry = zip.getEntry(path);
        if(entry == null) return null;
        try(InputStream in = zip.getInputStream(entry)) {
            return readLimited(in);
        }
    }

    private static String readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
            if(out.size() > MAX_ENTRY_BYTES) throw new IOException("metadata too large");
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    /** Renames to or from .disabled, like the mods screen does. */
    public static boolean setEnabled(File file, boolean enabled) {
        String fileName = file.getName();
        boolean isEnabled = !fileName.endsWith(".disabled");
        if(isEnabled == enabled) return true;
        String newName = enabled ? fileName.substring(0, fileName.length() - 9) : fileName + ".disabled";
        return file.renameTo(new File(file.getParentFile(), newName));
    }
}
