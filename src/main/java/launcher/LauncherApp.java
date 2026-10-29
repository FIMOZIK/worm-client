package launcher;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import launcher.auth.ElyAuth;
import launcher.auth.ElyOAuth;
import launcher.auth.GameSession;
import launcher.auth.MojangAuth;
import launcher.auth.TokenStore;
import launcher.mc.FabricInstaller;
import launcher.mc.GameLauncher;
import launcher.mc.VersionManifest;
import thunder.hack.utility.discord.DiscordEventHandlers;
import thunder.hack.utility.discord.DiscordRPC;
import thunder.hack.utility.discord.DiscordRichPresence;

import javax.imageio.ImageIO;
import java.awt.Desktop;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class LauncherApp extends Application {

    private static final String CLIENT_ID     = "worm-launcher1";
    private static final String CLIENT_SECRET = "YZdFkv0-L5K3aADqedEp0zamy2HzcjBcUApskOHxkzSsxE-XjsuCwdUsAIfVBShk";
    private static final Path   GAME_DIR      = Path.of("D:\\wormlauncher\\game");
    private static final String LAUNCHER_VERSION = "1.0.0";

    private static final String DISCORD_APP_ID = "1564662317663391774";
    private static final String DISCORD_LARGE_IMAGE = "https://i.ibb.co/B2cptB0Y/worm-logo.png";
    private static final String IMGBB_API_KEY = "6fa9d19c670bd0068a70d0f053e6c9e7";

    private static final ConcurrentHashMap<String, byte[]> headPngCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> headUrlCache = new ConcurrentHashMap<>();

    private GameSession session;
    private Config.Data cfg = Config.load();
    private TrayIcon trayIcon;
    private boolean trayReady = false;
    private Stage stage;
    private StackPane rootPane;
    private VBox contentHolder;

    // shared controls
    private Label titleLabel;
    private Label statusLabel;
    private TextField nickField;
    private PasswordField passField;
    private Button loginBtn, logoutBtn, oauthBtn, msBtn, openFolder, checkUpd, settingsBtn;
    private CheckBox minimizeBox, autoLaunchBox;
    private ComboBox<String> versionBox;
    private ComboBox<String> loaderBox;       // Vanilla / Fabric
    private ComboBox<String> fabricLoaderBox; // версии fabric-лоадера
    private Button playBtn;
    private ProgressBar progressBar;
    private TextArea log;

    // background layers
    private ImageView bgImageView;
    private MediaView bgMediaView;
    private MediaPlayer bgMediaPlayer;
    private Region tintLayer;

    // discord
    private static DiscordRPC rpc;
    private static DiscordRichPresence presence;
    private static volatile boolean rpcStarted = false;
    private static Thread rpcThread;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        Platform.setImplicitExit(false);

        initLauncherRpc();
        stage.getIcons().add(makeIcon());

        // === shared controls ===
        titleLabel = new Label("worm launcher");
        titleLabel.getStyleClass().add("title-label");

        statusLabel = new Label("не вошёл");
        statusLabel.getStyleClass().add("subtitle-label");

        nickField = new TextField();
        nickField.setPromptText("Ely.by ник");
        nickField.setPrefWidth(160);
        if (cfg.lastNick != null) nickField.setText(cfg.lastNick);

        passField = new PasswordField();
        passField.setPromptText("пароль");
        passField.setPrefWidth(160);

        loginBtn    = new Button("Войти (Ely)");
        logoutBtn   = new Button("Выйти");
        oauthBtn    = new Button("Ely через браузер");
        msBtn       = new Button("Войти через Microsoft");
        openFolder  = new Button("Открыть папку игры");
        checkUpd    = new Button("Проверить обновления");
        settingsBtn = new Button("⚙ Настройки");

        minimizeBox = new CheckBox("сворачивать в трей при игре");
        minimizeBox.setSelected(Boolean.TRUE.equals(cfg.minimizeOnPlay));
        minimizeBox.selectedProperty().addListener((o, a, b) -> { cfg.minimizeOnPlay = b; Config.save(cfg); });

        autoLaunchBox = new CheckBox("авто-запуск MC после входа");
        autoLaunchBox.setSelected(Boolean.TRUE.equals(cfg.autoLaunch));
        autoLaunchBox.selectedProperty().addListener((o, a, b) -> { cfg.autoLaunch = b; Config.save(cfg); });

        versionBox = new ComboBox<>();
        versionBox.setPrefWidth(200);
        versionBox.setPromptText("загрузка версий...");
        versionBox.setDisable(true);

        loaderBox = new ComboBox<>(FXCollections.observableArrayList("Vanilla", "Fabric"));
        loaderBox.setValue("Vanilla");
        loaderBox.setPrefWidth(120);

        fabricLoaderBox = new ComboBox<>();
        fabricLoaderBox.setPrefWidth(150);
        fabricLoaderBox.setPromptText("лоадер...");
        fabricLoaderBox.setDisable(true);
        fabricLoaderBox.setVisible(false);
        fabricLoaderBox.setManaged(false);

        playBtn = new Button("Играть");
        playBtn.getStyleClass().add("play-button");
        playBtn.setDisable(true);

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(180);
        progressBar.setVisible(false);

        log = new TextArea();
        log.setEditable(false);
        log.setPrefHeight(200);
        log.setMinHeight(120);
        log.setMaxHeight(Double.MAX_VALUE);
        log.setOpacity(cfg.logOpacity != null ? cfg.logOpacity : 1.0);

        // === background layers ===
        rootPane = new StackPane();

        bgImageView = new ImageView();
        bgImageView.setPreserveRatio(false);
        bgImageView.fitWidthProperty().bind(rootPane.widthProperty());
        bgImageView.fitHeightProperty().bind(rootPane.heightProperty());
        bgImageView.setManaged(false);
        bgImageView.setVisible(false);

        bgMediaView = new MediaView();
        bgMediaView.setPreserveRatio(false);
        bgMediaView.fitWidthProperty().bind(rootPane.widthProperty());
        bgMediaView.fitHeightProperty().bind(rootPane.heightProperty());
        bgMediaView.setManaged(false);
        bgMediaView.setVisible(false);

        tintLayer = new Region();
        tintLayer.setMouseTransparent(true);
        updateTint();

        contentHolder = new VBox();
        contentHolder.setSpacing(10);
        contentHolder.setPadding(new Insets(16));

        rootPane.getChildren().addAll(bgImageView, bgMediaView, tintLayer, contentHolder);

        // === scene ===
        double w = cfg.windowWidth  != null ? cfg.windowWidth  : 860;
        double h = cfg.windowHeight != null ? cfg.windowHeight : 700;
        Scene scene = new Scene(rootPane, w, h);

        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        ThemeManager.apply(scene, cfg);

        stage.setTitle("worm launcher " + LAUNCHER_VERSION);
        stage.setScene(scene);
        stage.setMinWidth(620);
        stage.setMinHeight(460);

        setupTray();
        applySavedWindowMode();

        rebuildBgLayer();
        applyLayout();

        stage.setOnCloseRequest(ev -> {
            if (!stage.isMaximized() && !stage.isFullScreen()) {
                cfg.windowWidth  = stage.getWidth();
                cfg.windowHeight = stage.getHeight();
            }
            if (stage.isFullScreen())      cfg.windowMode = "FULLSCREEN";
            else if (stage.isMaximized())  cfg.windowMode = "MAXIMIZED";
            else                           cfg.windowMode = "WINDOWED";
            Config.save(cfg);
            if (trayReady) {
                ev.consume();
                stage.hide();
                trayIcon.displayMessage("worm launcher",
                        "Свёрнут в трей. Двойной клик — открыть.",
                        TrayIcon.MessageType.INFO);
            }
        });

        // === handlers ===
        settingsBtn.setOnAction(e -> openSettings());

        new Thread(() -> {
            try {
                List<VersionManifest.Entry> list = VersionManifest.fetch();
                List<String> releases = list.stream()
                        .filter(e -> "release".equals(e.type()))
                        .map(VersionManifest.Entry::id)
                        .toList();
                Platform.runLater(() -> {
                    versionBox.setItems(FXCollections.observableArrayList(releases));
                    if (cfg.lastVersion != null && releases.contains(cfg.lastVersion)) versionBox.setValue(cfg.lastVersion);
                    else if (releases.contains("1.21.11")) versionBox.setValue("1.21.11");
                    else if (!releases.isEmpty()) versionBox.setValue(releases.get(0));
                    versionBox.setDisable(false);
                    logLine("✓ версий загружено: " + releases.size());
                });
            } catch (Exception ex) {
                Platform.runLater(() -> logLine("✗ не смог загрузить версии: " + ex.getMessage()));
            }
        }, "manifest").start();

        versionBox.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && !newV.isBlank()) { cfg.lastVersion = newV; Config.save(cfg); }
            if ("Fabric".equals(loaderBox.getValue())) {
                reloadFabricLoaders(newV);
            }
        });

        loaderBox.valueProperty().addListener((obs, oldV, newV) -> {
            boolean isFabric = "Fabric".equals(newV);
            fabricLoaderBox.setVisible(isFabric);
            fabricLoaderBox.setManaged(isFabric);
            if (isFabric) {
                reloadFabricLoaders(versionBox.getValue());
            } else {
                fabricLoaderBox.getItems().clear();
                fabricLoaderBox.setDisable(true);
            }
            applyLayout();
        });

        loginBtn.setOnAction(e -> {
            loginBtn.setDisable(true);
            String nickText = nickField.getText().trim();
            logLine("→ login (ely password): " + nickText);
            new Thread(() -> {
                try {
                    ElyAuth.Session s = ElyAuth.login(nickText, passField.getText());
                    GameSession gs = GameSession.fromEly(s);
                    TokenStore.save(gs);
                    cfg.lastNick = gs.username();
                    Config.save(cfg);
                    Platform.runLater(() -> {
                        session = gs;
                        logLine("✓ " + gs);
                        passField.clear();
                        playBtn.setDisable(false);
                        loginBtn.setDisable(false);
                        setStatus("ely: " + gs.username(), true);
                        refreshLauncherPresenceImages();
                        updateLauncherPresence("logged in as " + gs.username());
                        maybeAutoLaunch(autoLaunchBox.isSelected());
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> { logLine("✗ " + ex.getMessage()); loginBtn.setDisable(false); });
                }
            }, "ely-auth").start();
        });

        oauthBtn.setOnAction(e -> {
            oauthBtn.setDisable(true);
            logLine("→ login (ely browser) ...");
            new Thread(() -> {
                try {
                    ElyOAuth oauth = new ElyOAuth(CLIENT_ID, CLIENT_SECRET);
                    ElyOAuth.Tokens tokens = oauth.authorize();
                    ElyAuth.Session s = oauth.toSession(tokens);
                    GameSession gs = GameSession.fromEly(s);
                    TokenStore.save(gs);
                    cfg.lastNick = gs.username();
                    Config.save(cfg);
                    Platform.runLater(() -> {
                        session = gs;
                        logLine("✓ " + gs);
                        playBtn.setDisable(false);
                        oauthBtn.setDisable(false);
                        setStatus("ely: " + gs.username(), true);
                        refreshLauncherPresenceImages();
                        updateLauncherPresence("logged in as " + gs.username());
                        maybeAutoLaunch(autoLaunchBox.isSelected());
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> { logLine("✗ Ely OAuth: " + ex.getMessage()); oauthBtn.setDisable(false); });
                }
            }, "ely-oauth").start();
        });

        msBtn.setOnAction(e -> {
            msBtn.setDisable(true);
            logLine("→ login (microsoft) ...");
            new Thread(() -> {
                try {
                    MojangAuth.Session s = MojangAuth.login(dc -> {
                        Platform.runLater(() -> {
                            logLine("→ открой " + dc.verificationUri() + " и введи код:");
                            logLine("     " + dc.userCode());
                        });
                        try { if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(java.net.URI.create(dc.verificationUri())); }
                        catch (Exception ignored) {}
                    });
                    GameSession gs = GameSession.fromMojang(s);
                    TokenStore.save(gs);
                    cfg.lastNick = gs.username();
                    Config.save(cfg);
                    Platform.runLater(() -> {
                        session = gs;
                        logLine("✓ " + gs);
                        playBtn.setDisable(false);
                        msBtn.setDisable(false);
                        setStatus("mojang: " + gs.username(), true);
                        refreshLauncherPresenceImages();
                        updateLauncherPresence("logged in as " + gs.username());
                        maybeAutoLaunch(autoLaunchBox.isSelected());
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> { logLine("✗ Microsoft: " + ex.getMessage()); msBtn.setDisable(false); });
                }
            }, "ms-auth").start();
        });

        logoutBtn.setOnAction(e -> {
            try {
                if (session != null && session.isEly()) ElyAuth.invalidate(session.toEly());
                TokenStore.clear();
                session = null;
                playBtn.setDisable(true);
                setStatus("не вошёл", false);
                logLine("✓ сессия удалена");
                if (presence != null) { presence.smallImageKey = ""; presence.smallImageText = ""; }
                updateLauncherPresence("idle");
            } catch (Exception ex) { logLine("✗ " + ex.getMessage()); }
        });

        openFolder.setOnAction(e -> {
            try { Files.createDirectories(GAME_DIR); Desktop.getDesktop().open(GAME_DIR.toFile()); logLine("→ открыл " + GAME_DIR); }
            catch (Exception ex) { logLine("✗ " + ex.getMessage()); }
        });

        checkUpd.setOnAction(e -> {
            logLine("→ проверка обновлений...");
            new Thread(() -> {
                try { Thread.sleep(400); Platform.runLater(() -> logLine("✓ установлена версия " + LAUNCHER_VERSION + " (последняя)")); }
                catch (Exception ex) { Platform.runLater(() -> logLine("✗ " + ex.getMessage())); }
            }, "check-upd").start();
        });

        playBtn.setOnAction(e -> startGame());

        GameSession cached = TokenStore.load();
        if (cached != null) {
            session = cached;
            playBtn.setDisable(false);
            setStatus((cached.isEly() ? "ely: " : "mojang: ") + cached.username(), true);
            logLine("найдена сохранённая сессия: " + cached);
            refreshLauncherPresenceImages();
            updateLauncherPresence("logged in as " + cached.username());
            if (autoLaunchBox.isSelected()) Platform.runLater(() -> maybeAutoLaunch(true));
        }

        stage.show();
    }

    @Override
    public void stop() {
        stopLauncherRpc();
        if (bgMediaPlayer != null) {
            try { bgMediaPlayer.stop(); bgMediaPlayer.dispose(); } catch (Exception ignored) {}
            bgMediaPlayer = null;
        }
    }

    // ============================================================
    // LAYOUT
    // ============================================================

    private void applyLayout() {
        String layout = cfg.buttonLayout == null ? "TOP" : cfg.buttonLayout;
        boolean logInSeparate = logInSeparateWindow();

        detachAll(titleLabel, statusLabel, nickField, passField, loginBtn, logoutBtn,
                oauthBtn, msBtn, openFolder, checkUpd, settingsBtn,
                minimizeBox, autoLaunchBox, versionBox, loaderBox, fabricLoaderBox,
                playBtn, progressBar);
        if (!logInSeparate) detachAll(log);

        contentHolder.getChildren().clear();

        HBox titleRow = new HBox(12, titleLabel, statusLabel);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        if ("LEFT".equals(layout)) {
            VBox sidebar = new VBox(8,
                    nickField, passField,
                    new Separator(),
                    loginBtn, logoutBtn,
                    new Separator(),
                    oauthBtn, msBtn, openFolder, checkUpd, settingsBtn,
                    new Separator(),
                    minimizeBox, autoLaunchBox);
            sidebar.setPrefWidth(230);
            sidebar.setMinWidth(200);

            HBox playRow = new HBox(8,
                    new Label("версия:"), versionBox,
                    new Label("загрузчик:"), loaderBox, fabricLoaderBox,
                    playBtn, progressBar);
            playRow.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(versionBox, Priority.ALWAYS);

            VBox right = new VBox(10, titleRow, playRow);
            HBox.setHgrow(right, Priority.ALWAYS);

            if (!logInSeparate && !Boolean.FALSE.equals(cfg.showLog)) {
                right.getChildren().addAll(new Label("лог:"), log);
                VBox.setVgrow(log, Priority.ALWAYS);
            }

            HBox h = new HBox(10, sidebar, right);
            VBox.setVgrow(h, Priority.ALWAYS);
            contentHolder.getChildren().add(h);
        } else {
            HBox authRow = new HBox(8, nickField, passField, loginBtn, logoutBtn);
            authRow.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(nickField, Priority.ALWAYS);
            HBox.setHgrow(passField, Priority.ALWAYS);

            FlowPane actionRow = new FlowPane(8, 8, oauthBtn, msBtn, openFolder, checkUpd, settingsBtn);
            actionRow.setPrefWrapLength(700);

            FlowPane optsRow = new FlowPane(16, 8, minimizeBox, autoLaunchBox);

            HBox playRow = new HBox(8,
                    new Label("версия:"), versionBox,
                    new Label("загрузчик:"), loaderBox, fabricLoaderBox,
                    playBtn, progressBar);
            playRow.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(versionBox, Priority.ALWAYS);

            contentHolder.getChildren().addAll(titleRow, authRow, actionRow, optsRow, new Separator(), playRow);

            if (!logInSeparate && !Boolean.FALSE.equals(cfg.showLog)) {
                contentHolder.getChildren().addAll(new Label("лог:"), log);
                VBox.setVgrow(log, Priority.ALWAYS);
            }
        }

        log.setOpacity(cfg.logOpacity != null ? cfg.logOpacity : 1.0);
    }

    private void detachAll(Node... nodes) {
        for (Node n : nodes) {
            if (n == null) continue;
            Parent p = n.getParent();
            if (p instanceof Pane pane) pane.getChildren().remove(n);
        }
    }

    private boolean logInSeparateWindow() {
        if (log.getScene() == null) return false;
        Window w = log.getScene().getWindow();
        return w != null && w != stage;
    }

    // ============================================================
    // WINDOW MODE
    // ============================================================

    private void applySavedWindowMode() {
        String mode = cfg.windowMode == null ? "WINDOWED" : cfg.windowMode;
        switch (mode) {
            case "FULLSCREEN" -> stage.setFullScreen(true);
            case "MAXIMIZED"  -> stage.setMaximized(true);
            default -> {}
        }
    }

    private void applyWindowMode(String mode) {
        if (mode == null) mode = "WINDOWED";
        switch (mode) {
            case "FULLSCREEN" -> { stage.setMaximized(false); stage.setFullScreen(true); }
            case "MAXIMIZED"  -> { stage.setFullScreen(false); stage.setMaximized(true); }
            default           -> { stage.setFullScreen(false); stage.setMaximized(false); }
        }
    }

    // ============================================================
    // BACKGROUND
    // ============================================================

    private void rebuildBgLayer() {
        if (bgMediaPlayer != null) {
            try { bgMediaPlayer.stop(); bgMediaPlayer.dispose(); } catch (Exception ignored) {}
            bgMediaPlayer = null;
        }
        bgMediaView.setMediaPlayer(null);
        bgMediaView.setVisible(false);
        bgImageView.setVisible(false);
        bgImageView.setImage(null);

        if (cfg.bgImagePath == null || !Files.exists(Path.of(cfg.bgImagePath))) return;

        String lower = cfg.bgImagePath.toLowerCase();
        double opacity = cfg.bgOpacity != null ? cfg.bgOpacity : 0.55;

        if (lower.endsWith(".mp4") || lower.endsWith(".flv") || lower.endsWith(".m4v")) {
            try {
                Media m = new Media(Path.of(cfg.bgImagePath).toUri().toString());
                bgMediaPlayer = new MediaPlayer(m);
                bgMediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);
                bgMediaPlayer.setMute(true);
                bgMediaPlayer.setOnError(() ->
                        logLine("⚠ видео-фон: " + bgMediaPlayer.getError().getMessage()));
                bgMediaPlayer.play();
                bgMediaView.setMediaPlayer(bgMediaPlayer);
                bgMediaView.setOpacity(opacity);
                bgMediaView.setVisible(true);
            } catch (Exception e) {
                logLine("⚠ не смог загрузить видео-фон: " + e.getMessage());
            }
        } else {
            try {
                Image img = new Image(Path.of(cfg.bgImagePath).toUri().toString(), true);
                bgImageView.setImage(img);
                bgImageView.setOpacity(opacity);
                bgImageView.setVisible(true);
            } catch (Exception e) {
                logLine("⚠ не смог загрузить картинку-фон: " + e.getMessage());
            }
        }
    }

    private void setBgOpacity(double alpha) {
        bgImageView.setOpacity(alpha);
        bgMediaView.setOpacity(alpha);
    }

    private void updateTint() {
        tintLayer.setStyle("-fx-background-color: rgba(0,0,0,"
                + (Boolean.TRUE.equals(cfg.darkMode) ? 0.35 : 0.15) + ");");
    }

    // ============================================================
    // SETTINGS
    // ============================================================

    private void openSettings() {
        Stage s = new Stage();
        s.initOwner(stage);
        s.initModality(Modality.APPLICATION_MODAL);
        s.setTitle("Настройки");

        Label head = new Label("Настройки лаунчера");
        head.getStyleClass().add("title-label");
        head.setStyle("-fx-font-size: 16px;");

        ComboBox<String> themeCombo = new ComboBox<>(FXCollections.observableArrayList(ThemeManager.listPresets()));
        themeCombo.setValue(cfg.theme != null ? cfg.theme : "WormGreen");
        themeCombo.setPrefWidth(240);

        CheckBox darkBox = new CheckBox("тёмная тема");
        darkBox.setSelected(Boolean.TRUE.equals(cfg.darkMode));

        ComboBox<String> layoutCombo = new ComboBox<>(FXCollections.observableArrayList("TOP", "LEFT"));
        layoutCombo.setValue(cfg.buttonLayout != null ? cfg.buttonLayout : "TOP");
        layoutCombo.setPrefWidth(180);

        // background
        Label bgPath = new Label(cfg.bgImagePath == null ? "не выбрано" : cfg.bgImagePath);
        bgPath.getStyleClass().add("subtitle-label");
        bgPath.setWrapText(true);
        Button pickBg = new Button("Выбрать...");
        Button clearBg = new Button("Убрать");

        Slider bgOpacity = new Slider(0.0, 1.0, cfg.bgOpacity != null ? cfg.bgOpacity : 0.55);
        bgOpacity.setShowTickMarks(true);
        bgOpacity.setMajorTickUnit(0.25);
        Label opacityLbl = new Label("Прозрачность фона: " + Math.round(bgOpacity.getValue() * 100) + "%");
        bgOpacity.valueProperty().addListener((o, a, b) -> {
            cfg.bgOpacity = b.doubleValue();
            setBgOpacity(cfg.bgOpacity);
            opacityLbl.setText("Прозрачность фона: " + Math.round(cfg.bgOpacity * 100) + "%");
        });

        pickBg.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Картинки и видео",
                            "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp",
                            "*.mp4", "*.flv", "*.m4v"),
                    new FileChooser.ExtensionFilter("Картинки", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
                    new FileChooser.ExtensionFilter("Видео", "*.mp4", "*.flv", "*.m4v"),
                    new FileChooser.ExtensionFilter("Все файлы", "*.*")
            );
            java.io.File f = fc.showOpenDialog(s);
            if (f != null) {
                cfg.bgImagePath = f.getAbsolutePath();
                bgPath.setText(cfg.bgImagePath);
                rebuildBgLayer();
            }
        });
        clearBg.setOnAction(e -> {
            cfg.bgImagePath = null;
            bgPath.setText("не выбрано");
            rebuildBgLayer();
        });

        // window mode
        ToggleGroup wmGroup = new ToggleGroup();
        RadioButton wmWindowed   = new RadioButton("Оконный");
        RadioButton wmMaximized  = new RadioButton("Развёрнутый");
        RadioButton wmFullscreen = new RadioButton("Полноэкранный");
        wmWindowed.setToggleGroup(wmGroup);
        wmMaximized.setToggleGroup(wmGroup);
        wmFullscreen.setToggleGroup(wmGroup);

        String initialMode = cfg.windowMode == null ? "WINDOWED" : cfg.windowMode;
        switch (initialMode) {
            case "MAXIMIZED"  -> wmMaximized.setSelected(true);
            case "FULLSCREEN" -> wmFullscreen.setSelected(true);
            default           -> wmWindowed.setSelected(true);
        }
        HBox wmRow = new HBox(16, wmWindowed, wmMaximized, wmFullscreen);

        // log
        CheckBox showLogBox = new CheckBox("показывать лог в главном окне");
        showLogBox.setSelected(!Boolean.FALSE.equals(cfg.showLog));

        Slider logOpacity = new Slider(0.2, 1.0, cfg.logOpacity != null ? cfg.logOpacity : 1.0);
        logOpacity.setShowTickMarks(true);
        logOpacity.setMajorTickUnit(0.2);
        Label logOpLbl = new Label("Прозрачность лога: " + Math.round(logOpacity.getValue() * 100) + "%");
        logOpacity.valueProperty().addListener((o, a, b) -> {
            cfg.logOpacity = b.doubleValue();
            log.setOpacity(cfg.logOpacity);
            logOpLbl.setText("Прозрачность лога: " + Math.round(cfg.logOpacity * 100) + "%");
        });

        Button openLogBtn = new Button("Открыть лог в отдельном окне");
        openLogBtn.setOnAction(e -> {
            openLogWindow();
            s.close();
        });

        // buttons
        Button okBtn = new Button("Применить");
        okBtn.getStyleClass().add("play-button");
        Button cancelBtn = new Button("Отмена");
        HBox btnRow = new HBox(8, okBtn, cancelBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        final String oldMode = initialMode;

        okBtn.setOnAction(e -> {
            cfg.theme = themeCombo.getValue();
            cfg.darkMode = darkBox.isSelected();
            cfg.showLog = showLogBox.isSelected();
            cfg.buttonLayout = layoutCombo.getValue();

            String newMode;
            if (wmFullscreen.isSelected())      newMode = "FULLSCREEN";
            else if (wmMaximized.isSelected())  newMode = "MAXIMIZED";
            else                                newMode = "WINDOWED";

            boolean modeChanged = !newMode.equals(oldMode);
            cfg.windowMode = newMode;
            Config.save(cfg);

            for (Window w : Stage.getWindows()) {
                if (w instanceof Stage st && st.getScene() != null) {
                    ThemeManager.apply(st.getScene(), cfg);
                }
            }
            updateTint();

            if (modeChanged) applyWindowMode(cfg.windowMode);

            // пересобираем layout ВСЕГДА — подхватывает showLog и buttonLayout
            applyLayout();

            s.close();
        });
        cancelBtn.setOnAction(e -> s.close());

        VBox panel = new VBox(10,
                head,
                new Separator(),
                new Label("Тема"), themeCombo,
                darkBox,
                new Label("Расположение кнопок"), layoutCombo,
                new Separator(),
                new Label("Фон (картинка / gif / видео)"), bgPath, new HBox(8, pickBg, clearBg),
                opacityLbl, bgOpacity,
                new Separator(),
                new Label("Режим окна"), wmRow,
                new Separator(),
                new Label("Лог"), showLogBox,
                logOpLbl, logOpacity,
                new HBox(8, openLogBtn),
                new Separator(),
                btnRow
        );
        panel.getStyleClass().add("settings-panel");
        panel.setPadding(new Insets(20));
        panel.setPrefWidth(520);

        ScrollPane scroll = new ScrollPane(panel);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(640);
        scroll.setStyle("-fx-background-color: transparent;");
        scroll.getStyleClass().add("settings-scroll");

        Scene scene = new Scene(scroll, 560, 680);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        ThemeManager.apply(scene, cfg);

        s.setScene(scene);
        s.showAndWait();
    }

    // ============================================================
    // LOG WINDOW
    // ============================================================

    private void openLogWindow() {
        if (logInSeparateWindow()) return;

        if (log.getParent() instanceof Pane p) p.getChildren().remove(log);
        applyLayout();

        Stage logStage = new Stage();
        logStage.initOwner(stage);
        logStage.setTitle("worm launcher — лог");
        logStage.getIcons().add(makeIcon());

        VBox box = new VBox(log);
        box.setPadding(new Insets(8));
        VBox.setVgrow(log, Priority.ALWAYS);
        VBox.setVgrow(box, Priority.ALWAYS);

        Scene sc = new Scene(box, 640, 400);
        sc.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        ThemeManager.apply(sc, cfg);

        logStage.setScene(sc);
        logStage.setOnCloseRequest(e -> {
            box.getChildren().remove(log);
            applyLayout();
        });
        logStage.show();
    }

    // ============================================================
    // FABRIC LOADERS
    // ============================================================

    private void reloadFabricLoaders(String mcVersion) {
        if (mcVersion == null || mcVersion.isBlank()) return;
        fabricLoaderBox.setDisable(true);
        fabricLoaderBox.getItems().clear();
        fabricLoaderBox.setPromptText("загрузка лоадеров...");
        logLine("→ fabric loaders для " + mcVersion + " ...");

        new Thread(() -> {
            try {
                List<String> loaders = VersionManifest.fetchFabricLoaders(mcVersion);
                Platform.runLater(() -> {
                    fabricLoaderBox.setItems(FXCollections.observableArrayList(loaders));
                    if (!loaders.isEmpty()) fabricLoaderBox.setValue(loaders.get(0));
                    fabricLoaderBox.setDisable(loaders.isEmpty());
                    fabricLoaderBox.setPromptText(loaders.isEmpty() ? "нет лоадеров" : "лоадер...");
                    logLine("✓ fabric лоадеров: " + loaders.size());
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    fabricLoaderBox.setPromptText("ошибка загрузки");
                    logLine("✗ fabric loaders: " + ex.getMessage());
                });
            }
        }, "fabric-loaders").start();
    }

    // ============================================================
    // GAME LAUNCH
    // ============================================================

    private void maybeAutoLaunch(boolean enabled) {
        if (!enabled || session == null || versionBox.getValue() == null || playBtn.isDisabled()) return;
        logLine("→ авто-запуск MC включён, стартую...");
        startGame();
    }

    private void startGame() {
        if (session == null) { logLine("✗ сначала войди"); return; }
        String ver = versionBox.getValue();
        if (ver == null || ver.isBlank()) { logLine("✗ выбери версию"); return; }

        boolean isFabric = "Fabric".equals(loaderBox.getValue());
        final String loaderVer = isFabric ? fabricLoaderBox.getValue() : null;
        if (isFabric && (loaderVer == null || loaderVer.isBlank())) {
            logLine("✗ выбери версию fabric-лоадера");
            return;
        }

        playBtn.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(0);
        logLine("→ запуск Minecraft " + ver
                + (isFabric ? " (fabric " + loaderVer + ")" : "")
                + " [" + session.type() + "] ...");

        final boolean hiddenToTray = trayReady && Boolean.TRUE.equals(cfg.minimizeOnPlay);
        if (hiddenToTray) {
            stage.hide();
            trayIcon.displayMessage("worm launcher",
                    "Minecraft " + ver + " запускается. Свёрнут в трей.",
                    TrayIcon.MessageType.INFO);
        }

        new Thread(() -> {
            try {
                updateLauncherPresence("launching MC " + ver);
                stopLauncherRpc();

                String launchId = ver;
                if (isFabric) {
                    Platform.runLater(() -> logLine("→ установка fabric-профиля ..."));
                    launchId = FabricInstaller.install(GAME_DIR, ver, loaderVer);
                    final String fid = launchId;
                    Platform.runLater(() -> logLine("✓ fabric профиль: " + fid));
                }

                final String finalLaunchId = launchId;
                Process p = GameLauncher.launch(session, finalLaunchId, pct ->
                        Platform.runLater(() -> progressBar.setProgress(pct)));

                int code = p.waitFor();
                initLauncherRpc();
                Platform.runLater(() -> {
                    refreshLauncherPresenceImages();
                    updateLauncherPresence(session != null ? "logged in as " + session.username() : "idle");
                    progressBar.setVisible(false);
                    logLine("→ Minecraft завершился, код " + code);
                    playBtn.setDisable(false);
                    if (hiddenToTray) restoreWindow();
                });
            } catch (Exception ex) {
                initLauncherRpc();
                Platform.runLater(() -> {
                    refreshLauncherPresenceImages();
                    updateLauncherPresence(session != null ? "logged in as " + session.username() : "idle");
                    progressBar.setVisible(false);
                    logLine("✗ запуск упал: " + ex.getMessage());
                    playBtn.setDisable(false);
                    if (hiddenToTray) restoreWindow();
                });
            }
        }, "mc-launch").start();
    }

    private void setStatus(String text, boolean ok) {
        statusLabel.setText("● " + text);
        statusLabel.getStyleClass().remove("dim");
        if (!ok) statusLabel.getStyleClass().add("dim");
    }

    private void restoreWindow() {
        stage.setIconified(false);
        stage.show();
        stage.toFront();
        stage.requestFocus();
    }

    // ============================================================
    // TRAY
    // ============================================================

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
                stopLauncherRpc();
                if (bgMediaPlayer != null) {
                    try { bgMediaPlayer.stop(); bgMediaPlayer.dispose(); } catch (Exception ignored) {}
                }
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

    private Image makeIcon() {
        int size = 64;
        WritableImage img = new WritableImage(size, size);
        PixelWriter pw = img.getPixelWriter();
        Color body   = Color.web("#4caf50");
        Color border = Color.web("#2e7d32");
        double cx = size / 2.0, cy = size / 2.0;
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                double dx = x - cx, dy = y - cy;
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d < 26) {
                    double ring = (Math.sin(d * 0.9) + 1) * 0.5;
                    pw.setColor(x, y, body.interpolate(border, ring * 0.6));
                } else if (d < 29) {
                    pw.setColor(x, y, border);
                } else pw.setColor(x, y, Color.TRANSPARENT);
            }
        return img;
    }

    private static BufferedImage toAwt(javafx.scene.image.Image img) {
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        PixelReader pr = img.getPixelReader();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) out.setRGB(x, y, pr.getArgb(x, y));
        return out;
    }

    private void logLine(String s) { log.appendText(s + System.lineSeparator()); }

    // ============================================================
    // DISCORD RPC
    // ============================================================

    private void initLauncherRpc() {
        if (rpcStarted) return;
        rpcStarted = true;
        new Thread(() -> {
            try {
                rpc = DiscordRPC.INSTANCE;
                DiscordEventHandlers handlers = new DiscordEventHandlers();
                rpc.Discord_Initialize(DISCORD_APP_ID, handlers, true, "");
                presence = new DiscordRichPresence();
                presence.startTimestamp = System.currentTimeMillis() / 1000L;
                presence.details = "worm launcher — выбор версии";
                presence.state = session != null ? "logged in as " + session.username() : "idle";
                presence.largeImageKey = DISCORD_LARGE_IMAGE;
                presence.largeImageText = "worm launcher v" + LAUNCHER_VERSION;
                String nick = session != null ? session.username() : cfg.lastNick;
                if (nick != null && !nick.isBlank()) {
                    String url = resolveHeadUrl(nick, session);
                    if (url != null && !url.isEmpty()) {
                        presence.smallImageKey = url;
                        presence.smallImageText = "logged as " + nick;
                    }
                }
                rpc.Discord_UpdatePresence(presence);
                rpcThread = new Thread(() -> {
                    while (rpcStarted && !Thread.currentThread().isInterrupted()) {
                        try { rpc.Discord_RunCallbacks(); } catch (Exception ignored) {}
                        try { Thread.sleep(5000L); } catch (InterruptedException e) { break; }
                    }
                }, "Launcher-RPC");
                rpcThread.setDaemon(true);
                rpcThread.start();
                Platform.runLater(() -> logLine("✓ Discord RPC активен"));
            } catch (Exception e) {
                Platform.runLater(() -> logLine("⚠ RPC не поднялся: " + e.getMessage()));
            }
        }, "Launcher-RPC-Init").start();
    }

    private static void updateLauncherPresence(String state) {
        if (rpc == null || presence == null) return;
        presence.state = state;
        try { rpc.Discord_UpdatePresence(presence); } catch (Exception ignored) {}
    }

    private void refreshLauncherPresenceImages() {
        if (rpc == null || presence == null) return;
        String nick = session != null ? session.username() : cfg.lastNick;
        if (nick == null || nick.isBlank()) {
            presence.smallImageKey = "";
            presence.smallImageText = "";
            try { rpc.Discord_UpdatePresence(presence); } catch (Exception ignored) {}
            return;
        }
        final GameSession sess = session;
        new Thread(() -> {
            String url = resolveHeadUrl(nick, sess);
            if (url == null || url.isEmpty()) return;
            if (rpc == null || presence == null) return;
            presence.smallImageKey = url;
            presence.smallImageText = "logged as " + nick;
            try { rpc.Discord_UpdatePresence(presence); } catch (Exception ignored) {}
        }, "head-resolve").start();
    }

    private static void stopLauncherRpc() {
        rpcStarted = false;
        if (rpcThread != null) { rpcThread.interrupt(); rpcThread = null; }
    }

    private String resolveHeadUrl(String nick, GameSession sess) {
        if (sess != null && sess.isMojang()) return "https://mc-heads.net/avatar/" + nick + "/64";
        String cached = headUrlCache.get(nick);
        if (cached != null) return cached;
        byte[] png = headPngCache.computeIfAbsent(nick, this::renderElyHeadPng);
        if (png == null) return "";
        String url = uploadHead(png, nick);
        if (url != null && !url.isEmpty()) {
            headUrlCache.put(nick, url);
            final String u = url;
            Platform.runLater(() -> logLine("✓ голова " + nick + " → " + u));
            return url;
        }
        String fallback = "https://skinsystem.ely.by/skins/" + nick + ".png";
        headUrlCache.put(nick, fallback);
        Platform.runLater(() -> logLine("⚠ аплоадеры не сработали, использую скин: " + fallback));
        return fallback;
    }

    private String uploadHead(byte[] png, String nick) {
        String u;
        u = tryImgbb(png, nick);    if (u != null) return u;
        u = tryCatbox(png, nick);   if (u != null) return u;
        u = tryLitterbox(png, nick);if (u != null) return u;
        u = try0x0(png, nick);      if (u != null) return u;
        u = tryTmpfiles(png, nick); return u;
    }

    private byte[] renderElyHeadPng(String nick) {
        BufferedImage skin = fetchElySkin(nick);
        if (skin == null) return null;
        int scale = 8;
        BufferedImage head = new BufferedImage(8 * scale, 8 * scale, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++)
            for (int x = 0; x < 8; x++) {
                int rgb = skin.getRGB(8 + x, 8 + y);
                for (int dy = 0; dy < scale; dy++)
                    for (int dx = 0; dx < scale; dx++)
                        head.setRGB(x * scale + dx, y * scale + dy, rgb);
            }
        if (skin.getHeight() >= 64) {
            for (int y = 0; y < 8; y++)
                for (int x = 0; x < 8; x++) {
                    int rgb = skin.getRGB(40 + x, 8 + y);
                    int alpha = (rgb >> 24) & 0xFF;
                    if (alpha > 0) {
                        for (int dy = 0; dy < scale; dy++)
                            for (int dx = 0; dx < scale; dx++)
                                head.setRGB(x * scale + dx, y * scale + dy, rgb);
                    }
                }
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(head, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) { return null; }
    }

    private BufferedImage fetchElySkin(String nick) {
        String[] candidates = {
                "https://skinsystem.ely.by/skins/" + nick + ".png",
                "https://skinsystem.ely.by/textures/" + nick,
                "http://skinsystem.ely.by/skins/" + nick + ".png"
        };
        for (String c : candidates) {
            BufferedImage img = fetchImageFollowingRedirects(c, nick, 5);
            if (img != null && img.getWidth() >= 64 && img.getHeight() >= 32) return img;
        }
        return null;
    }

    private BufferedImage fetchImageFollowingRedirects(String urlStr, String nick, int maxHops) {
        String current = urlStr;
        for (int hop = 0; hop < maxHops; hop++) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(current);
                conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) worm-launcher/" + LAUNCHER_VERSION);
                int code = conn.getResponseCode();
                if (code >= 300 && code < 400) {
                    String loc = conn.getHeaderField("Location");
                    if (loc == null || loc.isEmpty()) return null;
                    if (loc.startsWith("/")) {
                        URL base = new URL(current);
                        loc = base.getProtocol() + "://" + base.getHost()
                                + (base.getPort() > 0 ? ":" + base.getPort() : "") + loc;
                    }
                    current = loc;
                    continue;
                }
                if (code != 200) return null;
                try (InputStream in = conn.getInputStream()) { return ImageIO.read(in); }
            } catch (Exception e) { return null; }
            finally { if (conn != null) conn.disconnect(); }
        }
        return null;
    }

    private String tryImgbb(byte[] png, String nick) {
        try {
            String boundary = "----worm" + System.currentTimeMillis();
            URL url = new URL("https://api.imgbb.com/1/upload?key=" + IMGBB_API_KEY);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            String head = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"image\"; filename=\"" + nick + ".png\"\r\n"
                    + "Content-Type: image/png\r\n\r\n";
            String tail = "\r\n--" + boundary + "--\r\n";
            try (OutputStream out = conn.getOutputStream()) {
                out.write(head.getBytes(StandardCharsets.UTF_8));
                out.write(png);
                out.write(tail.getBytes(StandardCharsets.UTF_8));
            }
            if (conn.getResponseCode() != 200) return null;
            try (InputStream in = conn.getInputStream()) {
                String result = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                int idx = result.indexOf("\"url\":\"");
                if (idx < 0) return null;
                int start = idx + 7;
                int end = result.indexOf('"', start);
                if (end < 0) return null;
                String u = result.substring(start, end).replace("\\/", "/");
                if (u.startsWith("https://i.ibb.co/")) return u;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String tryCatbox(byte[] png, String nick) {
        try {
            String boundary = "----worm" + System.currentTimeMillis();
            URL url = new URL("https://catbox.moe/user/api.php");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            String head = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"reqtype\"\r\n\r\nfileupload\r\n"
                    + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"fileToUpload\"; filename=\"" + nick + ".png\"\r\nContent-Type: image/png\r\n\r\n";
            String tail = "\r\n--" + boundary + "--\r\n";
            try (OutputStream out = conn.getOutputStream()) {
                out.write(head.getBytes(StandardCharsets.UTF_8));
                out.write(png);
                out.write(tail.getBytes(StandardCharsets.UTF_8));
            }
            if (conn.getResponseCode() != 200) return null;
            try (InputStream in = conn.getInputStream()) {
                String r = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
                if (r.startsWith("https://files.catbox.moe/")) return r;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String tryLitterbox(byte[] png, String nick) {
        try {
            String boundary = "----worm" + System.currentTimeMillis();
            URL url = new URL("https://litterbox.catbox.moe/resources/internals/api.php");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            String head = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"reqtype\"\r\n\r\nfileupload\r\n"
                    + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"time\"\r\n\r\n72h\r\n"
                    + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"fileToUpload\"; filename=\"" + nick + ".png\"\r\nContent-Type: image/png\r\n\r\n";
            String tail = "\r\n--" + boundary + "--\r\n";
            try (OutputStream out = conn.getOutputStream()) {
                out.write(head.getBytes(StandardCharsets.UTF_8));
                out.write(png);
                out.write(tail.getBytes(StandardCharsets.UTF_8));
            }
            if (conn.getResponseCode() != 200) return null;
            try (InputStream in = conn.getInputStream()) {
                String r = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
                if (r.startsWith("https://litter.catbox.moe/")) return r;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String try0x0(byte[] png, String nick) {
        try {
            String boundary = "----worm" + System.currentTimeMillis();
            URL url = new URL("https://0x0.st");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            String head = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + nick + ".png\"\r\nContent-Type: image/png\r\n\r\n";
            String tail = "\r\n--" + boundary + "--\r\n";
            try (OutputStream out = conn.getOutputStream()) {
                out.write(head.getBytes(StandardCharsets.UTF_8));
                out.write(png);
                out.write(tail.getBytes(StandardCharsets.UTF_8));
            }
            if (conn.getResponseCode() != 200) return null;
            try (InputStream in = conn.getInputStream()) {
                String r = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
                if (r.startsWith("https://")) return r;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String tryTmpfiles(byte[] png, String nick) {
        try {
            String boundary = "----worm" + System.currentTimeMillis();
            URL url = new URL("https://tmpfiles.org/api/v1/upload");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            String head = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + nick + ".png\"\r\nContent-Type: image/png\r\n\r\n";
            String tail = "\r\n--" + boundary + "--\r\n";
            try (OutputStream out = conn.getOutputStream()) {
                out.write(head.getBytes(StandardCharsets.UTF_8));
                out.write(png);
                out.write(tail.getBytes(StandardCharsets.UTF_8));
            }
            if (conn.getResponseCode() != 200) return null;
            try (InputStream in = conn.getInputStream()) {
                String result = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                int idx = result.indexOf("https://tmpfiles.org/");
                if (idx < 0) return null;
                int end = idx;
                while (end < result.length() && result.charAt(end) != '"' && result.charAt(end) != ' ') end++;
                return result.substring(idx, end).replace("tmpfiles.org/", "tmpfiles.org/dl/");
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static void main(String[] args) { launch(args); }
}