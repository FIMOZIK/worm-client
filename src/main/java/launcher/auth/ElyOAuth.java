package launcher.auth;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ElyOAuth {

    private static final String AUTHORIZE = "https://account.ely.by/oauth2/v1";
    private static final String TOKEN     = "https://account.ely.by/api/oauth2/v1/token";
    private static final String USERINFO  = "https://account.ely.by/api/account/v1/info";
    private static final int    PORT      = 8080;
    private static final String REDIRECT  = "http://localhost:" + PORT + "/callback";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(15)).build();
    private static final Gson GSON = new Gson();

    private final String clientId;
    private final String clientSecret;

    public ElyOAuth(String clientId, String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public record Tokens(String accessToken, String refreshToken, long expiresIn) {}

    public Tokens authorize() throws Exception {
        String state = UUID.randomUUID().toString();
        CompletableFuture<String> codeFuture = new CompletableFuture<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
        server.createContext("/callback", exchange -> {
            Map<String, String> q = parseQuery(exchange.getRequestURI().getQuery());
            String html = "<html><body style='font-family:sans-serif;text-align:center;padding:40px'>"
                    + "<h2>Готово.</h2><p>Закрой вкладку и вернись в лаунчер.</p></body></html>";
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();

            if (!state.equals(q.get("state"))) {
                codeFuture.completeExceptionally(new IllegalStateException("state mismatch"));
            } else if (q.containsKey("error")) {
                codeFuture.completeExceptionally(new RuntimeException("Ely.by error: " + q.get("error")));
            } else {
                codeFuture.complete(q.get("code"));
            }
        });
        server.start();
        try {
            String url = AUTHORIZE
                    + "?client_id=" + enc(clientId)
                    + "&redirect_uri=" + enc(REDIRECT)
                    + "&response_type=code"
                    + "&scope=" + enc("account_info offline_access minecraft_server_session")
                    + "&state=" + enc(state);

            Desktop.getDesktop().browse(URI.create(url));
            String code = codeFuture.get(5, TimeUnit.MINUTES);
            return exchangeCode(code);
        } finally {
            server.stop(0);
        }
    }

    private Tokens exchangeCode(String code) throws Exception {
        String form = "grant_type=authorization_code"
                + "&client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret)
                + "&code=" + enc(code)
                + "&redirect_uri=" + enc(REDIRECT);

        HttpRequest req = HttpRequest.newBuilder(URI.create(TOKEN))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "WormLauncher/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            throw new RuntimeException("token exchange failed: " + resp.statusCode() + "\n" + resp.body());

        JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
        return new Tokens(
                json.get("access_token").getAsString(),
                json.has("refresh_token") ? json.get("refresh_token").getAsString() : null,
                json.has("expires_in") ? json.get("expires_in").getAsLong() : 0
        );
    }

    public ElyAuth.Session toSession(Tokens tokens) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(USERINFO))
                .header("Authorization", "Bearer " + tokens.accessToken())
                .header("User-Agent", "WormLauncher/1.0")
                .GET().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            throw new RuntimeException("userinfo failed: " + resp.statusCode() + "\n" + resp.body());

        JsonObject user = JsonParser.parseString(resp.body()).getAsJsonObject();
        return new ElyAuth.Session(
                user.get("username").getAsString(),
                user.get("uuid").getAsString(),
                tokens.accessToken(),
                null,
                tokens.refreshToken()
        );
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private static Map<String, String> parseQuery(String q) {
        Map<String, String> m = new HashMap<>();
        if (q == null) return m;
        for (String pair : q.split("&")) {
            int i = pair.indexOf('=');
            if (i < 0) continue;
            m.put(URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
        }
        return m;
    }
}