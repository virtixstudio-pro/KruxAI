package com.clav.keyboard.ime;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.clav.keyboard.R;

/**
 * Generic grid of selectable options anchored above the pressed key — used
 * for accented letters (long-press) and for the emoji picker.
 */
public class OptionPopup extends AnchoredPopup {

    public interface Listener {
        void onOptionPicked(String option);
    }

    public static final int STYLE_LETTER = 0;
    public static final int STYLE_EMOJI = 1;

    private static final int COLUMNS = 8;
    private static final float OPTION_SIZE_DP = 42f;

    public OptionPopup(Context context, String[] options, int style, Listener listener) {
        super(context);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_card);
        int padding = (int) dp(8);
        root.setPadding(padding, padding, padding, padding);

        LinearLayout currentRow = null;
        for (int i = 0; i < options.length; i++) {
            if (i % COLUMNS == 0) {
                currentRow = new LinearLayout(context);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                root.addView(currentRow, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
            }
            currentRow.addView(buildOption(options[i], style, listener), optionParams());
        }
        setContentView(root);
    }

    public void show(View anchor, float alignment) {
        showAbove(anchor, alignment);
    }

    private LinearLayout.LayoutParams optionParams() {
        int size = (int) dp(OPTION_SIZE_DP);
        int margin = (int) dp(2);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size, 1f);
        params.setMargins(margin, margin, margin, margin);
        return params;
    }

    private TextView buildOption(String option, int style, final Listener listener) {
        TextView optionView = new TextView(context);
        optionView.setText(option);
        optionView.setGravity(Gravity.CENTER);
        optionView.setSingleLine(true);
        if (style == STYLE_EMOJI) {
            optionView.setTextSize(22);
        } else {
            optionView.setTextSize(18);
            optionView.setTextColor(context.getColor(R.color.text_primary));
        }
        optionView.setBackgroundResource(R.drawable.bg_key);
        optionView.setOnClickListener(v -> {
            listener.onOptionPicked(option);
            dismiss();
        });
        return optionView;
    }
}
