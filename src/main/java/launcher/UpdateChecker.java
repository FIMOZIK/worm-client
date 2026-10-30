package launcher;

import com.google.gson.*;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.IntConsumer;

/**
 * Проверка + скачивание обновлений через GitHub Releases API.
 */
public final class UpdateChecker {

    /** ЗАМЕНИ на свой репозиторий: "user/repo" (без .git). */
    public static final String GITHUB_REPO = "FIMOZIK/worm-client";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private UpdateChecker() {}

    public record UpdateInfo(
            String tagName,
            String htmlUrl,
            String body,
            String assetUrl,
            String assetName
    ) {
        public String version() {
            return tagName != null && tagName.startsWith("v") ? tagName.substring(1) : tagName;
        }
    }

    public static UpdateInfo check(String currentVersion) throws Exception {
        if (GITHUB_REPO.contains("YOUR_GITHUB_USER")) {
            throw new IllegalStateException("репозиторий для обновлений не настроен");
        }
        String url = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "WormLauncher/" + currentVersion)
                .header("Accept", "application/vnd.github+json")
                .timeout(Duration.ofSeconds(20))
                .GET().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 404) {
            throw new IllegalStateException("репозиторий " + GITHUB_REPO + " не найден или нет релизов");
        }
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("GitHub API ответил " + resp.statusCode());
        }
        JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
        String tag = root.has("tag_name") && !root.get("tag_name").isJsonNull()
                ? root.get("tag_name").getAsString() : null;
        String html = root.has("html_url") && !root.get("html_url").isJsonNull()
                ? root.get("html_url").getAsString() : null;
        String body = root.has("body") && !root.get("body").isJsonNull()
                ? root.get("body").getAsString() : "";
        if (tag == null || html == null) return null;

        String tagVer = tag.startsWith("v") ? tag.substring(1) : tag;
        if (!isNewer(tagVer, currentVersion)) return null;

        String assetUrl = null, assetName = null;
        if (root.has("assets") && root.get("assets").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("assets")) {
                JsonObject a = el.getAsJsonObject();
                String name = a.has("name") ? a.get("name").getAsString() : "";
                if (name.toLowerCase().endsWith(".jar")) {
                    assetUrl = a.has("browser_download_url")
                            ? a.get("browser_download_url").getAsString() : null;
                    assetName = name;
                    break;
                }
            }
        }
        return new UpdateInfo(tag, html, body, assetUrl, assetName);
    }

    /**
     * Скачать assetUrl в destPath. progress получает 0..100 или -1 если размер неизвестен.
     */
    public static void download(String assetUrl, Path destPath, IntConsumer progress) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(assetUrl))
                .header("User-Agent", "WormLauncher")
                .timeout(Duration.ofMinutes(5))
                .GET().build();
        HttpResponse<InputStream> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200) throw new IOException("HTTP " + resp.statusCode());

        long total = resp.headers().firstValueAsLong("content-length").orElse(-1);
        Files.createDirectories(destPath.getParent());

        try (InputStream in = resp.body();
             OutputStream out = Files.newOutputStream(destPath)) {
            byte[] buf = new byte[65536];
            long read = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                read += n;
                if (progress != null) {
                    progress.accept(total > 0 ? (int) (read * 100L / total) : -1);
                }
            }
        }
    }

    public static void openInBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception ignored) {}
    }

    public static boolean isNewer(String latest, String current) {
        if (latest == null || current == null) return false;
        String[] a = latest.split("\\.");
        String[] b = current.split("\\.");
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int av = i < a.length ? parseIntSafe(a[i]) : 0;
            int bv = i < b.length ? parseIntSafe(b[i]) : 0;
            if (av > bv) return true;
            if (av < bv) return false;
        }
        return false;
    }

    private static int parseIntSafe(String s) {
        try {
            StringBuilder sb = new StringBuilder();
            for (char c : s.toCharArray()) {
                if (Character.isDigit(c)) sb.append(c);
                else break;
            }
            return sb.length() == 0 ? 0 : Integer.parseInt(sb.toString());
        } catch (Exception e) {
            return 0;
        }
    }
}