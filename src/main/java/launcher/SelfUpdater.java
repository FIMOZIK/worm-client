package launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Самообновление: детект jar + запись update.bat + запуск bat + выход.
 * Работает только если лаунчер запущен из jar (не из IDE / gradle run).
 */
public final class SelfUpdater {

    private SelfUpdater() {}

    /** Путь к jar, из которого мы запущены, или null если это не jar. */
    public static Path detectJar() {
        try {
            Path p = Path.of(SelfUpdater.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".jar")) {
                return p;
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Скачали новый jar в newJar. Подменяем текущий currentJar через update.bat
     * и перезапускаем процесс. После вызова сразу вызывающий должен System.exit(0).
     */
    public static void scheduleRestart(Path currentJar, Path newJar) throws IOException {
        Path dir = currentJar.getParent();
        Path bat = dir.resolve("update.bat");

        String javaw = System.getProperty("java.home") + "\\bin\\javaw.exe";
        String current = currentJar.toAbsolutePath().toString();
        String fresh   = newJar.toAbsolutePath().toString();

        // .bat: ждёт пока файл освободится (JVM из него выгрузится), потом swap, потом start.
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
}