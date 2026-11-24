package com.virtixstudio.kruxai.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

/** Icônes vectorielles (remplacent les emoji) : teinte et taille appliquées par le code. */
final class UiIcons {
    private UiIcons() {}

    /** Icône teintée, carrée, de {@code sizeDp} dp. */
    static Drawable get(Context c, int res, int color, int sizeDp) {
        Drawable d = ContextCompat.getDrawable(c, res);
        if (d == null) return null;
        d = d.mutate();
        d.setTintList(ColorStateList.valueOf(color));
        int px = Math.round(sizeDp * c.getResources().getDisplayMetrics().density);
        d.setBounds(0, 0, px, px);
        return d;
    }

    /** Place l'icône à gauche du texte, avec un espace de {@code padDp} dp. */
    static void left(TextView t, int res, int color, int sizeDp, int padDp) {
        Context c = t.getContext();
        t.setCompoundDrawablesRelative(get(c, res, color, sizeDp), null, null, null);
        t.setCompoundDrawablePadding(Math.round(padDp * c.getResources().getDisplayMetrics().density));
    }
}
