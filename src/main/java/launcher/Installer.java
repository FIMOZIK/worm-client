package launcher;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class Installer {

    private Installer() {}

    public static boolean run(Path jarDir, Stage owner) {
        Stage stage = new Stage();
        if (owner != null && owner.isShowing()) {
            stage.initOwner(owner);
            stage.initModality(Modality.APPLICATION_MODAL);
        }

        Path defaultLauncher = Paths.launcherDir();
        Path defaultMc       = Paths.mcDir();

        String sysLang = System.getProperty("user.language", "ru");
        final String[] pickedLang = { Lang.EN.equalsIgnoreCase(sysLang) ? Lang.EN : Lang.RU };
        Lang.set(pickedLang[0]);

        TextField launcherField = new TextField(defaultLauncher.toAbsolutePath().toString());
        launcherField.setPrefWidth(400);
        TextField mcField = new TextField(defaultMc.toAbsolutePath().toString());
        mcField.setPrefWidth(400);

        ComboBox<String> langBox = new ComboBox<>();
        langBox.getItems().addAll("Русский", "English");
        langBox.setValue(pickedLang[0].equals(Lang.EN) ? "English" : "Русский");
        langBox.setPrefWidth(160);

        CheckBox shortcutBox = new CheckBox();
        shortcutBox.setSelected(true);

        Button pickLauncher = new Button();
        pickLauncher.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            File f = dc.showDialog(stage);
            if (f != null) launcherField.setText(f.getAbsolutePath());
        });
        Button pickMc = new Button();
        pickMc.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            File f = dc.showDialog(stage);
            if (f != null) mcField.setText(f.getAbsolutePath());
        });

        Label head = new Label();
        head.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Label sub = new Label();
        sub.setStyle("-fx-opacity: 0.7;");
        Label langLabel = new Label();
        Label launcherLabel = new Label();
        Label mcLabel = new Label();
        Label status = new Label();
        status.setStyle("-fx-opacity: 0.7;");

        Button installBtn = new Button();
        installBtn.setDefaultButton(true);
        Button cancelBtn = new Button();

        HBox langRow = new HBox(8, langLabel, langBox);
        langRow.setAlignment(Pos.CENTER_LEFT);
        HBox launcherRow = new HBox(8, launcherField, pickLauncher);
        HBox mcRow       = new HBox(8, mcField, pickMc);
        HBox.setHgrow(launcherField, Priority.ALWAYS);
        HBox.setHgrow(mcField, Priority.ALWAYS);

        Runnable relabel = () -> {
            head.setText(Lang.t("install.title"));
            sub.setText(Lang.t("install.sub"));
            langLabel.setText(Lang.t("install.lang") + ":");
            launcherLabel.setText(Lang.t("install.launcher_dir"));
            mcLabel.setText(Lang.t("install.mc_dir"));
            shortcutBox.setText(Lang.t("install.shortcut"));
            installBtn.setText(Lang.t("install.start"));
            cancelBtn.setText(Lang.t("install.cancel"));
            pickLauncher.setText(Lang.t("install.pick"));
            pickMc.setText(Lang.t("install.pick"));
            stage.setTitle(Lang.t("install.title"));
        };
        relabel.run();

        VBox panel = new VBox(10,
                head, sub,
                new Separator(),
                langRow,
                new Separator(),
                launcherLabel, launcherRow,
                mcLabel, mcRow,
                shortcutBox,
                new Separator(),
                status,
                new HBox(8, installBtn, cancelBtn)
        );
        panel.setPadding(new Insets(20));

        stage.setScene(new Scene(panel, 640, 400));
        stage.setTitle(Lang.t("install.title"));

        langBox.valueProperty().addListener((o, a, b) -> {
            pickedLang[0] = "English".equals(b) ? Lang.EN : Lang.RU;
            Lang.set(pickedLang[0]);
            relabel.run();
        });

        final boolean[] ok = {false};

        installBtn.setOnAction(e -> {
            try {
                Path lDir = Path.of(launcherField.getText().trim());
                Path mDir = Path.of(mcField.getText().trim());
                Paths.setAndSave(jarDir, lDir, mDir);
                Config.Data cfg = Config.load();
                cfg.lang = pickedLang[0];
                Config.save(cfg);
                Lang.set(pickedLang[0]);

                if (shortcutBox.isSelected()) {
                    DirectoryChooser dc = new DirectoryChooser();
                    dc.setTitle(Lang.t("shortcut.pick_title"));
                    Path desktop = defaultDesktop();
                    if (desktop != null && Files.isDirectory(desktop)) dc.setInitialDirectory(desktop.toFile());
                    File chosen = dc.showDialog(stage);
                    if (chosen != null) {
                        try {
                            Path lnk = createShortcutAt(jarDir, chosen.toPath());
                            System.out.println("[shortcut] created: " + lnk);
                        } catch (Exception ex) {
                            System.out.println("[shortcut] failed: " + ex);
                            Alert err = new Alert(Alert.AlertType.WARNING);
                            err.initOwner(stage);
                            err.setTitle(Lang.t("install.title"));
                            err.setHeaderText(Lang.t("install.shortcut_fail"));
                            err.setContentText(ex.getMessage());
                            err.showAndWait();
                        }
                    }
                }
                ok[0] = true;
                stage.close();
            } catch (Exception ex) {
                status.setText(Lang.t("install.error") + ex.getMessage());
            }
        });
        cancelBtn.setOnAction(e -> { ok[0] = false; stage.close(); });

        stage.showAndWait();
        return ok[0];
    }

    public static Path defaultDesktop() {
        try {
            String s = runPS("[Environment]::GetFolderPath('Desktop')").trim();
            if (!s.isEmpty()) return Path.of(s);
        } catch (Exception ignored) {}
        return Path.of(System.getProperty("user.home"), "Desktop");
    }

    /**
     * Создаёт .lnk на jar в указанной папке, с иконкой из worm_logo.png.
     */
    public static Path createShortcutAt(Path jarDir, Path targetDir) throws Exception {
        Path jar = findJar(jarDir);
        if (jar == null) throw new IllegalStateException("jar not found in " + jarDir);

        Path ico = prepareIco();

        String javaw = System.getProperty("java.home") + "\\bin\\javaw.exe";
        if (!Files.exists(Path.of(javaw))) {
            javaw = System.getProperty("java.home") + "\\bin\\java.exe";
        }

        if (!Files.isDirectory(targetDir)) Files.createDirectories(targetDir);
        Path lnk = targetDir.resolve("worm launcher.lnk");

        StringBuilder ps = new StringBuilder();
        ps.append("$s=(New-Object -COM WScript.Shell).CreateShortcut('")
          .append(lnk.toAbsolutePath().toString().replace("'", "''"))
          .append("');");
        ps.append("$s.TargetPath='").append(javaw.replace("'", "''")).append("';");
        ps.append("$s.Arguments='-jar \"").append(jar.toAbsolutePath().toString().replace("'", "''")).append("\"';");
        ps.append("$s.WorkingDirectory='").append(jarDir.toAbsolutePath().toString().replace("'", "''")).append("';");
        if (ico != null) {
            ps.append("$s.IconLocation='")
              .append(ico.toAbsolutePath().toString().replace("'", "''"))
              .append(",0';");
        }
        ps.append("$s.Save()");

        String out = runPS(ps.toString());
        if (!Files.exists(lnk)) throw new IllegalStateException("lnk not created: " + out);
        return lnk;
    }

    private static Path findJar(Path jarDir) throws Exception {
        try (var list = Files.list(jarDir)) {
            Optional<Path> any = list.filter(p -> p.getFileName().toString().endsWith(".jar")
                    && p.getFileName().toString().startsWith("worm-launcher")).findFirst();
            return any.orElse(null);
        }
    }

        /** PNG → ICO. Ищет PNG в classpath/jar-папке/launcherDir. Возвращает null если PNG нет. */
    private static Path prepareIco() {
        try {
            // сохраняем PNG во временный файл, чтобы был реальный путь для powershell
            Path png = extractLogoPng();
            if (png == null) return null;

            Path ico = png.resolveSibling("worm_logo.ico");
            if (Files.exists(ico)) return ico;

            String ps = String.format(
                    "Add-Type -AssemblyName System.Drawing; " +
                    "$img=[System.Drawing.Image]::FromFile('%s'); " +
                    "$bmp=New-Object System.Drawing.Bitmap 256,256; " +
                    "$g=[System.Drawing.Graphics]::FromImage($bmp); " +
                    "$g.InterpolationMode='HighQualityBicubic'; " +
                    "$g.SmoothingMode='HighQuality'; " +
                    "$g.PixelOffsetMode='HighQuality'; " +
                    "$g.DrawImage($img,0,0,256,256); " +
                    "$g.Dispose(); " +
                    "$hIcon=$bmp.GetHicon(); " +
                    "$icon=[System.Drawing.Icon]::FromHandle($hIcon); " +
                    "$fs=[System.IO.File]::Create('%s'); " +
                    "$icon.Save($fs); " +
                    "$fs.Close(); " +
                    "$icon.Dispose(); " +
                    "$img.Dispose(); $bmp.Dispose()",
                    png.toAbsolutePath().toString().replace("'", "''"),
                    ico.toAbsolutePath().toString().replace("'", "''")
            );
            runPS(ps);
            return Files.exists(ico) ? ico : null;
        } catch (Exception e) {
            System.out.println("[ico] failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Достаёт worm_logo.png в реальный файл:
     *  1) пробует classpath (внутри jar) → копирует во временный файл
     *  2) пробует рядом с jar
     *  3) пробует в папке лаунчера
     */
    private static Path extractLogoPng() throws Exception {
        // 1) classpath
        try (var in = Installer.class.getResourceAsStream("/worm_logo.png")) {
            if (in != null) {
                Path tmp = Files.createTempFile("worm_logo_", ".png");
                Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return tmp;
            }
        } catch (Exception ignored) {}

        // 2) рядом с jar
        try {
            Path jarDir = LauncherMain.detectJarDir();
            Path p = jarDir.resolve("worm_logo.png");
            if (Files.exists(p)) return p;
            Path p2 = jarDir.resolve("resources").resolve("worm_logo.png");
            if (Files.exists(p2)) return p2;
        } catch (Exception ignored) {}

        // 3) launcherDir
        try {
            Path p = Paths.launcherDir().resolve("worm_logo.png");
            if (Files.exists(p)) return p;
            Path p2 = Paths.launcherDir().resolve("resources").resolve("worm_logo.png");
            if (Files.exists(p2)) return p2;
        } catch (Exception ignored) {}

        return null;
    }

    private static String runPS(String command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-Command", command);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        byte[] bytes = p.getInputStream().readAllBytes();
        int code = p.waitFor();
        String s = new String(bytes, StandardCharsets.UTF_8);
        if (code != 0) throw new RuntimeException("powershell exit " + code + ": " + s.trim());
        return s;
    }
}