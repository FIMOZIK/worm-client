package launcher;

import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Звук нажатия клавиш/кнопок.
 * Пока пользователь не выбрал файл — звука НЕТ.
 * wav/mp3/aiff → AudioClip (мгновенно), mp4/m4a/aac → MediaPlayer.
 */
public final class SoundManager {

    private static AudioClip audioClip;
    private static MediaPlayer mediaPlayer;
    private static volatile boolean enabled = true;
    private static volatile boolean isMediaBased = false;

    private SoundManager() {}

    /** customPath = null / пусто / не существует → звука нет. */
    public static void init(String customPath) {
        stop();
        if (customPath == null || customPath.isBlank()) return;
        try {
            Path cp = Path.of(customPath);
            if (!Files.exists(cp)) return;
            String lower = cp.getFileName().toString().toLowerCase();
            if (lower.endsWith(".mp4") || lower.endsWith(".m4a") || lower.endsWith(".aac")) {
                Media media = new Media(cp.toUri().toString());
                mediaPlayer = new MediaPlayer(media);
                mediaPlayer.setCycleCount(1);
                mediaPlayer.setVolume(0.6);
                mediaPlayer.setOnEndOfMedia(() -> {
                    if (mediaPlayer != null) mediaPlayer.stop();
                });
                isMediaBased = true;
            } else {
                audioClip = new AudioClip(cp.toUri().toString());
                isMediaBased = false;
            }
        } catch (Exception ignored) {}
    }

    public static void setEnabled(boolean v) { enabled = v; }
    public static boolean isEnabled() { return enabled; }

    public static void playKeypress() {
        if (!enabled) return;
        try {
            if (isMediaBased && mediaPlayer != null) {
                mediaPlayer.seek(Duration.ZERO);
                mediaPlayer.play();
            } else if (audioClip != null) {
                audioClip.play();
            }
        } catch (Exception ignored) {}
    }

    public static void stop() {
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); mediaPlayer.dispose(); } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        audioClip = null;
        isMediaBased = false;
    }
}