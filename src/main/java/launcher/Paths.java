package launcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Пути установки лаунчера. Читаются из install.json рядом с jar:
 *   { "launcherDir": "D:\\wormlauncher", "mcDir": "D:\\wormlauncher\\game" }
 * Если install.json нет — используются дефолтные пути D:\wormlauncher.
 */
public final class Paths {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DEFAULT_LAUNCHER = Path.of("D:\\wormlauncher");
    private static final Path DEFAULT_MC       = Path.of("D:\\wormlauncher\\game");

    private static Path launcherDir = DEFAULT_LAUNCHER;
    private static Path mcDir       = DEFAULT_MC;
    private static Path installFile = null;

    private Paths() {}

    /** Вызывать ровно один раз при старте — до Config.load() и TokenStore.load(). */
    public static void init(Path jarDir) {
        installFile = jarDir.resolve("install.json");
        if (!Files.exists(installFile)) return;
        try {
            String s = Files.readString(installFile, StandardCharsets.UTF_8);
            InstallJson j = GSON.fromJson(s, InstallJson.class);
            if (j == null) return;
            if (j.launcherDir != null && !j.launcherDir.isBlank())
                launcherDir = Path.of(j.launcherDir);
            if (j.mcDir != null && !j.mcDir.isBlank())
                mcDir = Path.of(j.mcDir);
        } catch (Exception ignored) {}
    }

    public static void setAndSave(Path jarDir, Path newLauncherDir, Path newMcDir) throws Exception {
        launcherDir = newLauncherDir;
        mcDir = newMcDir;
        installFile = jarDir.resolve("install.json");
        InstallJson j = new InstallJson();
        j.launcherDir = launcherDir.toAbsolutePath().toString();
        j.mcDir = mcDir.toAbsolutePath().toString();
        Files.createDirectories(launcherDir);
        Files.createDirectories(mcDir);
        Files.writeString(installFile, GSON.toJson(j), StandardCharsets.UTF_8);
    }

    public static boolean isInstalled() {
        return installFile != null && Files.exists(installFile);
    }

    public static Path launcherDir() { return launcherDir; }
    public static Path mcDir()       { return mcDir; }
    public static Path configFile()  { return launcherDir.resolve("config.json"); }
    public static Path sessionFile() { return launcherDir.resolve("session.json"); }
    public static Path authlib()     { return launcherDir.resolve("libs").resolve("authlib-injector.jar"); }

    private static class InstallJson {
        String launcherDir;
        String mcDir;
    }
}