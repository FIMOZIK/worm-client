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
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.function.IntConsumer;

/**
 * Проверка + скачивание обновлений через GitHub Releases API.
 * Встроен retry: 3 попытки с задержкой 1s/2s между ними.
 */
public final class UpdateChecker {

    /** ЗАМЕНИ на свой репозиторий: "user/repo" (без .git). */
    public static final String GITHUB_REPO = "FIMOZIK/worm-client";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final int MAX_RETRIES = 3;

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

    // ============================================================
    // CHECK (с retry)
    // ============================================================
    public static UpdateInfo check(String currentVersion) throws Exception {
        if (GITHUB_REPO.contains("YOUR_GITHUB_USER")) {
            throw new IllegalStateException("репозиторий для обновлений не настроен");
        }

        Exception lastErr = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return checkOnce(currentVersion);
            } catch (IllegalStateException ise) {
                // 404 / "репозиторий не найден" — не retry, это не сеть
                throw ise;
            } catch (Exception e) {
                lastErr = e;
                System.err.println("[UpdateChecker] check attempt " + attempt
                        + " failed: " + e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try { Thread.sleep(1000L * attempt); } catch (InterruptedException ignored) {}
                }
            }
        }
        throw new Exception("update check failed after " + MAX_RETRIES + " attempts", lastErr);
    }

    private static UpdateInfo checkOnce(String currentVersion) throws Exception {
        String url = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "WormLauncher/" + currentVersion)
                .header("Accept", "application/vnd.github+json")
                .timeout(Duration.ofSeconds(30))
                .GET().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 404) {
            throw new IllegalStateException("репозиторий " + GITHUB_REPO + " не найден или нет релизов");
        }
        if (resp.statusCode() != 200) {
            throw new IOException("GitHub API ответил " + resp.statusCode());
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

    // ============================================================
    // DOWNLOAD (с retry)
    // ============================================================
    /**
     * Скачать assetUrl в destPath. progress получает 0..100 или -1 если размер неизвестен.
     */
    public static void download(String assetUrl, Path destPath, IntConsumer progress) throws Exception {
        Exception lastErr = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                downloadOnce(assetUrl, destPath, progress);
                return;
            } catch (Exception e) {
                lastErr = e;
                System.err.println("[UpdateChecker] download attempt " + attempt
                        + " failed: " + e.getMessage());
                try { Files.deleteIfExists(destPath); } catch (Exception ignored) {}
                if (attempt < MAX_RETRIES) {
                    try { Thread.sleep(1000L * attempt); } catch (InterruptedException ignored) {}
                }
            }
        }
        throw new Exception("download failed after " + MAX_RETRIES + " attempts", lastErr);
    }

    private static void downloadOnce(String assetUrl, Path destPath, IntConsumer progress) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(assetUrl))
                .header("User-Agent", "WormLauncher")
                .timeout(Duration.ofMinutes(10))
                .GET().build();
        HttpResponse<InputStream> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200) throw new IOException("HTTP " + resp.statusCode());

        long total = resp.headers().firstValueAsLong("content-length").orElse(-1);
        Files.createDirectories(destPath.getParent());

        // пишем в .part и переименовываем в конце — чтобы при обрыве не было битого jar
        Path tmp = destPath.resolveSibling(destPath.getFileName() + ".part");
        try (InputStream in = resp.body();
             OutputStream out = Files.newOutputStream(tmp)) {
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
        Files.move(tmp, destPath, StandardCopyOption.REPLACE_EXISTING);
    }

    // ============================================================
    // HELPERS
    // ============================================================
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