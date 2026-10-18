package org.bubble.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Bubble Apps: a second launcher entry with every app of the Meta space and of the bubble in one
 * grid. Meta apps come from LauncherApps (with the work badge) and open at once. The bubble's are
 * the list Bubble last read, shown at once and read again on a pull; only the engine can read and
 * open them, because Android hides the Private Space from every app but the launcher. Opening one
 * while the bubble is locked brings up Android's own unlock prompt. A long press pins a
 * home-screen shortcut, and a button places the widget ({@link AppsWidget}); Android confirms both.
 */
public final class AppsGridActivity extends Activity {

    static final String SPACE_META = "meta";
    static final String SPACE_BUBBLE = "bubble";
    private static final int COLUMNS = 4;

    private GridLayout metaGrid;
    private GridLayout bubbleGrid;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (BootReceiver.insideBubble()) {
            BootReceiver.hideLauncher(this);
            finish();
            return;
        }
        LinearLayout page = Ui.screen(this, getString(R.string.grid_label), null, null);
        page.addView(Ui.title(this, getString(R.string.home_meta)));
        metaGrid = grid();
        page.addView(metaGrid);
        page.addView(Ui.title(this, getString(R.string.home_bubble)));
        bubbleGrid = grid();
        page.addView(bubbleGrid);
        page.addView(Ui.small(this, getString(R.string.grid_hint)));
        page.addView(Ui.button(this, getString(R.string.grid_add_widget), v -> {
            if (!AppsWidget.requestPin(this)) Ui.alert(this, getString(R.string.grid_label), getString(R.string.grid_widget_unsupported));
        }));
        Ui.onPull(this, () -> {
            BubbleCache.clear();
            showMeta();
            readBubble();
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (metaGrid == null) return;
        showMeta();
        List<String[]> last = new Store(this).lastBubbleApps();
        if (last.isEmpty()) readBubble();
        else showBubble(AppInfo.inside(this, last));
    }

    // ---- Meta space ---------------------------------------------------------

    private void showMeta() {
        metaGrid.removeAllViews();
        UserHandle meta = AppsWidget.metaHandle(this);
        if (meta == null) {
            metaGrid.addView(Ui.body(this, getString(R.string.error_no_meta)));
            return;
        }
        if (getSystemService(UserManager.class).isQuietModeEnabled(meta)) {
            metaGrid.addView(Ui.body(this, getString(R.string.grid_meta_paused)));
            return;
        }
        LauncherApps launcher = getSystemService(LauncherApps.class);
        for (LauncherActivityInfo info : AppsWidget.metaApps(this, meta)) {
            String pkg = info.getComponentName().getPackageName();
            // Rounded square like the bubble's apps, with the work badge on top.
            Drawable icon = getPackageManager().getUserBadgedIcon(Ui.roundedIcon(this, info.getIcon(0), Ui.dp(this, 48)), meta);
            LinearLayout cell = cell(info.getLabel(), v -> launcher.startMainActivity(info.getComponentName(), meta, null, null));
            ((ImageView) cell.getChildAt(0)).setImageDrawable(icon);
            cell.setOnLongClickListener(v -> pin(SPACE_META, pkg, info.getLabel(), Icon.createWithBitmap(AppsWidget.bitmap(this, icon))));
            metaGrid.addView(cell);
        }
        if (metaGrid.getChildCount() == 0) metaGrid.addView(Ui.body(this, getString(R.string.apps_none)));
    }

    // ---- bubble -------------------------------------------------------------

    private void readBubble() {
        if (new Store(this).bubbleUser() < 0) {
            showBubble(new ArrayList<>());
            return;
        }
        Ui.work(this, getString(R.string.working_reading),
                () -> AppInfo.inside(this, new Controller(this).bubbleApps()), this::showBubble);
    }

    private void showBubble(List<AppInfo> apps) {
        bubbleGrid.removeAllViews();
        List<ImageView> icons = new ArrayList<>();
        List<AppInfo> shown = new ArrayList<>();
        for (AppInfo app : apps) {
            if (!AppsWidget.openable(app.pkg)) continue;
            LinearLayout cell = cell(app.label, v -> openInBubble(this, app.pkg));
            ImageView icon = (ImageView) cell.getChildAt(0);
            // The app's own icon as an adaptive bitmap: the launcher gives it its shape, not a frame.
            cell.setOnLongClickListener(v -> pin(SPACE_BUBBLE, app.pkg, app.label,
                    Icon.createWithAdaptiveBitmap(Ui.adaptiveBitmap(this, app.icon(this)))));
            bubbleGrid.addView(cell);
            icons.add(icon);
            shown.add(app);
        }
        if (shown.isEmpty()) {
            bubbleGrid.addView(Ui.body(this, getString(new Store(this).bubbleUser() < 0 ? R.string.error_no_bubble : R.string.apps_none)));
        }
        Ui.loadIcons(this, shown, icons);
    }

    /** Open an app of the bubble; Android asks to unlock the bubble first when it is locked. */
    static void openInBubble(Activity a, String pkg) {
        if (a instanceof AppsGridActivity) ((AppsGridActivity) a).opening = true;
        Ui.work(a, a.getString(R.string.working_opening), () -> new Controller(a).openInBubble(pkg), log -> {
            if (a instanceof AppsGridActivity) ((AppsGridActivity) a).opening = false;
            if (!log.contains("launched ")) Ui.alert(a, a.getString(R.string.error_title), log);
        });
    }

    /** An app of the bubble is being opened (and may need Android's unlock screen). */
    private boolean opening;

    @Override protected void onPause() {
        super.onPause();
        // Android's unlock screen covers this one: the opening must not ask for it again.
        if (opening) Controller.unlockPromptOpened();
    }

    // ---- grid and shortcuts -------------------------------------------------

    private GridLayout grid() {
        GridLayout g = new GridLayout(this);
        g.setColumnCount(COLUMNS);
        g.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 16));
        return g;
    }

    /** An icon over its name, a quarter of the width; the icon is set by the caller. */
    private LinearLayout cell(CharSequence label, View.OnClickListener open) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8));
        cell.addView(new ImageView(this), new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        TextView name = new TextView(this);
        name.setText(label);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER_HORIZONTAL);
        name.setTextSize(12);
        name.setPadding(0, Ui.dp(this, 6), 0, 0);
        cell.addView(name);
        cell.setOnClickListener(open);
        // A fixed quarter of the page (its side padding is 20dp), so a short row stays aligned left.
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = (getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 40)) / COLUMNS;
        cell.setLayoutParams(lp);
        return cell;
    }

    /** Ask the launcher to add a home-screen shortcut; Android shows its own confirmation. */
    private boolean pin(String space, String pkg, CharSequence label, Icon icon) {
        ShortcutManager shortcuts = getSystemService(ShortcutManager.class);
        if (shortcuts == null || !shortcuts.isRequestPinShortcutSupported() || icon == null) return true;
        Intent open = new Intent(this, ShortcutActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(ShortcutActivity.EXTRA_SPACE, space)
                .putExtra(ShortcutActivity.EXTRA_PACKAGE, pkg);
        shortcuts.requestPinShortcut(new ShortcutInfo.Builder(this, space + ":" + pkg)
                .setShortLabel(label)
                .setIcon(icon)
                .setIntent(open)
                .build(), null);
        return true;
    }
}
