package org.bubble.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The bubble itself: its state, a fresh check, replacing it with a new one, and deleting it. */
public final class BubbleActivity extends Activity {

    private Store store;
    private TextView state;
    private TextView details;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new Store(this);
        LinearLayout page = Ui.screen(this, getString(R.string.home_bubble), null, null);
        page.addView(Ui.logo(this, 56));
        state = Ui.title(this, "");
        page.addView(state);
        details = Ui.small(this, "");
        page.addView(details);
        page.addView(Ui.body(this, getString(R.string.bubble_how_to_enter)));
        page.addView(Ui.button(this, getString(R.string.bubble_check_now), v -> check()));
        // The bubble's own files and photos, without entering it; a locked bubble asks to unlock here.
        page.addView(Ui.button(this, getString(R.string.bubble_files), v -> openFiles()));
        page.addView(Ui.button(this, getString(R.string.photos_root),
                v -> startActivity(new android.content.Intent(this, SpacePhotosActivity.class))));
        Controller.BubbleReport cached = BubbleCache.report();
        if (cached != null) {
            show(cached);
            checked = true;
        }
        // Apps inside can use the fingerprint only once the bubble has a lock of its own; Android
        // makes the user choose its PIN on screen, so Bubble can only point there.
        page.addView(Ui.small(this, getString(R.string.bubble_fingerprint_hint)));
        page.addView(Ui.button(this, getString(R.string.bubble_fingerprint_open), v -> startActivity(
                new android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS))));
        page.addView(Ui.dangerButton(this, getString(R.string.bubble_replace), v ->
                BubbleFlow.recreate(this, store.bubbleUser(), r -> check())));
        page.addView(Ui.dangerButton(this, getString(R.string.bubble_delete), v -> confirmDelete()));
        Ui.onPull(this, this::check);
    }

    @Override protected void onStart() {
        super.onStart();
        // Once on entering; again only with "Check now" or after replacing the bubble.
        if (!checked) {
            checked = true;
            check();
        }
    }

    private boolean checked;
    /** Android's unlock screen was asked for; it covers this one until confirmed or cancelled. */
    private boolean unlocking;

    @Override protected void onPause() {
        super.onPause();
        if (unlocking) Controller.unlockPromptOpened();
    }

    @Override protected void onResume() {
        super.onResume();
        if (unlocking) Controller.unlockPromptClosed();
    }

    /** Android's file browser inside the bubble: its Downloads and folders, apart from the phone's. */
    private void openFiles() {
        unlocking = true;
        Ui.work(this, getString(R.string.working_opening), () -> new Controller(this).openBubbleFiles(), log -> {
            unlocking = false;
            if (!log.isEmpty()) Ui.alert(this, getString(R.string.error_title), log);
        });
    }

    /** Remove the bubble without a new one; home then shows the setup to create one again. */
    private void confirmDelete() {
        int user = store.bubbleUser();
        new Ui.Sheet(this)
                .title(R.string.bubble_delete_title)
                .message(R.string.bubble_delete_body)
                .danger(getString(R.string.action_delete), () -> Ui.work(this, getString(R.string.working_bubble),
                        () -> new Controller(this).deleteBubble(user), log -> {
                            if (log.contains("bubble-shell:")) {
                                Ui.alert(this, getString(R.string.error_title), log.substring(log.indexOf("bubble-shell:")));
                                return;
                            }
                            startActivity(new android.content.Intent(this, MainActivity.class)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP));
                            finish();
                        }))
                .cancel()
                .show();
    }

    private void check() {
        if (state.getText().length() == 0) state.setText(R.string.bubble_checking);
        Ui.work(this, getString(R.string.bubble_checking), () -> new Controller(this).bubbleStatus(), this::show);
    }

    private void show(Controller.BubbleReport r) {
        state.setText(BubbleFlow.summary(this, store, r));
        details.setText(r.connected && r.user > 0 && !r.log.contains("bubble_locked=true")
                ? getString(R.string.bubble_details, r.user, r.googleServices, r.googleActive) : "");
    }
}
