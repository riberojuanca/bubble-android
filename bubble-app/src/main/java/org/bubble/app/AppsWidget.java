package org.bubble.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.TypedValue;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.List;

/**
 * Bubble's home-screen widget: a group under Bubble's logo with every app of the Meta space (work
 * badge) and of the bubble, as close to a folder as Android lets an app get (no app can create
 * launcher folders: their database needs ACCESS_LAUNCHER_DATA, a system permission). Rows fit
 * the available height and the grid scrolls up and down for the rest; icons have the
 * launcher's own size (read by the engine from the launcher's dump). The bubble's apps are the
 * list Bubble last read; until it has read one, a Bubble tile opens Bubble Apps to read it.
 */
public final class AppsWidget extends AppWidgetProvider {

    /** Fixed parts of widget_apps.xml, in dp: side and vertical padding, header with its margin. */
    private static final int PADDING_SIDES = 16, PADDING_VERTICAL = 12, HEADER = 24;
    /** Icons as big as the launcher's (52 dp until it is known), never smaller than this. */
    private static final int ICON_DEFAULT = 52, ICON_MIN = 32, GAP = 8;

    /** One app on the widget: what opens it (space and package), its name and icon. */
    private static final class Entry {
        final String space, pkg;
        final CharSequence label;
        final Drawable icon;

        Entry(String space, String pkg, CharSequence label, Drawable icon) {
            this.space = space;
            this.pkg = pkg;
            this.label = label;
            this.icon = icon;
        }
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        List<Entry> entries = entries(context);
        for (int id : ids) manager.updateAppWidget(id, build(context, manager, id, entries));
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle options) {
        manager.updateAppWidget(id, build(context, manager, id, entries(context)));
    }

    /** Redraw every placed widget, after the apps of the Meta space or of the bubble change. */
    static void refresh(Context context) {
        AppIconWidget.refresh(context);
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, AppsWidget.class));
        if (ids.length == 0) return;
        List<Entry> entries = entries(context);
        for (int id : ids) manager.updateAppWidget(id, build(context, manager, id, entries));
    }

    /** Ask the launcher to place the widget; Android shows its own confirmation. */
    static boolean requestPin(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        if (!manager.isRequestPinAppWidgetSupported()) return false;
        return manager.requestPinAppWidget(new ComponentName(context, AppsWidget.class), null, null);
    }

    private static RemoteViews build(Context context, AppWidgetManager manager, int id, List<Entry> entries) {
        // Portrait size, in dp: the launcher reports the width as the minimum and the height as
        // the maximum of the sizes it may give the widget.
        Bundle options = manager.getAppWidgetOptions(id);
        int width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250);
        int height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110);
        int availableHeight = Math.max(ICON_MIN + GAP, height - PADDING_VERTICAL - HEADER);
        int icon = Math.max(ICON_MIN, Math.min(launcherIconDp(context), availableHeight - GAP));
        // Keep cells compact as the widget grows: GridView then reveals additional rows instead
        // of stretching two rows across all the available space.
        int rowHeight = icon + GAP;
        int columns = Math.max(1, (width - PADDING_SIDES) / (icon + 2 * GAP));

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_apps);
        views.setInt(R.id.widget_grid, "setNumColumns", columns);
        RemoteViews.RemoteCollectionItems.Builder items = new RemoteViews.RemoteCollectionItems.Builder();
        long n = 0;
        for (Entry e : entries) {
            RemoteViews item = new RemoteViews(context.getPackageName(), R.layout.widget_item);
            item.setViewLayoutHeight(R.id.widget_item, rowHeight, TypedValue.COMPLEX_UNIT_DIP);
            item.setViewLayoutWidth(R.id.widget_icon, icon, TypedValue.COMPLEX_UNIT_DIP);
            item.setViewLayoutHeight(R.id.widget_icon, icon, TypedValue.COMPLEX_UNIT_DIP);
            item.setImageViewBitmap(R.id.widget_icon, bitmap(context, e.icon, icon));
            item.setContentDescription(R.id.widget_icon, e.label);
            item.setOnClickFillInIntent(R.id.widget_item, new Intent()
                    .putExtra(ShortcutActivity.EXTRA_SPACE, e.space)
                    .putExtra(ShortcutActivity.EXTRA_PACKAGE, e.pkg));
            items.addItem(n++, item);
        }
        views.setRemoteAdapter(R.id.widget_grid, items.setViewTypeCount(1).build());
        // One template for every icon; each fills in its space and package.
        Intent open = new Intent(context, ShortcutActivity.class).setAction(Intent.ACTION_VIEW);
        views.setPendingIntentTemplate(R.id.widget_grid, PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE));
        return views;
    }

    /** The widget's icons are a little smaller than the launcher's: they have no names under them. */
    private static final float ICON_SCALE = 0.93f;

    /** The launcher's icon size in dp, as the engine last read it, scaled by ICON_SCALE. */
    static int launcherIconDp(Context context) {
        int px = new Store(context).launcherIconPx();
        float dp = px > 0 ? px / context.getResources().getDisplayMetrics().density : ICON_DEFAULT;
        return Math.round(dp * ICON_SCALE);
    }

    /** Meta apps first (rounded square with the work badge), then the bubble's. */
    private static List<Entry> entries(Context context) {
        List<Entry> out = new ArrayList<>();
        int size = Ui.dp(context, launcherIconDp(context));
        UserHandle meta = metaHandle(context);
        if (meta != null && !context.getSystemService(UserManager.class).isQuietModeEnabled(meta)) {
            for (LauncherActivityInfo info : metaApps(context, meta)) {
                Drawable icon = context.getPackageManager().getUserBadgedIcon(
                        Ui.roundedIcon(context, info.getIcon(0), size), meta);
                out.add(new Entry(AppsGridActivity.SPACE_META, info.getComponentName().getPackageName(),
                        info.getLabel(), icon));
            }
        }
        Store store = new Store(context);
        List<String[]> bubble = store.bubbleUser() < 0 ? new ArrayList<>() : store.lastBubbleApps();
        for (AppInfo app : AppInfo.inside(context, bubble)) {
            if (!openable(app.pkg)) continue;
            out.add(new Entry(AppsGridActivity.SPACE_BUBBLE, app.pkg, app.label,
                    Ui.roundedIcon(context, app.icon(context), size)));
        }
        if (store.bubbleUser() >= 0 && bubble.isEmpty()) {
            out.add(new Entry(ShortcutActivity.SPACE_GRID, "", context.getString(R.string.home_bubble),
                    context.getDrawable(R.drawable.ic_bubble)));
        }
        return out;
    }

    /** The user's apps of the Meta space: system ones (Files) and Bubble's own copy left out. */
    static List<LauncherActivityInfo> metaApps(Context context, UserHandle meta) {
        List<LauncherActivityInfo> out = new ArrayList<>();
        for (LauncherActivityInfo info : context.getSystemService(LauncherApps.class).getActivityList(null, meta)) {
            if (info.getComponentName().getPackageName().equals(context.getPackageName())
                    || (info.getApplicationInfo().flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            out.add(info);
        }
        return out;
    }

    /** Keyboards have nothing to open. */
    static boolean openable(String pkg) {
        return !pkg.equals(StarterApps.GBOARD) && !pkg.equals(StarterApps.KEYBOARD.pkg);
    }

    /** The Meta space's user, from the id Bubble saved; null when there is none. */
    static UserHandle metaHandle(Context context) {
        int id = new Store(context).metaUser();
        if (id < 0) return null;
        UserHandle handle = UserHandle.getUserHandleForUid(id * 100000);
        return context.getSystemService(LauncherApps.class).getProfiles().contains(handle) ? handle : null;
    }

    static Bitmap bitmap(Context context, Drawable d) {
        return bitmap(context, d, 48);
    }

    static Bitmap bitmap(Context context, Drawable d, int sizeDp) {
        int size = Ui.dp(context, sizeDp);
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Drawable copy = d.getConstantState() != null ? d.getConstantState().newDrawable().mutate() : d;
        copy.setBounds(0, 0, size, size);
        copy.draw(new Canvas(b));
        return b;
    }
}
