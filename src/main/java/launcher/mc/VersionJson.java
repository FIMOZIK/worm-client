package launcher.mc;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class VersionJson {

    public record Download(String path, String url, String sha1, long size) {}

    public record Library(
            String name,
            Download artifact,
            Map<String, String> rules
    ) {}

    public final String id;
    public final String mainClass;
    public final String assetIndexUrl;
    public final String assetIndexSha1;
    public final String assetIndexId;
    public final Download client;
    public final Download loggingClient;
    public final List<Library> libraries;

    private VersionJson(JsonObject o) {
        id = o.get("id").getAsString();
        mainClass = o.get("mainClass").getAsString();

        JsonObject idx = o.getAsJsonObject("assetIndex");
        assetIndexUrl = idx.get("url").getAsString();
        assetIndexSha1 = idx.has("sha1") ? idx.get("sha1").getAsString() : null;
        assetIndexId = idx.get("id").getAsString();

        JsonObject cl = o.getAsJsonObject("downloads").getAsJsonObject("client");
        client = new Download(null,
                cl.get("url").getAsString(),
                cl.has("sha1") ? cl.get("sha1").getAsString() : null,
                cl.has("size") ? cl.get("size").getAsLong() : -1);

        Download lc = null;
        if (o.has("logging")) {
            JsonObject lo = o.getAsJsonObject("logging");
            if (lo.has("client")) {
                JsonObject f = lo.getAsJsonObject("client").getAsJsonObject("file");
                lc = new Download(null,
                        f.get("url").getAsString(),
                        f.has("sha1") ? f.get("sha1").getAsString() : null,
                        f.has("size") ? f.get("size").getAsLong() : -1);
            }
        }
        loggingClient = lc;

        List<Library> libs = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("libraries")) {
            JsonObject lo = e.getAsJsonObject();
            String name = lo.get("name").getAsString();

            Download art = null;
            if (lo.has("downloads")) {
                JsonObject d = lo.getAsJsonObject("downloads");
                if (d.has("artifact")) {
                    JsonObject a = d.getAsJsonObject("artifact");
                    art = new Download(
                            a.has("path") ? a.get("path").getAsString() : null,
                            a.get("url").getAsString(),
                            a.has("sha1") ? a.get("sha1").getAsString() : null,
                            a.has("size") ? a.get("size").getAsLong() : -1);
                }
            }

            Map<String, String> rules = new HashMap<>();
            if (lo.has("rules")) {
                for (JsonElement r : lo.getAsJsonArray("rules")) {
                    JsonObject ro = r.getAsJsonObject();
                    String action = ro.get("action").getAsString();
                    String os = ro.has("os") && ro.getAsJsonObject("os").has("name")
                            ? ro.getAsJsonObject("os").get("name").getAsString() : "any";
                    rules.put(os, action);
                }
            }

            libs.add(new Library(name, art, rules));
        }
        libraries = libs;
    }

    public static VersionJson parse(Path file) throws Exception {
        return new VersionJson(JsonParser.parseString(
                Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject());
    }
}