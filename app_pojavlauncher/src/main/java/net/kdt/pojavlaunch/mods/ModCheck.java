package net.kdt.pojavlaunch.mods;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Finds problems in an instance's mods folder before the game starts, the kind that end in a crash. */
public final class ModCheck {
    public static final int WRONG_LOADER = 0;
    public static final int DUPLICATE = 1;
    public static final int MISSING_DEPENDENCY = 2;
    public static final int DISABLED_DEPENDENCY = 3;
    public static final int WRONG_GAME_VERSION = 4;
    public static final int CONFLICT = 5;

    private static final Set<String> BUILTIN = new HashSet<>(Arrays.asList(
            "minecraft", "java", "fabricloader", "fabric-loader", "quilt_loader", "quilt_base",
            "forge", "neoforge", "fml", "javafml", "lowcodefml", "mixinextras", "mixin"));
    private static final String[][] CONFLICTS = {
            {"sodium", "embeddium"}, {"sodium", "rubidium"}, {"embeddium", "rubidium"},
            {"optifine", "sodium"}, {"optifine", "embeddium"}, {"optifine", "rubidium"},
            {"optifine", "iris"}, {"optifine", "oculus"}
    };

    public static class Issue {
        public final int type;
        /** The mod the problem is about, or the missing dependency id. */
        public final String subject;
        @Nullable public final String other;
        public final List<File> toDisable = new ArrayList<>();
        @Nullable public File toEnable;
        @Nullable public String toInstall;

        Issue(int type, String subject, @Nullable String other) {
            this.type = type;
            this.subject = subject;
            this.other = other;
        }
    }

    private ModCheck() {}

    public static List<Issue> check(ContentTarget target) {
        List<Issue> issues = new ArrayList<>();
        if(target.loader == null) return issues;
        List<ModMetadata> all = ModMetadata.scan(target.getFolder(ContentType.MOD));
        List<ModMetadata> enabled = new ArrayList<>();
        for(ModMetadata mod : all) if(mod.enabled) enabled.add(mod);

        Set<String> provided = new HashSet<>();
        Map<String, List<ModMetadata>> byId = new LinkedHashMap<>();
        for(ModMetadata mod : enabled) {
            provided.addAll(mod.provides);
            String id = primaryId(mod);
            if(id == null) continue;
            List<ModMetadata> list = byId.get(id);
            if(list == null) byId.put(id, list = new ArrayList<>());
            list.add(mod);
        }
        boolean hasConnector = provided.contains("connector");

        for(ModMetadata mod : enabled) {
            if(!mod.hasMetadata()) continue;
            if(!loaderMatches(mod, target, hasConnector)) {
                Issue issue = new Issue(WRONG_LOADER, mod.displayName(), loaderName(mod));
                issue.toDisable.add(mod.file);
                issues.add(issue);
            }
        }

        for(Map.Entry<String, List<ModMetadata>> entry : byId.entrySet()) {
            List<ModMetadata> copies = entry.getValue();
            if(copies.size() < 2) continue;
            ModMetadata newest = copies.get(0);
            for(ModMetadata copy : copies) if(copy.file.lastModified() > newest.file.lastModified()) newest = copy;
            Issue issue = new Issue(DUPLICATE, newest.displayName(), String.valueOf(copies.size()));
            for(ModMetadata copy : copies) if(copy != newest) issue.toDisable.add(copy.file);
            issues.add(issue);
        }

        if(target.gameVersion != null) {
            for(ModMetadata mod : enabled) {
                for(ModMetadata.Dependency dependency : mod.dependencies) {
                    if(!"minecraft".equals(dependency.id)) continue;
                    if(VersionMatcher.matches(dependency.versionRange, dependency.forgeStyle, target.gameVersion)) continue;
                    Issue issue = new Issue(WRONG_GAME_VERSION, mod.displayName(), dependency.versionRange);
                    issue.toDisable.add(mod.file);
                    issues.add(issue);
                }
            }
        }

        Map<String, Issue> missing = new HashMap<>();
        for(ModMetadata mod : enabled) {
            for(ModMetadata.Dependency dependency : mod.dependencies) {
                String id = dependency.id;
                if(BUILTIN.contains(id) || provided.contains(id) || isFabricApiModule(id, provided)) continue;
                Issue issue = missing.get(id);
                if(issue == null) {
                    ModMetadata disabled = findDisabled(all, id);
                    issue = new Issue(disabled != null ? DISABLED_DEPENDENCY : MISSING_DEPENDENCY,
                            disabled != null ? disabled.displayName() : id, mod.displayName());
                    if(disabled != null) issue.toEnable = disabled.file;
                    else issue.toInstall = id;
                    missing.put(id, issue);
                    issues.add(issue);
                }
            }
        }

        for(String[] pair : CONFLICTS) {
            ModMetadata first = findEnabled(enabled, byId, pair[0]);
            ModMetadata second = findEnabled(enabled, byId, pair[1]);
            if(first == null || second == null || first == second) continue;
            Issue issue = new Issue(CONFLICT, first.displayName(), second.displayName());
            issue.toDisable.add(first.file);
            issues.add(issue);
        }
        return issues;
    }

    @Nullable
    private static String primaryId(ModMetadata mod) {
        if(mod.id != null) return mod.id;
        if(isOptiFine(mod)) return "optifine";
        return null;
    }

    private static boolean isOptiFine(ModMetadata mod) {
        String name = mod.file.getName().toLowerCase(Locale.ROOT);
        return !mod.hasMetadata() && name.contains("optifine") && !name.contains("optifabric");
    }

    @Nullable
    private static ModMetadata findEnabled(List<ModMetadata> enabled, Map<String, List<ModMetadata>> byId, String id) {
        List<ModMetadata> list = byId.get(id);
        if(list != null && !list.isEmpty()) return list.get(0);
        return null;
    }

    @Nullable
    private static ModMetadata findDisabled(List<ModMetadata> all, String id) {
        for(ModMetadata mod : all) if(!mod.enabled && mod.provides.contains(id)) return mod;
        return null;
    }

    private static boolean isFabricApiModule(String id, Set<String> provided) {
        if(!id.equals("fabric") && !id.startsWith("fabric-")) return false;
        return provided.contains("fabric-api") || provided.contains("fabric") || provided.contains("quilted_fabric_api");
    }

    private static boolean loaderMatches(ModMetadata mod, ContentTarget target, boolean hasConnector) {
        Set<String> loaders = mod.loaders;
        switch (target.loader) {
            case ModMetadata.LOADER_FABRIC:
                return loaders.contains(ModMetadata.LOADER_FABRIC);
            case ModMetadata.LOADER_QUILT:
                return loaders.contains(ModMetadata.LOADER_QUILT) || loaders.contains(ModMetadata.LOADER_FABRIC);
            case ModMetadata.LOADER_NEOFORGE:
                if(loaders.contains(ModMetadata.LOADER_NEOFORGE)) return true;
                if(hasConnector && loaders.contains(ModMetadata.LOADER_FABRIC)) return true;
                // NeoForge for 1.20.1 still loads Forge mods
                return loaders.contains(ModMetadata.LOADER_FORGE)
                        && (target.gameVersion == null || VersionMatcher.matches("<=1.20.1", false, target.gameVersion));
            case ModMetadata.LOADER_FORGE:
                return loaders.contains(ModMetadata.LOADER_FORGE);
            default:
                return true;
        }
    }

    private static String loaderName(ModMetadata mod) {
        if(mod.loaders.contains(ModMetadata.LOADER_FABRIC)) return "Fabric";
        if(mod.loaders.contains(ModMetadata.LOADER_QUILT)) return "Quilt";
        if(mod.loaders.contains(ModMetadata.LOADER_NEOFORGE)) return "NeoForge";
        return "Forge";
    }
}
