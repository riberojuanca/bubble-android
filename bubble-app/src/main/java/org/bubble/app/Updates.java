package org.bubble.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/** Checks the official GitHub release; installation always goes through Android's installer. */
final class Updates {
    private static final String LATEST =
            "https://api.github.com/repos/riberojuanca/bubble-android/releases/latest";
    private static final long CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L;
    private static boolean checking;

    interface Progress { void downloaded(int percent); }

    static final class Release {
        final String json;
        final String version;
        final long code;
        final String filename;
        final String url;
        final String checksumUrl;
        final long size;

        Release(String json, String version, long code, JSONObject apk, JSONObject checksum) throws Exception {
            this.json = json;
            this.version = version;
            this.code = code;
            filename = "bubble-" + version + ".apk";
            url = apk.getString("browser_download_url");
            checksumUrl = checksum.getString("browser_download_url");
            size = apk.getLong("size");
            String prefix = UpdatePolicy.DOWNLOAD_PREFIX + "v" + version + "/";
            if (!url.equals(prefix + filename) || !checksumUrl.equals(prefix + filename + ".sha256")
                    || size <= 0 || size > UpdatePolicy.MAX_APK_BYTES) {
                throw new IOException("Invalid release assets");
            }
        }
    }

    private Updates() {}

    /** F-Droid and the clients of its repositories: they deliver Bubble's updates themselves. */
    private static final java.util.Set<String> STORES = new java.util.HashSet<>(Arrays.asList(
            "org.fdroid.fdroid", "org.fdroid.basic", "com.looker.droidify", "com.machiav3lli.fdroid"));

    /**
     * Installed from F-Droid: then the store updates Bubble, and Bubble neither checks nor downloads
     * from GitHub. Decided at run time, so F-Droid ships the very APK published on GitHub (a
     * reproducible build, signed with Bubble's own key) instead of a separate build.
     */
    static boolean fromStore(Context context) {
        try {
            String installer = context.getPackageManager()
                    .getInstallSourceInfo(context.getPackageName()).getInstallingPackageName();
            return installer != null && STORES.contains(installer);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Automatic checks are opt-in: a fresh install never contacts GitHub on its own (F-Droid's
     * reviewers install the APK by hand, where fromStore cannot tell). Installs that already
     * checked automatically before this choice existed keep doing so.
     */
    static boolean automatic(Context c) {
        SharedPreferences prefs = c.getSharedPreferences("updates", Context.MODE_PRIVATE);
        return prefs.getBoolean("automatic", prefs.contains("last_attempt"));
    }

    static void setAutomatic(Context c, boolean on) {
        c.getSharedPreferences("updates", Context.MODE_PRIVATE).edit().putBoolean("automatic", on).apply();
    }

    /** Only the main-profile copy checks, at most once every six hours. Offline failures stay quiet. */
    static void checkAutomatically(Activity activity) {
        if (BootReceiver.insideBubble() || checking || fromStore(activity) || !automatic(activity)) return;
        SharedPreferences prefs = activity.getSharedPreferences("updates", Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long last = prefs.getLong("last_attempt", 0);
        if (now >= last && now - last < CHECK_INTERVAL_MS) return;
        checking = true;
        prefs.edit().putLong("last_attempt", now).apply();
        Context app = activity.getApplicationContext();
        new Thread(() -> {
            Release release = null;
            try {
                release = latest(app);
            } catch (Exception ignored) {
                // Automatic checks must not interrupt normal use when GitHub is unreachable.
            }
            Release found = release;
            activity.runOnUiThread(() -> {
                checking = false;
                if (found == null || activity.isFinishing() || activity.isDestroyed()
                        || !activity.hasWindowFocus()) return;
                new Ui.Sheet(activity).title(R.string.update_available_title)
                        .message(activity.getString(R.string.update_available_body, found.version))
                        .action(activity.getString(R.string.update_action), () -> activity.startActivity(
                                new Intent(activity, UpdateActivity.class).putExtra("release", found.json)
                                        .putExtra("download", true)))
                        .secondary(activity.getString(R.string.update_later), null).show();
            });
        }, "bubble-update-check").start();
    }

    static Release latest(Context context) throws Exception {
        File[] stale = context.getCacheDir().listFiles((dir, name) -> name.startsWith("bubble-update-")
                && name.endsWith(".apk"));
        if (stale != null) {
            long cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L;
            for (File file : stale) if (file.lastModified() < cutoff) file.delete();
        }
        try {
            return parse(context, readText(LATEST, 1024 * 1024));
        } catch (ReleaseMissing ignored) {
            return null;
        }
    }

    static Release parse(Context context, String json) throws Exception {
        JSONObject release = new JSONObject(json);
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null;
        String tag = release.getString("tag_name");
        if (!tag.startsWith("v")) throw new IOException("Invalid release tag");
        String version = tag.substring(1);
        long code = UpdatePolicy.versionCode(version);
        if (code <= installed(context).getLongVersionCode()) return null;
        String filename = "bubble-" + version + ".apk";
        JSONObject apk = null;
        JSONObject checksum = null;
        JSONArray assets = release.getJSONArray("assets");
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if (!"uploaded".equals(asset.optString("state"))) continue;
            if (filename.equals(asset.optString("name"))) apk = asset;
            if ((filename + ".sha256").equals(asset.optString("name"))) checksum = asset;
        }
        if (apk == null || checksum == null) throw new IOException("Release assets are missing");
        return new Release(json, version, code, apk, checksum);
    }

    static File download(Context context, Release release, Progress progress) throws Exception {
        String expected = UpdatePolicy.checksum(readText(release.checksumUrl, 4096), release.filename);
        File file = File.createTempFile("bubble-update-", ".apk", context.getCacheDir());
        boolean complete = false;
        HttpURLConnection connection = null;
        try {
            connection = connect(release.url);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long count = 0;
            int lastPercent = -1;
            long deadline = android.os.SystemClock.elapsedRealtime() + 5 * 60 * 1000;
            try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(file)) {
                byte[] buffer = new byte[32 * 1024];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    count += n;
                    if (count > release.size || count > UpdatePolicy.MAX_APK_BYTES
                            || android.os.SystemClock.elapsedRealtime() > deadline) {
                        throw new IOException("Download exceeded its limits");
                    }
                    digest.update(buffer, 0, n);
                    out.write(buffer, 0, n);
                    int percent = (int) (count * 100 / release.size);
                    if (percent != lastPercent) {
                        progress.downloaded(percent);
                        lastPercent = percent;
                    }
                }
                out.getFD().sync();
            }
            if (count != release.size || !MessageDigest.isEqual(digest.digest(), decodeHex(expected))) {
                throw new IOException("Download checksum mismatch");
            }
            verifyPackage(context, file, release.code);
            complete = true;
            return file;
        } finally {
            if (connection != null) connection.disconnect();
            if (!complete && !file.delete()) file.deleteOnExit();
        }
    }

    static void verifyPackage(Context context, File file, long expectedCode) throws Exception {
        PackageManager pm = context.getPackageManager();
        PackageInfo archive = pm.getPackageArchiveInfo(file.getAbsolutePath(),
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES));
        PackageInfo current = installed(context);
        if (archive == null || !context.getPackageName().equals(archive.packageName)
                || archive.getLongVersionCode() != expectedCode
                || expectedCode <= current.getLongVersionCode()
                || archive.signingInfo == null || current.signingInfo == null
                || !sameSigners(archive.signingInfo.getApkContentsSigners(),
                current.signingInfo.getApkContentsSigners())) {
            throw new IOException("Update package or signing certificate does not match");
        }
    }

    private static boolean sameSigners(Signature[] a, Signature[] b) {
        return a != null && b != null && a.length > 0 && a.length == b.length
                && Arrays.asList(a).containsAll(Arrays.asList(b));
    }

    private static PackageInfo installed(Context context) throws PackageManager.NameNotFoundException {
        return context.getPackageManager().getPackageInfo(context.getPackageName(),
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES));
    }

    private static byte[] decodeHex(String value) {
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }

    private static String readText(String url, int limit) throws Exception {
        HttpURLConnection connection = connect(url);
        try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (out.size() + n > limit) throw new IOException("Release response is too large");
                out.write(buffer, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection connect(String value) throws Exception {
        for (int redirects = 0; redirects <= 5; redirects++) {
            if (!UpdatePolicy.isTrustedUrl(value)) throw new IOException("Untrusted update URL");
            HttpURLConnection connection = (HttpURLConnection) new URL(value).openConnection();
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(30_000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "Bubble-Android-Updater");
            connection.setRequestProperty("Accept", value.startsWith(LATEST)
                    ? "application/vnd.github+json" : "application/octet-stream");
            try {
                int status = connection.getResponseCode();
                if (status == 200) return connection;
                String location = connection.getHeaderField("Location");
                if (status >= 300 && status < 400 && location != null) {
                    value = new URL(new URL(value), location).toString();
                    connection.disconnect();
                    continue;
                }
                if (status == 404 && value.equals(LATEST)) throw new ReleaseMissing();
                throw new IOException("GitHub returned HTTP " + status);
            } catch (Exception e) {
                connection.disconnect();
                throw e;
            }
        }
        throw new IOException("Too many update redirects");
    }

    private static final class ReleaseMissing extends IOException {}
}
