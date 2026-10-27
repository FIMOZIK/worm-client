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
    }

    public static Data load() {
        try {
            if (!Files.exists(FILE)) return new Data();
            Data d = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), Data.class);
            return d != null ? d : new Data();
        } catch (Exception e) {
            return new Data();
        }
    }

    public static void save(Data d) {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(d), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    public static Path file() { return FILE; }
}