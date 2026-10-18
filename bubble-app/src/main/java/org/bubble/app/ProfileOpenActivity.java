package org.bubble.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Runs in Bubble's copy inside the bubble and opens another app of the bubble. The engine falls
 * back to it when the shell's own "am start" is refused (seen on the Pixel with Bitwarden,
 * targetSdk 37: Android does not resolve the activity for the shell, while an app of the same
 * profile opens it normally). Started only by the shell: it requires INTERACT_ACROSS_USERS.
 */
public final class ProfileOpenActivity extends Activity {

    static final String EXTRA_PACKAGE = "package";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String pkg = getIntent().getStringExtra(EXTRA_PACKAGE);
        Intent open = pkg == null ? null : getPackageManager().getLaunchIntentForPackage(pkg);
        if (open != null) startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }
}
