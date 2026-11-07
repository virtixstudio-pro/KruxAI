package com.virtixstudio.kruxai.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class KruxWelcomeSceneView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean animating = true;
    private long animationStart;

    public KruxWelcomeSceneView(Context context) {
        super(context);
        init();
    }

    public KruxWelcomeSceneView(
            Context context,
            @Nullable AttributeSet attrs
    ) {
        super(context, attrs);
        init();
    }

    public KruxWelcomeSceneView(
            Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
        init();
    }


    // ---- Palette (suit le thème de l'app)
    private int bg = Color.rgb(5, 3, 14);
    private int accent = Color.rgb(168, 85, 247);
    private int warm = Color.rgb(255, 108, 88);

    /** Fond et accent du thème : la scène en dérive toutes ses couleurs. */
    public void setPalette(int background, int accentColor) {
        this.bg = Color.rgb(Color.red(background), Color.green(background), Color.blue(background));
        this.accent = Color.rgb(Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor));
        float[] hsv = new float[3];
        Color.colorToHSV(this.accent, hsv);
        hsv[0] = (hsv[0] + 95f) % 360f; // violet d'origine -> corail d'origine
        hsv[1] = 0.65f;
        hsv[2] = 1f;
        this.warm = Color.HSVToColor(hsv);
        invalidate();
    }

    private static int withA(int c, int a) {
        return Color.argb(Math.max(0, Math.min(255, a)), Color.red(c), Color.green(c), Color.blue(c));
    }

    private static int shade(int c, float f) {
        return Color.rgb((int) (Color.red(c) * f), (int) (Color.green(c) * f), (int) (Color.blue(c) * f));
    }

    private static int tint(int c, float t) {
        return Color.rgb(
                (int) (Color.red(c) + (255 - Color.red(c)) * t),
                (int) (Color.green(c) + (255 - Color.green(c)) * t),
                (int) (Color.blue(c) + (255 - Color.blue(c)) * t));
    }

    private void init() {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        animationStart = System.currentTimeMillis();

        setAlpha(0.96f);
    }

    public void setScene(String scene) {
        invalidate();
    }

    public void setAnimating(boolean animating) {
        this.animating = animating;

        if (animating) {
            animationStart = System.currentTimeMillis();
            postInvalidateOnAnimation();
        } else {
            invalidate();
        }
    }

    public void resetAnimation() {
        animationStart = System.currentTimeMillis();
        setAnimating(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();

        if (width <= 0 || height <= 0) {
            return;
        }

        canvas.drawColor(bg);

        float t =
                (System.currentTimeMillis() - animationStart)
                        / 1000f;

        drawNebula(canvas, width, height);
        drawOrb(canvas, width, height, t);

        if (animating) {
            postInvalidateOnAnimation();
        }
    }

    private void drawNebula(
            Canvas canvas,
            float width,
            float height
    ) {

        paint.setStyle(Paint.Style.FILL);

        paint.setShader(new RadialGradient(
                width * 0.50f,
                height * 0.43f,
                Math.max(width, height) * 0.55f,
                new int[]{
                        withA(shade(accent, 0.92f), 72),
                        withA(shade(accent, 0.65f), 36),
                        withA(bg, 0)
                },
                new float[]{
                        0f,
                        0.42f,
                        1f
                },
                Shader.TileMode.CLAMP
        ));

        canvas.drawRect(
                0,
                0,
                width,
                height,
                paint
        );


        paint.setShader(new RadialGradient(
                width * 0.08f,
                height * 0.82f,
                width * 0.48f,
                new int[]{
                        withA(warm, 27),
                        withA(warm, 0)
                },
                null,
                Shader.TileMode.CLAMP
        ));

        canvas.drawRect(
                0,
                0,
                width,
                height,
                paint
        );


        paint.setShader(new RadialGradient(
                width * 0.90f,
                height * 0.72f,
                width * 0.44f,
                new int[]{
                        withA(accent, 32),
                        withA(accent, 0)
                },
                null,
                Shader.TileMode.CLAMP
        ));

        canvas.drawRect(
                0,
                0,
                width,
                height,
                paint
        );

        paint.setShader(null);
    }

    private void drawOrb(
            Canvas canvas,
            float width,
            float height,
            float t
    ) {

        float cx = width * 0.50f;
        float cy = height * 0.40f;

        float pulse =
                1f +
                (float) Math.sin(t * 1.1f)
                * 0.025f;

        float radius =
                Math.min(width, height)
                * 0.165f
                * pulse;


        // GLOW

        paint.setStyle(Paint.Style.FILL);

        paint.setShader(new RadialGradient(
                cx,
                cy,
                radius * 1.8f,
                new int[]{
                        withA(accent, 65),
                        withA(shade(accent, 0.76f), 22),
                        withA(bg, 0)
                },
                new float[]{
                        0f,
                        0.35f,
                        1f
                },
                Shader.TileMode.CLAMP
        ));

        canvas.drawCircle(
                cx,
                cy,
                radius * 1.8f,
                paint
        );


        // CORE

        paint.setShader(new RadialGradient(
                cx - radius * 0.28f,
                cy - radius * 0.30f,
                radius * 1.15f,
                new int[]{
                        Color.WHITE,
                        tint(warm, 0.55f),
                        tint(accent, 0.10f),
                        shade(accent, 0.55f),
                        withA(bg, 0)
                },
                new float[]{
                        0f,
                        .08f,
                        .22f,
                        .62f,
                        1f
                },
                Shader.TileMode.CLAMP
        ));

        canvas.drawCircle(
                cx,
                cy,
                radius,
                paint
        );


        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);


        // ORBITES

        float[] rotations = {
                22f,
                -34f,
                62f
        };

        float[] widths = {
                .43f,
                .38f,
                .52f
        };

        float[] heights = {
                .16f,
                .28f,
                .22f
        };

        for (int i = 0; i < rotations.length; i++) {

            canvas.save();

            canvas.rotate(
                    rotations[i]
                            +
                            t * (
                                    i == 0
                                            ? 8f
                                            : i == 1
                                            ? -5f
                                            : 3.5f
                            ),
                    cx,
                    cy
            );

            paint.setStrokeWidth(1.15f);

            paint.setColor(withA(tint(accent, 0.30f), i == 2 ? 70 : 92));

            android.graphics.RectF oval =
                    new android.graphics.RectF(
                            cx - width * widths[i],
                            cy - height * heights[i],
                            cx + width * widths[i],
                            cy + height * heights[i]
                    );

            canvas.drawOval(
                    oval,
                    paint
            );

            canvas.restore();
        }


        // PARTICULES

        paint.setStyle(Paint.Style.FILL);

        for (int i = 0; i < 16; i++) {

            double phase =
                    t * (
                            0.18
                                    +
                                    (i % 4) * 0.035
                    )
                    +
                    i * 0.91;

            float orbit =
                    radius *
                    (
                            1.65f
                                    +
                                    (i % 5) * .22f
                    );

            float px =
                    cx
                            +
                            (float) Math.cos(phase)
                            * orbit;

            float py =
                    cy
                            +
                            (float) Math.sin(phase * 1.17)
                            * orbit
                            * .55f;

            float alpha =
                    65f
                            +
                            50f *
                            (float)
                                    (
                                            (
                                                    Math.sin(
                                                            phase * 1.9
                                                    )
                                                    +
                                                    1f
                                            )
                                            * .5f
                                    );

            paint.setColor(withA(tint(accent, 0.60f), (int) alpha));

            canvas.drawCircle(
                    px,
                    py,
                    1.2f
                            +
                            (i % 3) * .55f,
                    paint
            );
        }


        // LUMIERE ROTATIVE

        paint.setShader(new SweepGradient(
                cx,
                cy,
                new int[]{
                        withA(tint(warm, 0.35f), 0),
                        withA(accent, 65),
                        withA(tint(warm, 0.35f), 0)
                },
                null
        ));

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);

        canvas.save();

        canvas.rotate(
                t * 7f,
                cx,
                cy
        );

        canvas.drawOval(
                new android.graphics.RectF(
                        cx - radius * 2.0f,
                        cy - radius * .72f,
                        cx + radius * 2.0f,
                        cy + radius * .72f
                ),
                paint
        );

        canvas.restore();

        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
    }
}
