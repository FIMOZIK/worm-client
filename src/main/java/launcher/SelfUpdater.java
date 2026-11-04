package launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Самообновление.
 * Поддерживает два режима:
 *   1) jar-mode: запущен `javaw -jar worm-launcher.jar`
 *      → bat пишется в папку с jar, рестарт `javaw -jar`
 *   2) exe-mode (jpackage): запущен WormLauncher.exe, jar в app/
 *      → bat пишется в корень рядом с exe, рестарт `WormLauncher.exe`
 *
 * Файл jar НЕ переименовывается при обновлении (чтобы не сломать
 * app/WormLauncher.cfg, где захардкожено имя). Меняется только содержимое.
 */
public final class SelfUpdater {

    private SelfUpdater() {}

    // ============================================================
    // DETECT JAR
    // ============================================================
    /** Путь к jar, из которого мы запущены, или null. */
    public static Path detectJar() {
        // 1) protection domain — работает и в jar-mode, и в jpackage
        try {
            Path p = Path.of(SelfUpdater.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".jar")) {
                return p;
            }
        } catch (Exception ignored) {}

        // 2) fallback: exe-mode — ищем app/worm-launcher*.jar рядом с exe
        try {
            Path exe = exeFromProcess();
            if (exe != null && exe.getParent() != null) {
                Path appDir = exe.getParent().resolve("app");
                if (Files.isDirectory(appDir)) {
                    try (var s = Files.list(appDir)) {
                        return s.filter(p -> {
                                    String n = p.getFileName().toString().toLowerCase();
                                    return n.endsWith(".jar") && n.startsWith("worm-launcher");
                                })
                                .findFirst()
                                .orElse(null);
                    }
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    // ============================================================
    // SCHEDULE RESTART
    // ============================================================
    /**
     * Скачали новый jar в newJar. Подменяем текущий currentJar через update.bat
     * и перезапускаем процесс. После вызова сразу вызывающий должен System.exit(0).
     */
    public static void scheduleRestart(Path currentJar, Path newJar) throws IOException {
        Path exe = exeFromProcess();
        if (exe != null) {
            System.out.println("[SelfUpdater] exe mode: " + exe);
            scheduleExeRestart(currentJar, newJar, exe);
        } else {
            System.out.println("[SelfUpdater] jar mode");
            scheduleJarRestart(currentJar, newJar);
        }
    }

    // ============================================================
    // JAR MODE
    // ============================================================
    private static void scheduleJarRestart(Path currentJar, Path newJar) throws IOException {
        Path dir = currentJar.getParent();
        if (dir == null) throw new IOException("currentJar has no parent: " + currentJar);
        Path bat = dir.resolve("update.bat");

        String javaw = System.getProperty("java.home") + "\\bin\\javaw.exe";
        String current = currentJar.toAbsolutePath().toString();
        String fresh   = newJar.toAbsolutePath().toString();

        String content = String.join("\r\n",
                "@echo off",
                "chcp 65001 >nul",
                ":wait",
                "del /F /Q \"" + current + "\" 2>nul",
                "if exist \"" + current + "\" (",
                "  timeout /t 1 /nobreak >nul",
                "  goto wait",
                ")",
                "move /Y \"" + fresh + "\" \"" + current + "\" >nul",
                "start \"\" \"" + javaw + "\" -jar \"" + current + "\"",
                "del /F /Q \"%~f0\"",
                ""
        );
        Files.writeString(bat, content, StandardCharsets.UTF_8);

        new ProcessBuilder("cmd.exe", "/c", "start", "\"\"", bat.toAbsolutePath().toString())
                .directory(dir.toFile())
                .start();
    }

    // ============================================================
    // EXE MODE (jpackage)
    // ============================================================
    private static void scheduleExeRestart(Path currentJar, Path newJar, Path exe) throws IOException {
        Path exeDir = exe.getParent();
        if (exeDir == null) throw new IOException("exe has no parent: " + exe);
        Path bat = exeDir.resolve("update.bat");

        String current = currentJar.toAbsolutePath().toString();
        String fresh   = newJar.toAbsolutePath().toString();
        String exeStr  = exe.toAbsolutePath().toString();

        // start "" "C:\path\WormLauncher.exe"
        // exe сам находит app/ и runtime/ рядом с собой
        String content = String.join("\r\n",
                "@echo off",
                "chcp 65001 >nul",
                ":wait",
                "del /F /Q \"" + current + "\" 2>nul",
                "if exist \"" + current + "\" (",
                "  timeout /t 1 /nobreak >nul",
                "  goto wait",
                ")",
                "move /Y \"" + fresh + "\" \"" + current + "\" >nul",
                "start \"\" \"" + exeStr + "\"",
                "del /F /Q \"%~f0\"",
                ""
        );
        Files.writeString(bat, content, StandardCharsets.UTF_8);

        new ProcessBuilder("cmd.exe", "/c", "start", "\"\"", bat.toAbsolutePath().toString())
                .directory(exeDir.toFile())
                .start();
    }

    // ============================================================
    // EXE DETECTION
    // ============================================================
    private static Path exeFromProcess() {
        // 1) ProcessHandle — самый надёжный
        try {
            Optional<Path> opt = ProcessHandle.current().info().command()
                    .map(Path::of)
                    .filter(Files::exists);
            if (opt.isPresent()) {
                Path p = opt.get();
                String name = p.getFileName().toString().toLowerCase();
                if (name.endsWith(".exe")
                        && !name.equals("java.exe")
                        && !name.equals("javaw.exe")
                        && !name.equals("javaws.exe")
                        && !name.equals("javafx.exe")) {
                    return p;
                }
            }
        } catch (Exception ignored) {}

        // 2) fallback: exe рядом с app/ (jpackage convention)
        try {
            Path jar = Path.of(SelfUpdater.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(jar)) {
                Path appDir = jar.getParent();
                if (appDir != null && "app".equalsIgnoreCase(appDir.getFileName().toString())) {
                    Path root = appDir.getParent();
                    if (root != null) {
                        try (var s = Files.list(root)) {
                            return s.filter(p -> {
                                        String n = p.getFileName().toString().toLowerCase();
                                        return n.endsWith(".exe")
                                                && !n.equals("java.exe")
                                                && !n.equals("javaw.exe");
                                    })
                                    .findFirst()
                                    .orElse(null);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return null;
    }
}