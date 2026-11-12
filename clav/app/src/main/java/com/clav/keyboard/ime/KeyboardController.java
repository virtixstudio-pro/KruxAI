package com.clav.keyboard.ime;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.LinearLayout;

import com.clav.keyboard.R;

import java.util.List;
import java.util.Locale;

/**
 * Builds and drives the whole keyboard UI: suggestion bar, key rows,
 * shift / mode / language state, popups and touch animations.
 */
public class KeyboardController {

    public enum ShiftState {OFF, ON, LOCKED}

    private static final String PREFS = "clav_prefs";
    private static final String KEY_LANGUAGE = "language";
    private static final long DOUBLE_TAP_DELAY = 350L;
    private static final long DELETE_REPEAT_INTERVAL = 55L;

    private final Context context;
    private final SharedPreferences prefs;
    private final InputMediator mediator;
    private final WordSuggester suggester = new WordSuggester();
    private final SuggestionBarController suggestionBar;
    private final LinearLayout rowsContainer;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable deleteRepeater = new Runnable() {
        @Override
        public void run() {
            if (!deleting) {
                return;
            }
            mediator.deleteBackwards();
            handler.postDelayed(this, DELETE_REPEAT_INTERVAL);
        }
    };
    private boolean deleting = false;

    private ShiftState shift = ShiftState.OFF;
    private boolean symbolsMode = false;
    private String language;
    private long lastShiftTap = 0L;

    private BadgePopup badgePopup;
    private OptionPopup emojiPopup;
    private OptionPopup accentPopup;

    public KeyboardController(Context context, View root) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.language = prefs.getString(KEY_LANGUAGE, KeyboardLayouts.AZERTY);
        this.mediator = new InputMediator(word -> updateSuggestions(word));
        this.rowsContainer = root.findViewById(R.id.keyboard_rows);

        ViewGroup suggestionSlot = root.findViewById(R.id.suggestion_slot);
        this.suggestionBar = new SuggestionBarController(context, suggestionSlot,
                new SuggestionBarController.Listener() {
                    @Override
                    public void onSuggestionPicked(String word) {
                        mediator.applySuggestion(word);
                        setShift(ShiftState.OFF);
                    }

                    @Override
                    public void onBadgeClicked() {
                        showBadgePopup();
                    }
                });

        rebuildRows();
    }

    public InputMediator getMediator() {
        return mediator;
    }

    // ------------------------------------------------------------- lifecycle

    public void bindInput(InputConnection connection, EditorInfo info) {
        mediator.bind(connection, info);
        if (info != null && (info.inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER) {
            setSymbolsMode(true);
        }
        suggestionBar.setSecureField(mediator.isSecureField());
        updateSuggestions(mediator.composingWord());
    }

    public void onFinishInput() {
        mediator.unbind();
        dismissPopups();
        stopDeleteRepeat();
        setShift(ShiftState.OFF);
    }

    /** Keeps the tracked word in sync when the system clears the composing region. */
    public void onSelectionChanged(int candidatesStart, int candidatesEnd) {
        mediator.onSelectionChanged(candidatesStart, candidatesEnd);
    }

    /** Soft entrance whenever the IME window appears. */
    public void onWindowShown() {
        rowsContainer.setAlpha(0f);
        rowsContainer.animate().alpha(1f).setDuration(160L).start();
    }

    // ------------------------------------------------------------------ rows

    private void rebuildRows() {
        rowsContainer.removeAllViews();
        List<List<KeySpec>> layout = symbolsMode
                ? KeyboardLayouts.symbols()
                : KeyboardLayouts.letters(language);
        for (List<KeySpec> row : layout) {
            rowsContainer.addView(buildRow(row), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        applyShiftVisuals();
        rowsContainer.animate().cancel();
        rowsContainer.setAlpha(0f);
        rowsContainer.animate().alpha(1f).setDuration(130L).start();
    }

    private LinearLayout buildRow(List<KeySpec> specs) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int rowHeight = context.getResources().getDimensionPixelSize(R.dimen.key_row_height);
        int gap = context.getResources().getDimensionPixelSize(R.dimen.key_gap);

        for (KeySpec spec : specs) {
            KeyView key = new KeyView(context);
            styleKey(key, spec);
            key.setTag(spec);
            key.setOnClickListener(v -> onKey(spec));
            if (spec.type == KeySpec.Type.LETTER && spec.hasAccents()) {
                key.setOnLongClickListener(v -> {
                    showAccentPopup(key, spec);
                    return true;
                });
            } else if (spec.type == KeySpec.Type.DELETE) {
                key.setOnLongClickListener(v -> startDeleteRepeat());
                key.setPressListener((view, pressed) -> {
                    if (!pressed) {
                        stopDeleteRepeat();
                    }
                });
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, rowHeight, spec.weight);
            params.setMargins(gap, gap, gap, gap);
            row.addView(key, params);
        }
        return row;
    }

    private void styleKey(KeyView key, KeySpec spec) {
        switch (spec.type) {
            case LETTER:
            case CHAR:
                key.setKeyBackground(R.drawable.bg_key);
                key.setLabel(spec.label);
                key.setLabelColor(context.getColor(R.color.text_primary));
                key.setLabelTextSize(21);
                break;
            case SPACE:
                key.setKeyBackground(R.drawable.bg_key);
                key.setLabel(spaceLabel());
                key.setLabelColor(context.getColor(R.color.text_secondary));
                key.setLabelTextSize(13);
                break;
            case ENTER:
                key.setKeyBackground(R.drawable.bg_key_accent);
                key.setIcon(R.drawable.ic_enter);
                key.setIconTint(0xFFFFFFFF);
                break;
            case MODE:
                key.setKeyBackground(R.drawable.bg_key_function);
                key.setLabel(symbolsMode ? "ABC" : "#+=");
                key.setLabelColor(context.getColor(R.color.text_secondary));
                key.setLabelTextSize(15);
                break;
            default:
                key.setKeyBackground(R.drawable.bg_key_function);
                if (spec.iconRes != 0) {
                    key.setIcon(spec.iconRes);
                    key.setIconTint(context.getColor(R.color.text_secondary));
                } else {
                    key.setLabel(spec.label);
                    key.setLabelColor(context.getColor(R.color.text_secondary));
                    key.setLabelTextSize(15);
                }
                break;
        }
    }

    private String spaceLabel() {
        int res = KeyboardLayouts.AZERTY.equals(language)
                ? R.string.space_label_francais
                : R.string.space_label_qwerty;
        return context.getString(res);
    }

    // ------------------------------------------------------------- key events

    private void onKey(KeySpec spec) {
        switch (spec.type) {
            case LETTER:
                mediator.commitLetter(spec.output, shift != ShiftState.OFF);
                if (shift == ShiftState.ON) {
                    setShift(ShiftState.OFF);
                }
                break;
            case CHAR:
                mediator.commitChar(applyShift(spec.output));
                if (shift == ShiftState.ON) {
                    setShift(ShiftState.OFF);
                }
                break;
            case SPACE:
                mediator.commitSpace();
                if (shift == ShiftState.ON) {
                    setShift(ShiftState.OFF);
                }
                break;
            case ENTER:
                mediator.sendEnter();
                setShift(ShiftState.OFF);
                break;
            case DELETE:
                mediator.deleteBackwards();
                break;
            case SHIFT:
                handleShiftTap();
                break;
            case LANGUAGE:
                toggleLanguage();
                break;
            case MODE:
                setSymbolsMode(!symbolsMode);
                break;
            case EMOJI:
                showEmojiPopup();
                break;
            default:
                break;
        }
    }

    private String applyShift(String c) {
        return shift == ShiftState.OFF ? c : c.toUpperCase(Locale.FRENCH);
    }

    // ----------------------------------------------------------------- shift

    private void handleShiftTap() {
        long now = SystemClock.uptimeMillis();
        if (now - lastShiftTap < DOUBLE_TAP_DELAY) {
            // Double tap: lock / unlock capitals.
            setShift(shift == ShiftState.LOCKED ? ShiftState.OFF : ShiftState.LOCKED);
        } else {
            setShift(shift == ShiftState.OFF ? ShiftState.ON : ShiftState.OFF);
        }
        lastShiftTap = now;
    }

    private void setShift(ShiftState state) {
        if (shift == state) {
            return;
        }
        shift = state;
        applyShiftVisuals();
    }

    private void applyShiftVisuals() {
        int shiftBackground = shift == ShiftState.OFF ? R.drawable.bg_key_function : R.drawable.bg_key_accent;
        int shiftTint = shift == ShiftState.OFF
                ? context.getColor(R.color.text_secondary)
                : context.getColor(R.color.text_primary);
        for (int r = 0; r < rowsContainer.getChildCount(); r++) {
            View row = rowsContainer.getChildAt(r);
            if (!(row instanceof ViewGroup)) {
                continue;
            }
            ViewGroup rowGroup = (ViewGroup) row;
            for (int i = 0; i < rowGroup.getChildCount(); i++) {
                View child = rowGroup.getChildAt(i);
                if (!(child instanceof KeyView)) {
                    continue;
                }
                Object tag = child.getTag();
                if (!(tag instanceof KeySpec)) {
                    continue;
                }
                KeySpec spec = (KeySpec) tag;
                KeyView key = (KeyView) child;
                if (spec.type == KeySpec.Type.LETTER) {
                    key.setLabel(shift == ShiftState.OFF
                            ? spec.label.toLowerCase(Locale.FRENCH)
                            : spec.label.toUpperCase(Locale.FRENCH));
                } else if (spec.type == KeySpec.Type.SHIFT) {
                    key.setKeyBackground(shiftBackground);
                    key.setIconTint(shiftTint);
                }
            }
        }
    }

    // ------------------------------------------------------- mode & language

    private void setSymbolsMode(boolean symbols) {
        if (symbolsMode == symbols) {
            return;
        }
        symbolsMode = symbols;
        rebuildRows();
    }

    private void toggleLanguage() {
        language = KeyboardLayouts.AZERTY.equals(language) ? KeyboardLayouts.QWERTY : KeyboardLayouts.AZERTY;
        prefs.edit().putString(KEY_LANGUAGE, language).apply();
        rebuildRows();
    }

    // ---------------------------------------------------------------- popups

    private void showBadgePopup() {
        dismissPopups();
        badgePopup = new BadgePopup(context);
        badgePopup.show(suggestionBar.badgeAnchor());
    }

    private void showEmojiPopup() {
        dismissPopups();
        KeyView anchor = findKey(KeySpec.Type.EMOJI);
        if (anchor == null) {
            return;
        }
        emojiPopup = new OptionPopup(context,
                context.getResources().getStringArray(R.array.emoji_options),
                OptionPopup.STYLE_EMOJI,
                option -> mediator.commitText(option));
        emojiPopup.show(anchor, 0.15f);
    }

    private void showAccentPopup(KeyView anchor, KeySpec spec) {
        dismissPopups();
        String[] accents = new String[spec.accents.length];
        for (int i = 0; i < spec.accents.length; i++) {
            accents[i] = applyShift(spec.accents[i]);
        }
        accentPopup = new OptionPopup(context, accents, OptionPopup.STYLE_LETTER,
                option -> mediator.commitLetter(option, false));
        accentPopup.show(anchor, 0.5f);
    }

    private void dismissPopups() {
        if (badgePopup != null) {
            badgePopup.dismiss();
            badgePopup = null;
        }
        if (emojiPopup != null) {
            emojiPopup.dismiss();
            emojiPopup = null;
        }
        if (accentPopup != null) {
            accentPopup.dismiss();
            accentPopup = null;
        }
    }

    private KeyView findKey(KeySpec.Type type) {
        for (int r = 0; r < rowsContainer.getChildCount(); r++) {
            View row = rowsContainer.getChildAt(r);
            if (!(row instanceof ViewGroup)) {
                continue;
            }
            ViewGroup rowGroup = (ViewGroup) row;
            for (int i = 0; i < rowGroup.getChildCount(); i++) {
                View child = rowGroup.getChildAt(i);
                Object tag = child.getTag();
                if (tag instanceof KeySpec && ((KeySpec) tag).type == type && child instanceof KeyView) {
                    return (KeyView) child;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------- delete key repeat

    private boolean startDeleteRepeat() {
        if (deleting) {
            return true;
        }
        deleting = true;
        deleteRepeater.run();
        return true;
    }

    private void stopDeleteRepeat() {
        if (deleting) {
            deleting = false;
            handler.removeCallbacks(deleteRepeater);
        }
    }

    // ------------------------------------------------------------ suggestions

    private void updateSuggestions(String word) {
        if (mediator.isSecureField()) {
            return; // the bar already shows the secure-field state
        }
        suggestionBar.showSuggestions(suggester.suggest(word, 3));
    }
}
