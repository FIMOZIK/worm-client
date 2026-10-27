package launcher.auth;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class TokenStore {

    private static final Path FILE = Path.of("D:\\wormlauncher", "session.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void save(GameSession s) throws Exception {
        Files.createDirectories(FILE.getParent());
        Files.writeString(FILE, GSON.toJson(s), StandardCharsets.UTF_8);
    }

    public static GameSession load() {
        try {
            if (!Files.exists(FILE)) return null;
            return GSON.fromJson(Files.readString(FILE), GameSession.class);
        } catch (Exception e) {
            return null;
        }
    }

    public static void clear() throws Exception {
        Files.deleteIfExists(FILE);
    }

    public static Path file() { return FILE; }
}