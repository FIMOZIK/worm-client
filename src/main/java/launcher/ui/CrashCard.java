package launcher.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import launcher.mc.crash.CrashInfo;

import java.awt.Desktop;

public final class CrashCard {

    private CrashCard() {}

    public static void show(Window owner, CrashInfo info) {
        Stage s = new Stage();
        if (owner != null) s.initOwner(owner);
        s.initModality(Modality.APPLICATION_MODAL);
        s.setTitle("Minecraft упал");

        Label title = new Label(info.title());
        title.setFont(Font.font("System", FontWeight.BOLD, 18));
        title.setTextFill(Color.web("#b71c1c"));

        Label summary = new Label(info.summary());
        summary.setWrapText(true);

        Label hintsHeader = new Label("Возможные причины и фиксы:");
        hintsHeader.setFont(Font.font("System", FontWeight.BOLD, 13));

        VBox hints = new VBox(6);
        for (String h : info.suggestions()) {
            Label l = new Label("• " + h);
            l.setWrapText(true);
            hints.getChildren().add(l);
        }

        TextArea rawArea = new TextArea(info.raw() != null ? info.raw() : "");
        rawArea.setEditable(false);
        rawArea.setPrefRowCount(14);
        rawArea.setPrefColumnCount(80);
        rawArea.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 11;");

        TitledPane rawPane = new TitledPane("Сырой crash-report", rawArea);
        rawPane.setExpanded(false);

        Button openFile = new Button("Открыть в блокноте");
        openFile.setOnAction(e -> {
            try { Desktop.getDesktop().open(info.file().toFile()); } catch (Exception ignored) {}
        });

        Button openFolder = new Button("Открыть crash-reports");
        openFolder.setOnAction(e -> {
            try { Desktop.getDesktop().open(info.file().getParent().toFile()); } catch (Exception ignored) {}
        });

        Button copyPath = new Button("Копировать путь");
        copyPath.setOnAction(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(info.file().toAbsolutePath().toString());
            Clipboard.getSystemClipboard().setContent(cc);
        });

        Button close = new Button("Закрыть");
        close.setDefaultButton(true);
        close.setOnAction(e -> s.close());

        HBox topButtons = new HBox(8, openFile, openFolder, copyPath);
        HBox bottomButtons = new HBox(8, close);
        bottomButtons.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(12,
                title, summary,
                new Separator(),
                hintsHeader, hints,
                new Separator(),
                rawPane,
                topButtons,
                bottomButtons
        );
        root.setPadding(new Insets(20));
        root.setPrefWidth(720);
        VBox.setVgrow(rawPane, Priority.ALWAYS);

        Scene scene = new Scene(root, 740, 620);
        s.setScene(scene);
        s.show();
    }
}