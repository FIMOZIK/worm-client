package launcher.auth;

import com.google.gson.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Вход через Microsoft (Mojang) по device code flow.
 * Пользователь получает код, открывает microsoft.com/link, вводит код —
 * лаунчер поллит токен-эндпоинт, потом гоняет XBL → XSTS → Minecraft.
 */
public class MojangAuth {

    // официальный public client id Minecraft Launcher — поддерживает device code flow
    private static final String CLIENT_ID = "00000000402b5328";
    private static final String SCOPE = "XboxLive.signin offline_access";

    private static final String DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL       = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String XBL_URL         = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL        = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN_URL    = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE_URL  = "https://api.minecraftservices.com/minecraft/profile";

    public record Session(String username, String uuid, String accessToken, String userType, String refreshToken) {
        @Override
        public String toString() {
            return "MojangSession{" + username + ", uuid=" + uuid + "}";
        }
    }

    public record DeviceCode(String userCode, String verificationUri, String deviceCode, int interval, int expiresIn) {}

    /** Шаг 1: получить код у Microsoft. */
    public static DeviceCode startDeviceCode() throws IOException {
        String body = "client_id=" + CLIENT_ID + "&scope=" + url(SCOPE);
        JsonObject json = postForm(DEVICE_CODE_URL, body);
        return new DeviceCode(
                json.get("user_code").getAsString(),
                json.get("verification_uri").getAsString(),
                json.get("device_code").getAsString(),
                json.has("interval") ? json.get("interval").getAsInt() : 5,
                json.has("expires_in") ? json.get("expires_in").getAsInt() : 900
        );
    }

    /**
     * Полный логин. onCode вызывается сразу после получения кода —
     * UI должен показать user_code и verification_uri пользователю.
     * Метод блокирующий, зови из фонового потока.
     */
    public static Session login(Consumer<DeviceCode> onCode) throws Exception {
        DeviceCode dc = startDeviceCode();
        onCode.accept(dc);

        String msAccessToken = pollForAccessToken(dc);
        return exchangeForMinecraft(msAccessToken);
    }

    private static String pollForAccessToken(DeviceCode dc) throws Exception {
        long deadline = System.currentTimeMillis() + dc.expiresIn() * 1000L;
        int interval = Math.max(2, dc.interval());

        while (System.currentTimeMillis() < deadline) {
            String body = "grant_type=urn:ietf:params:oauth:grant-type:device_code"
                    + "&client_id=" + CLIENT_ID
                    + "&device_code=" + url(dc.deviceCode());
            HttpURLConnection conn = openPostForm(TOKEN_URL, body);
            int code = conn.getResponseCode();
            String text;
            try (InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream()) {
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            if (code == 200 && json.has("access_token")) {
                return json.get("access_token").getAsString();
            }
            String err = json.has("error") ? json.get("error").getAsString() : "";
            switch (err) {
                case "authorization_pending" -> { /* продолжаем ждать */ }
                case "slow_down" -> interval += 5;
                case "expired_token" -> throw new IOException("код истёк, попробуй ещё раз");
                case "access_denied" -> throw new IOException("вход отклонён пользователем");
                default -> throw new IOException("oauth: " + (err.isEmpty() ? text : err));
            }
            Thread.sleep(interval * 1000L);
        }
        throw new IOException("таймаут ожидания входа");
    }

    /** OAuth-токен → Minecraft access token + профиль. */
    public static Session exchangeForMinecraft(String msAccessToken) throws Exception {
        // 1. Xbox Live user token
        JsonObject xblReq = new JsonObject();
        JsonObject xblProps = new JsonObject();
        xblProps.addProperty("AuthMethod", "RPS");
        xblProps.addProperty("SiteName", "user.auth.xboxlive.com");
        xblProps.addProperty("RpsTicket", "d=" + msAccessToken);
        xblReq.add("Properties", xblProps);
        xblReq.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xblReq.addProperty("TokenType", "JWT");
        JsonObject xblResp = postJson(XBL_URL, xblReq.toString());
        String xblToken = xblResp.get("Token").getAsString();
        String uhs = xblResp.getAsJsonObject("DisplayClaims")
                .getAsJsonArray("xui").get(0).getAsJsonObject()
                .get("uhs").getAsString();

        // 2. XSTS
        JsonObject xstsReq = new JsonObject();
        JsonObject xstsProps = new JsonObject();
        xstsProps.addProperty("SandboxId", "RETAIL");
        JsonArray tokens = new JsonArray();
        tokens.add(xblToken);
        xstsProps.add("UserTokens", tokens);
        xstsReq.add("Properties", xstsProps);
        xstsReq.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsReq.addProperty("TokenType", "JWT");

        HttpURLConnection xstsConn = openJsonPost(XSTS_URL, xstsReq.toString());
        int xstsCode = xstsConn.getResponseCode();
        String xstsText;
        try (InputStream in = xstsCode >= 400 ? xstsConn.getErrorStream() : xstsConn.getInputStream()) {
            xstsText = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (xstsCode != 200) {
            try {
                JsonObject errJson = JsonParser.parseString(xstsText).getAsJsonObject();
                long xerr = errJson.has("XErr") ? errJson.get("XErr").getAsLong() : 0L;

                String msg;
                if (xerr == 2148916233L) {
                    msg = "у аккаунта Microsoft нет Xbox профиля — зайди на xbox.com и создай";
                } else if (xerr == 2148916235L) {
                    msg = "Xbox Live недоступен в этом регионе";
                } else if (xerr == 2148916236L || xerr == 2148916237L) {
                    msg = "нужна взрослая верификация Xbox";
                } else if (xerr == 2148916238L) {
                    msg = "детский аккаунт — нужен доступ от родителей";
                } else {
                    msg = "XSTS: " + xstsText;
                }
                throw new IOException(msg);
            } catch (JsonParseException jpe) {
                throw new IOException("XSTS HTTP " + xstsCode + ": " + xstsText);
            }
        }
        JsonObject xstsResp = JsonParser.parseString(xstsText).getAsJsonObject();
        String xstsToken = xstsResp.get("Token").getAsString();

        // 3. Minecraft access token
        JsonObject mcReq = new JsonObject();
        mcReq.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);
        JsonObject mcResp = postJson(MC_LOGIN_URL, mcReq.toString());
        String mcAccess = mcResp.get("access_token").getAsString();

        // 4. Профиль (uuid + nick)
        HttpURLConnection profConn = openGet(MC_PROFILE_URL, mcAccess);
        int profCode = profConn.getResponseCode();
        String profText;
        try (InputStream in = profCode >= 400 ? profConn.getErrorStream() : profConn.getInputStream()) {
            profText = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (profCode == 404) throw new IOException("на этом аккаунте нет Minecraft");
        if (profCode != 200) throw new IOException("profile HTTP " + profCode + ": " + profText);
        JsonObject prof = JsonParser.parseString(profText).getAsJsonObject();
        String uuid = prof.get("id").getAsString();
        String name = prof.get("name").getAsString();

        return new Session(name, uuid, mcAccess, "msa", "");
    }

    // ---- helpers ----

    private static String url(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static JsonObject postForm(String url, String body) throws IOException {
        HttpURLConnection conn = openPostForm(url, body);
        int code = conn.getResponseCode();
        String text;
        try (InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream()) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (code != 200) throw new IOException("HTTP " + code + ": " + text);
        return JsonParser.parseString(text).getAsJsonObject();
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

    private static JsonObject postJson(String url, String body) throws IOException {
        HttpURLConnection conn = openJsonPost(url, body);
        int code = conn.getResponseCode();
        String text;
        try (InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream()) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (code != 200) throw new IOException("HTTP " + code + ": " + text);
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static HttpURLConnection openJsonPost(String url, String body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        try (OutputStream out = conn.getOutputStream()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return conn;
    }

    private static HttpURLConnection openGet(String url, String bearer) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Authorization", "Bearer " + bearer);
        conn.setRequestProperty("Accept", "application/json");
        return conn;
    }
}