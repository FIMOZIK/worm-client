package launcher.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

public class SkinPreview extends VBox {

    private final ImageView head;
    private final Label nameLabel;

    public SkinPreview(double size) {
        head = new ImageView();
        head.setFitWidth(size);
        head.setFitHeight(size);
        head.setPreserveRatio(true);
        head.setSmooth(false); // pixelated (нужен nearest-neighbor)

        nameLabel = new Label("");
        nameLabel.setFont(Font.font(size / 4.5));

        setAlignment(Pos.CENTER);
        setSpacing(2);
        getChildren().addAll(head, nameLabel);
    }

    public void setSkin(Image image, String name) {
        head.setImage(image);
        nameLabel.setText(name != null ? name : "");
    }

    public void clear() {
        head.setImage(null);
        nameLabel.setText("");
    }
}