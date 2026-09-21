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

    private void init() {
        setLayerType(View.LAYER_TYPE_HARDWARE, null);

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

        canvas.drawColor(Color.rgb(5, 3, 14));

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
                        Color.argb(72, 123, 64, 255),
                        Color.argb(36, 80, 37, 170),
                        Color.argb(0, 5, 3, 14)
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
                        Color.argb(27, 255, 108, 88),
                        Color.argb(0, 255, 108, 88)
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
                        Color.argb(32, 156, 69, 255),
                        Color.argb(0, 156, 69, 255)
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
                        Color.argb(65, 168, 85, 247),
                        Color.argb(22, 120, 60, 190),
                        Color.argb(0, 5, 3, 14)
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
                        Color.rgb(255, 183, 216),
                        Color.rgb(181, 102, 255),
                        Color.rgb(65, 28, 150),
                        Color.argb(0, 14, 5, 38)
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

            paint.setColor(Color.argb(
                    i == 2 ? 70 : 92,
                    190,
                    137,
                    255
            ));

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

            paint.setColor(Color.argb(
                    (int) alpha,
                    220,
                    193,
                    255
            ));

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
                        Color.argb(
                                0,
                                255,
                                150,
                                190
                        ),
                        Color.argb(
                                65,
                                178,
                                92,
                                255
                        ),
                        Color.argb(
                                0,
                                255,
                                150,
                                190
                        )
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
