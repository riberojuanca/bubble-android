package org.bubble.app;

import android.content.Context;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;

/**
 * Bubble's adb client: connects to this phone's own wireless-debugging TLS daemon and runs the
 * engine with the shell UID. No third-party library.
 *
 * <p>Flow (reverse-engineered from AOSP {@code adb.cpp}): TCP connect to the {@code
 * _adb-tls-connect._tcp} endpoint (found with {@link NsdManager}); the daemon sends {@code A_STLS}
 * first, we answer {@code A_STLS} and do a mutual TLS 1.3 handshake presenting our paired key; then
 * the normal adb stream protocol (CNXN, OPEN/OKAY/WRTE/CLSE). Commands run as one {@code exec:}
 * stream each; the engine's own {@code 2>&1} merges stderr.
 *
 * <p>Pairing (authorizing our key the first time) is {@link PairingClient}; this class is used only
 * after the key is paired. Single stream at a time, driven synchronously (the engine issues one
 * command at a time), so no background reader is needed.
 */
final class AdbChannelImpl implements AdbChannel {

    // adb protocol constants (AOSP adb.h).
    private static final int A_CNXN = 0x4e584e43;
    private static final int A_OPEN = 0x4e45504f;
    private static final int A_OKAY = 0x59414b4f;
    private static final int A_CLSE = 0x45534c43;
    private static final int A_WRTE = 0x45545257;
    private static final int A_STLS = 0x534c5453;
    private static final int A_VERSION = 0x01000001;       // SKIP_CHECKSUM: data_check may be 0
    private static final int A_STLS_VERSION = 0x01000000;
    private static final int MAX_PAYLOAD = 256 * 1024;

    private final Context context;
    private final BubbleKey key;

    private Socket transport;
    private SSLSocket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private int nextLocalId = 1;

    AdbChannelImpl(Context context, BubbleKey key) {
        this.context = context.getApplicationContext();
        this.key = key;
    }

    @Override public synchronized boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override public synchronized boolean connect(long timeoutMs) {
        if (isConnected()) return true;
        long deadline = System.currentTimeMillis() + timeoutMs;
        try {
            Progress.step(context, R.string.step_find_port);
            InetSocketAddress endpoint = Mdns.resolve(context, Mdns.CONNECT, timeoutMs);
            if (endpoint == null) return false;
            int left = (int) Math.max(1000, deadline - System.currentTimeMillis());
            transport = new Socket();
            transport.connect(endpoint, left);
            transport.setSoTimeout(15000);

            // adb handshake (AOSP adb.cpp handle_new_connection): the client sends A_CNXN in
            // PLAINTEXT first; a wireless daemon answers A_STLS to ask for TLS; we answer A_STLS,
            // upgrade to TLS presenting our paired key, and read the daemon's A_CNXN inside TLS.
            DataInputStream rawIn = new DataInputStream(transport.getInputStream());
            DataOutputStream rawOut = new DataOutputStream(transport.getOutputStream());
            byte[] banner = "host::features=shell_v2,cmd\0".getBytes(StandardCharsets.UTF_8);
            writeMessage(rawOut, A_CNXN, A_VERSION, MAX_PAYLOAD, banner);
            Message reply = readMessage(rawIn);
            if (reply.command != A_STLS) throw new IOException("expected A_STLS, got " + hex(reply.command));
            writeMessage(rawOut, A_STLS, A_STLS_VERSION, 0, new byte[0]);

            Progress.step(context, R.string.step_connect);
            socket = (SSLSocket) tlsContext().getSocketFactory()
                    .createSocket(transport, endpoint.getHostString(), endpoint.getPort(), true);
            socket.setUseClientMode(true);
            socket.setEnabledProtocols(new String[]{"TLSv1.3"});
            socket.setSoTimeout(15000);
            socket.startHandshake();
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            Message cnxn = readMessage(in);
            if (cnxn.command != A_CNXN) throw new IOException("expected A_CNXN, got " + hex(cnxn.command));
            return true;
        } catch (Exception e) {
            closeQuietly();
            return false;
        }
    }

    @Override public synchronized void disconnect() {
        closeQuietly();
    }

    @Override public synchronized String exec(String command) throws Exception {
        return runStream("exec:" + command);
    }

    @Override public synchronized String execLong(String command) throws Exception {
        if (!isConnected()) throw new IOException("not connected");
        // Installing (verification, dexopt) can keep the daemon silent far longer than a command.
        socket.setSoTimeout(180000);
        try {
            return runStream("exec:" + command);
        } finally {
            if (socket != null) socket.setSoTimeout(15000);
        }
    }

    @Override public synchronized void shellDetached(String command) throws Exception {
        // The "shell:" command backgrounds its setsid child and returns, closing the stream; the
        // child survives (unlike under "exec:").
        runStream("shell:" + command);
    }

    @Override public synchronized void execTo(String command, java.io.OutputStream sink) throws Exception {
        if (!isConnected()) throw new IOException("not connected");
        socket.setSoTimeout(180000);
        try {
            runStream("exec:" + command, sink);
        } finally {
            if (socket != null) socket.setSoTimeout(15000);
        }
    }

    private String runStream(String service) throws Exception {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        runStream(service, output);
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private void runStream(String service, java.io.OutputStream output) throws Exception {
        if (!isConnected()) throw new IOException("not connected");
        int localId = nextLocalId++;
        writeMessage(out, A_OPEN, localId, 0, (service + "\0").getBytes(StandardCharsets.UTF_8));

        while (true) {
            Message m = readMessage(in);
            if (m.arg1 != 0 && m.arg1 != localId) continue; // not our stream
            switch (m.command) {
                case A_WRTE:
                    output.write(m.payload);
                    writeMessage(out, A_OKAY, localId, m.arg0, new byte[0]);
                    break;
                case A_CLSE:
                    writeMessage(out, A_CLSE, localId, m.arg0, new byte[0]);
                    return;
                default:
                    break;
            }
        }
    }

    @Override public synchronized String execWithInput(String command, java.io.InputStream data) throws Exception {
        if (!isConnected()) throw new IOException("not connected");
        int localId = nextLocalId++;
        writeMessage(out, A_OPEN, localId, 0, ("exec:" + command + "\0").getBytes(StandardCharsets.UTF_8));
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        int remoteId = 0;
        while (remoteId == 0) {
            Message m = readMessage(in);
            if (m.arg1 != localId) continue;
            if (m.command == A_OKAY) remoteId = m.arg0;
            else if (m.command == A_CLSE) throw new IOException("stream refused: " + command);
        }
        // Installing (verification, dexopt) can keep the daemon silent far longer than a command.
        socket.setSoTimeout(180000);
        try {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = data.read(buf)) > 0) {
                writeMessage(out, A_WRTE, localId, remoteId, java.util.Arrays.copyOf(buf, n));
                // One write in flight: wait for its OKAY, keeping any output that arrives meanwhile.
                boolean acked = false;
                while (!acked) {
                    Message m = readMessage(in);
                    if (m.arg1 != localId) continue;
                    if (m.command == A_OKAY) {
                        acked = true;
                    } else if (m.command == A_WRTE) {
                        output.write(m.payload);
                        writeMessage(out, A_OKAY, localId, m.arg0, new byte[0]);
                    } else if (m.command == A_CLSE) {
                        // The command ended before reading everything (e.g. it failed).
                        writeMessage(out, A_CLSE, localId, m.arg0, new byte[0]);
                        return output.toString(StandardCharsets.UTF_8.name());
                    }
                }
            }
            while (true) {
                Message m = readMessage(in);
                if (m.arg1 != localId) continue;
                if (m.command == A_WRTE) {
                    output.write(m.payload);
                    writeMessage(out, A_OKAY, localId, m.arg0, new byte[0]);
                } else if (m.command == A_CLSE) {
                    writeMessage(out, A_CLSE, localId, m.arg0, new byte[0]);
                    return output.toString(StandardCharsets.UTF_8.name());
                }
            }
        } finally {
            if (socket != null) socket.setSoTimeout(15000);
        }
    }

    @Override public synchronized void writeFile(String path, byte[] data) throws Exception {
        String b64 = android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);
        // echo is a shell builtin, so the base64 (no quotes) goes through as one argument.
        exec("echo " + b64 + " | base64 -d > " + path);
    }

    // ---- TLS ---------------------------------------------------------------

    private SSLContext tlsContext() throws Exception {
        X509ExtendedKeyManager keyManager = new X509ExtendedKeyManager() {
            @Override public String[] getClientAliases(String t, Principal[] i) { return new String[]{"bubble"}; }
            @Override public String chooseClientAlias(String[] t, Principal[] i, Socket s) { return "bubble"; }
            @Override public String chooseEngineClientAlias(String[] t, Principal[] i, javax.net.ssl.SSLEngine e) { return "bubble"; }
            @Override public String[] getServerAliases(String t, Principal[] i) { return null; }
            @Override public String chooseServerAlias(String t, Principal[] i, Socket s) { return null; }
            @Override public X509Certificate[] getCertificateChain(String a) {
                return new X509Certificate[]{(X509Certificate) key.certificate()};
            }
            @Override public PrivateKey getPrivateKey(String a) { return key.privateKey(); }
        };
        // The daemon authenticates us by our paired key, and we are on loopback, so the daemon's
        // own certificate is accepted without a CA (same as the adb host client).
        X509TrustManager trustManager = new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] c, String t) {}
            @Override public void checkServerTrusted(X509Certificate[] c, String t) {}
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };
        SSLContext ctx = SSLContext.getInstance("TLSv1.3");
        ctx.init(new KeyManager[]{keyManager}, new TrustManager[]{trustManager}, null);
        return ctx;
    }

    // ---- adb message framing (24-byte header, little-endian) ---------------

    private static final class Message {
        int command, arg0, arg1;
        byte[] payload;
    }

    private void writeMessage(DataOutputStream o, int command, int arg0, int arg1, byte[] payload)
            throws IOException {
        ByteBuffer header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(command);
        header.putInt(arg0);
        header.putInt(arg1);
        header.putInt(payload.length);
        header.putInt(checksum(payload));  // ignored by the daemon at A_VERSION, sent for older ones
        header.putInt(command ^ 0xffffffff);
        o.write(header.array());
        if (payload.length > 0) o.write(payload);
        o.flush();
    }

    private Message readMessage(DataInputStream i) throws IOException {
        byte[] head = new byte[24];
        i.readFully(head);
        ByteBuffer b = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        Message m = new Message();
        m.command = b.getInt();
        m.arg0 = b.getInt();
        m.arg1 = b.getInt();
        int length = b.getInt();
        b.getInt(); // data_check, not validated
        int magic = b.getInt();
        if ((m.command ^ 0xffffffff) != magic) throw new IOException("bad adb magic");
        if (length < 0 || length > MAX_PAYLOAD) throw new IOException("adb payload too large: " + length);
        m.payload = new byte[length];
        if (length > 0) i.readFully(m.payload);
        return m;
    }

    /** adb's "checksum" is the unsigned sum of the payload bytes (AOSP calculate_apacket_checksum). */
    private static int checksum(byte[] payload) {
        int sum = 0;
        for (byte value : payload) sum += (value & 0xff);
        return sum;
    }

    private static String hex(int value) { return String.format("0x%08x", value); }

    private void closeQuietly() {
        for (java.io.Closeable c : new java.io.Closeable[]{socket, transport}) {
            try {
                if (c != null) c.close();
            } catch (IOException ignored) {
            }
        }
        socket = null;
        transport = null;
        in = null;
        out = null;
    }
}
