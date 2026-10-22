package org.bubble.app;

import android.content.Context;
import android.provider.Settings;

/**
 * The wireless-debugging switch, flipped by Bubble itself: the engine grants this app
 * WRITE_SECURE_SETTINGS once, at pairing. Bubble turns it on only while it works, so the phone does
 * not carry it (and its notifications) all day. The pairing itself is not lost when it is off.
 */
final class WirelessDebugging {

    private static final String SETTING = "adb_wifi_enabled"; // Settings.Global.ADB_WIFI_ENABLED (hidden)

    private static int users;
    private static boolean turnedOn;

    private WirelessDebugging() {}

    /**
     * Sessions overlap (the status check on opening and an action, for example), so the switch is
     * shared: the first session turns it on if it was off, and only the last one to leave turns it
     * back off.
     */
    static synchronized void acquire(Context context) {
        if (users++ == 0) {
            recover(context);
            turnedOn = !isOn(context) && set(context, true);
            // Remembered on disk: if the process dies mid-session, the next start turns it off.
            marker(context).edit().putBoolean(MARKER, turnedOn).commit();
        }
    }

    static synchronized void release(Context context) {
        if (--users == 0 && turnedOn) {
            set(context, false);
            turnedOn = false;
            marker(context).edit().putBoolean(MARKER, false).commit();
        }
    }

    /** Turn the switch back on if Android turned it off while Bubble still holds it. */
    static synchronized void reassert(Context context) {
        if (users > 0 && !isOn(context) && set(context, true)) {
            turnedOn = true;
            marker(context).edit().putBoolean(MARKER, true).commit();
        }
    }

    /** Whether a Bubble session is running in this process (its connection is Bubble's own). */
    static synchronized boolean inSession() {
        return users > 0;
    }

    /** Turn the switch off if a previous process turned it on and died before releasing it. */
    static synchronized void recover(Context context) {
        if (users <= 1 && !turnedOn && marker(context).getBoolean(MARKER, false)) {
            set(context, false);
            marker(context).edit().putBoolean(MARKER, false).commit();
        }
    }

    private static final String MARKER = "debugging_on_by_bubble";

    private static android.content.SharedPreferences marker(Context context) {
        return context.getSharedPreferences("bubble", Context.MODE_PRIVATE);
    }

    static boolean isOn(Context context) {
        return Settings.Global.getInt(context.getContentResolver(), SETTING, 0) == 1;
    }

    /** The setup channel needs an associated Wi-Fi network, even when that network has no internet. */
    static boolean hasWifi(Context context) {
        android.net.ConnectivityManager cm = context.getSystemService(android.net.ConnectivityManager.class);
        for (android.net.Network network : cm.getAllNetworks()) {
            android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            if (caps != null && caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                    && !caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) return true;
        }
        return false;
    }

    /** Whether Bubble holds the permission to flip the switch (granted at pairing). */
    static boolean canSwitch(Context context) {
        return context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /** False when the permission is not granted yet (before the first pairing). */
    static boolean set(Context context, boolean on) {
        try {
            return Settings.Global.putInt(context.getContentResolver(), SETTING, on ? 1 : 0);
        } catch (SecurityException e) {
            return false;
        }
    }
}
