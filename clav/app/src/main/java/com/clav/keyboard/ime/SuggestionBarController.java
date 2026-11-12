package com.clav.keyboard.ime;

import android.animation.LayoutTransition;
import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.clav.keyboard.R;

import java.util.List;

/**
 * The bar above the keys: brand (left), suggestion chips with staggered
 * entrance (centre) and the certification badge (right).
 */
public class SuggestionBarController {

    public interface Listener {
        void onSuggestionPicked(String word);
        void onBadgeClicked();
    }

    private static final int MAX_CHIPS = 3;

    private final Context context;
    private final Listener listener;
    private final TextView brandView;
    private final LinearLayout chipsContainer;
    private final VerifiedBadgeView badgeView;

    public SuggestionBarController(Context context, ViewGroup container, Listener listener) {
        this.context = context;
        this.listener = listener;

        View bar = LayoutInflater.from(context).inflate(R.layout.view_suggestion_bar, container, false);
        container.addView(bar);

        brandView = bar.findViewById(R.id.brand);
        chipsContainer = bar.findViewById(R.id.suggestion_chips);
        badgeView = bar.findViewById(R.id.badge);

        // Fluid add/remove when chips change.
        chipsContainer.setLayoutTransition(new LayoutTransition());

        badgeView.setOnClickListener(v -> listener.onBadgeClicked());
        badgeView.startPulse();
    }

    /** View the certification popup anchors to. */
    public View badgeAnchor() {
        return badgeView;
    }

    /** Switches the bar between normal and password-field states. */
    public void setSecureField(boolean secure) {
        chipsContainer.removeAllViews();
        brandView.setVisibility(View.VISIBLE);
        brandView.setText(secure ? R.string.suggestion_secure_field : R.string.brand);
    }

    public void showSuggestions(List<String> suggestions) {
        chipsContainer.removeAllViews();
        if (suggestions == null || suggestions.isEmpty()) {
            brandView.setVisibility(View.VISIBLE);
            brandView.setText(R.string.brand);
            return;
        }
        brandView.setVisibility(View.GONE);
        int count = Math.min(suggestions.size(), MAX_CHIPS);
        for (int i = 0; i < count; i++) {
            chipsContainer.addView(buildChip(suggestions.get(i), i), i);
        }
    }

    private TextView buildChip(String word, int index) {
        TextView chip = new TextView(context);
        chip.setText(word);
        chip.setSingleLine(true);
        chip.setGravity(Gravity.CENTER);
        chip.setTextColor(context.getColor(R.color.text_primary));
        chip.setTextSize(15);
        chip.setBackgroundResource(R.drawable.bg_suggestion_chip);
        chip.setOnClickListener(v -> listener.onSuggestionPicked(word));

        // Staggered entrance.
        chip.setAlpha(0f);
        chip.animate()
                .alpha(1f)
                .setDuration(140L)
                .setStartDelay(index * 35L)
                .start();
        return chip;
    }
}
