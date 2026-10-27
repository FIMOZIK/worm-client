package launcher;

import launcher.auth.GameSession;
import launcher.auth.TokenStore;
import launcher.mc.GameLauncher;

public class Test3 {
    public static void main(String[] args) throws Exception {
        GameSession session = TokenStore.load();
        if (session == null) {
            System.out.println("нет сохранённой сессии. сначала запусти LauncherApp и залогинься.");
            return;
        }
        System.out.println("сессия: " + session.type() + " / " + session.username() + " / " + session.uuid());

        Process p = GameLauncher.launch(session, "1.21.11");
        int code = p.waitFor();
        System.out.println("Minecraft завершился с кодом " + code);
    }
}