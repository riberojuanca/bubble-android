package org.bubble.app;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * One photo or video of a space at full screen, read where it is ({@link SpaceChannel#open}): only
 * that file is opened. Swipe sideways for the next or previous one of the album; a video plays with
 * Android's controls, shown again with a tap.
 */
public final class GalleryViewerActivity extends Activity {

    static final String EXTRA_SPACE = "space", EXTRA_IDS = "ids", EXTRA_MIMES = "mimes", EXTRA_POSITION = "position";

    private String space;
    private ArrayList<String> ids, mimes;
    private int position;
    private FrameLayout stage;
    private TextView counter;
    private GestureDetector gestures;
    /** What is shown now; a load that finishes after the user moved on is dropped. */
    private int shown = -1;
    private ParcelFileDescriptor file;
    private MediaPlayer player;
    private MediaController controls;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) getActionBar().hide();
        space = getIntent().getStringExtra(EXTRA_SPACE);
        ids = getIntent().getStringArrayListExtra(EXTRA_IDS);
        mimes = getIntent().getStringArrayListExtra(EXTRA_MIMES);
        position = getIntent().getIntExtra(EXTRA_POSITION, 0);
        if (space == null || ids == null || mimes == null || ids.size() != mimes.size() || ids.isEmpty()) {
            finish();
            return;
        }
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        root.setFitsSystemWindows(true);
        stage = new FrameLayout(this);
        root.addView(stage, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        counter = Ui.text(this, "", android.R.style.TextAppearance_DeviceDefault_Small);
        counter.setTextColor(0xCCFFFFFF);
        counter.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        root.addView(counter, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        setContentView(root);
        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onFling(MotionEvent from, MotionEvent to, float vx, float vy) {
                if (Math.abs(vx) < Math.abs(vy) * 1.5f || Math.abs(vx) < 600) return false;
                show(position + (vx < 0 ? 1 : -1));
                return true;
            }

            @Override public boolean onSingleTapUp(MotionEvent e) {
                if (controls != null) controls.show();
                return controls != null;
            }
        });
        show(position);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (gestures != null && gestures.onTouchEvent(event)) return true;
        return super.dispatchTouchEvent(event);
    }

    @Override protected void onPause() {
        super.onPause();
        if (player != null && player.isPlaying()) player.pause();
    }

    @Override protected void onDestroy() {
        release();
        super.onDestroy();
    }

    private void show(int at) {
        if (at < 0 || at >= ids.size()) return;
        position = at;
        shown = at;
        release();
        stage.removeAllViews();
        counter.setText(getString(R.string.gallery_position, at + 1, ids.size()));
        String id = ids.get(at);
        boolean video = mimes.get(at).startsWith("video/");
        int width = getResources().getDisplayMetrics().widthPixels, height = getResources().getDisplayMetrics().heightPixels;
        new Thread(() -> {
            try {
                ParcelFileDescriptor opened = SpaceChannel.open(this, space, id);
                if (video) {
                    runOnUiThread(() -> {
                        if (shown != at || isDestroyed()) closeQuietly(opened);
                        else playVideo(opened);
                    });
                    return;
                }
                Bitmap bitmap;
                try {
                    // Decoded at the screen's size (a camera photo is far larger), turned by its EXIF.
                    bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(
                            () -> new AssetFileDescriptor(opened, 0, AssetFileDescriptor.UNKNOWN_LENGTH)), (decoder, info, source) -> {
                        int sample = Math.max(1, Math.min(info.getSize().getWidth() / width, info.getSize().getHeight() / height));
                        decoder.setTargetSampleSize(sample);
                    });
                } finally {
                    closeQuietly(opened);
                }
                runOnUiThread(() -> {
                    if (shown != at || isDestroyed()) return;
                    ImageView image = new ImageView(this);
                    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    image.setImageBitmap(bitmap);
                    stage.addView(image, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (shown != at || isDestroyed()) return;
                    TextView failed = Ui.text(this, getString(R.string.gallery_open_failed), android.R.style.TextAppearance_DeviceDefault_Medium);
                    failed.setTextColor(0xCCFFFFFF);
                    failed.setGravity(Gravity.CENTER);
                    stage.addView(failed, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                });
            }
        }, "bubble-gallery-open").start();
    }

    /** The video plays from its file where it is, kept open until another one is shown. */
    private void playVideo(ParcelFileDescriptor opened) {
        file = opened;
        MediaPlayer mp = new MediaPlayer();
        player = mp;
        SurfaceView surface = new SurfaceView(this);
        stage.addView(surface, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        controls = new MediaController(this);
        controls.setAnchorView(stage);
        controls.setMediaPlayer(new Control(mp));
        mp.setOnVideoSizeChangedListener((p, w, h) -> {
            if (w <= 0 || h <= 0) return;
            // Fit the frame inside the stage, keeping its shape.
            float scale = Math.min(stage.getWidth() / (float) w, stage.getHeight() / (float) h);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (w * scale), (int) (h * scale), Gravity.CENTER);
            surface.setLayoutParams(lp);
        });
        mp.setOnPreparedListener(p -> {
            p.start();
            controls.show();
        });
        mp.setOnErrorListener((p, what, extra) -> {
            android.widget.Toast.makeText(this, R.string.gallery_open_failed, android.widget.Toast.LENGTH_SHORT).show();
            return true;
        });
        surface.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override public void surfaceCreated(SurfaceHolder holder) {
                if (player != mp) return;
                try {
                    mp.setDisplay(holder);
                    mp.setDataSource(opened.getFileDescriptor());
                    mp.prepareAsync();
                } catch (Exception e) {
                    android.widget.Toast.makeText(GalleryViewerActivity.this, R.string.gallery_open_failed,
                            android.widget.Toast.LENGTH_SHORT).show();
                }
            }

            @Override public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {}

            @Override public void surfaceDestroyed(SurfaceHolder holder) {
                if (player == mp) mp.setDisplay(null);
            }
        });
    }

    private void release() {
        if (controls != null) controls.hide();
        controls = null;
        if (player != null) player.release();
        player = null;
        closeQuietly(file);
        file = null;
    }

    private static void closeQuietly(ParcelFileDescriptor f) {
        try {
            if (f != null) f.close();
        } catch (Exception ignored) {
            // Already closed.
        }
    }

    /** Android's video controls driving the player. */
    private static final class Control implements MediaController.MediaPlayerControl {
        private final MediaPlayer mp;

        Control(MediaPlayer mp) { this.mp = mp; }

        @Override public void start() { mp.start(); }
        @Override public void pause() { mp.pause(); }
        @Override public int getDuration() { return safe(mp::getDuration); }
        @Override public int getCurrentPosition() { return safe(mp::getCurrentPosition); }
        @Override public void seekTo(int ms) { mp.seekTo(ms); }
        @Override public boolean isPlaying() { return safe(() -> mp.isPlaying() ? 1 : 0) == 1; }
        @Override public int getBufferPercentage() { return 100; }
        @Override public boolean canPause() { return true; }
        @Override public boolean canSeekBackward() { return true; }
        @Override public boolean canSeekForward() { return true; }
        @Override public int getAudioSessionId() { return safe(mp::getAudioSessionId); }

        private interface Read { int get(); }

        /** The player throws once released or before it is ready; the controls just show nothing. */
        private static int safe(Read read) {
            try {
                return read.get();
            } catch (IllegalStateException e) {
                return 0;
            }
        }
    }
}
