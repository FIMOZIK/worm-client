package launcher;

/** Именованный профиль: сборка «версия + загрузчик + версия загрузчика». */
public class Profile {

    public String name;
    public String mcVersion;
    public String loader;          // Vanilla / Fabric / Forge / Quilt / OptiFine
    public String loaderVersion;   // null для Vanilla

    // ── modpack ──
    public String modpackId;       // null = обычный профиль; иначе папка game/modpacks/{id}
    public String modpackType;     // "MRPACK" | "CURSEFORGE"

    // ── memory ──
    public Integer memoryMb;       // null = использовать cfg.defaultMemoryMb

    // ── custom JVM args ──
    public String customJvmArgs;   // null = не передавать дополнительные -XX и т.п.

    public Profile() {}

    public Profile(String name, String mcVersion, String loader, String loaderVersion) {
        this.name = name;
        this.mcVersion = mcVersion;
        this.loader = loader;
        this.loaderVersion = loaderVersion;
    }

    public Profile copy() {
        Profile p = new Profile(name, mcVersion, loader, loaderVersion);
        p.modpackId = modpackId;
        p.modpackType = modpackType;
        p.memoryMb = memoryMb;
        p.customJvmArgs = customJvmArgs;
        return p;
    }

    @Override
    public String toString() { return name; }
}