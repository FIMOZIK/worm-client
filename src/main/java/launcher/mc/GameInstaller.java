package launcher.mc;

import java.nio.file.*;
import java.util.function.DoubleConsumer;

public class GameInstaller {

    public static Path install(String versionId, Path gameDir) throws Exception {
        return install(versionId, gameDir, p -> {});
    }

    public static Path install(String versionId, Path gameDir, DoubleConsumer progress) throws Exception {
        VersionManifest.Entry entry = VersionManifest.find(versionId);
        Path versionJsonFile = VersionManifest.downloadJson(entry, gameDir);
        VersionJson vj = VersionJson.parse(versionJsonFile);

        progress.accept(0.05);

        Path clientJar = gameDir.resolve("versions").resolve(vj.id).resolve(vj.id + ".jar");
        Downloader.download(vj.client.url(), clientJar, vj.client.sha1());
        progress.accept(0.15);

        var libs = LibraryDownloader.download(vj, gameDir);
        progress.accept(0.30);

        AssetDownloader.download(vj, gameDir, p -> progress.accept(0.30 + p * 0.65));

        if (vj.loggingClient != null) {
            Path logCfg = gameDir.resolve("versions").resolve(vj.id).resolve("logging.xml");
            Downloader.download(vj.loggingClient.url(), logCfg, vj.loggingClient.sha1());
        }
        progress.accept(1.0);
        return clientJar;
    }
}