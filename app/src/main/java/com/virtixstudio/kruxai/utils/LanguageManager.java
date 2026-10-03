package com.virtixstudio.kruxai.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.virtixstudio.kruxai.R;

public final class LanguageManager {

    private static final String PREFS = "krux_settings";
    private static final String KEY_LANGUAGE = "language";

    private LanguageManager() {
    }

    public static void applySavedLanguage(Context context) {
        String language = getLanguage(context);

        String current = AppCompatDelegate.getApplicationLocales()
                .toLanguageTags();

        if (!current.equals(language)) {
            AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(language)
            );
        }
    }

    public static String getLanguage(Context context) {
        return context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
        ).getString(KEY_LANGUAGE, "fr");
    }

    public static void showLanguageDialog(Activity activity) {

        String[] languages = {
                activity.getString(R.string.language_french),
                activity.getString(R.string.language_english),
                activity.getString(R.string.language_spanish),
                activity.getString(R.string.language_chinese)
        };

        String[] codes = {
                "fr",
                "en",
                "es",
                "zh"
        };

        String currentLanguage = getLanguage(activity);

        int checked = 0;

        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equals(currentLanguage)) {
                checked = i;
                break;
            }
        }

        new AlertDialog.Builder(activity)
                .setTitle(R.string.choose_language)
                .setSingleChoiceItems(
                        languages,
                        checked,
                        (dialog, which) -> {
                            String selected = codes[which];

                            activity.getSharedPreferences(
                                    PREFS,
                                    Context.MODE_PRIVATE
                            ).edit()
                                    .putString(KEY_LANGUAGE, selected)
                                    .apply();

                            AppCompatDelegate.setApplicationLocales(
                                    LocaleListCompat.forLanguageTags(selected)
                            );

                            dialog.dismiss();
                        }
                )
                .setNegativeButton(
                        android.R.string.cancel,
                        null
                )
                .show();
    }
}
