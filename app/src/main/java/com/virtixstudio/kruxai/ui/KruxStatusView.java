package com.virtixstudio.kruxai.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

public class KruxStatusView extends View {

    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private ValueAnimator animator;
    private float progress = 0f;
    private boolean active = true;

    public KruxStatusView(Context context) {
        super(context);
        init();
    }

    public KruxStatusView(
            Context context,
            @Nullable AttributeSet attrs
    ) {
        super(context, attrs);
        init();
    }

    public KruxStatusView(
            Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(0xFFEBD8FF);

        startAnimation();
    }

    public void setActive(boolean active) {
        this.active = active;
        invalidate();
    }

    private void startAnimation() {
        if (animator != null) {
            animator.cancel();
        }

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1250L);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);

        animator.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            invalidate();
        });

        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;

        float spacing = Math.min(getWidth(), getHeight()) * 0.20f;
        float baseRadius = Math.min(getWidth(), getHeight()) * 0.075f;

        for (int i = 0; i < 3; i++) {

            float phase = (progress + i * 0.22f) % 1f;

            /*
             * Petit mouvement vertical :
             * chaque point monte puis redescend.
             */
            float wave = (float) Math.sin(phase * Math.PI * 2.0);

            float y = cy - wave * baseRadius * 2.2f;

            /*
             * L'opacité suit légèrement le mouvement.
             */
            int alpha;

            if (!active) {
                alpha = 55;
            } else {
                alpha = 80 + (int) ((wave + 1f) * 55f);
            }

            dotPaint.setAlpha(Math.max(35, Math.min(190, alpha)));

            float x = cx + (i - 1) * spacing;

            canvas.drawCircle(
                    x,
                    y,
                    baseRadius,
                    dotPaint
            );
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }

        super.onDetachedFromWindow();
    }
}
