package launcher.mc;

import launcher.auth.GameSession;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.DoubleConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class GameLauncher {

    private static final String AUTH_SERVER = "https://account.ely.by/api/authlib-injector";
    private static final Path GAME_ROOT = Path.of("D:\\wormlauncher");

    public static Process launch(GameSession session, String versionId) throws Exception {
        return launch(session, versionId, p -> {});
    }

    public static Process launch(GameSession session, String versionId, DoubleConsumer progress) throws Exception {
        Path gameDir = GAME_ROOT.resolve("game");

        VersionJson vj = VersionJson.load(gameDir, versionId);

        Path clientJar = gameDir.resolve("versions").resolve(vj.jarVersionId)
                .resolve(vj.jarVersionId + ".jar");

        if (!Files.exists(clientJar)) {
            GameInstaller.install(vj.jarVersionId, gameDir, progress);
        } else {
            progress.accept(1.0);
        }

        Path libsRoot  = gameDir.resolve("libraries");
        Path natives   = gameDir.resolve("natives").resolve(vj.jarVersionId);
        Path assetsDir = gameDir.resolve("assets");
        Path logCfg    = gameDir.resolve("versions").resolve(vj.jarVersionId).resolve("logging.xml");
        Path authlib   = GAME_ROOT.resolve("libs").resolve("authlib-injector.jar");

        // 1) Докачиваем всё, чего нет.
        for (VersionJson.Library lib : vj.libraries) {
            if (!lib.rules().isEmpty() && !Rules.allows(lib.rules())) continue;
            if (lib.artifact() == null) continue;
            String rel = lib.artifact().path();
            if (rel == null) continue;
            Path p = libsRoot.resolve(rel);
            if (!Files.exists(p)) {
                Files.createDirectories(p.getParent());
                Downloader.download(lib.artifact().url(), p, lib.artifact().sha1());
            }
        }

        // 2) Classpath.
        List<String> cp = new ArrayList<>();
        cp.add(clientJar.toAbsolutePath().toString());
        for (VersionJson.Library lib : vj.libraries) {
            if (!lib.rules().isEmpty() && !Rules.allows(lib.rules())) continue;
            if (lib.artifact() == null) continue;
            if (isNative(lib)) continue;
            String rel = lib.artifact().path();
            if (rel == null) continue;
            Path p = libsRoot.resolve(rel);
            if (Files.exists(p)) cp.add(p.toAbsolutePath().toString());
        }
        String classpath = String.join(";", cp);

        // 3) Natives extraction.
        Files.createDirectories(natives);
        for (VersionJson.Library lib : vj.libraries) {
            if (!lib.rules().isEmpty() && !Rules.allows(lib.rules())) continue;
            if (lib.artifact() == null) continue;
            if (!isNative(lib)) continue;
            if (!nativesAllowedForArch(lib.name())) continue;
            String rel = lib.artifact().path();
            if (rel == null) continue;
            Path jar = libsRoot.resolve(rel);
            if (Files.exists(jar)) {
                try { extractNatives(jar, natives); } catch (IOException ignored) {}
            }
        }

        // 4) Собираем команду из аргументов version.json.
        List<String> cmd = new ArrayList<>();
        cmd.add(System.getProperty("java.home") + "\\bin\\java.exe");
        cmd.add("-Xmx2G");

        Map<String, String> vars = new HashMap<>();
        vars.put("natives_directory", natives.toAbsolutePath().toString());
        vars.put("launcher_name", "worm-launcher");
        vars.put("launcher_version", "1.0.0");
        vars.put("classpath", classpath);
        vars.put("classpath_separator", ";");
        vars.put("library_directory", libsRoot.toAbsolutePath().toString());
        vars.put("auth_player_name", session.username());
        vars.put("version_name", vj.id);
        vars.put("game_directory", gameDir.toAbsolutePath().toString());
        vars.put("assets_root", assetsDir.toAbsolutePath().toString());
        vars.put("assets_index_name", vj.assetIndexId);
        vars.put("auth_uuid", session.uuid());
        vars.put("auth_access_token", session.accessToken());
        vars.put("user_type", session.userType());
        vars.put("version_type", "release");
        vars.put("resolution_width", "854");
        vars.put("resolution_height", "480");

        for (String arg : vj.jvmArguments) {
            cmd.add(substitute(arg, vars));
        }

        // authlib-injector только для Ely.by
        if (session.isEly()) {
            cmd.add("-javaagent:" + authlib.toAbsolutePath() + "=" + AUTH_SERVER);
        }

        // log4j конфиг
        if (Files.exists(logCfg)) {
            cmd.add("-Dlog4j.configurationFile=" + logCfg.toAbsolutePath());
        }

        cmd.add(vj.mainClass);

        for (String arg : vj.gameArguments) {
            cmd.add(substitute(arg, vars));
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(gameDir.toFile());
        pb.inheritIO();
        return pb.start();
    }

    private static String substitute(String s, Map<String, String> vars) {
        if (s == null) return "";
        String out = s;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("${" + e.getKey() + "}", e.getValue());
        }
        return out;
    }

    private static boolean isNative(VersionJson.Library lib) {
        if (lib.artifact() == null || lib.artifact().url() == null) return false;
        return lib.artifact().url().contains("-natives-");
    }

    private static boolean nativesAllowedForArch(String libName) {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean isX86   = arch.matches("x86|i[3-6]86");
        boolean isArm64 = arch.contains("aarch64") || arch.contains("arm64");

        if (libName.endsWith(":natives-windows-x86") || libName.endsWith(":natives-linux-x86")) return isX86;
        if (libName.endsWith(":natives-windows-arm64")
                || libName.endsWith(":natives-macos-arm64")
                || libName.endsWith(":natives-linux-aarch_64")) return isArm64;
        if (libName.endsWith(":natives-windows")
                || libName.endsWith(":natives-linux")
                || libName.endsWith(":natives-macos")) return !isArm64;
        return true;
    }

    private static void extractNatives(Path jar, Path destDir) throws IOException {
        Files.createDirectories(destDir);
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(jar))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String entry = e.getName();
                String lower = entry.toLowerCase(Locale.ROOT);
                if (!lower.endsWith(".dll") && !lower.endsWith(".so") && !lower.endsWith(".dylib")) continue;
                int slash = entry.lastIndexOf('/');
                String fileName = slash >= 0 ? entry.substring(slash + 1) : entry;
                Path out = destDir.resolve(fileName);
                Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}