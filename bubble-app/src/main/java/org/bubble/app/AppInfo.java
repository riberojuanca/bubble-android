package org.bubble.app;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** An app shown in a list: its label and package. */
final class AppInfo {

    final String label;
    final String pkg;
    final boolean system;
    /** The APK inside the bubble ("" for apps outside): Bubble cannot see the bubble's apps otherwise. */
    final String path;

    AppInfo(String label, String pkg, boolean system, String path) {
        this.label = label;
        this.pkg = pkg;
        this.system = system;
        this.path = path;
    }

    /** The app's icon: from the phone when it is installed outside, else read from its APK. Slow: off the main thread. */
    android.graphics.drawable.Drawable icon(Context c) {
        PackageManager pm = c.getPackageManager();
        try {
            return pm.getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException ignored) {
            // Only inside the bubble.
        }
        try {
            PackageInfo pi = path.isEmpty() ? null : pm.getPackageArchiveInfo(path, 0);
            if (pi != null && pi.applicationInfo != null) {
                pi.applicationInfo.sourceDir = path;
                pi.applicationInfo.publicSourceDir = path;
                return pi.applicationInfo.loadIcon(pm);
            }
        } catch (Exception ignored) {
            // Unreadable APK: generic icon.
        }
        return pm.getDefaultActivityIcon();
    }

    static final Comparator<AppInfo> BY_LABEL = Comparator.comparing(a -> a.label.toLowerCase(java.util.Locale.ROOT));

    /** Apps installed outside the bubble (the primary Android), Bubble itself excluded. */
    static List<AppInfo> outside(Context c) {
        PackageManager pm = c.getPackageManager();
        List<AppInfo> out = new ArrayList<>();
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if (ai.packageName.equals(c.getPackageName())) continue;
            out.add(new AppInfo(pm.getApplicationLabel(ai).toString(), ai.packageName,
                    (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0, ""));
        }
        out.sort(BY_LABEL);
        return out;
    }

    /**
     * Apps inside the bubble, from the engine's {package, apk path} list. Bubble cannot see the
     * bubble's apps, so the label is read from the APK itself; the package name is the fallback.
     */
    static List<AppInfo> inside(Context c, List<String[]> apps) {
        PackageManager pm = c.getPackageManager();
        List<AppInfo> out = new ArrayList<>();
        for (String[] app : apps) out.add(new AppInfo(label(pm, app[0], app[1]), app[0], false, app[1]));
        out.sort(BY_LABEL);
        return out;
    }

    private static String label(PackageManager pm, String pkg, String path) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException ignored) {
            // Only inside the bubble: read the APK.
        }
        try {
            PackageInfo pi = path.isEmpty() ? null : pm.getPackageArchiveInfo(path, 0);
            if (pi != null && pi.applicationInfo != null) {
                pi.applicationInfo.sourceDir = path;
                pi.applicationInfo.publicSourceDir = path;
                return pi.applicationInfo.loadLabel(pm).toString();
            }
        } catch (Exception ignored) {
            // Unreadable APK: fall back to the package name.
        }
        return pkg;
    }
}
