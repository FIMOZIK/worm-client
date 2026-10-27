package launcher.mc;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.DoubleConsumer;

public class AssetDownloader {

    public static void download(VersionJson vj, Path gameDir) throws Exception {
        download(vj, gameDir, p -> {});
    }

    public static void download(VersionJson vj, Path gameDir, DoubleConsumer progress) throws Exception {
        Path assetsRoot = gameDir.resolve("assets");
        Path indexes = assetsRoot.resolve("indexes");
        Path objects = assetsRoot.resolve("objects");
        Files.createDirectories(indexes);
        Files.createDirectories(objects);

        Path indexFile = indexes.resolve(vj.assetIndexId + ".json");
        Downloader.download(vj.assetIndexUrl, indexFile, vj.assetIndexSha1);

        JsonObject root = JsonParser.parseString(Files.readString(indexFile)).getAsJsonObject();
        JsonObject objs = root.getAsJsonObject("objects");

        int total = objs.size();
        int done = 0;
        for (Map.Entry<String, JsonElement> e : objs.entrySet()) {
            JsonObject o = e.getValue().getAsJsonObject();
            String hash = o.get("hash").getAsString();
            String sub = hash.substring(0, 2);
            Path target = objects.resolve(sub).resolve(hash);
            if (!Files.exists(target)) {
                String url = "https://resources.download.minecraft.net/" + sub + "/" + hash;
                Downloader.download(url, target, hash);
            }
            done++;
            if (done % 50 == 0) progress.accept((double) done / total);
        }
        progress.accept(1.0);
    }
}