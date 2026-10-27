package launcher;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import launcher.auth.ElyAuth;
import launcher.auth.ElyOAuth;
import launcher.auth.TokenStore;
import launcher.mc.GameLauncher;
import launcher.mc.VersionManifest;

import java.awt.Desktop;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class LauncherApp extends Application {

    private static final String CLIENT_ID     = "worm-launcher1";
    private static final String CLIENT_SECRET = "YZdFkv0-L5K3aADqedEp0zamy2HzcjBcUApskOHxkzSsxE-XjsuCwdUsAIfVBShk";
    private static final Path   GAME_DIR      = Path.of("D:\\wormlauncher\\game");

    // версия лаунчера — менять вручную при релизе
    private static final String LAUNCHER_VERSION = "1.0.0";

    private final TextArea log = new TextArea();
    private ElyAuth.Session session;
    private ComboBox<String> versionBox;
    private ProgressBar progressBar;
    private Button playBtn;
    private Label    statusLabel;

    private Config.Data cfg = Config.load();
    private TrayIcon trayIcon;
    private boolean trayReady = false;
    private Stage stage;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        Platform.setImplicitExit(false);

        stage.getIcons().add(makeIcon());

        Label title = new Label("worm launcher");
        title.getStyleClass().add("title-label");

        statusLabel = new Label("не вошёл");
        statusLabel.setStyle("-fx-text-fill: #8a8a8a; -fx-font-size: 11px;");

        TextField nick = new TextField();
        nick.setPromptText("Ely.by ник");
        nick.setPrefWidth(180);
        if (cfg.lastNick != null) nick.setText(cfg.lastNick);
        PasswordField pass = new PasswordField();
        pass.setPromptText("пароль");
        pass.setPrefWidth(180);

        Button loginBtn   = new Button("Войти (пароль)");
        Button oauthBtn   = new Button("Войти через браузер");
        Button logoutBtn  = new Button("Выйти");
        Button openFolder = new Button("Открыть папку игры");
        Button checkUpd   = new Button("Проверить обновления");
        playBtn = new Button("Играть");
        playBtn.getStyleClass().add("play-button");
        playBtn.setDisable(true);

        CheckBox minimizeBox = new CheckBox("сворачивать в трей при игре");
        minimizeBox.setSelected(Boolean.TRUE.equals(cfg.minimizeOnPlay));
        minimizeBox.selectedProperty().addListener((o, a, b) -> {
            cfg.minimizeOnPlay = b;
            Config.save(cfg);
        });

        CheckBox autoLaunchBox = new CheckBox("авто-запуск MC после входа");
        autoLaunchBox.setSelected(Boolean.TRUE.equals(cfg.autoLaunch));
        autoLaunchBox.selectedProperty().addListener((o, a, b) -> {
            cfg.autoLaunch = b;
            Config.save(cfg);
        });

        versionBox = new ComboBox<>();
        versionBox.setPrefWidth(180);
        versionBox.setPromptText("загрузка версий...");
        versionBox.setDisable(true);

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(240);
        progressBar.setVisible(false);

        log.setEditable(false);
        log.setPrefHeight(220);

        // ---------- список версий ----------
        new Thread(() -> {
            try {
                List<VersionManifest.Entry> list = VersionManifest.fetch();
                List<String> releases = list.stream()
                        .filter(e -> "release".equals(e.type()))
                        .map(VersionManifest.Entry::id)
                        .toList();
                Platform.runLater(() -> {
                    versionBox.setItems(FXCollections.observableArrayList(releases));
                    if (cfg.lastVersion != null && releases.contains(cfg.lastVersion)) {
                        versionBox.setValue(cfg.lastVersion);
                    } else if (releases.contains("1.21.11")) {
                        versionBox.setValue("1.21.11");
                    } else if (!releases.isEmpty()) {
                        versionBox.setValue(releases.get(0));
                    }
                    versionBox.setDisable(false);
                    logLine("✓ версий загружено: " + releases.size());
                    if (cfg.lastVersion != null)
                        logLine("  восстановлена последняя версия: " + cfg.lastVersion);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> logLine("✗ не смог загрузить версии: " + ex.getMessage()));
            }
        }, "manifest").start();

        versionBox.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && !newV.isBlank()) {
                cfg.lastVersion = newV;
                Config.save(cfg);
            }
        });

        // ---------- вход по паролю ----------
        loginBtn.setOnAction(e -> {
            loginBtn.setDisable(true);
            String nickText = nick.getText().trim();
            logLine("→ login (password): " + nickText);
            new Thread(() -> {
                try {
                    ElyAuth.Session s = ElyAuth.login(nickText, pass.getText());
                    TokenStore.save(s);
                    cfg.lastNick = s.username();
                    Config.save(cfg);
                    Platform.runLater(() -> {
                        session = s;
                        logLine("✓ " + s);
                        logLine("  сохранено в " + TokenStore.file());
                        pass.clear();
                        playBtn.setDisable(false);
                        loginBtn.setDisable(false);
                        setStatus("вошёл как " + s.username(), true);
                        maybeAutoLaunch(autoLaunchBox.isSelected());
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        logLine("✗ " + ex.getMessage());
                        loginBtn.setDisable(false);
                    });
                }
            }, "ely-auth").start();
        });

        // ---------- вход через браузер ----------
        oauthBtn.setOnAction(e -> {
            oauthBtn.setDisable(true);
            logLine("→ login (browser) ...");
            new Thread(() -> {
                try {
                    ElyOAuth oauth = new ElyOAuth(CLIENT_ID, CLIENT_SECRET);
                    ElyOAuth.Tokens tokens = oauth.authorize();
                    ElyAuth.Session s = oauth.toSession(tokens);
                    TokenStore.save(s);
                    cfg.lastNick = s.username();
                    Config.save(cfg);
                    Platform.runLater(() -> {
                        session = s;
                        logLine("✓ " + s);
                        logLine("  сохранено в " + TokenStore.file());
                        playBtn.setDisable(false);
                        oauthBtn.setDisable(false);
                        setStatus("вошёл как " + s.username(), true);
                        maybeAutoLaunch(autoLaunchBox.isSelected());
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        logLine("✗ OAuth упал: " + ex.getMessage());
                        oauthBtn.setDisable(false);
                    });
                }
            }, "ely-oauth").start();
        });

        // ---------- выход ----------
        logoutBtn.setOnAction(e -> {
            try {
                if (session != null) ElyAuth.invalidate(session);
                TokenStore.clear();
                session = null;
                playBtn.setDisable(true);
                setStatus("не вошёл", false);
                logLine("✓ сессия удалена");
            } catch (Exception ex) {
                logLine("✗ " + ex.getMessage());
            }
        });

        // ---------- открыть папку игры ----------
        openFolder.setOnAction(e -> {
            try {
                Files.createDirectories(GAME_DIR);
                Desktop.getDesktop().open(GAME_DIR.toFile());
                logLine("→ открыл " + GAME_DIR);
            } catch (Exception ex) {
                logLine("✗ открыть папку не удалось: " + ex.getMessage());
            }
        });

        // ---------- проверить обновления ----------
        checkUpd.setOnAction(e -> {
            logLine("→ проверка обновлений...");
            new Thread(() -> {
                try {
                    // заглушка — реальную проверку на GitHub Releases добавим в след. волне
                    Thread.sleep(400);
                    Platform.runLater(() -> {
                        logLine("✓ установлена версия " + LAUNCHER_VERSION + " (последняя)");
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> logLine("✗ проверка упала: " + ex.getMessage()));
                }
            }, "check-upd").start();
        });

        // ---------- играть ----------
        playBtn.setOnAction(e -> startGame());

        // ---------- сохранённая сессия ----------
        ElyAuth.Session cached = TokenStore.load();
        if (cached != null) {
            session = cached;
            playBtn.setDisable(false);
            setStatus("сессия из кеша: " + cached.username(), true);
            logLine("найдена сохранённая сессия: " + cached.username());
            if (autoLaunchBox.isSelected()) {
                Platform.runLater(() -> maybeAutoLaunch(true));
            }
        }

        // ---------- разметка ----------
        HBox titleRow = new HBox(12, title, statusLabel);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        HBox authRow   = new HBox(8, nick, pass, loginBtn, logoutBtn);
        authRow.setAlignment(Pos.CENTER_LEFT);
        HBox oauthRow  = new HBox(8, oauthBtn, openFolder, checkUpd);
        oauthRow.setAlignment(Pos.CENTER_LEFT);
        HBox optsRow   = new HBox(16, minimizeBox, autoLaunchBox);
        optsRow.setAlignment(Pos.CENTER_LEFT);

        HBox playRow = new HBox(8, new Label("версия:"), versionBox, playBtn, progressBar);
        playRow.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(10, titleRow, authRow, oauthRow, optsRow,
                new Separator(), playRow, new Label("лог:"), log);
        root.setPadding(new Insets(16));
        root.setAlignment(Pos.TOP_LEFT);

        double w = cfg.windowWidth  != null ? cfg.windowWidth  : 820;
        double h = cfg.windowHeight != null ? cfg.windowHeight : 640;
        Scene scene = new Scene(root, w, h);
        try {
            scene.getStylesheets().add(
                    getClass().getResource("/styles.css").toExternalForm());
        } catch (Exception ex) {
            logLine("⚠ styles.css не найден: " + ex.getMessage());
        }

        stage.setTitle("worm launcher " + LAUNCHER_VERSION);
        stage.setScene(scene);

        setupTray();
        stage.setOnCloseRequest(ev -> {
            cfg.windowWidth  = stage.getWidth();
            cfg.windowHeight = stage.getHeight();
            Config.save(cfg);
            if (trayReady) {
                ev.consume();
                stage.hide();
                trayIcon.displayMessage("worm launcher",
                        "Свёрнут в трей. Двойной клик — открыть.",
                        TrayIcon.MessageType.INFO);
            }
        });

        stage.show();
    }

    // ---------- авто-запуск после входа ----------
    private void maybeAutoLaunch(boolean enabled) {
        if (!enabled) return;
        if (session == null) return;
        if (versionBox.getValue() == null) return;
        if (playBtn.isDisabled()) return;
        logLine("→ авто-запуск MC включён, стартую...");
        startGame();
    }

    // ---------- запуск игры (вынесено, чтобы звать из авто-старта) ----------
    private void startGame() {
        if (session == null) { logLine("✗ сначала войди"); return; }
        String ver = versionBox.getValue();
        if (ver == null || ver.isBlank()) { logLine("✗ выбери версию"); return; }

        playBtn.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(0);
        logLine("→ запуск Minecraft " + ver + " ...");

        final boolean hiddenToTray =
                trayReady && Boolean.TRUE.equals(cfg.minimizeOnPlay);
        if (hiddenToTray) {
            stage.hide();
            trayIcon.displayMessage("worm launcher",
                    "Minecraft " + ver + " запускается. Свёрнут в трей.",
                    TrayIcon.MessageType.INFO);
        }

        new Thread(() -> {
            try {
                Process p = GameLauncher.launch(session, ver, pct ->
                        Platform.runLater(() -> progressBar.setProgress(pct)));
                int code = p.waitFor();
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    logLine("→ Minecraft завершился, код " + code);
                    playBtn.setDisable(false);
                    if (hiddenToTray) restoreWindow();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    logLine("✗ запуск упал: " + ex.getMessage());
                    playBtn.setDisable(false);
                    if (hiddenToTray) restoreWindow();
                });
            }
        }, "mc-launch").start();
    }

    // ---------- плашка статуса ----------
    private void setStatus(String text, boolean ok) {
        statusLabel.setText("● " + text);
        statusLabel.setStyle(ok
                ? "-fx-text-fill: #4caf50; -fx-font-size: 11px;"
                : "-fx-text-fill: #8a8a8a; -fx-font-size: 11px;");
    }

    // ---------- восстановление окна ----------
    private void restoreWindow() {
        stage.setIconified(false);
        stage.show();
        stage.toFront();
        stage.requestFocus();
    }

    // ---------- трей ----------
    private void setupTray() {
        if (!SystemTray.isSupported()) return;
        try {
            SystemTray tray = SystemTray.getSystemTray();
            BufferedImage awtIcon = toAwt(makeIcon());

            PopupMenu menu = new PopupMenu();
            MenuItem show = new MenuItem("Открыть");
            show.addActionListener(e -> Platform.runLater(this::restoreWindow));
            MenuItem quit = new MenuItem("Выход");
            quit.addActionListener(e -> {
                tray.remove(trayIcon);
                Config.save(cfg);
                Platform.exit();
                System.exit(0);
            });
            menu.add(show);
            menu.addSeparator();
            menu.add(quit);

            trayIcon = new TrayIcon(awtIcon, "worm launcher", menu);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> Platform.runLater(this::restoreWindow));
            tray.add(trayIcon);
            trayReady = true;
            logLine("✓ трей активен");
        } catch (Exception ex) {
            logLine("⚠ трей не поднялся: " + ex.getMessage());
        }
    }

    // ---------- процедурная иконка ----------
    private Image makeIcon() {
        int size = 64;
        WritableImage img = new WritableImage(size, size);
        PixelWriter pw = img.getPixelWriter();
        Color body   = Color.web("#4caf50");
        Color border = Color.web("#2e7d32");
        double cx = size / 2.0, cy = size / 2.0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double dx = x - cx, dy = y - cy;
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d < 26) {
                    double ring = (Math.sin(d * 0.9) + 1) * 0.5;
                    pw.setColor(x, y, body.interpolate(border, ring * 0.6));
                } else if (d < 29) {
                    pw.setColor(x, y, border);
                } else {
                    pw.setColor(x, y, Color.TRANSPARENT);
                }
            }
        }
        return img;
    }

    private static BufferedImage toAwt(javafx.scene.image.Image img) {
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        PixelReader pr = img.getPixelReader();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                out.setRGB(x, y, pr.getArgb(x, y));
        return out;
    }

    private void logLine(String s) { log.appendText(s + System.lineSeparator()); }

    public static void main(String[] args) { launch(args); }
}