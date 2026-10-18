package org.bubble.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * A thin full-width bar that shows Bubble is working, in the logo's colors: a lilac glint sliding
 * over a faint track of the logo's black. It never blocks the screen. Starts and stops are counted,
 * so overlapping work keeps it running until the last one ends; when idle it keeps its space
 * (invisible), so nothing jumps.
 */
final class LoadingBar extends View {

    private static final int LILAC = 0xFF9D7BFF;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
    private int running;
    private float phase;

    LoadingBar(Context c) {
        super(c);
        track.setColor(Ui.night(c) ? 0x26FFFFFF : 0x1F1C1F23);
        anim.setDuration(1100);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            invalidate();
        });
        setVisibility(INVISIBLE);
        setContentDescription(c.getString(R.string.working));
    }

    void start() {
        if (running++ == 0) {
            setVisibility(VISIBLE);
            anim.start();
        }
    }

    void stop() {
        if (running > 0 && --running == 0) {
            anim.cancel();
            setVisibility(INVISIBLE);
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight(), r = h / 2f;
        canvas.drawRoundRect(0, 0, w, h, r, r, track);
        float seg = w * 0.35f;
        float x = phase * (w + seg) - seg;
        int ink = Ui.night(getContext()) ? 0x00FFFFFF : 0x001C1F23;
        glint.setShader(new LinearGradient(x, 0, x + seg, 0,
                new int[]{ink, LILAC, Ui.night(getContext()) ? 0xFFFFFFFF : Ui.INK},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(Math.max(0, x), 0, Math.min(w, x + seg), h, r, r, glint);
    }

    @Override protected void onDetachedFromWindow() {
        anim.cancel();
        super.onDetachedFromWindow();
    }
}
