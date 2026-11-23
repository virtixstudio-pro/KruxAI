package com.virtixstudio.kruxai.ui;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.virtixstudio.kruxai.live.ScreenReaderService;

/**
 * Guide d'activation de la lecture d'écran : ouvre directement la bonne page d'Android, explique le déblocage
 * des « paramètres restreints » (apps installées hors Play Store) et se ferme tout seul quand c'est activé.
 */
public class ScreenAccessActivity extends AppCompatActivity {

    private static final int BG = 0xFF0B0A12;
    private static final int PANEL = 0xFF16131F;
    private static final int TXT = 0xFFE8E4F2;
    private static final int DIM = 0xFF8B849C;
    private static final int GREEN = 0xFF3DDC84;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private float d;
    private int accent;
    private TextView status;
    private LinearLayout steps;
    private boolean done;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            check();
            if (!done) handler.postDelayed(this, 700);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        d = getResources().getDisplayMetrics().density;
        accent = KruxTheme.get(this, "accent");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(px(10), px(6), px(10), px(6));
        TextView close = pill("Fermer", TXT, PANEL);
        close.setOnClickListener(v -> finish());
        TextView title = new TextView(this);
        title.setText("Lecture de l'écran");
        title.setTextColor(TXT);
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        bar.addView(close);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        bar.addView(new View(this), new LinearLayout.LayoutParams(px(70), 1));
        root.addView(bar, new LinearLayout.LayoutParams(-1, px(56)));

        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(16), px(8), px(16), px(24));
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView intro = new TextView(this);
        intro.setText("Krux lit ton écran seulement quand tu le lui demandes. Rien n'est enregistré, et les mots de passe sont ignorés. "
                + "Android demande de l'activer à la main : suis ces deux étapes, c'est rapide.");
        intro.setTextColor(DIM);
        intro.setTextSize(14);
        col.addView(intro);

        status = new TextView(this);
        status.setTextSize(15);
        status.setTypeface(Typeface.DEFAULT_BOLD);
        status.setPadding(px(14), px(12), px(14), px(12));
        status.setBackground(round(PANEL, 14));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = px(16);
        col.addView(status, sp);

        steps = new LinearLayout(this);
        steps.setOrientation(LinearLayout.VERTICAL);
        col.addView(steps, new LinearLayout.LayoutParams(-1, -2));

        steps.addView(card("1   Activer KRUX",
                "Appuie sur le bouton, cherche « KRUX » dans la liste (parfois sous « Apps installées »), "
                        + "ouvre-le, active l'interrupteur puis appuie sur « Autoriser ». Ce guide se ferme tout seul quand c'est bon.",
                "Ouvrir Accessibilité", this::openAccessibility));
        if (Build.VERSION.SDK_INT >= 33) {
            steps.addView(card("2   Si c'est grisé ou refusé",
                    "Android bloque cette option pour les apps installées hors Play Store. Appuie sur le bouton, puis sur ⋮ "
                            + "(trois points en haut à droite) et choisis « Autoriser les paramètres restreints ». "
                            + "Si ⋮ n'apparaît pas, essaie d'abord l'étape 1, puis reviens ici. Ensuite recommence l'étape 1.",
                    "Ouvrir les infos de KRUX", this::openAppInfo));
        }

        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets s = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(s.left, s.top, s.right, s.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        check();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!done) handler.post(poll);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(poll);
        super.onPause();
    }

    private void check() {
        if (done) return;
        if (ScreenReaderService.isOn()) {
            done = true;
            status.setText("✓ Lecture d'écran activée. Tu peux demander à Krux de lire ton écran.");
            status.setTextColor(GREEN);
            steps.setVisibility(View.GONE);
            Toast.makeText(this, "Lecture d'écran activée", Toast.LENGTH_SHORT).show();
            handler.postDelayed(this::finish, 1600);
        } else {
            status.setText("Pas encore activée");
            status.setTextColor(DIM);
        }
    }

    private void openAccessibility() {
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                ComponentName cn = new ComponentName(this, ScreenReaderService.class);
                startActivity(new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                        .putExtra(Intent.EXTRA_COMPONENT_NAME, cn.flattenToString()));
                return;
            } catch (Exception ignored) { }
        }
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, "Ouvre Réglages, puis Accessibilité.", Toast.LENGTH_LONG).show();
        }
    }

    private void openAppInfo() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            Toast.makeText(this, "Ouvre Réglages, Applications, KRUX.", Toast.LENGTH_LONG).show();
        }
    }

    private int px(int dp) { return (int) (dp * d); }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusDp * d);
        return g;
    }

    private TextView pill(String label, int color, int fill) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(14);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(px(16), 0, px(16), 0);
        t.setBackground(round(fill, 16));
        t.setMinHeight(px(42));
        t.setClickable(true);
        return t;
    }

    private LinearLayout card(String title, String text, String button, final Runnable action) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(PANEL, 16));
        c.setPadding(px(16), px(14), px(16), px(16));
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(TXT);
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(t);
        TextView body = new TextView(this);
        body.setText(text);
        body.setTextColor(DIM);
        body.setTextSize(13);
        body.setPadding(0, px(6), 0, px(12));
        c.addView(body);
        TextView btn = pill(button, 0xFF0B0A12, accent);
        btn.setOnClickListener(v -> action.run());
        c.addView(btn, new LinearLayout.LayoutParams(-1, px(46)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = px(14);
        c.setLayoutParams(lp);
        return c;
    }
}
