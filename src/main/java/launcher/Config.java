package launcher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Config {

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

        public List<Profile> profiles = new ArrayList<>();
        public String customFontPath;
        public Boolean soundsEnabled = true;
        public String keypressSoundPath;
        public Boolean autoCheckUpdates = true;

        public String lang = Lang.RU;
    }

    public static Path FILE() { return Paths.configFile(); }

    public static Data load() {
        try {
            Path f = FILE();
            if (!Files.exists(f)) return new Data();
            String s = Files.readString(f, StandardCharsets.UTF_8);
            Data d = GSON.fromJson(s, Data.class);
            if (d == null) return new Data();
            if (d.profiles == null) d.profiles = new ArrayList<>();
            if (d.soundsEnabled == null) d.soundsEnabled = true;
            if (d.autoCheckUpdates == null) d.autoCheckUpdates = true;
            if (d.lang == null) d.lang = Lang.RU;
            return d;
        } catch (Exception e) {
            return new Data();
        }
    }

    public static void save(Data d) {
        try {
            Path f = FILE();
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(d), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }
}