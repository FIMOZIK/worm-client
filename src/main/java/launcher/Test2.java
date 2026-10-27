package launcher;

import launcher.mc.GameInstaller;
import java.nio.file.*;

public class Test2 {
    public static void main(String[] args) throws Exception {
        Path gameDir = Path.of("D:\\wormlauncher", "game");
        System.out.println("gameDir: " + gameDir);
        Path jar = GameInstaller.install("1.21.11", gameDir);
        System.out.println("готово. client jar: " + jar);
        System.out.println("проверь: " + gameDir.resolve("libraries"));
        System.out.println("       " + gameDir.resolve("assets"));
        System.out.println("       " + gameDir.resolve("natives").resolve("1.21.11"));
    }
}