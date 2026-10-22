package com.virtixstudio.kruxai.ui;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Thème central de KRUX. Dans un layout, mets android:tag="krux:CLE" sur un élément :
 * bg, text, text2, hint, button, buttontext, input, accent, bubble_user, bubble_ai.
 * KruxTheme.apply(activity) (après setContentView) colore tous les éléments étiquetés.
 */
public final class KruxTheme {
    private KruxTheme() {}

    public static final String[] KEYS = {
        "bg", "text", "text2", "hint", "button", "buttontext", "input", "accent", "bubble_user", "bubble_ai"};
    public static final String[] LABELS = {
        "Fond des écrans", "Textes principaux", "Textes secondaires", "Textes d'aide (champs)",
        "Boutons", "Texte des boutons", "Champs de saisie", "Accent (liens, icônes)",
        "Bulle utilisateur", "Bulle KRUX"};
    public static final String[] PALETTES = {"noir", "blanc", "midnight", "graphite", "ocean"};
    public static final String[] PALETTE_LABELS = {"Noir", "Blanc", "Midnight", "Graphite", "Ocean"};

    //                                bg          text        text2       hint        button      buttontext  input       accent      bubble_user bubble_ai
    private static final int[] NOIR     = {0xFF000000, 0xFFFFFFFF, 0xFFB3B3B3, 0xFF808080, 0xFFFFFFFF, 0xFF000000, 0xFF1A1A1A, 0xFFFFFFFF, 0xFF222222, 0x00000000};
    private static final int[] BLANC    = {0xFFFFFFFF, 0xFF000000, 0xFF555555, 0xFF888888, 0xFF000000, 0xFFFFFFFF, 0xFFEEEEEE, 0xFF000000, 0xFFE2E2E2, 0x00000000};
    private static final int[] MIDNIGHT = {0xFF07050F, 0xFFFAF7FF, 0xFFBFA8D6, 0xFF8B7BA8, 0xFFA855F7, 0xFFFFFFFF, 0xFF14101D, 0xFFA855F7, 0xFF120B24, 0x00000000};
    private static final int[] GRAPHITE = {0xFF101216, 0xFFEBEFF5, 0xFF9AA4B2, 0xFF6B7380, 0xFFA855F7, 0xFFFFFFFF, 0xFF1C2027, 0xFFA855F7, 0xFF262B34, 0x00000000};
    private static final int[] OCEAN    = {0xFF05101C, 0xFFE8F8FF, 0xFF9CC3D6, 0xFF6C93A6, 0xFF22D3EE, 0xFF05101C, 0xFF0B2233, 0xFF22D3EE, 0xFF0A344C, 0x00000000};

    private static int[] defaults(String palette) {
        switch (palette == null ? "noir" : palette) {
            case "blanc": return BLANC;
            case "midnight": return MIDNIGHT;
            case "graphite": return GRAPHITE;
            case "ocean": return OCEAN;
            default: return NOIR;
        }
    }

    private static int indexOf(String key) {
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return i;
        return -1;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("krux_theme", Context.MODE_PRIVATE);
    }

    public static String palette(Context c) { return prefs(c).getString("palette", "noir"); }

    public static boolean hasOverride(Context c, String key) { return prefs(c).contains("c_" + key); }

    public static int get(Context c, String key) {
        int i = indexOf(key);
        if (i < 0) return Color.MAGENTA;
        return prefs(c).getInt("c_" + key, defaults(palette(c))[i]);
    }

    public static void set(Context c, String key, int color) {
        prefs(c).edit().putInt("c_" + key, color).apply();
    }

    public static void clearOverrides(Context c) {
        SharedPreferences.Editor e = prefs(c).edit();
        for (String k : KEYS) e.remove("c_" + k);
        e.apply();
    }

    public static void setPalette(Context c, String palette) {
        clearOverrides(c);
        prefs(c).edit().putString("palette", palette).apply();
    }

    /** À appeler juste après setContentView(...). */
    public static void apply(Activity a) {
        View content = a.findViewById(android.R.id.content);
        if (content == null) return;
        walk(content, a);
        if (content instanceof ViewGroup && ((ViewGroup) content).getChildCount() > 0) {
            View root = ((ViewGroup) content).getChildAt(0);
            if (root != null && "krux:bg".equals(root.getTag())) {
                int bg = get(a, "bg");
                Window w = a.getWindow();
                w.setStatusBarColor(bg);
                w.setNavigationBarColor(bg);
                boolean light = ColorUtils.calculateLuminance(bg) > 0.5;
                WindowInsetsControllerCompat ic = WindowCompat.getInsetsController(w, w.getDecorView());
                ic.setAppearanceLightStatusBars(light);
                ic.setAppearanceLightNavigationBars(light);
            }
        }
    }

    private static void walk(View v, Context c) {
        Object t = v.getTag();
        if (t instanceof String) {
            String s = (String) t;
            if (s.startsWith("krux:")) style(v, s.substring(5), c);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) walk(g.getChildAt(i), c);
        }
    }

    private static void style(View v, String key, Context c) {
        if (indexOf(key) < 0) return;
        int color = get(c, key);
        switch (key) {
            case "text":
            case "text2":
            case "buttontext":
                if (v instanceof TextView) ((TextView) v).setTextColor(color);
                else if (v instanceof ImageView) ((ImageView) v).setImageTintList(ColorStateList.valueOf(color));
                break;
            case "hint":
                if (v instanceof TextView) ((TextView) v).setHintTextColor(color);
                break;
            case "accent":
                if (v instanceof ProgressBar) {
                    ProgressBar p = (ProgressBar) v;
                    ColorStateList l = ColorStateList.valueOf(color);
                    p.setIndeterminateTintList(l);
                    p.setProgressTintList(l);
                } else if (v instanceof ImageView) {
                    ((ImageView) v).setImageTintList(ColorStateList.valueOf(color));
                } else if (v instanceof TextView) {
                    ((TextView) v).setTextColor(color);
                } else {
                    paintBackground(v, color);
                }
                break;
            case "bg":
                v.setBackgroundColor(color);
                break;
            default: // button, input, bubble_user, bubble_ai
                paintBackground(v, color);
        }
    }

    private static void paintBackground(View v, int color) {
        Drawable bg = v.getBackground();
        if (bg instanceof GradientDrawable) {
            ((GradientDrawable) bg.mutate()).setColor(color);
        } else if (bg != null) {
            ViewCompat.setBackgroundTintList(v, ColorStateList.valueOf(color));
        } else {
            v.setBackgroundColor(color);
        }
    }

    private static String hex(int color) {
        return Color.alpha(color) == 255
            ? String.format(java.util.Locale.US, "#%06X", color & 0xFFFFFF)
            : String.format(java.util.Locale.US, "#%08X", color);
    }

    /** Éditeur de couleurs : palette de départ + une couleur par ligne (#RRGGBB). */
    public static void showEditor(final Activity a, final Runnable onChanged) {
        final float d = a.getResources().getDisplayMetrics().density;
        final int pad = (int) (16 * d);
        ScrollView scroll = new ScrollView(a);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, pad);
        scroll.addView(box);

        TextView head = new TextView(a);
        head.setText("Palette de départ");
        head.setTextSize(13);
        box.addView(head);

        final androidx.appcompat.app.AlertDialog[] ref = new androidx.appcompat.app.AlertDialog[1];

        HorizontalScrollView hs = new HorizontalScrollView(a);
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < PALETTES.length; i++) {
            final String p = PALETTES[i];
            Button b = new Button(a);
            b.setText(PALETTE_LABELS[i]);
            b.setTextSize(12);
            b.setAllCaps(false);
            b.setOnClickListener(v -> {
                setPalette(a, p);
                apply(a);
                if (onChanged != null) onChanged.run();
                if (ref[0] != null) ref[0].dismiss();
            });
            row.addView(b);
        }
        hs.addView(row);
        box.addView(hs);

        final EditText[] fields = new EditText[KEYS.length];
        for (int i = 0; i < KEYS.length; i++) {
            TextView label = new TextView(a);
            label.setText(LABELS[i]);
            label.setTextSize(12);
            label.setPadding(0, (int) (10 * d), 0, 0);
            box.addView(label);
            EditText et = new EditText(a);
            et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            et.setSingleLine(true);
            et.setText(hex(get(a, KEYS[i])));
            fields[i] = et;
            box.addView(et);
        }

        ref[0] = new androidx.appcompat.app.AlertDialog.Builder(a)
            .setTitle("Couleurs")
            .setView(scroll)
            .setPositiveButton("Appliquer", (dlg, which) -> {
                int bad = 0;
                int[] def = defaults(palette(a));
                for (int i = 0; i < KEYS.length; i++) {
                    String s = fields[i].getText().toString().trim();
                    if (s.isEmpty()) continue;
                    try {
                        int col = Color.parseColor(s);
                        if (col == def[i]) prefs(a).edit().remove("c_" + KEYS[i]).apply();
                        else set(a, KEYS[i], col);
                    } catch (IllegalArgumentException e) {
                        bad++;
                    }
                }
                apply(a);
                if (onChanged != null) onChanged.run();
                if (bad > 0) Toast.makeText(a, bad + " couleur(s) invalide(s) ignorée(s). Format : #RRGGBB", Toast.LENGTH_LONG).show();
            })
            .setNeutralButton("Réinitialiser", (dlg, which) -> {
                clearOverrides(a);
                apply(a);
                if (onChanged != null) onChanged.run();
            })
            .setNegativeButton("Fermer", null)
            .create();
        ref[0].show();
    }
}
