package org.bubble.app;

import android.app.Activity;
import android.os.Bundle;

/**
 * Opened from Android's file picker when another space is locked or paused: asks Android to open
 * it (its own unlock prompt, when one is needed) and stays, invisible, in the picker's task until
 * the space is open. Finishing at once sent the user to the home screen after the unlock prompt
 * (seen on the emulator); finishing afterwards, with RESULT_OK, returns to the picker, which then
 * reads the space again.
 */
public final class SpaceUnlockActivity extends Activity {

    private static final long WAIT_MS = 60_000;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String space = getIntent().getStringExtra(SpaceChannel.SPACE);
        new Thread(() -> {
            try {
                if (space != null) {
                    SpaceChannel.unlock(this, space);
                    long deadline = System.currentTimeMillis() + WAIT_MS;
                    while (System.currentTimeMillis() < deadline && !isFinishing() && locked(space)) Thread.sleep(500);
                    if (!locked(space)) {
                        // The picker reloads the folder only on RESULT_OK, and its list of
                        // places (with "locked" under the name) only when told it changed.
                        setResult(RESULT_OK);
                        getContentResolver().notifyChange(
                                android.provider.DocumentsContract.buildRootsUri(SpacesProvider.AUTHORITY), null);
                    }
                }
            } catch (Exception e) {
                android.util.Log.w("Bubble", "could not open " + space + ": " + e.getMessage());
            }
            runOnUiThread(this::finish);
        }, "bubble-space-unlock").start();
    }

    private boolean locked(String space) throws Exception {
        for (SpaceChannel.Space s : SpaceChannel.spaces(this)) if (s.name.equals(space)) return s.locked;
        return false;
    }
}
