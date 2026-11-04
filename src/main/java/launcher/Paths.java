package launcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Пути установки лаунчера.
 *
 * Основное хранилище install.json:  %USERPROFILE%\.wormlauncher\install.json
 *   — стабильное, не сносится clean/build, не зависит от cwd.
 *
 * При старте пробуем в порядке:
 *   1) %USERPROFILE%\.wormlauncher\install.json
 *   2) jarDir/install.json  (legacy, миграция в 1)
 *   3) автовосстановление из D:\wormlauncher\config.json или game/
 *   4) не установлен → установщик
 */
public final class Paths {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DEFAULT_LAUNCHER = Path.of("D:\\wormlauncher");
    private static final Path DEFAULT_MC       = Path.of("D:\\wormlauncher\\game");

    private static Path launcherDir = DEFAULT_LAUNCHER;
    private static Path mcDir       = DEFAULT_MC;
    private static Path installFile = null;

    private Paths() {}

    public static Path stableInstallFile() {
        Path home = Path.of(System.getProperty("user.home", "."));
        return home.resolve(".wormlauncher").resolve("install.json");
    }

    /** Вызывать ровно один раз при старте — до Config.load() и TokenStore.load(). */
    public static void init(Path jarDir) {
        Path stable = stableInstallFile();

        // 1) стабильное место
        if (Files.exists(stable) && readAndApply(stable)) {
            installFile = stable;
            return;
        }

        // 2) legacy рядом с jar → мигрируем в stable
        if (jarDir != null) {
            Path legacy = jarDir.resolve("install.json");
            if (Files.exists(legacy) && readAndApply(legacy)) {
                installFile = legacy;
                try {
                    Files.createDirectories(stable.getParent());
                    Files.copy(legacy, stable, StandardCopyOption.REPLACE_EXISTING);
                    installFile = stable;
                } catch (Exception ignored) {}
                return;
            }
        }

        // 3) восстановление из дефолта
        if (recoverFromDefaults(stable)) {
            installFile = stable;
            return;
        }

        // 4) реально не установлен
        installFile = null;
    }

    private static boolean readAndApply(Path file) {
        try {
            String s = Files.readString(file, StandardCharsets.UTF_8);
            InstallJson j = GSON.fromJson(s, InstallJson.class);
            if (j == null) return false;
            if (j.launcherDir != null && !j.launcherDir.isBlank())
                launcherDir = Path.of(j.launcherDir);
            if (j.mcDir != null && !j.mcDir.isBlank())
                mcDir = Path.of(j.mcDir);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean recoverFromDefaults(Path stable) {
        Path home = Path.of(System.getProperty("user.home", "."));
        Path[] markers = {
                DEFAULT_LAUNCHER.resolve("config.json"),
                DEFAULT_LAUNCHER.resolve("game"),
                home.resolve(".wormlauncher").resolve("config.json")
        };
        for (Path m : markers) {
            if (Files.exists(m)) {
                launcherDir = DEFAULT_LAUNCHER;
                mcDir = DEFAULT_MC;
                try {
                    Files.createDirectories(stable.getParent());
                    InstallJson j = new InstallJson();
                    j.launcherDir = launcherDir.toAbsolutePath().toString();
                    j.mcDir = mcDir.toAbsolutePath().toString();
                    Files.writeString(stable, GSON.toJson(j), StandardCharsets.UTF_8);
                } catch (Exception ignored) {}
                return true;
            }
        }
        return false;
    }

    public static void setAndSave(Path jarDir, Path newLauncherDir, Path newMcDir) throws Exception {
        launcherDir = newLauncherDir;
        mcDir = newMcDir;

        Path stable = stableInstallFile();
        Files.createDirectories(stable.getParent());
        InstallJson j = new InstallJson();
        j.launcherDir = launcherDir.toAbsolutePath().toString();
        j.mcDir = mcDir.toAbsolutePath().toString();
        Files.createDirectories(launcherDir);
        Files.createDirectories(mcDir);
        Files.writeString(stable, GSON.toJson(j), StandardCharsets.UTF_8);
        installFile = stable;

        // legacy cleanup: старый jarDir/install.json больше не нужен
        if (jarDir != null) {
            Path legacy = jarDir.resolve("install.json");
            try { Files.deleteIfExists(legacy); } catch (Exception ignored) {}
        }
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