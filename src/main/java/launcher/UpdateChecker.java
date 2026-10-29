package launcher;

import com.google.gson.*;

import java.awt.Desktop;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Проверка обновлений через GitHub Releases API.
 * Сравнивает tag_name последнего релиза с текущей версией лаунчера.
 */
public final class UpdateChecker {

    /** ЗАМЕНИ на свой репозиторий: "user/repo". */
    public static final String GITHUB_REPO = "YOUR_GITHUB_USER/YOUR_REPO";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
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

    /**
     * Возвращает UpdateInfo если апдейт есть, null если текущая версия не хуже.
     * Бросает IllegalStateException если репозиторий не настроен / не найден.
     */
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

        // первый попавшийся .jar-ассет
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

    public static void openInBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception ignored) {}
    }

    /** true если latest > current, по числовому сравнению major.minor.patch. */
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