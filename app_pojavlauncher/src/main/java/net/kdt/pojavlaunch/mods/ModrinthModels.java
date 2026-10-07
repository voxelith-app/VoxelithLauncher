package net.kdt.pojavlaunch.mods;

import androidx.annotation.Nullable;

/**
 * Subset of the Modrinth API v2 responses used by the content browser.
 * Field names follow the API through FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES.
 */
public final class ModrinthModels {
    private ModrinthModels() {}

    public static class SearchResponse {
        public SearchHit[] hits;
        public int offset;
        public int totalHits;
    }

    public static class SearchHit {
        public String projectId;
        public String slug;
        public String title;
        public String description;
        public String author;
        public String iconUrl;
        public long downloads;
        public String projectType;
    }

    public static class Project {
        public String id;
        public String title;
        public String iconUrl;
        public String projectType;
        public String description;
        public String body;
        public long downloads;
        public GalleryImage[] gallery;
    }

    public static class GalleryImage {
        public String url;
        public String title;
        public boolean featured;
    }

    public static class Version {
        public String id;
        public String projectId;
        public String name;
        public String versionNumber;
        public String versionType;
        public String[] gameVersions;
        public String[] loaders;
        public String changelog;
        public VersionFile[] files;
        public Dependency[] dependencies;

        @Nullable
        public VersionFile primaryFile() {
            if(files == null || files.length == 0) return null;
            for(VersionFile file : files) if(file.primary) return file;
            return files[0];
        }
    }

    public static class VersionFile {
        public String url;
        public String filename;
        public boolean primary;
        public long size;
        public Hashes hashes;
    }

    public static class Hashes {
        public String sha1;
        public String sha512;
    }

    public static class Dependency {
        public String projectId;
        public String versionId;
        public String dependencyType;
    }
}
