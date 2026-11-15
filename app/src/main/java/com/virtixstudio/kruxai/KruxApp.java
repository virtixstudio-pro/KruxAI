package com.virtixstudio.kruxai;

import android.app.Application;

import com.virtixstudio.kruxai.ui.KruxTheme;

/** Règle le mode clair/sombre d'après la palette dès le démarrage, avant le premier écran. */
public class KruxApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        KruxTheme.syncNightMode(this);
    }
}
