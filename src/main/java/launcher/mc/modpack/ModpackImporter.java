package launcher.mc.modpack;

import com.google.gson.*;
import launcher.Profile;
import launcher.mc.Downloader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ModpackImporter {

    public record Result(Profile profile, Path modpackDir) {}

    public interface Progress {
        void status(String msg);
        void progress(double pct);
    }

    // ============================================================
    // DETECT
    // ============================================================
    public static ModpackType detectType(Path archive) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            if (zip.getEntry("modrinth.index.json") != null) return ModpackType.MRPACK;
            if (zip.getEntry("manifest.json") != null)         return ModpackType.CURSEFORGE;
        }
        return ModpackType.UNKNOWN;
    }

    public static Result importModpack(Path archive, Path gameDir, Progress p) throws Exception {
        ModpackType type = detectType(archive);
        p.status("detected: " + type + " (" + archive.getFileName() + ")");
        return switch (type) {
            case MRPACK      -> importModrinth(archive, gameDir, p);
            case CURSEFORGE  -> importCurse(archive, gameDir, p);
            default          -> throw new Exception("unknown modpack format: " + archive.getFileName());
        };
    }

    // ============================================================
    // MODRINTH .mrpack
    // ============================================================
    private static Result importModrinth(Path archive, Path gameDir, Progress p) throws Exception {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry idxEntry = zip.getEntry("modrinth.index.json");
            if (idxEntry == null) throw new Exception("modrinth.index.json missing");
            String idxStr = readEntry(zip, idxEntry);
            JsonObject idx = JsonParser.parseString(idxStr).getAsJsonObject();

            String name = idx.has("name") ? idx.get("name").getAsString() : "modpack";
            JsonObject deps = idx.has("dependencies") ? idx.getAsJsonObject("dependencies") : new JsonObject();

            if (!deps.has("minecraft"))
                throw new Exception("modrinth.index.json has no minecraft version");
            String mcVersion = deps.get("minecraft").getAsString();

            String loader = "Vanilla";
            String loaderVersion = null;
            if (deps.has("fabric-loader")) {
                loader = "Fabric";
                loaderVersion = deps.get("fabric-loader").getAsString();
            } else if (deps.has("forge")) {
                loader = "Forge";
                loaderVersion = deps.get("forge").getAsString();
            } else if (deps.has("quilt-loader")) {
                loader = "Quilt";
                loaderVersion = deps.get("quilt-loader").getAsString();
            } else if (deps.has("neoforge")) {
                // у нас пока нет NeoForge, ставим через Forge-путь
                loader = "Forge";
                loaderVersion = deps.get("neoforge").getAsString();
            }

            String safeName = sanitize(name);
            Path mpDir = gameDir.resolve("modpacks").resolve(safeName);
            Files.createDirectories(mpDir.resolve("mods"));

            JsonArray files = idx.has("files") ? idx.getAsJsonArray("files") : new JsonArray();
            int total = files.size();
            int done = 0;
            int failed = 0;

            for (JsonElement fe : files) {
                JsonObject fo = fe.getAsJsonObject();
                String path = fo.get("path").getAsString();

                // пропускаем server-only
                if (fo.has("env")) {
                    JsonObject env = fo.getAsJsonObject("env");
                    if (env.has("client") && "unsupported".equals(env.get("client").getAsString())) {
                        done++;
                        p.progress((double) done / Math.max(1, total));
                        continue;
                    }
                }

                JsonArray downloads = fo.has("downloads")
                        ? fo.getAsJsonArray("downloads") : new JsonArray();
                if (downloads.isEmpty()) {
                    p.status("skip (no downloads): " + path);
                    done++;
                    p.progress((double) done / Math.max(1, total));
                    continue;
                }

                String url = downloads.get(0).getAsString();
                String sha1 = null;
                if (fo.has("hashes")) {
                    JsonObject h = fo.getAsJsonObject("hashes");
                    if (h.has("sha1")) sha1 = h.get("sha1").getAsString();
                }

                Path target = mpDir.resolve(path);
                try {
                    Downloader.download(url, target, sha1);
                    p.status("[" + (done + 1) + "/" + total + "] " + path);
                } catch (Exception e) {
                    failed++;
                    p.status("✗ " + path + " — " + e.getMessage());
                }
                done++;
                p.progress((double) done / Math.max(1, total));
            }

            extractOverrides(zip, "overrides/", mpDir, p);

            Profile profile = new Profile(name, mcVersion, loader, loaderVersion);
            profile.modpackId = safeName;
            profile.modpackType = ModpackType.MRPACK.name();

            p.status("✓ imported: " + name
                    + " | mc=" + mcVersion
                    + " | " + loader + (loaderVersion != null ? " " + loaderVersion : "")
                    + " | mods=" + (total - failed) + "/" + total);
            return new Result(profile, mpDir);
        }
    }

    // ============================================================
    // CURSEFORGE .zip
    // ============================================================
    private static Result importCurse(Path archive, Path gameDir, Progress p) throws Exception {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry mf = zip.getEntry("manifest.json");
            if (mf == null) throw new Exception("manifest.json missing");
            String mfStr = readEntry(zip, mf);
            JsonObject o = JsonParser.parseString(mfStr).getAsJsonObject();

            String name = o.has("name") ? o.get("name").getAsString() : "modpack";
            JsonObject mcObj = o.getAsJsonObject("minecraft");
            String mcVersion = mcObj.get("version").getAsString();

            String loader = "Vanilla";
            String loaderVersion = null;
            if (mcObj.has("modLoaders")) {
                for (JsonElement le : mcObj.getAsJsonArray("modLoaders")) {
                    JsonObject lo = le.getAsJsonObject();
                    if (lo.has("primary") && lo.get("primary").getAsBoolean()) {
                        String id = lo.get("id").getAsString(); // "forge-47.2.0"
                        int dash = id.indexOf('-');
                        if (dash > 0) {
                            String kind = id.substring(0, dash).toLowerCase();
                            loaderVersion = id.substring(dash + 1);
                            loader = switch (kind) {
                                case "forge"    -> "Forge";
                                case "fabric"   -> "Fabric";
                                case "quilt"    -> "Quilt";
                                case "neoforge" -> "Forge";
                                default         -> "Vanilla";
                            };
                        }
                        break;
                    }
                }
            }

            String safeName = sanitize(name);
            Path mpDir = gameDir.resolve("modpacks").resolve(safeName);
            Files.createDirectories(mpDir.resolve("mods"));

            JsonArray files = o.has("files") ? o.getAsJsonArray("files") : new JsonArray();
            int total = files.size();
            int done = 0;
            int failed = 0;

            for (JsonElement fe : files) {
                JsonObject fo = fe.getAsJsonObject();
                long projectID = fo.get("projectID").getAsLong();
                long fileID    = fo.get("fileID").getAsLong();
                boolean required = !fo.has("required") || fo.get("required").getAsBoolean();

                if (!required) {
                    p.status("skip (optional): " + projectID + "/" + fileID);
                    done++;
                    p.progress((double) done / Math.max(1, total));
                    continue;
                }

                p.status("[" + (done + 1) + "/" + total + "] resolve curse "
                        + projectID + "/" + fileID + " ...");
                try {
                    CurseApi.File cf = CurseApi.resolve(projectID, fileID);
                    if (cf == null) {
                        failed++;
                        p.status("✗ cannot resolve " + projectID + "/" + fileID);
                    } else {
                        Path target = mpDir.resolve("mods").resolve(cf.fileName());
                        Downloader.download(cf.url(), target, null);
                        p.status("   ← " + cf.fileName());
                    }
                } catch (Exception e) {
                    failed++;
                    p.status("✗ " + projectID + "/" + fileID + " — " + e.getMessage());
                }
                done++;
                p.progress((double) done / Math.max(1, total));
            }

            extractOverrides(zip, "overrides/", mpDir, p);

            Profile profile = new Profile(name, mcVersion, loader, loaderVersion);
            profile.modpackId = safeName;
            profile.modpackType = ModpackType.CURSEFORGE.name();

            p.status("✓ imported: " + name
                    + " | mc=" + mcVersion
                    + " | " + loader + (loaderVersion != null ? " " + loaderVersion : "")
                    + " | mods=" + (total - failed) + "/" + total);
            return new Result(profile, mpDir);
        }
    }

    // ============================================================
    // HELPERS
    // ============================================================
    private static void extractOverrides(ZipFile zip, String prefix, Path dest, Progress p)
            throws IOException {
        Enumeration<? extends ZipEntry> en = zip.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            if (e.isDirectory()) continue;
            if (!e.getName().startsWith(prefix)) continue;
            String rel = e.getName().substring(prefix.length());
            if (rel.isEmpty()) continue;
            Path target = dest.resolve(rel);
            Files.createDirectories(target.getParent());
            try (InputStream in = zip.getInputStream(e)) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            p.status("override: " + rel);
        }
    }

    private static String readEntry(ZipFile zip, ZipEntry e) throws IOException {
        try (InputStream in = zip.getInputStream(e)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String sanitize(String s) {
        if (s == null || s.isBlank()) return "modpack";
        String out = s.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (out.length() > 64) out = out.substring(0, 64);
        return out.isEmpty() ? "modpack" : out;
    }

    private ModpackImporter() {}
}