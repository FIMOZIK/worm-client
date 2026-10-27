package launcher.mc;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;


public class Downloader {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)       // ← критично: HTTP/2 дропается CDN
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final int MAX_RETRIES = 5;

    public static void download(String url, Path target, String expectedSha1) throws Exception {
        if (expectedSha1 != null && Files.exists(target) && sha1(target).equalsIgnoreCase(expectedSha1))
            return;

        Files.createDirectories(target.getParent());

        Exception last = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            Path tmp = target.resolveSibling(target.getFileName() + ".part");
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", "WormLauncher/1.0")
                        .GET().build();
                HttpResponse<Path> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofFile(tmp,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING));
                if (resp.statusCode() != 200)
                    throw new RuntimeException("download failed " + resp.statusCode() + " " + url);

                if (expectedSha1 != null && !sha1(tmp).equalsIgnoreCase(expectedSha1))
                    throw new RuntimeException("sha1 mismatch for " + target);

                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (Exception e) {
                last = e;
                try { Files.deleteIfExists(tmp); } catch (Exception ignored) {}
                if (attempt < MAX_RETRIES) {
                    Thread.sleep(200L * attempt);   // 200, 400, 600, 800 ms
                }
            }
        }
        throw new RuntimeException("download failed after " + MAX_RETRIES + " attempts: " + url, last);
    }

    public static String sha1(Path p) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        try (var in = Files.newInputStream(p)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}