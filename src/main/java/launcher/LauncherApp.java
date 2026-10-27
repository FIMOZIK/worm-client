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
import launcher.auth.GameSession;
import launcher.auth.MojangAuth;
import launcher.auth.TokenStore;
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

    private final TextArea log = new TextArea();
    private GameSession session;
    private ComboBox<String> versionBox;
    private ProgressBar progressBar;
    private Button playBtn;
    private Label    statusLabel;

    private Config.Data cfg = Config.load();
    private TrayIcon trayIcon;
    private boolean trayReady = false;
    private Stage stage;

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

        Label title = new Label("worm launcher");
        title.getStyleClass().add("title-label");

        statusLabel = new Label("не вошёл");
        statusLabel.setStyle("-fx-text-fill: #8a8a8a; -fx-font-size: 11px;");

        TextField nick = new TextField();
        nick.setPromptText("Ely.by ник");
        nick.setPrefWidth(150);
        if (cfg.lastNick != null) nick.setText(cfg.lastNick);
        PasswordField pass = new PasswordField();
        pass.setPromptText("пароль");
        pass.setPrefWidth(150);

        Button loginBtn   = new Button("Войти (Ely)");
        Button oauthBtn   = new Button("Ely через браузер");
        Button msBtn      = new Button("Войти через Microsoft");
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

        // ---------- Ely: вход по паролю ----------
        loginBtn.setOnAction(e -> {
            loginBtn.setDisable(true);
            String nickText = nick.getText().trim();
            logLine("→ login (ely password): " + nickText);
            new Thread(() -> {
                try {
                    ElyAuth.Session s = ElyAuth.login(nickText, pass.getText());
                    GameSession gs = GameSession.fromEly(s);
                    TokenStore.save(gs);
                    cfg.lastNick = gs.username();
                    Config.save(cfg);
                    Platform.runLater(() -> {
                        session = gs;
                        logLine("✓ " + gs);
                        logLine("  сохранено в " + TokenStore.file());
                        pass.clear();
                        playBtn.setDisable(false);
                        loginBtn.setDisable(false);
                        setStatus("ely: " + gs.username(), true);
                        refreshLauncherPresenceImages();
                        updateLauncherPresence("logged in as " + gs.username());
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

        // ---------- Ely: вход через браузер ----------
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
                        logLine("  сохранено в " + TokenStore.file());
                        playBtn.setDisable(false);
                        oauthBtn.setDisable(false);
                        setStatus("ely: " + gs.username(), true);
                        refreshLauncherPresenceImages();
                        updateLauncherPresence("logged in as " + gs.username());
                        maybeAutoLaunch(autoLaunchBox.isSelected());
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        logLine("✗ Ely OAuth упал: " + ex.getMessage());
                        oauthBtn.setDisable(false);
                    });
                }
            }, "ely-oauth").start();
        });

        // ---------- Microsoft / Mojang ----------
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
                        try {
                            if (Desktop.isDesktopSupported()) {
                                Desktop.getDesktop().browse(java.net.URI.create(dc.verificationUri()));
                            }
                        } catch (Exception ignored) {}
                    });
                    GameSession gs = GameSession.fromMojang(s);
                    TokenStore.save(gs);
                    cfg.lastNick = gs.username();
                    Config.save(cfg);
                    Platform.runLater(() -> {
                        session = gs;
                        logLine("✓ " + gs);
                        logLine("  сохранено в " + TokenStore.file());
                        playBtn.setDisable(false);
                        msBtn.setDisable(false);
                        setStatus("mojang: " + gs.username(), true);
                        refreshLauncherPresenceImages();
                        updateLauncherPresence("logged in as " + gs.username());
                        maybeAutoLaunch(autoLaunchBox.isSelected());
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        logLine("✗ Microsoft: " + ex.getMessage());
                        msBtn.setDisable(false);
                    });
                }
            }, "ms-auth").start();
        });

        // ---------- выход ----------
        logoutBtn.setOnAction(e -> {
            try {
                if (session != null && session.isEly()) {
                    ElyAuth.invalidate(session.toEly());
                }
                TokenStore.clear();
                session = null;
                playBtn.setDisable(true);
                setStatus("не вошёл", false);
                logLine("✓ сессия удалена");
                if (presence != null) {
                    presence.smallImageKey = "";
                    presence.smallImageText = "";
                }
                updateLauncherPresence("idle");
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
                    Thread.sleep(400);
                    Platform.runLater(() ->
                            logLine("✓ установлена версия " + LAUNCHER_VERSION + " (последняя)"));
                } catch (Exception ex) {
                    Platform.runLater(() -> logLine("✗ проверка упала: " + ex.getMessage()));
                }
            }, "check-upd").start();
        });

        // ---------- играть ----------
        playBtn.setOnAction(e -> startGame());

        // ---------- сохранённая сессия ----------
        GameSession cached = TokenStore.load();
        if (cached != null) {
            session = cached;
            playBtn.setDisable(false);
            setStatus((cached.isEly() ? "ely: " : "mojang: ") + cached.username(), true);
            logLine("найдена сохранённая сессия: " + cached);
            refreshLauncherPresenceImages();
            updateLauncherPresence("logged in as " + cached.username());
            if (autoLaunchBox.isSelected()) {
                Platform.runLater(() -> maybeAutoLaunch(true));
            }
        }

        // ---------- разметка ----------
        HBox titleRow = new HBox(12, title, statusLabel);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        HBox authRow   = new HBox(8, nick, pass, loginBtn, logoutBtn);
        authRow.setAlignment(Pos.CENTER_LEFT);
        HBox oauthRow  = new HBox(8, oauthBtn, msBtn, openFolder, checkUpd);
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

    @Override
    public void stop() {
        stopLauncherRpc();
    }

    // ---------- авто-запуск ----------
    private void maybeAutoLaunch(boolean enabled) {
        if (!enabled) return;
        if (session == null) return;
        if (versionBox.getValue() == null) return;
        if (playBtn.isDisabled()) return;
        logLine("→ авто-запуск MC включён, стартую...");
        startGame();
    }

    // ---------- запуск игры ----------
    private void startGame() {
        if (session == null) { logLine("✗ сначала войди"); return; }
        String ver = versionBox.getValue();
        if (ver == null || ver.isBlank()) { logLine("✗ выбери версию"); return; }

        playBtn.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(0);
        logLine("→ запуск Minecraft " + ver + " (" + session.type() + ") ...");

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
                updateLauncherPresence("launching MC " + ver);
                stopLauncherRpc();

                Process p = GameLauncher.launch(session, ver, pct ->
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

    // ---------- статус ----------
    private void setStatus(String text, boolean ok) {
        statusLabel.setText("● " + text);
        statusLabel.setStyle(ok
                ? "-fx-text-fill: #4caf50; -fx-font-size: 11px;"
                : "-fx-text-fill: #8a8a8a; -fx-font-size: 11px;");
    }

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
                stopLauncherRpc();
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

    // ---------- иконка ----------
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

    // ============================================================
    // Discord RPC
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
        if (rpcThread != null) {
            rpcThread.interrupt();
            rpcThread = null;
        }
    }

    // ============================================================
    // Голова
    // ============================================================

    private String resolveHeadUrl(String nick, GameSession sess) {
        if (sess != null && sess.isMojang()) {
            return "https://mc-heads.net/avatar/" + nick + "/64";
        }

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
        Platform.runLater(() -> logLine("⚠ аплоадеры не сработали, использую скин напрямую: " + fallback));
        return fallback;
    }

    private String uploadHead(byte[] png, String nick) {
        String u;
        u = tryImgbb(png, nick);
        if (u != null) return u;
        u = tryCatbox(png, nick);
        if (u != null) return u;
        u = tryLitterbox(png, nick);
        if (u != null) return u;
        u = try0x0(png, nick);
        if (u != null) return u;
        u = tryTmpfiles(png, nick);
        return u;
    }

    private byte[] renderElyHeadPng(String nick) {
        BufferedImage skin = fetchElySkin(nick);
        if (skin == null) return null;

        int scale = 8;
        BufferedImage head = new BufferedImage(8 * scale, 8 * scale, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int rgb = skin.getRGB(8 + x, 8 + y);
                for (int dy = 0; dy < scale; dy++)
                    for (int dx = 0; dx < scale; dx++)
                        head.setRGB(x * scale + dx, y * scale + dy, rgb);
            }
        }

        if (skin.getHeight() >= 64) {
            for (int y = 0; y < 8; y++) {
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
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(head, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            Platform.runLater(() -> logLine("⚠ png-write " + nick + ": " + e.getMessage()));
            return null;
        }
    }

    private BufferedImage fetchElySkin(String nick) {
        String[] candidates = {
                "https://skinsystem.ely.by/skins/" + nick + ".png",
                "https://skinsystem.ely.by/textures/" + nick,
                "http://skinsystem.ely.by/skins/" + nick + ".png"
        };
        for (String candidate : candidates) {
            BufferedImage img = fetchImageFollowingRedirects(candidate, nick, 5);
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
                conn.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) worm-launcher/" + LAUNCHER_VERSION);
                conn.setRequestProperty("Accept", "image/png,image/*;q=0.8,*/*;q=0.5");

                int code = conn.getResponseCode();
                if (code >= 300 && code < 400) {
                    String loc = conn.getHeaderField("Location");
                    if (loc == null || loc.isEmpty()) {
                        final int c = code;
                        Platform.runLater(() -> logLine("⚠ " + nick + ": " + c + " без Location"));
                        return null;
                    }
                    if (loc.startsWith("/")) {
                        URL base = new URL(current);
                        loc = base.getProtocol() + "://" + base.getHost()
                                + (base.getPort() > 0 ? ":" + base.getPort() : "") + loc;
                    }
                    current = loc;
                    continue;
                }

                if (code != 200) {
                    final int c = code;
                    Platform.runLater(() -> logLine("⚠ скин " + nick + ": HTTP " + c));
                    return null;
                }

                try (InputStream in = conn.getInputStream()) {
                    return ImageIO.read(in);
                }
            } catch (Exception e) {
                Platform.runLater(() -> logLine("⚠ скин " + nick + ": " + e.getMessage()));
                return null;
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        Platform.runLater(() -> logLine("⚠ скин " + nick + ": слишком много редиректов"));
        return null;
    }

    // ---------- uploaders ----------

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
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) worm-launcher/" + LAUNCHER_VERSION);

            String head = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"image\"; filename=\""
                    + nick + ".png\"\r\n"
                    + "Content-Type: image/png\r\n\r\n";
            String tail = "\r\n--" + boundary + "--\r\n";

            try (OutputStream out = conn.getOutputStream()) {
                out.write(head.getBytes(StandardCharsets.UTF_8));
                out.write(png);
                out.write(tail.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code != 200) {
                final int c = code;
                Platform.runLater(() -> logLine("  imgbb: HTTP " + c));
                return null;
            }
            try (InputStream in = conn.getInputStream()) {
                String result = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                int idx = result.indexOf("\"url\":\"");
                if (idx < 0) {
                    Platform.runLater(() -> logLine("  imgbb ответ: " + result));
                    return null;
                }
                int start = idx + 7;
                int end = result.indexOf('"', start);
                if (end < 0) return null;

                String u = result.substring(start, end).replace("\\/", "/");
                if (u.startsWith("https://i.ibb.co/")) return u;
                Platform.runLater(() -> logLine("  imgbb url: " + u));
            }
        } catch (Exception e) {
            Platform.runLater(() -> logLine("  imgbb: " + e.getMessage()));
        }
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
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) worm-launcher/" + LAUNCHER_VERSION);

            String head = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"reqtype\"\r\n\r\n"
                    + "fileupload\r\n"
                    + "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"fileToUpload\"; filename=\""
                    + nick + ".png\"\r\n"
                    + "Content-Type: image/png\r\n\r\n";
            String tail = "\r\n--" + boundary + "--\r\n";

            try (OutputStream out = conn.getOutputStream()) {
                out.write(head.getBytes(StandardCharsets.UTF_8));
                out.write(png);
                out.write(tail.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code != 200) {
                final int c = code;
                Platform.runLater(() -> logLine("  catbox: HTTP " + c));
                return null;
            }
            try (InputStream in = conn.getInputStream()) {
                String result = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
                if (result.startsWith("https://files.catbox.moe/")) return result;
                Platform.runLater(() -> logLine("  catbox ответ: " + result));
            }
        } catch (Exception e) {
            Platform.runLater(() -> logLine("  catbox: " + e.getMessage()));
        }
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
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) worm-launcher/" + LAUNCHER_VERSION);

            String head = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"reqtype\"\r\n\r\n"
                    + "fileupload\r\n"
                    + "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"time\"\r\n\r\n"
                    + "72h\r\n"
                    + "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"fileToUpload\"; filename=\""
                    + nick + ".png\"\r\n"
                    + "Content-Type: image/png\r\n\r\n";
            String tail = "\r\n--" + boundary + "--\r\n";

            try (OutputStream out = conn.getOutputStream()) {
                out.write(head.getBytes(StandardCharsets.UTF_8));
                out.write(png);
                out.write(tail.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code != 200) {
                final int c = code;
                Platform.runLater(() -> logLine("  litterbox: HTTP " + c));
                return null;
            }
            try (InputStream in = conn.getInputStream()) {
                String result = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
                if (result.startsWith("https://litter.catbox.moe/")) return result;
                Platform.runLater(() -> logLine("  litterbox ответ: " + result));
            }
        } catch (Exception e) {
            Platform.runLater(() -> logLine("  litterbox: " + e.getMessage()));
        }
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
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) worm-launcher/" + LAUNCHER_VERSION);

            String head = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\""
                    + nick + ".png\"\r\n"
                    + "Content-Type: image/png\r\n\r\n";
            String tail = "\r\n--" + boundary + "--\r\n";

            try (OutputStream out = conn.getOutputStream()) {
                out.write(head.getBytes(StandardCharsets.UTF_8));
                out.write(png);
                out.write(tail.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code != 200) {
                final int c = code;
                Platform.runLater(() -> logLine("  0x0.st: HTTP " + c));
                return null;
            }
            try (InputStream in = conn.getInputStream()) {
                String result = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
                if (result.startsWith("https://")) return result;
                Platform.runLater(() -> logLine("  0x0.st ответ: " + result));
            }
        } catch (Exception e) {
            Platform.runLater(() -> logLine("  0x0.st: " + e.getMessage()));
        }
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
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) worm-launcher/" + LAUNCHER_VERSION);

            String head = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\""
                    + nick + ".png\"\r\n"
                    + "Content-Type: image/png\r\n\r\n";
            String tail = "\r\n--" + boundary + "--\r\n";

            try (OutputStream out = conn.getOutputStream()) {
                out.write(head.getBytes(StandardCharsets.UTF_8));
                out.write(png);
                out.write(tail.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code != 200) {
                final int c = code;
                Platform.runLater(() -> logLine("  tmpfiles: HTTP " + c));
                return null;
            }
            try (InputStream in = conn.getInputStream()) {
                String result = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                int idx = result.indexOf("https://tmpfiles.org/");
                if (idx < 0) {
                    Platform.runLater(() -> logLine("  tmpfiles ответ: " + result));
                    return null;
                }
                int end = idx;
                while (end < result.length() && result.charAt(end) != '"' && result.charAt(end) != ' ') end++;
                String raw = result.substring(idx, end);
                return raw.replace("tmpfiles.org/", "tmpfiles.org/dl/");
            }
        } catch (Exception e) {
            Platform.runLater(() -> logLine("  tmpfiles: " + e.getMessage()));
        }
        return null;
    }

    public static void main(String[] args) { launch(args); }
}