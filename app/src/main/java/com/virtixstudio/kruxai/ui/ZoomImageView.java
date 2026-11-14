package com.virtixstudio.kruxai.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

/** Image qu'on peut zoomer (pincer), déplacer (glisser) et agrandir d'un double appui. */
public class ZoomImageView extends ImageView {

    private final Matrix m = new Matrix();
    private final float[] v = new float[9];
    private float fitScale = 1f;
    private final ScaleGestureDetector scaler;
    private final GestureDetector gestures;
    private Runnable onTap;

    public ZoomImageView(Context c) {
        super(c);
        setScaleType(ScaleType.MATRIX);
        scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                zoomBy(d.getScaleFactor(), d.getFocusX(), d.getFocusY());
                return true;
            }
        });
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onScroll(MotionEvent a, MotionEvent b, float dx, float dy) {
                if (scale() > fitScale * 1.02f) { m.postTranslate(-dx, -dy); clamp(); setImageMatrix(m); }
                return true;
            }
            @Override public boolean onDoubleTap(MotionEvent e) {
                if (scale() > fitScale * 1.1f) fit();
                else zoomBy(2.6f, e.getX(), e.getY());
                return true;
            }
            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                if (onTap != null) onTap.run();
                return true;
            }
        });
    }

    /** Appelé sur un simple appui (pour montrer ou cacher les barres). */
    public void setOnSingleTap(Runnable r) { onTap = r; }

    @Override public void setImageBitmap(Bitmap bm) { super.setImageBitmap(bm); fit(); }
    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { super.onSizeChanged(w, h, ow, oh); fit(); }

    private float scale() { m.getValues(v); return v[Matrix.MSCALE_X]; }

    private void fit() {
        Drawable d = getDrawable();
        if (d == null || getWidth() == 0) return;
        float dw = d.getIntrinsicWidth(), dh = d.getIntrinsicHeight();
        if (dw <= 0 || dh <= 0) return;
        fitScale = Math.min(getWidth() / dw, getHeight() / dh);
        m.reset();
        m.postScale(fitScale, fitScale);
        m.postTranslate((getWidth() - dw * fitScale) / 2f, (getHeight() - dh * fitScale) / 2f);
        setImageMatrix(m);
    }

    private void zoomBy(float f, float px, float py) {
        float target = Math.max(fitScale, Math.min(fitScale * 6f, scale() * f));
        float real = target / scale();
        m.postScale(real, real, px, py);
        clamp();
        setImageMatrix(m);
    }

    private void clamp() {
        Drawable d = getDrawable();
        if (d == null) return;
        RectF r = new RectF(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        m.mapRect(r);
        float dx = 0, dy = 0;
        if (r.width() <= getWidth()) dx = (getWidth() - r.width()) / 2f - r.left;
        else if (r.left > 0) dx = -r.left;
        else if (r.right < getWidth()) dx = getWidth() - r.right;
        if (r.height() <= getHeight()) dy = (getHeight() - r.height()) / 2f - r.top;
        else if (r.top > 0) dy = -r.top;
        else if (r.bottom < getHeight()) dy = getHeight() - r.bottom;
        m.postTranslate(dx, dy);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaler.onTouchEvent(e);
        gestures.onTouchEvent(e);
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        return true;
    }
}
