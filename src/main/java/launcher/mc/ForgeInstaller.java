package launcher.mc;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.DoubleConsumer;
import java.util.regex.*;

/**
 * Ставит Forge через официальный installer.jar:
 *   1) гарантирует установленную ваниль (нужен client.jar для патча)
 *   2) создаёт launcher_profiles.json — иначе installer падает с
 *      "There is no Minecraft launcher profile in ..."
 *   3) качает forge-{mc}-{forge}-installer.jar с maven.minecraftforge.net
 *   4) запускает: java -jar installer.jar --installClient {gameDir}
 *   5) возвращает id получившейся версии ({mc}-forge-{forgeVer})
 */
public final class ForgeInstaller {

    private static final String MAVEN_META = "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml";
    private static final String MAVEN_BASE = "https://maven.minecraftforge.net/net/minecraftforge/forge";

    private ForgeInstaller() {}

    /** Список forge-версий под конкретную mc-версию, свежие сверху. */
    public static List<String> fetchForgeVersions(String mcVersion) throws Exception {
        String xml = httpGet(MAVEN_META);
        Pattern p = Pattern.compile("<version>([^<]+)</version>");
        Matcher m = p.matcher(xml);
        String prefix = mcVersion + "-";
        List<String> out = new ArrayList<>();
        while (m.find()) {
            String v = m.group(1);
            if (v.startsWith(prefix)) out.add(v.substring(prefix.length()));
        }
        Collections.reverse(out); // maven-metadata идёт oldest→newest, нам нужно наоборот
        return out;
    }

    /** Установить Forge и вернуть id готовой версии, которую можно скармливать GameLauncher. */
    public static String install(Path gameDir, String mcVersion, String forgeLoaderVersion,
                                 DoubleConsumer progress) throws Exception {
        // 1) Ваниль должна быть установлена — иначе forge-патч не найдёт client.jar.
        Path vanillaJson = gameDir.resolve("versions").resolve(mcVersion).resolve(mcVersion + ".json");
        Path vanillaJar  = gameDir.resolve("versions").resolve(mcVersion).resolve(mcVersion + ".jar");
        if (!Files.exists(vanillaJson) || !Files.exists(vanillaJar)) {
            GameInstaller.install(mcVersion, gameDir, p -> progress.accept(p * 0.3));
        } else {
            progress.accept(0.3);
        }

        // 2) Forge installer проверяет наличие launcher_profiles.json в gameDir.
        //    Создаём минимальный, иначе падает с "There is no Minecraft launcher profile".
        Path profiles = gameDir.resolve("launcher_profiles.json");
        if (!Files.exists(profiles)) {
            Files.writeString(profiles, "{}", StandardCharsets.UTF_8);
        }
        //    На новых версиях MC (1.20+) Forge также ищет
        //    launcher_profiles_microsoft_store.json — создаём и его.
        Path profilesMs = gameDir.resolve("launcher_profiles_microsoft_store.json");
        if (!Files.exists(profilesMs)) {
            Files.writeString(profilesMs, "{}", StandardCharsets.UTF_8);
        }

        String fullVersion = mcVersion + "-" + forgeLoaderVersion;
        Path cacheDir = gameDir.resolve(".forge-cache").resolve(fullVersion);
        Files.createDirectories(cacheDir);
        Path installerJar = cacheDir.resolve("forge-installer.jar");

        if (!Files.exists(installerJar)) {
            String url = MAVEN_BASE + "/" + fullVersion + "/forge-" + fullVersion + "-installer.jar";
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
        pb.inheritIO();
        Process p = pb.start();
        int code = p.waitFor();
        if (code != 0) throw new RuntimeException("Forge installer exit code " + code);

        // 4) Проверяем, что installer реально создал папку версии.
        String[] candidates = {
                mcVersion + "-forge-" + forgeLoaderVersion,   // modern (1.13+)
                mcVersion + "-forge" + forgeLoaderVersion     // legacy (≤1.12)
        };
        String resultId = null;
        for (String cand : candidates) {
            Path vj = gameDir.resolve("versions").resolve(cand).resolve(cand + ".json");
            if (Files.exists(vj)) { resultId = cand; break; }
        }
        if (resultId == null)
            throw new RuntimeException("Forge installer не создал версию для " + fullVersion);

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