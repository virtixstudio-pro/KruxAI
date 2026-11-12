package com.clav.keyboard.ime;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * A single keyboard key with fluid touch feedback: ripple (provided by the
 * background drawable), a press scale animation and a light haptic tick.
 */
public class KeyView extends FrameLayout {

    /** Notified on press-state changes (used to stop key-repeat on release). */
    public interface PressListener {
        void onPressStateChanged(KeyView view, boolean pressed);
    }

    private static final float PRESSED_SCALE = 0.94f;
    private static final long PRESS_DURATION = 60L;
    private static final long RELEASE_DURATION = 110L;

    private final TextView labelView;
    private final ImageView iconView;

    private PressListener pressListener;
    private boolean pressed;

    public KeyView(Context context) {
        this(context, null);
    }

    public KeyView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setClickable(true);
        setFocusable(false);

        labelView = new TextView(context);
        labelView.setGravity(Gravity.CENTER);
        labelView.setSingleLine(true);
        addView(labelView, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        addView(iconView, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    }

    // ------------------------------------------------------------------ look

    public void setLabel(String label) {
        labelView.setText(label);
        labelView.setVisibility(VISIBLE);
        iconView.setVisibility(GONE);
    }

    public void setIcon(int iconRes) {
        iconView.setImageResource(iconRes);
        iconView.setVisibility(VISIBLE);
        labelView.setVisibility(GONE);
    }

    public void setIconTint(int color) {
        iconView.setColorFilter(color);
    }

    public void setLabelColor(int color) {
        labelView.setTextColor(color);
    }

    public void setLabelTextSize(float sizeSp) {
        labelView.setTextSize(sizeSp);
    }

    public void setKeyBackground(int backgroundRes) {
        setBackgroundResource(backgroundRes);
    }

    public void setPressListener(PressListener listener) {
        pressListener = listener;
    }

    // ---------------------------------------------------------------- touch

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = true;
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                animateTo(PRESSED_SCALE, PRESS_DURATION);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (pressed) {
                    pressed = false;
                    animateTo(1f, RELEASE_DURATION);
                }
                break;
            default:
                break;
        }
        return super.onTouchEvent(event);
    }

    private void animateTo(float scale, long duration) {
        animate().scaleX(scale).scaleY(scale).setDuration(duration).start();
        if (pressListener != null) {
            pressListener.onPressStateChanged(this, scale < 1f);
        }
    }
}
