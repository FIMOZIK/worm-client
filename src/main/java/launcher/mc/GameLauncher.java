package launcher.mc;

import launcher.auth.ElyAuth;

import java.nio.file.*;
import java.util.*;
import java.util.function.DoubleConsumer;

public class GameLauncher {

    private static final String AUTH_SERVER = "https://account.ely.by/api/authlib-injector";
    private static final Path GAME_ROOT = Path.of("D:\\wormlauncher");

    public static Process launch(ElyAuth.Session session, String versionId) throws Exception {
        return launch(session, versionId, p -> {});
    }

    public static Process launch(ElyAuth.Session session, String versionId, DoubleConsumer progress) throws Exception {
        Path gameDir = GAME_ROOT.resolve("game");
        Path clientJar = gameDir.resolve("versions").resolve(versionId).resolve(versionId + ".jar");

        if (!Files.exists(clientJar)) {
            GameInstaller.install(versionId, gameDir, progress);
        } else {
            progress.accept(1.0);
        }

        VersionJson vj = VersionJson.parse(
                gameDir.resolve("versions").resolve(versionId).resolve(versionId + ".json"));

        Path libsRoot  = gameDir.resolve("libraries");
        Path natives   = gameDir.resolve("natives").resolve(versionId);
        Path assetsDir = gameDir.resolve("assets");
        Path logCfg    = gameDir.resolve("versions").resolve(versionId).resolve("logging.xml");
        Path authlib   = GAME_ROOT.resolve("libs").resolve("authlib-injector.jar");

        List<String> cp = new ArrayList<>();
        cp.add(clientJar.toAbsolutePath().toString());
        for (VersionJson.Library lib : vj.libraries) {
            if (!Rules.allows(lib.rules())) continue;
            if (lib.artifact() == null) continue;
            String rel = lib.artifact().path();
            if (rel == null) continue;
            if (lib.artifact().url().contains("-natives-")) continue;
            Path p = libsRoot.resolve(rel);
            if (Files.exists(p)) cp.add(p.toAbsolutePath().toString());
        }
        String classpath = String.join(";", cp);

        List<String> cmd = new ArrayList<>();
        cmd.add(System.getProperty("java.home") + "\\bin\\java.exe");
        cmd.add("-Xmx2G");
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-Djava.library.path=" + natives.toAbsolutePath());
        if (Files.exists(logCfg))
            cmd.add("-Dlog4j.configurationFile=" + logCfg.toAbsolutePath());
        cmd.add("-javaagent:" + authlib.toAbsolutePath() + "=" + AUTH_SERVER);
        cmd.add("-cp");
        cmd.add(classpath);
        cmd.add(vj.mainClass);

        cmd.add("--username");    cmd.add(session.username());
        cmd.add("--uuid");        cmd.add(session.uuid());
        cmd.add("--accessToken"); cmd.add(session.accessToken());
        cmd.add("--clientId");    cmd.add("");
        cmd.add("--xuid");        cmd.add("");
        cmd.add("--userType");    cmd.add("mojang");
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
}