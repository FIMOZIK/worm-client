package launcher;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;

public class LauncherMain {

    public static void main(String[] args) {
        Path installDir = detectInstallDir();
        Paths.init(installDir);

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

    public static Path detectInstallDir() {
        Path jarDir = detectJarDir();
        Path parent = jarDir.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("runtime"))) {
            return parent;
        }
        return jarDir;
    }

    public static class CombinedApp extends Application {
        private LauncherApp launcher;

        @Override
        public void start(Stage primary) {
            // installDir уже применён через Paths.init в main().
            // Здесь проверяем только isInstalled() (он смотрит в user.home).
            if (!Paths.isInstalled()) {
                Path installDir = detectInstallDir();
                boolean installed = Installer.run(installDir, primary);
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