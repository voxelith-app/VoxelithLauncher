package net.kdt.pojavlaunch.mods;

import androidx.annotation.Nullable;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import net.kdt.pojavlaunch.Tools;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Minimal client for the public Modrinth API v2, the same one used by the PC launcher.
 */
public class ModrinthClient {
    private static final String BASE_URL = "https://api.modrinth.com/v2/";
    private static final String USER_AGENT = "voxelith-app/VoxelithLauncher (github.com/voxelith-app/VoxelithLauncher)";
    public static final int PAGE_SIZE = 20;

    private final Gson mGson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    public ModrinthModels.SearchResponse search(ContentType type, ContentTarget target, String query, int offset) throws IOException {
        JsonArray facets = new JsonArray();
        facets.add(facet("project_type:" + type.modrinthType));
        if(type == ContentType.MOD && target.loader != null) facets.add(facet("categories:" + target.loader));
        if(target.gameVersion != null) facets.add(facet("versions:" + target.gameVersion));
        String url = BASE_URL + "search?limit=" + PAGE_SIZE
                + "&offset=" + offset
                + "&index=" + (query.isEmpty() ? "downloads" : "relevance")
                + "&query=" + encode(query)
                + "&facets=" + encode(facets.toString());
        return mGson.fromJson(request(url, null), ModrinthModels.SearchResponse.class);
    }


    /** Every version of a project that matches the target, newest first. */
    public ModrinthModels.Version[] getCompatibleVersions(String projectId, ContentType type, ContentTarget target) throws IOException {
        StringBuilder url = new StringBuilder(BASE_URL).append("project/").append(encode(projectId)).append("/version?include_changelog=false");
        String[] loaders = loadersFor(type, target);
        if(loaders != null) url.append("&loaders=").append(encode(mGson.toJson(loaders)));
        if(target.gameVersion != null) url.append("&game_versions=").append(encode(mGson.toJson(new String[]{target.gameVersion})));
        ModrinthModels.Version[] versions = mGson.fromJson(request(url.toString(), null), ModrinthModels.Version[].class);
        return versions == null ? new ModrinthModels.Version[0] : versions;
    }

    /** Newest version of a project that matches the target, or null if there is none. */
    @Nullable
    public ModrinthModels.Version getCompatibleVersion(String projectId, ContentType type, ContentTarget target) throws IOException {
        ModrinthModels.Version[] versions = getCompatibleVersions(projectId, type, target);
        return versions.length == 0 ? null : versions[0];
    }

    public ModrinthModels.Version getVersion(String versionId) throws IOException {
        return mGson.fromJson(request(BASE_URL + "version/" + encode(versionId), null), ModrinthModels.Version.class);
    }

    /** Looks up installed files by SHA-1. Unknown files are missing from the result. */
    public Map<String, ModrinthModels.Version> identify(Collection<String> sha1Hashes) throws IOException {
        if(sha1Hashes.isEmpty()) return Collections.emptyMap();
        JsonObject body = new JsonObject();
        body.add("hashes", mGson.toJsonTree(sha1Hashes));
        body.addProperty("algorithm", "sha1");
        String response = request(BASE_URL + "version_files", body.toString());
        return mGson.fromJson(response, new TypeToken<Map<String, ModrinthModels.Version>>(){}.getType());
    }

    /** Latest compatible version for each installed file, keyed by the file's SHA-1. */
    public Map<String, ModrinthModels.Version> checkUpdates(Collection<String> sha1Hashes, ContentType type, ContentTarget target) throws IOException {
        if(sha1Hashes.isEmpty()) return Collections.emptyMap();
        JsonObject body = new JsonObject();
        body.add("hashes", mGson.toJsonTree(sha1Hashes));
        body.addProperty("algorithm", "sha1");
        String[] loaders = loadersFor(type, target);
        if(loaders != null) body.add("loaders", mGson.toJsonTree(loaders));
        if(target.gameVersion != null) body.add("game_versions", mGson.toJsonTree(new String[]{target.gameVersion}));
        String response = request(BASE_URL + "version_files/update", body.toString());
        return mGson.fromJson(response, new TypeToken<Map<String, ModrinthModels.Version>>(){}.getType());
    }

    public ModrinthModels.Project getProject(String projectId) throws IOException {
        return mGson.fromJson(request(BASE_URL + "project/" + encode(projectId), null), ModrinthModels.Project.class);
    }

    public ModrinthModels.Project[] getProjects(Collection<String> projectIds) throws IOException {
        if(projectIds.isEmpty()) return new ModrinthModels.Project[0];
        String url = BASE_URL + "projects?ids=" + encode(mGson.toJson(projectIds));
        return mGson.fromJson(request(url, null), ModrinthModels.Project[].class);
    }

    @Nullable
    private static String[] loadersFor(ContentType type, ContentTarget target) {
        switch (type) {
            case MOD: return target.loader == null ? null : new String[]{target.loader};
            case RESOURCE_PACK: return new String[]{"minecraft"};
            default: return null;
        }
    }

    private static JsonArray facet(String value) {
        JsonArray array = new JsonArray();
        array.add(value);
        return array;
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        }catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String request(String url, @Nullable String postBody) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "application/json");
            if(postBody != null) {
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                try(OutputStream outputStream = connection.getOutputStream()) {
                    outputStream.write(postBody.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = connection.getResponseCode();
            if(code < 200 || code >= 300) throw new IOException("Modrinth HTTP " + code + " (" + url + ")");
            try(InputStream inputStream = connection.getInputStream()) {
                return Tools.read(inputStream);
            }
        }finally {
            connection.disconnect();
        }
    }
}
