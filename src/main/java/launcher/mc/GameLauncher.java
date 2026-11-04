package launcher.mc;

import launcher.auth.GameSession;
import launcher.mc.jre.JavaRuntimeInstaller;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.DoubleConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class GameLauncher {

    private static final String AUTH_SERVER = "https://account.ely.by/api/authlib-injector";
    private static final String LAUNCHER_VERSION = "1.0.9";
    private static final int DEFAULT_MEMORY_MB = 4096;

    public record LaunchOptions(Integer memoryMb, List<String> extraJvmArgs,
                                String serverHost, Integer serverPort) {
        public LaunchOptions(Integer memoryMb, List<String> extraJvmArgs) {
            this(memoryMb, extraJvmArgs, null, null);
        }
    }

    public static Process launch(GameSession session, String versionId) throws Exception {
        return launch(session, versionId, null, null, p -> {});
    }

    public static Process launch(GameSession session, String versionId, DoubleConsumer progress) throws Exception {
        return launch(session, versionId, null, null, progress);
    }

    public static Process launch(GameSession session, String versionId, Path runDirOverride,
                                 DoubleConsumer progress) throws Exception {
        return launch(session, versionId, runDirOverride, null, progress);
    }

    public static Process launch(GameSession session, String versionId, Path runDirOverride,
                                 LaunchOptions opts, DoubleConsumer progress) throws Exception {

        Path gameDir = launcher.Paths.mcDir();
        Path runDir  = runDirOverride != null ? runDirOverride : gameDir;
        Files.createDirectories(runDir);

        VersionJson vj = VersionJson.load(gameDir, versionId);

        String javaExe = ensureJava(vj.javaVersionMajor, pct -> progress.accept(pct * 0.4));
        progress.accept(0.4);

        Path clientJar = gameDir.resolve("versions").resolve(vj.jarVersionId)
                .resolve(vj.jarVersionId + ".jar");

        if (!Files.exists(clientJar)) {
            GameInstaller.install(vj.jarVersionId, gameDir,
                    pct -> progress.accept(0.4 + pct * 0.6));
        } else {
            progress.accept(1.0);
        }

        Path libsRoot  = gameDir.resolve("libraries");
        Path natives   = gameDir.resolve("natives").resolve(vj.jarVersionId);
        Path assetsDir = gameDir.resolve("assets");
        Path logCfg    = gameDir.resolve("versions").resolve(vj.jarVersionId).resolve("logging.xml");
        Path authlib   = launcher.Paths.authlib();

        for (VersionJson.Library lib : vj.libraries) {
            if (!lib.rules().isEmpty() && !Rules.allows(lib.rules())) continue;
            if (lib.artifact() == null) continue;
            String rel = lib.artifact().path();
            if (rel == null) continue;
            Path p = libsRoot.resolve(rel);
            if (!Files.exists(p)) {
                try {
                    Files.createDirectories(p.getParent());
                    Downloader.download(lib.artifact().url(), p, lib.artifact().sha1());
                } catch (Exception e) {
                    System.out.println("[GameLauncher] lib download skipped: " + rel + " — " + e.getMessage());
                }
            }
        }

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

        // ============================================================
        // CLASSPATH
        // ============================================================
List<String> cp = new ArrayList<>();

// для launchwrapper (OptiFine) own jar ДОЛЖЕН быть первым в classpath,
// иначе Launch не находит manifest с TweakClass
boolean launchwrapperMode = vj.mainClass.toLowerCase().contains("launchwrapper");

Path ownJar = gameDir.resolve("versions").resolve(vj.id).resolve(vj.id + ".jar");
boolean hasOwnJar = !ownJar.toAbsolutePath().equals(clientJar.toAbsolutePath()) && Files.exists(ownJar);

if (launchwrapperMode && hasOwnJar) {
    // own jar первым — Launch читает TweakClass из его manifest
    cp.add(ownJar.toAbsolutePath().toString());
    cp.add(clientJar.toAbsolutePath().toString());
    System.out.println("[GameLauncher] own jar (FIRST): " + ownJar.getFileName());
} else {
    cp.add(clientJar.toAbsolutePath().toString());
    if (hasOwnJar) {
        cp.add(ownJar.toAbsolutePath().toString());
        System.out.println("[GameLauncher] own jar: " + ownJar.getFileName());
    }
}

        for (VersionJson.Library lib : vj.libraries) {
            if (!lib.rules().isEmpty() && !Rules.allows(lib.rules())) continue;
            if (lib.artifact() == null) continue;
            String rel = lib.artifact().path();
            if (rel == null) continue;
            Path p = libsRoot.resolve(rel);
            if (!Files.exists(p)) continue;
            if (isNative(lib) && !nativesAllowedForArch(lib.name())) continue;
            cp.add(p.toAbsolutePath().toString());
        }

        addOptifineLibs(cp, libsRoot.resolve("optifine"));
        addOptifineLibs(cp, libsRoot.resolve("net").resolve("minecraft").resolve("launchwrapper"));
        addOptifineLibs(cp, libsRoot.resolve("net").resolve("optifine"));

        boolean hasLaunchwrapper = cp.stream().anyMatch(s -> s.toLowerCase().contains("launchwrapper"));
        if (!hasLaunchwrapper && vj.mainClass.toLowerCase().contains("launchwrapper")) {
            System.out.println("[GameLauncher] launchwrapper not in game, checking .minecraft ...");
            String appdata = System.getenv("APPDATA");
            if (appdata != null) {
                Path dotMcLibs = Path.of(appdata).resolve(".minecraft").resolve("libraries");
                addOptifineLibs(cp, dotMcLibs.resolve("optifine"));
                addOptifineLibs(cp, dotMcLibs.resolve("net").resolve("minecraft").resolve("launchwrapper"));
                addOptifineLibs(cp, dotMcLibs.resolve("net").resolve("optifine"));
            }
        }

        String classpath = String.join(";", cp);

        boolean isMc26Plus = vj.javaVersionMajor >= 25;
        boolean isLaunchwrapper = vj.mainClass.toLowerCase().contains("launchwrapper");

        int memMb = DEFAULT_MEMORY_MB;
        if (opts != null && opts.memoryMb() != null && opts.memoryMb() > 0) {
            memMb = opts.memoryMb();
        }

        System.out.println("[GameLauncher] java: " + javaExe);
        System.out.println("[GameLauncher] memory: -Xmx" + memMb + "M");
        System.out.println("[GameLauncher] mainClass: " + vj.mainClass);
        System.out.println("[GameLauncher] launchwrapper mode: " + isLaunchwrapper);

        List<String> cmd = new ArrayList<>();
        cmd.add(javaExe);
        cmd.add("-Xmx" + memMb + "M");
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-Djava.library.path=" + natives.toAbsolutePath());
        cmd.add("-Djna.tmpdir=" + natives.toAbsolutePath());
        cmd.add("-Dio.netty.native.workdir=" + natives.toAbsolutePath());
        cmd.add("-Dminecraft.launcher.brand=worm-launcher");
        cmd.add("-Dminecraft.launcher.version=" + LAUNCHER_VERSION);
        if (Files.exists(logCfg))
            cmd.add("-Dlog4j.configurationFile=" + logCfg.toAbsolutePath());

        if (opts != null && opts.extraJvmArgs() != null) {
            for (String a : opts.extraJvmArgs()) {
                if (a != null && !a.isBlank()) cmd.add(a);
            }
        }

        // ---- authlib: ОТКЛЮЧАЕМ для launchwrapper (optifine) ----
        if (session.isEly() && !isLaunchwrapper && !isMc26Plus) {
            cmd.add("-javaagent:" + authlib.toAbsolutePath() + "=" + AUTH_SERVER);
            System.out.println("[GameLauncher] authlib: ON");
        } else if (session.isEly()) {
            System.out.println("[GameLauncher] authlib: OFF (launchwrapper / mc26+)");
        }

        cmd.add("-cp");
        cmd.add(classpath);
        cmd.add(vj.mainClass);

        cmd.add("--username");    cmd.add(session.username());
        cmd.add("--uuid");        cmd.add(session.uuid());
        cmd.add("--accessToken"); cmd.add(session.accessToken());
        cmd.add("--clientId");    cmd.add("");
        cmd.add("--xuid");        cmd.add("");
        if (!isMc26Plus) {
            cmd.add("--userType"); cmd.add(session.userType());
        }
cmd.add("--versionType"); cmd.add("release");
cmd.add("--version");     cmd.add(vj.id);
cmd.add("--gameDir");     cmd.add(runDir.toAbsolutePath().toString());
cmd.add("--assetsDir");   cmd.add(assetsDir.toAbsolutePath().toString());
cmd.add("--assetIndex");  cmd.add(vj.assetIndexId);

        // auto-connect to server
        if (opts != null && opts.serverHost() != null && !opts.serverHost().isBlank()) {
            cmd.add("--server"); cmd.add(opts.serverHost());
            if (opts.serverPort() != null && opts.serverPort() > 0) {
                cmd.add("--port"); cmd.add(String.valueOf(opts.serverPort()));
            }
            System.out.println("[GameLauncher] auto-connect: " + opts.serverHost()
                    + ":" + (opts.serverPort() != null ? opts.serverPort() : 25565));
        }

// для launchwrapper передаём --tweakClass (иначе Launch берёт VanillaTweaker)
if (launchwrapperMode) {
    // OptiFine всегда регистрирует optifine.OptiFineTweaker в своём manifest,
    // но если own jar не первый — Launch его не находит. Дополнительно явно указываем.
    cmd.add("--tweakClass");
    cmd.add("optifine.OptiFineTweaker");
    System.out.println("[GameLauncher] added --tweakClass optifine.OptiFineTweaker");
}

        // ---- redirect MC output to file ----
        Path mcLog = runDir.resolve("worm-mc-latest.log");
        try { Files.deleteIfExists(mcLog); } catch (Exception ignored) {}
        File mcLogFile = mcLog.toFile();

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(runDir.toFile());
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(mcLogFile));

        System.out.println("[GameLauncher] cmd: " + String.join(" ", cmd));
        System.out.println("[GameLauncher] MC log: " + mcLog);

        Process p = pb.start();

        // логгируем exit code в отдельном потоке (не блокируя)
        final Path finalMcLog = mcLog;
        new Thread(() -> {
            try {
                int code = p.waitFor();
                System.out.println("[GameLauncher] MC exit code: " + code + " (log: " + finalMcLog + ")");
                if (code != 0) {
                    try {
                        List<String> lines = Files.readAllLines(finalMcLog);
                        int from = Math.max(0, lines.size() - 60);
                        System.out.println("------ MC log tail (" + lines.size() + " lines) ------");
                        for (int i = from; i < lines.size(); i++) {
                            System.out.println("  " + lines.get(i));
                        }
                        System.out.println("------ end of MC log tail ------");
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }, "mc-exit-watcher").start();

        return p;
    }

    private static void addOptifineLibs(List<String> cp, Path dir) {
        if (!Files.isDirectory(dir)) return;
        try (var s = Files.walk(dir, 3)) {
            s.filter(p -> p.toString().toLowerCase().endsWith(".jar"))
                    .filter(Files::isRegularFile)
                    .forEach(p -> {
                        String path = p.toAbsolutePath().toString();
                        if (!cp.contains(path)) {
                            cp.add(path);
                            System.out.println("[GameLauncher] +lib: " + p.getFileName());
                        }
                    });
        } catch (Exception ignored) {}
    }

    private static String ensureJava(int wantedMajor, DoubleConsumer progress) throws Exception {
        try {
            return findJava(wantedMajor);
        } catch (Exception e) {
            System.out.println("[GameLauncher] java " + wantedMajor + " не найден — качаю runtime...");
            Path runtimeDir = launcher.Paths.mcDir().resolve("runtime");
            Path exe = JavaRuntimeInstaller.ensureRuntime(wantedMajor, runtimeDir, progress);
            return exe.toString();
        }
    }

    private static String findJava(int wantedMajor) throws Exception {
        Path runtimeExe = JavaRuntimeInstaller.findJavaExe(
                launcher.Paths.mcDir().resolve("runtime").resolve(String.valueOf(wantedMajor)));
        if (runtimeExe != null) return runtimeExe.toString();

        try {
            int cur = parseMajor(System.getProperty("java.version", "0"));
            if (cur == wantedMajor) {
                String curExe = System.getProperty("java.home") + "\\bin\\java.exe";
                if (Files.exists(Path.of(curExe))) return curExe;
            }
        } catch (Exception ignored) {}

        String userHome = System.getProperty("user.home", "");
        String[] candidates = {
                "C:\\Program Files\\Java\\jdk-" + wantedMajor,
                "C:\\Program Files\\Java\\jdk-" + wantedMajor + ".0",
                "C:\\Program Files\\Eclipse Adoptium\\jdk-" + wantedMajor,
                userHome + "\\AppData\\Local\\Programs\\Eclipse Adoptium\\jdk-" + wantedMajor,
                "C:\\Program Files\\Microsoft\\jdk-" + wantedMajor,
                "C:\\Program Files\\Zulu\\zulu-" + wantedMajor,
        };

        for (String base : candidates) {
            try {
                Path dir = Path.of(base);
                if (Files.isDirectory(dir) && Files.exists(dir.resolve("bin").resolve("java.exe"))) {
                    return dir.resolve("bin").resolve("java.exe").toString();
                }
                Path parent = dir.getParent();
                if (parent != null && Files.isDirectory(parent)) {
                    try (var s = Files.list(parent)) {
                        var hit = s.filter(p -> p.getFileName().toString()
                                        .startsWith(dir.getFileName().toString()))
                                .sorted()
                                .findFirst();
                        if (hit.isPresent()) {
                            Path exe = hit.get().resolve("bin").resolve("java.exe");
                            if (Files.exists(exe)) return exe.toString();
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        throw new IllegalStateException("java " + wantedMajor + " not found");
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

    private static int parseMajor(String ver) {
        if (ver == null) return 0;
        String[] p = ver.split("\\.");
        try {
            int first = Integer.parseInt(p[0]);
            if (first == 1 && p.length > 1) return Integer.parseInt(p[1]);
            return first;
        } catch (Exception e) { return 0; }
    }
}