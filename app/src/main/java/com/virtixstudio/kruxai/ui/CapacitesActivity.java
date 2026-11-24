package com.virtixstudio.kruxai.ui;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.virtixstudio.kruxai.R;

/** Écran « Capacités » (même esprit que celui de Claude) : agent, navigateur en direct, nombre d'actions. */
public class CapacitesActivity extends Activity {
    private int bg, text, text2, accent, surface;
    private LinearLayout root;
    private TextView maxValue;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        bg = KruxTheme.get(this, "bg");
        text = KruxTheme.get(this, "text");
        text2 = KruxTheme.get(this, "text2");
        accent = KruxTheme.get(this, "accent");
        surface = KruxTheme.get(this, "input");
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(32));
        scroll.addView(root);

        TextView back = new TextView(this);
        back.setText("←  Capacités");
        back.setTextColor(text);
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        back.setTypeface(null, Typeface.BOLD);
        back.setPadding(dp(4), dp(8), 0, dp(16));
        back.setOnClickListener(v -> finish());
        root.addView(back);

        toggle(R.drawable.ic_line_agent, "Mode agent", "Krux Code peut ouvrir des sites, cliquer, taper dans les champs et défiler pour accomplir une tâche, puis te rendre le résultat.",
                "agent", AgentPrefs.agentOn(this));
        toggle(R.drawable.ic_line_globe, "Navigateur en direct", "Affiche le navigateur de Krux pendant que l'agent travaille, pour que tu voies chaque page en temps réel. Tu peux t'y connecter toi-même à un site : les sessions restent ouvertes.",
                "voir_navigateur", AgentPrefs.showBrowser(this));

        // nombre d'actions par demande : 3, 5, 8, 12 (un appui change la valeur)
        LinearLayout card = card();
        TextView t = title("Actions par demande");
        TextView d = desc("Plus il y en a, plus l'agent va loin, mais plus ça consomme de quota. Touche pour changer.");
        maxValue = new TextView(this);
        maxValue.setTextColor(accent);
        maxValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        maxValue.setTypeface(null, Typeface.BOLD);
        maxValue.setPadding(0, dp(6), 0, 0);
        showMax();
        card.addView(t); card.addView(d); card.addView(maxValue);
        card.setOnClickListener(v -> {
            int[] steps = {3, 5, 8, 12};
            int cur = AgentPrefs.maxSteps(this), next = steps[0];
            for (int i = 0; i < steps.length; i++) if (steps[i] == cur) next = steps[(i + 1) % steps.length];
            AgentPrefs.setMax(this, next);
            showMax();
        });

        LinearLayout note = card();
        note.addView(title("Connexion aux sites"));
        note.addView(desc("L'agent ne tape jamais de mot de passe. Pour un site avec compte : ouvre-le dans le navigateur de Krux, connecte-toi toi-même, puis demande à l'agent de continuer."));

        setContentView(scroll);
    }

    private void showMax() { maxValue.setText(AgentPrefs.maxSteps(this) + " actions maximum"); }

    private void toggle(int icon, String title, String desc, final String key, boolean on) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ic = new ImageView(this);
        ic.setImageDrawable(UiIcons.get(this, icon, text, 24));
        ic.setPadding(0, 0, dp(14), 0);
        card.addView(ic, new LinearLayout.LayoutParams(dp(38), dp(24)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(title(title));
        col.addView(desc(desc));
        card.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(on);
        sw.setOnCheckedChangeListener((v, checked) -> AgentPrefs.set(this, key, checked));
        card.addView(sw);
        card.setOnClickListener(v -> sw.toggle());
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable g = new GradientDrawable();
        g.setColor(surface);
        g.setCornerRadius(dp(18));
        c.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        root.addView(c, lp);
        return c;
    }

    private TextView title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private TextView desc(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(text2);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setPadding(0, dp(4), dp(8), 0);
        return t;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
