package launcher;

import javafx.scene.Scene;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Генерирует CSS под выбранный пресет + light/dark.
 * Применяется через data-URI — JavaFX не кэширует, тема меняется мгновенно.
 */
public class ThemeManager {

    public record Colors(
            String accent,
            String accentDim,
            String bgFrom,
            String bgTo,
            String plate,
            String plate2,
            String plateHover,
            String text,
            String textDim,
            String border
    ) {}

    public record Preset(String name, Colors dark, Colors light) {}

    private static final List<Preset> PRESETS = List.of(
            // WormLauncher — цвета из логотипа: DarkBlue #00008B + LightSalmon #FFA07A
            new Preset("WormLauncher",
                    new Colors("#FFA07A", "#C97B5A", "#00008B", "#0a0a3a",
                            "#14144a", "#1c1c5c", "#2a2a70", "#F5E8DC", "#9999BB", "#3a3a8a"),
                    new Colors("#00008B", "#00006B", "#FFE0D0", "#FFC8B0",
                            "#FFFFFF", "#FFF5F0", "#FFE0D0", "#1a1a2a", "#606080", "#D0B8B0")),
            new Preset("Cyberpunk",
                    new Colors("#ff00c8", "#00e0ff", "#1a0a24", "#05030a",
                            "#241230", "#2c1a3c", "#3a1f52", "#f0e8ff", "#9a8ab0", "#3a2a4a"),
                    new Colors("#d500f9", "#00b8d4", "#f3e5f5", "#e3f2fd",
                            "#fcf4ff", "#f0e8fb", "#e0d0f0", "#1a0a24", "#7a6a8a", "#d0c0e0")),
            new Preset("Blood",
                    new Colors("#e53935", "#b71c1c", "#1f0808", "#0a0202",
                            "#2a0c0c", "#381010", "#4a1414", "#f0e0e0", "#a08a8a", "#4a1a1a"),
                    new Colors("#c62828", "#8e0000", "#ffebee", "#fdecea",
                            "#fff5f5", "#fbe8e8", "#f5d0d0", "#2a0a0a", "#7a5a5a", "#e0b0b0")),
            new Preset("Aqua",
                    new Colors("#00bcd4", "#00838f", "#06202a", "#020a0e",
                            "#0e2832", "#142e3c", "#1c4048", "#dff4f8", "#8aa0a8", "#1e3a44"),
                    new Colors("#0097a7", "#006064", "#e0f7fa", "#f0fbfd",
                            "#f5fdfe", "#e8f7fa", "#d0eef5", "#062028", "#4a6670", "#b0dde5")),
            new Preset("Mono",
                    new Colors("#b0b0b0", "#707070", "#181818", "#0a0a0a",
                            "#222222", "#2a2a2a", "#383838", "#e8e8e8", "#8a8a8a", "#3a3a3a"),
                    new Colors("#555555", "#333333", "#f5f5f5", "#fafafa",
                            "#ffffff", "#f0f0f0", "#e0e0e0", "#1a1a1a", "#6a6a6a", "#d0d0d0")),
            new Preset("Sunset",
                    new Colors("#ff9800", "#e65100", "#2a1408", "#0a0402",
                            "#321a0e", "#3a2214", "#4a2c18", "#fef0e0", "#a08870", "#4a2c18"),
                    new Colors("#ef6c00", "#bf360c", "#fff3e0", "#fbe9e7",
                            "#fffaf5", "#fff0e0", "#fadcb8", "#2a1408", "#7a6048", "#e8c8a0")),
            new Preset("Mint",
                    new Colors("#26a69a", "#00695c", "#082018", "#020a08",
                            "#0e2a22", "#14342c", "#1c4038", "#dff4ef", "#88a098", "#1e3a32"),
                    new Colors("#00897b", "#004d40", "#e0f2f1", "#f0faf9",
                            "#f5fdfc", "#e8f7f5", "#d0eeea", "#082018", "#4a6660", "#b0ddd5"))
    );

    public static List<String> listPresets() {
        return PRESETS.stream().map(Preset::name).toList();
    }

    public static Colors getColors(Config.Data cfg) {
        // fallback: старые конфиги с "WormGreen" → автоматически подхватят "WormLauncher" (PRESETS.get(0))
        String name = cfg.theme != null ? cfg.theme : "WormLauncher";
        Preset p = PRESETS.stream().filter(x -> x.name().equals(name)).findFirst().orElse(PRESETS.get(0));
        return Boolean.TRUE.equals(cfg.darkMode) ? p.dark() : p.light();
    }

    /** Применяет тему через data-URI — без временных файлов, без кэша. */
    public static void apply(Scene scene, Config.Data cfg) {
        try {
            String css = generateCss(getColors(cfg));
            String base64 = Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
            String dataUri = "data:text/css;base64," + base64;

            scene.getStylesheets().removeIf(s -> s.startsWith("data:text/css"));
            scene.getStylesheets().add(dataUri);
        } catch (Exception e) {
            System.err.println("theme apply failed: " + e.getMessage());
        }
    }

    private static String generateCss(Colors c) {
        return String.join("\n",
                ".root {",
                "    -fx-base: " + c.plate() + ";",
                "    -fx-background: " + c.bgFrom() + ";",
                "    -fx-control-inner-background: " + c.plate2() + ";",
                "    -fx-accent: " + c.accent() + ";",
                "    -fx-focus-color: " + c.accent() + ";",
                "    -fx-faint-focus-color: " + c.accent() + "22;",
                "    -fx-text-base-color: " + c.text() + ";",
                "    -fx-background-color: linear-gradient(to bottom right, " + c.bgFrom() + ", " + c.bgTo() + ");",
                "}",

                ".label { -fx-text-fill: " + c.text() + "; }",
                ".label.dim { -fx-text-fill: " + c.textDim() + "; }",
                ".title-label { -fx-text-fill: " + c.accent() + "; }",
                ".subtitle-label { -fx-text-fill: " + c.textDim() + "; }",

                ".text-field, .password-field {",
                "    -fx-background-color: " + c.plate2() + ";",
                "    -fx-text-fill: " + c.text() + ";",
                "    -fx-prompt-text-fill: " + c.textDim() + ";",
                "    -fx-border-color: " + c.border() + ";",
                "}",
                ".text-field:focused, .password-field:focused { -fx-border-color: " + c.accent() + "; }",

                ".combo-box {",
                "    -fx-background-color: " + c.plate2() + ";",
                "    -fx-border-color: " + c.border() + ";",
                "}",
                ".combo-box .list-cell {",
                "    -fx-text-fill: " + c.text() + ";",
                "    -fx-background-color: " + c.plate2() + ";",
                "}",
                ".combo-box .arrow { -fx-background-color: " + c.textDim() + "; }",
                ".combo-box-popup .list-view { -fx-background-color: " + c.plate() + "; -fx-border-color: " + c.border() + "; }",
                ".combo-box-popup .list-cell:hover { -fx-background-color: " + c.plateHover() + "; }",
                ".combo-box-popup .list-cell:selected { -fx-background-color: " + c.accent() + "; -fx-text-fill: " + (isLightAccent(c) ? "#000000" : "#ffffff") + "; }",

                ".button {",
                "    -fx-background-color: " + c.plate() + ";",
                "    -fx-text-fill: " + c.text() + ";",
                "    -fx-border-color: " + c.border() + ";",
                "}",
                ".button:hover { -fx-background-color: " + c.plateHover() + "; -fx-border-color: " + c.accent() + "; }",
                ".button:disabled { -fx-opacity: 0.45; }",

                ".play-button {",
                "    -fx-background-color: " + c.accentDim() + ";",
                "    -fx-text-fill: #ffffff;",
                "    -fx-border-color: " + c.accent() + ";",
                "}",
                ".play-button:hover { -fx-background-color: " + c.accent() + "; }",
                ".play-button:disabled { -fx-background-color: " + c.accentDim() + "; -fx-opacity: 0.4; }",

                ".text-area {",
                "    -fx-control-inner-background: " + c.bgTo() + ";",
                "    -fx-background-color: " + c.bgTo() + ";",
                "    -fx-text-fill: " + c.accent() + ";",
                "    -fx-border-color: " + c.border() + ";",
                "}",
                ".text-area .content { -fx-background-color: " + c.bgTo() + "; }",

                ".progress-bar > .track { -fx-background-color: " + c.plate2() + "; }",
                ".progress-bar > .bar   { -fx-background-color: " + c.accent() + "; }",

                ".separator .line { -fx-border-color: " + c.border() + "; -fx-border-width: 1 0 0 0; }",

                ".scroll-bar { -fx-background-color: " + c.bgFrom() + "; }",
                ".scroll-bar .thumb { -fx-background-color: " + c.border() + "; -fx-background-radius: 4; }",
                ".scroll-bar .thumb:hover { -fx-background-color: " + c.accent() + "; }",

                ".settings-panel { -fx-background-color: " + c.plate() + "; }",
                ".check-box .box { -fx-background-color: " + c.plate2() + "; -fx-border-color: " + c.border() + "; }",
                ".check-box:selected .mark { -fx-background-color: " + c.accent() + "; }",
                ".slider .track { -fx-background-color: " + c.plate2() + "; }",
                ".slider .thumb { -fx-background-color: " + c.accent() + "; }",
                ".radio-button .radio { -fx-background-color: " + c.plate2() + "; -fx-border-color: " + c.border() + "; }",
                ".radio-button:selected .dot { -fx-background-color: " + c.accent() + "; }"
        );
    }

    private static boolean isLightAccent(Colors c) {
        String a = c.accent().replace("#", "");
        if (a.length() < 6) return false;
        int r = Integer.parseInt(a.substring(0, 2), 16);
        int g = Integer.parseInt(a.substring(2, 4), 16);
        int b = Integer.parseInt(a.substring(4, 6), 16);
        return (r * 299 + g * 587 + b * 114) / 1000 > 140;
    }
}