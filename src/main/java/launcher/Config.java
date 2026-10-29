package launcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Config {

    public static final Path FILE = Path.of("D:\\wormlauncher\\config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class Data {
        public String lastVersion;
        public String lastNick;
        public Double windowWidth;
        public Double windowHeight;
        public Boolean minimizeOnPlay;
        public Boolean autoLaunch;
        public String theme;
        public Boolean darkMode;
        public String bgImagePath;
        public Double bgOpacity;
        public String windowMode;
        public Double logOpacity;
        public Boolean showLog;
        public String buttonLayout;

        // приоритет 4 — кастомизация
        public List<Profile> profiles = new ArrayList<>();
        public String customFontPath;
        public Boolean soundsEnabled = true;
        public String keypressSoundPath;

        // приоритет 5 — автообновление
        public Boolean autoCheckUpdates = true;
    }

    public static Data load() {
        try {
            if (!Files.exists(FILE)) return new Data();
            String s = Files.readString(FILE, StandardCharsets.UTF_8);
            Data d = GSON.fromJson(s, Data.class);
            if (d == null) return new Data();
            if (d.profiles == null) d.profiles = new ArrayList<>();
            if (d.soundsEnabled == null) d.soundsEnabled = true;
            if (d.autoCheckUpdates == null) d.autoCheckUpdates = true;
            return d;
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
}