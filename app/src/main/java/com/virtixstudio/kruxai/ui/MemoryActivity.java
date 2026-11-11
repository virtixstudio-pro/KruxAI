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
import com.virtixstudio.kruxai.core.MemoryRepository;
import com.virtixstudio.kruxai.database.KruxDatabaseHelper;
import com.virtixstudio.kruxai.utils.MemoryBook;

import java.util.List;

/** Ce que KRUX sait de toi : rangé par thème, avec les dates. Tu peux modifier, supprimer, ajouter. */
public class MemoryActivity extends AppCompatActivity {

    private int bg, text, text2, accent, surface, buttonBg, buttonText;
    private MemoryRepository memory;
    private FirebaseUser user;
    private LinearLayout list;
    private TextView summary;
    private TextView learnBtn;
    private TextView undoBtn;
    private boolean learning;

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
        memory = new MemoryRepository(new KruxDatabaseHelper(this, user.getUid()));
        memory.setOnChange(() -> CloudSync.markMemoryReplace(this, user));

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

        summary = new TextView(this);
        summary.setTextColor(text);
        summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        summary.setLineSpacing(0, 1.15f);
        summary.setBackgroundColor(surface);
        summary.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.setMargins(0, 0, 0, dp(10));
        root.addView(summary, slp);

        LinearLayout actions = new LinearLayout(this);
        actions.addView(button("Ajouter", this::addDialog), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        actions.addView(button("Tout effacer", this::clearDialog), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(actions);
        LinearLayout actions2 = new LinearLayout(this);
        learnBtn = button("Apprendre de mes discussions", this::learnFromChats);
        actions2.addView(learnBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams a2 = new LinearLayout.LayoutParams(-1, -2);
        a2.setMargins(0, dp(6), 0, 0);
        root.addView(actions2, a2);

        LinearLayout actions3 = new LinearLayout(this);
        undoBtn = button("Annuler la dernière modification", this::undoLastChange);
        undoBtn.setVisibility(android.view.View.GONE);
        actions3.addView(undoBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams a3 = new LinearLayout.LayoutParams(-1, -2);
        a3.setMargins(0, dp(6), 0, 0);
        root.addView(actions3, a3);

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
        List<MemoryBook.Entry> all = memory.all();
        String sum = MemoryBook.summary(all, 700);
        summary.setText(sum.isEmpty() ? "Je ne sais encore rien sur toi." : "Ce que je sais de toi\n\n" + sum);
        if (undoBtn != null) {
            int n = memory.undoCount();
            undoBtn.setVisibility(n > 0 ? android.view.View.VISIBLE : android.view.View.GONE);
            undoBtn.setText(n > 1 ? "Annuler (" + n + ")" : "Annuler la dernière modification");
        }
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
        render(); // la synchro est prévenue par l'écouteur du dépôt
    }

    /** Remet la mémoire dans l'état d'avant la dernière modification (suppression, édition, apprentissage…). */
    private void undoLastChange() {
        if (memory.undo()) {
            changed();
            android.widget.Toast.makeText(this, "Modification annulée", android.widget.Toast.LENGTH_SHORT).show();
        } else {
            android.widget.Toast.makeText(this, "Rien à annuler", android.widget.Toast.LENGTH_SHORT).show();
        }
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
                    if (n.length() >= 3 && n.length() <= 300 && !n.equals(e.text)) { memory.edit(e.text, n); changed(); }
                })
                .setNeutralButton("Supprimer", (x, w) -> { memory.delete(e.text); changed(); })
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
                    if (n.length() >= 3 && n.length() <= 300 && memory.remember(n)) changed();
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
                .setPositiveButton("Tout effacer", (x, w) -> { memory.clear(); changed(); })
                .setNegativeButton("Annuler", null)
                .create();
        d.show();
        KruxTheme.skin(d, this);
    }

    /** Relit tes anciens messages (ceux où tu parles de toi) et retient ce qui compte. Quelques petits appels, une seule fois par clic. */
    private void learnFromChats() {
        if (learning) return;
        final List<String> batches = MemoryBook.learnBatches(memory.recentUserMessages(120), 1800, 3);
        if (batches.isEmpty()) {
            android.widget.Toast.makeText(this, "Rien à apprendre dans tes discussions pour l'instant.", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        learning = true;
        learnBtn.setText("Je lis tes discussions…");
        final int[] done = {0};
        final int[] added = {0};
        final String sys = MemoryBook.extractionSystemPrompt();
        for (final String batch : batches) {
            String usr = MemoryBook.extractionUserPrompt(memory.all(), batch, "(ce sont plusieurs anciens messages de l'utilisateur ; ne retiens que ce qui est durable)");
            com.virtixstudio.kruxai.api.ApiClient.sendQuiet(sys, usr, out -> {
                try {
                    if (out != null) {
                        List<MemoryBook.Op> ops = MemoryBook.parseOps(out, System.currentTimeMillis() / 1000L);
                        if (!ops.isEmpty()) added[0] += memory.apply(ops).size();
                    }
                } catch (Exception ignore) { }
                runOnUiThread(() -> {
                    done[0]++;
                    if (done[0] < batches.size()) return;
                    learning = false;
                    learnBtn.setText("Apprendre de mes discussions");
                    if (added[0] > 0) CloudSync.markMemoryReplace(this, user);
                    render();
                    android.widget.Toast.makeText(this, added[0] > 0 ? added[0] + " souvenir(s) mis à jour" : "Rien de nouveau à retenir", android.widget.Toast.LENGTH_SHORT).show();
                });
            });
        }
    }
}
