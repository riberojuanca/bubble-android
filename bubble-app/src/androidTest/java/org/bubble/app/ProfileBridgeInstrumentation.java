package org.bubble.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** Explicit on-device regression probe; restores the user's network choices even on failure. */
public final class ProfileBridgeInstrumentation extends Instrumentation {
    private Bundle args;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        args = arguments == null ? new Bundle() : arguments;
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context c = getTargetContext();
            check(ProfileBridge.available(c), "Local profile permissions missing");
            check(!WirelessDebugging.hasWifi(c), "Probe requires Wi-Fi disconnected");
            Store store = new Store(c);
            int user = Integer.parseInt(args.getString("user", String.valueOf(store.metaUser())));
            boolean meta = user == store.metaUser();
            check(meta || user == store.bubbleUser(), "Unknown profile");
            check(!ProfileBridge.locked(c, user), "Profile is locked");
            check(!ProfileBridge.apps(c, user).isEmpty(), "No apps received through Binder");
            ProfileBridge.call(c, user, ProfileBridge.STATUS, null);
            String mode = args.getString("mode", "read");
            if ("network".equals(mode)) network(c, store, user, meta);
            if ("launch".equals(mode)) {
                String pkg = args.getString("package");
                check(pkg != null, "Missing package");
                Activity host = startActivitySync(new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                try {
                    ProfileBridge.open(c, user, pkg);
                    Thread.sleep(1500);
                    String activities = shell("dumpsys activity activities");
                    check(activities.contains("u" + user + " " + pkg + "/"), "Target activity was not launched in its profile");
                } finally { runOnMainSync(host::finish); }
            }
            result.putString("stream", "PASS: local " + mode + " without Wi-Fi, profile " + user + "\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable e) {
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(e));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void network(Context c, Store store, int user, boolean meta) throws Exception {
        String pkg = args.getString("package", meta ? "com.instagram.android" : "org.fdroid.fdroid");
        check(ProfileBridge.apps(c, user).stream().anyMatch(app -> pkg.equals(app[0])), "Test app is missing");
        Set<String> oldWifi = meta ? store.metaNet(true) : store.bubbleNet(true);
        Set<String> oldMobile = meta ? store.metaNet(false) : store.bubbleNet(false);
        try {
            for (boolean enabled : new boolean[]{false, true, false}) {
                Set<String> wifi = new HashSet<>(oldWifi), mobile = new HashSet<>(oldMobile);
                wifi.remove(pkg);
                if (enabled) mobile.add(pkg); else mobile.remove(pkg);
                if (meta) { store.setMetaNet(true, wifi); store.setMetaNet(false, mobile); }
                else { store.setBubbleNet(true, wifi); store.setBubbleNet(false, mobile); }
                String applied = ProfileBridge.applyNetwork(c, user);
                check(!applied.contains("locked"), "Profile locked while applying");
                String prefs = shell("run-as org.bubble.app --user " + user + " cat shared_prefs/bubble-vpn.xml");
                String allowed = prefs.split("<string name=\"allowed\">", 2)[1].split("</string>", 2)[0];
                boolean received = java.util.Arrays.asList(allowed.split(",")).contains(pkg + "@mobile");
                check(received == enabled, "VPN received the wrong mobile permission");
                check(!java.util.Arrays.asList(allowed.split(",")).contains(pkg), "Wi-Fi permission unexpectedly enabled");
                Thread.sleep(250);
                verifyVpnUid(user, pkg, !enabled);
                if (!enabled) {
                    // A delayed shell/guard update must not undo a newer local choice.
                    shell("am broadcast --user " + user + " -n org.bubble.app/.FirewallReceiver --es allowed " + pkg);
                    Thread.sleep(500);
                    verifyVpnUid(user, pkg, true);
                }
            }
        } finally {
            if (meta) { store.setMetaNet(true, oldWifi); store.setMetaNet(false, oldMobile); }
            else { store.setBubbleNet(true, oldWifi); store.setBubbleNet(false, oldMobile); }
            ProfileBridge.applyNetwork(c, user);
        }
    }

    private void verifyVpnUid(int user, String pkg, boolean expectedBlocked) throws Exception {
        String packageInfo = shell("cmd package list packages -U --user " + user + " " + pkg);
        java.util.regex.Matcher uidMatch = java.util.regex.Pattern.compile("uid:(\\d+)").matcher(packageInfo);
        check(uidMatch.find(), "Could not read app UID");
        int uid = Integer.parseInt(uidMatch.group(1));
        String dump = shell("dumpsys vpn_management");
        int start = dump.indexOf("\n  " + user + ": org.bubble.app");
        check(start >= 0, "Profile VPN is missing");
        String section = dump.substring(start);
        int end = section.indexOf("\n  ", 4);
        if (end > 0) section = section.substring(0, end);
        java.util.regex.Matcher ranges = java.util.regex.Pattern.compile("(\\d+)-(\\d+)").matcher(section);
        boolean blocked = false;
        while (ranges.find()) {
            long low = Long.parseLong(ranges.group(1)), high = Long.parseLong(ranges.group(2));
            if (uid >= low && uid <= high) blocked = true;
        }
        check(blocked == expectedBlocked, "VPN UID routing mismatch for " + pkg + ": blocked=" + blocked);
    }

    private String shell(String command) throws Exception {
        ParcelFileDescriptor fd = getUiAutomation().executeShellCommand(command);
        try (ParcelFileDescriptor.AutoCloseInputStream in = new ParcelFileDescriptor.AutoCloseInputStream(fd)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
