package t;

import android.app.Activity;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;
import java.util.List;

/** Probe: what an app with Google's cross-user permissions can read about another profile. */
public class Main extends Activity {
    static final String T = "UsageProbe";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        long now = System.currentTimeMillis(), from = now - 3 * 86400000L;
        int user = getIntent().getIntExtra("user", 10);
        probe("own user", this, from, now);
        try {
            // Hidden from the SDK (system API); a privileged app such as Google's calls it directly.
            Context other = (Context) Context.class.getMethod("createContextAsUser", UserHandle.class, int.class)
                    .invoke(this, UserHandle.getUserHandleForUid(user * 100000), 0);
            probe("user " + user, other, from, now);
            try {
                List<PackageInfo> pkgs = other.getPackageManager().getInstalledPackages(0);
                StringBuilder sb = new StringBuilder();
                for (PackageInfo p : pkgs) if (!p.packageName.startsWith("com.android") && !p.packageName.startsWith("android")) sb.append(p.packageName).append(' ');
                Log.i(T, "user " + user + " packages: " + pkgs.size() + " -> " + sb);
            } catch (Throwable e) { Log.i(T, "user " + user + " packages: DENIED " + e); }
        } catch (Throwable e) { Throwable c = e.getCause() != null ? e.getCause() : e; Log.i(T, "createContextAsUser: DENIED " + c); }
        direct(user, from, now);
        dump(user);
        finish();
    }

    /** The DUMP route: ask the usage service for its diagnostic dump, as Google's DUMP permission allows. */
    void dump(int user) {
        try {
            Object binder = Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "usagestats");
            android.os.ParcelFileDescriptor[] pipe = android.os.ParcelFileDescriptor.createPipe();
            new Thread(() -> {
                try { ((android.os.IBinder) binder).dump(pipe[1].getFileDescriptor(), new String[0]); pipe[1].close(); }
                catch (Exception e) { Log.i(T, "dump write: " + e); }
            }).start();
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(new android.os.ParcelFileDescriptor.AutoCloseInputStream(pipe[0])));
            String line; int lines = 0; boolean inUser = false; int hits = 0; String first = null;
            while ((line = r.readLine()) != null) {
                lines++; if (lines <= 2) Log.i(T, "dump line " + lines + ": " + line);
                if (line.startsWith("user=")) inUser = line.startsWith("user=" + user);
                if (inUser && line.contains("org.fdroid.fdroid")) { hits++; if (first == null) first = line.trim(); }
            }
            Log.i(T, "dump usagestats: " + lines + " lines, user " + user + " F-Droid lines: " + hits + " e.g. " + first);
        } catch (Throwable e) { Throwable c = e.getCause() != null ? e.getCause() : e; Log.i(T, "dump usagestats: DENIED " + c); }
    }

    /** The direct routes: the system services called with the other user's id. */
    void direct(int user, long from, long now) {
        try {
            Object binder = Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "usagestats");
            Object svc = Class.forName("android.app.usage.IUsageStatsManager$Stub").getMethod("asInterface", android.os.IBinder.class).invoke(null, binder);
            java.lang.reflect.Method q = null;
            for (java.lang.reflect.Method m : svc.getClass().getMethods()) if (m.getName().equals("queryUsageStats")) q = m;
            Object slice = q.invoke(svc, UsageStatsManager.INTERVAL_DAILY, from, now, getPackageName(), user);
            List<?> list = slice == null ? null : (List<?>) slice.getClass().getMethod("getList").invoke(slice);
            StringBuilder sb = new StringBuilder();
            if (list != null) for (Object o : list) { UsageStats us = (UsageStats) o; if (us.getTotalTimeInForeground() > 0) sb.append(us.getPackageName()).append('=').append(us.getTotalTimeInForeground() / 1000).append("s "); }
            Log.i(T, "direct usage stats user " + user + ": " + (list == null ? "null" : list.size()) + " -> " + sb);
        } catch (Throwable e) { Throwable c = e.getCause() != null ? e.getCause() : e; Log.i(T, "direct usage stats user " + user + ": DENIED " + c); }
        try {
            java.lang.reflect.Method m = android.content.pm.PackageManager.class.getMethod("getInstalledPackagesAsUser", int.class, int.class);
            List<?> pkgs = (List<?>) m.invoke(getPackageManager(), 0, user);
            StringBuilder sb = new StringBuilder();
            for (Object o : pkgs) { String n = ((PackageInfo) o).packageName; if (!n.startsWith("com.android") && !n.equals("android")) sb.append(n).append(' '); }
            Log.i(T, "direct packages user " + user + ": " + pkgs.size() + " -> " + sb);
        } catch (Throwable e) { Throwable c = e.getCause() != null ? e.getCause() : e; Log.i(T, "direct packages user " + user + ": DENIED " + c); }
    }

    static void probe(String who, Context c, long from, long now) {
        UsageStatsManager usm = c.getSystemService(UsageStatsManager.class);
        try {
            List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, from, now);
            StringBuilder sb = new StringBuilder();
            for (UsageStats s : stats) if (s.getTotalTimeInForeground() > 0) sb.append(s.getPackageName()).append('=').append(s.getTotalTimeInForeground() / 1000).append("s ");
            Log.i(T, who + " usage stats: " + stats.size() + " -> " + sb);
        } catch (Throwable e) { Log.i(T, who + " usage stats: DENIED " + e); }
        try {
            UsageEvents ev = usm.queryEvents(from, now);
            int n = 0; StringBuilder sb = new StringBuilder();
            UsageEvents.Event e = new UsageEvents.Event();
            while (ev != null && ev.hasNextEvent()) { ev.getNextEvent(e); if (n++ < 6) sb.append(e.getPackageName()).append(' '); }
            Log.i(T, who + " usage events: " + n + " -> " + sb);
        } catch (Throwable e) { Log.i(T, who + " usage events: DENIED " + e); }
    }
}
