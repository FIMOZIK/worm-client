package launcher.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import launcher.Profile;

/**
 * Редактор одного профиля.
 * mcVersion / loader / loaderVersion read-only — менять через создание нового профиля.
 * Возвращает edited profile или null если отменили.
 */
public final class ProfileEditor {

    public static Profile edit(Window owner, Profile source, Integer globalDefaultMemoryMb) {
        Stage s = new Stage();
        if (owner != null) s.initOwner(owner);
        s.initModality(Modality.APPLICATION_MODAL);
        s.setTitle("Редактировать профиль: " + source.name);

        Label head = new Label(source.name);
        head.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // ---- name ----
        TextField nameField = new TextField(source.name);
        nameField.setPrefWidth(360);

        // ---- read-only meta ----
        Label mcVersionLabel = new Label(source.mcVersion != null ? source.mcVersion : "—");
        mcVersionLabel.setStyle("-fx-opacity: 0.7;");
        Label loaderLabel = new Label((source.loader != null ? source.loader : "Vanilla")
                + (source.loaderVersion != null ? " " + source.loaderVersion : ""));
        loaderLabel.setStyle("-fx-opacity: 0.7;");

        // ---- memory ----
        int initialMem = source.memoryMb != null && source.memoryMb > 0
                ? source.memoryMb
                : (globalDefaultMemoryMb != null && globalDefaultMemoryMb > 0
                        ? globalDefaultMemoryMb : 4096);

        CheckBox useCustomMemoryBox = new CheckBox("Своя память для этого профиля");
        useCustomMemoryBox.setSelected(source.memoryMb != null && source.memoryMb > 0);

        Slider memorySlider = new Slider(1024, 16384, initialMem);
        memorySlider.setShowTickMarks(true);
        memorySlider.setShowTickLabels(false);
        memorySlider.setMajorTickUnit(2048);
        memorySlider.setBlockIncrement(512);
        memorySlider.setPrefWidth(360);
        memorySlider.setDisable(!useCustomMemoryBox.isSelected());

        Label memoryLabel = new Label();
        Runnable updateMemLabel = () -> memoryLabel.setText("-Xmx" + (int) memorySlider.getValue() + " MB");
        memorySlider.valueProperty().addListener((o, a, b) -> updateMemLabel.run());
        updateMemLabel.run();

        useCustomMemoryBox.selectedProperty().addListener((o, a, b) -> {
            memorySlider.setDisable(!b);
            memoryLabel.setStyle(b ? "" : "-fx-opacity: 0.5;");
        });
        if (!useCustomMemoryBox.isSelected()) {
            memoryLabel.setStyle("-fx-opacity: 0.5;");
        }

        // ---- custom JVM args ----
        TextField jvmArgsField = new TextField(
                source.customJvmArgs != null ? source.customJvmArgs : "");
        jvmArgsField.setPromptText("например: -XX:+UseG1GC -XX:MaxGCPauseMillis=50");
        jvmArgsField.setPrefWidth(360);

        Label jvmArgsHint = new Label(
                "JVM-аргументы через пробел. -Xmx задаётся слайдером выше, сюда не пиши.");
        jvmArgsHint.setStyle("-fx-opacity: 0.6; -fx-font-size: 11px;");
        jvmArgsHint.setWrapText(true);

        // ---- modpack info ----
        VBox modpackBox = new VBox(2);
        if (source.modpackId != null && !source.modpackId.isBlank()) {
            Label modpackLabel = new Label("Modpack: " + source.modpackId
                    + (source.modpackType != null ? " [" + source.modpackType + "]" : ""));
            modpackLabel.setStyle("-fx-opacity: 0.7;");
            modpackBox.getChildren().add(modpackLabel);
        }

        // ---- buttons ----
        Button saveBtn = new Button("Сохранить");
        saveBtn.setDefaultButton(true);
        Button cancelBtn = new Button("Отмена");

        HBox btnRow = new HBox(8, saveBtn, cancelBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        // ---- layout ----
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);
        grid.setPadding(new Insets(8, 0, 8, 0));

        int row = 0;
        grid.add(new Label("Имя:"), 0, row);
        grid.add(nameField, 1, row++);

        grid.add(new Label("Minecraft:"), 0, row);
        grid.add(mcVersionLabel, 1, row++);

        grid.add(new Label("Загрузчик:"), 0, row);
        grid.add(loaderLabel, 1, row++);

        grid.add(new Label("Память:"), 0, row);
        grid.add(useCustomMemoryBox, 1, row++);

        grid.add(new Label(""), 0, row);
        grid.add(memorySlider, 1, row++);

        grid.add(new Label(""), 0, row);
        grid.add(memoryLabel, 1, row++);

        grid.add(new Label("JVM args:"), 0, row);
        grid.add(jvmArgsField, 1, row++);

        grid.add(new Label(""), 0, row);
        grid.add(jvmArgsHint, 1, row++);

        VBox root = new VBox(12,
                head,
                new Separator(),
                grid,
                modpackBox,
                new Separator(),
                btnRow);
        root.setPadding(new Insets(20));
        root.setPrefWidth(520);

        final Profile[] result = {null};

        saveBtn.setOnAction(e -> {
            String newName = nameField.getText().trim();
            if (newName.isEmpty()) {
                Alert err = new Alert(Alert.AlertType.WARNING);
                err.initOwner(s);
                err.setTitle("Ошибка");
                err.setHeaderText(null);
                err.setContentText("Имя профиля не может быть пустым");
                err.showAndWait();
                return;
            }

            Profile edited = new Profile(newName, source.mcVersion, source.loader, source.loaderVersion);
            edited.modpackId = source.modpackId;
            edited.modpackType = source.modpackType;
            edited.memoryMb = useCustomMemoryBox.isSelected()
                    ? (int) memorySlider.getValue()
                    : null;

            String jvm = jvmArgsField.getText().trim();
            edited.customJvmArgs = jvm.isEmpty() ? null : jvm;

            result[0] = edited;
            s.close();
        });

        cancelBtn.setOnAction(e -> {
            result[0] = null;
            s.close();
        });

        Scene scene = new Scene(root);
        if (owner != null && owner.getScene() != null) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        s.setScene(scene);
        s.showAndWait();
        return result[0];
    }

    private ProfileEditor() {}
}