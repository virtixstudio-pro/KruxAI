package com.clav.keyboard.ime;

import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import com.clav.keyboard.R;

/**
 * The IME entry point: a thin lifecycle shell — everything else lives in
 * {@link KeyboardController} and its collaborators.
 */
public class ClavImeService extends InputMethodService {

    private KeyboardController controller;

    @Override
    public void onCreate() {
        super.onCreate();
        setTheme(R.style.Theme_Clav);
    }

    @Override
    public View onCreateInputView() {
        View root = getLayoutInflater().inflate(R.layout.keyboard_view, null);
        controller = new KeyboardController(this, root);
        return root;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (controller != null) {
            controller.bindInput(getCurrentInputConnection(), info);
        }
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        if (controller != null) {
            controller.onFinishInput();
        }
        super.onFinishInputView(finishingInput);
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd,
                                   int newSelStart, int newSelEnd,
                                   int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);
        if (controller != null) {
            controller.onSelectionChanged(candidatesStart, candidatesEnd);
        }
    }

    @Override
    public void onWindowShown() {
        super.onWindowShown();
        if (controller != null) {
            controller.onWindowShown();
        }
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        // Never take over the whole screen in landscape.
        return false;
    }
}
