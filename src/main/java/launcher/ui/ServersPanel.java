package launcher.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import launcher.Config;
import launcher.mc.ServerPinger;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Панель «Серверы»: список сохранённых серверов с online/players/ping.
 * Сохраняет в cfg.servers[] при добавлении/удалении.
 */
public class ServersPanel extends VBox {

    public static class ServerEntry {
        public String name;
        public String host;
        public int port;

        public ServerEntry() {}
        public ServerEntry(String name, String host, int port) {
            this.name = name;
            this.host = host;
            this.port = port;
        }

        public String address() {
            return port == 25565 ? host : host + ":" + port;
        }
    }

    public static class ServerRow {
        final ServerEntry entry;
        String players = "—";
        String ping = "—";
        String status = "не проверен";
        String motd = "";

        ServerRow(ServerEntry e) { this.entry = e; }
    }

    private final Config.Data cfg;
    private final Runnable onPlayOnServer;
    private final ObservableList<ServerRow> rows = FXCollections.observableArrayList();
    private final TableView<ServerRow> table = new TableView<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    public ServersPanel(Config.Data cfg, Runnable onPlayOnServer) {
        this.cfg = cfg;
        this.onPlayOnServer = onPlayOnServer;
        setSpacing(8);
        setPadding(new Insets(8));

        // ============ TABLE ============
        TableColumn<ServerRow, String> colName = new TableColumn<>("Название");
        colName.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().entry.name));
        colName.setPrefWidth(140);

        TableColumn<ServerRow, String> colAddr = new TableColumn<>("Адрес");
        colAddr.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().entry.address()));
        colAddr.setPrefWidth(180);

        TableColumn<ServerRow, String> colPlayers = new TableColumn<>("Игроки");
        colPlayers.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().players));
        colPlayers.setPrefWidth(80);

        TableColumn<ServerRow, String> colPing = new TableColumn<>("Ping");
        colPing.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().ping));
        colPing.setPrefWidth(70);

        TableColumn<ServerRow, String> colStatus = new TableColumn<>("Статус");
        colStatus.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().status));
        colStatus.setPrefWidth(220);

        table.getColumns().addAll(colName, colAddr, colPlayers, colPing, colStatus);
        table.setItems(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);

        // double-click → Play
        table.setRowFactory(tv -> {
            TableRow<ServerRow> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    playOn(row.getItem());
                }
            });
            return row;
        });

        // ============ BUTTONS ============
        Button addBtn = new Button("Добавить");
        Button refreshBtn = new Button("Обновить все");
        Button refreshOneBtn = new Button("Обновить");
        Button deleteBtn = new Button("Удалить");
        Button playBtn = new Button("Play на сервере");

        addBtn.setOnAction(e -> addServer());
        refreshBtn.setOnAction(e -> refreshAll());
        refreshOneBtn.setOnAction(e -> {
            ServerRow sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) refreshOne(sel);
        });
        deleteBtn.setOnAction(e -> {
            ServerRow sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) deleteServer(sel);
        });
        playBtn.setOnAction(e -> {
            ServerRow sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) playOn(sel);
        });

        HBox btnRow = new HBox(8, addBtn, refreshBtn, refreshOneBtn, deleteBtn, playBtn);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(table, btnRow);

        // ============ INIT ============
        if (cfg.servers != null) {
            for (ServerEntry e : cfg.servers) {
                rows.add(new ServerRow(e));
            }
        }
    }

    // ============================================================
    // ACTIONS
    // ============================================================
    private void addServer() {
        Dialog<ServerEntry> dlg = new Dialog<>();
        dlg.setTitle("Добавить сервер");

        TextField nameF = new TextField();
        nameF.setPromptText("Hypixel (или любое имя)");
        TextField hostF = new TextField();
        hostF.setPromptText("mc.hypixel.net");
        TextField portF = new TextField("25565");
        portF.setPrefWidth(80);

        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(8);
        g.setPadding(new Insets(16));
        g.add(new Label("Имя:"), 0, 0); g.add(nameF, 1, 0);
        g.add(new Label("Хост:"), 0, 1); g.add(hostF, 1, 1);
        g.add(new Label("Порт:"), 0, 2); g.add(portF, 1, 2);
        dlg.getDialogPane().setContent(g);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dlg.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                String name = nameF.getText().trim();
                String host = hostF.getText().trim();
                int port;
                try { port = Integer.parseInt(portF.getText().trim()); }
                catch (Exception e) { port = 25565; }
                if (name.isEmpty() || host.isEmpty()) return null;
                return new ServerEntry(name, host, port);
            }
            return null;
        });

        Optional<ServerEntry> res = dlg.showAndWait();
        if (res.isEmpty() || res.get() == null) return;

        ServerEntry e = res.get();
        if (cfg.servers == null) cfg.servers = new java.util.ArrayList<>();
        cfg.servers.add(e);
        Config.save(cfg);

        ServerRow row = new ServerRow(e);
        rows.add(row);
        refreshOne(row);
    }

    private void deleteServer(ServerRow row) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("Удалить сервер");
        a.setHeaderText("Удалить «" + row.entry.name + "»?");
        a.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        Optional<ButtonType> res = a.showAndWait();
        if (res.isEmpty() || res.get() != ButtonType.OK) return;

        rows.remove(row);
        if (cfg.servers != null) {
            cfg.servers.removeIf(x -> x.name.equals(row.entry.name)
                    && x.host.equals(row.entry.host)
                    && x.port == row.entry.port);
        }
        Config.save(cfg);
    }

    private void playOn(ServerRow row) {
        // сохраняем выбранный сервер в cfg (lastServerHost/Port) — LauncherApp подхватит
        cfg.lastServerHost = row.entry.host;
        cfg.lastServerPort = row.entry.port;
        Config.save(cfg);
        if (onPlayOnServer != null) onPlayOnServer.run();
    }

    public void refreshAll() {
        for (ServerRow r : rows) refreshOne(r);
    }

    private void refreshOne(ServerRow row) {
        row.status = "ping...";
        row.players = "—";
        row.ping = "—";
        table.refresh();

        pool.submit(() -> {
            ServerPinger.Status st = ServerPinger.ping(row.entry.host, row.entry.port, 4000);
            Platform.runLater(() -> {
                if (st.online()) {
                    row.status = st.motd() != null && !st.motd().isBlank()
                            ? st.motd()
                            : "online (" + st.version() + ")";
                    row.players = st.playersOnline() + "/" + st.playersMax();
                    row.ping = st.pingMs() + " ms";
                } else {
                    row.status = "offline (" + (st.error() != null ? st.error() : "unknown") + ")";
                    row.players = "—";
                    row.ping = "—";
                }
                table.refresh();
            });
        });
    }

    public void shutdown() {
        pool.shutdownNow();
    }
}