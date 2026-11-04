package launcher.mc;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * Minecraft Server List Ping (protocol 1.7+).
 * Отправляет handshake → status request, читает JSON, потом ping/pong для измерения RTT.
 */
public final class ServerPinger {

    public record Status(
            boolean online,
            String motd,
            int playersOnline,
            int playersMax,
            long pingMs,
            String version,
            String error
    ) {}

    private ServerPinger() {}

    public static Status ping(String host, int port, int timeoutMs) {
        long t0 = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // 1) Handshake (packet id 0x00)
            ByteArrayOutputStream handshake = new ByteArrayOutputStream();
            DataOutputStream hs = new DataOutputStream(handshake);
            writeVarInt(hs, 0x00);             // packet id
            writeVarInt(hs, 767);              // protocol (1.21)
            writeString(hs, host);             // server address
            hs.writeShort(port);               // port
            writeVarInt(hs, 0x01);             // next state = status

            writeVarInt(out, handshake.size());
            out.write(handshake.toByteArray());

            // 2) Status request (packet id 0x00, без данных)
            writeVarInt(out, 1);
            writeVarInt(out, 0x00);
            out.flush();

            // 3) Response: varint length, varint packetId, string json
            int len = readVarInt(in);
            byte[] buf = new byte[len];
            in.readFully(buf);

            DataInputStream pkt = new DataInputStream(new ByteArrayInputStream(buf));
            int pktId = readVarInt(pkt);
            if (pktId != 0x00) {
                return new Status(false, null, 0, 0, 0, null, "unexpected packet id " + pktId);
            }
            String json = readString(pkt, 32767);

            // 4) Ping (0x01) — random long
            long pingPayload = System.currentTimeMillis();
            ByteArrayOutputStream pingOut = new ByteArrayOutputStream();
            DataOutputStream p = new DataOutputStream(pingOut);
            writeVarInt(p, 0x01);
            p.writeLong(pingPayload);
            writeVarInt(out, pingOut.size());
            out.write(pingOut.toByteArray());
            out.flush();

            // 5) Pong (0x01) — read and measure
            int pongLen = readVarInt(in);
            byte[] pongBuf = new byte[pongLen];
            in.readFully(pongBuf);
            long pingMs = System.currentTimeMillis() - t0;

            // 6) parse JSON
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            String versionName = null;
            if (root.has("version") && root.getAsJsonObject("version").has("name")) {
                versionName = root.getAsJsonObject("version").get("name").getAsString();
            }

            int online = 0, max = 0;
            if (root.has("players") && root.getAsJsonObject("players").isJsonObject()) {
                JsonObject players = root.getAsJsonObject("players");
                if (players.has("online")) online = players.get("online").getAsInt();
                if (players.has("max"))    max    = players.get("max").getAsInt();
            }

            String motd = parseMotd(root);

            return new Status(true, motd, online, max, pingMs, versionName, null);
        } catch (Exception e) {
            long t = System.currentTimeMillis() - t0;
            return new Status(false, null, 0, 0, t, null, e.getMessage());
        }
    }

    // ============================================================
    // MOTD parsing (JSON: string | { text, extra[] } | array of components)
    // ============================================================
    private static String parseMotd(JsonObject root) {
        if (!root.has("description")) return "";
        JsonElement d = root.get("description");
        StringBuilder sb = new StringBuilder();
        flatten(d, sb);
        return sb.toString();
    }

    private static void flatten(JsonElement el, StringBuilder sb) {
        if (el == null || el.isJsonNull()) return;
        if (el.isJsonPrimitive()) {
            sb.append(el.getAsString());
            return;
        }
        if (el.isJsonArray()) {
            for (JsonElement e : el.getAsJsonArray()) flatten(e, sb);
            return;
        }
        if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("text")) sb.append(o.get("text").getAsString());
            if (o.has("extra")) flatten(o.get("extra"), sb);
        }
    }

    // ============================================================
    // varint / string helpers
    // ============================================================
    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.writeByte(value);
                return;
            }
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0, position = 0;
        while (true) {
            int b = in.readByte();
            value |= (b & 0x7F) << position;
            if ((b & 0x80) == 0) break;
            position += 7;
            if (position >= 32) throw new IOException("varint too big");
        }
        return value;
    }

    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, b.length);
        out.write(b);
    }

    private static String readString(DataInputStream in, int max) throws IOException {
        int len = readVarInt(in);
        if (len > max * 4) throw new IOException("string too long");
        byte[] b = new byte[len];
        in.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }
}