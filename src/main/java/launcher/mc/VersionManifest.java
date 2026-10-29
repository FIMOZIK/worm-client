package launcher.mc;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

public class VersionManifest {

    private static final String URL =
            "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).build();

    public record Entry(String id, String type, String url, String sha1) {}

    private static List<Entry> cache = null;

    private static final Map<String, List<String>> FABRIC_CACHE = new HashMap<>();
    private static final Map<String, List<String>> FORGE_CACHE  = new HashMap<>();
    private static final Map<String, List<String>> QUILT_CACHE  = new HashMap<>();

    public static List<Entry> fetch() throws Exception {
        if (cache != null) return cache;
        HttpRequest req = HttpRequest.newBuilder(URI.create(URL))
                .header("User-Agent", "WormLauncher/1.0").GET().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            throw new RuntimeException("manifest fetch failed: " + resp.statusCode());

        JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
        List<Entry> out = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("versions")) {
            JsonObject o = e.getAsJsonObject();
            out.add(new Entry(
                    o.get("id").getAsString(),
                    o.get("type").getAsString(),
                    o.get("url").getAsString(),
                    o.has("sha1") ? o.get("sha1").getAsString() : null
            ));
        }
        cache = out;
        return out;
    }

    public static Entry find(String id) throws Exception {
        return fetch().stream()
                .filter(e -> e.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("версия не найдена: " + id));
    }

    public static Path downloadJson(Entry e, Path gameDir) throws Exception {
        Path dir = gameDir.resolve("versions").resolve(e.id());
        Files.createDirectories(dir);
        Path out = dir.resolve(e.id() + ".json");
        Downloader.download(e.url(), out, e.sha1());
        return out;
    }

    public static synchronized List<String> fetchFabricLoaders(String mcVersion) throws Exception {
        List<String> cached = FABRIC_CACHE.get(mcVersion);
        if (cached != null) return cached;
        List<String> fetched = FabricInstaller.fetchLoaders(mcVersion);
        FABRIC_CACHE.put(mcVersion, fetched);
        return fetched;
    }

    public static synchronized List<String> fetchForgeVersions(String mcVersion) throws Exception {
        List<String> cached = FORGE_CACHE.get(mcVersion);
        if (cached != null) return cached;
        List<String> fetched = ForgeInstaller.fetchForgeVersions(mcVersion);
        FORGE_CACHE.put(mcVersion, fetched);
        return fetched;
    }

    public static synchronized List<String> fetchQuiltLoaders(String mcVersion) throws Exception {
        List<String> cached = QUILT_CACHE.get(mcVersion);
        if (cached != null) return cached;
        List<String> fetched = QuiltInstaller.fetchLoaders(mcVersion);
        QUILT_CACHE.put(mcVersion, fetched);
        return fetched;
    }
}