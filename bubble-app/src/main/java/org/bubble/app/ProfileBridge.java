package org.bubble.app;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Local, signature-protected Binder calls between Bubble's copies. No network transport. */
final class ProfileBridge {
    static final int APPS = 1, OPEN = 2, NETWORK = 3, RULES = 4, STATUS = 5;
    static final String ERROR = "error", ALLOWED = "allowed", PACKAGE = "package";
    static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ProfileBridge() {}

    static boolean available(Context c) {
        return c.checkSelfPermission("android.permission.INTERACT_ACROSS_USERS") == PackageManager.PERMISSION_GRANTED
                && c.checkSelfPermission("android.permission.MODIFY_QUIET_MODE") == PackageManager.PERMISSION_GRANTED;
    }

    static UserHandle handle(int user) {
        if (user < 0 || user > 20000) throw new IllegalArgumentException("Invalid profile");
        return UserHandle.getUserHandleForUid(user * 100000);
    }

    static boolean locked(Context c, int user) {
        return c.getSystemService(UserManager.class).isQuietModeEnabled(handle(user));
    }

    /** Only called by a worker. Reply handlers stay on main, so Binder cannot deadlock the UI. */
    static Bundle call(Context c, int user, int operation, Bundle args) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("Profile call on main thread");
        int ownUser = Process.myUid() / 100000;
        Store store = new Store(c);
        boolean valid = ownUser == 0 ? user > 0 && (user == store.bubbleUser() || user == store.metaUser())
                : user == 0 && operation == RULES;
        if (!valid) throw new SecurityException("Profile not managed by Bubble");
        ArrayBlockingQueue<Bundle> result = new ArrayBlockingQueue<>(1);
        Messenger reply = new Messenger(new Handler(Looper.getMainLooper(), message -> {
            Bundle data = message.getData();
            data.setClassLoader(PendingIntent.class.getClassLoader());
            result.offer(data);
            return true;
        }));
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                try {
                    Message request = Message.obtain(null, operation);
                    request.setData(args == null ? new Bundle() : args);
                    request.replyTo = reply;
                    new Messenger(binder).send(request);
                } catch (Exception e) { result.offer(error(e.getMessage())); }
            }
            @Override public void onServiceDisconnected(ComponentName name) {
                result.offer(error(c.getString(R.string.error_profile_unavailable)));
            }
            @Override public void onNullBinding(ComponentName name) { onServiceDisconnected(name); }
            @Override public void onBindingDied(ComponentName name) { onServiceDisconnected(name); }
        };
        Intent intent = new Intent().setComponent(new ComponentName(c.getPackageName(), ProfileControlService.class.getName()));
        boolean bound = c.bindServiceAsUser(intent, connection, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT, handle(user));
        if (!bound) throw new IOException(c.getString(R.string.error_profile_unavailable));
        try {
            Bundle response = result.poll(10, TimeUnit.SECONDS);
            if (response == null) throw new IOException(c.getString(R.string.error_profile_unavailable));
            if (response.containsKey(ERROR)) throw new IOException(response.getString(ERROR));
            return response;
        } finally {
            c.unbindService(connection);
        }
    }

    static Bundle error(String text) {
        Bundle b = new Bundle();
        b.putString(ERROR, text == null ? "Profile operation failed" : text);
        return b;
    }

    static List<String[]> apps(Context c, int user) throws Exception {
        Bundle response = call(c, user, APPS, null);
        ArrayList<String> packages = response.getStringArrayList("packages");
        ArrayList<String> paths = response.getStringArrayList("paths");
        if (packages == null || paths == null || packages.size() != paths.size()) throw new IOException("Invalid app list");
        List<String[]> out = new ArrayList<>();
        for (int i = 0; i < packages.size(); i++) out.add(new String[]{packages.get(i), paths.get(i)});
        return out;
    }

    static String applyNetwork(Context c, int user) throws Exception {
        if (locked(c, user)) return "bubble locked: firewall list saved, applied when the profile is unlocked\n";
        // The profile pulls the authoritative list from user 0. No stale snapshot can overwrite it.
        call(c, user, NETWORK, null);
        return "profile firewall applied locally\n";
    }

    static String rules(Context c) throws Exception {
        Bundle response = call(c, 0, RULES, null);
        if (!response.containsKey(ALLOWED)) throw new IOException("Missing profile rules");
        return response.getString(ALLOWED);
    }

    static void open(Context c, int user, String pkg) throws Exception {
        Bundle request = new Bundle();
        request.putString(PACKAGE, pkg);
        PendingIntent launch = call(c, user, OPEN, request).getParcelable("launch", PendingIntent.class);
        if (launch == null) throw new IOException(c.getString(R.string.error_profile_unavailable));
        android.app.ActivityOptions options = android.app.ActivityOptions.makeBasic();
        options.setPendingIntentBackgroundActivityStartMode(android.os.Build.VERSION.SDK_INT >= 36
                ? android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
                : android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        launch.send(c, 0, null, null, null, null, options.toBundle());
    }
}
