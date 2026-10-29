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

        // 2) Classpath: только НЕ-natives jar'ы.
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

        // === DEBUG / FALLBACK: если lwjgl-glfw не попал в classpath, добираем вручную ===
        String cpJoin = String.join(";", cp);
        boolean hasGlfwMain = cp.stream().anyMatch(s ->
                s.toLowerCase().endsWith("lwjgl-glfw-3.3.3.jar") || s.toLowerCase().endsWith("lwjgl-glfw-3.3.1.jar"));
        System.out.println("[WORM-DEBUG] cp entries=" + cp.size() + " lwjgl-glfw main=" + hasGlfwMain);
        if (!hasGlfwMain) {
            Path glfwRoot = libsRoot.resolve("org/lwjgl/lwjgl-glfw");
            if (Files.isDirectory(glfwRoot)) {
                try (var vers = Files.list(glfwRoot)) {
                    for (Path v : vers.filter(Files::isDirectory).toList()) {
                        try (var jars = Files.list(v)) {
                            for (Path j : jars.filter(p ->
                                    p.getFileName().toString().endsWith(".jar")
                                    && !p.getFileName().toString().contains("-natives-")).toList()) {
                                String abs = j.toAbsolutePath().toString();
                                if (!cp.contains(abs)) {
                                    cp.add(abs);
                                    System.out.println("[WORM-DEBUG] force-added " + j);
                                }
                            }
                        }
                    }
                }
            }
            cpJoin = String.join(";", cp);
        }
        // === /DEBUG ===

        String classpath = cpJoin;

        // 3) Natives: извлекаем .dll/.so/.dylib в natives/<versionId>/.
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

        List<String> cmd = new ArrayList<>();
        cmd.add(System.getProperty("java.home") + "\\bin\\java.exe");
        cmd.add("-Xmx2G");
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-Djava.library.path=" + natives.toAbsolutePath());
        cmd.add("-Djna.tmpdir=" + natives.toAbsolutePath());
        cmd.add("-Dorg.lwjgl.system.SharedLibraryExtractPath=" + natives.toAbsolutePath());
        cmd.add("-Dio.netty.native.workdir=" + natives.toAbsolutePath());
        cmd.add("-Dminecraft.launcher.brand=worm-launcher");
        cmd.add("-Dminecraft.launcher.version=1.0.0");
        if (Files.exists(logCfg))
            cmd.add("-Dlog4j.configurationFile=" + logCfg.toAbsolutePath());

        if (session.isEly()) {
            cmd.add("-javaagent:" + authlib.toAbsolutePath() + "=" + AUTH_SERVER);
        }

        cmd.add("-cp");
        cmd.add(classpath);
        cmd.add(vj.mainClass);

        cmd.add("--username");    cmd.add(session.username());
        cmd.add("--uuid");        cmd.add(session.uuid());
        cmd.add("--accessToken"); cmd.add(session.accessToken());
        cmd.add("--clientId");    cmd.add("");
        cmd.add("--xuid");        cmd.add("");
        cmd.add("--userType");    cmd.add(session.userType());
        cmd.add("--versionType"); cmd.add("release");
        cmd.add("--version");     cmd.add(vj.id);
        cmd.add("--gameDir");     cmd.add(gameDir.toAbsolutePath().toString());
        cmd.add("--assetsDir");   cmd.add(assetsDir.toAbsolutePath().toString());
        cmd.add("--assetIndex");  cmd.add(vj.assetIndexId);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(gameDir.toFile());
        pb.inheritIO();
        return pb.start();
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