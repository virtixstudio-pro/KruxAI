package com.virtixstudio.kruxai.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/** Globe animé (fil de fer) qui entoure le message d'accueil. Couleurs = thème courant. */
public class WelcomeGlobeView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private int accent = Color.rgb(140, 120, 255);
    private boolean lightBg = false;
    private boolean minimal = false;
    private boolean animating = true;
    private long start = SystemClock.uptimeMillis();

    public WelcomeGlobeView(Context c) { super(c); }
    public WelcomeGlobeView(Context c, AttributeSet a) { super(c, a); }
    public WelcomeGlobeView(Context c, AttributeSet a, int s) { super(c, a, s); }

    /** Suit les réglages actuels : fond et couleur d'accent du thème. */
    public void setPalette(int background, int accentColor) {
        accent = Color.rgb(Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor));
        lightBg = (0.299f * Color.red(background) + 0.587f * Color.green(background)
                + 0.114f * Color.blue(background)) > 150f;
        invalidate();
    }

    /** Réglage « Minimal sombre » : pas de halo lumineux. */
    public void setMinimal(boolean minimal) {
        this.minimal = minimal;
        invalidate();
    }

    public void showAnimated() {
        animate().cancel();
        setVisibility(VISIBLE);
        animating = true;
        start = SystemClock.uptimeMillis();
        animate().alpha(1f).setDuration(260).start();
        postInvalidateOnAnimation();
    }

    /** Disparition douce à l'envoi du message. */
    public void hideAnimated() {
        if (getVisibility() != VISIBLE) return;
        animate().cancel();
        animate().alpha(0f).setDuration(280).withEndAction(() -> {
            if (getAlpha() < 0.05f) {
                animating = false;
                setVisibility(GONE);
            }
        }).start();
    }

    private int a(int c, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(c), Color.green(c), Color.blue(c));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float cx = w / 2f, cy = h / 2f;
        float r = Math.min(w, h) * 0.46f;
        float t = (SystemClock.uptimeMillis() - start) / 1000f;
        // Tracé adouci : le titre, le sous-titre et les suggestions passent par-dessus le globe.
        float k = (lightBg ? 1.35f : 1f) * 0.6f;

        if (!minimal) {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(cx, cy, r * 1.25f,
                    new int[]{a(accent, 70), a(accent, 22), a(accent, 0)},
                    new float[]{0.55f, 0.85f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r * 1.25f, paint);
        }
        paint.setShader(new RadialGradient(cx - r * 0.3f, cy - r * 0.35f, r * 1.1f,
                new int[]{a(accent, 36), a(accent, 8)}, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(getResources().getDisplayMetrics().density * 1.2f);
        paint.setColor(a(accent, (int) (150 * k)));
        canvas.drawCircle(cx, cy, r, paint);

        // Parallèles (inclinés), fixes.
        float tilt = 0.28f;
        paint.setColor(a(accent, (int) (70 * k)));
        for (int i = -2; i <= 2; i++) {
            double lat = Math.toRadians(i * 28.0);
            float rx = (float) (r * Math.cos(lat));
            float ry = rx * tilt;
            float oy = (float) (r * Math.sin(lat)) * (1f - tilt * 0.4f);
            oval.set(cx - rx, cy + oy - ry, cx + rx, cy + oy + ry);
            canvas.drawOval(oval, paint);
        }

        // Méridiens qui tournent.
        for (int i = 0; i < 6; i++) {
            double phi = t * 0.45 + i * Math.PI / 6.0;
            float rx = (float) Math.abs(r * Math.sin(phi));
            paint.setColor(a(accent, (int) ((50 + 60 * Math.abs(Math.cos(phi))) * k)));
            oval.set(cx - rx, cy - r, cx + rx, cy + r);
            canvas.drawOval(oval, paint);
        }

        // Petit satellite en orbite.
        double ang = t * 0.9;
        float sx = cx + (float) (Math.cos(ang) * r * 1.12f);
        float sy = cy + (float) (Math.sin(ang) * r * 1.12f * 0.32f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(a(accent, 230));
        canvas.drawCircle(sx, sy, getResources().getDisplayMetrics().density * 3f, paint);

        if (animating) postInvalidateOnAnimation();
    }
}
