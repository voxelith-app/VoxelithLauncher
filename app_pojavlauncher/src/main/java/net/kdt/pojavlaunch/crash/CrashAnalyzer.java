package net.kdt.pojavlaunch.crash;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.mods.ModMetadata;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the game log and the newest crash report and tries to say, in plain words, why the game closed. */
public final class CrashAnalyzer {
    public static final int UNKNOWN = 0;
    public static final int OUT_OF_MEMORY = 1;
    public static final int WRONG_JAVA = 2;
    public static final int MISSING_DEPENDENCY = 3;
    public static final int DUPLICATE_MOD = 4;
    public static final int MOD_ERROR = 5;
    public static final int RENDERER = 6;
    public static final int KILLED = 7;

    public static final int ACTION_NONE = 0;
    public static final int ACTION_MODS = 1;
    public static final int ACTION_VIDEO = 2;
    public static final int ACTION_JAVA = 3;

    private static final int LOG_TAIL = 384 * 1024;
    private static final Set<String> IGNORED_IDS = new HashSet<>(Arrays.asList(
            "minecraft", "java", "fabricloader", "fabric-loader", "forge", "neoforge", "mixinextras", "fml",
            // Fabric API shows up as a suspect in almost every crash report
            "fabric-api", "fabric"));

    private static final Pattern CLASS_VERSION = Pattern.compile("class file version (\\d+)");
    private static final Pattern FABRIC_MISSING = Pattern.compile("requires (?:any version|version [^,]*?) of (?:mod )?'?([^'(,!]+?)'?\\s*(?:\\(([\\w\\-.]+)\\))?, which is missing");
    private static final Pattern FORGE_MISSING = Pattern.compile("Mod ID: '([\\w\\-.]+)', Requested by: '([\\w\\-.]+)'");
    // Only errors count: plain "from mod" warnings show up in healthy logs too
    private static final Pattern MIXIN_MOD = Pattern.compile("Mixin apply for mod ([\\w\\-.]+) failed|(?:MixinApplyError|InvalidMixinException|InvalidInjectionException|MixinTransformerError)[^\\n]*?from mod ([\\w\\-.]+)");
    private static final Pattern SUSPECTED = Pattern.compile("Suspected Mods?:\\s*(.+)");
    private static final Pattern ID_IN_PARENS = Pattern.compile("\\(([\\w\\-.]+)\\)");
    private static final Pattern FORGE_MOD_FILE = Pattern.compile("Mod File: (?:.*/)?([^/\\s]+\\.jar)");
    private static final Pattern CAUSE = Pattern.compile("(?:Caused by: |Exception in thread \"[^\"]*\" )(\\S+(?:Exception|Error)[^\\n]*)");

    public static class Result {
        public int kind = UNKNOWN;
        public int action = ACTION_NONE;
        /** Extra text for the message: mod names, Java version... */
        @Nullable public String detail;
        /** The line from the log that gave it away. */
        @Nullable public String evidence;
        public final List<ModMetadata> culprits = new ArrayList<>();
    }

    private CrashAnalyzer() {}

    public static Result analyze(@Nullable File gameDirectory, long sessionStart, boolean killedBySignal) {
        String log = readTail(new File(Tools.DIR_GAME_HOME, "latestlog.txt"));
        String report = gameDirectory == null ? "" : readNewestCrashReport(gameDirectory, sessionStart);
        String text = report + "\n" + log;
        Result result = new Result();

        if(text.contains("java.lang.OutOfMemoryError") || text.contains("GC overhead limit exceeded")) {
            result.kind = OUT_OF_MEMORY;
            result.action = ACTION_JAVA;
            result.evidence = lineWith(text, "OutOfMemoryError");
            return result;
        }

        Matcher classVersion = CLASS_VERSION.matcher(text);
        if(text.contains("UnsupportedClassVersionError") && classVersion.find()) {
            result.kind = WRONG_JAVA;
            result.action = ACTION_JAVA;
            result.detail = String.valueOf(Integer.parseInt(classVersion.group(1)) - 44);
            result.evidence = lineWith(text, "UnsupportedClassVersionError");
            return result;
        }

        Set<String> missing = new LinkedHashSet<>();
        Matcher fabricMissing = FABRIC_MISSING.matcher(text);
        while(fabricMissing.find()) missing.add(fabricMissing.group(1).trim());
        Matcher forgeMissing = FORGE_MISSING.matcher(text);
        while(forgeMissing.find()) missing.add(forgeMissing.group(1));
        if(!missing.isEmpty()) {
            result.kind = MISSING_DEPENDENCY;
            result.action = ACTION_MODS;
            result.detail = join(missing);
            return result;
        }

        if(text.contains("Duplicate mod") || text.contains("duplicate mods") || text.contains("DuplicateModsFoundException")) {
            result.kind = DUPLICATE_MOD;
            result.action = ACTION_MODS;
            result.evidence = firstLineMatching(text, "uplicate");
            return result;
        }

        List<ModMetadata> mods = gameDirectory == null ? new ArrayList<>() : ModMetadata.scan(new File(gameDirectory, "mods"));
        Set<String> culpritIds = new LinkedHashSet<>();
        Matcher suspected = SUSPECTED.matcher(text);
        while(suspected.find()) {
            Matcher ids = ID_IN_PARENS.matcher(suspected.group(1));
            while(ids.find()) culpritIds.add(ids.group(1));
        }
        Matcher mixin = MIXIN_MOD.matcher(text);
        while(mixin.find()) culpritIds.add(mixin.group(1) != null ? mixin.group(1) : mixin.group(2));
        culpritIds.removeAll(IGNORED_IDS);
        for(String id : culpritIds) {
            ModMetadata mod = findById(mods, id);
            if(mod != null && !result.culprits.contains(mod)) result.culprits.add(mod);
        }
        Matcher modFile = FORGE_MOD_FILE.matcher(report);
        while(modFile.find()) {
            String fileName = modFile.group(1);
            for(ModMetadata mod : mods) {
                if(mod.enabled && mod.file.getName().equals(fileName) && !result.culprits.contains(mod)
                        && !isBuiltinFile(fileName)) result.culprits.add(mod);
            }
        }
        if(!result.culprits.isEmpty()) {
            result.kind = MOD_ERROR;
            result.action = ACTION_MODS;
            result.evidence = cause(text);
            return result;
        }

        String lower = text.toLowerCase(Locale.ROOT);
        if(lower.contains("glfw error") || lower.contains("no opengl context") || lower.contains("failed to create window")
                || lower.contains("pixel format") || lower.contains("eglcreatecontext") || lower.contains("opengl 3.")
                || lower.contains("unsupported graphics") || lower.contains("graphics card")) {
            result.kind = RENDERER;
            result.action = ACTION_VIDEO;
            result.evidence = firstLineMatching(text, "GLFW", "OpenGL", "EGL", "graphics");
            return result;
        }

        result.evidence = cause(text);
        if(killedBySignal && result.evidence == null) result.kind = KILLED;
        return result;
    }

    @Nullable
    private static ModMetadata findById(List<ModMetadata> mods, String id) {
        for(ModMetadata mod : mods) if(mod.enabled && id.equals(mod.id)) return mod;
        for(ModMetadata mod : mods) if(mod.enabled && mod.provides.contains(id)) return mod;
        return null;
    }

    private static boolean isBuiltinFile(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        return name.startsWith("forge-") || name.startsWith("neoforge-") || name.startsWith("minecraft");
    }

    @Nullable
    private static String cause(String text) {
        Matcher matcher = CAUSE.matcher(text);
        String last = null;
        // The deepest "Caused by" is usually the real reason
        while(matcher.find()) last = matcher.group(1).trim();
        return last;
    }

    @Nullable
    private static String lineWith(String text, String needle) {
        return firstLineMatching(text, needle);
    }

    @Nullable
    private static String firstLineMatching(String text, String... needles) {
        for(String line : text.split("\n")) {
            for(String needle : needles) {
                if(line.contains(needle)) return line.trim();
            }
        }
        return null;
    }

    private static String join(Set<String> values) {
        StringBuilder builder = new StringBuilder();
        for(String value : values) {
            if(builder.length() > 0) builder.append(", ");
            builder.append(value);
        }
        return builder.toString();
    }

    private static String readNewestCrashReport(File gameDirectory, long sessionStart) {
        File[] reports = new File(gameDirectory, "crash-reports").listFiles();
        if(reports == null) return "";
        File newest = null;
        for(File report : reports) {
            if(!report.isFile() || !report.getName().endsWith(".txt")) continue;
            if(report.lastModified() < sessionStart - 5000) continue;
            if(newest == null || report.lastModified() > newest.lastModified()) newest = report;
        }
        return newest == null ? "" : readTail(newest);
    }

    private static String readTail(File file) {
        if(!file.isFile()) return "";
        try(RandomAccessFile input = new RandomAccessFile(file, "r")) {
            long length = input.length();
            long start = Math.max(0, length - LOG_TAIL);
            byte[] bytes = new byte[(int) (length - start)];
            input.seek(start);
            input.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }catch (IOException e) {
            return "";
        }
    }
}
