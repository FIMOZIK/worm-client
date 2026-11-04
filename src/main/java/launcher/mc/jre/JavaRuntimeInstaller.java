package launcher.mc.jre;

import com.google.gson.*;
import launcher.mc.Downloader;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.function.DoubleConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class JavaRuntimeInstaller {

    private static final String ALL_URL =
            "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Возвращает путь к bin/java.exe. Если уже стоит — сразу. Иначе качает. */
    public static Path ensureRuntime(int wantedMajor, Path runtimeDir, DoubleConsumer progress)
            throws Exception {

        Path target = runtimeDir.resolve(String.valueOf(wantedMajor));
        Path existing = findJavaExe(target);
        if (existing != null) return existing;

        Files.createDirectories(target);

        // 1) Mojang
        try {
            Path exe = viaMojang(wantedMajor, target, progress);
            if (exe != null) return exe;
        } catch (Exception ignored) { /* fallback ниже */ }

        // 2) Adoptium (JDK zip)
        Path exe = viaAdoptium(wantedMajor, target, progress);
        if (exe != null) return exe;

        throw new Exception("не удалось скачать java " + wantedMajor
                + " (ни Mojang, ни Adoptium)");
    }

    public static Path findJavaExe(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) return null;
        Path flat = dir.resolve("bin").resolve("java.exe");
        if (Files.exists(flat)) return flat;
        try (var s = Files.walk(dir, 4)) {
            return s.filter(p -> p.getFileName().toString().equals("java.exe"))
                    .filter(p -> p.getParent() != null
                            && p.getParent().getFileName().toString().equals("bin"))
                    .findFirst().orElse(null);
        } catch (Exception e) { return null; }
    }

    // ============================================================
    // MOJANG RUNTIME
    // ============================================================
    private static Path viaMojang(int wantedMajor, Path target, DoubleConsumer progress)
            throws Exception {

        String json = httpGet(ALL_URL);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        String archKey = archKey();
        JsonObject platform = root.getAsJsonObject(archKey);
        if (platform == null) return null;

        JsonObject best = null;
        int bestMajor = Integer.MAX_VALUE;
        for (String key : platform.keySet()) {
            JsonArray arr = platform.getAsJsonArray(key);
            if (arr == null || arr.isEmpty()) continue;
            JsonObject first = arr.get(0).getAsJsonObject();
            if (!first.has("version")) continue;
            String verName = first.getAsJsonObject("version").get("name").getAsString();
            int major = parseMajor(verName);
            if (major >= wantedMajor && major < bestMajor) {
                bestMajor = major;
                best = first;
            }
        }
        if (best == null) return null;

        JsonObject m = best.getAsJsonObject("manifest");
        String mUrl = m.get("url").getAsString();
        String mSha = m.has("sha1") ? m.get("sha1").getAsString() : null;

        Path mFile = target.resolve(".manifest.json");
        Downloader.download(mUrl, mFile, mSha);
        JsonObject mf = JsonParser.parseString(Files.readString(mFile)).getAsJsonObject();
        JsonObject files = mf.getAsJsonObject("files");

        int total = files.size();
        int done = 0;
        Map<String, String> links = new LinkedHashMap<>();

        for (String path : files.keySet()) {
            JsonObject fo = files.getAsJsonObject(path);
            String type = fo.get("type").getAsString();
            Path dest = target.resolve(path);

            switch (type) {
                case "directory" -> Files.createDirectories(dest);
                case "file" -> {
                    JsonObject raw = fo.getAsJsonObject("downloads").getAsJsonObject("raw");
                    String url = raw.get("url").getAsString();
                    String sha = raw.has("sha1") ? raw.get("sha1").getAsString() : null;
                    Files.createDirectories(dest.getParent());
                    Downloader.download(url, dest, sha);
                    if (fo.has("executable") && fo.get("executable").getAsBoolean()) {
                        try { dest.toFile().setExecutable(true); } catch (Exception ignored) {}
                    }
                }
                case "link" -> links.put(path, fo.get("target").getAsString());
            }
            done++;
            if (done % 5 == 0 || done == total) progress.accept((double) done / total);
        }

        // resolve links (обычно bin/server/java.exe → ../java.exe)
        for (var e : links.entrySet()) {
            Path dest = target.resolve(e.getKey());
            Path src = dest.getParent().resolve(e.getValue()).normalize();
            if (!Files.exists(src)) src = target.resolve(e.getValue()).normalize();
            try {
                Files.createDirectories(dest.getParent());
                if (Files.exists(src)) Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {}
        }

        try { Files.deleteIfExists(mFile); } catch (Exception ignored) {}

        return findJavaExe(target);
    }

    // ============================================================
    // ADOPTIUM FALLBACK
    // ============================================================
    private static Path viaAdoptium(int wantedMajor, Path target, DoubleConsumer progress)
            throws Exception {

        String api = "https://api.adoptium.net/v3/assets/latest/" + wantedMajor
                + "/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse";

        String json = httpGet(api);
        JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
        if (arr.isEmpty()) return null;

        JsonObject pkg = arr.get(0).getAsJsonObject()
                .getAsJsonObject("binary").getAsJsonObject("package");
        String url  = pkg.get("link").getAsString();
        String name = pkg.get("name").getAsString();
        String sha  = pkg.has("checksum") ? pkg.get("checksum").getAsString() : null;

        Path zip = target.resolve(name);
        progress.accept(0.01);
        Downloader.download(url, zip, sha);
        progress.accept(0.5);

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                Path out = target.resolve(e.getName()).normalize();
                if (!out.startsWith(target)) continue;
                if (e.isDirectory()) { Files.createDirectories(out); continue; }
                Files.createDirectories(out.getParent());
                Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                if (e.getName().endsWith("java.exe")) {
                    try { out.toFile().setExecutable(true); } catch (Exception ignored) {}
                }
            }
        }
        try { Files.deleteIfExists(zip); } catch (Exception ignored) {}
        progress.accept(1.0);

        return findJavaExe(target);
    }

    // ============================================================
    // HELPERS
    // ============================================================
    private static String httpGet(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "WormLauncher/1.0")
                .GET().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            throw new Exception("http " + resp.statusCode() + " " + url);
        return resp.body();
    }

    private static String archKey() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean arm64 = arch.contains("aarch64") || arch.contains("arm64");
        boolean x86   = arch.matches("x86|i[3-6]86");
        if (x86)   return "windows-x86";
        if (arm64) return "windows-arm64";
        return "windows-x64";
    }

    private static int parseMajor(String v) {
        if (v == null) return 0;
        String[] p = v.split("\\.");
        try {
            int first = Integer.parseInt(p[0]);
            if (first == 1 && p.length > 1) return Integer.parseInt(p[1]);
            return first;
        } catch (Exception e) { return 0; }
    }

    private JavaRuntimeInstaller() {}
}