package launcher.mc.modpack;

import com.google.gson.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

final class CurseApi {

    record File(long projectID, long fileID, String fileName, String url) {}

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    static File resolve(long projectID, long fileID) {
        try {
            File f = viaCfwidget(projectID, fileID);
            if (f != null) return f;
        } catch (Exception ignored) {}

        try {
            return viaForgeCdnGuess(projectID, fileID);
        } catch (Exception ignored) {}

        return null;
    }

    private static File viaCfwidget(long projectID, long fileID) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(
                        URI.create("https://api.cfwidget.com/" + projectID))
                .header("User-Agent", "WormLauncher/1.0")
                .GET().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) return null;

        JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
        if (!root.has("files")) return null;

        for (JsonElement fe : root.getAsJsonArray("files")) {
            JsonObject fo = fe.getAsJsonObject();
            if (!fo.has("id")) continue;
            if (fo.get("id").getAsLong() != fileID) continue;

            String name = fo.has("name") ? fo.get("name").getAsString() : null;
            String url  = fo.has("url")  ? fo.get("url").getAsString()  : null;

            if ((url == null || url.isBlank()) && name != null) {
                url = "https://mediafilez.forgecdn.net/files/"
                        + (fileID / 1000) + "/" + (fileID % 1000) + "/" + name;
            }
            if (url == null) return null;

            return new File(projectID, fileID,
                    name != null ? name : ("mod-" + fileID + ".jar"),
                    url);
        }
        return null;
    }

    /**
     * Крайний fallback: без filename не обойтись, но forgecdn часто отдаёт
     * редирект с /files/{a}/{b}/{filename}. Здесь filename неизвестен,
     * поэтому используем типичный паттерн .jar — не идеально, но иногда
     * срабатывает, если имя совпадает.
     */
    private static File viaForgeCdnGuess(long projectID, long fileID) {
        // Этот путь намеренно оставлен пустым: filename без API не восстановить.
        // Если He задаст в будущем curseApiKey — сюда можно добавить
        // официальный вызов https://api.curseforge.com/v1/mods/{pid}/files/{fid}.
        return null;
    }

    private CurseApi() {}
}