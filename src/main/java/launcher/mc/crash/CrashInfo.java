package launcher.mc.crash;

import java.nio.file.Path;
import java.util.List;

public record CrashInfo(
        String title,
        String summary,
        List<String> suggestions,
        Path file,
        String raw
) {}