package launcher.auth;

/**
 * Универсальная сессия: Ely.by или Mojang (Microsoft).
 * Хранится в session.json, из неё разворачивается в ElyAuth.Session
 * или MojangAuth.Session при запуске игры.
 */
public record GameSession(
        String type,           // "ely" | "mojang"
        String username,
        String uuid,
        String accessToken,
        String clientToken,    // только для ely
        String refreshToken,   // пока пусто
        String userType        // "mojang" для ely, "msa" для mojang
) {
    public static final String TYPE_ELY    = "ely";
    public static final String TYPE_MOJANG = "mojang";

    public static GameSession fromEly(ElyAuth.Session s) {
        return new GameSession(TYPE_ELY, s.username(), s.uuid(),
                s.accessToken(), s.clientToken(), s.refreshToken(), "mojang");
    }

    public static GameSession fromMojang(MojangAuth.Session s) {
        return new GameSession(TYPE_MOJANG, s.username(), s.uuid(),
                s.accessToken(), null, s.refreshToken(), "msa");
    }

    public ElyAuth.Session toEly() {
        return new ElyAuth.Session(username, uuid, accessToken, clientToken, refreshToken);
    }

    public MojangAuth.Session toMojang() {
        return new MojangAuth.Session(username, uuid, accessToken, userType, refreshToken);
    }

    public boolean isEly()    { return TYPE_ELY.equals(type); }
    public boolean isMojang() { return TYPE_MOJANG.equals(type); }

    @Override
    public String toString() {
        return "GameSession[" + type + ", " + username + ", uuid=" + uuid + "]";
    }
}