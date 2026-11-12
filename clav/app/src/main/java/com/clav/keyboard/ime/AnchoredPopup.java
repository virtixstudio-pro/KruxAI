package com.clav.keyboard.ime;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.PopupWindow;

/**
 * Base class for popups anchored above a keyboard key: dark card, entrance
 * animation (scale + fade from the anchor) and outside-touch dismissal.
 */
abstract class AnchoredPopup {

    private final PopupWindow popupWindow;
    protected final Context context;

    protected AnchoredPopup(Context context) {
        this.context = context;
        popupWindow = new PopupWindow(context);
        popupWindow.setFocusable(false);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setClippingEnabled(true);
        popupWindow.setElevation(dp(8));
    }

    protected void setContentView(View view) {
        int maxWidth = context.getResources().getDisplayMetrics().widthPixels - (int) dp(24);
        view.measure(
                View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        popupWindow.setContentView(view);
        popupWindow.setWidth(view.getMeasuredWidth());
        popupWindow.setHeight(view.getMeasuredHeight());
    }

    /**
     * Shows the popup above the anchor. {@code alignment} is the horizontal
     * fraction (0 = left edge, 0.5 = centre) of both anchor and popup that
     * should line up; the position is clamped inside the IME window.
     */
    protected void showAbove(View anchor, float alignment) {
        if (popupWindow.isShowing() || anchor == null) {
            return;
        }
        int[] anchorLocation = new int[2];
        anchor.getLocationInWindow(anchorLocation);
        int windowWidth = anchor.getRootView().getWidth();

        int x = Math.round(anchorLocation[0]
                + anchor.getWidth() * alignment
                - popupWindow.getWidth() * alignment);
        x = Math.max((int) dp(6), Math.min(x, windowWidth - popupWindow.getWidth() - (int) dp(6)));
        int y = anchorLocation[1] - popupWindow.getHeight() - (int) dp(10);

        popupWindow.showAtLocation(anchor, Gravity.TOP | Gravity.START, x, y);
        animateIn();
    }

    private void animateIn() {
        View content = popupWindow.getContentView();
        content.setAlpha(0f);
        content.setScaleX(0.88f);
        content.setScaleY(0.88f);
        content.setPivotY(popupWindow.getHeight());
        content.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200L)
                .setInterpolator(new OvershootInterpolator(1.15f))
                .start();
    }

    public boolean isShowing() {
        return popupWindow.isShowing();
    }

    public void dismiss() {
        popupWindow.dismiss();
    }

    protected float dp(float value) {
        return value * context.getResources().getDisplayMetrics().density;
    }
}
