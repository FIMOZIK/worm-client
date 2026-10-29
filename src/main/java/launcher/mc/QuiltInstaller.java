package launcher.mc;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Ставит Quilt-лоадер в gameDir как синтетическую папку версии:
 *   versions/quilt-loader-{loader}-{mc}/quilt-loader-{loader}-{mc}.json
 * Meta API у Quilt совпадает по структуре с Fabric (v3), поэтому install()
 * идентичен FabricInstaller, отличается только base URL.
 */
public final class QuiltInstaller {

    private static final String META = "https://meta.quiltmc.org/v3";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private QuiltInstaller() {}

    public static List<String> fetchLoaders(String mcVersion) throws Exception {
        JsonArray arr = getJsonArray(META + "/versions/loader/" + mcVersion);
        List<String> out = new ArrayList<>();
        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("loader") && o.getAsJsonObject("loader").has("version")) {
                out.add(o.getAsJsonObject("loader").get("version").getAsString());
            }
        }
        return out;
    }

    public static String latestLoader(String mcVersion) throws Exception {
        List<String> l = fetchLoaders(mcVersion);
        return l.isEmpty() ? null : l.get(0);
    }

    public static String install(Path gameDir, String mcVersion, String loaderVersion) throws Exception {
        String id = "quilt-loader-" + loaderVersion + "-" + mcVersion;
        Path versionDir = gameDir.resolve("versions").resolve(id);
        Path versionJson = versionDir.resolve(id + ".json");

        String profileUrl = META + "/versions/loader/" + mcVersion + "/" + loaderVersion + "/profile/json";
        JsonObject profile = getJsonObject(profileUrl);

        profile.addProperty("id", id);
        if (!profile.has("inheritsFrom")) {
            profile.addProperty("inheritsFrom", mcVersion);
        }

        Files.createDirectories(versionDir);
        Files.writeString(versionJson, GSON.toJson(profile), StandardCharsets.UTF_8);

        VersionJson vj = VersionJson.load(gameDir, id);

        Path libsDir = gameDir.resolve("libraries");
        Files.createDirectories(libsDir);

        for (VersionJson.Library lib : vj.libraries) {
            if (!lib.rules().isEmpty() && !Rules.allows(lib.rules())) continue;
            if (lib.artifact() == null) continue;
            String rel = lib.artifact().path();
            if (rel == null) continue;
            Path dest = libsDir.resolve(rel);
            if (!Files.exists(dest)) {
                Files.createDirectories(dest.getParent());
                Downloader.download(lib.artifact().url(), dest, lib.artifact().sha1());
            }
        }

        return id;
    }

    private static JsonArray getJsonArray(String url) throws Exception {
        try (InputStream in = open(url)) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
        }
    }

    private static JsonObject getJsonObject(String url) throws Exception {
        try (InputStream in = open(url)) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static InputStream open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", "WormLauncher/1.0");
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new IOException("HTTP " + code + " for " + url);
        return c.getInputStream();
    }
}