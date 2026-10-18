package org.bubble.app;

import android.app.Activity;
import android.content.Context;

/**
 * Creating the bubble, shared by the setup assistant and the Bubble screen. If a Private Space
 * already exists the engine changes nothing and the user decides: with Google services inside it
 * can only be replaced; without them it can be used as is or replaced. Replacing always asks twice,
 * because its apps and data are lost.
 */
final class BubbleFlow {

    private BubbleFlow() {}

    static void create(Activity a, Ui.Done<Controller.BubbleReport> done) {
        run(a, Controller::createBubble, done);
    }

    static void recreate(Activity a, int user, Ui.Done<Controller.BubbleReport> done) {
        confirmRecreate(a, user, done);
    }

    private interface Step {
        Controller.BubbleReport run(Controller c) throws Exception;
    }

    private static void run(Activity a, Step step, Ui.Done<Controller.BubbleReport> done) {
        Ui.work(a, a.getString(R.string.working_bubble), () -> step.run(new Controller(a)), r -> {
            if (r.needsDecision) {
                askDecision(a, r, done);
            } else if (!r.ready()) {
                Ui.alert(a, a.getString(R.string.error_title), problem(a, r));
            } else {
                done.accept(r);
            }
        });
    }

    private static void askDecision(Activity a, Controller.BubbleReport r, Ui.Done<Controller.BubbleReport> done) {
        Ui.Sheet dialog = new Ui.Sheet(a);
        if (r.googleServices > 0) {
            dialog.title(R.string.decision_google_title)
                    .message(a.getString(R.string.decision_google_body, r.googleServices))
                    .danger(a.getString(R.string.action_replace), () -> confirmRecreate(a, r.user, done));
        } else {
            dialog.title(R.string.decision_clean_title)
                    .message(R.string.decision_clean_body)
                    .action(a.getString(R.string.action_use_this), () -> run(a, Controller::adoptBubble, done))
                    .secondary(a.getString(R.string.action_replace), () -> confirmRecreate(a, r.user, done));
        }
        dialog.cancel().show();
    }

    private static void confirmRecreate(Activity a, int user, Ui.Done<Controller.BubbleReport> done) {
        new Ui.Sheet(a)
                .title(R.string.confirm_replace_title)
                .message(R.string.confirm_replace_body)
                .danger(a.getString(R.string.action_delete), () -> run(a, c -> c.recreateBubble(user), done))
                .cancel()
                .show();
    }

    /** One line for the bubble's state, from a fresh report. */
    static String summary(Context c, Store store, Controller.BubbleReport r) {
        if (!r.connected) return c.getString(R.string.bubble_unknown);
        if (r.user < 0) return c.getString(R.string.bubble_none);
        if (locked(r)) return c.getString(R.string.bubble_locked);
        if (r.user != store.bubbleUser()) {
            return r.googleServices > 0 ? c.getString(R.string.bubble_other_with_google)
                    : c.getString(R.string.bubble_other_clean);
        }
        if (r.googleActive > 0) return c.getString(R.string.bubble_google_active, r.googleActive);
        if (r.googleServices > 0) return c.getString(R.string.bubble_google_disabled, r.googleServices);
        return c.getString(R.string.bubble_ready);
    }

    /** The bubble exists and is locked (its apps stopped, its storage encrypted). */
    static boolean locked(Controller.BubbleReport r) {
        return r.connected && r.user > 0 && r.log.contains("bubble_locked=true");
    }

    private static String problem(Context c, Controller.BubbleReport r) {
        if (!r.connected) return r.log;
        int at = r.log.indexOf("bubble-shell:");
        return at >= 0 ? r.log.substring(at) : c.getString(R.string.error_generic);
    }
}
