package launcher.mc;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.DoubleConsumer;
import java.util.regex.*;

/**
 * Ставит NeoForge через официальный installer.jar:
 *   1) гарантирует установленную ваниль (нужен client.jar для патча)
 *   2) создаёт launcher_profiles.json — иначе installer падает
 *   3) качает neoforge-{version}-installer.jar с maven.neoforged.net
 *   4) запускает: java -jar installer.jar --installClient {gameDir}
 *   5) возвращает id получившейся версии (neoforge-{version})
 *
 * Формат версий NeoForge: {mcMajor}.{mcMinor}.{patch}
 *   20.2.86   → MC 1.20.2
 *   21.1.172  → MC 1.21.1
 *   21.4.50   → MC 1.21.4
 *   21.11.0   → MC 1.21.11
 * Для MC 1.20.1 и ниже NeoForge делался как форк Forge с форматом {mc}-{major}.{minor}.{patch}
 * — эта ветка НЕ поддерживается (используй обычный Forge).
 */
public final class NeoForgeInstaller {

    private static final String MAVEN_META = "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml";
    private static final String MAVEN_BASE = "https://maven.neoforged.net/releases/net/neoforged/neoforge";

    private NeoForgeInstaller() {}

    /** Список NeoForge-версий под конкретную mc-версию, свежие сверху. */
    public static List<String> fetchNeoForgeVersions(String mcVersion) throws Exception {
        String xml = httpGet(MAVEN_META);
        Pattern p = Pattern.compile("<version>([^<]+)</version>");
        Matcher m = p.matcher(xml);
        List<String> out = new ArrayList<>();
        while (m.find()) {
            String v = m.group(1);
            if (isForMc(v, mcVersion)) out.add(v);
        }
        Collections.reverse(out); // maven-metadata идёт oldest→newest
        return out;
    }

    /**
     * NeoForge 20.2.86 для MC 1.20.2:
     *   major = 20 → mc[1] = "20"
     *   minor = 2  → mc[2] = "2"
     *   MC = "1.20.2"
     */
    private static boolean isForMc(String neoforgeVer, String mcVersion) {
        String[] mc = mcVersion.split("\\.");
        if (mc.length < 3) return false; // нужен "1.X.Y"
        if (!"1".equals(mc[0])) return false;
        String expectedPrefix = mc[1] + "." + mc[2] + ".";
        return neoforgeVer.startsWith(expectedPrefix);
    }

    /** Установить NeoForge и вернуть id готовой версии. */
    public static String install(Path gameDir, String mcVersion, String neoforgeVersion,
                                 DoubleConsumer progress) throws Exception {
        // 1) Ваниль должна быть установлена
        Path vanillaJson = gameDir.resolve("versions").resolve(mcVersion).resolve(mcVersion + ".json");
        Path vanillaJar  = gameDir.resolve("versions").resolve(mcVersion).resolve(mcVersion + ".jar");
        if (!Files.exists(vanillaJson) || !Files.exists(vanillaJar)) {
            GameInstaller.install(mcVersion, gameDir, p -> progress.accept(p * 0.3));
        } else {
            progress.accept(0.3);
        }

        // 2) launcher_profiles.json (и microsoft_store для 1.20+)
        Path profiles = gameDir.resolve("launcher_profiles.json");
        if (!Files.exists(profiles)) {
            Files.writeString(profiles, "{}", StandardCharsets.UTF_8);
        }
        Path profilesMs = gameDir.resolve("launcher_profiles_microsoft_store.json");
        if (!Files.exists(profilesMs)) {
            Files.writeString(profilesMs, "{}", StandardCharsets.UTF_8);
        }

        Path cacheDir = gameDir.resolve(".neoforge-cache").resolve(neoforgeVersion);
        Files.createDirectories(cacheDir);
        Path installerJar = cacheDir.resolve("neoforge-installer.jar");

        if (!Files.exists(installerJar)) {
            String url = MAVEN_BASE + "/" + neoforgeVersion
                    + "/neoforge-" + neoforgeVersion + "-installer.jar";
            Downloader.download(url, installerJar, null);
        }
        progress.accept(0.4);

        // 3) Запуск installer.jar --installClient
        String javaExe = System.getProperty("java.home") + "\\bin\\java.exe";
        ProcessBuilder pb = new ProcessBuilder(
                javaExe, "-jar", installerJar.toAbsolutePath().toString(),
                "--installClient", gameDir.toAbsolutePath().toString()
        );
        pb.directory(gameDir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        // ловим вывод (чтобы видеть ошибки installer'а)
        try (var in = p.getInputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                System.out.print(new String(buf, 0, n, StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {}
        int code = p.waitFor();
        if (code != 0) throw new RuntimeException("NeoForge installer exit code " + code);

        // 4) Проверяем что installer создал папку версии
        String resultId = "neoforge-" + neoforgeVersion;
        Path vj = gameDir.resolve("versions").resolve(resultId).resolve(resultId + ".json");
        if (!Files.exists(vj)) {
            // fallback: поиск по префиксу (некоторые версии создают по-другому)
            Path versionsDir = gameDir.resolve("versions");
            if (Files.isDirectory(versionsDir)) {
                try (var s = Files.list(versionsDir)) {
                    Optional<String> found = s.filter(Files::isDirectory)
                            .map(pp -> pp.getFileName().toString())
                            .filter(name -> name.startsWith("neoforge-"))
                            .filter(name -> name.contains(neoforgeVersion))
                            .findFirst();
                    if (found.isPresent()) {
                        resultId = found.get();
                        progress.accept(1.0);
                        return resultId;
                    }
                } catch (Exception ignored) {}
            }
            throw new RuntimeException("NeoForge installer не создал версию для "
                    + neoforgeVersion + " (ожидалось " + resultId + ")");
        }

        progress.accept(1.0);
        return resultId;
    }

    private static String httpGet(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setRequestProperty("User-Agent", "WormLauncher/1.0");
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new IOException("HTTP " + code + " " + urlStr);
        try (InputStream in = c.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}