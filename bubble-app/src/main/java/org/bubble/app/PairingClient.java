package org.bubble.app;

import android.net.ssl.SSLSockets;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Principal;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;

/**
 * The one piece to implement by hand: the adb wireless-debugging pairing handshake (SPAKE2 +
 * TLS 1.3), bit-compatible with AOSP's adb. Full specification, with the exact source files and
 * constants, in {@code docs/reverse-engineering/pairing-protocol.md}.
 *
 * <p>Everything else in the app (TLS connect/exec in {@link AdbChannel}, the engine, the UI, the
 * firewall, boot restore) is written and does not touch this. The implementor fills only
 * {@link #pair}.
 */
interface PairingClient {

    /**
     * Pair with the daemon so {@code ourCertificate}'s key becomes authorized for later TLS
     * connections.
     *
     * @param host        pairing host, from the "Pair device with pairing code" screen (usually 127.0.0.1)
     * @param port        pairing port from that screen (NOT the connect port; it changes each time)
     * @param pairingCode the 6-digit code the phone shows
     * @param ourKey      our RSA-2048 private key (kept wrapped in the Android keystore; see BubbleKey)
     * @param ourCertificate our self-signed X.509 certificate for that key
     * @return true if the daemon accepted the pairing
     */
    default boolean pair(String host, int port, String pairingCode,
                         PrivateKey ourKey, Certificate ourCertificate) throws Exception {
        // Blocking operation: the caller must use a worker thread.
        if (host == null || host.isEmpty() || port < 1 || port > 65535
                || pairingCode == null || !pairingCode.matches("[0-9]{6}")) {
            throw new IllegalArgumentException("Expected a host, pairing port and six ASCII digits");
        }
        if (ourKey == null || !"RSA".equals(ourKey.getAlgorithm())
                || !(ourCertificate instanceof X509Certificate)
                || !(ourCertificate.getPublicKey() instanceof RSAPublicKey)) {
            throw new IllegalArgumentException("Expected an RSA key and X.509 certificate");
        }
        X509Certificate certificate = (X509Certificate) ourCertificate;
        RSAPublicKey publicKey = (RSAPublicKey) certificate.getPublicKey();
        if (publicKey.getModulus().bitLength() != 2048
                || !publicKey.getModulus().testBit(0)
                || !(publicKey.getPublicExponent().equals(BigInteger.valueOf(65537))
                || publicKey.getPublicExponent().equals(BigInteger.valueOf(3)))) {
            throw new IllegalArgumentException("Expected an adb-compatible RSA-2048 public key");
        }

        // Everything is local to this invocation: no shared counters, keys or TLS trust policy.
        class Protocol {
            final BigInteger p = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19));
            final BigInteger order = BigInteger.ONE.shiftLeft(252)
                    .add(new BigInteger("27742317777372353535851937790883648493"));
            final BigInteger d = BigInteger.valueOf(-121665)
                    .multiply(BigInteger.valueOf(121666).modInverse(p)).mod(p);
            final BigInteger sqrtMinusOne = BigInteger.TWO.modPow(p.subtract(BigInteger.ONE)
                    .shiftRight(2), p);
            final ArrayList<byte[]> secrets = new ArrayList<>();

            byte[] secret(byte[] value) {
                secrets.add(value);
                return value;
            }

            byte[] ascii(String value) {
                return value.getBytes(StandardCharsets.US_ASCII);
            }

            BigInteger fromLittleEndian(byte[] bytes) {
                byte[] reversed = bytes.clone();
                for (int i = 0; i < reversed.length / 2; i++) {
                    byte temp = reversed[i];
                    reversed[i] = reversed[reversed.length - 1 - i];
                    reversed[reversed.length - 1 - i] = temp;
                }
                BigInteger value = new BigInteger(1, reversed);
                Arrays.fill(reversed, (byte) 0);
                return value;
            }

            byte[] littleEndian(BigInteger value, int length) {
                if (value.signum() < 0 || value.bitLength() > length * 8) {
                    throw new IllegalArgumentException("Integer does not fit the wire encoding");
                }
                byte[] bigEndian = value.toByteArray();
                byte[] result = new byte[length];
                for (int i = 0; i < Math.min(length, bigEndian.length); i++) {
                    result[i] = bigEndian[bigEndian.length - 1 - i];
                }
                Arrays.fill(bigEndian, (byte) 0);
                return result;
            }

            byte[] hex(String text) {
                byte[] result = new byte[text.length() / 2];
                for (int i = 0; i < result.length; i++) {
                    result[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
                }
                return result;
            }

            // Extended Edwards coordinates (X:Y:Z:T), x=X/Z, y=Y/Z, T=XY/Z.
            // These formulas implement Edwards25519, NOT X25519 or Ed25519 signing.
            // IMPORTANT: BigInteger arithmetic is variable-time and cannot be erased.
            // This dependency-free port does not provide BoringSSL's side-channel guarantees.
            BigInteger[] point(BigInteger x, BigInteger y) {
                return new BigInteger[]{x, y, BigInteger.ONE, x.multiply(y).mod(p)};
            }

            BigInteger[] decode(byte[] encoded) throws IOException {
                byte[] yBytes = encoded.clone();
                boolean sign = (yBytes[31] & 128) != 0;
                yBytes[31] &= 127;
                BigInteger y = fromLittleEndian(yBytes);
                // BoringSSL's field decoder reduces the encoded field element modulo p.
                y = y.mod(p);
                BigInteger yy = y.multiply(y).mod(p);
                BigInteger denominator = d.multiply(yy).add(BigInteger.ONE).mod(p);
                if (denominator.signum() == 0) throw new IOException("Invalid SPAKE2 point");
                BigInteger xx = yy.subtract(BigInteger.ONE).multiply(denominator.modInverse(p)).mod(p);
                BigInteger x = xx.modPow(p.add(BigInteger.valueOf(3)).shiftRight(3), p);
                if (!x.multiply(x).mod(p).equals(xx)) x = x.multiply(sqrtMinusOne).mod(p);
                if (!x.multiply(x).mod(p).equals(xx)) throw new IOException("Invalid SPAKE2 point");
                if (x.testBit(0) != sign) x = x.negate().mod(p);
                return point(x, y);
            }

            BigInteger[] add(BigInteger[] a, BigInteger[] b) {
                BigInteger aa = a[1].subtract(a[0]).multiply(b[1].subtract(b[0])).mod(p);
                BigInteger bb = a[1].add(a[0]).multiply(b[1].add(b[0])).mod(p);
                BigInteger cc = d.shiftLeft(1).multiply(a[3]).multiply(b[3]).mod(p);
                BigInteger dd = a[2].multiply(b[2]).shiftLeft(1).mod(p);
                BigInteger e = bb.subtract(aa), f = dd.subtract(cc);
                BigInteger g = dd.add(cc), h = bb.add(aa);
                return new BigInteger[]{e.multiply(f).mod(p), g.multiply(h).mod(p),
                        f.multiply(g).mod(p), e.multiply(h).mod(p)};
            }

            BigInteger[] negate(BigInteger[] a) {
                return new BigInteger[]{a[0].negate().mod(p), a[1], a[2], a[3].negate().mod(p)};
            }

            BigInteger[] multiply(BigInteger[] a, BigInteger scalar) {
                BigInteger[] result = point(BigInteger.ZERO, BigInteger.ONE);
                // Do not reduce modulo order here: M and N have cofactor components.
                for (int bit = 255; bit >= 0; bit--) {
                    result = add(result, result);
                    if (scalar.testBit(bit)) result = add(result, a);
                }
                return result;
            }

            byte[] encode(BigInteger[] a) {
                BigInteger inverse = a[2].modInverse(p);
                byte[] encoded = littleEndian(a[1].multiply(inverse).mod(p), 32);
                if (a[0].multiply(inverse).mod(p).testBit(0)) encoded[31] |= (byte) 128;
                return encoded;
            }

            void writePacket(DataOutputStream output, int type, byte[] body) throws IOException {
                output.writeByte(1);
                output.writeByte(type);
                output.writeInt(body.length); // Network byte order, unlike SPAKE2 length prefixes.
                output.write(body);
                output.flush();
            }

            byte[] readPacket(DataInputStream input, int type, int expectedLength) throws IOException {
                int version = input.readUnsignedByte();
                int actualType = input.readUnsignedByte();
                int length = input.readInt();
                if (version != 1 || actualType != type || length <= 0 || length > 16384
                        || length != expectedLength) {
                    throw new IOException("Invalid pairing packet header for type " + type);
                }
                byte[] body = new byte[length];
                input.readFully(body);
                return body;
            }
        }
        Protocol protocol = new Protocol();
        SecureRandom random = new SecureRandom();
        // A dedicated key manager also works with an RSA PrivateKey backed by a provider;
        // it never exports the private key. TLS proves possession of the matching key.
        X509ExtendedKeyManager keyManager = new X509ExtendedKeyManager() {
            @Override public String[] getClientAliases(String type, Principal[] issuers) {
                return "RSA".equals(type) ? new String[]{"pairing"} : null;
            }
            @Override public String chooseClientAlias(String[] types, Principal[] issuers, Socket socket) {
                if (types != null) for (String type : types) {
                    if ("RSA".equals(type)) return "pairing";
                }
                return null;
            }
            @Override public String chooseEngineClientAlias(String[] types, Principal[] issuers, SSLEngine engine) {
                return chooseClientAlias(types, issuers, null);
            }
            @Override public String[] getServerAliases(String type, Principal[] issuers) { return null; }
            @Override public String chooseServerAlias(String type, Principal[] issuers, Socket socket) {
                return null;
            }
            @Override public X509Certificate[] getCertificateChain(String alias) {
                return "pairing".equals(alias) ? new X509Certificate[]{certificate} : null;
            }
            @Override public PrivateKey getPrivateKey(String alias) {
                return "pairing".equals(alias) ? ourKey : null;
            }
        };
        // The pairing code authenticates this session. This trust manager must never be
        // installed as an application-wide default or reused for ordinary TLS connections.
        X509TrustManager trustManager = new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };
        SSLContext context = SSLContext.getInstance("TLSv1.3");
        context.init(new KeyManager[]{keyManager}, new TrustManager[]{trustManager}, random);
        try (Socket transport = new Socket()) {
            transport.connect(new InetSocketAddress(host, port), 15000);
            transport.setSoTimeout(15000);
            try (SSLSocket socket = (SSLSocket) context.getSocketFactory()
                    .createSocket(transport, host, port, true)) {
                socket.setUseClientMode(true);
                socket.setEnabledProtocols(new String[]{"TLSv1.3"});
                socket.setSoTimeout(15000);
                socket.startHandshake();
                // AOSP tls_connection.cpp passes sizeof(kExportedKeyLabel): TEN bytes.
                // Android's public exporter is available since API 31 (minSdk here is 34).
                byte[] exported = SSLSockets.exportKeyingMaterial(socket, "adb-label\0", null, 64);
                if (exported == null || exported.length != 64) {
                    throw new IOException("TLS exporter did not return 64 bytes");
                }
                protocol.secret(exported);
                byte[] password = protocol.secret(new byte[70]);
                byte[] code = protocol.secret(protocol.ascii(pairingCode));
                System.arraycopy(code, 0, password, 0, 6);
                System.arraycopy(exported, 0, password, 6, 64);
                MessageDigest sha512 = MessageDigest.getInstance("SHA-512");
                byte[] passwordHash = protocol.secret(sha512.digest(password));
                byte[] entropy = protocol.secret(new byte[64]);
                random.nextBytes(entropy);
                BigInteger privateScalar = protocol.fromLittleEndian(entropy).mod(protocol.order).shiftLeft(3);
                BigInteger passwordScalar = protocol.fromLittleEndian(passwordHash).mod(protocol.order);
                // BoringSSL's historical compatibility fix: add l, 2l and 4l when the
                // corresponding low bit is set. Do NOT multiply this scalar by eight.
                for (int bit = 0; bit < 3; bit++) {
                    if (passwordScalar.testBit(bit)) passwordScalar = passwordScalar.add(protocol.order.shiftLeft(bit));
                }
                BigInteger[] base = protocol.decode(protocol.hex(
                        "5866666666666666666666666666666666666666666666666666666666666666"));
                BigInteger[] m = protocol.decode(protocol.hex(
                        "5ada7e4bf6ddd9adb6626d32131c6b5c51a1e347a3478f53cfcf441b88eed12e"));
                BigInteger[] n = protocol.decode(protocol.hex(
                        "10e3df0ae37d8e7a99b5fe74b44672103dbddcbd06af680d71329a11693bc778"));
                byte[] ourMessage = protocol.encode(protocol.add(protocol.multiply(base, privateScalar),
                        protocol.multiply(m, passwordScalar)));
                DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                DataInputStream input = new DataInputStream(socket.getInputStream());
                protocol.writePacket(output, 0, ourMessage);
                byte[] theirMessage = protocol.readPacket(input, 0, 32);
                BigInteger[] unmasked = protocol.add(protocol.decode(theirMessage),
                        protocol.negate(protocol.multiply(n, passwordScalar)));
                byte[] shared = protocol.secret(protocol.encode(protocol.multiply(unmasked, privateScalar)));
                // Alice's transcript order, including BOTH terminating NUL bytes.
                byte[][] transcript = {protocol.ascii("adb pair client\0"),
                        protocol.ascii("adb pair server\0"), ourMessage, theirMessage, shared, passwordHash};
                for (byte[] item : transcript) {
                    sha512.update(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(item.length).array());
                    sha512.update(item);
                }
                byte[] keyMaterial = protocol.secret(sha512.digest());
                // RFC 5869: absent salt = HashLen zero bytes; a single expand block
                // suffices for 16 bytes. The info string does NOT include a NUL.
                Mac hmac = Mac.getInstance("HmacSHA256");
                hmac.init(new SecretKeySpec(new byte[32], "HmacSHA256"));
                byte[] prk = protocol.secret(hmac.doFinal(keyMaterial));
                hmac.init(new SecretKeySpec(prk, "HmacSHA256"));
                hmac.update(protocol.ascii("adb pairing_auth aes-128-gcm key"));
                byte[] block = protocol.secret(hmac.doFinal(new byte[]{1}));
                byte[] aesKey = protocol.secret(Arrays.copyOf(block, 16));

                // android_pubkey_encode: 64 words, -n^-1 mod 2^32, n, R^2 mod n, e.
                BigInteger modulus = publicKey.getModulus();
                BigInteger wordModulus = BigInteger.ONE.shiftLeft(32);
                ByteBuffer adbKey = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
                adbKey.putInt(64);
                adbKey.putInt(modulus.modInverse(wordModulus).negate().mod(wordModulus).intValue());
                adbKey.put(protocol.littleEndian(modulus, 256));
                adbKey.put(protocol.littleEndian(BigInteger.ONE.shiftLeft(4096).mod(modulus), 256));
                adbKey.putInt(publicKey.getPublicExponent().intValue());
                byte[] publicText = protocol.ascii(Base64.getEncoder().encodeToString(adbKey.array())
                        + " bubble@device");
                byte[] peerInfo = new byte[8192]; // type 0; NUL terminator and padding remain zero.
                System.arraycopy(publicText, 0, peerInfo, 1, publicText.length);
                // Exactly one message per direction: each independent sequence starts at 0.
                // Never reuse this key/session to encrypt another message with this nonce.
                SecretKeySpec key = new SecretKeySpec(aesKey, "AES");
                Cipher encrypt = Cipher.getInstance("AES/GCM/NoPadding");
                encrypt.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, new byte[12]));
                protocol.writePacket(output, 1, encrypt.doFinal(peerInfo));
                byte[] encrypted = protocol.readPacket(input, 1, 8192 + 16);
                Cipher decrypt = Cipher.getInstance("AES/GCM/NoPadding");
                decrypt.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, new byte[12]));
                byte[] response;
                try {
                    response = decrypt.doFinal(encrypted);
                } catch (AEADBadTagException wrongCodeOrSession) {
                    return false;
                }
                // adbd returns ADB_DEVICE_GUID (1), not another RSA public key (0).
                if (response.length != 8192 || response[0] != 1 || response[1] == 0) {
                    throw new IOException("Invalid daemon PeerInfo");
                }
                int terminator = 1;
                while (terminator < response.length && response[terminator] != 0) terminator++;
                if (terminator == response.length) throw new IOException("Unterminated daemon GUID");
                // The protocol has no final acknowledgement of durable adb_keys storage.
                // A later TLS connection and the phone's paired-device list verify persistence.
                return true;
            }
        } finally {
            for (byte[] secret : protocol.secrets) Arrays.fill(secret, (byte) 0);
        }
    }
}
