package launcher;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.nio.file.Path;

public class LauncherMain {

    public static void main(String[] args) {
        boolean uninstallMode = false;
        for (String a : args) if ("--uninstall".equals(a)) { uninstallMode = true; break; }

        if (uninstallMode) {
            Application.launch(UninstallerMain.UninstallerApp.class);
            return;
        }
        Application.launch(CombinedApp.class, args);
    }

    public static Path detectJarDir() {
        try {
            Path p = Path.of(LauncherMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return p.toFile().isFile() ? p.getParent() : p;
        } catch (Exception e) {
            return Path.of(".").toAbsolutePath();
        }
    }

    /**
     * Один Application, который сначала показывает окно установки (если нужно),
     * затем передаёт управление LauncherApp на тот же primary Stage.
     */
    public static class CombinedApp extends Application {

        private LauncherApp launcher;

        @Override
        public void start(Stage primary) {
            Path jarDir = detectJarDir();
            Paths.init(jarDir);

            if (!Paths.isInstalled()) {
                boolean installed = Installer.run(jarDir, primary);
                if (!installed) {
                    Platform.exit();
                    return;
                }
            }

            Config.Data cfg = Config.load();
            Lang.set(cfg.lang);

            launcher = new LauncherApp();
            launcher.start(primary);
        }

        @Override
        public void stop() {
            if (launcher != null) launcher.stop();
        }
    }
}