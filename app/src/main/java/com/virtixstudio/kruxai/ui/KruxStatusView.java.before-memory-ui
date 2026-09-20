package com.virtixstudio.kruxai.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

public class KruxStatusView extends View {

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint kPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float rotation = 0f;
    private ValueAnimator animator;

    public KruxStatusView(Context context) {
        super(context);
        init();
    }

    public KruxStatusView(Context context, @Nullable AttributeSet attrs) {
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
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(2.2f);
        ringPaint.setColor(0xFFB56CFF);
        ringPaint.setAlpha(210);

        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(0xFFE0B8FF);

        kPaint.setStyle(Paint.Style.STROKE);
        kPaint.setStrokeWidth(2.8f);
        kPaint.setStrokeCap(Paint.Cap.ROUND);
        kPaint.setStrokeJoin(Paint.Join.ROUND);
        kPaint.setColor(0xFFF7EFFF);
        kPaint.setShadowLayer(8f, 0f, 0f, 0xFFA855F7);

        startAnimation();
    }

    private void startAnimation() {
        if (animator != null) {
            animator.cancel();
        }

        animator = ValueAnimator.ofFloat(0f, 360f);
        animator.setDuration(2200L);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(animation -> {
            rotation = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) * 0.28f;

        RectF orbit = new RectF(
                cx - radius,
                cy - radius,
                cx + radius,
                cy + radius
        );

        canvas.drawArc(orbit, rotation, 285f, false, ringPaint);

        double angle = Math.toRadians(rotation - 90f);
        float dotX = cx + (float) Math.cos(angle) * radius;
        float dotY = cy + (float) Math.sin(angle) * radius;

        canvas.drawCircle(dotX, dotY, 4.2f, dotPaint);

        float kSize = radius * 0.95f;
        float left = cx - kSize * 0.38f;
        float top = cy - kSize * 0.55f;
        float bottom = cy + kSize * 0.55f;
        float midX = cx + kSize * 0.05f;
        float upperX = cx + kSize * 0.42f;
        float lowerX = cx + kSize * 0.42f;

        canvas.drawLine(left, top, left, bottom, kPaint);
        canvas.drawLine(left, cy, upperX, top, kPaint);
        canvas.drawLine(left, cy, lowerX, bottom, kPaint);
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
