package launcher.mc;

import java.nio.file.*;
import java.util.*;

public class LibraryDownloader {

    public static List<Path> download(VersionJson vj, Path gameDir) throws Exception {
        Path libsRoot = gameDir.resolve("libraries");
        Path nativesRoot = gameDir.resolve("natives").resolve(vj.id);
        Files.createDirectories(nativesRoot);

        List<Path> classpath = new ArrayList<>();

        for (VersionJson.Library lib : vj.libraries) {
            if (!Rules.allows(lib.rules())) continue;
            if (lib.artifact() == null) continue;

            String rel = lib.artifact().path();
            if (rel == null) continue;

            Path dest = libsRoot.resolve(rel);
            Downloader.download(lib.artifact().url(), dest, lib.artifact().sha1());

            String filename = dest.getFileName().toString();
            if (filename.contains("-natives-")) {
                // natives jar → распаковать, в classpath не добавлять
                unzip(dest, nativesRoot);
            } else {
                classpath.add(dest);
            }
        }
        return classpath;
    }

    private static void unzip(Path jar, Path targetDir) throws Exception {
        try (var zf = new java.util.zip.ZipFile(jar.toFile())) {
            for (var it = zf.entries(); it.hasMoreElements(); ) {
                var e = it.nextElement();
                String n = e.getName();
                if (n.startsWith("META-INF/") || e.isDirectory()) continue;
                Path out = targetDir.resolve(n);
                Files.createDirectories(out.getParent());
                try (var in = zf.getInputStream(e)) {
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}