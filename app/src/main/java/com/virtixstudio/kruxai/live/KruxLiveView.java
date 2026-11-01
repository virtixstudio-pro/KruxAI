package com.virtixstudio.kruxai.live;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.Choreographer;
import android.view.View;

/**
 * Visage de KRUX en mode Live : fond 100 % noir, traits seulement, dessiné image par image (60 i/s ou plus selon l'écran).
 * Toute l'animation vient de LiveMotion ; cette classe ne fait que dessiner. Coordonnées du dessin : 512 x 512.
 */
public class KruxLiveView extends View implements Choreographer.FrameCallback {

    private static final int CY = 0xFF22D3EE, VI = 0xFFA855F7, PK = 0xFFFF7AA8;

    private final LiveMotion motion = new LiveMotion(System.nanoTime());
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private long lastNanos;
    private boolean running;

    public KruxLiveView(Context c) {
        super(c);
        setBackgroundColor(Color.BLACK);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    public LiveMotion motion() { return motion; }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); start(); }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }

    public void start() {
        if (running) return;
        running = true;
        lastNanos = 0;
        Choreographer.getInstance().postFrameCallback(this);
    }

    public void stop() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!running) return;
        float dt = lastNanos == 0 ? 1f / 60f : (frameTimeNanos - lastNanos) / 1_000_000_000f;
        lastNanos = frameTimeNanos;
        motion.update(dt);
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void stroke(Canvas c, int color, float w, float alpha) {
        line.setColor(color);
        line.setAlpha(Math.round(255 * Math.max(0f, Math.min(1f, alpha))));
        line.setStrokeWidth(w);
        c.drawPath(path, line);
    }

    /** Trait avec halo : un trait large et transparent dessous, le trait net dessus. */
    private void glowStroke(Canvas c, int color, float w, float glow) {
        if (glow > 0.05f) stroke(c, color, w + 16f, glow * 0.22f);
        stroke(c, color, w, 1f);
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(Color.BLACK);
        final LiveMotion m = motion;
        float side = Math.min(getWidth(), getHeight()) * 0.94f;
        float s = side / 512f;
        c.save();
        c.translate((getWidth() - side) / 2f, (getHeight() - side) / 2f - getHeight() * 0.04f);
        c.scale(s, s);

        // Transformation de la tête (rotation + respiration autour du centre du visage)
        c.translate(m.headX, m.headY);
        c.rotate(m.roll, 256f, 300f);
        c.scale(m.scale, m.scale, 256f, 300f);

        final float g = m.glow;

        // ---- Casque
        path.reset();
        path.moveTo(66, 252); path.cubicTo(52, 170, 108, 90, 190, 78);
        path.moveTo(446, 252); path.cubicTo(460, 170, 404, 90, 322, 78);
        glowStroke(c, CY, 9f, g);
        path.reset();
        path.moveTo(70, 232); path.arcTo(70, 212, 110, 252, 180, 180, false); path.rLineTo(0, 54);
        path.arcTo(70, 266, 110, 306, 0, 180, false); path.close();
        glowStroke(c, CY, 9f, g);
        path.reset();
        path.moveTo(402, 232); path.arcTo(402, 212, 442, 252, 180, 180, false); path.rLineTo(0, 54);
        path.arcTo(402, 266, 442, 306, 0, 180, false); path.close();
        glowStroke(c, CY, 9f, g);

        // ---- Visage
        path.reset();
        path.moveTo(256, 96);
        path.cubicTo(366, 96, 436, 176, 436, 264);
        path.cubicTo(436, 360, 364, 428, 256, 428);
        path.cubicTo(148, 428, 76, 360, 76, 264);
        path.cubicTo(76, 176, 146, 96, 256, 96);
        path.close();
        glowStroke(c, CY, 11f, g * 0.6f);

        // ---- Mèche
        path.reset();
        path.moveTo(128, 190); path.cubicTo(160, 140, 232, 118, 306, 130);
        path.cubicTo(270, 138, 220, 152, 196, 188); path.cubicTo(176, 170, 150, 172, 128, 190); path.close();
        stroke(c, VI, 9f, 1f);

        // ---- Antenne et boule
        path.reset();
        path.moveTo(256, 96); path.cubicTo(252, 64, 276, 44, 302, 52);
        stroke(c, VI, 8f, 1f);
        float r = 10f * (1f + 0.3f * m.orb * (0.5f + g));
        fill.setColor(CY);
        fill.setAlpha(Math.round(255 * (0.25f + 0.2f * g)));
        c.drawCircle(302, 52, r * 2.1f, fill);
        fill.setAlpha(255);
        c.drawCircle(302, 52, r, fill);

        // ---- Pince "K"
        rect.set(348, 146, 388, 186);
        line.setColor(CY); line.setAlpha(255); line.setStrokeWidth(6f);
        c.drawOval(rect, line);
        path.reset();
        path.moveTo(360, 154); path.rLineTo(0, 24);
        path.moveTo(362, 166); path.rLineTo(12, -12);
        path.moveTo(363, 165); path.rLineTo(12, 13);
        stroke(c, VI, 5f, 1f);

        // ---- Sourcils
        float lift = -m.browUp * 16f;
        path.reset();
        path.moveTo(160, 214 + lift + m.browUp * 4f); path.quadTo(196, 196 + lift, 230, 212 + lift);
        path.moveTo(282, 212 + lift); path.quadTo(316, 196 + lift, 352, 214 + lift + m.browUp * 4f);
        stroke(c, VI, 8f, 1f);

        // ---- Yeux
        drawEye(c, 196f, 268f, m);
        drawEye(c, 316f, 268f, m);

        // ---- Joues
        path.reset();
        path.moveTo(124, 318); path.rLineTo(14, -10);
        path.moveTo(140, 326); path.rLineTo(14, -10);
        path.moveTo(358, 308); path.rLineTo(14, 10);
        path.moveTo(374, 300); path.rLineTo(14, 10);
        stroke(c, PK, 7f, 0.75f + 0.25f * m.happy);

        // ---- Bouche : ferme = sourire, ouvre = ovale qui bouge
        float o = m.mouth, sm = m.smile;
        float hw = 32f + (sm - 1f) * 10f;
        float topCtrl = lerp(340f + 32f * sm, 346f, o);
        float botCtrl = lerp(342f + 32f * sm, 340f + 64f, o);
        path.reset();
        path.moveTo(256f - hw, 340f);
        path.quadTo(256f, topCtrl, 256f + hw, 340f);
        path.quadTo(256f, botCtrl, 256f - hw, 340f);
        stroke(c, VI, 9f, 1f);

        c.restore();
    }

    private void drawEye(Canvas c, float cx, float cy, LiveMotion m) {
        float open = Math.max(0.08f, m.eyeOpen);
        float ry = 34f * open;
        float notHappy = 1f - m.happy;
        if (notHappy > 0.02f) {
            rect.set(cx - 28f, cy - ry, cx + 28f, cy + ry);
            line.setColor(VI); line.setStrokeWidth(9f);
            line.setAlpha(Math.round(255 * notHappy));
            c.drawOval(rect, line);
            if (open > 0.3f) {
                float px = cx + m.eyeX * 9f, py = cy + 4f + m.eyeY * 8f;
                fill.setColor(VI); fill.setAlpha(Math.round(255 * notHappy));
                c.drawCircle(px, py, 14f * Math.min(1f, open + 0.2f), fill);
                fill.setColor(CY); fill.setAlpha(Math.round(255 * notHappy));
                c.drawCircle(px - 5f, py - 8f, 4.5f, fill);
            }
        }
        if (m.happy > 0.02f) {
            path.reset();
            path.moveTo(cx - 32f, cy + 10f); path.quadTo(cx, cy - 26f, cx + 32f, cy + 10f);
            stroke(c, VI, 10f, m.happy);
        }
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}
