package launcher;

import javafx.application.Application;
import javafx.stage.Stage;

import java.nio.file.Path;

/**
 * Точка входа для отдельного exe-установщика "WormUninstaller".
 * Не наследуется от Application.
 */
public class UninstallerMain {

    public static void main(String[] args) {
        Path jarDir = detectJarDir();
        Paths.init(jarDir);
        Config.Data cfg = Config.load();
        Lang.set(cfg.lang);

        Application.launch(UninstallerApp.class);
    }

    private static Path detectJarDir() {
        try {
            Path p = Path.of(UninstallerMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return p.toFile().isFile() ? p.getParent() : p;
        } catch (Exception e) {
            return Path.of(".").toAbsolutePath();
        }
    }

    public static class UninstallerApp extends Application {
        @Override public void start(Stage stage) {
            Uninstaller.run(stage, detectJarDir());
            stage.show();
        }
    }
}