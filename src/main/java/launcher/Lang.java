package launcher;

import java.util.HashMap;
import java.util.Map;

public final class Lang {

    public static final String RU = "ru";
    public static final String EN = "en";

    private static String current = RU;
    private static final Map<String, Map<String, String>> M = new HashMap<>();

    static {
        Map<String, String> ru = new HashMap<>();
        ru.put("app.title", "worm launcher");
        ru.put("status.not_logged", "не вошёл");
        ru.put("field.nick", "Ely.by ник");
        ru.put("field.pass", "пароль");
        ru.put("btn.login_ely", "Войти (Ely)");
        ru.put("btn.logout", "Выйти");
        ru.put("btn.oauth", "Ely через браузер");
        ru.put("btn.ms", "Войти через Microsoft");
        ru.put("btn.open_folder", "Открыть папку игры");
        ru.put("btn.check_updates", "Проверить обновления");
        ru.put("btn.settings", "⚙ Настройки");
        ru.put("chk.minimize", "сворачивать в трей при игре");
        ru.put("chk.autolaunch", "авто-запуск MC после входа");
        ru.put("label.profile", "профиль:");
        ru.put("btn.save_profile", "+ профиль");
        ru.put("btn.delete_profile", "×");
        ru.put("label.version", "версия:");
        ru.put("label.loader", "загрузчик:");
        ru.put("label.log", "лог:");
        ru.put("btn.play", "Играть");
        ru.put("prompt.loading_versions", "загрузка версий...");
        ru.put("prompt.loading_loaders", "загрузка лоадеров...");
        ru.put("prompt.loading_forge", "загрузка forge...");
        ru.put("prompt.loading_quilt", "загрузка quilt...");
        ru.put("prompt.no_loaders", "нет лоадеров");
        ru.put("prompt.no_forge", "нет forge");
        ru.put("prompt.no_quilt", "нет quilt");
        ru.put("prompt.loader", "лоадер...");
        ru.put("prompt.forge", "forge...");
        ru.put("prompt.quilt", "quilt...");
        ru.put("prompt.profile", "профиль...");
        ru.put("prompt.no_profiles", "нет профилей");

        ru.put("settings.title", "Настройки");
        ru.put("settings.header", "Настройки лаунчера");
        ru.put("settings.theme", "Тема");
        ru.put("settings.dark", "тёмная тема");
        ru.put("settings.layout", "Расположение кнопок");
        ru.put("settings.bg", "Фон (картинка / gif / видео)");
        ru.put("settings.pick", "Выбрать...");
        ru.put("settings.clear", "Убрать");
        ru.put("settings.bg_opacity", "Прозрачность фона");
        ru.put("settings.font", "Шрифт (.ttf / .otf)");
        ru.put("settings.font_pick", "Выбрать .ttf");
        ru.put("settings.font_reset", "Сбросить");
        ru.put("settings.sound", "Звук нажатия клавиш (wav/mp3/mp4)");
        ru.put("settings.sound_pick", "Выбрать...");
        ru.put("settings.sound_reset", "Сбросить");
        ru.put("settings.sound_test", "Тест");
        ru.put("settings.sound_on", "звук нажатия клавиш: включён");
        ru.put("settings.sound_off", "звук нажатия клавиш: выключен");
        ru.put("settings.sound_none", "не выбрано");
        ru.put("settings.updates", "Обновления");
        ru.put("settings.auto_check", "проверять обновления при запуске");
        ru.put("settings.window", "Режим окна");
        ru.put("settings.windowed", "Оконный");
        ru.put("settings.maximized", "Развёрнутый");
        ru.put("settings.fullscreen", "Полноэкранный");
        ru.put("settings.log", "Лог");
        ru.put("settings.show_log", "показывать лог в главном окне");
        ru.put("settings.log_opacity", "Прозрачность лога");
        ru.put("settings.open_log", "Открыть лог в отдельном окне");
        ru.put("settings.apply", "Применить");
        ru.put("settings.cancel", "Отмена");
        ru.put("settings.lang", "Язык");
        ru.put("settings.lang_ru", "Русский");
        ru.put("settings.lang_en", "English");
        ru.put("settings.uninstall", "Удалить лаунчер...");
        ru.put("settings.default", "по умолчанию");
        ru.put("settings.shortcut", "Создать ярлык на рабочем столе");
        ru.put("settings.shortcut", "Создать ярлык...");
        ru.put("shortcut.pick_title", "Куда сохранить ярлык?");
        ru.put("shortcut.done", "Ярлык создан:");

        ru.put("log.title", "worm launcher — лог");

        ru.put("install.title", "worm launcher — установка");
        ru.put("install.sub", "выбери, где хранить лаунчер и Minecraft");
        ru.put("install.lang", "Язык / Language");
        ru.put("install.launcher_dir", "Папка лаунчера:");
        ru.put("install.mc_dir", "Папка Minecraft:");
        ru.put("install.shortcut", "создать ярлык на рабочем столе");
        ru.put("install.start", "Установить и запустить");
        ru.put("install.cancel", "Отмена");
        ru.put("install.pick", "Выбрать...");
        ru.put("install.shortcut_fail", "ярлык не создан: ");
        ru.put("install.error", "ошибка: ");

        ru.put("uninst.title", "worm launcher — удаление");
        ru.put("uninst.sub", "отметь, что удалять");
        ru.put("uninst.cb_launcher", "удалить файлы лаунчера");
        ru.put("uninst.cb_mc", "удалить файлы Minecraft");
        ru.put("uninst.btn", "Удалить");
        ru.put("uninst.cancel", "Отмена");
        ru.put("uninst.confirm_title", "Подтверждение");
        ru.put("uninst.confirm_msg", "Удалить выбранное? Это действие нельзя отменить.");
        ru.put("uninst.done", "✓ удалено");

        ru.put("upd.title", "Доступно обновление");
        ru.put("upd.header", "worm launcher");
        ru.put("upd.you_have", "у тебя");
        ru.put("upd.download_install", "Скачать и обновить");
        ru.put("upd.open_page", "Открыть страницу релиза");
        ru.put("upd.later", "Позже");
        ru.put("upd.checking", "→ проверка обновлений...");
        ru.put("upd.latest", "✓ установлена последняя версия");
        ru.put("upd.available", "⚠ доступно обновление");
        ru.put("upd.startup_available", "⚠ доступно обновление {0} (у тебя {1}) — нажми \"Проверить обновления\"");
        ru.put("upd.startup_latest", "✓ обновлений нет ({0} актуальна)");
        ru.put("upd.startup_failed", "⚠ авто-проверка обновлений: ");
        ru.put("upd.check_failed", "Не удалось проверить:\\n");
        ru.put("upd.downloading", "→ скачивание обновления");
        ru.put("upd.downloaded", "✓ обновление скачано, перезапуск ...");
        ru.put("upd.closing", "→ закрываю лаунчер для замены jar ...");
        ru.put("upd.download_failed", "✗ скачивание упало: ");
        ru.put("upd.bat_failed", "✗ не смог запустить update.bat: ");
        ru.put("upd.jar_only", "⚠ автообновление доступно только при запуске из jar");
        ru.put("upd.no_asset", "⚠ в релизе нет .jar-ассета — открыл страницу");
        ru.put("upd.no_repo", "репозиторий для обновлений не настроен");
        ru.put("upd.not_found", "не найден или нет релизов");

        ru.put("game.launching", "→ запуск Minecraft");
        ru.put("game.installing_fabric", "→ установка fabric-профиля ...");
        ru.put("game.installing_forge", "→ установка forge (может занять пару минут) ...");
        ru.put("game.installing_quilt", "→ установка quilt-профиля ...");
        ru.put("game.finished", "→ Minecraft завершился, код");
        ru.put("game.failed", "✗ запуск упал: ");
        ru.put("game.no_session", "✗ сначала войди");
        ru.put("game.no_version", "✗ выбери версию");
        ru.put("game.no_fabric", "✗ выбери версию fabric-лоадера");
        ru.put("game.no_forge", "✗ выбери версию forge");
        ru.put("game.no_quilt", "✗ выбери версию quilt-лоадера");
        ru.put("game.tray_launching", "Minecraft запускается. Свёрнут в трей.");

        ru.put("tray.open", "Открыть");
        ru.put("tray.quit", "Выход");
        ru.put("tray.minimized", "Свёрнут в трей. Двойной клик — открыть.");

        M.put(RU, ru);

        Map<String, String> en = new HashMap<>();
        en.put("app.title", "worm launcher");
        en.put("status.not_logged", "not logged in");
        en.put("field.nick", "Ely.by nickname");
        en.put("field.pass", "password");
        en.put("btn.login_ely", "Login (Ely)");
        en.put("btn.logout", "Logout");
        en.put("btn.oauth", "Ely via browser");
        en.put("btn.ms", "Login via Microsoft");
        en.put("btn.open_folder", "Open game folder");
        en.put("btn.check_updates", "Check for updates");
        en.put("btn.settings", "⚙ Settings");
        en.put("chk.minimize", "minimize to tray on launch");
        en.put("chk.autolaunch", "auto-launch MC after login");
        en.put("label.profile", "profile:");
        en.put("btn.save_profile", "+ profile");
        en.put("btn.delete_profile", "×");
        en.put("label.version", "version:");
        en.put("label.loader", "loader:");
        en.put("label.log", "log:");
        en.put("btn.play", "Play");
        en.put("prompt.loading_versions", "loading versions...");
        en.put("prompt.loading_loaders", "loading loaders...");
        en.put("prompt.loading_forge", "loading forge...");
        en.put("prompt.loading_quilt", "loading quilt...");
        en.put("prompt.no_loaders", "no loaders");
        en.put("prompt.no_forge", "no forge");
        en.put("prompt.no_quilt", "no quilt");
        en.put("prompt.loader", "loader...");
        en.put("prompt.forge", "forge...");
        en.put("prompt.quilt", "quilt...");
        en.put("prompt.profile", "profile...");
        en.put("prompt.no_profiles", "no profiles");

        en.put("settings.title", "Settings");
        en.put("settings.header", "Launcher settings");
        en.put("settings.theme", "Theme");
        en.put("settings.dark", "dark theme");
        en.put("settings.layout", "Button layout");
        en.put("settings.bg", "Background (image / gif / video)");
        en.put("settings.pick", "Choose...");
        en.put("settings.clear", "Clear");
        en.put("settings.bg_opacity", "Background opacity");
        en.put("settings.font", "Font (.ttf / .otf)");
        en.put("settings.font_pick", "Choose .ttf");
        en.put("settings.font_reset", "Reset");
        en.put("settings.sound", "Keypress sound (wav/mp3/mp4)");
        en.put("settings.sound_pick", "Choose...");
        en.put("settings.sound_reset", "Reset");
        en.put("settings.sound_test", "Test");
        en.put("settings.sound_on", "keypress sound: on");
        en.put("settings.sound_off", "keypress sound: off");
        en.put("settings.sound_none", "not selected");
        en.put("settings.updates", "Updates");
        en.put("settings.auto_check", "check for updates on startup");
        en.put("settings.window", "Window mode");
        en.put("settings.windowed", "Windowed");
        en.put("settings.maximized", "Maximized");
        en.put("settings.fullscreen", "Fullscreen");
        en.put("settings.log", "Log");
        en.put("settings.show_log", "show log in main window");
        en.put("settings.log_opacity", "Log opacity");
        en.put("settings.open_log", "Open log in a separate window");
        en.put("settings.apply", "Apply");
        en.put("settings.cancel", "Cancel");
        en.put("settings.lang", "Language");
        en.put("settings.lang_ru", "Русский");
        en.put("settings.lang_en", "English");
        en.put("settings.uninstall", "Uninstall launcher...");
        en.put("settings.shortcut", "Create desktop shortcut");
        en.put("settings.shortcut", "Create shortcut...");
        en.put("shortcut.pick_title", "Where to save shortcut?");
        en.put("shortcut.done", "Shortcut created:");
        en.put("settings.default", "default");

        en.put("log.title", "worm launcher — log");

        en.put("install.title", "worm launcher — setup");
        en.put("install.sub", "choose where to store launcher and Minecraft");
        en.put("install.lang", "Language / Язык");
        en.put("install.launcher_dir", "Launcher folder:");
        en.put("install.mc_dir", "Minecraft folder:");
        en.put("install.shortcut", "create desktop shortcut");
        en.put("install.start", "Install and launch");
        en.put("install.cancel", "Cancel");
        en.put("install.pick", "Choose...");
        en.put("install.shortcut_fail", "shortcut not created: ");
        en.put("install.error", "error: ");

        en.put("uninst.title", "worm launcher — uninstall");
        en.put("uninst.sub", "check what to remove");
        en.put("uninst.cb_launcher", "delete launcher files");
        en.put("uninst.cb_mc", "delete Minecraft files");
        en.put("uninst.btn", "Uninstall");
        en.put("uninst.cancel", "Cancel");
        en.put("uninst.confirm_title", "Confirm");
        en.put("uninst.confirm_msg", "Delete selected? This cannot be undone.");
        en.put("uninst.done", "✓ removed");

        en.put("upd.title", "Update available");
        en.put("upd.header", "worm launcher");
        en.put("upd.you_have", "you have");
        en.put("upd.download_install", "Download and update");
        en.put("upd.open_page", "Open release page");
        en.put("upd.later", "Later");
        en.put("upd.checking", "→ checking for updates...");
        en.put("upd.latest", "✓ latest version installed");
        en.put("upd.available", "⚠ update available");
        en.put("upd.startup_available", "⚠ update available {0} (you have {1}) — click \"Check for updates\"");
        en.put("upd.startup_latest", "✓ no updates ({0} is latest)");
        en.put("upd.startup_failed", "⚠ auto update check: ");
        en.put("upd.check_failed", "Check failed:\\n");
        en.put("upd.downloading", "→ downloading update");
        en.put("upd.downloaded", "✓ update downloaded, restarting ...");
        en.put("upd.closing", "→ closing launcher to replace jar ...");
        en.put("upd.download_failed", "✗ download failed: ");
        en.put("upd.bat_failed", "✗ failed to launch update.bat: ");
        en.put("upd.jar_only", "⚠ self-update available only when launched from jar");
        en.put("upd.no_asset", "⚠ no .jar asset in release — opened page");
        en.put("upd.no_repo", "update repo not configured");
        en.put("upd.not_found", "not found or no releases");

        en.put("game.launching", "→ launching Minecraft");
        en.put("game.installing_fabric", "→ installing fabric profile ...");
        en.put("game.installing_forge", "→ installing forge (may take a few minutes) ...");
        en.put("game.installing_quilt", "→ installing quilt profile ...");
        en.put("game.finished", "→ Minecraft finished, exit code");
        en.put("game.failed", "✗ launch failed: ");
        en.put("game.no_session", "✗ please log in first");
        en.put("game.no_version", "✗ select a version");
        en.put("game.no_fabric", "✗ select fabric loader version");
        en.put("game.no_forge", "✗ select forge version");
        en.put("game.no_quilt", "✗ select quilt loader version");
        en.put("game.tray_launching", "Minecraft launching. Minimized to tray.");

        en.put("tray.open", "Open");
        en.put("tray.quit", "Quit");
        en.put("tray.minimized", "Minimized to tray. Double-click to open.");

        M.put(EN, en);
    }

    private Lang() {}

    public static void set(String lang) {
        current = EN.equalsIgnoreCase(lang) ? EN : RU;
    }

    public static String current() { return current; }

    public static String t(String key) {
        Map<String, String> m = M.get(current);
        String v = m != null ? m.get(key) : null;
        if (v != null) return v;
        Map<String, String> fb = M.get(RU);
        return fb != null && fb.containsKey(key) ? fb.get(key) : key;
    }

    public static String t(String key, Object... args) {
        String s = t(key);
        for (int i = 0; i < args.length; i++) {
            s = s.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return s;
    }
}