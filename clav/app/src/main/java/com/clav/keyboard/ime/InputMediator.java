package com.clav.keyboard.ime;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.util.Locale;

/**
 * Bridges the keyboard UI and the current editor: owns the composing word
 * (driving the suggestion bar) and every text operation the IME performs.
 */
public class InputMediator {

    /** Notified whenever the composing word changes. */
    public interface Listener {
        void onComposingWordChanged(String word);
    }

    private final Listener listener;
    private final StringBuilder composingWord = new StringBuilder();

    private InputConnection inputConnection;
    private EditorInfo editorInfo;
    private boolean secureField;

    public InputMediator(Listener listener) {
        this.listener = listener;
    }

    // ------------------------------------------------------------- lifecycle

    public void bind(InputConnection connection, EditorInfo info) {
        inputConnection = connection;
        editorInfo = info;
        secureField = isSecureField(info);
        resetComposing();
    }

    public void unbind() {
        finishComposing();
        inputConnection = null;
        editorInfo = null;
    }

    public boolean isSecureField() {
        return secureField;
    }

    public String composingWord() {
        return composingWord.toString();
    }

    // ------------------------------------------------------------ text input

    public void commitLetter(String letter, boolean uppercase) {
        if (inputConnection == null) {
            return;
        }
        String out = uppercase ? letter.toUpperCase(Locale.FRENCH) : letter;
        if (secureField) {
            // Never compose (underline) in password fields.
            inputConnection.commitText(out, 1);
        } else {
            composingWord.append(out);
            inputConnection.setComposingText(composingWord, 1);
        }
        notifyWordChanged();
    }

    public void commitChar(String c) {
        if (inputConnection == null) {
            return;
        }
        finishComposing();
        inputConnection.commitText(c, 1);
        notifyWordChanged();
    }

    public void commitSpace() {
        if (inputConnection == null) {
            return;
        }
        finishComposing();
        inputConnection.commitText(" ", 1);
        notifyWordChanged();
    }

    /** Commits raw text (emoji…) without composing handling. */
    public void commitText(String text) {
        if (inputConnection == null) {
            return;
        }
        finishComposing();
        inputConnection.commitText(text, 1);
        notifyWordChanged();
    }

    public void deleteBackwards() {
        if (inputConnection == null) {
            return;
        }
        if (!secureField && composingWord.length() > 0) {
            composingWord.deleteCharAt(composingWord.length() - 1);
            // Empty composing text removes the composing region entirely.
            inputConnection.setComposingText(composingWord, 1);
        } else {
            inputConnection.deleteSurroundingText(1, 0);
        }
        notifyWordChanged();
    }

    public void sendEnter() {
        if (inputConnection == null) {
            return;
        }
        finishComposing();
        boolean handled = editorInfo != null && inputConnection.sendDefaultEditorAction(true);
        if (!handled) {
            inputConnection.commitText("\n", 1);
        }
        notifyWordChanged();
    }

    /** Replaces the composing word with the picked suggestion, then adds a space. */
    public void applySuggestion(String suggestion) {
        if (inputConnection == null) {
            return;
        }
        if (composingWord.length() > 0) {
            inputConnection.setComposingText(suggestion, 1);
        }
        finishComposing();
        inputConnection.commitText(" ", 1);
        notifyWordChanged();
    }

    /**
     * The system can drop our composing region (user moved the cursor…).
     * If the region is gone while we still track a word, discard it so the
     * next key press never duplicates characters.
     */
    public void onSelectionChanged(int candidatesStart, int candidatesEnd) {
        if (composingWord.length() > 0 && (candidatesStart < 0 || candidatesEnd < 0)) {
            composingWord.setLength(0);
            notifyWordChanged();
        }
    }

    // --------------------------------------------------------------- helpers

    private void resetComposing() {
        composingWord.setLength(0);
        notifyWordChanged();
    }

    private void finishComposing() {
        if (inputConnection != null) {
            inputConnection.finishComposingText();
        }
        composingWord.setLength(0);
    }

    private void notifyWordChanged() {
        listener.onComposingWordChanged(composingWord());
    }

    private static boolean isSecureField(EditorInfo info) {
        if (info == null) {
            return false;
        }
        int inputClass = info.inputType & InputType.TYPE_MASK_CLASS;
        if (inputClass != InputType.TYPE_CLASS_TEXT) {
            return false;
        }
        int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
    }
}
