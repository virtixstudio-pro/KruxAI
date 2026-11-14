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
import android.widget.FrameLayout;
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
    public static final String[] PALETTES = {"noir", "blanc", "midnight", "graphite", "ocean", "sable", "foret", "rose"};
    public static final String[] PALETTE_LABELS = {"Noir", "Blanc", "Midnight", "Graphite", "Océan", "Sable", "Forêt", "Rose"};

    //                                bg          text        text2       hint        button      buttontext  input       accent      bubble_user bubble_ai
    private static final int[] NOIR     = {0xFF000000, 0xFFFFFFFF, 0xFFB3B3B3, 0xFF808080, 0xFFFFFFFF, 0xFF000000, 0xFF1A1A1A, 0xFFFFFFFF, 0xFF222222, 0x00000000};
    private static final int[] BLANC    = {0xFFFFFFFF, 0xFF000000, 0xFF555555, 0xFF888888, 0xFF000000, 0xFFFFFFFF, 0xFFEEEEEE, 0xFF000000, 0xFFE2E2E2, 0x00000000};
    private static final int[] MIDNIGHT = {0xFF07050F, 0xFFFAF7FF, 0xFFBFA8D6, 0xFF8B7BA8, 0xFFA855F7, 0xFFFFFFFF, 0xFF14101D, 0xFFA855F7, 0xFF120B24, 0x00000000};
    private static final int[] GRAPHITE = {0xFF101216, 0xFFEBEFF5, 0xFF9AA4B2, 0xFF6B7380, 0xFFA855F7, 0xFFFFFFFF, 0xFF1C2027, 0xFFA855F7, 0xFF262B34, 0x00000000};
    private static final int[] OCEAN    = {0xFF05101C, 0xFFE8F8FF, 0xFF9CC3D6, 0xFF6C93A6, 0xFF22D3EE, 0xFF05101C, 0xFF0B2233, 0xFF22D3EE, 0xFF0A344C, 0x00000000};

    private static final int[] SABLE = {0xFFF7F3EC, 0xFF1C1A17, 0xFF6B645A, 0xFF9A9286, 0xFF1C1A17, 0xFFF7F3EC, 0xFFECE6DA, 0xFFB5651D, 0xFFE6DDCC, 0x00000000};
    private static final int[] FORET = {0xFF08120D, 0xFFE9F5EE, 0xFF9DBBA9, 0xFF6C8A78, 0xFF34D399, 0xFF04120B, 0xFF11231A, 0xFF34D399, 0xFF0F2E20, 0x00000000};
    private static final int[] ROSE  = {0xFF140A10, 0xFFFFF1F5, 0xFFD6A9B8, 0xFF9A7080, 0xFFF472B6, 0xFF1A0610, 0xFF22121A, 0xFFF472B6, 0xFF2E1220, 0x00000000};

    private static int[] defaults(String palette) {
        switch (palette == null ? "noir" : palette) {
            case "blanc": return BLANC;
            case "midnight": return MIDNIGHT;
            case "graphite": return GRAPHITE;
            case "ocean": return OCEAN;
            case "sable": return SABLE;
            case "foret": return FORET;
            case "rose": return ROSE;
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

    /** Arrondi des boutons en dp : 10 doux, 18 arrondi (défaut), 999 pilule. */
    public static int radiusDp(Context c) { return prefs(c).getInt("radius", 18); }
    public static void setRadiusDp(Context c, int dp) { prefs(c).edit().putInt("radius", dp).apply(); }

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
            if (root != null && hasKey(root, "bg")) {
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

    /** Colore une vue créée à la main (feuille du bas, boîte de dialogue, élément de liste). */
    public static void applyTo(View root, Context c) {
        if (root != null) walk(root, c);
    }

    /** Une étiquette peut porter plusieurs clés : "krux:input,text,hint". */
    private static boolean hasKey(View v, String key) {
        Object t = v.getTag();
        if (!(t instanceof String) || !((String) t).startsWith("krux:")) return false;
        for (String k : ((String) t).substring(5).split(",")) if (k.trim().equals(key)) return true;
        return false;
    }

    private static void walk(View v, Context c) {
        Object t = v.getTag();
        if (t instanceof String) {
            String s = (String) t;
            if (s.startsWith("krux:")) {
                for (String k : s.substring(5).split(",")) style(v, k.trim(), c);
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) walk(g.getChildAt(i), c);
        }
    }

    /**
     * Habille une boîte de dialogue ou une feuille du bas déjà affichée (show() appelé) :
     * fond, titres, messages, boutons et champs suivent le thème choisi.
     */
    public static void skin(android.app.Dialog d, Context c) {
        if (d == null || c == null) return;
        try {
            Window w = d.getWindow();
            if (w == null) return;
            int bg = get(c, "bg");
            float r = 20 * c.getResources().getDisplayMetrics().density;
            GradientDrawable g = new GradientDrawable();
            g.setColor(bg);
            g.setCornerRadii(new float[]{r, r, r, r, r, r, r, r});
            View sheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                float t = r;
                GradientDrawable top = new GradientDrawable();
                top.setColor(bg);
                top.setCornerRadii(new float[]{t, t, t, t, 0, 0, 0, 0});
                sheet.setBackground(top);
            } else {
                w.setBackgroundDrawable(g);
            }
            skinWalk(w.getDecorView(), c);
        } catch (Exception ignored) { }
    }

    private static void skinWalk(View v, Context c) {
        Object t = v.getTag();
        boolean tagged = t instanceof String && ((String) t).startsWith("krux:");
        if (tagged) {
            for (String k : ((String) t).substring(5).split(",")) style(v, k.trim(), c);
        } else if (v instanceof EditText) {
            ((EditText) v).setTextColor(get(c, "text"));
            ((EditText) v).setHintTextColor(get(c, "hint"));
        } else if (v instanceof Button) {
            ((Button) v).setTextColor(get(c, "accent"));
        } else if (v instanceof TextView) {
            ((TextView) v).setTextColor(get(c, "text"));
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) skinWalk(g.getChildAt(i), c);
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
            case "button":
                paintBackground(v, color);
                if (v.getBackground() instanceof GradientDrawable) {
                    ((GradientDrawable) v.getBackground().mutate())
                        .setCornerRadius(radiusDp(c) * c.getResources().getDisplayMetrics().density);
                }
                break;
            default: // input, bubble_user, bubble_ai
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

    private static final int[] ACCENTS = {0xFFA855F7, 0xFF22D3EE, 0xFF34D399, 0xFFF472B6, 0xFFFB923C,
        0xFFFACC15, 0xFF60A5FA, 0xFFF87171, 0xFFFFFFFF, 0xFF111111};

    private static GradientDrawable round(int fill, float radiusPx, int stroke, int strokePx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radiusPx);
        if (strokePx > 0) g.setStroke(strokePx, stroke);
        return g;
    }

    private static TextView label(Activity a, String text, float d) {
        TextView t = new TextView(a);
        t.setText(text);
        t.setTextSize(13);
        t.setTag("krux:text2");
        t.setPadding(0, (int) (18 * d), 0, (int) (8 * d));
        return t;
    }

    /** Personnalisation : aperçu en direct, palettes, couleur d'accent, arrondi des boutons. */
    public static void showEditor(final Activity a, final Runnable onChanged) {
        final float d = a.getResources().getDisplayMetrics().density;
        final int pad = (int) (18 * d);
        final ScrollView scroll = new ScrollView(a);
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, pad);
        scroll.addView(box);

        final androidx.appcompat.app.AlertDialog[] ref = new androidx.appcompat.app.AlertDialog[1];
        final Runnable[] refresh = new Runnable[1];

        // --- aperçu
        final LinearLayout preview = new LinearLayout(a);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setPadding((int) (14 * d), (int) (14 * d), (int) (14 * d), (int) (14 * d));
        final TextView pUser = new TextView(a);
        pUser.setText("Fais-moi une app de notes");
        pUser.setTextSize(13);
        final TextView pAi = new TextView(a);
        pAi.setText("Voilà, ton app est prête.");
        pAi.setTextSize(13);
        final TextView pInput = new TextView(a);
        pInput.setText("Écris à KRUX…");
        pInput.setTextSize(13);
        final TextView pBtn = new TextView(a);
        pBtn.setText("Envoyer");
        pBtn.setTextSize(13);
        pBtn.setGravity(android.view.Gravity.CENTER);
        pBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams lpU = new LinearLayout.LayoutParams(-2, -2);
        lpU.gravity = android.view.Gravity.END;
        pUser.setPadding((int) (12 * d), (int) (8 * d), (int) (12 * d), (int) (8 * d));
        pAi.setPadding((int) (2 * d), (int) (10 * d), (int) (12 * d), (int) (10 * d));
        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        pInput.setPadding((int) (14 * d), (int) (10 * d), (int) (14 * d), (int) (10 * d));
        bar.addView(pInput, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams lpB = new LinearLayout.LayoutParams((int) (84 * d), (int) (40 * d));
        lpB.leftMargin = (int) (8 * d);
        bar.addView(pBtn, lpB);
        preview.addView(pUser, lpU);
        preview.addView(pAi);
        LinearLayout.LayoutParams lpBar = new LinearLayout.LayoutParams(-1, -2);
        lpBar.topMargin = (int) (10 * d);
        preview.addView(bar, lpBar);
        box.addView(preview, new LinearLayout.LayoutParams(-1, -2));

        box.addView(label(a, "Palette", d));
        HorizontalScrollView hsP = new HorizontalScrollView(a);
        hsP.setHorizontalScrollBarEnabled(false);
        final LinearLayout rowP = new LinearLayout(a);
        hsP.addView(rowP);
        box.addView(hsP);

        box.addView(label(a, "Couleur d'accent", d));
        HorizontalScrollView hsA = new HorizontalScrollView(a);
        hsA.setHorizontalScrollBarEnabled(false);
        final LinearLayout rowA = new LinearLayout(a);
        hsA.addView(rowA);
        box.addView(hsA);

        box.addView(label(a, "Boutons", d));
        final LinearLayout rowR = new LinearLayout(a);
        box.addView(rowR);

        TextView adv = new TextView(a);
        adv.setText("Réglage détaillé des couleurs…");
        adv.setTextSize(13);
        adv.setTag("krux:accent");
        adv.setPadding(0, (int) (22 * d), 0, (int) (6 * d));
        adv.setOnClickListener(v -> { if (ref[0] != null) ref[0].dismiss(); showAdvanced(a, onChanged); });
        box.addView(adv);

        refresh[0] = () -> {
            if (ref[0] != null) skin(ref[0], a); // d'abord le fond du dialogue, puis nos couleurs par-dessus
            int tx = get(a, "text"), ac = get(a, "accent");
            float rb = radiusDp(a) * d;
            preview.setBackground(round(get(a, "bg"), 18 * d, ColorUtils.setAlphaComponent(tx, 40), (int) d));
            pUser.setTextColor(tx);
            pUser.setBackground(round(get(a, "bubble_user"), 16 * d, 0, 0));
            pAi.setTextColor(tx);
            pInput.setTextColor(get(a, "hint"));
            pInput.setBackground(round(get(a, "input"), 22 * d, 0, 0));
            pBtn.setTextColor(get(a, "buttontext"));
            pBtn.setBackground(round(get(a, "button"), rb, 0, 0));

            rowP.removeAllViews();
            for (int i = 0; i < PALETTES.length; i++) {
                final String p = PALETTES[i];
                int[] def = defaults(p);
                boolean sel = p.equals(palette(a));
                LinearLayout cell = new LinearLayout(a);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(android.view.Gravity.CENTER);
                cell.setPadding((int) (4 * d), 0, (int) (8 * d), 0);
                View sw = new View(a);
                sw.setBackground(round(def[0], 14 * d, sel ? ac : ColorUtils.setAlphaComponent(tx, 60), (int) ((sel ? 3 : 1) * d)));
                FrameLayout fl = new FrameLayout(a);
                fl.addView(sw, new FrameLayout.LayoutParams((int) (62 * d), (int) (62 * d)));
                View dot = new View(a);
                dot.setBackground(round(def[7], 100 * d, 0, 0));
                fl.addView(dot, new FrameLayout.LayoutParams((int) (20 * d), (int) (20 * d), android.view.Gravity.CENTER));
                TextView nm = new TextView(a);
                nm.setText(PALETTE_LABELS[i]);
                nm.setTextSize(11);
                nm.setTextColor(sel ? tx : get(a, "text2"));
                nm.setGravity(android.view.Gravity.CENTER);
                nm.setPadding(0, (int) (4 * d), 0, 0);
                cell.addView(fl);
                cell.addView(nm);
                cell.setOnClickListener(v -> { setPalette(a, p); refresh[0].run(); });
                rowP.addView(cell);
            }

            rowA.removeAllViews();
            for (final int col : ACCENTS) {
                boolean sel = get(a, "accent") == col;
                View c = new View(a);
                c.setBackground(round(col, 100 * d, sel ? tx : ColorUtils.setAlphaComponent(tx, 60), (int) ((sel ? 3 : 1) * d)));
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams((int) (38 * d), (int) (38 * d));
                cp.rightMargin = (int) (10 * d);
                c.setOnClickListener(v -> {
                    set(a, "accent", col);
                    set(a, "button", col);
                    set(a, "buttontext", ColorUtils.calculateLuminance(col) > 0.5 ? 0xFF000000 : 0xFFFFFFFF);
                    refresh[0].run();
                });
                rowA.addView(c, cp);
            }

            rowR.removeAllViews();
            String[] names = {"Doux", "Arrondi", "Pilule"};
            final int[] vals = {10, 18, 999};
            for (int i = 0; i < 3; i++) {
                final int val = vals[i];
                boolean sel = radiusDp(a) == val;
                TextView b = new TextView(a);
                b.setText(names[i]);
                b.setTextSize(13);
                b.setGravity(android.view.Gravity.CENTER);
                b.setTextColor(sel ? get(a, "buttontext") : tx);
                b.setBackground(round(sel ? get(a, "button") : get(a, "input"), val * d, 0, 0));
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, (int) (42 * d), 1f);
                rp.rightMargin = (int) (8 * d);
                b.setOnClickListener(v -> { setRadiusDp(a, val); refresh[0].run(); });
                rowR.addView(b, rp);
            }
            applyTo(box, a);
        };

        ref[0] = new androidx.appcompat.app.AlertDialog.Builder(a)
            .setTitle("Personnaliser")
            .setView(scroll)
            .setPositiveButton("Terminé", (dlg, which) -> {
                apply(a);
                if (onChanged != null) onChanged.run();
            })
            .setNeutralButton("Réinitialiser", (dlg, which) -> {
                clearOverrides(a);
                setRadiusDp(a, 18);
                apply(a);
                if (onChanged != null) onChanged.run();
            })
            .create();
        ref[0].setOnCancelListener(dlg -> { apply(a); if (onChanged != null) onChanged.run(); });
        ref[0].show();
        refresh[0].run();
    }

    /** Réglage fin : une couleur par ligne (#RRGGBB). */
    private static void showAdvanced(final Activity a, final Runnable onChanged) {
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
