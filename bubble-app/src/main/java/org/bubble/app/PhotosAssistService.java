package org.bubble.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Puts "Bubble photos" on top of Google Photos' picker when Wallpaper & style opened it. The Pixel
 * wallpaper app asks Google Photos by name for a photo (setPackage in its startPhotoPicker), so no
 * other app can be offered there and Google Photos only lists the normal phone's photos. This
 * service only receives window changes of the wallpaper app and Google Photos (see
 * xml/photos_assist.xml) and never reads their content. Tapping the button closes Google Photos'
 * picker and opens Bubble's, which unlocks the bubble in place and sets the chosen photo through
 * Android's own wallpaper screen.
 */
public final class PhotosAssistService extends AccessibilityService {

    private static final String PHOTOS = "com.google.android.apps.photos";
    /** Google Photos' picker for other apps: ExternalPickerActivity, then SearchablePickerActivity. */
    private static final String PHOTOS_PICKER = PHOTOS + ".picker.";
    /**
     * How long after the wallpaper app was on screen a Google Photos picker counts as its own. The
     * wallpaper app reports nothing while one browses it, so this covers a long look at wallpapers.
     */
    private static final long FROM_WALLPAPER_MS = 10 * 60_000;
    private static final long SHOWN_MS = 60_000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private static final long PICKER_SETTLE_MS = 2_000;

    private long wallpaperSeenAt;
    private long pickerSeenAt;
    private View button;
    /** Takes the button away if it is left unused; hiding cancels only this, not an opening. */
    private final Runnable autoHide = this::hide;

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        String cls = event.getClassName() == null ? "" : event.getClassName().toString();
        long now = System.currentTimeMillis();
        if (!pkg.equals(PHOTOS)) {
            // The wallpaper app is on screen, or back after the picker closed. Right after the
            // picker opens the wallpaper app reports its own window too: that one keeps the button.
            wallpaperSeenAt = now;
            if (now - pickerSeenAt > PICKER_SETTLE_MS) hide();
        } else if (cls.startsWith(PHOTOS_PICKER) && now - wallpaperSeenAt < FROM_WALLPAPER_MS) {
            pickerSeenAt = now;
            show();
        } else if (!cls.startsWith(PHOTOS_PICKER) && cls.startsWith(PHOTOS)) {
            hide();
        }
    }

    @Override public void onInterrupt() {
        hide();
    }

    @Override public void onDestroy() {
        hide();
        super.onDestroy();
    }

    /** A pill at the bottom, in Bubble's black (white at night), above Google Photos' picker. */
    private void show() {
        if (button != null) return;
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.dp(this, 14);
        pill.setPadding(pad, Ui.dp(this, 10), Ui.dp(this, 20), Ui.dp(this, 10));
        boolean night = Ui.night(this);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(night ? 0xFFFFFFFF : Ui.INK);
        shape.setCornerRadius(Ui.dp(this, 28));
        pill.setBackground(shape);
        pill.setElevation(Ui.dp(this, 6));
        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.ic_bubble);
        pill.addView(mark, new LinearLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 28)));
        TextView label = new TextView(this);
        label.setText(R.string.photos_root);
        label.setTextColor(night ? Ui.INK : 0xFFFFFFFF);
        label.setTextSize(16);
        label.setPadding(Ui.dp(this, 10), 0, 0, 0);
        pill.addView(label);
        pill.setOnClickListener(v -> open());

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.y = Ui.dp(this, 96);
        getSystemService(WindowManager.class).addView(pill, lp);
        button = pill;
        main.postDelayed(autoHide, SHOWN_MS);
    }

    private void hide() {
        main.removeCallbacks(autoHide);
        if (button == null) return;
        try {
            getSystemService(WindowManager.class).removeView(button);
        } catch (IllegalArgumentException ignored) {
            // Already gone with its window.
        }
        button = null;
    }

    /** Close Google Photos' picker, then open Bubble's: the photo goes to Android's wallpaper screen. */
    private void open() {
        hide();
        performGlobalAction(GLOBAL_ACTION_BACK);
        main.postDelayed(() -> startActivity(new Intent(this, BubblePhotoPickerActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)), 250);
    }

    private static final int LOST = 41;

    /**
     * Turned on by the user but off now (Android may turn an app's accessibility service off, for
     * example when the app is updated): never silently. Bubble does not turn it back on by itself;
     * a notification leads to the switch, so the user decides again.
     */
    static boolean lost(android.content.Context c) {
        return new Store(c).assistWanted() && !enabled(c);
    }

    /** Tell the user with a notification when {@link #lost}; called at boot, after updates and on opening Bubble. */
    static void checkLost(android.content.Context c) {
        android.app.NotificationManager nm = c.getSystemService(android.app.NotificationManager.class);
        if (!lost(c)) {
            nm.cancel(LOST);
            return;
        }
        nm.createNotificationChannel(new android.app.NotificationChannel(BubbleVpnService.ALERTS,
                c.getString(R.string.alerts_channel), android.app.NotificationManager.IMPORTANCE_DEFAULT));
        android.app.PendingIntent open = android.app.PendingIntent.getActivity(c, LOST,
                new Intent(c, SettingsActivity.class).putExtra(SettingsActivity.EXTRA_ASSIST, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
        nm.notify(LOST, new android.app.Notification.Builder(c, BubbleVpnService.ALERTS)
                .setContentTitle(c.getString(R.string.assist_lost_title))
                .setContentText(c.getString(R.string.assist_lost_body))
                .setStyle(new android.app.Notification.BigTextStyle().bigText(c.getString(R.string.assist_lost_body)))
                .setSmallIcon(R.drawable.ic_bubble)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build());
    }

    static String component(android.content.Context c) {
        return c.getPackageName() + "/" + PhotosAssistService.class.getName();
    }

    /** Whether Android has the service on (Bubble turns it on itself: it holds WRITE_SECURE_SETTINGS). */
    static boolean enabled(android.content.Context c) {
        String list = android.provider.Settings.Secure.getString(c.getContentResolver(),
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return list != null && java.util.Arrays.asList(list.split(":")).contains(component(c));
    }

    /**
     * Turn the service on or off, keeping any other accessibility service the user has on. False
     * when Bubble may not write that setting (WRITE_SECURE_SETTINGS comes with pairing): then the
     * user turns it on in Android's accessibility settings.
     */
    static boolean setEnabled(android.content.Context c, boolean on) {
        if (c.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            new Store(c).setAssistWanted(on);
            return false;
        }
        new Store(c).setAssistWanted(on);
        c.getSystemService(android.app.NotificationManager.class).cancel(LOST);
        android.content.ContentResolver r = c.getContentResolver();
        String list = android.provider.Settings.Secure.getString(r, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        java.util.List<String> services = new java.util.ArrayList<>();
        if (list != null) for (String s : list.split(":")) if (!s.isEmpty() && !s.equals(component(c))) services.add(s);
        if (on) services.add(component(c));
        android.provider.Settings.Secure.putString(r, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                String.join(":", services));
        if (on) android.provider.Settings.Secure.putInt(r, android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        return true;
    }
}
