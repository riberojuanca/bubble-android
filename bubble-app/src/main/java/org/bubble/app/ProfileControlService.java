package org.bubble.app;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.ResultReceiver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Exposes only Bubble's own profile operations, authenticated by signature and calling app ID. */
public final class ProfileControlService extends Service {
    private final Messenger messenger = new Messenger(new Handler(Looper.getMainLooper(), this::receive));

    @Override public IBinder onBind(Intent intent) { return messenger.getBinder(); }

    private boolean receive(Message request) {
        if (request.replyTo == null) return true;
        int sender = request.sendingUid;
        int ownUser = Process.myUid() / 100000;
        Messenger reply = request.replyTo;
        if (sender < 0 || sender % 100000 != Process.myUid() % 100000) {
            reply(reply, ProfileBridge.error("Caller is not Bubble"));
            return true;
        }
        try {
            Bundle out = new Bundle();
            if (ownUser == 0) {
                Store store = new Store(this);
                int profile = sender / 100000;
                if (request.what != ProfileBridge.RULES || profile <= 0
                        || (profile != store.bubbleUser() && profile != store.metaUser())) {
                    throw new SecurityException("Unknown profile request");
                }
                out.putString(ProfileBridge.ALLOWED, String.join(",", profile == store.bubbleUser()
                        ? store.bubbleTokens() : store.metaTokens()));
            } else {
                if (sender / 100000 != 0) throw new SecurityException("Only the main copy controls a profile");
                switch (request.what) {
                    case ProfileBridge.APPS:
                        listApps(out);
                        break;
                    case ProfileBridge.OPEN:
                        String pkg = request.getData().getString(ProfileBridge.PACKAGE);
                        Intent launch = pkg == null ? null : getPackageManager().getLaunchIntentForPackage(pkg);
                        if (launch == null) throw new IllegalArgumentException(getString(R.string.error_profile_app_missing));
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        out.putParcelable("launch", PendingIntent.getActivity(this, 0, launch,
                                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
                        break;
                    case ProfileBridge.NETWORK:
                        // Pull from the main copy asynchronously: never block either Binder thread.
                        new Thread(() -> {
                            try {
                                String rules = ProfileBridge.rules(this);
                                ProfileBridge.MAIN.post(() -> applyNetwork(rules, reply));
                            } catch (Exception e) { reply(reply, ProfileBridge.error(e.getMessage())); }
                        }, "bubble-profile-rules").start();
                        return true;
                    case ProfileBridge.STATUS:
                        profileStatus(out);
                        break;
                    case ProfileBridge.PHOTO_FOLDERS:
                    case ProfileBridge.PHOTOS:
                    case ProfileBridge.PHOTO_THUMB:
                    case ProfileBridge.PHOTO_OPEN:
                        // Media queries and thumbnails read storage: off the main thread.
                        int what = request.what;
                        Bundle args = new Bundle(request.getData());
                        new Thread(() -> {
                            try {
                                Bundle photos = BubblePhotos.serve(this, what, args);
                                reply(reply, photos);
                                android.os.ParcelFileDescriptor sent =
                                        photos.getParcelable(ProfileBridge.FILE, android.os.ParcelFileDescriptor.class);
                                if (sent != null) sent.close();
                            } catch (Exception e) { reply(reply, ProfileBridge.error(e.getMessage())); }
                        }, "bubble-profile-photos").start();
                        return true;
                    default:
                        throw new IllegalArgumentException("Unknown profile operation");
                }
            }
            reply(reply, out);
        } catch (Exception e) { reply(reply, ProfileBridge.error(e.getMessage())); }
        return true;
    }

    private void applyNetwork(String rules, Messenger reply) {
        try {
            if (!BubbleVpnService.configured(this)) throw new IllegalStateException(getString(R.string.error_profile_setup));
            if (BubbleVpnService.applyLocalRules(rules)) {
                reply(reply, new Bundle());
            } else {
                Intent start = new Intent(this, BubbleVpnService.class)
                        .putExtra(BubbleVpnService.EXTRA_ALLOWED, rules)
                        .putExtra(BubbleVpnService.EXTRA_LOCAL, true)
                        .putExtra(BubbleVpnService.EXTRA_RESULT, new ResultReceiver(ProfileBridge.MAIN) {
                            @Override protected void onReceiveResult(int code, Bundle data) {
                                reply(reply, code == 0 ? new Bundle() : ProfileBridge.error(getString(R.string.error_profile_unavailable)));
                            }
                        });
                startForegroundService(start);
            }
        } catch (Exception e) { reply(reply, ProfileBridge.error(e.getMessage())); }
    }

    // Same base packages excluded by the shell engine, with the current profile's Bubble copy.
    private static final Set<String> BASE = new HashSet<>(Arrays.asList(
            "android", "com.android.systemui", "com.android.providers.settings", "com.google.android.permissioncontroller",
            "com.android.server.telecom", "com.google.android.providers.media.module", "com.android.providers.userdictionary",
            "com.android.settings", "com.android.permissioncontroller", "com.android.providers.media.module",
            "com.google.android.webview", "com.android.webview", "com.google.android.packageinstaller",
            "com.android.packageinstaller", "com.google.android.documentsui", "com.android.documentsui", "com.android.intentresolver"));

    private void listApps(Bundle out) {
        ArrayList<String> packages = new ArrayList<>(), paths = new ArrayList<>();
        for (ApplicationInfo info : getPackageManager().getInstalledApplications(0)) {
            if (BASE.contains(info.packageName) || info.packageName.equals(getPackageName())
                    || info.packageName.equals("com.google.android.gms") || info.packageName.equals("com.google.android.gsf")
                    || info.packageName.equals("com.android.vending")
                    || (info.flags & ApplicationInfo.FLAG_HAS_CODE) == 0) continue;
            packages.add(info.packageName);
            paths.add(info.sourceDir);
        }
        out.putStringArrayList("packages", packages);
        out.putStringArrayList("paths", paths);
    }

    private void profileStatus(Bundle out) {
        int installed = 0, enabled = 0;
        out.putString("play_services", "absent");
        for (ApplicationInfo info : getPackageManager().getInstalledApplications(0)) {
            boolean google = false;
            for (String name : GOOGLE_SERVICES) {
                if (info.packageName.equals(name) || info.packageName.startsWith(name + ".")) google = true;
            }
            if (!google) continue;
            installed++;
            if (info.enabled) enabled++;
            if (info.packageName.equals("com.google.android.gms")) out.putString("play_services", info.enabled ? "enabled" : "disabled");
        }
        out.putInt("google_services", installed);
        out.putInt("google_enabled", enabled);
    }

    private static final String[] GOOGLE_SERVICES = {
            "com.google.android.gms", "com.google.android.gsf", "com.android.vending",
            "com.google.android.syncadapters", "com.google.android.partnersetup", "com.google.android.setupwizard",
            "com.google.android.backuptransport", "com.google.android.feedback", "com.google.android.configupdater",
            "com.google.android.onetimeinitializer", "com.google.android.apps.restore"
    };

    private static void reply(Messenger target, Bundle data) {
        try {
            Message response = Message.obtain();
            response.setData(data);
            target.send(response);
        } catch (android.os.RemoteException ignored) {
            // The requesting screen went away; no command is retried implicitly.
        }
    }
}
