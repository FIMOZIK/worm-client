package launcher.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import launcher.Config;
import launcher.Paths;
import launcher.Profile;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class ModsPanel extends VBox {

    public static class ModRow {
        final Path path;
        final String name;
        final long size;
        final boolean enabled;

        ModRow(Path p) {
            this.path = p;
            String fn = p.getFileName().toString();
            if (fn.endsWith(".jar.disabled")) {
                this.name = fn.substring(0, fn.length() - ".disabled".length());
                this.enabled = false;
            } else {
                this.name = fn;
                this.enabled = true;
            }
            long s = 0;
            try { s = Files.size(p); } catch (Exception ignored) {}
            this.size = s;
        }
    }

    private final Config.Data cfg;
    private final ObservableList<ModRow> rows = FXCollections.observableArrayList();
    private final TableView<ModRow> table = new TableView<>();
    private final Label pathLabel = new Label("(профиль не выбран)");
    private final Button addBtn = new Button("Добавить мод");
    private final Button deleteBtn = new Button("Удалить");
    private final Button refreshBtn = new Button("Обновить");
    private final Button folderBtn = new Button("Открыть папку");

    private Path currentModsDir;

    public ModsPanel(Config.Data cfg) {
        this.cfg = cfg;
        setSpacing(8);
        setPadding(new Insets(8));

        pathLabel.setStyle("-fx-opacity: 0.7;");
        pathLabel.setWrapText(true);

        TableColumn<ModRow, String> colName = new TableColumn<>("Имя");
        colName.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().name));
        colName.setPrefWidth(340);

        TableColumn<ModRow, String> colSize = new TableColumn<>("Размер");
        colSize.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(formatSize(c.getValue().size)));
        colSize.setPrefWidth(90);

        TableColumn<ModRow, String> colStatus = new TableColumn<>("Статус");
        colStatus.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().enabled ? "включён" : "отключён"));
        colStatus.setPrefWidth(100);

        table.getColumns().addAll(colName, colSize, colStatus);
        table.setItems(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);

        addBtn.setOnAction(e -> addMod());
        deleteBtn.setOnAction(e -> deleteMod());
        refreshBtn.setOnAction(e -> reload());
        folderBtn.setOnAction(e -> openFolder());

        HBox btnRow = new HBox(8, addBtn, deleteBtn, refreshBtn, folderBtn);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(pathLabel, table, btnRow);

        table.setRowFactory(tv -> {
            TableRow<ModRow> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !row.isEmpty()) {
                    toggle(row.getItem());
                }
            });
            return row;
        });

        setDisabled(true);
    }

    // ============================================================
    // set profile context (называется из applyProfile)
    // ============================================================
    public void setProfile(Profile p) {
        Path mcDir = Paths.mcDir();

        if (p != null && p.modpackId != null && !p.modpackId.isBlank()) {
            currentModsDir = mcDir.resolve("modpacks").resolve(p.modpackId).resolve("mods");
            pathLabel.setText("Модпак: " + p.name + "  →  " + currentModsDir);
        } else if (p != null) {
            currentModsDir = mcDir.resolve("mods");
            pathLabel.setText("Профиль: " + p.name + "  →  " + currentModsDir);
        } else {
            currentModsDir = mcDir.resolve("mods");
            pathLabel.setText("Профиль не выбран  →  " + currentModsDir);
        }

        addBtn.setDisable(false);
        deleteBtn.setDisable(false);
        refreshBtn.setDisable(false);
        folderBtn.setDisable(false);
        setDisabled(false);

        reload();
    }

    // ============================================================
    // reload
    // ============================================================
    public void reload() {
        rows.clear();

        if (currentModsDir == null) {
            table.refresh();
            return;
        }

        try { Files.createDirectories(currentModsDir); } catch (Exception ignored) {}

        if (!Files.isDirectory(currentModsDir)) {
            table.refresh();
            return;
        }

        List<Path> jars = new ArrayList<>();
        try (Stream<Path> s = Files.list(currentModsDir)) {
            s.filter(Files::isRegularFile)
                    .filter(p -> {
                        String fn = p.getFileName().toString().toLowerCase();
                        return fn.endsWith(".jar") || fn.endsWith(".jar.disabled");
                    })
                    .forEach(jars::add);
        } catch (Exception ignored) {}

        jars.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()));

        for (Path j : jars) rows.add(new ModRow(j));
        table.refresh();
    }

    // ============================================================
    // add mod
    // ============================================================
    private void addMod() {
        if (currentModsDir == null) return;
        FileChooser fc = new FileChooser();
        fc.setTitle("Добавить мод (.jar)");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Minecraft mods", "*.jar"));
        File f = fc.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (f == null) return;

        Path src = f.toPath();
        Path dst = currentModsDir.resolve(f.getName());

        if (Files.exists(dst)) {
            Alert a = new Alert(Alert.AlertType.CONFIRMATION);
            a.setTitle("Файл уже существует");
            a.setHeaderText("Мод «" + f.getName() + "» уже есть");
            a.setContentText("Заменить?");
            a.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
            Optional<ButtonType> r = a.showAndWait();
            if (r.isEmpty() || r.get() != ButtonType.OK) return;
        }

        try {
            Files.createDirectories(currentModsDir);
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            reload();
        } catch (Exception e) {
            Alert err = new Alert(Alert.AlertType.ERROR);
            err.setTitle("Ошибка");
            err.setHeaderText(null);
            err.setContentText("Не удалось скопировать: " + e.getMessage());
            err.showAndWait();
        }
    }

    // ============================================================
    // delete mod
    // ============================================================
    private void deleteMod() {
        ModRow sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("Удалить мод");
        a.setHeaderText("Удалить «" + sel.name + "»?");
        a.setContentText("Файл будет удалён с диска без возможности восстановления.");
        a.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        Optional<ButtonType> r = a.showAndWait();
        if (r.isEmpty() || r.get() != ButtonType.OK) return;

        try {
            Files.deleteIfExists(sel.path);
            reload();
        } catch (Exception e) {
            Alert err = new Alert(Alert.AlertType.ERROR);
            err.setTitle("Ошибка");
            err.setHeaderText(null);
            err.setContentText("Не удалось удалить: " + e.getMessage());
            err.showAndWait();
        }
    }

    // ============================================================
    // toggle .jar ↔ .jar.disabled
    // ============================================================
    private void toggle(ModRow row) {
        if (row == null || currentModsDir == null) return;
        try {
            String fn = row.path.getFileName().toString();
            Path target;
            if (fn.endsWith(".jar.disabled")) {
                String newName = fn.substring(0, fn.length() - ".disabled".length());
                target = row.path.resolveSibling(newName);
            } else {
                target = row.path.resolveSibling(fn + ".disabled");
            }
            Files.move(row.path, target, StandardCopyOption.REPLACE_EXISTING);
            reload();
        } catch (Exception e) {
            Alert err = new Alert(Alert.AlertType.ERROR);
            err.setTitle("Ошибка");
            err.setHeaderText(null);
            err.setContentText("Не удалось переключить: " + e.getMessage());
            err.showAndWait();
        }
    }

    // ============================================================
    // open folder
    // ============================================================
    private void openFolder() {
        if (currentModsDir == null) return;
        try {
            Files.createDirectories(currentModsDir);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(currentModsDir.toFile());
            }
        } catch (Exception ignored) {}
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}