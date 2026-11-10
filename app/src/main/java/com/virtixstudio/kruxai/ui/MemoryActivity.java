package com.virtixstudio.kruxai.ui;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.virtixstudio.kruxai.core.CloudSync;
import com.virtixstudio.kruxai.database.KruxDatabaseHelper;
import com.virtixstudio.kruxai.utils.MemoryBook;

import java.util.List;

/** Ce que KRUX sait de toi : rangé par thème, avec les dates. Tu peux modifier, supprimer, ajouter. */
public class MemoryActivity extends AppCompatActivity {

    private int bg, text, text2, accent, surface, buttonBg, buttonText;
    private KruxDatabaseHelper db;
    private FirebaseUser user;
    private LinearLayout list;

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

        try { user = FirebaseAuth.getInstance().getCurrentUser(); } catch (Exception ignore) { }
        if (user == null) { finish(); return; }
        db = new KruxDatabaseHelper(this, user.getUid());

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

        LinearLayout header = new LinearLayout(this);
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
        title.setText("Mémoire");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(text);
        header.addView(title);
        root.addView(header);

        TextView hint = new TextView(this);
        hint.setText("Ce que KRUX sait de toi. Touche un souvenir pour le modifier ou le supprimer.");
        hint.setTextColor(text2);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        hint.setPadding(0, dp(6), 0, dp(10));
        root.addView(hint);

        LinearLayout actions = new LinearLayout(this);
        actions.addView(button("Ajouter", this::addDialog), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        actions.addView(button("Tout effacer", this::clearDialog), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(actions);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

        setContentView(scroll);
        render();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private TextView button(String label, Runnable r) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(buttonText);
        t.setBackgroundColor(buttonBg);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        t.setLayoutParams(lp);
        t.setOnClickListener(v -> r.run());
        return t;
    }

    private void render() {
        list.removeAllViews();
        List<MemoryBook.Entry> all = db.getMemoryEntries();
        if (all.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Rien pour l'instant. KRUX retiendra ce que tu lui dis de toi, de tes projets et de tes goûts.");
            empty.setTextColor(text2);
            empty.setPadding(0, dp(24), 0, 0);
            list.addView(empty);
            return;
        }
        for (String cat : MemoryBook.CATEGORIES) {
            boolean first = true;
            for (int i = all.size() - 1; i >= 0; i--) { // le plus récent d'abord
                final MemoryBook.Entry e = all.get(i);
                if (!cat.equals(e.cat)) continue;
                if (first) {
                    TextView label = new TextView(this);
                    label.setText(MemoryBook.label(cat).toUpperCase());
                    label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    label.setLetterSpacing(0.12f);
                    label.setTypeface(null, Typeface.BOLD);
                    label.setTextColor(accent);
                    label.setPadding(0, dp(18), 0, dp(6));
                    list.addView(label);
                    first = false;
                }
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackgroundColor(surface);
                card.setPadding(dp(14), dp(10), dp(14), dp(10));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.setMargins(0, 0, 0, dp(6));
                card.setLayoutParams(lp);
                TextView t = new TextView(this);
                t.setText(e.text);
                t.setTextColor(text);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                card.addView(t);
                TextView d = new TextView(this);
                d.setText(MemoryBook.date(e.ts));
                d.setTextColor(text2);
                d.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                card.addView(d);
                card.setOnClickListener(v -> editDialog(e));
                list.addView(card);
            }
        }
    }

    private void changed() {
        CloudSync.markMemoryReplace(this, user); // les suppressions doivent aussi partir vers les autres appareils
        render();
    }

    private EditText input(String initial) {
        EditText et = new EditText(this);
        et.setText(initial);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        et.setMaxLines(4);
        et.setTextColor(text);
        et.setHintTextColor(text2);
        int p = dp(20);
        et.setPadding(p, p / 2, p, p / 2);
        return et;
    }

    private void editDialog(final MemoryBook.Entry e) {
        final EditText et = input(e.text);
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Souvenir")
                .setView(et)
                .setPositiveButton("Enregistrer", (x, w) -> {
                    String n = et.getText().toString().trim();
                    if (n.length() >= 3 && n.length() <= 300 && !n.equals(e.text)) { db.editMemoryText(e.text, n); changed(); }
                })
                .setNeutralButton("Supprimer", (x, w) -> { db.deleteMemoryText(e.text); changed(); })
                .setNegativeButton("Annuler", null)
                .create();
        d.show();
        KruxTheme.skin(d, this);
    }

    private void addDialog() {
        final EditText et = input("");
        et.setHint("Ex. : J'habite à Lubumbashi");
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Ajouter un souvenir")
                .setView(et)
                .setPositiveButton("Ajouter", (x, w) -> {
                    String n = et.getText().toString().trim();
                    if (n.length() >= 3 && n.length() <= 300 && db.upsertMemory(n)) changed();
                })
                .setNegativeButton("Annuler", null)
                .create();
        d.show();
        KruxTheme.skin(d, this);
    }

    private void clearDialog() {
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Tout effacer ?")
                .setMessage("KRUX oubliera tout ce qu'il sait de toi, sur tous tes appareils.")
                .setPositiveButton("Tout effacer", (x, w) -> { db.clearMemory(); changed(); })
                .setNegativeButton("Annuler", null)
                .create();
        d.show();
        KruxTheme.skin(d, this);
    }
}
