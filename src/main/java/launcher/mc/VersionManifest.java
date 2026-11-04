package launcher.mc;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

public class VersionManifest {

    private static final String[] MANIFEST_URLS = {
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json",
            "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json",
            "https://bmclapi2.bangbang93.com/mc/game/version_manifest_v2.json"
    };

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public record Entry(String id, String type, String url, String sha1) {}

    private static List<Entry> cache = null;

    private static final Map<String, List<String>> FABRIC_CACHE   = new HashMap<>();
    private static final Map<String, List<String>> FORGE_CACHE    = new HashMap<>();
    private static final Map<String, List<String>> NEOFORGE_CACHE = new HashMap<>();
    private static final Map<String, List<String>> QUILT_CACHE    = new HashMap<>();

    public static List<Entry> fetch() throws Exception {
        if (cache != null) return cache;

        Exception lastErr = null;
        for (String url : MANIFEST_URLS) {
            try {
                List<Entry> list = fetchFrom(url);
                cache = list;
                System.out.println("[VersionManifest] loaded " + list.size()
                        + " versions from " + hostOf(url));
                return list;
            } catch (Exception e) {
                lastErr = e;
                System.err.println("[VersionManifest] failed " + hostOf(url)
                        + ": " + e.getMessage());
            }
        }
        throw new Exception("all manifest sources failed", lastErr);
    }

    private static List<Entry> fetchFrom(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "WormLauncher/1.0")
                .timeout(Duration.ofSeconds(30))
                .GET().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            throw new RuntimeException("http " + resp.statusCode());

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

    public static synchronized List<String> fetchNeoForgeVersions(String mcVersion) throws Exception {
        List<String> cached = NEOFORGE_CACHE.get(mcVersion);
        if (cached != null) return cached;
        List<String> fetched = NeoForgeInstaller.fetchNeoForgeVersions(mcVersion);
        NEOFORGE_CACHE.put(mcVersion, fetched);
        return fetched;
    }

    public static synchronized List<String> fetchQuiltLoaders(String mcVersion) throws Exception {
        List<String> cached = QUILT_CACHE.get(mcVersion);
        if (cached != null) return cached;
        List<String> fetched = QuiltInstaller.fetchLoaders(mcVersion);
        QUILT_CACHE.put(mcVersion, fetched);
        return fetched;
    }

    private static String hostOf(String url) {
        try { return URI.create(url).getHost(); } catch (Exception e) { return url; }
    }
}