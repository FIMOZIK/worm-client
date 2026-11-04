package launcher.auth.microsoft;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import launcher.auth.MojangAuth;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.function.Consumer;

/**
 * Microsoft OAuth — authorization code flow с localhost redirect + PKCE.
 * После получения ms access_token → передаём в MojangAuth.exchangeForMinecraft.
 */
public final class MicrosoftOAuthFlow {

    // тот же CLIENT_ID что в MojangAuth — если ты зарегистрируешь свой Azure-app,
    // замени здесь на свой (нужен public client + redirect http://localhost:1337/callback).
    private static final String CLIENT_ID = "00000000402b5328";

    private static final String SCOPE = "XboxLive.signin offline_access";
    private static final String AUTHORIZE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
    private static final String TOKEN_URL     = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";

    private static final int PREFERRED_PORT = 1337;
    private static final long TIMEOUT_SECONDS = 300L;

    private MicrosoftOAuthFlow() {}

    /**
     * Полный browser-flow.
     * @param onBrowserOpen если задан — вызывается ПЕРЕД открытием браузера
     *                      (можешь логать URL в UI)
     */
    public static MojangAuth.Session login(Consumer<String> onBrowserOpen) throws Exception {
        Pkce pkce = Pkce.generate();
        String state = randomState();

        try (LocalServer server = new LocalServer(PREFERRED_PORT, state)) {
            String redirectUri = server.redirectUri();
            String authorizeUrl = buildAuthorizeUrl(redirectUri, state, pkce.challenge);

            if (onBrowserOpen != null) onBrowserOpen.accept(authorizeUrl);
            openBrowser(authorizeUrl);

            String code = server.awaitCode(TIMEOUT_SECONDS);

            String msAccessToken = exchangeCodeForToken(code, redirectUri, pkce.verifier);
            return MojangAuth.exchangeForMinecraft(msAccessToken, "");
        }
    }

    private static String buildAuthorizeUrl(String redirectUri, String state, String challenge) {
        return AUTHORIZE_URL
                + "?client_id=" + enc(CLIENT_ID)
                + "&response_type=code"
                + "&redirect_uri=" + enc(redirectUri)
                + "&response_mode=query"
                + "&scope=" + enc(SCOPE)
                + "&state=" + enc(state)
                + "&code_challenge=" + enc(challenge)
                + "&code_challenge_method=S256"
                + "&prompt=select_account";
    }

    private static String exchangeCodeForToken(String code, String redirectUri, String verifier)
            throws IOException {
        String body = "client_id=" + enc(CLIENT_ID)
                + "&grant_type=authorization_code"
                + "&code=" + enc(code)
                + "&redirect_uri=" + enc(redirectUri)
                + "&code_verifier=" + enc(verifier)
                + "&scope=" + enc(SCOPE);

        HttpURLConnection conn = openPostForm(TOKEN_URL, body);
        int status = conn.getResponseCode();
        String text;
        try (InputStream in = status >= 400 ? conn.getErrorStream() : conn.getInputStream()) {
            text = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        JsonObject json = JsonParser.parseString(text).getAsJsonObject();
        if (status != 200 || !json.has("access_token")) {
            String err = json.has("error") ? json.get("error").getAsString() : "http_" + status;
            String desc = json.has("error_description") ? json.get("error_description").getAsString() : text;
            // частые кейсы:
            //  AADSTS50011 — redirect_uri mismatch (нет localhost в Azure App)
            //  invalid_grant — code истёк/использован
            throw new IOException("token exchange: " + err + " — " + desc);
        }
        return json.get("access_token").getAsString();
    }

    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception ignored) {}
        // fallback — пользователь откроет вручную из лога
    }

    private static String randomState() {
        byte[] b = new byte[24];
        new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static HttpURLConnection openPostForm(String url, String body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Accept", "application/json");
        try (OutputStream out = conn.getOutputStream()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return conn;
    }
}