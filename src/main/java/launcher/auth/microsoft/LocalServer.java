package launcher.auth.microsoft;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Мини HTTP-сервер на localhost. Ловит redirect от MS OAuth:
 *   GET /callback?code=...&state=...
 * Отдаёт code в CompletableFuture, потом сам останавливается.
 */
final class LocalServer implements AutoCloseable {

    private final HttpServer server;
    private final int port;
    private final CompletableFuture<String> codeFuture = new CompletableFuture<>();
    private final String expectedState;
    private volatile String error;

    LocalServer(int preferredPort, String expectedState) throws IOException {
        this.expectedState = expectedState;
        HttpServer srv = null;
        int actualPort = -1;
        IOException lastErr = null;

        // пробуем preferredPort..preferredPort+10
        for (int p = preferredPort; p <= preferredPort + 10; p++) {
            try {
                srv = HttpServer.create(new InetSocketAddress("127.0.0.1", p), 0);
                actualPort = p;
                break;
            } catch (IOException e) {
                lastErr = e;
            }
        }
        if (srv == null) throw lastErr != null ? lastErr : new IOException("cannot bind local server");

        this.server = srv;
        this.port = actualPort;

        server.createContext("/callback", exchange -> {
            try {
                String query = exchange.getRequestURI().getQuery();
                String code = param(query, "code");
                String state = param(query, "state");
                String err = param(query, "error");
                String errDesc = param(query, "error_description");

                String html;
                if (err != null) {
                    error = errDesc != null ? errDesc : err;
                    html = page("Вход отклонён", errDesc != null ? errDesc : err);
                    codeFuture.completeExceptionally(new IOException(error));
                } else if (code == null) {
                    error = "no code in redirect";
                    html = page("Ошибка", "В redirect нет code");
                    codeFuture.completeExceptionally(new IOException(error));
                } else if (expectedState != null && !expectedState.equals(state)) {
                    error = "state mismatch (CSRF)";
                    html = page("Ошибка", "state не совпал");
                    codeFuture.completeExceptionally(new IOException(error));
                } else {
                    html = page("Готово", "Можно закрыть вкладку и вернуться в лаунчер.");
                    codeFuture.complete(code);
                }

                byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
            } catch (Exception e) {
                try { exchange.sendResponseHeaders(500, -1); } catch (Exception ignored) {}
            } finally {
                exchange.close();
            }
        });

        server.setExecutor(null); // single thread
        server.start();
    }

    int port() { return port; }
    String redirectUri() { return "http://localhost:" + port + "/callback"; }

    String awaitCode(long timeoutSeconds) throws Exception {
        return codeFuture.get(timeoutSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        try { server.stop(0); } catch (Exception ignored) {}
    }

    private static String param(String query, String key) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            String k = java.net.URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            if (k.equals(key)) {
                return java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String page(String title, String msg) {
        return "<!DOCTYPE html><html><head><meta charset='utf-8'><title>" + esc(title) + "</title>"
                + "<style>body{font-family:system-ui;background:#0a0a3a;color:#F5E8DC;"
                + "display:flex;align-items:center;justify-content:center;height:100vh;margin:0}"
                + "div{text-align:center;padding:32px;background:#14144a;border-radius:12px;"
                + "box-shadow:0 4px 24px rgba(0,0,0,.4)}h1{margin:0 0 12px;color:#FFA07A}</style>"
                + "</head><body><div><h1>" + esc(title) + "</h1><p>" + esc(msg) + "</p></div></body></html>";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}