package org.bubble.app;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Ties the identity, the adb channel, the pairing handshake and the engine together. The UI and
 * the boot service call only this. Messages for the user come from string resources (English and
 * Spanish); the engine's own log stays technical English.
 */
final class Controller {

    private final Context context;
    private final Store store;
    private final BubbleKey key;
    private final Engine engine;
    private final PairingClient pairing;

    Controller(Context context) throws Exception {
        this.context = context.getApplicationContext();
        this.store = new Store(this.context);
        this.key = Link.key();
        this.engine = new Engine(this.context, Link.channel(this.context));
        this.engine.setBubbleUser(store.bubbleUser());
        this.pairing = new PairingClient() {}; // default-method implementation
    }

    Store store() { return store; }

    interface Op<T> {
        T run() throws Exception;
    }

    final class NotConnected extends IllegalStateException {
        NotConnected() { super(context.getString(notConnectedReason())); }
    }

    private int notConnectedReason() {
        if (WirelessDebugging.isOn(context) || !WirelessDebugging.hasWifi(context)) return R.string.error_not_connected;
        // Off because Bubble could not turn it on (the permission was lost, e.g. reinstalled).
        if (!WirelessDebugging.canSwitch(context)) return R.string.error_debugging_permission;
        // Bubble turned it on and it is off again: Android switched it off on a network not
        // marked "Always allow" and asked the user instead (AdbDebuggingManager).
        return R.string.error_network_not_trusted;
    }

    private int depth;
    /**
     * Only opening an app of the bubble: skip the once-per-process upkeep (firewall list, F-Droid,
     * keyboard), which reads the bubble and adds seconds; the next Bubble session does it.
     */
    private boolean quick;
    /** Once per app process: the launcher's icon size, for the widget (the user may change the grid). */
    private static boolean launcherChecked;
    /** Once per app process, not per Controller: every screen action creates a new Controller. */
    private static boolean startersChecked;

    /**
     * Every engine call runs in a session: Bubble turns wireless debugging on, connects, works,
     * disconnects and leaves the switch as it found it (shared across overlapping sessions, see
     * WirelessDebugging). Nested sessions share the outer one. The guard is a detached shell
     * process in adbd's cgroup: it keeps running with the switch off while adbd runs (USB debugging
     * on), and dies with adbd otherwise (both verified on the emulator). Each session makes sure it
     * runs, and its first pass checks the bubble at once, so every action and every boot re-checks
     * even when the guard does not outlive the session.
     */
    <T> T session(Op<T> op) throws Exception {
        if (depth > 0) return op.run();
        if (!WirelessDebugging.hasWifi(context)) {
            throw new IllegalStateException(context.getString(R.string.error_setup_wifi));
        }
        android.util.Log.i("Bubble", "session start");
        // While a Bubble screen is visible the connection is kept open (Link): no switching
        // debugging on and off, no reconnecting, for every action.
        boolean held = Link.held();
        if (!held) {
            Progress.step(context, R.string.step_debug_on);
            WirelessDebugging.acquire(context);
        } else {
            // Android may have switched it off under the kept connection (network not trusted
            // yet): once the user allows the network, the next try must turn it on again.
            WirelessDebugging.reassert(context);
        }
        depth++;
        try {
            if (!engine.connect(30000)) throw new NotConnected();
            // Connected because the user turned debugging on by hand: take the switch back.
            if (!WirelessDebugging.canSwitch(context)) engine.grantSecureSettings();
            T result;
            try {
                result = op.run();
            } catch (java.io.IOException e) {
                // A kept connection may have dropped (Wi-Fi changed, debugging switched off):
                // reconnect once and retry.
                engine.disconnect();
                if (!engine.connect(30000)) throw new NotConnected();
                result = op.run();
            }
            if (store.bubbleUser() > 0 && !startersChecked && !quick) {
                ensureStarters(store.bubbleUser());
                Progress.step(context, R.string.step_quiet);
                String privacy = engine.run("quiet-logs");
                if (privacy.contains("bubble-shell:")) {
                    startersChecked = false;
                    store.setLog(privacy);
                    throw new IllegalStateException(context.getString(R.string.notification_privacy_failed));
                }
            }
            if (!launcherChecked) {
                launcherChecked = true;
                int px = engine.launcherIconPx();
                if (px > 0 && px != store.launcherIconPx()) {
                    store.setLauncherIconPx(px);
                    AppsWidget.refresh(context);
                }
            }
            if (store.bubbleUser() > 0 || !store.applied().isEmpty()) engine.ensureWatcher();
            // The guard outlives the session only while adbd keeps running, i.e. with USB
            // debugging on; otherwise it dies when wireless debugging goes off and each session
            // (opening Bubble, an action, boot) does the checking.
            boolean running = engine.watcherRunning();
            boolean usb = android.provider.Settings.Global.getInt(context.getContentResolver(),
                    android.provider.Settings.Global.ADB_ENABLED, 0) == 1;
            store.setGuard(!running ? Store.GUARD_OFF : usb ? Store.GUARD_ALWAYS : Store.GUARD_SESSION);
            return result;
        } finally {
            depth--;
            if (!held) {
                Progress.step(context, R.string.step_debug_off);
                engine.disconnect();
                WirelessDebugging.release(context);
            }
        }
    }

    /** First-time setup: pair with the code, then push the engine and grant persistence. */
    String pair(String host, int port, String code) throws Exception {
        boolean ok = pairing.pair(host, port, code, key.privateKey(), key.certificate());
        if (!ok) return context.getString(R.string.pair_wrong_code);
        if (!engine.connect(20000)) return context.getString(R.string.pair_not_connected_yet);
        store.setPaired(true);
        String log = engine.setup();
        store.setLog(log);
        // From now on Bubble switches wireless debugging on only while it works.
        engine.disconnect();
        WirelessDebugging.set(context, false);
        return context.getString(R.string.pair_done);
    }

    /**
     * The phone's (outside) firewall: the chosen list is kept; it is applied only while the
     * firewall is on (off lifts every rule but remembers the list).
     */
    String applyPhone(Set<String> wanted, boolean on) throws Exception {
        Set<String> target = on ? wanted : new TreeSet<>();
        return session(() -> {
            String log = engine.applyFirewall(target, store.applied());
            store.setApplied(target);
            store.setWanted(wanted);
            store.setPhoneFirewallOn(on);
            store.setLog(log);
            return log;
        });
    }

    /** After a reboot: reconnect and make sure the firewalls and the guard are up. */
    String restore() throws Exception {
        boolean firewall = !store.applied().isEmpty();
        if (!store.paired() || (!firewall && store.bubbleUser() < 0)) return "";
        try {
            return session(() -> {
                String log = engine.restore(firewall);
                store.setLog(log);
                return log;
            });
        } catch (NotConnected e) {
            return e.getMessage();
        }
    }

    /** What the engine reported about the Private Space. */
    static final class BubbleReport {
        final String log;
        final boolean connected;
        final int user;            // -1: none
        final int googleServices;
        final int googleActive;
        final boolean needsDecision;

        BubbleReport(String log, boolean connected) {
            this.log = log;
            this.connected = connected;
            this.user = intValue(log, "bubble_user_id=");
            this.googleServices = Math.max(0, intValue(log, "bubble_google_services="));
            this.googleActive = Math.max(0, intValue(log, "bubble_google_active="));
            this.needsDecision = log.contains("bubble_decision=needed");
        }

        /** Ready to use: it exists, nothing is pending, no Google services and the engine did not fail. */
        boolean ready() {
            return connected && user > 0 && !needsDecision && googleServices == 0 && !log.contains("bubble-shell:");
        }

        static int intValue(String log, String key) {
            for (String line : log.split("\n")) {
                if (!line.startsWith(key)) continue;
                try {
                    return Integer.parseInt(line.substring(key.length()).trim());
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
            return -1;
        }
    }

    /** Create the bubble; if a Private Space already exists, report it for the user to decide. */
    BubbleReport createBubble() throws Exception {
        return bubble("bubble-create", true);
    }

    /** Use the existing Private Space; the engine refuses if Google services are inside. */
    BubbleReport adoptBubble() throws Exception {
        return bubble("bubble-adopt", true);
    }

    /** Remove the Private Space the user saw (its apps and data are lost) and create a new one. */
    BubbleReport recreateBubble(int user) throws Exception {
        return bubble("bubble-recreate " + user, true);
    }

    /** Re-check the bubble (Google services inside or not) without changing anything. */
    BubbleReport bubbleStatus() throws Exception {
        if (ProfileBridge.available(context) && store.bubbleUser() > 0) {
            startUpkeep();
            int user = store.bubbleUser();
            try {
                if (ProfileBridge.locked(context, user)) {
                    // Nothing can be asked inside a locked bubble: the counts last read stand.
                    return new BubbleReport("bubble_user_id=" + user + "\nbubble_locked=true"
                            + "\nbubble_google_services=" + store.lastBubbleGoogle(false)
                            + "\nbubble_google_active=" + store.lastBubbleGoogle(true) + "\n", true);
                }
                android.os.Bundle status = ProfileBridge.call(context, user, ProfileBridge.STATUS, null);
                store.setLastBubbleGoogle(status.getInt("google_services"), status.getInt("google_enabled"));
                BubbleReport report = new BubbleReport("bubble_user_id=" + user
                        + "\nbubble_google_services=" + status.getInt("google_services")
                        + "\nbubble_google_active=" + status.getInt("google_enabled") + "\n", true);
                BubbleCache.setReport(report);
                return report;
            } catch (Exception e) {
                // The bubble does not answer: removed from Android's Settings, or its copy of
                // Bubble is gone. The engine checks, and forgets a bubble that no longer exists.
                android.util.Log.w("Bubble", "bubble status through the bridge failed", e);
            }
        }
        return bubble("bubble-status", false);
    }

    /** Once per app process: the engine's upkeep has started (or there was no Wi-Fi for it). */
    private static boolean upkeepStarted;

    /**
     * What only the engine can do, which opening Bubble used to do through every status check:
     * silence the bubble-revealing events (lost at each reboot), start the guard, reapply the
     * firewall, F-Droid and the keyboard, read the launcher's icon size. With the bridge the
     * status no longer needs the engine, so this runs once per app process in the background,
     * while the screen already shows the status. It needs wireless debugging, hence Wi-Fi:
     * without it, it is tried again next time.
     */
    private void startUpkeep() {
        synchronized (Controller.class) {
            if (upkeepStarted || !WirelessDebugging.hasWifi(context)) return;
            upkeepStarted = true;
        }
        new Thread(() -> {
            try {
                // Its own Controller: a session's state (depth, connection) is per instance, and
                // the caller may start its own sessions on this one meanwhile.
                new Controller(context).session(() -> "");
            } catch (Exception e) {
                android.util.Log.w("Bubble", "engine upkeep did not run", e);
                synchronized (Controller.class) {
                    upkeepStarted = false;
                }
            }
        }, "bubble-upkeep").start();
    }

    /**
     * Only create/adopt/recreate make a Private Space the bubble. A status check never does; it
     * only forgets the bubble when that profile is gone.
     */
    private BubbleReport bubble(String command, boolean decides) throws Exception {
        try {
            return session(() -> bubbleConnected(command, decides));
        } catch (NotConnected e) {
            return new BubbleReport(e.getMessage(), false);
        }
    }

    private BubbleReport bubbleConnected(String command, boolean decides) throws Exception {
        if (decides) BubbleCache.clear();
        BubbleReport report = new BubbleReport(engine.bubble(command), true);
        if (decides) {
            store.setBubbleUser(report.ready() ? report.user : -1);
            if (report.ready()) report = new BubbleReport(report.log + ensureStarters(report.user), true);
            store.setLog(report.log);
            // The guard follows the bubble: restarted with the new id (or none).
            engine.setBubbleUser(store.bubbleUser());
            engine.restartWatcher();
        } else if (report.user != store.bubbleUser()) {
            store.setBubbleUser(-1);
        }
        if (report.connected && report.user > 0 && !report.log.contains("bubble-shell:")) {
            store.setLastBubbleGoogle(report.googleServices, report.googleActive);
        }
        BubbleCache.setReport(report);
        return report;
    }

    // ---- removing ----------------------------------------------------------

    /** Remove the bubble only (its apps and data are lost); Bubble then offers to create one. */
    String deleteBubble(int user) throws Exception {
        return session(() -> {
            String log = engine.bubble("bubble-delete " + user);
            forgetBubble();
            engine.restartWatcher();
            return keepLog(log);
        });
    }

    /**
     * Leave the phone as it was before Bubble: the Meta space and the bubble removed (apps and data
     * lost), the phone's firewall lifted, the silenced events and the usage dump restored, the
     * guard stopped. Pairing stays (only the user can revoke it in Developer options), so Bubble
     * can still be uninstalled or start again.
     */
    String removeEverything() throws Exception {
        return session(() -> {
            StringBuilder log = new StringBuilder();
            if (store.metaUser() > 0) log.append(engine.meta("meta-delete " + store.metaUser()));
            store.setMetaUser(-1);
            store.setMetaNet(true, new TreeSet<>());
            store.setMetaNet(false, new TreeSet<>());
            store.setMetaFirewallSet(false);
            store.setMetaSettingsOff(false);
            if (store.bubbleUser() > 0) log.append(engine.bubble("bubble-delete " + store.bubbleUser()));
            forgetBubble();
            if (!store.applied().isEmpty()) log.append(engine.applyFirewall(new TreeSet<>(), store.applied()));
            store.setApplied(new TreeSet<>());
            store.setPhoneFirewallOn(false);
            engine.stopWatcher();
            String restored = engine.run("undo-tweaks");
            log.append(restored);
            if (restored.contains("bubble-shell:")) {
                store.setLog(log.toString());
                throw new IllegalStateException(context.getString(R.string.notification_restore_failed));
            }
            return keepLog(log.toString());
        });
    }

    private void forgetBubble() {
        store.setBubbleUser(-1);
        store.setLastBubbleApps(new ArrayList<>());
        BubbleCache.clear();
        engine.setBubbleUser(-1);
        AppsWidget.refresh(context);
    }

    // ---- the Meta space -----------------------------------------------------

    /** What the engine reported about the Meta space. */
    static final class MetaReport {
        final String log;
        final boolean connected;
        final int user;             // -1: none
        final boolean googleOff;    // Play Services installed and disabled, nothing of Google running
        final boolean googleActive;

        MetaReport(String log, boolean connected) {
            this.log = log;
            this.connected = connected;
            this.user = BubbleReport.intValue(log, "meta_user_id=");
            this.googleOff = log.contains("meta_play_services=disabled") && log.contains("meta_google_active=0");
            this.googleActive = BubbleReport.intValue(log, "meta_google_active=") > 0;
        }

        boolean failed() { return !connected || log.contains("bubble-shell:"); }
    }

    MetaReport metaStatus() throws Exception {
        if (ProfileBridge.available(context) && store.metaUser() > 0) {
            int user = store.metaUser();
            try {
                if (ProfileBridge.locked(context, user)) return new MetaReport("meta_user_id=" + user + "\nmeta_locked=true\n", true);
                android.os.Bundle status = ProfileBridge.call(context, user, ProfileBridge.STATUS, null);
                return new MetaReport("meta_user_id=" + user + "\nmeta_google_active=" + status.getInt("google_enabled")
                        + "\nmeta_play_services=" + status.getString("play_services") + "\n", true);
            } catch (Exception e) {
                // Not answering (removed from Settings, or its copy is gone): the engine checks
                // and forgets a Meta space that no longer exists.
                android.util.Log.w("Bubble", "Meta status through the bridge failed", e);
            }
        }
        return meta("meta-status");
    }

    /** Create the Meta space, then give it its firewall and a keyboard without network. */
    MetaReport createMeta() throws Exception {
        try {
            return session(() -> {
                MetaReport r = metaConnected("meta-create");
                if (r.user > 0 && !r.failed()) {
                    r = new MetaReport(r.log + ensureMetaStarters(r.user), true);
                    store.setMetaFirewallSet(true);
                }
                return r;
            });
        } catch (NotConnected e) {
            return new MetaReport(e.getMessage(), false);
        }
    }

    /** Remove the Meta space with its apps and data; the bubble is not touched. */
    MetaReport deleteMeta(int user) throws Exception {
        store.setMetaNet(true, new TreeSet<>());
        store.setMetaNet(false, new TreeSet<>());
        return meta("meta-delete " + user);
    }

    private MetaReport meta(String command) throws Exception {
        try {
            return session(() -> metaConnected(command));
        } catch (NotConnected e) {
            return new MetaReport(e.getMessage(), false);
        }
    }

    private MetaReport metaConnected(String command) throws Exception {
        MetaReport r = new MetaReport(engine.meta(command), true);
        if (command.startsWith("meta-delete")) r = new MetaReport(r.log + engine.meta("meta-status"), true);
        store.setMetaUser(r.user);
        AppsWidget.refresh(context);
        // A Meta space this Bubble never set up (made before, or Bubble reinstalled): its apps keep
        // network, the keyboard does not, as if they had been added here.
        if (r.user > 0 && !r.failed() && !store.metaFirewallSet() && !command.startsWith("meta-create")) {
            Set<String> allowed = new TreeSet<>(packages(engine.metaApps(r.user)));
            allowed.remove(StarterApps.GBOARD);
            store.setMetaNet(true, allowed);
            store.setMetaNet(false, allowed);
            r = new MetaReport(r.log + engine.metaFirewall(r.user, store.metaTokens()), true);
        }
        if (r.user > 0 && r.log.contains("bubble firewall (deny by default)")) store.setMetaFirewallSet(true);
        // Meta spaces made before the fix still let Instagram open Play Services' App info.
        if (r.user > 0 && !r.failed() && !store.metaSettingsOff()) {
            String off = engine.metaSettingsOff(r.user);
            if (off.contains("Meta space Settings off")) store.setMetaSettingsOff(true);
            r = new MetaReport(r.log + off, true);
        }
        if (r.user < 0) {
            store.setMetaFirewallSet(false);
            store.setMetaSettingsOff(false);
        }
        store.setLog(r.log);
        return r;
    }

    /** The Meta space's firewall (deny by default) and Gboard as its keyboard, without network. */
    private String ensureMetaStarters(int user) throws Exception {
        StringBuilder log = new StringBuilder(engine.metaFirewall(user, store.metaTokens()));
        if (installedOutside(StarterApps.GBOARD)) {
            log.append(engine.metaAdd(user, Collections.singleton(StarterApps.GBOARD)));
            log.append(engine.metaKeyboard(user, StarterApps.GBOARD_IME));
        }
        return log.toString();
    }

    List<String[]> metaApps() throws Exception {
        int user = metaUserOrThrow();
        if (ProfileBridge.available(context)) return ProfileBridge.locked(context, user)
                ? new java.util.ArrayList<>() : ProfileBridge.apps(context, user);
        return session(() -> engine.metaApps(user));
    }

    /** Apps go to the Meta space to work there, so they get network at once (Gboard never does). */
    String addMetaApps(Set<String> pkgs) throws Exception {
        int user = metaUserOrThrow();
        return session(() -> {
            String log = engine.metaAdd(user, pkgs);
            for (boolean wifi : new boolean[]{true, false}) {
                Set<String> allowed = store.metaNet(wifi);
                allowed.addAll(pkgs);
                allowed.remove(StarterApps.GBOARD);
                store.setMetaNet(wifi, allowed);
            }
            AppsWidget.refresh(context);
            return keepLog(log + engine.metaFirewall(user, store.metaTokens()));
        });
    }

    String removeMetaApps(Set<String> pkgs) throws Exception {
        int user = metaUserOrThrow();
        return session(() -> {
            String log = engine.metaRemove(user, pkgs);
            AppsWidget.refresh(context);
            boolean changed = false;
            for (boolean wifi : new boolean[]{true, false}) {
                Set<String> allowed = store.metaNet(wifi);
                if (allowed.removeAll(pkgs)) {
                    store.setMetaNet(wifi, allowed);
                    changed = true;
                }
            }
            if (changed) log += engine.metaFirewall(user, store.metaTokens());
            return keepLog(log);
        });
    }

    /** Apply the Meta space's firewall as saved (Wi-Fi and mobile data lists). */
    String applyMetaNetwork() throws Exception {
        int user = metaUserOrThrow();
        if (ProfileBridge.available(context)) return keepLog(ProfileBridge.applyNetwork(context, user));
        return session(() -> keepLog(engine.metaFirewall(user, store.metaTokens())));
    }

    private int metaUserOrThrow() {
        int user = store.metaUser();
        if (user < 0) throw new IllegalStateException(context.getString(R.string.error_no_meta));
        return user;
    }

    // ---- what every bubble has ----------------------------------------------

    /**
     * Keep what every bubble needs, once per session: its firewall up (deny by default, the allowed
     * list), F-Droid (reinstalled if it was removed) and a working keyboard (only when the bubble
     * has none: a keyboard the user enabled is respected). Gboard is preferred: copied from outside,
     * and without network because it is not allowed; HeliBoard is downloaded when the phone has no
     * Gboard. A failed download is reported and retried next time.
     */
    private String ensureStarters(int user) throws Exception {
        StringBuilder log = new StringBuilder();
        // The bubble locks itself unless something plays. Before the firewall: sending the list
        // starts the VPN, which starts the smart lock, and that needs this approval first.
        log.append(engine.smartLock(user));
        // Then the firewall, every session: the bubble denies network to every app not allowed.
        log.append(engine.bubbleFirewall(user, store.bubbleTokens()));
        log.append(engine.ensureTools(user));
        List<String> inside = packages(engine.bubbleApps(user));
        if (!inside.contains(StarterApps.FDROID.pkg) && installStarter(user, StarterApps.FDROID, log)) {
            log.append(engine.allowInstalls(user, StarterApps.FDROID.pkg));
            store.addNotice(context.getString(R.string.notice_fdroid));
        }
        if (engine.bubble("bubble-status").contains("bubble_keyboard=none")) {
            if (installedOutside(StarterApps.GBOARD)) {
                // No network: the firewall denies every app not explicitly allowed, and it is
                // already up (applied at the start of this method) before Gboard is selected.
                log.append(engine.addApps(user, Collections.singleton(StarterApps.GBOARD)));
                log.append(engine.setKeyboard(user, StarterApps.GBOARD_IME));
                store.addNotice(context.getString(R.string.notice_keyboard_gboard));
            } else if (inside.contains(StarterApps.KEYBOARD.pkg) || installStarter(user, StarterApps.KEYBOARD, log)) {
                log.append(engine.setKeyboard(user, StarterApps.KEYBOARD_IME));
                store.addNotice(context.getString(R.string.notice_keyboard_heliboard));
            }
        }
        startersChecked = true;
        return log.toString();
    }

    private boolean installedOutside(String pkg) {
        try {
            context.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private boolean installStarter(int user, StarterApps.App app, StringBuilder log) {
        java.io.File apk = null;
        try {
            Progress.step(context, R.string.step_download, app.label);
            apk = StarterApps.download(context, app);
            String out;
            Progress.step(context, R.string.step_install, app.label);
            try (InputStream in = new java.io.FileInputStream(apk)) {
                out = engine.installApk(user, in, apk.length());
            }
            log.append(app.label).append(": ").append(out);
            return out.contains("Success");
        } catch (Exception e) {
            log.append(app.label).append(": ").append(e.getMessage()).append('\n');
            return false;
        } finally {
            if (apk != null) apk.delete();
        }
    }

    private static List<String> packages(List<String[]> apps) {
        List<String> out = new ArrayList<>();
        for (String[] app : apps) out.add(app[0]);
        return out;
    }

    // ---- apps inside the bubble -------------------------------------------

    /** Apps inside the bubble beyond its base, as {package, apk path}. */
    /** From the cache when fresh; otherwise read (and cache) through a session. */
    List<String[]> bubbleApps() throws Exception {
        List<String[]> cached = BubbleCache.apps();
        if (cached != null) return cached;
        int user = bubbleUserOrThrow();
        List<String[]> apps;
        if (ProfileBridge.available(context)) {
            if (ProfileBridge.locked(context, user)) return store.lastBubbleApps();
            apps = ProfileBridge.apps(context, user);
        } else apps = session(() -> engine.bubbleApps(user));
        BubbleCache.setApps(apps);
        store.setLastBubbleApps(apps);
        AppsWidget.refresh(context);
        return apps;
    }

    String addApps(Set<String> pkgs) throws Exception {
        BubbleCache.clear();
        int user = bubbleUserOrThrow();
        return session(() -> keepLog(engine.addApps(user, pkgs)));
    }

    String removeApps(Set<String> pkgs) throws Exception {
        BubbleCache.clear();
        int user = bubbleUserOrThrow();
        return session(() -> {
            String log = engine.removeApps(user, pkgs);
            boolean changed = false;
            for (boolean wifi : new boolean[]{true, false}) {
                Set<String> allowed = store.bubbleNet(wifi);
                if (allowed.removeAll(pkgs)) {
                    store.setBubbleNet(wifi, allowed);
                    changed = true;
                }
            }
            if (changed) log += engine.bubbleFirewall(user, store.bubbleTokens());
            return keepLog(log);
        });
    }

    /** Apply the bubble's firewall as saved: which apps have network on Wi-Fi and on mobile data. */
    String applyBubbleNetwork() throws Exception {
        int user = bubbleUserOrThrow();
        if (ProfileBridge.available(context)) return keepLog(ProfileBridge.applyNetwork(context, user));
        return session(() -> keepLog(engine.bubbleFirewall(user, store.bubbleTokens())));
    }

    /** Install a picked APK file into the bubble. */
    /** APKs in the bubble's Downloads, as {id, size, name}; null when the bubble is locked. */
    /**
     * The APKs in the bubble's Downloads as {id, size, name, local copy}: each is copied once into
     * Bubble's cache (its icon and name are inside the file), and installing then uses the copy.
     * Copies of files no longer there are deleted. Null when the bubble is locked.
     */
    List<String[]> bubbleDownloads() throws Exception {
        int user = bubbleUserOrThrow();
        return session(() -> {
            List<String[]> apks = engine.bubbleDownloads(user);
            if (apks == null) return null;
            java.io.File dir = new java.io.File(context.getCacheDir(), "downloads");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("no cache dir");
            List<String[]> out = new ArrayList<>();
            Set<String> keep = new TreeSet<>();
            for (String[] apk : apks) {
                java.io.File copy = new java.io.File(dir, apk[0] + "-" + apk[1] + ".apk");
                keep.add(copy.getName());
                if (copy.length() != Long.parseLong(apk[1])) {
                    Progress.step(context, R.string.step_copy, apk[2]);
                    engine.copyDownload(user, apk[0], copy);
                }
                out.add(new String[]{apk[0], apk[1], apk[2], copy.getPath()});
            }
            java.io.File[] old = dir.listFiles();
            if (old != null) for (java.io.File f : old) if (!keep.contains(f.getName())) f.delete();
            return out;
        });
    }

    /**
     * Show Android's unlock prompt for the bubble and wait (up to a minute) for the user to
     * confirm it. True once the bubble is unlocked.
     */
    boolean unlockBubble() throws Exception {
        int user = bubbleUserOrThrow();
        unlockCancelledAt = 0;
        unlockPromptShown = false;
        if (ProfileBridge.available(context)) {
            android.os.UserManager users = context.getSystemService(android.os.UserManager.class);
            android.os.UserHandle handle = ProfileBridge.handle(user);
            if (users.isQuietModeEnabled(handle)) {
                boolean now = users.requestQuietModeEnabled(false, handle);
                android.util.Log.i("Bubble", "unlock requested (no prompt needed: " + now + ")");
            }
            for (int i = 0; i < 60; i++) {
                if (!users.isQuietModeEnabled(handle) && users.isUserUnlocked(handle)) return true;
                // The prompt closed: cancelled if the bubble is still in quiet mode. Confirmed, it
                // leaves quiet mode at once but needs a few more seconds to finish unlocking.
                if (unlockCancelledAt > 0 && System.currentTimeMillis() - unlockCancelledAt > 3000
                        && users.isQuietModeEnabled(handle)) return false;
                // Seen on the Pixel: a request answered with no prompt at all. If Android's unlock
                // screen has not come up after 3 s (nothing covered Bubble), ask once more.
                if (i == 3 && !unlockPromptShown && users.isQuietModeEnabled(handle)) {
                    boolean now = users.requestQuietModeEnabled(false, handle);
                    android.util.Log.w("Bubble", "no unlock prompt after 3 s, asked again (no prompt needed: " + now + ")");
                }
                Thread.sleep(1000);
            }
            android.util.Log.w("Bubble", "bubble still locked after 60 s (prompt shown: " + unlockPromptShown + ")");
            return false;
        }
        return session(() -> {
            engine.unlockBubble(user);
            for (int i = 0; i < 60; i++) {
                Thread.sleep(1000);
                if (!engine.bubbleLocked(user)) return true;
                // The prompt was closed: a few more seconds in case it was a real unlock that
                // Android is still applying, then give up.
                if (unlockCancelledAt > 0 && System.currentTimeMillis() - unlockCancelledAt > 3000) return false;
            }
            return false;
        });
    }

    /** When the screen waiting for the unlock prompt came back to the front; 0 while waiting. */
    private static volatile long unlockCancelledAt;
    /** Android's unlock screen covered the waiting screen (it was asked for and shown). */
    private static volatile boolean unlockPromptShown;

    /** The waiting screen went to the background: Android's unlock screen is up. */
    static void unlockPromptOpened() {
        unlockPromptShown = true;
    }

    /** The unlock prompt is gone (cancelled, or confirmed): stop waiting for it soon. */
    static void unlockPromptClosed() {
        unlockCancelledAt = System.currentTimeMillis();
    }

    /** The bubble's file browser; when it is locked, Android's unlock prompt comes first. */
    String openBubbleFiles() throws Exception {
        int user = bubbleUserOrThrow();
        if (!ProfileBridge.available(context)) throw new IllegalStateException(context.getString(R.string.error_profile_setup));
        if (ProfileBridge.locked(context, user) && !unlockBubble()) return context.getString(R.string.grid_still_locked);
        ProfileBridge.files(context, user);
        return "";
    }

    /** The Meta space's file browser; a paused space is resumed from Android's Work tab, not here. */
    String openMetaFiles() throws Exception {
        int user = metaUserOrThrow();
        if (!ProfileBridge.available(context)) throw new IllegalStateException(context.getString(R.string.error_profile_setup));
        if (ProfileBridge.locked(context, user)) return context.getString(R.string.grid_meta_paused);
        ProfileBridge.files(context, user);
        return "";
    }

    /** Open an app of the bubble; when it is locked, Android's unlock prompt comes first. */
    String openInBubble(String pkg) throws Exception {
        return openInBubble(pkg, null);
    }

    /** The same; {@code unlocked} runs (off the main thread) right after an unlock, before the launch. */
    String openInBubble(String pkg, Runnable unlocked) throws Exception {
        int user = bubbleUserOrThrow();
        if (ProfileBridge.available(context)) {
            if (ProfileBridge.locked(context, user)) {
                if (!unlockBubble()) return context.getString(R.string.grid_still_locked);
                if (unlocked != null) unlocked.run();
            }
            Progress.step(context, R.string.step_launch);
            ProfileBridge.open(context, user, pkg);
            return "launched " + pkg + "\n";
        }
        quick = true;
        return session(() -> {
            if (engine.bubbleLocked(user)) {
                if (!unlockBubble()) return context.getString(R.string.grid_still_locked);
                if (unlocked != null) unlocked.run();
            }
            Progress.step(context, R.string.step_launch);
            return engine.bubbleLaunch(user, pkg);
        });
    }

    String installDownload(String[] apk) throws Exception {
        BubbleCache.clear();
        int user = bubbleUserOrThrow();
        return session(() -> {
            Progress.step(context, R.string.step_install, apk[2]);
            java.io.File copy = new java.io.File(apk[3]);
            if (copy.length() == Long.parseLong(apk[1])) {
                String out;
                try (InputStream in = new java.io.FileInputStream(copy)) {
                    out = keepLog(engine.installApk(user, in, copy.length()));
                }
                if (out.contains("Success")) copy.delete(); // a big APK should not stay in the cache
                return out;
            }
            return keepLog(engine.installDownload(user, apk[0], Long.parseLong(apk[1])));
        });
    }

    String installApk(Uri apk) throws Exception {
        BubbleCache.clear();
        int user = bubbleUserOrThrow();
        long size = -1;
        try (Cursor c = context.getContentResolver().query(apk, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) size = c.getLong(0);
        }
        if (size <= 0) throw new IllegalStateException(context.getString(R.string.error_apk_unreadable));
        long apkSize = size;
        return session(() -> {
            Progress.step(context, R.string.step_install, "APK");
            try (InputStream in = context.getContentResolver().openInputStream(apk)) {
                if (in == null) throw new IllegalStateException(context.getString(R.string.error_apk_unreadable));
                return keepLog(engine.installApk(user, in, apkSize));
            }
        });
    }

    private int bubbleUserOrThrow() {
        int user = store.bubbleUser();
        if (user < 0) throw new IllegalStateException(context.getString(R.string.error_no_bubble));
        return user;
    }

    private String keepLog(String log) {
        store.setLog(log);
        return log;
    }

    /**
     * Move a photo of another space into Work's Pictures/Bubble, so Work apps (Instagram) show it in
     * their gallery. The file is moved, not copied; it leaves its space. Needs the engine (a session).
     */
    String moveToWork(int fromUser, String ref) throws Exception {
        if (store.metaUser() < 0) throw new IllegalStateException(context.getString(R.string.move_no_work));
        return session(() -> {
            String log = engine.run("space-move " + fromUser + " " + ref);
            if (log.contains("bubble-shell:")) throw new IllegalStateException(log.substring(log.indexOf("bubble-shell:")));
            return keepLog(log);
        });
    }
}
