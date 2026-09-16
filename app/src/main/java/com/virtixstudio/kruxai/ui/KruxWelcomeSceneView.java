package com.virtixstudio.kruxai.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class KruxWelcomeSceneView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float rotation;
    private boolean animating = true;
    private String scene = "blackhole";

    public KruxWelcomeSceneView(Context context) {
        super(context);
        init();
    }

    public KruxWelcomeSceneView(Context context, @Nullable AttributeSet attrs) {
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
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setScene(String scene) {
        if (scene != null && !scene.trim().isEmpty()) {
            this.scene = scene;
        }
        invalidate();
    }

    public void setAnimating(boolean animating) {
        this.animating = animating;
        if (animating) {
            postInvalidateOnAnimation();
        } else {
            invalidate();
        }
    }

    public void resetAnimation() {
        rotation = 0f;
        setAnimating(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float centerX = getWidth() * 0.5f;
        float centerY = getHeight() * 0.43f;
        float radius = Math.min(getWidth(), getHeight()) * 0.25f;

        drawScene(canvas, centerX, centerY, radius);

        if (animating) {
            rotation = (rotation + 0.7f) % 360f;
            postInvalidateOnAnimation();
        }
    }

    private void drawScene(
            Canvas canvas,
            float centerX,
            float centerY,
            float radius
    ) {
        if ("aurora".equals(scene)) {
            drawAurora(canvas, centerX, centerY, radius);
            return;
        }

        if ("minimal".equals(scene)) {
            drawMinimal(canvas, centerX, centerY, radius);
            return;
        }

        drawBlackHole(canvas, centerX, centerY, radius);
    }

    private void drawBlackHole(
            Canvas canvas,
            float centerX,
            float centerY,
            float radius
    ) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(
                centerX,
                centerY,
                radius * 1.55f,
                new int[]{0xEE05030A, 0xAA32124F, 0x003B164E},
                null,
                Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(centerX, centerY, radius * 1.55f, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(Math.max(3f, radius * 0.035f));
        paint.setColor(0xC8D77BFF);
        canvas.save();
        canvas.rotate(rotation, centerX, centerY);
        canvas.drawOval(
                centerX - radius * 1.18f,
                centerY - radius * 0.40f,
                centerX + radius * 1.18f,
                centerY + radius * 0.40f,
                paint
        );
        paint.setStrokeWidth(Math.max(1.5f, radius * 0.018f));
        paint.setColor(0xA8F0B7FF);
        canvas.drawOval(
                centerX - radius * 0.92f,
                centerY - radius * 0.30f,
                centerX + radius * 0.92f,
                centerY + radius * 0.30f,
                paint
        );
        canvas.restore();

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF030207);
        canvas.drawCircle(centerX, centerY, radius * 0.48f, paint);
        paint.setColor(0xFFDAB8FF);
        canvas.drawCircle(
                centerX + (float) Math.cos(Math.toRadians(rotation)) * radius * 1.18f,
                centerY + (float) Math.sin(Math.toRadians(rotation)) * radius * 0.40f,
                radius * 0.055f,
                paint
        );
    }

    private void drawAurora(
            Canvas canvas,
            float centerX,
            float centerY,
            float radius
    ) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(3f, radius * 0.045f));
        paint.setColor(0xB535D4FF);
        canvas.drawCircle(centerX, centerY, radius * 0.72f, paint);
        paint.setStrokeWidth(Math.max(2f, radius * 0.025f));
        paint.setColor(0xB864F7A8);
        canvas.save();
        canvas.rotate(rotation, centerX, centerY);
        canvas.drawOval(
                centerX - radius * 1.15f,
                centerY - radius * 0.43f,
                centerX + radius * 1.15f,
                centerY + radius * 0.43f,
                paint
        );
        canvas.restore();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF0A1422);
        canvas.drawCircle(centerX, centerY, radius * 0.48f, paint);
    }

    private void drawMinimal(
            Canvas canvas,
            float centerX,
            float centerY,
            float radius
    ) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, radius * 0.025f));
        paint.setColor(0x889B8AAE);
        canvas.drawCircle(centerX, centerY, radius * 0.68f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF18121F);
        canvas.drawCircle(centerX, centerY, radius * 0.40f, paint);
    }
}
