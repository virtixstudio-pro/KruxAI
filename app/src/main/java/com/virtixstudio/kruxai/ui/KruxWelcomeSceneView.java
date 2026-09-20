package com.virtixstudio.kruxai.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.virtixstudio.kruxai.R;

public class KruxWelcomeSceneView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private Bitmap backgroundBitmap;

    private boolean animating = true;
    private long animationStart;

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
        setLayerType(View.LAYER_TYPE_HARDWARE, null);

        backgroundBitmap = BitmapFactory.decodeResource(
                getResources(),
                R.drawable.krux_welcome_background
        );

        animationStart = System.currentTimeMillis();
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

        canvas.drawColor(Color.rgb(4, 2, 16));

        if (backgroundBitmap == null) {
            return;
        }

        float width = getWidth();
        float height = getHeight();

        long elapsed = System.currentTimeMillis() - animationStart;

        /*
         * Mouvement très lent et organique.
         * L'objectif est de donner l'impression que la nébuleuse
         * respire sans transformer l'écran en animation agressive.
         */
        float seconds = elapsed / 1000f;

        float breathing =
                (float) Math.sin(seconds * 0.32f) * 0.012f;

        float driftX =
                (float) Math.sin(seconds * 0.18f) * width * 0.008f;

        float driftY =
                (float) Math.cos(seconds * 0.15f) * height * 0.006f;

        float scale = 1.035f + breathing;

        float bitmapWidth = backgroundBitmap.getWidth();
        float bitmapHeight = backgroundBitmap.getHeight();

        float viewRatio = width / height;
        float bitmapRatio = bitmapWidth / bitmapHeight;

        float drawWidth;
        float drawHeight;

        if (bitmapRatio > viewRatio) {
            drawHeight = height * scale;
            drawWidth = drawHeight * bitmapRatio;
        } else {
            drawWidth = width * scale;
            drawHeight = drawWidth / bitmapRatio;
        }

        float left = (width - drawWidth) / 2f + driftX;
        float top = (height - drawHeight) / 2f + driftY;

        RectF destination = new RectF(
                left,
                top,
                left + drawWidth,
                top + drawHeight
        );

        paint.setAlpha(255);
        canvas.drawBitmap(backgroundBitmap, null, destination, paint);

        /*
         * Voile sombre très léger pour conserver la lisibilité
         * du texte et des contrôles au-dessus de la nébuleuse.
         */
        paint.setColor(Color.argb(28, 3, 1, 12));
        canvas.drawRect(0, 0, width, height, paint);

        if (animating) {
            postInvalidateOnAnimation();
        }
    }
}
