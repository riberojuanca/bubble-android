package org.bubble.app;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import java.util.TreeSet;

/** Drives the privileged engine (bubble-shell) over Bubble's own adb channel. */
final class Engine {

    static final String DIR = "/data/local/tmp/bubble";
    static final String JAR = DIR + "/bubble-shell.jar";
    static final String RULES = DIR + "/rules.txt";
    private static final String RUN = "CLASSPATH=" + JAR + " app_process /system/bin org.bubble.shell.BubbleEngine ";
    // The [ ] keeps pgrep/pkill from matching the sh -c command line that runs them.
    /**
     * Guard period. Each pass costs a few tens of ms of CPU; with wireless debugging off almost no
     * one holds the shell identity, so a minute is enough. The guard runs its first pass at once.
     */
    private static final int WATCH_SECONDS = 60;
    private static final String WATCH_PATTERN = "'BubbleEngine[ ]watch'";

    private final Context context;
    private final AdbChannel adb;
    /** The bubble the guard keeps free of Google services; -1: none (it guards no profile). */
    private int bubbleUser = -1;

    Engine(Context context, AdbChannel channel) {
        this.context = context.getApplicationContext();
        this.adb = channel;
    }

    AdbChannel adb() { return adb; }

    void setBubbleUser(int user) { bubbleUser = user; }

    /**
     * Connect to the local TLS daemon; false while wireless debugging is off or not paired. Retries
     * a few times: right after pairing, adbd needs a moment to trust the new key on the connect
     * server, so the first TLS handshake can be closed mid-way.
     */
    boolean connect(long timeoutMs) {
        if (adb.isConnected()) return true;
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (adb.connect(Math.min(8000, Math.max(1000, deadline - System.currentTimeMillis())))) return true;
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    void disconnect() {
        adb.disconnect();
    }

    /** One-time setup after pairing; safe to repeat. */
    String setup() throws Exception {
        StringBuilder log = new StringBuilder();
        install(log);
        log.append(adb.exec("pm grant " + context.getPackageName()
                + " android.permission.WRITE_SECURE_SETTINGS 2>&1"));
        log.append(allowNoticeListener());
        // The paired key never expires (AdbKeyStore default is 7 days without use).
        log.append(adb.exec("settings put global adb_allowed_connection_time 0 2>&1"));
        return log.toString();
    }

    /** Let Bubble hide the "Wireless debugging connected" notification during its own sessions. */
    String allowNoticeListener() throws Exception {
        return adb.exec("cmd notification allow_listener " + AdbNoticeListener.COMPONENT + " 0 2>&1");
    }

    /** Push the engine bundled in the APK if the copy on the phone differs. */
    void install(StringBuilder log) throws Exception {
        if (!ProfileBridge.available(context)) {
            log.append(adb.exec("pm grant --user 0 " + context.getPackageName() + " android.permission.INTERACT_ACROSS_USERS"));
            log.append(adb.exec("pm grant --user 0 " + context.getPackageName() + " android.permission.MODIFY_QUIET_MODE"));
        }
        byte[] jar = asset("bubble-shell.jar");
        String want = sha256(jar);
        String have = adb.exec("sha256sum " + JAR + " 2>/dev/null").trim();
        if (have.startsWith(want)) return;
        Progress.step(context, R.string.step_engine);
        adb.exec("mkdir -p " + DIR);
        adb.writeFile(JAR, jar);
        if (!adb.exec("sha256sum " + JAR).trim().startsWith(want)) {
            throw new IllegalStateException("engine copy is corrupt");
        }
        // A guard already running still has the old engine loaded: stop it, so the session's
        // ensureWatcher starts it again with the new one.
        adb.exec("pkill -f " + WATCH_PATTERN);
        log.append("engine installed\n");
    }

    String run(String args) throws Exception {
        return adb.exec(RUN + args + " 2>&1");
    }

    /**
     * Bring the phone's firewall to {@code wanted}: block what is new (and make it survive boot via
     * Settings), unblock what was removed, then restart the guard with the new list.
     */
    String applyFirewall(Set<String> wanted, Set<String> previous) throws Exception {
        Progress.step(context, R.string.step_apply_phone);
        StringBuilder log = new StringBuilder();
        install(log);
        Set<String> added = new TreeSet<>(wanted);
        added.removeAll(previous);
        Set<String> removed = new TreeSet<>(previous);
        removed.removeAll(wanted);
        if (!removed.isEmpty()) log.append(run("host-unblock " + String.join(" ", removed)));
        if (!added.isEmpty()) {
            log.append(run("host-block " + String.join(" ", added)));
            log.append(run("host-bgdata-off " + String.join(" ", added)));
        }
        writeRules(wanted);
        log.append(run("host-apply " + RULES));
        restartWatcher();
        log.append(watcherRunning() ? "guard running\n" : "guard DID NOT start\n");
        return log.toString();
    }

    /**
     * After a reboot: rules are already on the phone; re-apply them (if the firewall is on) and make
     * sure the guard runs. The guard also keeps Google services out of the bubble.
     */
    String restore(boolean firewall) throws Exception {
        StringBuilder log = new StringBuilder();
        install(log);
        if (firewall) log.append(run("host-apply " + RULES));
        if (!watcherRunning()) restartWatcher();
        log.append(watcherRunning() ? "guard running\n" : "guard DID NOT start\n");
        return log.toString();
    }

    /**
     * Bubble commands: bubble-create, bubble-adopt, bubble-recreate &lt;id&gt;, bubble-status. See
     * BubbleEngine for what each one checks.
     */
    String bubble(String command) throws Exception {
        StringBuilder log = new StringBuilder();
        install(log);
        Progress.step(context, command.startsWith("bubble-create") || command.startsWith("bubble-recreate")
                ? R.string.step_creating : R.string.step_check);
        return log + run(command);
    }


    /** Apps inside the bubble beyond its base, as {package, apk path}; Bubble's own copy excluded. */
    java.util.List<String[]> bubbleApps(int user) throws Exception {
        StringBuilder log = new StringBuilder();
        install(log);
        Progress.step(context, R.string.step_read_apps);
        java.util.List<String[]> apps = new java.util.ArrayList<>();
        for (String line : run("bubble-apps " + user).split("\n")) {
            if (!line.startsWith("app=")) continue;
            int path = line.indexOf(" path=");
            String pkg = (path < 0 ? line.substring(4) : line.substring(4, path)).trim();
            if (pkg.equals(context.getPackageName())) continue;
            apps.add(new String[]{pkg, path < 0 ? "" : line.substring(path + 6).trim()});
        }
        return apps;
    }

    /** The bubble's firewall: only these apps inside have network. */
    String bubbleFirewall(int user, Set<String> allowed) throws Exception {
        Progress.step(context, R.string.step_firewall);
        return run("bubble-firewall " + user + " " + context.getPackageName()
                + (allowed.isEmpty() ? "" : " " + String.join(" ", allowed)));
    }

    // ---- the Meta space ----------------------------------------------------

    /** meta-create, meta-status, meta-delete &lt;id&gt;. */
    String meta(String command) throws Exception {
        StringBuilder log = new StringBuilder();
        install(log);
        Progress.step(context, command.startsWith("meta-create") ? R.string.step_creating_meta : R.string.step_check);
        return log + run(command);
    }

    /** Apps of the Meta space beyond its base, as {package, apk path}; Bubble's own copy excluded. */
    java.util.List<String[]> metaApps(int user) throws Exception {
        Progress.step(context, R.string.step_read_apps);
        java.util.List<String[]> apps = new java.util.ArrayList<>();
        for (String line : run("meta-apps " + user).split("\n")) {
            if (!line.startsWith("app=")) continue;
            int path = line.indexOf(" path=");
            String pkg = (path < 0 ? line.substring(4) : line.substring(4, path)).trim();
            if (pkg.equals(context.getPackageName())) continue;
            apps.add(new String[]{pkg, path < 0 ? "" : line.substring(path + 6).trim()});
        }
        return apps;
    }

    String metaAdd(int user, Set<String> pkgs) throws Exception {
        Progress.step(context, R.string.step_adding);
        return run("meta-add " + user + " " + String.join(" ", pkgs));
    }

    String metaRemove(int user, Set<String> pkgs) throws Exception {
        Progress.step(context, R.string.step_removing);
        return run("meta-remove " + user + " " + String.join(" ", pkgs));
    }

    String metaFirewall(int user, Set<String> allowed) throws Exception {
        Progress.step(context, R.string.step_firewall);
        return run("meta-firewall " + user + " " + context.getPackageName()
                + (allowed.isEmpty() ? "" : " " + String.join(" ", allowed)));
    }

    String metaKeyboard(int user, String imeId) throws Exception {
        Progress.step(context, R.string.step_keyboard);
        return run("meta-keyboard " + user + " " + imeId);
    }

    /** The launcher's home-screen icon size in px (BubbleEngine.launcherIcons); 0 if unknown. */
    int launcherIconPx() throws Exception {
        for (String line : run("launcher-icons").split("\n")) {
            if (!line.startsWith("launcher_icon_px=")) continue;
            try {
                return Integer.parseInt(line.substring("launcher_icon_px=".length()).trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /** The bubble locks itself unless an app inside plays (BubbleEngine.bubbleSmartLock). */
    String smartLock(int user) throws Exception {
        return run("bubble-smart-lock " + user + " " + context.getPackageName());
    }

    /** Stop Facebook and Instagram from opening Play Services' App info in the Meta space. */
    String metaSettingsOff(int user) throws Exception {
        return run("meta-settings-off " + user);
    }

    /** Open an app of the bubble (only the shell can, see BubbleEngine.bubbleLaunch). */
    String bubbleLaunch(int user, String pkg) throws Exception {
        StringBuilder log = new StringBuilder();
        install(log);
        return log + run("bubble-launch " + user + " " + pkg + " " + context.getPackageName());
    }

    /** Enable apps installed outside in the bubble (the engine refuses Google services). */
    String addApps(int user, Set<String> pkgs) throws Exception {
        Progress.step(context, R.string.step_adding);
        StringBuilder log = new StringBuilder();
        install(log);
        return log + run("bubble-add " + user + " " + String.join(" ", pkgs));
    }

    /** Installer and file picker inside the bubble (older bubbles were made without the picker). */
    String ensureTools(int user) throws Exception {
        return run("bubble-tools " + user);
    }

    String allowInstalls(int user, String pkg) throws Exception {
        return run("bubble-allow-installs " + user + " " + pkg);
    }

    String setKeyboard(int user, String imeId) throws Exception {
        Progress.step(context, R.string.step_keyboard);
        return run("bubble-keyboard " + user + " " + imeId);
    }

    String removeApps(int user, Set<String> pkgs) throws Exception {
        Progress.step(context, R.string.step_removing);
        StringBuilder log = new StringBuilder();
        install(log);
        return log + run("bubble-remove " + user + " " + String.join(" ", pkgs));
    }

    /** Install an APK into the bubble only, streamed like adb install (-S: pm reads exactly size bytes). */
    String installApk(int user, InputStream apk, long size) throws Exception {
        return adb.execWithInput("cmd package install --user " + user + " -S " + size + " 2>&1", apk);
    }

    private static final String DOWNLOADS = "content://media/external/downloads";

    /**
     * APKs in the bubble's Downloads (a browser inside saves them there), as {id, size, name}.
     * The shell cannot open the bubble's storage, but can ask its media provider as that user.
     * Null when the bubble is locked: its provider is not running then.
     */
    java.util.List<String[]> bubbleDownloads(int user) throws Exception {
        String out = adb.exec("content query --user " + user + " --uri " + DOWNLOADS
                + " --projection _id:_size:is_pending:_display_name 2>&1");
        if (!out.startsWith("Row:") && !out.contains("No result found")) return null;
        java.util.List<String[]> apks = new java.util.ArrayList<>();
        for (String line : out.split("\n")) {
            int id = line.indexOf("_id="), size = line.indexOf(", _size="),
                    pending = line.indexOf(", is_pending="), name = line.indexOf(", _display_name=");
            if (id < 0 || size < 0 || pending < 0 || name < 0) continue;
            String n = line.substring(name + 16).trim();
            String s = line.substring(size + 8, pending);
            if (!n.toLowerCase(java.util.Locale.ROOT).endsWith(".apk") || s.equals("NULL")
                    || !line.substring(pending + 13, name).equals("0")) continue;
            apks.add(new String[]{line.substring(id + 4, size), s, n});
        }
        return apks;
    }

    /** Copy one of {@link #bubbleDownloads} into {@code file} (Bubble's cache), to read its icon. */
    void copyDownload(int user, String id, java.io.File file) throws Exception {
        java.io.File part = new java.io.File(file.getPath() + ".part");
        try (java.io.OutputStream out = new java.io.BufferedOutputStream(new java.io.FileOutputStream(part))) {
            adb.execTo("content read --user " + user + " --uri " + DOWNLOADS + "/" + Long.parseLong(id), out);
        }
        if (!part.renameTo(file)) throw new java.io.IOException("could not save " + file);
    }

    /** Install one of {@link #bubbleDownloads} straight from the bubble's provider, only inside. */
    String installDownload(int user, String id, long size) throws Exception {
        return adb.execLong("content read --user " + user + " --uri " + DOWNLOADS + "/" + Long.parseLong(id)
                + " | cmd package install --user " + user + " -S " + size + " 2>&1");
    }

    /**
     * Ask Android to unlock the bubble: it shows its own fingerprint / PIN prompt and unlocks it
     * once confirmed (cmd prints "Failed" meanwhile, so the outcome is read with bubbleLocked).
     */
    void unlockBubble(int user) throws Exception {
        adb.exec("cmd user set-quiet-mode " + user + " false 2>&1");
    }

    boolean bubbleLocked(int user) throws Exception {
        return adb.exec("dumpsys user | grep -A2 'UserInfo{" + user + ":'").contains("QUIET_MODE");
    }

    /** Stop the guard (after "Remove everything" there is nothing left to guard). */
    void stopWatcher() throws Exception {
        adb.exec("pkill -f " + WATCH_PATTERN);
    }

    /** Start the guard if it is not running. */
    void ensureWatcher() throws Exception {
        Progress.step(context, R.string.step_guard);
        if (!watcherRunning()) restartWatcher();
    }

    String status() throws Exception {
        return run("host-status " + RULES) + (watcherRunning() ? "guard running\n" : "guard stopped\n");
    }

    void writeRules(Set<String> pkgs) throws Exception {
        StringBuilder sb = new StringBuilder("# written by the Bubble app\n");
        for (String p : pkgs) sb.append(p).append('\n');
        adb.writeFile(RULES, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    boolean watcherRunning() throws Exception {
        return !adb.exec("pgrep -f " + WATCH_PATTERN).trim().isEmpty();
    }

    void restartWatcher() throws Exception {
        adb.exec("pkill -f " + WATCH_PATTERN);
        // shellDetached (the "shell:" service) + setsid: the guard outlives this connection and
        // adbd's stream-close kill; it runs until the next reboot.
        // The trailing `sleep 1` keeps the shell stream open a moment after backgrounding so adbd's
        // stream-close SIGHUP lands after setsid has fully detached the guard (otherwise a fast
        // close races the detach and kills it).
        adb.shellDetached("setsid nohup sh -c '" + RUN + "watch " + RULES + " " + WATCH_SECONDS + " " + bubbleUser + "' > " + DIR
                + "/watch.log 2>&1 < /dev/null & sleep 1");
        Thread.sleep(1500);
    }

    private byte[] asset(String name) throws Exception {
        try (InputStream in = context.getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static String sha256(byte[] data) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(data)) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
