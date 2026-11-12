package com.clav.keyboard.ime;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/**
 * The certification badge: a blue scalloped starburst with a white check
 * mark (verified-profile style), drawn on the fly and pulsing softly.
 */
public class VerifiedBadgeView extends View {

    private static final int SPIKES = 12;
    private static final float INNER_RADIUS_RATIO = 0.80f;
    private static final int TOP_COLOR = 0xFF45A3FF;
    private static final int BOTTOM_COLOR = 0xFF1173EE;

    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path badgePath = new Path();
    private final Path checkPath = new Path();

    private ValueAnimator pulseAnimator;
    private boolean pulseRequested;
    private float pulseScale = 1f;

    public VerifiedBadgeView(Context context) {
        this(context, null);
    }

    public VerifiedBadgeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        badgePaint.setStyle(Paint.Style.FILL);
        checkPaint.setStyle(Paint.Style.STROKE);
        checkPaint.setColor(0xFFFFFFFF);
        checkPaint.setStrokeCap(Paint.Cap.ROUND);
        checkPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    /** Starts a subtle infinite scale pulse (no-op if already pulsing). */
    public void startPulse() {
        pulseRequested = true;
        if (pulseAnimator == null) {
            pulseAnimator = ValueAnimator.ofFloat(1f, 1.07f);
            pulseAnimator.setDuration(950L);
            pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
            pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
            pulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            pulseAnimator.addUpdateListener(animation -> {
                pulseScale = (float) animation.getAnimatedValue();
                invalidate();
            });
        }
        if (!pulseAnimator.isStarted()) {
            pulseAnimator.start();
        }
    }

    public void stopPulse() {
        pulseRequested = false;
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (pulseRequested) {
            startPulse();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        float cx = width / 2f;
        float cy = height / 2f;
        float outer = Math.min(width, height) / 2f * 0.97f;
        float inner = outer * INNER_RADIUS_RATIO;

        badgePath.rewind();
        int points = SPIKES * 2;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points - Math.PI / 2.0;
            float radius = (i % 2 == 0) ? outer : inner;
            float x = cx + (float) (Math.cos(angle) * radius);
            float y = cy + (float) (Math.sin(angle) * radius);
            if (i == 0) {
                badgePath.moveTo(x, y);
            } else {
                badgePath.lineTo(x, y);
            }
        }
        badgePath.close();

        badgePaint.setShader(new LinearGradient(
                cx, cy - outer, cx, cy + outer, TOP_COLOR, BOTTOM_COLOR, Shader.TileMode.CLAMP));

        checkPath.rewind();
        checkPath.moveTo(cx - 0.32f * outer, cy + 0.02f * outer);
        checkPath.lineTo(cx - 0.10f * outer, cy + 0.25f * outer);
        checkPath.lineTo(cx + 0.36f * outer, cy - 0.26f * outer);
        checkPaint.setStrokeWidth(0.17f * outer);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        int checkpoint = canvas.save();
        canvas.scale(pulseScale, pulseScale, cx, cy);
        canvas.drawPath(badgePath, badgePaint);
        canvas.drawPath(checkPath, checkPaint);
        canvas.restoreToCount(checkpoint);
    }
}
