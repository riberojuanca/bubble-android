package org.bubble.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Set;
import java.util.TreeSet;

/** The app's own settings: paired or not, the host block list, and the last engine output. */
final class Store {

    /** Pre-selected on first run: the apps the owner blocked with NetPatch. */
    static final String[] SUGGESTED = {
            "com.google.android.apps.work.oobconfig",      // Device Setup
            "com.google.android.devicelockcontroller",    // DeviceLock Controller (Pixel)
            "com.android.devicelockcontroller",           // DeviceLock Controller (AOSP name)
            "com.google.android.euicc",                   // SIM Manager
    };

    private final SharedPreferences prefs;

    Store(Context context) {
        prefs = context.getSharedPreferences("bubble", Context.MODE_PRIVATE);
    }

    /** The user turned on "Bubble photos in Wallpaper & style": if Android turns it off, Bubble says so. */
    boolean assistWanted() { return prefs.getBoolean("assist_wanted", false); }

    void setAssistWanted(boolean on) { prefs.edit().putBoolean("assist_wanted", on).apply(); }

    /** The app an icon widget shows, as "space:package"; null when that widget is not Bubble's. */
    String iconWidget(int id) { return prefs.getString("icon_widget_" + id, null); }

    /** Remember (or, with null, forget) the app an icon widget shows. */
    void setIconWidget(int id, String app) {
        if (app == null) prefs.edit().remove("icon_widget_" + id).apply();
        else prefs.edit().putString("icon_widget_" + id, app).apply();
    }

    /** The launcher's icon shape as the user chose it in Bubble (IconShape); the rounded square by default. */
    String iconShape() { return prefs.getString("icon_shape", IconShape.SQUARE); }

    void setIconShape(String shape) { prefs.edit().putString("icon_shape", shape).apply(); }

    boolean paired() { return prefs.getBoolean("paired", false); }

    void setPaired(boolean v) { prefs.edit().putBoolean("paired", v).apply(); }

    /** Whether Android's notification prompt was shown once (after a denial it may stop showing). */
    boolean notificationsAsked() { return prefs.getBoolean("notifications_asked", false); }

    void setNotificationsAsked() { prefs.edit().putBoolean("notifications_asked", true).apply(); }

    /** What the user wants blocked (the checkboxes). */
    Set<String> wanted() {
        if (!prefs.contains("wanted")) return new TreeSet<>(java.util.Arrays.asList(SUGGESTED));
        return new TreeSet<>(prefs.getStringSet("wanted", new TreeSet<>()));
    }

    void setWanted(Set<String> v) { prefs.edit().putStringSet("wanted", new TreeSet<>(v)).apply(); }

    /** What is actually applied on the phone. */
    Set<String> applied() { return new TreeSet<>(prefs.getStringSet("applied", new TreeSet<>())); }

    void setApplied(Set<String> v) { prefs.edit().putStringSet("applied", new TreeSet<>(v)).apply(); }

    /**
     * Apps inside the bubble with network on Wi-Fi ({@code wifi}) or on mobile data; every other
     * one has none there (the bubble's own firewall denies by default). Until the user chooses per
     * network, both start from the single list of before: F-Droid by default, so it can install.
     */
    Set<String> bubbleNet(boolean wifi) {
        String key = wifi ? "bubble_wifi" : "bubble_mobile";
        if (prefs.contains(key)) return new TreeSet<>(prefs.getStringSet(key, new TreeSet<>()));
        if (!prefs.contains("bubble_allowed")) return new TreeSet<>(java.util.Collections.singleton(StarterApps.FDROID.pkg));
        return new TreeSet<>(prefs.getStringSet("bubble_allowed", new TreeSet<>()));
    }

    void setBubbleNet(boolean wifi, Set<String> v) {
        prefs.edit().putStringSet(wifi ? "bubble_wifi" : "bubble_mobile", new TreeSet<>(v)).apply();
    }

    /** The same for the Meta space (its firewall also denies by default). */
    Set<String> metaNet(boolean wifi) {
        String key = wifi ? "meta_wifi" : "meta_mobile";
        if (prefs.contains(key)) return new TreeSet<>(prefs.getStringSet(key, new TreeSet<>()));
        return new TreeSet<>(prefs.getStringSet("meta_allowed", new TreeSet<>()));
    }

    void setMetaNet(boolean wifi, Set<String> v) {
        prefs.edit().putStringSet(wifi ? "meta_wifi" : "meta_mobile", new TreeSet<>(v)).apply();
    }

    /** Apps with network on at least one of the two. */
    Set<String> bubbleAnyNet() {
        Set<String> any = bubbleNet(true);
        any.addAll(bubbleNet(false));
        return any;
    }

    Set<String> bubbleTokens() { return tokens(bubbleNet(true), bubbleNet(false)); }

    Set<String> metaTokens() { return tokens(metaNet(true), metaNet(false)); }

    /**
     * The list the firewall VPN reads (BubbleVpnService): "pkg" with network on both, "pkg@wifi"
     * or "pkg@mobile" on one of them.
     */
    static Set<String> tokens(Set<String> wifi, Set<String> mobile) {
        Set<String> out = new TreeSet<>();
        for (String p : wifi) out.add(mobile.contains(p) ? p : p + "@wifi");
        for (String p : mobile) if (!wifi.contains(p)) out.add(p + "@mobile");
        return out;
    }

    /**
     * The bubble's apps as last read, {package, apk path} each: the widget and Bubble Apps show them
     * without connecting (only the engine can read the bubble).
     */
    java.util.List<String[]> lastBubbleApps() {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        for (String line : prefs.getString("last_bubble_apps", "").split("\n")) {
            int tab = line.indexOf('\t');
            if (tab > 0) out.add(new String[]{line.substring(0, tab), line.substring(tab + 1)});
        }
        return out;
    }

    void setLastBubbleApps(java.util.List<String[]> apps) {
        StringBuilder sb = new StringBuilder();
        for (String[] app : apps) sb.append(app[0]).append('\t').append(app[1]).append('\n');
        prefs.edit().putString("last_bubble_apps", sb.toString()).apply();
    }

    /** The launcher's home-screen icon size in px, as the engine last read it; 0 until then. */
    int launcherIconPx() { return prefs.getInt("launcher_icon_px", 0); }

    void setLauncherIconPx(int v) { prefs.edit().putInt("launcher_icon_px", v).apply(); }

    /** Google services in the bubble (all, or enabled ones), as last read: shown while it is locked. */
    int lastBubbleGoogle(boolean enabled) {
        return prefs.getInt(enabled ? "last_bubble_google_active" : "last_bubble_google", 0);
    }

    void setLastBubbleGoogle(int all, int enabled) {
        prefs.edit().putInt("last_bubble_google", all).putInt("last_bubble_google_active", enabled).apply();
    }

    /** Whether this Bubble has given the current Meta space its firewall. */
    boolean metaFirewallSet() { return prefs.getBoolean("meta_firewall_set", false); }

    void setMetaFirewallSet(boolean v) { prefs.edit().putBoolean("meta_firewall_set", v).apply(); }

    /** Whether the current Meta space's own Settings is off (see BubbleEngine.metaSettingsOff). */
    boolean metaSettingsOff() { return prefs.getBoolean("meta_settings_off", false); }

    void setMetaSettingsOff(boolean v) { prefs.edit().putBoolean("meta_settings_off", v).apply(); }

    /** User id of the Meta space (a work profile); -1 when there is none. */
    int metaUser() { return prefs.getInt("meta_user", -1); }

    void setMetaUser(int v) { prefs.edit().putInt("meta_user", v).apply(); }

    static final int GUARD_OFF = 0, GUARD_SESSION = 1, GUARD_ALWAYS = 2;

    /** How the guard was left by the last session (see Controller.session). */
    int guard() { return prefs.getInt("guard", GUARD_OFF); }

    void setGuard(int v) { prefs.edit().putInt("guard", v).apply(); }

    /** The bubble stays open when the screen goes off, so its apps keep receiving and notifying. */
    boolean keepBubbleOpen() { return prefs.getBoolean("keep_bubble_open", false); }

    void setKeepBubbleOpen(boolean v) { prefs.edit().putBoolean("keep_bubble_open", v).apply(); }

    boolean noticeListenerAllowed() { return prefs.getBoolean("notice_listener", false); }

    void setNoticeListenerAllowed() { prefs.edit().putBoolean("notice_listener", true).apply(); }

    /** User id of the bubble's Private Space; -1 until created or adopted. */
    int bubbleUser() { return prefs.getInt("bubble_user", -1); }

    void setBubbleUser(int v) { prefs.edit().putInt("bubble_user", v).apply(); }

    /** A message for the user from a background session (something was reinstalled); shown once. */
    String takeNotice() {
        String v = prefs.getString("notice", "");
        prefs.edit().remove("notice").apply();
        return v;
    }

    void addNotice(String v) {
        String old = prefs.getString("notice", "");
        prefs.edit().putString("notice", old.isEmpty() ? v : old + "\n" + v).apply();
    }

    /** Whether the phone's firewall is on (the list is kept either way). */
    boolean phoneFirewallOn() { return prefs.getBoolean("phone_firewall_on", !applied().isEmpty()); }

    void setPhoneFirewallOn(boolean v) { prefs.edit().putBoolean("phone_firewall_on", v).apply(); }

    String log() { return prefs.getString("log", ""); }

    void setLog(String v) { prefs.edit().putString("log", v).apply(); }
}
