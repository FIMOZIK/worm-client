package launcher;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class Config {

    private static final Path FILE = Path.of("D:\\wormlauncher", "config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class Data {
        public String  lastVersion;
        public String  lastNick;
        public Double  windowWidth;
        public Double  windowHeight;
        public Boolean minimizeOnPlay = true;
        public Boolean autoLaunch     = false;

        public String  theme          = "WormGreen";
        public Boolean darkMode       = true;
        public String  bgImagePath    = null;
        public Double  bgOpacity      = 0.55;
        public String  windowMode     = "WINDOWED";

        // new
        public Double  logOpacity     = 1.0;
        public Boolean showLog        = true;
        public String  buttonLayout   = "TOP"; // TOP | LEFT
    }

    public static Data load() {
        try {
            if (!Files.exists(FILE)) return new Data();
            Data d = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), Data.class);
            if (d == null) return new Data();
            if (d.theme == null) d.theme = "WormGreen";
            if (d.darkMode == null) d.darkMode = true;
            if (d.bgOpacity == null) d.bgOpacity = 0.55;
            if (d.windowMode == null) d.windowMode = "WINDOWED";
            if (d.logOpacity == null) d.logOpacity = 1.0;
            if (d.showLog == null) d.showLog = true;
            if (d.buttonLayout == null) d.buttonLayout = "TOP";
            return d;
        } catch (Exception e) { return new Data(); }
    }

    public static void save(Data d) {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(d), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    public static Path file() { return FILE; }
}