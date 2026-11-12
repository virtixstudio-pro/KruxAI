package com.clav.keyboard;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import com.clav.keyboard.ime.ClavImeService;

import java.util.List;

/**
 * Onboarding screen: guides the user through enabling Clav and selecting it
 * as the active keyboard, with live status feedback on resume.
 */
public class LauncherActivity extends Activity {

    private TextView statusEnabled;
    private TextView statusDefault;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);

        statusEnabled = findViewById(R.id.status_enabled);
        statusDefault = findViewById(R.id.status_default);

        findViewById(R.id.action_enable).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        findViewById(R.id.action_pick).setOnClickListener(v ->
                inputMethodManager().showInputMethodPicker());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void refreshStatus() {
        boolean enabled = false;
        List<InputMethodInfo> enabledMethods = inputMethodManager().getEnabledInputMethodList();
        for (InputMethodInfo info : enabledMethods) {
            if (getPackageName().equals(info.getPackageName())) {
                enabled = true;
                break;
            }
        }

        String defaultId = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        boolean isDefault = defaultId != null && defaultId.equals(
                new ComponentName(this, ClavImeService.class).flattenToString());

        setStatus(statusEnabled, enabled, R.string.status_enabled, R.string.status_not_enabled);
        setStatus(statusDefault, isDefault, R.string.status_default, R.string.status_not_default);
    }

    private void setStatus(TextView view, boolean ok, int okRes, int koRes) {
        view.setText(ok ? "✓ " + getString(okRes) : "• " + getString(koRes));
        view.setTextColor(getColor(ok ? R.color.accent : R.color.text_secondary));
    }

    private InputMethodManager inputMethodManager() {
        return (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    }
}
