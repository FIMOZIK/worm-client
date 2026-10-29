package launcher.mc;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class VersionJson {

    public record Download(String path, String url, String sha1, long size) {}

    public record Library(
            String name,
            Download artifact,
            Map<String, String> rules
    ) {}

    public final String id;
    public final String inheritsFrom;
    public final String jarVersionId;
    public final String mainClass;
    public final String assetIndexUrl;
    public final String assetIndexSha1;
    public final String assetIndexId;
    public final Download client;
    public final Download loggingClient;
    public final List<Library> libraries;
    public final List<String> gameArguments;
    public final List<String> jvmArguments;

    private VersionJson(String id, String inheritsFrom, String jarVersionId, String mainClass,
                        String assetIndexUrl, String assetIndexSha1, String assetIndexId,
                        Download client, Download loggingClient, List<Library> libraries,
                        List<String> gameArguments, List<String> jvmArguments) {
        this.id = id;
        this.inheritsFrom = inheritsFrom;
        this.jarVersionId = jarVersionId;
        this.mainClass = mainClass;
        this.assetIndexUrl = assetIndexUrl;
        this.assetIndexSha1 = assetIndexSha1;
        this.assetIndexId = assetIndexId;
        this.client = client;
        this.loggingClient = loggingClient;
        this.libraries = libraries;
        this.gameArguments = gameArguments;
        this.jvmArguments = jvmArguments;
    }

    public static Path jsonPath(Path gameDir, String versionId) {
        return gameDir.resolve("versions").resolve(versionId).resolve(versionId + ".json");
    }

    public static VersionJson load(Path gameDir, String versionId) throws Exception {
        JsonObject child = readRaw(gameDir, versionId);

        List<JsonObject> chain = new ArrayList<>();
        JsonObject cur = child;
        while (cur.has("inheritsFrom")) {
            String parentId = cur.get("inheritsFrom").getAsString();
            JsonObject parent = readRaw(gameDir, parentId);
            chain.add(parent);
            cur = parent;
        }

        JsonObject merged = new JsonObject();
        for (int i = chain.size() - 1; i >= 0; i--) {
            mergeOnto(merged, chain.get(i));
        }
        mergeOnto(merged, child);

        String jarVersionId = versionId;
        for (JsonObject p : chain) {
            if (p.has("downloads") && p.getAsJsonObject("downloads").has("client")) {
                jarVersionId = p.get("id").getAsString();
                break;
            }
        }
        if (jarVersionId.equals(versionId)
                && child.has("downloads")
                && child.getAsJsonObject("downloads").has("client")) {
            jarVersionId = versionId;
        }

        String inheritsFrom = child.has("inheritsFrom") ? child.get("inheritsFrom").getAsString() : null;
        return fromJson(versionId, inheritsFrom, jarVersionId, merged);
    }

    public static VersionJson parse(Path file) throws Exception {
        Path versionDir = file.getParent();
        Path versionsDir = versionDir.getParent();
        Path gameDir = versionsDir.getParent();
        String fileName = file.getFileName().toString();
        String versionId = fileName.endsWith(".json")
                ? fileName.substring(0, fileName.length() - 5)
                : fileName;
        return load(gameDir, versionId);
    }

    private static JsonObject readRaw(Path gameDir, String versionId) throws Exception {
        Path p = jsonPath(gameDir, versionId);
        if (!Files.exists(p)) throw new IllegalStateException("version.json не найден: " + p);
        return JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static void mergeOnto(JsonObject target, JsonObject src) {
        if (src.has("id")) target.add("id", src.get("id"));
        if (src.has("mainClass")) target.add("mainClass", src.get("mainClass"));

        for (String k : new String[]{"assetIndex", "downloads", "logging", "assets"}) {
            if (src.has(k)) target.add(k, src.get(k));
        }

        if (src.has("libraries")) {
            LinkedHashMap<String, JsonObject> map = new LinkedHashMap<>();
            if (target.has("libraries")) {
                for (JsonElement el : target.getAsJsonArray("libraries")) {
                    JsonObject lib = el.getAsJsonObject();
                    map.put(libKey(lib), lib);
                }
            }
            for (JsonElement el : src.getAsJsonArray("libraries")) {
                JsonObject lib = el.getAsJsonObject();
                map.put(libKey(lib), lib);
            }
            JsonArray combined = new JsonArray();
            for (JsonObject o : map.values()) combined.add(o);
            target.add("libraries", combined);
        }

        // Аргументы: склеиваем родительские и дочерние (ваниль + Forge/Fabric).
        if (src.has("arguments")) {
            JsonObject srcArgs = src.getAsJsonObject("arguments");
            JsonObject targetArgs;
            if (target.has("arguments")) {
                targetArgs = target.getAsJsonObject("arguments");
            } else {
                targetArgs = new JsonObject();
                target.add("arguments", targetArgs);
            }
            for (String key : new String[]{"game", "jvm"}) {
                JsonArray combined = new JsonArray();
                if (targetArgs.has(key)) {
                    for (JsonElement e : targetArgs.getAsJsonArray(key)) combined.add(e);
                }
                if (srcArgs.has(key)) {
                    for (JsonElement e : srcArgs.getAsJsonArray(key)) combined.add(e);
                }
                targetArgs.add(key, combined);
            }
        }
        if (src.has("minecraftArguments") && !target.has("minecraftArguments")) {
            target.add("minecraftArguments", src.get("minecraftArguments"));
        }
    }

    private static String libKey(JsonObject lib) {
        return lib.has("name") ? lib.get("name").getAsString() : "";
    }

    private static String mavenPath(String name) {
        String[] p = name.split(":");
        if (p.length < 3) return null;
        String group = p[0].replace('.', '/');
        String artifact = p[1];
        String version = p[2];
        String classifier = p.length >= 4 ? p[3] : null;
        String file = artifact + "-" + version
                + (classifier != null ? "-" + classifier : "") + ".jar";
        return group + "/" + artifact + "/" + version + "/" + file;
    }

    private static VersionJson fromJson(String id, String inheritsFrom, String jarVersionId, JsonObject o) {
        String mainClass = o.get("mainClass").getAsString();

        JsonObject idx = o.getAsJsonObject("assetIndex");
        String assetIndexUrl = idx.get("url").getAsString();
        String assetIndexSha1 = idx.has("sha1") ? idx.get("sha1").getAsString() : null;
        String assetIndexId = idx.get("id").getAsString();

        Download client = null;
        if (o.has("downloads")) {
            JsonObject d = o.getAsJsonObject("downloads");
            if (d.has("client")) {
                JsonObject cl = d.getAsJsonObject("client");
                client = new Download(null,
                        cl.get("url").getAsString(),
                        cl.has("sha1") ? cl.get("sha1").getAsString() : null,
                        cl.has("size") ? cl.get("size").getAsLong() : -1);
            }
        }

        Download lc = null;
        if (o.has("logging")) {
            JsonObject lo = o.getAsJsonObject("logging");
            if (lo.has("client")) {
                JsonObject f = lo.getAsJsonObject("client").getAsJsonObject("file");
                lc = new Download(null,
                        f.get("url").getAsString(),
                        f.has("sha1") ? f.get("sha1").getAsString() : null,
                        f.has("size") ? f.get("size").getAsLong() : -1);
            }
        }

        List<Library> libs = new ArrayList<>();
        if (o.has("libraries")) {
            for (JsonElement e : o.getAsJsonArray("libraries")) {
                JsonObject lo = e.getAsJsonObject();
                String name = lo.get("name").getAsString();

                Download art = null;
                if (lo.has("downloads")) {
                    JsonObject dd = lo.getAsJsonObject("downloads");
                    if (dd.has("artifact")) {
                        JsonObject a = dd.getAsJsonObject("artifact");
                        art = new Download(
                                a.has("path") ? a.get("path").getAsString() : null,
                                a.get("url").getAsString(),
                                a.has("sha1") ? a.get("sha1").getAsString() : null,
                                a.has("size") ? a.get("size").getAsLong() : -1);
                    }
                }
                if (art == null && lo.has("url")) {
                    String mp = mavenPath(name);
                    if (mp != null) {
                        String base = lo.get("url").getAsString();
                        if (!base.endsWith("/")) base += "/";
                        art = new Download(
                                mp,
                                base + mp,
                                lo.has("sha1") ? lo.get("sha1").getAsString() : null,
                                lo.has("size") ? lo.get("size").getAsLong() : -1);
                    }
                }

                Map<String, String> rules = new HashMap<>();
                if (lo.has("rules")) {
                    for (JsonElement r : lo.getAsJsonArray("rules")) {
                        JsonObject ro = r.getAsJsonObject();
                        String action = ro.get("action").getAsString();
                        String os = ro.has("os") && ro.getAsJsonObject("os").has("name")
                                ? ro.getAsJsonObject("os").get("name").getAsString() : "any";
                        rules.put(os, action);
                    }
                }
                libs.add(new Library(name, art, rules));
            }
        }

        List<String> gameArgs = new ArrayList<>();
        List<String> jvmArgs = new ArrayList<>();
        if (o.has("arguments")) {
            JsonObject args = o.getAsJsonObject("arguments");
            if (args.has("game")) {
                for (JsonElement e : args.getAsJsonArray("game")) {
                    addArg(gameArgs, e);
                }
            }
            if (args.has("jvm")) {
                for (JsonElement e : args.getAsJsonArray("jvm")) {
                    addArg(jvmArgs, e);
                }
            }
        }
        if (o.has("minecraftArguments")) {
            String legacy = o.get("minecraftArguments").getAsString();
            for (String s : legacy.split(" ")) {
                if (!s.isEmpty()) gameArgs.add(s);
            }
        }

        return new VersionJson(id, inheritsFrom, jarVersionId, mainClass,
                assetIndexUrl, assetIndexSha1, assetIndexId, client, lc, libs,
                gameArgs, jvmArgs);
    }

    private static void addArg(List<String> out, JsonElement e) {
        if (e.isJsonPrimitive()) {
            out.add(e.getAsString());
            return;
        }
        if (!e.isJsonObject()) return;
        JsonObject obj = e.getAsJsonObject();
        if (obj.has("rules")) {
            for (JsonElement r : obj.getAsJsonArray("rules")) {
                JsonObject rule = r.getAsJsonObject();
                if (rule.has("features")) return; // пропускаем аргументы, завязанные на фичи
                if (rule.has("os")) {
                    String osName = rule.getAsJsonObject("os").has("name")
                            ? rule.getAsJsonObject("os").get("name").getAsString() : null;
                    if (osName != null && !osName.equals("windows")) return;
                }
            }
        }
        if (obj.has("value")) {
            JsonElement v = obj.get("value");
            if (v.isJsonArray()) {
                for (JsonElement ve : v.getAsJsonArray()) {
                    if (ve.isJsonPrimitive()) out.add(ve.getAsString());
                }
            } else if (v.isJsonPrimitive()) {
                out.add(v.getAsString());
            }
        }
    }
}