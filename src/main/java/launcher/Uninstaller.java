package launcher;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public final class Uninstaller {

    private Uninstaller() {}

    public static void run(Stage stage, Path jarDir) {
        Path launcherDir = Paths.launcherDir();
        Path mcDir       = Paths.mcDir();
        Path installFile = jarDir.resolve("install.json");

        CheckBox launcherBox = new CheckBox(Lang.t("uninst.cb_launcher") + "  (" + launcherDir + ")");
        launcherBox.setSelected(true);
        CheckBox mcBox = new CheckBox(Lang.t("uninst.cb_mc") + "  (" + mcDir + ")");
        mcBox.setSelected(true);

        Label head = new Label(Lang.t("uninst.title"));
        head.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Label sub = new Label(Lang.t("uninst.sub"));
        sub.setStyle("-fx-opacity: 0.7;");

        Label status = new Label();
        status.setStyle("-fx-opacity: 0.7;");

        Button uninstBtn = new Button(Lang.t("uninst.btn"));
        uninstBtn.setStyle("-fx-background-color: #b71c1c; -fx-text-fill: white;");
        Button cancelBtn = new Button(Lang.t("uninst.cancel"));

        VBox panel = new VBox(10,
                head, sub,
                new Separator(),
                launcherBox,
                mcBox,
                new Separator(),
                status,
                new HBox(8, uninstBtn, cancelBtn)
        );
        panel.setPadding(new Insets(20));

        uninstBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.initOwner(stage);
            confirm.setTitle(Lang.t("uninst.confirm_title"));
            confirm.setHeaderText(Lang.t("uninst.confirm_msg"));
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

            try {
                // MC удаляем ПЕРВЫМ, потом лаунчер (потому что лаунчер может держать session/config)
                if (mcBox.isSelected() && Files.exists(mcDir)) deleteDir(mcDir);
                if (launcherBox.isSelected() && Files.exists(launcherDir)) deleteDir(launcherDir);
                // install.json удаляем только если удалили папку лаунчера
                if (launcherBox.isSelected()) Files.deleteIfExists(installFile);
                status.setText(Lang.t("uninst.done"));
                stage.close();
            } catch (Exception ex) {
                status.setText("✗ " + ex.getMessage());
            }
        });
        cancelBtn.setOnAction(e -> stage.close());

        stage.setScene(new Scene(panel, 660, 340));
        stage.setTitle(Lang.t("uninst.title"));
    }

    private static void deleteDir(Path dir) throws Exception {
        try (var s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
            });
        }
    }
}