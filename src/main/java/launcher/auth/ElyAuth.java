package launcher.auth;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

public class ElyAuth {

    private static final String AUTH = "https://authserver.ely.by";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(15)).build();
    private static final Gson GSON = new Gson();

    public record Session(String username, String uuid, String accessToken,
                          String clientToken, String refreshToken) {
        @Override public String toString() {
            return "Session[username=" + username
                    + ", uuid=" + uuid
                    + ", token=" + accessToken.substring(0, Math.min(8, accessToken.length())) + "...]";
        }
    }

    public static Session login(String login, String password) throws Exception {
        String clientToken = UUID.randomUUID().toString();

        JsonObject agent = new JsonObject();
        agent.addProperty("name", "Minecraft");
        agent.addProperty("version", 1);

        JsonObject body = new JsonObject();
        body.add("agent", agent);
        body.addProperty("username", login);
        body.addProperty("password", password);
        body.addProperty("clientToken", clientToken);
        body.addProperty("requestUser", true);

        HttpRequest req = HttpRequest.newBuilder(URI.create(AUTH + "/auth/authenticate"))
                .header("Content-Type", "application/json")
                .header("User-Agent", "WormLauncher/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            throw new RuntimeException("Ely.by auth failed: " + resp.statusCode() + "\n" + resp.body());

        JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
        JsonObject profile = json.getAsJsonObject("selectedProfile");
        if (profile == null)
            throw new RuntimeException("Ely.by не вернул selectedProfile");

        return new Session(
                profile.get("name").getAsString(),
                profile.get("id").getAsString(),
                json.get("accessToken").getAsString(),
                json.has("clientToken") ? json.get("clientToken").getAsString() : clientToken,
                null
        );
    }

    public static void invalidate(Session s) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("accessToken", s.accessToken());
            if (s.clientToken() != null) body.addProperty("clientToken", s.clientToken());
            HttpRequest req = HttpRequest.newBuilder(URI.create(AUTH + "/auth/invalidate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                    .build();
            HTTP.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {}
    }
}