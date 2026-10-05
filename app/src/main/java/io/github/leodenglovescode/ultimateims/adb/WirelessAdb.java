package io.github.leodenglovescode.ultimateims.adb;

import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.SSLSocket;
import org.conscrypt.Conscrypt;

/** 自写 ADB TLS/配对帧；只允许本机连接，不暴露用户输入的命令执行。 */
public final class WirelessAdb {
    private final AdbIdentity identity;
    public WirelessAdb() throws Exception { identity = new AdbIdentity(); }
    private static Socket connect(int port) throws IOException {
        if (port < 1024 || port > 65535) throw new IllegalArgumentException("Invalid local debugging port");
        Socket socket = new Socket(); socket.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127,0,0,1}), port), 5000); socket.setSoTimeout(12000); return socket;
    }
    public String pair(int pairingPort, byte[] code) throws Exception {
        if (code.length != 6) throw new IllegalArgumentException("Six digit pairing code required");
        for (byte b : code) if (b < '0' || b > '9') throw new IllegalArgumentException("Invalid pairing code");
        byte[] password = null;
        try (Socket raw = connect(pairingPort); SSLSocket tls = identity.tls(raw, pairingPort)) {
            byte[] exported = Conscrypt.exportKeyingMaterial(tls, "adb-label", null, 64);
            password = ByteBuffer.allocate(code.length + exported.length).put(code).put(exported).array();
            try (PairingCrypto crypto = new PairingCrypto(password)) {
                DataInputStream in = new DataInputStream(tls.getInputStream()); DataOutputStream out = new DataOutputStream(tls.getOutputStream());
                pairingWrite(out, 0, crypto.outbound()); crypto.accept(pairingRead(in, 0));
                byte[] peer = new byte[8192]; byte[] hostKey = identity.publicAdbKey();
                if (hostKey.length >= peer.length) throw new IllegalStateException("Host key too long");
                System.arraycopy(hostKey, 0, peer, 1, hostKey.length);
                pairingWrite(out, 1, crypto.encrypt(peer)); byte[] response = crypto.decrypt(pairingRead(in, 1));
                if (response.length != 8192 || response[0] != 1) throw new SecurityException("Invalid device pairing identity");
                int end = 1; while (end < response.length && response[end] != 0) end++;
                String guid = new String(response, 1, end - 1, StandardCharsets.US_ASCII);
                if (!guid.matches("[A-Za-z0-9._-]{1,128}")) throw new SecurityException("Invalid device GUID"); return guid;
            }
        } finally { java.util.Arrays.fill(code, (byte) 0); if (password != null) java.util.Arrays.fill(password, (byte) 0); }
    }
    private static void pairingWrite(DataOutputStream out, int type, byte[] data) throws IOException {
        out.writeByte(1); out.writeByte(type); out.writeInt(data.length); out.write(data); out.flush();
    }
    private static byte[] pairingRead(DataInputStream in, int expected) throws IOException {
        if (in.readUnsignedByte() != 1 || in.readUnsignedByte() != expected) throw new IOException("Pairing protocol mismatch");
        int size = in.readInt(); if (size < 1 || size > 16384) throw new IOException("Pairing payload size invalid");
        byte[] data = new byte[size]; in.readFully(data); return data;
    }
    private record Packet(int command, int a, int b, byte[] data) { }
    private static int command(String name) { return ByteBuffer.wrap(name.getBytes(StandardCharsets.US_ASCII)).order(ByteOrder.LITTLE_ENDIAN).getInt(); }
    private static void send(OutputStream out, String name, int a, int b, byte[] data) throws IOException {
        int cmd = command(name), checksum = 0; for (byte value : data) checksum += value & 255;
        out.write(ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).putInt(cmd).putInt(a).putInt(b).putInt(data.length).putInt(checksum).putInt(cmd ^ -1).array()); out.write(data); out.flush();
    }
    private static Packet receive(InputStream stream) throws IOException {
        DataInputStream input = new DataInputStream(stream); byte[] bytes = new byte[24]; input.readFully(bytes);
        ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int cmd = header.getInt(), a = header.getInt(), b = header.getInt(), size = header.getInt(), checksum = header.getInt(), magic = header.getInt();
        if (size < 0 || size > 1048576 || magic != (cmd ^ -1)) throw new IOException("Invalid ADB packet");
        byte[] data = new byte[size]; input.readFully(data);
        // ADB 1.0.1 可省略 checksum；旧校验字段非零时仍核验。
        if (checksum != 0) { int sum = 0; for (byte value : data) sum += value & 255; if (sum != checksum) throw new IOException("ADB checksum mismatch"); }
        return new Packet(cmd, a, b, data);
    }
    public void startBackend(int connectionPort, String fixedLaunchCommand) throws Exception {
        if (!fixedLaunchCommand.matches("CLASSPATH='/data/app/[A-Za-z0-9_./=+~-]+\' /system/bin/setsid /system/bin/app_process /system/bin --nice-name=ultimateims\\.backend io\\.github\\.leodenglovescode\\.ultimateims\\.backend\\.EmbeddedMain [0-9a-f-]{36} [0-9]{1,5} >/dev/null 2>&1 </dev/null &")) throw new SecurityException("Only fixed private-backend launch is allowed");
        try (Socket raw = connect(connectionPort)) {
            send(raw.getOutputStream(), "CNXN", 0x01000001, 1048576, "host::\0".getBytes(StandardCharsets.US_ASCII));
            Packet upgrade = receive(raw.getInputStream());
            if (upgrade.command != command("STLS")) throw new SecurityException("Wireless ADB TLS is required; legacy TCP ADB is refused");
            send(raw.getOutputStream(), "STLS", 0x01000000, 0, new byte[0]);
            try (SSLSocket tls = identity.tls(raw, connectionPort)) {
                Packet connected = receive(tls.getInputStream()); if (connected.command != command("CNXN")) throw new SecurityException("ADB authorization failed");
                send(tls.getOutputStream(), "OPEN", 1, 0, ("shell:" + fixedLaunchCommand + "\0").getBytes(StandardCharsets.UTF_8));
                int seen = 0;
                for (int n = 0; n < 128; n++) {
                    Packet packet = receive(tls.getInputStream());
                    if (packet.b != 1) throw new IOException("Unexpected ADB stream");
                    if (packet.command == command("WRTE")) { seen += packet.data.length; if (seen > 65536) throw new IOException("ADB output bound exceeded"); send(tls.getOutputStream(), "OKAY", 1, packet.a, new byte[0]); }
                    else if (packet.command == command("CLSE")) { send(tls.getOutputStream(), "CLSE", 1, packet.a, new byte[0]); return; }
                    else if (packet.command != command("OKAY")) throw new IOException("Unexpected ADB command");
                }
                throw new IOException("ADB stream did not close");
            }
        }
    }
}
