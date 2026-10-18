package org.bubble.app;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * The few building blocks every screen uses, on the system's DeviceDefault (Material) theme: a
 * scrolling page, titles, body text, tappable rows ("franjas") with a subtitle and a chevron,
 * buttons, and a busy dialog for engine work, which blocks for seconds. Built in code, like the
 * rest of the app; texts come from string resources.
 */
final class Ui {

    private Ui() {}

    static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** A page without header (home, setup). */
    static LinearLayout page(Activity a) {
        return screen(a, null, null, null);
    }

    /**
     * A screen: optional header (back, title, optional action) and a scrolling column with side
     * padding, which is returned. The app draws its own header instead of the action bar: apps
     * targeting Android 15+ are edge-to-edge, and the root fits the system bars itself.
     */
    static LinearLayout screen(Activity a, CharSequence title, CharSequence action, View.OnClickListener onAction) {
        if (a.getActionBar() != null) a.getActionBar().hide();
        LinearLayout root = column(a);
        root.setFitsSystemWindows(true);
        if (title != null) {
            LinearLayout header = new LinearLayout(a);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(dp(a, 4), dp(a, 4), dp(a, 8), dp(a, 4));
            TextView back = text(a, "←", android.R.style.TextAppearance_DeviceDefault_Large);
            back.setPadding(dp(a, 16), dp(a, 12), dp(a, 16), dp(a, 12));
            back.setBackground(drawableAttr(a, android.R.attr.selectableItemBackgroundBorderless));
            back.setContentDescription(a.getString(R.string.action_back));
            back.setOnClickListener(v -> a.finish());
            header.addView(back);
            TextView t = text(a, title, android.R.style.TextAppearance_DeviceDefault_Large);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            header.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (action != null) {
                TextView act = text(a, action, android.R.style.TextAppearance_DeviceDefault_Medium);
                act.setTextColor(actionColor(a));
                act.setPadding(dp(a, 16), dp(a, 12), dp(a, 16), dp(a, 12));
                act.setBackground(drawableAttr(a, android.R.attr.selectableItemBackground));
                act.setOnClickListener(onAction);
                header.addView(act);
            }
            header.addView(spy(a));
            root.addView(header);
            root.addView(loadingBar(a));
            root.addView(loadingLabel(a));
        }
        LinearLayout content = column(a);
        content.setPadding(dp(a, 20), dp(a, 8), dp(a, 20), dp(a, 24));
        ScrollView scroll = new PullScroll(a);
        scroll.setId(R.id.screen_scroll);
        scroll.setFillViewport(true);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        a.setContentView(root);
        return content;
    }

    /** Pull the screen down from its top to read it again; {@code refresh} shows its own loading bar. */
    static void onPull(Activity a, Runnable refresh) {
        View scroll = a.findViewById(R.id.screen_scroll);
        if (scroll instanceof PullScroll) ((PullScroll) scroll).onPull = refresh;
    }

    /**
     * The screen's scroll view with pull to refresh, without libraries: a downward drag that starts
     * at the top moves the content with the finger (at half speed, up to 72dp) and, released at
     * that distance, runs the refresh. It watches every touch, also those that start on a row.
     */
    static final class PullScroll extends ScrollView {
        Runnable onPull;
        private final int slop;
        private float startY = -1;
        private boolean pulling;

        PullScroll(Context c) {
            super(c);
            slop = android.view.ViewConfiguration.get(c).getScaledTouchSlop();
        }

        @Override public boolean dispatchTouchEvent(android.view.MotionEvent e) {
            View content = getChildAt(0);
            if (onPull == null || content == null) return super.dispatchTouchEvent(e);
            int full = dp(getContext(), 72);
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    startY = getScrollY() == 0 ? e.getY() : -1;
                    pulling = false;
                    break;
                case android.view.MotionEvent.ACTION_MOVE:
                    if (startY < 0) break;
                    float dy = e.getY() - startY;
                    if (!pulling && dy > slop && getScrollY() == 0) {
                        pulling = true;
                        // The row under the finger must not take this as a tap.
                        android.view.MotionEvent cancel = android.view.MotionEvent.obtain(e);
                        cancel.setAction(android.view.MotionEvent.ACTION_CANCEL);
                        super.dispatchTouchEvent(cancel);
                        cancel.recycle();
                    }
                    if (pulling) {
                        content.setTranslationY(Math.max(0, Math.min((dy - slop) / 2, full)));
                        return true;
                    }
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    startY = -1;
                    if (pulling) {
                        pulling = false;
                        boolean go = e.getActionMasked() == android.view.MotionEvent.ACTION_UP
                                && content.getTranslationY() >= full;
                        content.animate().translationY(0).setDuration(200).start();
                        if (go) onPull.run();
                        return true;
                    }
                    break;
                default:
                    break;
            }
            return super.dispatchTouchEvent(e);
        }
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static TextView text(Context c, CharSequence s, int appearance) {
        TextView t = new TextView(c);
        t.setTextAppearance(appearance);
        t.setText(s);
        return t;
    }

    static TextView title(Context c, CharSequence s) {
        TextView t = text(c, s, android.R.style.TextAppearance_DeviceDefault_Large);
        t.setPadding(0, dp(c, 16), 0, dp(c, 8));
        return t;
    }

    static TextView body(Context c, CharSequence s) {
        TextView t = text(c, s, android.R.style.TextAppearance_DeviceDefault_Medium);
        t.setPadding(0, dp(c, 4), 0, dp(c, 8));
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    static TextView small(Context c, CharSequence s) {
        TextView t = text(c, s, android.R.style.TextAppearance_DeviceDefault_Small);
        t.setPadding(0, dp(c, 4), 0, dp(c, 4));
        return t;
    }

    /** The logo: three glossy bubbles, in its own colors. */
    static ImageView logo(Context c, int sizeDp) {
        ImageView logo = new ImageView(c);
        logo.setImageResource(R.drawable.ic_bubble);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(c, 24);
        logo.setLayoutParams(lp);
        return logo;
    }

    /** The logo's black glass: buttons and header actions use it. */
    static final int INK = 0xFF1C1F23;

    static boolean night(Context c) {
        return (c.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    /** Header actions: the logo's black by day, white by night (black would vanish there). */
    static int actionColor(Context c) {
        return night(c) ? 0xFFFFFFFF : INK;
    }

    /**
     * A full-width button in the logo's black: rounded, white text, one fixed height for all, no
     * elevation and no border.
     */
    static Button button(Context c, CharSequence label, View.OnClickListener onClick) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
        b.setStateListAnimator(null);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(c, 20), 0, dp(c, 20), 0);
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(INK);
        shape.setCornerRadius(dp(c, RADIUS_DP));
        b.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33FFFFFF), shape, null));
        b.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 52));
        lp.topMargin = dp(c, 12);
        b.setLayoutParams(lp);
        return b;
    }

    /** A tappable row: title, subtitle that can change, chevron. */
    static final class Row {
        final LinearLayout view;
        final TextView title;
        final TextView subtitle;

        ImageView icon;

        Row(Context c, CharSequence titleText, CharSequence subtitleText, View.OnClickListener onClick) {
            this(c, false, titleText, subtitleText, onClick);
        }

        /** With {@code withIcon}, an app icon on the left spans both lines. */
        Row(Context c, boolean withIcon, CharSequence titleText, CharSequence subtitleText, View.OnClickListener onClick) {
            view = new LinearLayout(c);
            view.setOrientation(LinearLayout.HORIZONTAL);
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setPadding(dp(c, 4), dp(c, 14), dp(c, 4), dp(c, 14));
            view.setMinimumHeight(dp(c, 64));
            view.setBackground(drawableAttr(c, android.R.attr.selectableItemBackground));
            view.setOnClickListener(onClick);
            if (withIcon) {
                icon = appIcon(c);
                view.addView(icon);
            }

            LinearLayout texts = column(c);
            title = text(c, titleText, android.R.style.TextAppearance_DeviceDefault_Medium);
            subtitle = text(c, subtitleText, android.R.style.TextAppearance_DeviceDefault_Small);
            texts.addView(title);
            texts.addView(subtitle);
            view.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView chevron = text(c, "›", android.R.style.TextAppearance_DeviceDefault_Large);
            chevron.setPadding(dp(c, 12), 0, dp(c, 4), 0);
            view.addView(chevron);
        }

        void setSubtitle(CharSequence s) { subtitle.setText(s); }
    }

    /** An app's icon in lists: 24dp, the size of the other icons (the guard's spy). */
    static ImageView appIcon(Context c) {
        ImageView i = new ImageView(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, 24), dp(c, 24));
        lp.rightMargin = dp(c, 16);
        i.setLayoutParams(lp);
        return i;
    }

    /** A row for an app: icon on the left (two lines high), name and subtitle, chevron. */
    static Row appRow(LinearLayout parent, CharSequence title, CharSequence subtitle, View.OnClickListener onClick) {
        Row row = new Row(parent.getContext(), true, title, subtitle, onClick);
        parent.addView(row.view);
        parent.addView(divider(parent.getContext()));
        return row;
    }

    /** A network toggle: Wi-Fi or mobile data, lilac when the app has it, crossed out and grey when not. */
    static void showNetwork(Context c, ImageView v, boolean on, boolean wifi) {
        v.setImageResource(wifi ? (on ? R.drawable.ic_wifi : R.drawable.ic_wifi_off)
                : (on ? R.drawable.ic_cell : R.drawable.ic_cell_off));
        v.setImageTintList(android.content.res.ColorStateList.valueOf(on ? LILAC : MUTED));
        v.setContentDescription(c.getString(wifi ? (on ? R.string.app_wifi_on : R.string.app_wifi_off)
                : (on ? R.string.app_mobile_on : R.string.app_mobile_off)));
    }

    /** The logo's lilac: the one accent, for "on" states (Wi-Fi with internet). */
    static final int LILAC = 0xFF9D7BFF;
    /** Neutral grey for secondary icons (no internet, remove). */
    static final int MUTED = 0x99888888;
    /** Text size of app names in every list. */
    static final float LIST_TEXT_SP = 16;

    static TextView listText(Context c, CharSequence s) {
        TextView t = text(c, s, android.R.style.TextAppearance_DeviceDefault_Medium);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, LIST_TEXT_SP);
        return t;
    }

    /** A 40dp tappable icon with a 20dp glyph, neutral grey unless told otherwise. */
    static ImageView iconButton(Context c, int drawable, CharSequence description, View.OnClickListener onClick) {
        ImageView b = new ImageView(c);
        if (drawable != 0) b.setImageResource(drawable);
        b.setImageTintList(android.content.res.ColorStateList.valueOf(MUTED));
        int pad = dp(c, 10);
        b.setPadding(pad, pad, pad, pad);
        b.setBackground(drawableAttr(c, android.R.attr.selectableItemBackgroundBorderless));
        b.setContentDescription(description);
        b.setOnClickListener(onClick);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 40), dp(c, 40)));
        return b;
    }

    /** A checkable app row: icon, name, checkbox at the end; tapping anywhere toggles it. */
    static final class CheckRow {
        final LinearLayout view;
        final ImageView icon;
        final CheckBox box;
        final String pkg;
        final String label;

        CheckRow(Context c, String label, String pkg, boolean checked) {
            this.pkg = pkg;
            this.label = label;
            view = new LinearLayout(c);
            view.setOrientation(LinearLayout.HORIZONTAL);
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setPadding(dp(c, 4), dp(c, 4), 0, dp(c, 4));
            view.setMinimumHeight(dp(c, 52));
            view.setBackground(drawableAttr(c, android.R.attr.selectableItemBackground));
            icon = appIcon(c);
            view.addView(icon);
            TextView name = listText(c, label);
            view.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            box = new CheckBox(c);
            box.setChecked(checked);
            view.addView(box);
            view.setOnClickListener(v -> box.toggle());
        }

        boolean matches(String query) {
            return query.isEmpty() || label.toLowerCase(java.util.Locale.ROOT).contains(query) || pkg.contains(query);
        }
    }

    /** A search box that calls {@code onQuery} with the lowercased text as the user types. */
    static android.widget.EditText search(Context c, Done<String> onQuery) {
        android.widget.EditText search = new android.widget.EditText(c);
        search.setHint(R.string.picker_search);
        search.setSingleLine(true);
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                onQuery.accept(s.toString().trim().toLowerCase(java.util.Locale.ROOT));
            }
        });
        return search;
    }

    /**
     * An app icon as a rounded square, the buttons' rounding scaled to the icon (14dp on a 52dp
     * button is about a quarter of the height). Adaptive icons are drawn like a launcher does (their
     * layers are 1.5 times the visible size) but clipped to this shape instead of the system's.
     */
    static android.graphics.drawable.Drawable roundedIcon(Context c, android.graphics.drawable.Drawable d) {
        return roundedIcon(c, d, dp(c, 24) * 2); // drawn at 2x for sharpness, shown at 24dp
    }

    /** The same rounded square, {@code size} pixels wide (the widget draws bigger icons). */
    static android.graphics.drawable.Drawable roundedIcon(Context c, android.graphics.drawable.Drawable d, int size) {
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
        android.graphics.Path clip = new android.graphics.Path();
        float r = size * 0.27f;
        clip.addRoundRect(0, 0, size, size, r, r, android.graphics.Path.Direction.CW);
        canvas.clipPath(clip);
        if (d instanceof android.graphics.drawable.AdaptiveIconDrawable) {
            android.graphics.drawable.AdaptiveIconDrawable ad = (android.graphics.drawable.AdaptiveIconDrawable) d;
            int extra = size / 4;
            for (android.graphics.drawable.Drawable layer : new android.graphics.drawable.Drawable[]{ad.getBackground(), ad.getForeground()}) {
                if (layer == null) continue;
                layer.setBounds(-extra, -extra, size + extra, size + extra);
                layer.draw(canvas);
            }
        } else {
            d.setBounds(0, 0, size, size);
            d.draw(canvas);
        }
        return new android.graphics.drawable.BitmapDrawable(c.getResources(), bmp);
    }

    /** Load icons off the main thread (hundreds of system apps would stall the screen). */
    static void loadIcons(Activity a, List<AppInfo> apps, List<ImageView> views) {
        new Thread(() -> {
            for (int i = 0; i < apps.size() && i < views.size(); i++) {
                android.graphics.drawable.Drawable d = roundedIcon(a, apps.get(i).icon(a));
                ImageView v = views.get(i);
                a.runOnUiThread(() -> v.setImageDrawable(d));
            }
        }, "bubble-icons").start();
    }

    static Row row(LinearLayout parent, CharSequence title, CharSequence subtitle, View.OnClickListener onClick) {
        Row row = new Row(parent.getContext(), title, subtitle, onClick);
        parent.addView(row.view);
        parent.addView(divider(parent.getContext()));
        return row;
    }

    static View divider(Context c) {
        View d = new View(c);
        d.setBackground(drawableAttr(c, android.R.attr.listDivider));
        d.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return d;
    }

    // ---- background work ----------------------------------------------------

    interface Job<T> {
        T run() throws Exception;
    }

    interface Done<T> {
        void accept(T value);
    }

    /**
     * The guard, always in the top corner: black (or white at night) when it watches all the time,
     * lilac when it checks on every use, grey when it is not running. Tapping explains the state.
     */
    static android.widget.ImageView spy(Activity a) {
        android.widget.ImageView spy = new android.widget.ImageView(a);
        spy.setImageResource(R.drawable.ic_spy);
        int pad = dp(a, 12);
        spy.setPadding(pad, pad, pad, pad);
        spy.setBackground(drawableAttr(a, android.R.attr.selectableItemBackgroundBorderless));
        spy.setLayoutParams(new LinearLayout.LayoutParams(dp(a, 48), dp(a, 48)));
        refreshSpy(a, spy);
        spy.setOnClickListener(v -> {
            int g = new Store(a).guard();
            alert(a, a.getString(R.string.guard_title), a.getString(g == Store.GUARD_ALWAYS ? R.string.guard_always
                    : g == Store.GUARD_SESSION ? R.string.guard_session : R.string.guard_off));
        });
        return spy;
    }

    static void refreshSpy(Activity a, android.widget.ImageView spy) {
        int g = new Store(a).guard();
        int color = g == Store.GUARD_ALWAYS ? actionColor(a) : g == Store.GUARD_SESSION ? 0xFF9D7BFF : 0x80888888;
        spy.setImageTintList(android.content.res.ColorStateList.valueOf(color));
        spy.setContentDescription(a.getString(R.string.guard_title));
        spy.setTag("spy");
    }

    /** A loading bar for this activity's screen, found by the work below. */
    static LoadingBar loadingBar(Activity a) {
        LoadingBar bar = new LoadingBar(a);
        bar.setId(R.id.loading_bar);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 3));
        lp.bottomMargin = dp(a, 8); // room for the step text under it, the same on every screen
        bar.setLayoutParams(lp);
        return bar;
    }

    /** The step text shown under the loading bar while Bubble works; hidden when idle. */
    static TextView loadingLabel(Activity a) {
        TextView t = text(a, "", android.R.style.TextAppearance_DeviceDefault_Small);
        t.setId(R.id.loading_label);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setSingleLine(true); // one line: a long step is cut with "…", the screen does not jump
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setPadding(dp(a, 20), dp(a, 4), dp(a, 20), dp(a, 10));
        t.setVisibility(View.GONE);
        return t;
    }

    /** Show the steps of work started by this activity under its loading bar. */
    static Progress.Listener showSteps(Activity a) {
        TextView label = a.findViewById(R.id.loading_label);
        Progress.Listener l = step -> {
            if (label == null) return;
            label.setText(step);
            label.setVisibility(View.VISIBLE);
        };
        Progress.listen(l);
        return l;
    }

    static void hideSteps(Activity a, Progress.Listener l) {
        Progress.stopListening(l);
        TextView label = a.findViewById(R.id.loading_label);
        if (label != null) label.setVisibility(View.GONE);
    }

    private static final java.util.Map<Activity, Boolean> BUSY = new java.util.WeakHashMap<>();

    /**
     * Run engine work off the main thread while the screen's loading bar runs; the screen stays
     * usable. A second call while one is running on the same screen is ignored (no double taps).
     * Errors are shown in a dialog; {@code done} runs on the main thread with the result.
     */
    static <T> void work(Activity a, CharSequence message, Job<T> job, Done<T> done) {
        if (BUSY.containsKey(a)) return;
        BUSY.put(a, true);
        LoadingBar bar = a.findViewById(R.id.loading_bar);
        if (bar != null) bar.start();
        Progress.Listener steps = showSteps(a);
        new Thread(() -> {
            T result = null;
            Exception error = null;
            try {
                result = job.run();
            } catch (Exception e) {
                error = e;
            }
            T r = result;
            Exception err = error;
            a.runOnUiThread(() -> {
                BUSY.remove(a);
                if (bar != null) bar.stop();
                hideSteps(a, steps);
                View spy = a.getWindow().getDecorView().findViewWithTag("spy");
                if (spy instanceof android.widget.ImageView) refreshSpy(a, (android.widget.ImageView) spy);
                if (a.isFinishing() || a.isDestroyed()) return;
                if (err != null) {
                    alert(a, a.getString(R.string.error_title), String.valueOf(err.getMessage()));
                } else if (done != null) {
                    done.accept(r);
                }
            });
        }, "bubble-work").start();
    }

    static void alert(Activity a, CharSequence title, CharSequence message) {
        new Sheet(a).title(title).message(message).action(a.getString(android.R.string.ok), null).show();
    }

    /** The radius of buttons and dialogs. */
    static final int RADIUS_DP = 14;

    /**
     * Bubble's dialog, made of the same pieces as its screens instead of the system's AlertDialog:
     * a rounded panel with a title, text, choices as rows (franjas, which read as tappable) and
     * actions as Bubble's rounded buttons, the main one black and the others outlined. Any choice
     * or action closes it first.
     */
    static final class Sheet {
        private final Activity a;
        private final android.app.Dialog dialog;
        private final LinearLayout body;
        private final LinearLayout choices;
        private final LinearLayout actions;

        Sheet(Activity a) {
            this.a = a;
            dialog = new android.app.Dialog(a);
            dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
            body = column(a);
            choices = column(a);
            actions = column(a);
            LinearLayout panel = column(a);
            panel.setPadding(dp(a, 20), dp(a, 8), dp(a, 20), dp(a, 20));
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            TypedArray ta = a.obtainStyledAttributes(new int[]{android.R.attr.colorBackground});
            bg.setColor(ta.getColor(0, night(a) ? 0xFF000000 : 0xFFFFFFFF));
            ta.recycle();
            bg.setCornerRadius(dp(a, RADIUS_DP));
            panel.setBackground(bg);
            // Title, text and buttons stay in place; a long list of choices scrolls between them,
            // up to 60% of the screen.
            int maxList = a.getResources().getDisplayMetrics().heightPixels * 6 / 10;
            ScrollView list = new ScrollView(a) {
                @Override protected void onMeasure(int w, int h) {
                    super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxList, MeasureSpec.AT_MOST));
                }
            };
            list.addView(choices);
            panel.addView(body);
            panel.addView(list);
            panel.addView(actions);
            dialog.setContentView(panel);
            android.view.Window w = dialog.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new android.graphics.drawable.InsetDrawable(
                        new android.graphics.drawable.ColorDrawable(0), dp(a, 24), 0, dp(a, 24), 0));
                w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        }

        Sheet title(CharSequence t) {
            TextView v = text(a, t, android.R.style.TextAppearance_DeviceDefault_Medium);
            v.setPadding(0, dp(a, 12), 0, dp(a, 4));
            body.addView(v);
            return this;
        }

        Sheet title(int res) {
            return title(a.getString(res));
        }

        Sheet message(CharSequence m) {
            body.addView(small(a, m));
            return this;
        }

        Sheet message(int res) {
            return message(a.getString(res));
        }

        Sheet view(View v) {
            body.addView(v);
            return this;
        }

        /** A row to pick, at the size of app lists; the subtitle and icon can be null. */
        Sheet choice(CharSequence title, CharSequence subtitle, Drawable icon, Runnable onPick) {
            Row row = new Row(a, icon != null, title, subtitle == null ? "" : subtitle, v -> {
                dialog.dismiss();
                onPick.run();
            });
            row.title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, LIST_TEXT_SP);
            row.view.setPadding(dp(a, 4), dp(a, 8), dp(a, 4), dp(a, 8));
            row.view.setMinimumHeight(dp(a, 52));
            if (icon != null) row.icon.setImageDrawable(roundedIcon(a, icon));
            if (subtitle == null) row.subtitle.setVisibility(View.GONE);
            choices.addView(row.view);
            choices.addView(divider(a));
            return this;
        }

        Sheet choice(CharSequence title, CharSequence subtitle, Runnable onPick) {
            return choice(title, subtitle, null, onPick);
        }

        /** The main action: Bubble's black button. {@code run} may be null (just close). */
        Sheet action(CharSequence label, Runnable run) {
            actions.addView(button(a, label, v -> {
                dialog.dismiss();
                if (run != null) run.run();
            }));
            return this;
        }

        /** A secondary action (cancel, an alternative): same shape, outlined. */
        Sheet secondary(CharSequence label, Runnable run) {
            Button b = button(a, label, v -> {
                dialog.dismiss();
                if (run != null) run.run();
            });
            int ink = actionColor(a);
            b.setTextColor(ink);
            android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
            shape.setColor(0);
            shape.setStroke(dp(a, 1), ink);
            shape.setCornerRadius(dp(a, RADIUS_DP));
            b.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x22888888), shape, null));
            actions.addView(b);
            return this;
        }

        Sheet cancel() {
            return secondary(a.getString(android.R.string.cancel), null);
        }

        void show() {
            if (!a.isFinishing() && !a.isDestroyed()) dialog.show();
        }
    }

    static int accent(Context c) {
        TypedArray ta = c.obtainStyledAttributes(new int[]{android.R.attr.colorAccent});
        int color = ta.getColor(0, 0xff3d7bd9);
        ta.recycle();
        return color;
    }

    private static Drawable drawableAttr(Context c, int attr) {
        TypedArray ta = c.obtainStyledAttributes(new int[]{attr});
        Drawable d = ta.getDrawable(0);
        ta.recycle();
        return d;
    }
}
