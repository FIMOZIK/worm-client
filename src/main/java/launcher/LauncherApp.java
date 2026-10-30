package launcher;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
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
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;
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
import launcher.mc.ForgeInstaller;
import launcher.mc.GameLauncher;
import launcher.mc.QuiltInstaller;
import launcher.mc.VersionManifest;
import thunder.hack.utility.discord.DiscordEventHandlers;
import thunder.hack.utility.discord.DiscordRPC;
import thunder.hack.utility.discord.DiscordRichPresence;

import javax.imageio.ImageIO;
import java.awt.Desktop;
import java.io.File;
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
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class LauncherApp extends Application {

    private static final String CLIENT_ID     = "worm-launcher1";
    private static final String CLIENT_SECRET = "YZdFkv0-L5K3aADqedEp0zamy2HzcjBcUApskOHxkzSsxE-XjsuCwdUsAIfVBShk";
    private static final String LAUNCHER_VERSION = "1.0.3";

    private static final String DISCORD_APP_ID = "1564662317663391774";
    private static final String DISCORD_LARGE_IMAGE = "https://i.ibb.co/B2cptB0Y/worm-logo.png";
    private static final String IMGBB_API_KEY = "6fa9d19c670bd0068a70d0f053e6c9e7";

    private static final ConcurrentHashMap<String, byte[]> headPngCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> headUrlCache = new ConcurrentHashMap<>();

    private static Path GAME_DIR = Paths.mcDir();

    private GameSession session;
    private Config.Data cfg = Config.load();
    private TrayIcon trayIcon;
    private boolean trayReady = false;
    private Stage stage;
    private StackPane rootPane;
    private VBox contentHolder;

    private boolean autoLaunchFired = false;
    private Path selfJarPath;

    private Label titleLabel;
    private Label statusLabel;
    private TextField nickField;
    private PasswordField passField;
    private Button loginBtn, logoutBtn, oauthBtn, msBtn, openFolder, checkUpd, settingsBtn;
    private CheckBox minimizeBox, autoLaunchBox;
    private ComboBox<Profile> profileBox;
    private Button saveProfileBtn, deleteProfileBtn;
    private ComboBox<String> versionBox;
    private ComboBox<String> loaderBox;
    private ComboBox<String> fabricLoaderBox;
    private ComboBox<String> forgeVersionBox;
    private ComboBox<String> quiltLoaderBox;
    private Button playBtn;
    private ProgressBar progressBar;
    private TextArea log;

    private String pendingLoaderVersion;

    private ImageView bgImageView;
    private MediaView bgMediaView;
    private MediaPlayer bgMediaPlayer;
    private Region tintLayer;

    private static DiscordRPC rpc;
    private static DiscordRichPresence presence;
    private static volatile boolean rpcStarted = false;
    private static Thread rpcThread;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        Platform.setImplicitExit(false);

        selfJarPath = SelfUpdater.detectJar();
        GAME_DIR = Paths.mcDir();

        if (cfg.profiles == null) cfg.profiles = new java.util.ArrayList<>();
        if (cfg.autoCheckUpdates == null) cfg.autoCheckUpdates = true;
        if (cfg.lang == null) cfg.lang = Lang.RU;
        Lang.set(cfg.lang);

        SoundManager.init(cfg.keypressSoundPath);
        SoundManager.setEnabled(!Boolean.FALSE.equals(cfg.soundsEnabled) && cfg.keypressSoundPath != null);

        initLauncherRpc();
        stage.getIcons().add(makeIcon());

        titleLabel = new Label(Lang.t("app.title"));
        titleLabel.getStyleClass().add("title-label");

        statusLabel = new Label(Lang.t("status.not_logged"));
        statusLabel.getStyleClass().add("subtitle-label");

        nickField = new TextField();
        nickField.setPromptText(Lang.t("field.nick"));
        nickField.setPrefWidth(160);
        if (cfg.lastNick != null) nickField.setText(cfg.lastNick);

        passField = new PasswordField();
        passField.setPromptText(Lang.t("field.pass"));
        passField.setPrefWidth(160);

        loginBtn    = new Button(Lang.t("btn.login_ely"));
        logoutBtn   = new Button(Lang.t("btn.logout"));
        oauthBtn    = new Button(Lang.t("btn.oauth"));
        msBtn       = new Button(Lang.t("btn.ms"));
        openFolder  = new Button(Lang.t("btn.open_folder"));
        checkUpd    = new Button(Lang.t("btn.check_updates"));
        settingsBtn = new Button(Lang.t("btn.settings"));

        minimizeBox = new CheckBox(Lang.t("chk.minimize"));
        minimizeBox.setSelected(Boolean.TRUE.equals(cfg.minimizeOnPlay));
        minimizeBox.selectedProperty().addListener((o, a, b) -> { cfg.minimizeOnPlay = b; Config.save(cfg); });

        autoLaunchBox = new CheckBox(Lang.t("chk.autolaunch"));
        autoLaunchBox.setSelected(Boolean.TRUE.equals(cfg.autoLaunch));
        autoLaunchBox.selectedProperty().addListener((o, a, b) -> {
            cfg.autoLaunch = b;
            Config.save(cfg);
            if (!b) autoLaunchFired = false;
        });

        profileBox = new ComboBox<>();
        profileBox.setPrefWidth(180);
        profileBox.setPromptText(Lang.t("prompt.profile"));
        refreshProfiles();

        saveProfileBtn = new Button(Lang.t("btn.save_profile"));
        deleteProfileBtn = new Button(Lang.t("btn.delete_profile"));

        versionBox = new ComboBox<>();
        versionBox.setPrefWidth(200);
        versionBox.setPromptText(Lang.t("prompt.loading_versions"));
        versionBox.setDisable(true);

        loaderBox = new ComboBox<>(FXCollections.observableArrayList("Vanilla", "Fabric", "Forge", "Quilt"));
        loaderBox.setValue("Vanilla");
        loaderBox.setPrefWidth(120);

        fabricLoaderBox = new ComboBox<>();
        fabricLoaderBox.setPrefWidth(150);
        fabricLoaderBox.setPromptText(Lang.t("prompt.loader"));
        fabricLoaderBox.setDisable(true);
        fabricLoaderBox.setVisible(false);
        fabricLoaderBox.setManaged(false);

        forgeVersionBox = new ComboBox<>();
        forgeVersionBox.setPrefWidth(150);
        forgeVersionBox.setPromptText(Lang.t("prompt.forge"));
        forgeVersionBox.setDisable(true);
        forgeVersionBox.setVisible(false);
        forgeVersionBox.setManaged(false);

        quiltLoaderBox = new ComboBox<>();
        quiltLoaderBox.setPrefWidth(150);
        quiltLoaderBox.setPromptText(Lang.t("prompt.quilt"));
        quiltLoaderBox.setDisable(true);
        quiltLoaderBox.setVisible(false);
        quiltLoaderBox.setManaged(false);

        playBtn = new Button(Lang.t("btn.play"));
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

        double w = cfg.windowWidth  != null ? cfg.windowWidth  : 860;
        double h = cfg.windowHeight != null ? cfg.windowHeight : 700;
        Scene scene = new Scene(rootPane, w, h);

        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        ThemeManager.apply(scene, cfg);
        applyFont(scene);

        stage.setTitle(Lang.t("app.title") + " " + LAUNCHER_VERSION);
        stage.setScene(scene);
        stage.setMinWidth(620);
        stage.setMinHeight(460);

        setupTray();
        applySavedWindowMode();

        rebuildBgLayer();
        applyLayout();

        wireSounds();

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
                trayIcon.displayMessage(Lang.t("app.title"),
                        Lang.t("tray.minimized"),
                        TrayIcon.MessageType.INFO);
            }
        });

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
                    logLine("✓ " + releases.size() + " versions loaded");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> logLine("✗ manifest: " + ex.getMessage()));
            }
        }, "manifest").start();

        versionBox.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && !newV.isBlank()) { cfg.lastVersion = newV; Config.save(cfg); }
            if ("Fabric".equals(loaderBox.getValue())) reloadFabricLoaders(newV);
            if ("Forge".equals(loaderBox.getValue()))  reloadForgeVersions(newV);
            if ("Quilt".equals(loaderBox.getValue()))  reloadQuiltLoaders(newV);
        });

        loaderBox.valueProperty().addListener((obs, oldV, newV) -> {
            boolean isFabric = "Fabric".equals(newV);
            boolean isForge  = "Forge".equals(newV);
            boolean isQuilt  = "Quilt".equals(newV);
            fabricLoaderBox.setVisible(isFabric); fabricLoaderBox.setManaged(isFabric);
            forgeVersionBox.setVisible(isForge);  forgeVersionBox.setManaged(isForge);
            quiltLoaderBox.setVisible(isQuilt);   quiltLoaderBox.setManaged(isQuilt);
            if (isFabric) reloadFabricLoaders(versionBox.getValue());
            else { fabricLoaderBox.getItems().clear(); fabricLoaderBox.setDisable(true); }
            if (isForge) reloadForgeVersions(versionBox.getValue());
            else { forgeVersionBox.getItems().clear(); forgeVersionBox.setDisable(true); }
            if (isQuilt) reloadQuiltLoaders(versionBox.getValue());
            else { quiltLoaderBox.getItems().clear(); quiltLoaderBox.setDisable(true); }
            applyLayout();
        });

        saveProfileBtn.setOnAction(e -> saveCurrentProfile());
        deleteProfileBtn.setOnAction(e -> deleteSelectedProfile());
        profileBox.valueProperty().addListener((obs, oldP, newP) -> {
            if (newP != null) applyProfile(newP);
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
                        setStatus((gs.isEly() ? "ely: " : "mojang: ") + gs.username(), true);
                        refreshLauncherPresenceImages();
                        updateLauncherPresence("logged in as " + gs.username());
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
                            logLine("→ open " + dc.verificationUri() + " and enter code:");
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
                setStatus(Lang.t("status.not_logged"), false);
                logLine("✓ session cleared");
                if (presence != null) { presence.smallImageKey = ""; presence.smallImageText = ""; }
                updateLauncherPresence("idle");
            } catch (Exception ex) { logLine("✗ " + ex.getMessage()); }
        });

        openFolder.setOnAction(e -> {
            try { Files.createDirectories(GAME_DIR); Desktop.getDesktop().open(GAME_DIR.toFile()); logLine("→ opened " + GAME_DIR); }
            catch (Exception ex) { logLine("✗ " + ex.getMessage()); }
        });

        checkUpd.setOnAction(e -> checkUpdatesAsync(true));

        playBtn.setOnAction(e -> startGame());

        GameSession cached = TokenStore.load();
        if (cached != null) {
            session = cached;
            playBtn.setDisable(false);
            setStatus((cached.isEly() ? "ely: " : "mojang: ") + cached.username(), true);
            logLine("saved session: " + cached);
            refreshLauncherPresenceImages();
            updateLauncherPresence("logged in as " + cached.username());
            if (autoLaunchBox.isSelected()) Platform.runLater(() -> maybeAutoLaunch(true));
        }

        stage.show();

        if (Boolean.TRUE.equals(cfg.autoCheckUpdates)) {
            startupUpdateCheck();
        }
    }

    @Override
    public void stop() {
        stopLauncherRpc();
        SoundManager.stop();
        if (bgMediaPlayer != null) {
            try { bgMediaPlayer.stop(); bgMediaPlayer.dispose(); } catch (Exception ignored) {}
            bgMediaPlayer = null;
        }
    }

    // ============================================================
    // AUTO-UPDATE
    // ============================================================

    private void startupUpdateCheck() {
        new Thread(() -> {
            try {
                UpdateChecker.UpdateInfo info = UpdateChecker.check(LAUNCHER_VERSION);
                Platform.runLater(() -> {
                    if (info != null) {
                        logLine(Lang.t("upd.startup_available", info.tagName(), LAUNCHER_VERSION));
                    } else {
                        logLine(Lang.t("upd.startup_latest", LAUNCHER_VERSION));
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> logLine(Lang.t("upd.startup_failed") + ex.getMessage()));
            }
        }, "startup-upd-check").start();
    }

    private void checkUpdatesAsync(boolean loud) {
        if (loud) logLine(Lang.t("upd.checking"));
        new Thread(() -> {
            try {
                UpdateChecker.UpdateInfo info = UpdateChecker.check(LAUNCHER_VERSION);
                Platform.runLater(() -> {
                    if (info == null) {
                        logLine(Lang.t("upd.latest") + " (" + LAUNCHER_VERSION + ")");
                        if (loud) showInfo(Lang.t("upd.title"), Lang.t("upd.latest") + ": " + LAUNCHER_VERSION);
                    } else {
                        logLine(Lang.t("upd.available") + " " + info.tagName());
                        showUpdateDialog(info);
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    logLine(Lang.t("upd.startup_failed") + ex.getMessage());
                    if (loud) showInfo(Lang.t("upd.title"), Lang.t("upd.check_failed") + ex.getMessage());
                });
            }
        }, "upd-check").start();
    }

    private void showUpdateDialog(UpdateChecker.UpdateInfo info) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(stage);
        alert.setTitle(Lang.t("upd.title"));
        alert.setHeaderText(Lang.t("upd.header") + " " + info.tagName()
                + " (" + Lang.t("upd.you_have") + " " + LAUNCHER_VERSION + ")");

        String body = info.body() == null ? "" : info.body();
        if (body.length() > 800) body = body.substring(0, 800) + "…";
        TextArea ta = new TextArea(body);
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.setPrefRowCount(10);
        ta.setPrefColumnCount(50);
        alert.getDialogPane().setContent(ta);

        boolean canSelfUpdate = selfJarPath != null
                && info.assetUrl() != null && !info.assetUrl().isBlank();

        ButtonType primary;
        if (canSelfUpdate) primary = new ButtonType(Lang.t("upd.download_install"), ButtonBar.ButtonData.OK_DONE);
        else               primary = new ButtonType(Lang.t("upd.open_page"), ButtonBar.ButtonData.OK_DONE);
        ButtonType later = new ButtonType(Lang.t("upd.later"), ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(primary, later);

        Optional<ButtonType> res = alert.showAndWait();
        if (res.isPresent() && res.get() == primary) {
            if (canSelfUpdate) {
                downloadAndRestart(info);
            } else {
                UpdateChecker.openInBrowser(info.htmlUrl());
                logLine("→ opened " + info.htmlUrl());
                if (selfJarPath == null) logLine(Lang.t("upd.jar_only"));
                else logLine(Lang.t("upd.no_asset"));
            }
        }
    }

    private void downloadAndRestart(UpdateChecker.UpdateInfo info) {
        logLine(Lang.t("upd.downloading") + " " + info.tagName() + " ...");
        new Thread(() -> {
            try {
                Path current = selfJarPath;
                Path dir = current.getParent();
                Path fresh = dir.resolve(current.getFileName() + ".new");

                UpdateChecker.download(info.assetUrl(), fresh, pct -> {
                    if (pct < 0) return;
                    Platform.runLater(() -> logLine("  … " + pct + "%"));
                });

                Platform.runLater(() -> {
                    logLine(Lang.t("upd.downloaded"));
                    try {
                        SelfUpdater.scheduleRestart(current, fresh);
                        logLine(Lang.t("upd.closing"));
                        stopLauncherRpc();
                        if (bgMediaPlayer != null) {
                            try { bgMediaPlayer.stop(); bgMediaPlayer.dispose(); } catch (Exception ignored) {}
                        }
                        new Thread(() -> {
                            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                            Platform.exit();
                            System.exit(0);
                        }).start();
                    } catch (Exception ex) {
                        logLine(Lang.t("upd.bat_failed") + ex.getMessage());
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> logLine(Lang.t("upd.download_failed") + ex.getMessage()));
            }
        }, "upd-download").start();
    }

    private void showInfo(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.initOwner(stage);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    // ============================================================
    // PROFILES
    // ============================================================

    private void refreshProfiles() {
        profileBox.setItems(FXCollections.observableArrayList(cfg.profiles));
        if (cfg.profiles.isEmpty()) profileBox.setPromptText(Lang.t("prompt.no_profiles"));
        else profileBox.setPromptText(Lang.t("prompt.profile"));
    }

    private void applyProfile(Profile p) {
        if (p.mcVersion != null && !p.mcVersion.isBlank()) {
            versionBox.setValue(p.mcVersion);
        }
        String loader = p.loader == null ? "Vanilla" : p.loader;
        loaderBox.setValue(loader);
        pendingLoaderVersion = p.loaderVersion;
        logLine("→ profile applied: " + p.name);
    }

    private void saveCurrentProfile() {
        if (versionBox.getValue() == null) { logLine("✗ no version"); return; }
        TextInputDialog dlg = new TextInputDialog("My modpack");
        dlg.setTitle(Lang.t("label.profile"));
        dlg.setHeaderText(Lang.t("label.profile"));
        dlg.initOwner(stage);
        Optional<String> res = dlg.showAndWait();
        if (res.isEmpty() || res.get().isBlank()) return;
        String name = res.get().trim();
        String loader = loaderBox.getValue();
        String lv = null;
        if ("Fabric".equals(loader)) lv = fabricLoaderBox.getValue();
        else if ("Forge".equals(loader)) lv = forgeVersionBox.getValue();
        else if ("Quilt".equals(loader)) lv = quiltLoaderBox.getValue();

        cfg.profiles.removeIf(x -> x.name != null && x.name.equals(name));
        cfg.profiles.add(new Profile(name, versionBox.getValue(), loader, lv));
        Config.save(cfg);
        refreshProfiles();
        for (Profile p : cfg.profiles) if (p.name.equals(name)) profileBox.setValue(p);
        logLine("✓ profile saved: " + name);
    }

    private void deleteSelectedProfile() {
        Profile p = profileBox.getValue();
        if (p == null) return;
        cfg.profiles.removeIf(x -> x.name != null && x.name.equals(p.name));
        Config.save(cfg);
        refreshProfiles();
        logLine("✓ profile deleted: " + p.name);
    }

    // ============================================================
    // SOUND / FONT
    // ============================================================

    private void wireSounds() {
        for (Button b : new Button[]{loginBtn, logoutBtn, oauthBtn, msBtn, openFolder, checkUpd,
                settingsBtn, playBtn, saveProfileBtn, deleteProfileBtn}) {
            if (b == null) continue;
            b.addEventHandler(ActionEvent.ACTION, e -> SoundManager.playKeypress());
        }
    }

    private void applyFont(Scene scene) {
        if (cfg.customFontPath == null || cfg.customFontPath.isBlank()) return;
        try {
            Path p = Path.of(cfg.customFontPath);
            if (!Files.exists(p)) return;
            Font f = Font.loadFont(p.toUri().toString(), 14);
            if (f == null) return;
            String fam = f.getFamily().replace("'", "");
            String css = ".root, .label, .button, .text-field, .text-area, " +
                    ".combo-box, .list-cell, .check-box, .radio-button, .tooltip " +
                    "{ -fx-font-family: '" + fam + "'; }";
            String b64 = Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
            scene.getStylesheets().add("data:text/css;base64," + b64);
        } catch (Exception ignored) {}
    }

    // ============================================================
    // LAYOUT
    // ============================================================

    private void applyLayout() {
        String layout = cfg.buttonLayout == null ? "TOP" : cfg.buttonLayout;
        boolean logInSeparate = logInSeparateWindow();

        detachAll(titleLabel, statusLabel, nickField, passField, loginBtn, logoutBtn,
                oauthBtn, msBtn, openFolder, checkUpd, settingsBtn,
                minimizeBox, autoLaunchBox, profileBox, saveProfileBtn, deleteProfileBtn,
                versionBox, loaderBox, fabricLoaderBox, forgeVersionBox, quiltLoaderBox,
                playBtn, progressBar);
        if (!logInSeparate) detachAll(log);

        contentHolder.getChildren().clear();

        HBox titleRow = new HBox(12, titleLabel, statusLabel);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        HBox profileRow = new HBox(8,
                new Label(Lang.t("label.profile")), profileBox, saveProfileBtn, deleteProfileBtn);
        profileRow.setAlignment(Pos.CENTER_LEFT);

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
                    new Label(Lang.t("label.version")), versionBox,
                    new Label(Lang.t("label.loader")), loaderBox, fabricLoaderBox, forgeVersionBox, quiltLoaderBox,
                    playBtn, progressBar);
            playRow.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(versionBox, Priority.ALWAYS);

            VBox right = new VBox(10, titleRow, profileRow, playRow);
            HBox.setHgrow(right, Priority.ALWAYS);

            if (!logInSeparate && !Boolean.FALSE.equals(cfg.showLog)) {
                right.getChildren().addAll(new Label(Lang.t("label.log")), log);
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
                    new Label(Lang.t("label.version")), versionBox,
                    new Label(Lang.t("label.loader")), loaderBox, fabricLoaderBox, forgeVersionBox, quiltLoaderBox,
                    playBtn, progressBar);
            playRow.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(versionBox, Priority.ALWAYS);

            contentHolder.getChildren().addAll(titleRow, authRow, actionRow, optsRow, profileRow, new Separator(), playRow);

            if (!logInSeparate && !Boolean.FALSE.equals(cfg.showLog)) {
                contentHolder.getChildren().addAll(new Label(Lang.t("label.log")), log);
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

        private void rebuildBgLayer() {
        if (bgMediaPlayer != null) {
            try { bgMediaPlayer.stop(); bgMediaPlayer.dispose(); } catch (Exception ignored) {}
            bgMediaPlayer = null;
        }
        bgMediaView.setMediaPlayer(null);
        bgMediaView.setVisible(false);
        bgImageView.setVisible(false);
        bgImageView.setImage(null);

        boolean userBgValid = cfg.bgImagePath != null && Files.exists(Path.of(cfg.bgImagePath));

        // нет пользовательского фона → fallback на worm_logo_mic.png
        if (!userBgValid) {
            Image def = loadBundledImage("worm_logo_mic.png");
            if (def != null) {
                bgImageView.setImage(def);
                bgImageView.setOpacity(1.0);
                bgImageView.setVisible(true);
            }
            return;
        }

        String lower = cfg.bgImagePath.toLowerCase();
        double opacity = cfg.bgOpacity != null ? cfg.bgOpacity : 0.55;

        if (lower.endsWith(".mp4") || lower.endsWith(".flv") || lower.endsWith(".m4v")) {
            try {
                Media m = new Media(Path.of(cfg.bgImagePath).toUri().toString());
                bgMediaPlayer = new MediaPlayer(m);
                bgMediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);
                bgMediaPlayer.setMute(true);
                bgMediaPlayer.setOnError(() ->
                        logLine("⚠ video bg: " + bgMediaPlayer.getError().getMessage()));
                bgMediaPlayer.play();
                bgMediaView.setMediaPlayer(bgMediaPlayer);
                bgMediaView.setOpacity(opacity);
                bgMediaView.setVisible(true);
            } catch (Exception e) {
                logLine("⚠ video bg: " + e.getMessage());
            }
        } else {
            try {
                Image img = new Image(Path.of(cfg.bgImagePath).toUri().toString(), true);
                bgImageView.setImage(img);
                bgImageView.setOpacity(opacity);
                bgImageView.setVisible(true);
            } catch (Exception e) {
                logLine("⚠ image bg: " + e.getMessage());
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

    private void rebuildMainUi() {
        if (titleLabel != null) titleLabel.setText(Lang.t("app.title"));
        if (statusLabel != null && session == null) statusLabel.setText(Lang.t("status.not_logged"));
        if (loginBtn != null) loginBtn.setText(Lang.t("btn.login_ely"));
        if (logoutBtn != null) logoutBtn.setText(Lang.t("btn.logout"));
        if (oauthBtn != null) oauthBtn.setText(Lang.t("btn.oauth"));
        if (msBtn != null) msBtn.setText(Lang.t("btn.ms"));
        if (openFolder != null) openFolder.setText(Lang.t("btn.open_folder"));
        if (checkUpd != null) checkUpd.setText(Lang.t("btn.check_updates"));
        if (settingsBtn != null) settingsBtn.setText(Lang.t("btn.settings"));
        if (playBtn != null) playBtn.setText(Lang.t("btn.play"));
        if (minimizeBox != null) minimizeBox.setText(Lang.t("chk.minimize"));
        if (autoLaunchBox != null) autoLaunchBox.setText(Lang.t("chk.autolaunch"));
        if (saveProfileBtn != null) saveProfileBtn.setText(Lang.t("btn.save_profile"));
        if (deleteProfileBtn != null) deleteProfileBtn.setText(Lang.t("btn.delete_profile"));
        if (nickField != null) nickField.setPromptText(Lang.t("field.nick"));
        if (passField != null) passField.setPromptText(Lang.t("field.pass"));
        if (profileBox != null) {
            if (cfg.profiles.isEmpty()) profileBox.setPromptText(Lang.t("prompt.no_profiles"));
            else profileBox.setPromptText(Lang.t("prompt.profile"));
        }
        if (fabricLoaderBox != null) fabricLoaderBox.setPromptText(Lang.t("prompt.loader"));
        if (forgeVersionBox != null) forgeVersionBox.setPromptText(Lang.t("prompt.forge"));
        if (quiltLoaderBox != null) quiltLoaderBox.setPromptText(Lang.t("prompt.quilt"));
        if (stage != null) stage.setTitle(Lang.t("app.title") + " " + LAUNCHER_VERSION);
        applyLayout();
    }

    private void openSettings() {
        Stage s = new Stage();
        s.initOwner(stage);
        s.initModality(Modality.APPLICATION_MODAL);
        s.setTitle(Lang.t("settings.title"));

        Label head = new Label(Lang.t("settings.header"));
        head.getStyleClass().add("title-label");
        head.setStyle("-fx-font-size: 16px;");

        ComboBox<String> themeCombo = new ComboBox<>(FXCollections.observableArrayList(ThemeManager.listPresets()));
        themeCombo.setValue(cfg.theme != null ? cfg.theme : "WormGreen");
        themeCombo.setPrefWidth(240);

        CheckBox darkBox = new CheckBox(Lang.t("settings.dark"));
        darkBox.setSelected(Boolean.TRUE.equals(cfg.darkMode));

        ComboBox<String> layoutCombo = new ComboBox<>(FXCollections.observableArrayList("TOP", "LEFT"));
        layoutCombo.setValue(cfg.buttonLayout != null ? cfg.buttonLayout : "TOP");
        layoutCombo.setPrefWidth(180);

        // языки
        ComboBox<String> langCombo = new ComboBox<>(FXCollections.observableArrayList(
                Lang.t("settings.lang_ru"), Lang.t("settings.lang_en")));
        langCombo.setValue(Lang.EN.equals(Lang.current()) ? Lang.t("settings.lang_en") : Lang.t("settings.lang_ru"));
        langCombo.setPrefWidth(180);

        // фон
        Label bgPath = new Label(cfg.bgImagePath == null ? Lang.t("settings.sound_none") : cfg.bgImagePath);
        bgPath.getStyleClass().add("subtitle-label");
        bgPath.setWrapText(true);
        Button pickBg = new Button(Lang.t("settings.pick"));
        Button clearBg = new Button(Lang.t("settings.clear"));

        Slider bgOpacity = new Slider(0.0, 1.0, cfg.bgOpacity != null ? cfg.bgOpacity : 0.55);
        bgOpacity.setShowTickMarks(true);
        bgOpacity.setMajorTickUnit(0.25);
        Label opacityLbl = new Label(Lang.t("settings.bg_opacity") + ": " + Math.round(bgOpacity.getValue() * 100) + "%");
        bgOpacity.valueProperty().addListener((o, a, b) -> {
            cfg.bgOpacity = b.doubleValue();
            setBgOpacity(cfg.bgOpacity);
            opacityLbl.setText(Lang.t("settings.bg_opacity") + ": " + Math.round(cfg.bgOpacity * 100) + "%");
        });

        pickBg.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Images & video",
                            "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp",
                            "*.mp4", "*.flv", "*.m4v"),
                    new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
                    new FileChooser.ExtensionFilter("Video", "*.mp4", "*.flv", "*.m4v"),
                    new FileChooser.ExtensionFilter("All files", "*.*")
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
            bgPath.setText(Lang.t("settings.sound_none"));
            rebuildBgLayer();
        });

        // шрифт
        Label fontPath = new Label(cfg.customFontPath == null ? Lang.t("settings.default") : cfg.customFontPath);
        fontPath.getStyleClass().add("subtitle-label");
        fontPath.setWrapText(true);
        Button pickFont = new Button(Lang.t("settings.font_pick"));
        Button clearFont = new Button(Lang.t("settings.font_reset"));
        pickFont.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Fonts", "*.ttf", "*.otf"));
            java.io.File f = fc.showOpenDialog(s);
            if (f != null) {
                cfg.customFontPath = f.getAbsolutePath();
                fontPath.setText(cfg.customFontPath);
            }
        });
        clearFont.setOnAction(e -> {
            cfg.customFontPath = null;
            fontPath.setText(Lang.t("settings.default"));
        });

        // звук
        Label soundPath = new Label(cfg.keypressSoundPath == null || cfg.keypressSoundPath.isBlank()
                ? Lang.t("settings.sound_none") : cfg.keypressSoundPath);
        soundPath.getStyleClass().add("subtitle-label");
        soundPath.setWrapText(true);
        Button pickSound = new Button(Lang.t("settings.sound_pick"));
        Button resetSound = new Button(Lang.t("settings.sound_reset"));
        Button testSound = new Button(Lang.t("settings.sound_test"));

        CheckBox soundsBox = new CheckBox();

        Runnable updateSoundUI = () -> {
            boolean hasSound = cfg.keypressSoundPath != null
                    && !cfg.keypressSoundPath.isBlank()
                    && Files.exists(Path.of(cfg.keypressSoundPath));
            soundsBox.setDisable(!hasSound);
            if (!hasSound) {
                soundsBox.setSelected(false);
                soundsBox.setText(Lang.t("settings.sound_off"));
            } else {
                soundsBox.setText(soundsBox.isSelected()
                        ? Lang.t("settings.sound_on")
                        : Lang.t("settings.sound_off"));
            }
        };
        soundsBox.selectedProperty().addListener((o, a, b) -> updateSoundUI.run());

        boolean initHasSound = cfg.keypressSoundPath != null
                && !cfg.keypressSoundPath.isBlank()
                && Files.exists(Path.of(cfg.keypressSoundPath));
        soundsBox.setSelected(initHasSound && !Boolean.FALSE.equals(cfg.soundsEnabled));
        updateSoundUI.run();

        pickSound.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Sounds", "*.wav", "*.mp3", "*.aiff", "*.mp4", "*.m4a", "*.aac"),
                    new FileChooser.ExtensionFilter("All files", "*.*")
            );
            java.io.File f = fc.showOpenDialog(s);
            if (f != null) {
                cfg.keypressSoundPath = f.getAbsolutePath();
                soundPath.setText(cfg.keypressSoundPath);
                SoundManager.init(cfg.keypressSoundPath);
                soundsBox.setSelected(true);
                updateSoundUI.run();
            }
        });
        resetSound.setOnAction(e -> {
            cfg.keypressSoundPath = null;
            soundPath.setText(Lang.t("settings.sound_none"));
            SoundManager.init(null);
            soundsBox.setSelected(false);
            updateSoundUI.run();
        });
        testSound.setOnAction(e -> {
            if (cfg.keypressSoundPath == null || cfg.keypressSoundPath.isBlank()) {
                logLine("⚠ choose sound file first");
                return;
            }
            SoundManager.init(cfg.keypressSoundPath);
            SoundManager.playKeypress();
        });

        CheckBox autoCheckUpdBox = new CheckBox(Lang.t("settings.auto_check"));
        autoCheckUpdBox.setSelected(!Boolean.FALSE.equals(cfg.autoCheckUpdates));

        // режим окна
        ToggleGroup wmGroup = new ToggleGroup();
        RadioButton wmWindowed   = new RadioButton(Lang.t("settings.windowed"));
        RadioButton wmMaximized  = new RadioButton(Lang.t("settings.maximized"));
        RadioButton wmFullscreen = new RadioButton(Lang.t("settings.fullscreen"));
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

        CheckBox showLogBox = new CheckBox(Lang.t("settings.show_log"));
        showLogBox.setSelected(!Boolean.FALSE.equals(cfg.showLog));

        Slider logOpacity = new Slider(0.2, 1.0, cfg.logOpacity != null ? cfg.logOpacity : 1.0);
        logOpacity.setShowTickMarks(true);
        logOpacity.setMajorTickUnit(0.2);
        Label logOpLbl = new Label(Lang.t("settings.log_opacity") + ": " + Math.round(logOpacity.getValue() * 100) + "%");
        logOpacity.valueProperty().addListener((o, a, b) -> {
            cfg.logOpacity = b.doubleValue();
            log.setOpacity(cfg.logOpacity);
            logOpLbl.setText(Lang.t("settings.log_opacity") + ": " + Math.round(cfg.logOpacity * 100) + "%");
        });

        Button openLogBtn = new Button(Lang.t("settings.open_log"));
        openLogBtn.setOnAction(e -> {
            openLogWindow();
            s.close();
        });

        Button uninstallBtn = new Button(Lang.t("settings.uninstall"));
        uninstallBtn.setStyle("-fx-text-fill: #b71c1c;");
        uninstallBtn.setOnAction(e -> {
            try {
                Stage us = new Stage();
                Uninstaller.run(us, Paths.launcherDir());
                us.show();
                s.close();
            } catch (Exception ex) { logLine("✗ uninstall: " + ex.getMessage()); }
        });
        
        Button shortcutBtn = new Button(Lang.t("settings.shortcut"));
shortcutBtn.setOnAction(e -> {
    Path jd = selfJarPath != null ? selfJarPath.getParent() : Paths.launcherDir();
    DirectoryChooser dc = new DirectoryChooser();
    dc.setTitle(Lang.t("shortcut.pick_title"));
    Path desktop = Installer.defaultDesktop();
    if (desktop != null && Files.isDirectory(desktop)) dc.setInitialDirectory(desktop.toFile());
    File chosen = dc.showDialog(s);
    if (chosen == null) return;
    try {
        Path lnk = Installer.createShortcutAt(jd, chosen.toPath());
        logLine("✓ shortcut: " + lnk);
        Alert ok = new Alert(Alert.AlertType.INFORMATION);
        ok.initOwner(s);
        ok.setTitle(Lang.t("settings.shortcut"));
        ok.setHeaderText(Lang.t("shortcut.done"));
        ok.setContentText(lnk.toString());
        ok.showAndWait();
    } catch (Exception ex) {
        logLine("✗ shortcut: " + ex.getMessage());
        Alert err = new Alert(Alert.AlertType.ERROR);
        err.initOwner(s);
        err.setTitle(Lang.t("settings.shortcut"));
        err.setHeaderText(null);
        err.setContentText(ex.getMessage());
        err.showAndWait();
    }
});

        Button okBtn = new Button(Lang.t("settings.apply"));
        okBtn.getStyleClass().add("play-button");
        Button cancelBtn = new Button(Lang.t("settings.cancel"));
        HBox btnRow = new HBox(8, okBtn, cancelBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        final String oldMode = initialMode;

        okBtn.setOnAction(e -> {
            // язык
            String pickedLangKey = langCombo.getValue();
            boolean wantEn = pickedLangKey != null && pickedLangKey.equals(Lang.t("settings.lang_en"));
            cfg.lang = wantEn ? Lang.EN : Lang.RU;
            Lang.set(cfg.lang);

            cfg.theme = themeCombo.getValue();
            cfg.darkMode = darkBox.isSelected();
            cfg.showLog = showLogBox.isSelected();
            cfg.buttonLayout = layoutCombo.getValue();
            cfg.autoCheckUpdates = autoCheckUpdBox.isSelected();

            boolean hasSound = cfg.keypressSoundPath != null
                    && !cfg.keypressSoundPath.isBlank()
                    && Files.exists(Path.of(cfg.keypressSoundPath));
            cfg.soundsEnabled = hasSound && soundsBox.isSelected();
            SoundManager.setEnabled(cfg.soundsEnabled);
            SoundManager.init(hasSound ? cfg.keypressSoundPath : null);

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
                    applyFont(st.getScene());
                }
            }
            updateTint();

            if (modeChanged) applyWindowMode(cfg.windowMode);

            applyLayout();
            rebuildMainUi();

            s.close();
        });
        cancelBtn.setOnAction(e -> s.close());

        VBox panel = new VBox(10,
                head,
                new Separator(),
                new Label(Lang.t("settings.lang")), langCombo,
                new Separator(),
                new Label(Lang.t("settings.theme")), themeCombo,
                darkBox,
                new Label(Lang.t("settings.layout")), layoutCombo,
                new Separator(),
                new Label(Lang.t("settings.bg")), bgPath, new HBox(8, pickBg, clearBg),
                opacityLbl, bgOpacity,
                new Separator(),
                new Label(Lang.t("settings.font")), fontPath, new HBox(8, pickFont, clearFont),
                new Separator(),
                new Label(Lang.t("settings.sound")), soundPath,
                new HBox(8, pickSound, resetSound, testSound),
                soundsBox,
                new Separator(),
                new Label(Lang.t("settings.updates")), autoCheckUpdBox,
                new Separator(),
                new Label(Lang.t("settings.window")), wmRow,
                new Separator(),
                new Label(Lang.t("settings.log")), showLogBox,
                logOpLbl, logOpacity,
                new HBox(8, openLogBtn),
                new Separator(),
                new HBox(8, shortcutBtn, uninstallBtn),
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
        applyFont(scene);

        s.setScene(scene);
        s.showAndWait();
    }

    private void openLogWindow() {
        if (logInSeparateWindow()) return;

        if (log.getParent() instanceof Pane p) p.getChildren().remove(log);
        applyLayout();

        Stage logStage = new Stage();
        logStage.initOwner(stage);
        logStage.setTitle(Lang.t("log.title"));
        logStage.getIcons().add(makeIcon());

        VBox box = new VBox(log);
        box.setPadding(new Insets(8));
        VBox.setVgrow(log, Priority.ALWAYS);
        VBox.setVgrow(box, Priority.ALWAYS);

        Scene sc = new Scene(box, 640, 400);
        sc.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        ThemeManager.apply(sc, cfg);
        applyFont(sc);

        logStage.setScene(sc);
        logStage.setOnCloseRequest(e -> {
            box.getChildren().remove(log);
            applyLayout();
        });
        logStage.show();
    }

    private void reloadFabricLoaders(String mcVersion) {
        if (mcVersion == null || mcVersion.isBlank()) return;
        fabricLoaderBox.setDisable(true);
        fabricLoaderBox.getItems().clear();
        fabricLoaderBox.setPromptText(Lang.t("prompt.loading_loaders"));
        logLine("→ fabric loaders for " + mcVersion + " ...");

        new Thread(() -> {
            try {
                List<String> loaders = VersionManifest.fetchFabricLoaders(mcVersion);
                Platform.runLater(() -> {
                    fabricLoaderBox.setItems(FXCollections.observableArrayList(loaders));
                    if (!loaders.isEmpty()) fabricLoaderBox.setValue(loaders.get(0));
                    fabricLoaderBox.setDisable(loaders.isEmpty());
                    fabricLoaderBox.setPromptText(loaders.isEmpty() ? Lang.t("prompt.no_loaders") : Lang.t("prompt.loader"));
                    applyPendingToBox(fabricLoaderBox, loaders);
                    logLine("✓ fabric loaders: " + loaders.size());
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    fabricLoaderBox.setPromptText(Lang.t("prompt.no_loaders"));
                    logLine("✗ fabric loaders: " + ex.getMessage());
                });
            }
        }, "fabric-loaders").start();
    }

    private void reloadForgeVersions(String mcVersion) {
        if (mcVersion == null || mcVersion.isBlank()) return;
        forgeVersionBox.setDisable(true);
        forgeVersionBox.getItems().clear();
        forgeVersionBox.setPromptText(Lang.t("prompt.loading_forge"));
        logLine("→ forge versions for " + mcVersion + " ...");

        new Thread(() -> {
            try {
                List<String> versions = VersionManifest.fetchForgeVersions(mcVersion);
                Platform.runLater(() -> {
                    forgeVersionBox.setItems(FXCollections.observableArrayList(versions));
                    if (!versions.isEmpty()) forgeVersionBox.setValue(versions.get(0));
                    forgeVersionBox.setDisable(versions.isEmpty());
                    forgeVersionBox.setPromptText(versions.isEmpty() ? Lang.t("prompt.no_forge") : Lang.t("prompt.forge"));
                    applyPendingToBox(forgeVersionBox, versions);
                    logLine("✓ forge versions: " + versions.size());
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    forgeVersionBox.setPromptText(Lang.t("prompt.no_forge"));
                    logLine("✗ forge versions: " + ex.getMessage());
                });
            }
        }, "forge-versions").start();
    }

    private void reloadQuiltLoaders(String mcVersion) {
        if (mcVersion == null || mcVersion.isBlank()) return;
        quiltLoaderBox.setDisable(true);
        quiltLoaderBox.getItems().clear();
        quiltLoaderBox.setPromptText(Lang.t("prompt.loading_quilt"));
        logLine("→ quilt loaders for " + mcVersion + " ...");

        new Thread(() -> {
            try {
                List<String> loaders = VersionManifest.fetchQuiltLoaders(mcVersion);
                Platform.runLater(() -> {
                    quiltLoaderBox.setItems(FXCollections.observableArrayList(loaders));
                    if (!loaders.isEmpty()) quiltLoaderBox.setValue(loaders.get(0));
                    quiltLoaderBox.setDisable(loaders.isEmpty());
                    quiltLoaderBox.setPromptText(loaders.isEmpty() ? Lang.t("prompt.no_quilt") : Lang.t("prompt.quilt"));
                    applyPendingToBox(quiltLoaderBox, loaders);
                    logLine("✓ quilt loaders: " + loaders.size());
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    quiltLoaderBox.setPromptText(Lang.t("prompt.no_quilt"));
                    logLine("✗ quilt loaders: " + ex.getMessage());
                });
            }
        }, "quilt-loaders").start();
    }

    private void applyPendingToBox(ComboBox<String> box, List<String> items) {
        if (pendingLoaderVersion == null) return;
        if (items.contains(pendingLoaderVersion)) {
            box.setValue(pendingLoaderVersion);
            pendingLoaderVersion = null;
        }
    }

    private void maybeAutoLaunch(boolean enabled) {
        if (autoLaunchFired) return;
        if (!enabled || session == null || versionBox.getValue() == null || playBtn.isDisabled()) return;
        autoLaunchFired = true;
        logLine("→ auto-launch MC (once per session) ...");
        startGame();
    }

    private void startGame() {
        if (session == null) { logLine(Lang.t("game.no_session")); return; }
        String ver = versionBox.getValue();
        if (ver == null || ver.isBlank()) { logLine(Lang.t("game.no_version")); return; }

        boolean isFabric = "Fabric".equals(loaderBox.getValue());
        boolean isForge  = "Forge".equals(loaderBox.getValue());
        boolean isQuilt  = "Quilt".equals(loaderBox.getValue());
        final String fabricLoaderVer = isFabric ? fabricLoaderBox.getValue() : null;
        final String forgeLoaderVer  = isForge  ? forgeVersionBox.getValue()  : null;
        final String quiltLoaderVer  = isQuilt  ? quiltLoaderBox.getValue()   : null;

        if (isFabric && (fabricLoaderVer == null || fabricLoaderVer.isBlank())) {
            logLine(Lang.t("game.no_fabric")); return;
        }
        if (isForge && (forgeLoaderVer == null || forgeLoaderVer.isBlank())) {
            logLine(Lang.t("game.no_forge")); return;
        }
        if (isQuilt && (quiltLoaderVer == null || quiltLoaderVer.isBlank())) {
            logLine(Lang.t("game.no_quilt")); return;
        }

        playBtn.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(0);
        logLine(Lang.t("game.launching") + " " + ver
                + (isFabric ? " (fabric " + fabricLoaderVer + ")" : "")
                + (isForge  ? " (forge "  + forgeLoaderVer  + ")" : "")
                + (isQuilt  ? " (quilt "  + quiltLoaderVer  + ")" : "")
                + " [" + session.type() + "] ...");

        final boolean hiddenToTray = trayReady && Boolean.TRUE.equals(cfg.minimizeOnPlay);
        if (hiddenToTray) {
            stage.hide();
            trayIcon.displayMessage(Lang.t("app.title"),
                    Lang.t("game.tray_launching"),
                    TrayIcon.MessageType.INFO);
        }

        new Thread(() -> {
            try {
                updateLauncherPresence("launching MC " + ver);
                stopLauncherRpc();

                String launchId = ver;
                if (isFabric) {
                    Platform.runLater(() -> logLine(Lang.t("game.installing_fabric")));
                    launchId = FabricInstaller.install(GAME_DIR, ver, fabricLoaderVer);
                    final String fid = launchId;
                    Platform.runLater(() -> logLine("✓ fabric profile: " + fid));
                } else if (isForge) {
                    Platform.runLater(() -> logLine(Lang.t("game.installing_forge")));
                    launchId = ForgeInstaller.install(GAME_DIR, ver, forgeLoaderVer, pct ->
                            Platform.runLater(() -> progressBar.setProgress(pct)));
                    final String fid = launchId;
                    Platform.runLater(() -> logLine("✓ forge profile: " + fid));
                } else if (isQuilt) {
                    Platform.runLater(() -> logLine(Lang.t("game.installing_quilt")));
                    launchId = QuiltInstaller.install(GAME_DIR, ver, quiltLoaderVer);
                    final String fid = launchId;
                    Platform.runLater(() -> logLine("✓ quilt profile: " + fid));
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
                    logLine(Lang.t("game.finished") + " " + code);
                    playBtn.setDisable(false);
                    if (hiddenToTray) restoreWindow();
                });
            } catch (Exception ex) {
                initLauncherRpc();
                Platform.runLater(() -> {
                    refreshLauncherPresenceImages();
                    updateLauncherPresence(session != null ? "logged in as " + session.username() : "idle");
                    progressBar.setVisible(false);
                    logLine(Lang.t("game.failed") + ex.getMessage());
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

    private void setupTray() {
        if (!SystemTray.isSupported()) return;
        try {
            SystemTray tray = SystemTray.getSystemTray();
            BufferedImage awtIcon = toAwt(makeIcon());
            PopupMenu menu = new PopupMenu();
            MenuItem show = new MenuItem(Lang.t("tray.open"));
            show.addActionListener(e -> Platform.runLater(this::restoreWindow));
            MenuItem quit = new MenuItem(Lang.t("tray.quit"));
            quit.addActionListener(e -> {
                tray.remove(trayIcon);
                Config.save(cfg);
                stopLauncherRpc();
                SoundManager.stop();
                if (bgMediaPlayer != null) {
                    try { bgMediaPlayer.stop(); bgMediaPlayer.dispose(); } catch (Exception ignored) {}
                }
                Platform.exit();
                System.exit(0);
            });
            menu.add(show);
            menu.addSeparator();
            menu.add(quit);
            trayIcon = new TrayIcon(awtIcon, Lang.t("app.title"), menu);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> Platform.runLater(this::restoreWindow));
            tray.add(trayIcon);
            trayReady = true;
            logLine("✓ tray ready");
        } catch (Exception ex) {
            logLine("⚠ tray failed: " + ex.getMessage());
        }
    }

            private Image makeIcon() {
        Image img = loadBundledImage("worm_logo.png");
        if (img != null) return img;

        // fallback: синтетическая
        int size = 64;
        WritableImage wi = new WritableImage(size, size);
        PixelWriter pw = wi.getPixelWriter();
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
        return wi;
    }

    /** Ищет картинку сначала в classpath (внутри jar), потом рядом с jar, потом в launcherDir. */
    private Image loadBundledImage(String name) {
        // 1) classpath (src/main/resources)
        try (InputStream in = getClass().getResourceAsStream("/" + name)) {
            if (in != null) {
                Image img = new Image(in);
                if (!img.isError() && img.getWidth() > 0) return img;
            }
        } catch (Exception ignored) {}

        // 2) рядом с jar + 3) в папке лаунчера
        java.util.List<Path> candidates = new java.util.ArrayList<>();
        if (selfJarPath != null) {
            candidates.add(selfJarPath.getParent().resolve(name));
            candidates.add(selfJarPath.getParent().resolve("resources").resolve(name));
        }
        candidates.add(Paths.launcherDir().resolve(name));
        candidates.add(Paths.launcherDir().resolve("resources").resolve(name));

        for (Path p : candidates) {
            try {
                if (Files.exists(p)) {
                    Image img = new Image(p.toUri().toString());
                    if (!img.isError() && img.getWidth() > 0) return img;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static BufferedImage toAwt(javafx.scene.image.Image img) {
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        PixelReader pr = img.getPixelReader();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) out.setRGB(x, y, pr.getArgb(x, y));
        return out;
    }

    private void logLine(String s) { log.appendText(s + System.lineSeparator()); }

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
                presence.details = "worm launcher";
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
                Platform.runLater(() -> logLine("✓ Discord RPC ready"));
            } catch (Exception e) {
                Platform.runLater(() -> logLine("⚠ RPC failed: " + e.getMessage()));
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
            Platform.runLater(() -> logLine("✓ head " + nick + " → " + u));
            return url;
        }
        String fallback = "https://skinsystem.ely.by/skins/" + nick + ".png";
        headUrlCache.put(nick, fallback);
        Platform.runLater(() -> logLine("⚠ upload failed, skin url: " + fallback));
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