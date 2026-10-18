package org.bubble.app;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import javax.security.auth.x500.X500Principal;

/**
 * Bubble's adb identity: an RSA-2048 key that pairing authorizes and every later TLS connection
 * presents. It lives in the Android keystore, StrongBox-backed when the hardware has it, and is
 * <b>never exportable</b>.
 *
 * <p>This is possible because JSSE signs the TLS handshake through the key's provider; the raw
 * private bytes are never needed in the app. Both {@link PairingClient} and {@link AdbChannelImpl}
 * use the key only via a {@code KeyManager}, so a non-exportable keystore key works for both.
 *
 * <p>The keystore generates a self-signed certificate for the key, so no certificate library is
 * needed. adb only reads the public modulus and exponent from it; AndroidKeyStore RSA uses the
 * exponent 65537 that adb accepts.
 */
final class BubbleKey {

    private static final String ALIAS = "bubble-adb";
    private static final String KEYSTORE = "AndroidKeyStore";

    private final PrivateKey privateKey;
    private final X509Certificate certificate;

    private BubbleKey(PrivateKey privateKey, X509Certificate certificate) {
        this.privateKey = privateKey;
        this.certificate = certificate;
    }

    PrivateKey privateKey() { return privateKey; }

    Certificate certificate() { return certificate; }

    /** Load the identity, creating it on first use. */
    static BubbleKey load() throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            try {
                generate(true);
            } catch (Exception strongBoxOrUnsupported) {
                // StrongBox may be absent or reject raw-RSA / DIGEST_NONE; fall back to the TEE.
                ks.deleteEntry(ALIAS);
                generate(false);
            }
            ks.load(null);
        }
        PrivateKey key = (PrivateKey) ks.getKey(ALIAS, null);
        X509Certificate cert = (X509Certificate) ks.getCertificate(ALIAS);
        if (key == null || cert == null) throw new IllegalStateException("adb identity missing after create");
        return new BubbleKey(key, cert);
    }

    private static void generate(boolean strongBox) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE);
        KeyGenParameterSpec.Builder spec = new KeyGenParameterSpec.Builder(ALIAS,
                // PURPOSE_SIGN for the TLS signature; PURPOSE_DECRYPT because Conscrypt does the
                // TLS 1.3 RSA-PSS by a raw RSA (NoPadding) Cipher through the key.
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(2048)
                .setCertificateSubject(new X500Principal("CN=Bubble"))
                .setCertificateSerialNumber(BigInteger.valueOf(1))
                // DIGEST_NONE lets Conscrypt sign a pre-computed digest; the SHAs cover direct use.
                .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256,
                        KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PSS,
                        KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setIsStrongBoxBacked(strongBox);
        gen.initialize(spec.build());
        gen.generateKeyPair();
    }
}
