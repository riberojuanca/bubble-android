package org.bubble.app;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import javax.net.ssl.HttpsURLConnection;

/**
 * The apps every bubble starts with: F-Droid (to install and update apps inside) and a keyboard
 * (keyboards are per profile; the bubble has none). Downloaded from f-droid.org at the version it
 * recommends, and accepted only when signed by the pinned certificate, so a tampered download or a
 * different app under the same name is rejected.
 */
final class StarterApps {

    static final class App {
        final String pkg, label, certSha256;
        App(String pkg, String label, String certSha256) {
            this.pkg = pkg;
            this.label = label;
            this.certSha256 = certSha256;
        }
    }

    static final App FDROID = new App("org.fdroid.fdroid", "F-Droid",
            "43238d512c1e5eb2d6569f4a3afbf5523418b82e0a3ed1552770abb9a9c9ccab");
    /** HeliBoard is published on F-Droid with its developer's own signature (reproducible build). */
    static final App KEYBOARD = new App("helium314.keyboard", "HeliBoard",
            "5ec0a5313aa43558ee75b20b58ccd8194cdbf066df94a43ba288d933d60b86ce");
    static final String KEYBOARD_IME = "helium314.keyboard/.latin.LatinIME";

    /**
     * Preferred keyboard: Gboard, when the phone has it, copied from outside (no download) and cut
     * off the network by the bubble's firewall before it ever runs. HeliBoard is the fallback.
     */
    static final String GBOARD = "com.google.android.inputmethod.latin";
    static final String GBOARD_IME = GBOARD + "/com.android.inputmethod.latin.LatinIME";
    static final App[] ALL = {FDROID, KEYBOARD};

    private static final String API = "https://f-droid.org/api/v1/packages/";
    private static final String REPO = "https://f-droid.org/repo/";

    private StarterApps() {}

    /** Download the recommended version into the cache and verify its signer; the caller deletes it. */
    static File download(Context context, App app) throws Exception {
        JSONObject info = new JSONObject(new String(get(API + app.pkg), StandardCharsets.UTF_8));
        long versionCode = info.getLong("suggestedVersionCode");
        File apk = new File(context.getCacheDir(), app.pkg + ".apk");
        HttpsURLConnection c = open(REPO + app.pkg + "_" + versionCode + ".apk");
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(apk)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            c.disconnect();
        }
        try {
            verify(context, apk, app);
        } catch (Exception e) {
            apk.delete();
            throw e;
        }
        return apk;
    }

    /** Parsing the archive checks its signature; then the single signer must be the pinned one. */
    private static void verify(Context context, File apk, App app) throws Exception {
        PackageInfo pi = context.getPackageManager().getPackageArchiveInfo(apk.getPath(),
                PackageManager.GET_SIGNING_CERTIFICATES);
        if (pi == null || pi.signingInfo == null || !app.pkg.equals(pi.packageName)) {
            throw new SecurityException(app.label + ": the downloaded file is not valid");
        }
        Signature[] signers = pi.signingInfo.getApkContentsSigners();
        if (pi.signingInfo.hasMultipleSigners() || signers.length != 1
                || !app.certSha256.equals(sha256(signers[0].toByteArray()))) {
            throw new SecurityException(app.label + ": la firma no es la oficial");
        }
    }

    private static byte[] get(String url) throws Exception {
        HttpsURLConnection c = open(url);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    private static HttpsURLConnection open(String url) throws Exception {
        HttpsURLConnection c = (HttpsURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        if (c.getResponseCode() != 200) {
            c.disconnect();
            throw new IllegalStateException("f-droid.org answered " + c.getResponseCode() + " for " + url);
        }
        return c;
    }

    private static String sha256(byte[] data) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(data)) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
