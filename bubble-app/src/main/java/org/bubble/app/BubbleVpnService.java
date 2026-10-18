package org.bubble.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The bubble's firewall, deny by default. Runs in the copy of Bubble that lives inside the bubble
 * (VPNs are per profile, so it only sees the bubble's apps). Every app of the bubble is routed into
 * a tunnel whose packets are read and dropped, except the allowed ones, which keep their normal
 * connection: an app installed later has no network until the user allows it. No packet is
 * inspected or forwarded. Each app is allowed on Wi-Fi, on mobile data or both: the tunnel is
 * rebuilt with the right list whenever the phone's network changes between them.
 *
 * <p>The engine (shell) grants the VPN consent through appops (no dialog) and makes this the
 * profile's always-on VPN, so Android starts it every time the bubble is unlocked; the allowed list
 * is kept here for that.
 */
public final class BubbleVpnService extends VpnService {

    /** Comma-separated: "pkg" has network on both, "pkg@wifi" or "pkg@mobile" on one (Store.tokens). */
    static final String EXTRA_ALLOWED = "allowed";
    static final String EXTRA_LOCAL = "local";
    static final String EXTRA_RESULT = "result";
    private static final String PREFS = "bubble-vpn";
    private static final String CHANNEL = "bubble-firewall";

    private ParcelFileDescriptor tun;
    private List<String> appliedAllowed;
    private android.net.ConnectivityManager.NetworkCallback watcher;
    private android.net.Network currentNetwork;
    /** Null until Android reports the transport: only permissions shared by both lists apply. */
    private Boolean onWifi;
    private static BubbleVpnService active;
    private long rulesGeneration;

    @Override public void onCreate() {
        super.onCreate();
        active = this;
    }

    /** Called on main by the local profile service, which has already authenticated the caller. */
    static boolean applyLocalRules(String rules) {
        if (active == null) return false;
        active.saveRules(rules);
        active.watchNetwork();
        active.applyFor(active.onWifi);
        if (active.tun == null) throw new IllegalStateException("Profile VPN could not start");
        return true;
    }

    private void saveRules(String rules) {
        rulesGeneration++;
        if (!prefs(this).edit().putString(EXTRA_ALLOWED, rules).putBoolean(CONFIGURED, true).commit()) {
            throw new IllegalStateException("Could not save firewall rules");
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        // Started as a foreground service (from the shell or at profile start); once the tunnel is
        // up the system's own binding keeps it alive, so the notification goes away again.
        startForeground(3, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        // Android starts this always-on VPN at every unlock: the bubble's smart lock lives here.
        SmartLock.start(this);
        boolean local = intent != null && intent.getBooleanExtra(EXTRA_LOCAL, false);
        android.os.ResultReceiver result = intent == null ? null
                : intent.getParcelableExtra(EXTRA_RESULT, android.os.ResultReceiver.class);
        try {
            if (local || !ProfileBridge.available(this)) {
                if (intent != null && intent.hasExtra(EXTRA_ALLOWED)) saveRules(intent.getStringExtra(EXTRA_ALLOWED));
                if (configured(this)) {
                    watchNetwork();
                    applyFor(onWifi);
                } else stopSelf();
                if (result != null) result.send(tun != null ? 0 : 1, null);
            } else {
                // The main copy owns the choices, including edits made while this profile was
                // locked. Never replay an older shell-side file over a newer local choice.
                if (tun == null) apply(java.util.Collections.emptyList());
                long generation = ++rulesGeneration;
                new Thread(() -> {
                    // Right after an unlock the main copy may still be starting: a few tries, then
                    // the user is told, since every app of this profile stays without network.
                    for (long delay : new long[]{0, 3000, 10000, 30000}) {
                        try {
                            Thread.sleep(delay);
                            if (active != this || generation != rulesGeneration) return;
                            String rules = ProfileBridge.rules(this);
                            ProfileBridge.MAIN.post(() -> {
                                if (active != this || generation != rulesGeneration) return;
                                try {
                                    applyLocalRules(rules);
                                    rulesMissing(false);
                                } catch (Exception e) {
                                    android.util.Log.e("Bubble", "Could not apply profile rules", e);
                                    rulesMissing(true);
                                }
                            });
                            return;
                        } catch (InterruptedException e) {
                            return;
                        } catch (Exception e) {
                            android.util.Log.w("Bubble", "Could not read main profile rules yet", e);
                        }
                    }
                    ProfileBridge.MAIN.post(() -> rulesMissing(true));
                }, "bubble-vpn-rules").start();
            }
        } catch (Exception e) {
            android.util.Log.e("Bubble", "Could not apply firewall rules", e);
            if (result != null) result.send(1, null);
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        return START_STICKY;
    }

    private static final String CONFIGURED = "configured";

    /** Whether the engine ever set the firewall up here (otherwise there is nothing to run). */
    static boolean configured(Context context) {
        return prefs(context).getBoolean(CONFIGURED, false);
    }

    /** The apps with network on Wi-Fi ({@code wifi}) or on mobile data, from the saved list. */
    static List<String> allowed(Context context, boolean wifi) {
        String csv = prefs(context).getString(EXTRA_ALLOWED, "");
        List<String> out = new ArrayList<>();
        for (String token : Arrays.asList(csv.split(","))) {
            token = token.trim();
            if (token.isEmpty()) continue;
            int at = token.indexOf('@');
            if (at < 0) {
                out.add(token);
            } else if (token.substring(at + 1).equals(wifi ? "wifi" : "mobile")) {
                out.add(token.substring(0, at));
            }
        }
        return out;
    }

    /**
     * Follow the network apps would use (the best one that is not a VPN): mobile data means the
     * mobile list, anything else (Wi-Fi, Ethernet) the Wi-Fi list.
     */
    private void watchNetwork() {
        if (watcher != null) return;
        android.net.ConnectivityManager cm = getSystemService(android.net.ConnectivityManager.class);
        watcher = new android.net.ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(android.net.Network network) {
                currentNetwork = network;
                onWifi = null;
                applyFor(null);
            }

            @Override public void onCapabilitiesChanged(android.net.Network network, android.net.NetworkCapabilities caps) {
                if (!network.equals(currentNetwork)) return;
                onWifi = !caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR);
                applyFor(onWifi);
            }

            @Override public void onLost(android.net.Network network) {
                if (!network.equals(currentNetwork)) return;
                currentNetwork = null;
                onWifi = null;
                applyFor(null);
            }
        };
        cm.registerBestMatchingNetworkCallback(new android.net.NetworkRequest.Builder()
                .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build(), watcher, new android.os.Handler(android.os.Looper.getMainLooper()));
    }

    /** Rebuild only when the effective permissions change. Unknown transport uses their intersection. */
    private synchronized void applyFor(Boolean wifi) {
        if (!configured(this)) return;
        List<String> pkgs = allowed(this, wifi == null || wifi);
        if (wifi == null) pkgs.retainAll(allowed(this, false));
        if (tun != null && pkgs.equals(appliedAllowed)) return;
        if (apply(pkgs)) appliedAllowed = pkgs;
    }

    private synchronized boolean apply(List<String> pkgs) {
        Builder b = new Builder()
                .setSession("Bubble firewall")
                .addAddress("10.111.222.1", 32)
                .addRoute("0.0.0.0", 0)
                .addAddress("fd11:2233:4455::1", 128)
                .addRoute("::", 0)
                .setBlocking(true);
        for (String pkg : pkgs) {
            try {
                b.addDisallowedApplication(pkg); // outside the tunnel: normal network
            } catch (PackageManager.NameNotFoundException ignored) {
                // Not installed in the bubble (yet); applied next time.
            }
        }
        // With ACTIVATE_VPN pre-granted by the engine (appops), prepare() takes the VPN slot without
        // the consent dialog, which the minimal bubble does not even have (no vpndialogs).
        if (prepare(this) != null) {
            stopSelf();
            return false;
        }
        // Android replaces the interface when establish succeeds. Keep the previous tunnel until
        // then, so rebuilding does not deliberately remove the firewall between interfaces.
        ParcelFileDescriptor next = b.establish(); // null if the VPN consent is missing
        if (next == null) {
            stopSelf();
            return false;
        }
        close();
        tun = next;
        ParcelFileDescriptor fd = tun;
        Thread drain = new Thread(() -> {
            byte[] packet = new byte[32 * 1024];
            try (FileInputStream in = new ParcelFileDescriptor.AutoCloseInputStream(fd)) {
                while (in.read(packet) >= 0) {
                    // Dropped: apps without permission get no answer, ever.
                }
            } catch (Exception ignored) {
                // Tunnel closed.
            }
        }, "bubble-vpn-drain");
        drain.setDaemon(true);
        drain.start();
        return true;
    }

    private synchronized void close() {
        if (tun == null) return;
        try {
            tun.close();
        } catch (Exception ignored) {
        }
        tun = null;
    }

    @Override public void onRevoke() {
        close();
        stopSelf();
    }

    @Override public void onDestroy() {
        if (active == this) active = null;
        if (watcher != null) {
            getSystemService(android.net.ConnectivityManager.class).unregisterNetworkCallback(watcher);
            watcher = null;
        }
        close();
        super.onDestroy();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private static final String ALERTS = "bubble-alerts";
    private static final int RULES_MISSING = 4;

    /**
     * Fail closed, but not silently: without the list every app of this profile stays without
     * network, so a notification says so until a list arrives.
     */
    private void rulesMissing(boolean missing) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (!missing) {
            nm.cancel(RULES_MISSING);
            return;
        }
        nm.createNotificationChannel(new NotificationChannel(ALERTS, getString(R.string.alerts_channel),
                NotificationManager.IMPORTANCE_DEFAULT));
        nm.notify(RULES_MISSING, new Notification.Builder(this, ALERTS)
                .setContentTitle(getString(R.string.firewall_rules_missing_title))
                .setContentText(getString(R.string.firewall_rules_missing_body))
                .setStyle(new Notification.BigTextStyle().bigText(getString(R.string.firewall_rules_missing_body)))
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .build());
    }

    private Notification notification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Bubble", NotificationManager.IMPORTANCE_MIN));
        return new Notification.Builder(this, CHANNEL)
                .setContentTitle("Bubble")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .build();
    }
}
