package launcher.mc;

import java.util.Locale;
import java.util.Map;

public class Rules {

    public static String osName() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "osx";
        if (os.contains("linux")) return "linux";
        return "unknown";
    }

    // rules: osName -> action ("allow"/"disallow"), "any" = без привязки к ОС
    // возвращает true, если библиотеку надо качать/добавлять в classpath
    public static boolean allows(Map<String, String> rules) {
        if (rules == null || rules.isEmpty()) return true;
        String cur = osName();
        String action = rules.getOrDefault(cur, rules.get("any"));
        if (action == null) return true;
        return "allow".equals(action);
    }
}