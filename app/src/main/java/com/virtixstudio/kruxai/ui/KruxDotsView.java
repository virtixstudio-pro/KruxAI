package com.virtixstudio.kruxai.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Trois points qui rebondissent, animés au rythme de l'écran (fluide). Même API que KruxStatusView. */
public class KruxDotsView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator anim;
    private float t = 0f;
    private boolean active = false;
    private int color = Color.WHITE;

    public KruxDotsView(Context c) { super(c); }
    public KruxDotsView(Context c, AttributeSet a) { super(c, a); }

    public void setColor(int c) { color = c; invalidate(); }

    public void setActive(boolean on) {
        if (on == active) return;
        active = on;
        if (on) {
            startAnim();
        } else {
            if (anim != null) anim.cancel();
            t = 0f;
            invalidate();
        }
    }

    private void startAnim() {
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(1200);
        anim.setInterpolator(new LinearInterpolator());
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (active) startAnim();
    }

    @Override protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float r = Math.min(w / 14f, h / 5f);
        float gap = r * 3.4f;
        float cx = w / 2f - gap;
        float cy = h * 0.62f;
        for (int i = 0; i < 3; i++) {
            float phase = t - i * 0.14f;
            float s = (float) Math.sin(phase * 2 * Math.PI);
            float bounce = active ? Math.max(0f, s) : 0f;
            float y = cy - bounce * r * 1.8f;
            float rad = r * (1f + 0.22f * bounce);
            int alpha = (int) (255 * (0.4f + 0.6f * (active ? bounce : 0.4f)));
            paint.setColor(color);
            paint.setAlpha(alpha);
            canvas.drawCircle(cx + i * gap, y, rad, paint);
        }
    }
}
