package com.virtixstudio.kruxai.core;

/** Choix faits dans le panneau de questions avant la création d'une app (icône importée…). */
public final class AppOptions {
    private static volatile String iconB64;

    private AppOptions() { }

    public static void setIcon(String pngBase64) { iconB64 = pngBase64; }

    /** Rend l'icône en attente (PNG en base64) puis l'oublie : elle ne sert qu'à une seule création. */
    public static String takeIcon() {
        String v = iconB64;
        iconB64 = null;
        return v;
    }
}
