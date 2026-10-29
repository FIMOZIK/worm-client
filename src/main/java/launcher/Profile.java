package launcher;

/** Именованный профиль: сборка «версия + загрузчик + версия загрузчика». */
public class Profile {

    public String name;
    public String mcVersion;
    public String loader;         // Vanilla / Fabric / Forge / Quilt
    public String loaderVersion;  // null для Vanilla

    public Profile() {}

    public Profile(String name, String mcVersion, String loader, String loaderVersion) {
        this.name = name;
        this.mcVersion = mcVersion;
        this.loader = loader;
        this.loaderVersion = loaderVersion;
    }

    @Override
    public String toString() { return name; }
}