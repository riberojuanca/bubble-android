package org.bubble.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.TypedValue;
import android.widget.RemoteViews;

import java.util.List;

/**
 * One app of the bubble or of the Meta space as a home-screen icon. Shortcuts cannot look like
 * plain icons: the Pixel launcher badges every pinned shortcut with the icon of the app that pinned
 * it, in its own circle (IconCache.getShortcutIcon), and only system apps may change that badge
 * (extra_shortcut_badge_override_package needs a trusted package). Widgets get no badge, so here
 * the app's real icon, Bubble's own small mark and the name are drawn exactly as wanted. The mark
 * is a resource the launcher resolves itself, so it follows the light or dark theme.
 */
public final class AppIconWidget extends AppWidgetProvider {

    private static final String ACTION_PLACED = "org.bubble.app.ICON_WIDGET_PLACED";

    @Override public void onReceive(Context context, Intent intent) {
        if (ACTION_PLACED.equals(intent.getAction())) {
            // The launcher placed the widget asked for in requestPin: remember which app it shows.
            int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            String space = intent.getStringExtra(ShortcutActivity.EXTRA_SPACE);
            String pkg = intent.getStringExtra(ShortcutActivity.EXTRA_PACKAGE);
            if (id == AppWidgetManager.INVALID_APPWIDGET_ID || space == null || pkg == null) return;
            new Store(context).setIconWidget(id, space + ":" + pkg);
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            RemoteViews views = build(context, id, space, pkg);
            if (views != null) manager.updateAppWidget(id, views);
            return;
        }
        super.onReceive(context, intent);
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) update(context, manager, id);
    }

    @Override public void onDeleted(Context context, int[] ids) {
        Store store = new Store(context);
        for (int id : ids) store.setIconWidget(id, null);
    }

    /** Redraw every placed icon (after an update, or when the bubble's app list changes). */
    static void refresh(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        for (int id : manager.getAppWidgetIds(new ComponentName(context, AppIconWidget.class))) update(context, manager, id);
    }

    private static void update(Context context, AppWidgetManager manager, int id) {
        String app = new Store(context).iconWidget(id);
        int colon = app == null ? -1 : app.indexOf(':');
        if (colon < 0) return; // not placed yet: ACTION_PLACED draws it
        RemoteViews views = build(context, id, app.substring(0, colon), app.substring(colon + 1));
        if (views != null) manager.updateAppWidget(id, views);
    }

    /**
     * Ask the launcher to place one app's icon; Android shows its own confirmation with this
     * icon as the preview. False when the launcher does not take widgets from apps.
     */
    static boolean requestPin(Context context, String space, String pkg) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        if (!manager.isRequestPinAppWidgetSupported()) return false;
        RemoteViews preview = build(context, 0, space, pkg);
        if (preview == null) return false;
        Bundle extras = new Bundle();
        extras.putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, preview);
        // Mutable: Android adds the new widget's id to it.
        Intent placed = new Intent(context, AppIconWidget.class).setAction(ACTION_PLACED)
                .putExtra(ShortcutActivity.EXTRA_SPACE, space)
                .putExtra(ShortcutActivity.EXTRA_PACKAGE, pkg);
        PendingIntent callback = PendingIntent.getBroadcast(context, (space + ":" + pkg).hashCode(), placed,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        return manager.requestPinAppWidget(new ComponentName(context, AppIconWidget.class), extras, callback);
    }

    /** The icon for one app; null when that app is no longer in its space. */
    private static RemoteViews build(Context context, int id, String space, String pkg) {
        CharSequence label;
        Drawable icon;
        if (AppsGridActivity.SPACE_META.equals(space)) {
            UserHandle meta = AppsWidget.metaHandle(context);
            List<LauncherActivityInfo> found = meta == null ? null
                    : context.getSystemService(LauncherApps.class).getActivityList(pkg, meta);
            if (found == null || found.isEmpty()) return null;
            label = found.get(0).getLabel();
            // The work badge stays: it is what tells Meta apps apart.
            icon = context.getPackageManager().getUserBadgedIcon(Ui.roundedIcon(context, found.get(0).getIcon(0), iconPx(context)), meta);
        } else {
            AppInfo app = null;
            for (AppInfo a : AppInfo.inside(context, new Store(context).lastBubbleApps())) if (a.pkg.equals(pkg)) app = a;
            if (app == null) return null;
            label = app.label;
            icon = Ui.roundedIcon(context, app.icon(context), iconPx(context));
        }
        int size = iconPx(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_app_icon);
        views.setViewLayoutWidth(R.id.app_icon_frame, size, TypedValue.COMPLEX_UNIT_PX);
        views.setViewLayoutHeight(R.id.app_icon_frame, size, TypedValue.COMPLEX_UNIT_PX);
        // The mark keeps its proportion to the icon, like the launcher's own badges.
        int mark = Math.round(size * 0.36f);
        views.setViewLayoutWidth(R.id.app_icon_mark, mark, TypedValue.COMPLEX_UNIT_PX);
        views.setViewLayoutHeight(R.id.app_icon_mark, mark, TypedValue.COMPLEX_UNIT_PX);
        views.setImageViewBitmap(R.id.app_icon, draw(icon, size));
        views.setTextViewText(R.id.app_icon_label, label);
        views.setContentDescription(R.id.app_icon_root, label);
        // The bubble mark only on the bubble's apps; Meta's carry the work badge instead.
        views.setViewVisibility(R.id.app_icon_mark, AppsGridActivity.SPACE_BUBBLE.equals(space)
                ? android.view.View.VISIBLE : android.view.View.GONE);
        Intent open = new Intent(context, ShortcutActivity.class).setAction(Intent.ACTION_VIEW)
                .putExtra(ShortcutActivity.EXTRA_SPACE, space)
                .putExtra(ShortcutActivity.EXTRA_PACKAGE, pkg);
        views.setOnClickPendingIntent(R.id.app_icon_root, PendingIntent.getActivity(context, (space + ":" + pkg).hashCode(),
                open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return views;
    }

    /** As big as the icons of the big widget (AppsWidget), so both look the same on the home screen. */
    private static int iconPx(Context context) {
        return Ui.dp(context, AppsWidget.launcherIconDp(context));
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle options) {
        update(context, manager, id);
    }

    /** The icon, already in the chosen shape (Ui.roundedIcon), as a bitmap of {@code size} pixels. */
    private static Bitmap draw(Drawable icon, int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Drawable copy = icon.getConstantState() != null ? icon.getConstantState().newDrawable().mutate() : icon;
        copy.setBounds(0, 0, size, size);
        copy.draw(new Canvas(b));
        return b;
    }
}
