package org.bubble.app;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;

/** Downloads a verified update and hands it to Android without changing Bubble's data. */
public final class UpdateActivity extends Activity {
    private static final String INSTALL_RESULT = "org.bubble.app.UPDATE_INSTALL_RESULT";
    private Task task;
    private TextView status;
    private Button action;
    private LoadingBar loading;
    private boolean loadingActive;
    private boolean resumed;
    private boolean waitingForPermission;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout page = Ui.screen(this, getString(R.string.settings_updates), null, null);
        status = Ui.body(this, "");
        page.addView(status);
        action = Ui.button(this, getString(R.string.update_action), v -> act());
        page.addView(action);
        loading = findViewById(R.id.loading_bar);
        Object retained = getLastNonConfigurationInstance();
        task = retained instanceof Task ? (Task) retained : new Task(getApplicationContext());
        task.listener = new WeakReference<>(this);
        waitingForPermission = savedInstanceState != null
                && savedInstanceState.getBoolean("waiting_permission");
        if (handleResult(getIntent())) return;
        if (retained != null) {
            render();
            return;
        }
        String release = savedInstanceState != null ? savedInstanceState.getString("release")
                : getIntent().getStringExtra("release");
        task.autoDownload = savedInstanceState == null && getIntent().getBooleanExtra("download", false);
        task.check(release);
    }

    @Override public Object onRetainNonConfigurationInstance() { return task; }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (task.release != null) state.putString("release", task.release.json);
        state.putBoolean("waiting_permission", waitingForPermission);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        render();
        if (waitingForPermission) {
            waitingForPermission = false;
            if (getPackageManager().canRequestPackageInstalls()) install();
            else Ui.alert(this, getString(R.string.settings_updates), getString(R.string.update_permission));
        } else {
            continueInstall();
        }
    }

    @Override protected void onPause() {
        resumed = false;
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (task.listener.get() == this) task.listener.clear();
        if (isFinishing()) {
            task.closed = true;
            if (task.stage == Task.READY && task.file != null) task.file.delete();
            if (task.stage == Task.PREPARED) task.abandon();
        }
        super.onDestroy();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleResult(intent);
    }

    private boolean handleResult(Intent intent) {
        if (!INSTALL_RESULT.equals(intent.getAction())) return false;
        int result = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (result == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            if (confirmation == null) task.state(Task.ERROR, R.string.update_install_failed);
            else {
                task.state(Task.INSTALLING, R.string.update_installing);
                try {
                    startActivity(confirmation);
                } catch (RuntimeException e) {
                    task.state(Task.ERROR, R.string.update_install_failed);
                }
            }
        } else if (result == PackageInstaller.STATUS_SUCCESS) {
            task.state(Task.DONE, R.string.update_installed);
        } else {
            task.state(Task.ERROR, result == PackageInstaller.STATUS_FAILURE_ABORTED
                    ? R.string.update_cancelled : R.string.update_install_failed);
        }
        return true;
    }

    private void act() {
        if (task.stage == Task.AVAILABLE) task.download();
        else if (task.stage == Task.READY) install();
        else if (task.stage == Task.DONE) {
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
        } else if (task.stage == Task.ERROR) task.check(null);
    }

    private void install() {
        if (task.stage != Task.READY) return;
        if (!getPackageManager().canRequestPackageInstalls()) {
            waitingForPermission = true;
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
            } catch (RuntimeException e) {
                waitingForPermission = false;
                task.state(Task.ERROR, R.string.update_install_failed);
            }
            return;
        }
        task.prepare();
    }

    @SuppressWarnings("deprecation") // Android 15 uses the original background-start mode.
    private void continueInstall() {
        if (!resumed || waitingForPermission || isFinishing()) return;
        if (task.stage == Task.READY && task.autoInstall) {
            task.autoInstall = false;
            install();
        } else if (task.stage == Task.PREPARED) {
            task.state(Task.INSTALLING, R.string.update_installing);
            try {
                Intent result = new Intent(this, UpdateActivity.class).setAction(INSTALL_RESULT)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                Bundle options = null;
                if (Build.VERSION.SDK_INT >= 35) {
                    int mode = Build.VERSION.SDK_INT >= 36
                            ? ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                            : ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED;
                    options = ActivityOptions.makeBasic().setPendingIntentCreatorBackgroundActivityStartMode(
                            mode).toBundle();
                }
                PendingIntent callback = PendingIntent.getActivity(this, task.sessionId, result,
                        PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT, options);
                try (PackageInstaller.Session session = getPackageManager().getPackageInstaller()
                        .openSession(task.sessionId)) {
                    session.commit(callback.getIntentSender());
                }
            } catch (Exception e) {
                task.abandon();
                task.state(Task.ERROR, R.string.update_install_failed);
            }
        }
    }

    private void render() {
        if (isFinishing() || isDestroyed()) return;
        String message = getString(task.message);
        if (task.stage == Task.AVAILABLE) message = getString(R.string.update_available_body, task.release.version);
        if (task.stage == Task.DOWNLOADING) message = getString(R.string.update_downloading, task.percent);
        status.setText(message);
        boolean busy = task.stage == Task.CHECKING || task.stage == Task.DOWNLOADING
                || task.stage == Task.PREPARING || task.stage == Task.PREPARED || task.stage == Task.INSTALLING;
        if (loading != null && busy != loadingActive) {
            if (busy) loading.start(); else loading.stop();
            loadingActive = busy;
        }
        action.setVisibility(busy || task.stage == Task.CURRENT ? View.GONE : View.VISIBLE);
        action.setText(task.stage == Task.ERROR ? R.string.update_retry
                : task.stage == Task.DONE ? R.string.update_open
                : task.stage == Task.READY ? R.string.update_install : R.string.update_action);
    }

    /** Retained work holds only the application context; rotating the screen does not restart it. */
    private static final class Task {
        static final int CHECKING = 0, AVAILABLE = 1, DOWNLOADING = 2, READY = 3,
                PREPARING = 4, PREPARED = 5, INSTALLING = 6, CURRENT = 7, ERROR = 8, DONE = 9;
        final Context app;
        final Handler main = new Handler(Looper.getMainLooper());
        WeakReference<UpdateActivity> listener = new WeakReference<>(null);
        Updates.Release release;
        File file;
        int stage = CHECKING;
        int message = R.string.update_checking;
        int percent;
        int sessionId = -1;
        boolean autoInstall;
        boolean autoDownload;
        volatile boolean closed;

        Task(Context app) { this.app = app; }

        void state(int stage, int message) {
            this.stage = stage;
            this.message = message;
            UpdateActivity activity = listener.get();
            if (activity != null) {
                activity.render();
                activity.continueInstall();
            }
        }

        void check(String knownRelease) {
            state(CHECKING, R.string.update_checking);
            new Thread(() -> {
                try {
                    Updates.Release found = knownRelease != null ? Updates.parse(app, knownRelease) : Updates.latest(app);
                    main.post(() -> {
                        release = found;
                        state(found == null ? CURRENT : AVAILABLE,
                                found == null ? R.string.update_current : R.string.update_available_body);
                        if (found != null && autoDownload && !closed) {
                            autoDownload = false;
                            download();
                        }
                    });
                } catch (Exception e) {
                    main.post(() -> state(ERROR, R.string.update_check_failed));
                }
            }, "bubble-update-check").start();
        }

        void download() {
            state(DOWNLOADING, R.string.update_downloading);
            new Thread(() -> {
                try {
                    File downloaded = Updates.download(app, release, value -> main.post(() -> {
                        percent = value;
                        if (stage == DOWNLOADING) state(DOWNLOADING, R.string.update_downloading);
                    }));
                    main.post(() -> {
                        if (closed) {
                            downloaded.delete();
                            return;
                        }
                        file = downloaded;
                        autoInstall = true;
                        state(READY, R.string.update_ready);
                    });
                } catch (Exception e) {
                    main.post(() -> state(ERROR, R.string.update_download_failed));
                }
            }, "bubble-update-download").start();
        }

        void prepare() {
            state(PREPARING, R.string.update_installing);
            new Thread(() -> {
                try {
                    Updates.verifyPackage(app, file, release.code);
                    PackageInstaller installer = app.getPackageManager().getPackageInstaller();
                    PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                            PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                    params.setAppPackageName(app.getPackageName());
                    params.setSize(file.length());
                    params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
                    sessionId = installer.createSession(params);
                    try (PackageInstaller.Session session = installer.openSession(sessionId);
                         FileInputStream in = new FileInputStream(file);
                         OutputStream out = session.openWrite("base.apk", 0, file.length())) {
                        byte[] buffer = new byte[32 * 1024];
                        int n;
                        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                        session.fsync(out);
                    }
                    file.delete();
                    main.post(() -> {
                        if (closed) abandon();
                        else state(PREPARED, R.string.update_installing);
                    });
                } catch (Exception e) {
                    abandon();
                    main.post(() -> state(ERROR, R.string.update_install_failed));
                }
            }, "bubble-update-install").start();
        }

        void abandon() {
            if (sessionId < 0) return;
            try {
                app.getPackageManager().getPackageInstaller().abandonSession(sessionId);
            } catch (RuntimeException ignored) {
                // The system may already have finished or removed the session.
            }
            sessionId = -1;
        }
    }
}
