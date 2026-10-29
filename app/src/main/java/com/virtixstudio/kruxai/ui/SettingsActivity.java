package com.virtixstudio.kruxai.ui;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.virtixstudio.kruxai.core.KruxConfig;
import com.virtixstudio.kruxai.utils.LanguageManager;

/**
 * Écran Paramètres de KRUX : un seul endroit pour le compte, l'apparence, la langue, les données et l'à-propos.
 * Les actions qui dépendent de la discussion en cours (export, personnalisation du fond) sont renvoyées à
 * MainActivity par un résultat : setResult(RESULT_OK, intent avec "action").
 */
public class SettingsActivity extends AppCompatActivity {

    public static final String EXTRA_ACTION = "action";
    public static final String ACTION_EXPORT = "export";
    public static final String ACTION_CUSTOMIZE = "customize";

    private int bg, text, text2, accent, surface, buttonBg, buttonText;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        bg = KruxTheme.get(this, "bg");
        text = KruxTheme.get(this, "text");
        text2 = KruxTheme.get(this, "text2");
        accent = KruxTheme.get(this, "accent");
        surface = KruxTheme.get(this, "input");
        buttonBg = KruxTheme.get(this, "button");
        buttonText = KruxTheme.get(this, "buttontext");

        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        boolean light = ColorUtils.calculateLuminance(bg) > 0.5;
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(light);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        scroll.setFillViewport(true);
        scroll.setFitsSystemWindows(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(28));
        scroll.addView(root);

        // ---- En-tête
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this);
        back.setText("←");
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        back.setTextColor(text);
        back.setPadding(dp(4), dp(8), dp(16), dp(8));
        back.setContentDescription("Retour");
        back.setOnClickListener(v -> finish());
        header.addView(back);
        TextView title = new TextView(this);
        title.setText("Paramètres");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(text);
        header.addView(title);
        root.addView(header);

        // ---- Compte
        FirebaseUser user = null;
        try { user = FirebaseAuth.getInstance().getCurrentUser(); } catch (Exception ignore) { /* Firebase absent */ }
        String email = user != null && user.getEmail() != null ? user.getEmail() : "Non connecté";
        LinearLayout account = section(root, "Compte");
        profileRow(account, email);
        row(account, "Se déconnecter", "Retourner à l'écran de connexion", true, this::logout);

        // ---- Apparence
        LinearLayout look = section(root, "Apparence");
        row(look, "Couleurs et thème", "Palette, fond, boutons, bulles", false,
            () -> KruxTheme.showEditor(this, this::recreate));
        row(look, "Personnaliser l'app", "Fond d'écran et aperçu des bulles", false, () -> give(ACTION_CUSTOMIZE));
        row(look, "Langue", languageName(LanguageManager.getLanguage(this)), false,
            () -> LanguageManager.showLanguageDialog(this));

        // ---- Données
        LinearLayout data = section(root, "Mes données");
        row(data, "Exporter la discussion", "Envoie la discussion en cours en texte (Markdown)", false, () -> give(ACTION_EXPORT));
        row(data, "Mes apps", "Les applications créées par KRUX et le quota de la semaine", false,
            () -> startActivity(new Intent(this, MyAppsActivity.class)));

        // ---- À propos
        LinearLayout about = section(root, "À propos");
        row(about, "KRUX AI", "Version " + versionName() + "  ·  Virtix Studio", false, null);
        row(about, "Générateur d'apps",
            "Android " + KruxConfig.build().targetSdk + "  ·  AGP " + KruxConfig.build().agp
                + "  ·  config : " + KruxConfig.source(), false, null);

        setContentView(scroll);
    }

    // ------------------------------------------------------------------ construction

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private LinearLayout section(LinearLayout root, String name) {
        TextView label = new TextView(this);
        label.setText(name.toUpperCase());
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setLetterSpacing(0.12f);
        label.setTypeface(null, Typeface.BOLD);
        label.setTextColor(accent);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(6), dp(24), 0, dp(8));
        root.addView(label, lp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(surface);
        g.setCornerRadius(dp(18));
        card.setBackground(g);
        card.setClipToOutline(true);
        root.addView(card, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private void divider(LinearLayout card) {
        if (card.getChildCount() == 0) return;
        View d = new View(this);
        d.setBackgroundColor(ColorUtils.setAlphaComponent(text2, 40));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1);
        lp.setMargins(dp(16), 0, 0, 0);
        card.addView(d, lp);
    }

    private void profileRow(LinearLayout card, String email) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(16), dp(14), dp(16), dp(14));

        TextView avatar = new TextView(this);
        String initial = email.isEmpty() ? "K" : email.substring(0, 1).toUpperCase();
        avatar.setText(initial);
        avatar.setGravity(Gravity.CENTER);
        avatar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        avatar.setTypeface(null, Typeface.BOLD);
        avatar.setTextColor(buttonText);
        GradientDrawable ag = new GradientDrawable();
        ag.setShape(GradientDrawable.OVAL);
        ag.setColor(buttonBg);
        avatar.setBackground(ag);
        r.addView(avatar, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), 0, 0, 0);
        TextView t = new TextView(this);
        t.setText(email);
        t.setTextColor(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setTypeface(null, Typeface.BOLD);
        col.addView(t);
        TextView s = new TextView(this);
        s.setText("Compte KRUX");
        s.setTextColor(text2);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        col.addView(s);
        r.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(r);
    }

    private void row(LinearLayout card, String title, String subtitle, boolean danger, final Runnable onClick) {
        divider(card);
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(16), dp(14), dp(16), dp(14));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(danger ? 0xFFEF4444 : text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        col.addView(t);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = new TextView(this);
            s.setText(subtitle);
            s.setTextColor(text2);
            s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            s.setPadding(0, dp(2), 0, 0);
            col.addView(s);
        }
        r.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (onClick != null) {
            TextView chevron = new TextView(this);
            chevron.setText("›");
            chevron.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            chevron.setTextColor(text2);
            r.addView(chevron);
            r.setClickable(true);
            r.setFocusable(true);
            r.setOnClickListener(v -> onClick.run());
            android.util.TypedValue tv = new android.util.TypedValue();
            if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
                r.setForeground(getDrawable(tv.resourceId));
            }
        }
        card.addView(r);
    }

    // ------------------------------------------------------------------ actions

    private void give(String action) {
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_ACTION, action));
        finish();
    }

    private void logout() {
        try { FirebaseAuth.getInstance().signOut(); } catch (Exception ignore) { /* déjà déconnecté */ }
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }

    private static String languageName(String code) {
        switch (code) {
            case "en": return "English";
            case "es": return "Español";
            case "zh": return "中文";
            default: return "Français";
        }
    }

    private String versionName() {
        try {
            PackageInfo p = getPackageManager().getPackageInfo(getPackageName(), 0);
            return p.versionName == null ? "?" : p.versionName;
        } catch (Exception e) {
            return "?";
        }
    }
}
