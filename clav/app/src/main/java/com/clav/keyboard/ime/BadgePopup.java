package com.clav.keyboard.ime;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import com.clav.keyboard.R;

/**
 * The certification card shown when the verified badge on the suggestion
 * bar is tapped.
 */
public class BadgePopup extends AnchoredPopup {

    public BadgePopup(Context context) {
        super(context);
        View content = LayoutInflater.from(context).inflate(R.layout.popup_badge, null, false);
        setContentView(content);
    }

    public void show(View anchor) {
        showAbove(anchor, 0.5f);
    }
}
