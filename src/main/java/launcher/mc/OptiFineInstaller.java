package launcher.mc;

import com.google.gson.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.DoubleConsumer;

public final class OptiFineInstaller {

    private static final String[] BMCLAPI_MIRRORS = {
            "https://bmclapi2.bangbang93.com/optifine/",
            "https://bsmirror.hkmc.online/optifine/",
            "https://mirror.hkmc.online/optifine/"
    };

    private OptiFineInstaller() {}

    public record VersionInfo(String mcVersion, String type, String patch, String filename) {
        @Override
        public String toString() {
            return mcVersion + "_" + type + "_" + patch;
        }
    }

    // ============================================================
    // FETCH — 3 зеркала + retry внутри httpGet
    // ============================================================
    public static List<VersionInfo> fetchVersions(String mcVersion) throws Exception {
        Exception last = null;
        for (String mirror : BMCLAPI_MIRRORS) {
            try {
                String url = mirror + URLEncoder.encode(mcVersion, StandardCharsets.UTF_8);
                String json = httpGet(url);
                JsonArray arr = JsonParser.parseString(json).getAsJsonArray();

                List<VersionInfo> out = new ArrayList<>();
                for (JsonElement e : arr) {
                    JsonObject o = e.getAsJsonObject();
                    if (!o.has("type") || !o.has("patch") || !o.has("filename")) continue;
                    out.add(new VersionInfo(
                            mcVersion,
                            o.get("type").getAsString(),
                            o.get("patch").getAsString(),
                            o.get("filename").getAsString()
                    ));
                }
                out.removeIf(v -> v.patch().contains("_pre") || v.filename().contains("_pre"));
                out.sort(Comparator.comparing(VersionInfo::patch).reversed());

                if (!out.isEmpty()) {
                    System.out.println("[OptiFine] versions loaded from " + hostOf(mirror));
                    return out;
                }
            } catch (Exception e) {
                last = e;
                System.err.println("[OptiFine] mirror failed " + hostOf(mirror)
                        + ": " + e.getMessage());
            }
        }
        throw new Exception("all optifine mirrors failed for " + mcVersion, last);
    }

    // ============================================================
    // ENSURE INSTALLED
    // ============================================================
    public static String ensureInstalled(Path gameDir, String mcVersion,
                                         VersionInfo ver,
                                         DoubleConsumer progress) throws Exception {

        String existing = findInstalledVersion(gameDir.resolve("versions"), mcVersion);
        if (existing != null) {
            System.out.println("[OptiFine] already installed in game: " + existing);
            copyOptifineLibraries(gameDir, mcVersion);
            progress.accept(1.0);
            return existing;
        }

        Path dotMcRoot = dotMinecraftRoot();
        if (dotMcRoot != null) {
            Path dotMcVersions = dotMcRoot.resolve("versions");
            String foundInDotMc = findInstalledVersion(dotMcVersions, mcVersion);
            if (foundInDotMc != null) {
                Path src = dotMcVersions.resolve(foundInDotMc);
                Path dst = gameDir.resolve("versions").resolve(foundInDotMc);
                copyFolder(src, dst);
                System.out.println("[OptiFine] copied profile from .minecraft → game: " + foundInDotMc);
                copyOptifineLibraries(gameDir, mcVersion);
                progress.accept(1.0);
                return foundInDotMc;
            }
        }

        Path cacheDir = gameDir.resolve(".cache").resolve("optifine");
        Files.createDirectories(cacheDir);
        Path jarFile = cacheDir.resolve(ver.filename());

        if (Files.exists(jarFile) && !isValidJar(jarFile)) {
            System.out.println("[OptiFine] cached jar is broken, deleting: " + jarFile);
            try { Files.delete(jarFile); } catch (Exception ignored) {}
        }

        if (!Files.exists(jarFile)) {
            boolean downloaded = tryAdloadx(ver, jarFile);
            if (!downloaded || !isValidJar(jarFile)) {
                throw new Exception(buildManualInstruction(ver, jarFile));
            }
        }
        progress.accept(0.3);

        Path lpj = gameDir.resolve("launcher_profiles.json");
        try { Files.writeString(lpj, "{}", StandardCharsets.UTF_8); } catch (Exception ignored) {}

        if (dotMcRoot != null) {
            try {
                Path dotMcLpj = dotMcRoot.resolve("launcher_profiles.json");
                if (!Files.exists(dotMcLpj)) {
                    Files.writeString(dotMcLpj, "{}", StandardCharsets.UTF_8);
                }
            } catch (Exception ignored) {}
        }

        int major = javaMajorForMc(mcVersion);
        String javaExe = findJava(major);
        System.out.println("[OptiFine] java: " + javaExe);

        System.out.println("[OptiFine] launching installer GUI: " + jarFile);
        ProcessBuilder pb = new ProcessBuilder(
                javaExe,
                "-Djava.awt.headless=false",
                "-Dfile.encoding=UTF-8",
                "-jar", jarFile.toAbsolutePath().toString()
        );
        pb.directory(gameDir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        progress.accept(0.5);

        StringBuilder out = new StringBuilder();
        try (var in = p.getInputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                String chunk = new String(buf, 0, n, StandardCharsets.UTF_8);
                out.append(chunk);
                System.out.print(chunk);
                if (out.length() > 20000) out.delete(0, out.length() - 10000);
            }
        } catch (Exception ignored) {}

        int code = p.waitFor();
        progress.accept(0.9);
        if (code != 0) {
            String tail = out.length() > 2000 ? out.substring(out.length() - 2000) : out.toString();
            throw new Exception("OptiFine installer exit code " + code + ". tail:\n" + tail);
        }

        String found = findInstalledVersion(gameDir.resolve("versions"), mcVersion);
        if (found != null) {
            copyOptifineLibraries(gameDir, mcVersion);
            progress.accept(1.0);
            return found;
        }

        if (dotMcRoot != null) {
            Path dotMcVersions = dotMcRoot.resolve("versions");
            String inDotMc = findInstalledVersion(dotMcVersions, mcVersion);
            if (inDotMc != null) {
                Path src = dotMcVersions.resolve(inDotMc);
                Path dst = gameDir.resolve("versions").resolve(inDotMc);
                copyFolder(src, dst);
                System.out.println("[OptiFine] copied from .minecraft after install: " + inDotMc);
                copyOptifineLibraries(gameDir, mcVersion);
                progress.accept(1.0);
                return inDotMc;
            }
        }

        throw new Exception("installer не создал профиль ни в game\\versions, ни в .minecraft\\versions.\n"
                + "ты нажал Install в окне установщика?\n"
                + "output tail:\n" + (out.length() > 1000 ? out.substring(out.length() - 1000) : out));
    }

    // ============================================================
    // Copy optifine libraries из .minecraft в game
    // ============================================================
    private static void copyOptifineLibraries(Path gameDir, String mcVersion) {
        Path dotMcRoot = dotMinecraftRoot();
        if (dotMcRoot == null) return;

        Path dotMcLibs = dotMcRoot.resolve("libraries");
        Path gameLibs = gameDir.resolve("libraries");
        if (!Files.isDirectory(dotMcLibs)) return;

        Path srcOptifine = dotMcLibs.resolve("optifine");
        if (Files.isDirectory(srcOptifine)) {
            Path dstOptifine = gameLibs.resolve("optifine");
            try {
                copyFolder(srcOptifine, dstOptifine);
                System.out.println("[OptiFine] copied libraries/optifine → game");
            } catch (Exception e) {
                System.out.println("[OptiFine] copy libraries/optifine failed: " + e.getMessage());
            }
        }

        Path srcLw = dotMcLibs.resolve("net").resolve("minecraft").resolve("launchwrapper");
        if (Files.isDirectory(srcLw)) {
            Path dstLw = gameLibs.resolve("net").resolve("minecraft").resolve("launchwrapper");
            try {
                copyFolder(srcLw, dstLw);
                System.out.println("[OptiFine] copied libraries/net/minecraft/launchwrapper → game");
            } catch (Exception e) {
                System.out.println("[OptiFine] copy launchwrapper failed: " + e.getMessage());
            }
        }

        Path srcNetOptifine = dotMcLibs.resolve("net").resolve("optifine");
        if (Files.isDirectory(srcNetOptifine)) {
            Path dstNetOptifine = gameLibs.resolve("net").resolve("optifine");
            try {
                copyFolder(srcNetOptifine, dstNetOptifine);
                System.out.println("[OptiFine] copied libraries/net/optifine → game");
            } catch (Exception e) {
                System.out.println("[OptiFine] copy net/optifine failed: " + e.getMessage());
            }
        }
    }

    // ============================================================
    // Поиск OptiFine профиля
    // ============================================================
    private static String findInstalledVersion(Path versionsDir, String mcVersion) {
        if (!Files.isDirectory(versionsDir)) return null;
        try (var s = Files.list(versionsDir)) {
            return s.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.startsWith(mcVersion + "-OptiFine"))
                    .max(Comparator.naturalOrder())
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static Path dotMinecraftRoot() {
        String appdata = System.getenv("APPDATA");
        if (appdata == null || appdata.isBlank()) return null;
        Path root = Path.of(appdata).resolve(".minecraft");
        return Files.isDirectory(root) ? root : null;
    }

    private static void copyFolder(Path src, Path dst) throws IOException {
        if (!Files.isDirectory(src)) return;
        Files.walk(src).forEach(source -> {
            try {
                Path target = dst.resolve(src.relativize(source));
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception ignored) {}
        });
    }

    // ============================================================
    // Try adloadx
    // ============================================================
    private static boolean tryAdloadx(VersionInfo ver, Path jarFile) {
        try {
            String url = "http://optifine.net/adloadx?f=" + URLEncoder.encode(ver.filename(), StandardCharsets.UTF_8);
            System.out.println("[OptiFine] trying adloadx: " + url);
            Downloader.download(url, jarFile, null);
            return isValidJar(jarFile);
        } catch (Exception e) {
            System.out.println("[OptiFine] adloadx failed: " + e.getMessage());
            return false;
        }
    }

    private static String buildManualInstruction(VersionInfo ver, Path jarFile) {
        return "Не удалось скачать OptiFine автоматически.\n\n" +
               "Скачай файл ВРУЧНУЮ:\n" +
               "  1) Открой https://optifine.net/downloads\n" +
               "  2) Найди Minecraft " + ver.mcVersion() + " → OptiFine " + ver.patch() + "\n" +
               "  3) Нажми (Mirror) → Download\n" +
               "  4) Сохрани файл как: " + ver.filename() + "\n" +
               "  5) Положи его сюда:\n     " + jarFile + "\n\n" +
               "После этого снова нажми Play — лаунчер подхватит готовый jar.";
    }

    // ============================================================
    // JAR VALIDATION
    // ============================================================
    private static boolean isValidJar(Path p) {
        try {
            if (!Files.exists(p)) return false;
            if (Files.size(p) < 100_000) return false;
            try (InputStream in = Files.newInputStream(p)) {
                byte[] head = new byte[4];
                int n = in.read(head);
                if (n < 4) return false;
                return head[0] == 'P' && head[1] == 'K' && head[2] == 0x03 && head[3] == 0x04;
            }
        } catch (Exception e) { return false; }
    }

    private static int javaMajorForMc(String mcVersion) {
        try {
            String[] p = mcVersion.split("\\.");
            if (p.length >= 1 && Integer.parseInt(p[0]) >= 26) return 25;
        } catch (Exception ignored) {}
        return 21;
    }

    private static String findJava(int wantedMajor) throws Exception {
        Path rt = launcher.Paths.mcDir().resolve("runtime").resolve(String.valueOf(wantedMajor));
        Path rtExe = launcher.mc.jre.JavaRuntimeInstaller.findJavaExe(rt);
        if (rtExe != null) return rtExe.toString();

        try {
            String ver = System.getProperty("java.version", "0");
            String[] p = ver.split("\\.");
            int cur = Integer.parseInt(p[0]);
            if (cur == 1 && p.length > 1) cur = Integer.parseInt(p[1]);
            if (cur == wantedMajor) {
                String curExe = System.getProperty("java.home") + "\\bin\\java.exe";
                if (Files.exists(Path.of(curExe))) return curExe;
            }
        } catch (Exception ignored) {}

        String home = System.getProperty("user.home", "");
        String[] cands = {
                "C:\\Program Files\\Java\\jdk-" + wantedMajor,
                "C:\\Program Files\\Eclipse Adoptium\\jdk-" + wantedMajor,
                home + "\\AppData\\Local\\Programs\\Eclipse Adoptium\\jdk-" + wantedMajor,
                "C:\\Program Files\\Microsoft\\jdk-" + wantedMajor,
                "C:\\Program Files\\Zulu\\zulu-" + wantedMajor,
        };
        for (String base : cands) {
            Path dir = Path.of(base);
            if (Files.isDirectory(dir) && Files.exists(dir.resolve("bin").resolve("java.exe"))) {
                return dir.resolve("bin").resolve("java.exe").toString();
            }
        }
        return System.getProperty("java.home") + "\\bin\\java.exe";
    }

    // ============================================================
    // httpGet — retry 3x per request
    // ============================================================
    private static String httpGet(String url) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setRequestMethod("GET");
                c.setConnectTimeout(20000);
                c.setReadTimeout(30000);
                c.setRequestProperty("User-Agent", "WormLauncher/1.0");
                c.setInstanceFollowRedirects(true);

                int code = c.getResponseCode();
                if (code != 200) {
                    throw new IOException("http " + code + " " + url);
                }

                try (InputStream in = c.getInputStream()) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                last = e;
                if (attempt < 3) {
                    System.err.println("[OptiFine] " + hostOf(url) + " attempt "
                            + attempt + " failed: " + e.getMessage());
                    try { Thread.sleep(500L * attempt); } catch (InterruptedException ignored) {}
                }
            } finally {
                if (c != null) c.disconnect();
            }
        }
        throw new Exception("http get failed after 3 attempts: " + url, last);
    }

    private static String hostOf(String url) {
        try { return java.net.URI.create(url).getHost(); }
        catch (Exception e) { return url; }
    }
}