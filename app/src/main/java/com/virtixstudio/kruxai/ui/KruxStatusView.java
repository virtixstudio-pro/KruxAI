package com.virtixstudio.kruxai.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

/**
 * Indicateur de travail de Krux : trois points qui se déplacent et tracent un K,
 * le tiennent un instant, puis l'effacent et reviennent en ligne. Boucle continue.
 */
public class KruxStatusView extends View {

    private static final long CYCLE_MS = 2400L;

    /* K en coordonnées normalisées (-1..1) : une barre et deux branches, un trait par point. */
    private static final float[][] START = {
            {-0.55f, -0.90f},   // point 1 : la barre, de haut en bas
            {-0.55f, 0.05f},    // point 2 : la branche du haut
            {-0.55f, 0.05f}     // point 3 : la branche du bas
    };
    private static final float[][] END = {
            {-0.55f, 0.90f},
            {0.70f, -0.90f},
            {0.70f, 0.90f}
    };
    /* Position de repos : trois points en ligne. */
    private static final float[][] ROW = {
            {-0.55f, 0f},
            {0f, 0f},
            {0.55f, 0f}
    };

    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private ValueAnimator animator;
    private float progress = 0f;
    private boolean active = true;

    public KruxStatusView(Context context) {
        super(context);
        init();
    }

    public KruxStatusView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public KruxStatusView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        int accent = KruxTheme.get(getContext(), "accent");
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(accent);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setColor(accent);
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
        animator.setDuration(CYCLE_MS);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private static float ease(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x < 0.5f ? 4f * x * x * x : 1f - (float) Math.pow(-2f * x + 2f, 3f) / 2f;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float size = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float half = size * 0.40f;
        float stroke = size * 0.12f;
        float headRadius = stroke * 0.62f;

        linePaint.setStrokeWidth(stroke);

        float t = progress;
        float baseAlpha = active ? 1f : 0.35f;

        for (int i = 0; i < 3; i++) {
            float px;
            float py;
            float trail = 0f;       // part du trait déjà tracé (0..1)
            float trailAlpha = 0f;  // visibilité du trait

            if (t < 0.12f) {
                // Les points quittent la ligne pour rejoindre leur départ.
                float e = ease(t / 0.12f);
                px = lerp(ROW[i][0], START[i][0], e);
                py = lerp(ROW[i][1], START[i][1], e);
            } else if (t < 0.55f) {
                // Chaque point trace son trait du K.
                trail = ease((t - 0.12f) / 0.43f);
                px = lerp(START[i][0], END[i][0], trail);
                py = lerp(START[i][1], END[i][1], trail);
                trailAlpha = 1f;
            } else if (t < 0.75f) {
                // Le K est tenu un instant.
                trail = 1f;
                px = END[i][0];
                py = END[i][1];
                trailAlpha = 1f;
            } else {
                // Le K s'efface pendant que les points reviennent en ligne.
                float f = (t - 0.75f) / 0.25f;
                float e = ease(f);
                trail = 1f;
                px = lerp(END[i][0], ROW[i][0], e);
                py = lerp(END[i][1], ROW[i][1], e);
                trailAlpha = 1f - Math.min(1f, f * 1.4f);
            }

            if (trailAlpha > 0f && trail > 0f) {
                float sx = cx + START[i][0] * half;
                float sy = cy + START[i][1] * half;
                float ex = cx + lerp(START[i][0], END[i][0], trail) * half;
                float ey = cy + lerp(START[i][1], END[i][1], trail) * half;
                if (t >= 0.75f) {
                    // Pendant l'effacement, le trait reste le K complet.
                    ex = cx + END[i][0] * half;
                    ey = cy + END[i][1] * half;
                }
                linePaint.setAlpha((int) (200 * trailAlpha * baseAlpha));
                canvas.drawLine(sx, sy, ex, ey, linePaint);
            }

            dotPaint.setAlpha((int) (235 * baseAlpha));
            canvas.drawCircle(cx + px * half, cy + py * half, headRadius, dotPaint);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // La liste recycle les vues : l'animation repart quand la vue revient à l'écran.
        if (animator == null) startAnimation();
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
