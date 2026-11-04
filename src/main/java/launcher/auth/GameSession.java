package launcher.auth;

/**
 * Универсальная сессия: Ely.by или Mojang (Microsoft).
 * Хранится в session.json, из неё разворачивается в ElyAuth.Session
 * или MojangAuth.Session при запуске игры.
 *
 * expiresAt: время смерти MC access_token (ms since epoch), 0/null если неизвестно.
 * используется для авто-refresh MS-сессии.
 */
public record GameSession(
        String type,           // "ely" | "mojang"
        String username,
        String uuid,
        String accessToken,
        String clientToken,    // только для ely
        String refreshToken,   // для mojang — MS refresh_token
        String userType,       // "mojang" для ely, "msa" для mojang
        Long expiresAt         // new: null для старых сессий
) {
    public static final String TYPE_ELY    = "ely";
    public static final String TYPE_MOJANG = "mojang";

    public static GameSession fromEly(ElyAuth.Session s) {
        return new GameSession(TYPE_ELY, s.username(), s.uuid(),
                s.accessToken(), s.clientToken(), s.refreshToken(), "mojang", null);
    }

    public static GameSession fromMojang(MojangAuth.Session s) {
        return new GameSession(TYPE_MOJANG, s.username(), s.uuid(),
                s.accessToken(), null, s.refreshToken(), "msa", s.expiresAt());
    }

    public ElyAuth.Session toEly() {
        return new ElyAuth.Session(username, uuid, accessToken, clientToken, refreshToken);
    }

    public MojangAuth.Session toMojang() {
        return new MojangAuth.Session(username, uuid, accessToken, userType,
                refreshToken, expiresAt != null ? expiresAt : 0L);
    }

    public boolean isEly()    { return TYPE_ELY.equals(type); }
    public boolean isMojang() { return TYPE_MOJANG.equals(type); }

    /** true если MS access_token истёк или истечёт в ближайшие 5 минут. */
    public boolean needsRefresh() {
        if (!isMojang()) return false;
        if (refreshToken == null || refreshToken.isBlank()) return false;
        if (expiresAt == null || expiresAt <= 0) return true; // старая сессия без expiresAt
        return expiresAt < System.currentTimeMillis() + 5L * 60_000L;
    }

    @Override
    public String toString() {
        return "GameSession[" + type + ", " + username + ", uuid=" + uuid + "]";
    }
}