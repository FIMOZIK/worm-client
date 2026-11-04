package launcher.mc.crash;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

public final class CrashParser {

    private CrashParser() {}

    public static CrashInfo parse(Path crashFile) {
        String raw;
        try {
            raw = Files.readString(crashFile, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return new CrashInfo(
                    "Crash report не читается",
                    crashFile.toString(),
                    List.of("Ошибка чтения: " + e.getMessage()),
                    crashFile, "");
        }

        String desc = extractLine(raw, "Description:");

        for (Rule r : RULES) {
            if (r.pattern().matcher(raw).find()) {
                return new CrashInfo(
                        r.title(),
                        desc != null ? desc : r.summary(),
                        r.suggestions(),
                        crashFile,
                        raw
                );
            }
        }

        return new CrashInfo(
                "Minecraft упал",
                desc != null ? desc : "неизвестная ошибка",
                List.of(
                        "Неизвестный крэш — смотри сырой лог ниже.",
                        "Частые причины: конфликт модов, нет зависимости, битый конфиг.",
                        "Папка crash-reports: " + crashFile.getParent()
                ),
                crashFile, raw
        );
    }

    public static Path findLatestCrashAfter(Path dir, long afterMs) {
        if (dir == null || !Files.isDirectory(dir)) return null;
        try {
            return Files.list(dir)
                    .filter(p -> p.getFileName().toString().startsWith("crash-"))
                    .filter(p -> {
                        try { return Files.getLastModifiedTime(p).toMillis() >= afterMs; }
                        catch (Exception e) { return false; }
                    })
                    .max(Comparator.comparingLong(p -> {
                        try { return Files.getLastModifiedTime(p).toMillis(); }
                        catch (Exception e) { return 0L; }
                    }))
                    .orElse(null);
        } catch (Exception e) { return null; }
    }

    private static String extractLine(String text, String prefix) {
        int idx = text.indexOf(prefix);
        if (idx < 0) return null;
        int start = idx + prefix.length();
        int end = text.indexOf('\n', start);
        if (end < 0) end = text.length();
        String line = text.substring(start, end).trim();
        return line.isEmpty() ? null : line;
    }

    private record Rule(Pattern pattern, String title, String summary, List<String> suggestions) {}

    private static final List<Rule> RULES = List.of(
        new Rule(
            Pattern.compile("UnsupportedClassVersionError|class file version \\d+"),
            "Несовпадение версии Java",
            "Minecraft требует другую версию JDK.",
            List.of(
                "Установи JDK 25: https://adoptium.net/",
                "Проверь что JDK в C:\\Program Files\\Eclipse Adoptium\\jdk-25*",
                "Лаунчер найдёт его сам через findJava()."
            )
        ),
        new Rule(
            Pattern.compile("EXCEPTION_ACCESS_VIOLATION.*?(lwjgl|GLFW)|org\\.lwjgl.*(Violation|crash)", Pattern.DOTALL),
            "Крэш нативной библиотеки (LWJGL)",
            "Нативная библиотека (LWJGL или драйвер GPU) жёстко упала.",
            List.of(
                "Обнови драйверы GPU (NVIDIA / AMD).",
                "Удали D:\\wormlauncher\\game\\natives\\{version}\\ и перезапусти.",
                "Не запускай MC через RDP / VM."
            )
        ),
        new Rule(
            Pattern.compile("NoClassDefFoundError.*?CallbackInfo|CallbackInfo.*?NoClassDefFoundError"),
            "Конфликт Mixin / authlib",
            "Mixin-система упала — обычно из-за authlib-injector на современном MC.",
            List.of(
                "Известная проблема authlib-injector на MC 26.x.",
                "GameLauncher уже пропускает authlib для MC 25+.",
                "Если повторяется — смени версию authlib-injector."
            )
        ),
        new Rule(
            Pattern.compile("No OpenGL context found|GLFW.*?error|Failed to create window"),
            "Ошибка OpenGL / GPU",
            "Minecraft не смог создать окно рендера.",
            List.of(
                "Установи драйверы GPU.",
                "Не запускай через RDP — запускай локально.",
                "Для ноутбука с гибридным GPU — форсируй dGPU через Windows."
            )
        ),
        new Rule(
            Pattern.compile("OutOfMemoryError"),
            "Не хватает памяти",
            "Java закончилась heap-память.",
            List.of(
                "Увеличь -Xmx в GameLauncher (сейчас 4G).",
                "Закрой лишние приложения.",
                "Для тяжёлых сборок нормально 6-8G."
            )
        ),
        new Rule(
            Pattern.compile("Missing or unsupported mandatory dependencies"),
            "Нет обязательных модов",
            "Один из модов требует другой, который не установлен.",
            List.of(
                "Смотри crash report — там список нужных модов.",
                "Добавь недостающие моды в сборку.",
                "Проверь версию fabric-api."
            )
        ),
        new Rule(
            Pattern.compile("java\\.lang\\.NoSuchMethodError|java\\.lang\\.AbstractMethodError"),
            "Несовместимость модов",
            "Два мода ожидают разные версии одной библиотеки.",
            List.of(
                "Мод собран под другую версию MC.",
                "Обнови все моды под ту же версию MC.",
                "Проверь fabric-loader совпадает с версией модов."
            )
        ),
        new Rule(
            Pattern.compile("UnsatisfiedLinkError"),
            "Не найдена нативная библиотека",
            "Minecraft'у не хватает DLL/SO файла.",
            List.of(
                "Удали D:\\wormlauncher\\game\\natives\\{version}\\ и перезапусти.",
                "Проверь разрядность Java (32-bit vs 64-bit)."
            )
        ),
        new Rule(
            Pattern.compile("java\\.util\\.zip\\.ZipException|java\\.io\\.IOException.*?(corrupt|truncated)|Invalid or corrupt jarfile"),
            "Повреждены файлы игры",
            "Jar или asset повреждён.",
            List.of(
                "Удали D:\\wormlauncher\\game\\versions\\{version}\\/ и перезапусти.",
                "Или удали D:\\wormlauncher\\game\\assets\\ и перезапусти."
            )
        )
    );
}