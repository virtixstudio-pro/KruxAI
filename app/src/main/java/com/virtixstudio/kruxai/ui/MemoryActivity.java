package com.virtixstudio.kruxai.ui;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.content.Intent;
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
    private String openCat;
    private TextView titleView, menuBtn;
    private LinearLayout bottomBar;
    private EditText instruction;

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
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(bg));
        boolean light = ColorUtils.calculateLuminance(bg) > 0.5;
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(light);

        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        // contrôles de l'ancien écran, gardés hors écran car learnFromChats() et render() les mettent à jour
        summary = new TextView(this);
        learnBtn = new TextView(this);
        undoBtn = new TextView(this);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(bg);
        page.setFitsSystemWindows(true);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(10), dp(14), dp(10));
        TextView back = new TextView(this);
        back.setText("←");
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        back.setTextColor(text);
        back.setPadding(dp(4), dp(8), dp(12), dp(8));
        back.setContentDescription("Retour");
        back.setOnClickListener(v -> onBackPressed());
        header.addView(back);
        titleView = new TextView(this);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(text);
        header.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));
        menuBtn = new TextView(this);
        menuBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        menuBtn.setPadding(dp(12), dp(6), dp(4), dp(6));
        menuBtn.setOnClickListener(v -> {
            if (openCat != null) { trashDialog(); return; }
            android.widget.PopupMenu pm = new android.widget.PopupMenu(this, v);
            pm.getMenu().add(0, 1, 1, "Ajouter un souvenir");
            pm.getMenu().add(0, 2, 2, "Apprendre de mes discussions");
            if (memory.undoCount() > 0) pm.getMenu().add(0, 3, 3, "Annuler la dernière modification");
            pm.getMenu().add(0, 4, 4, "Tout effacer");
            pm.setOnMenuItemClickListener(it -> {
                switch (it.getItemId()) {
                    case 1: addDialog(); break;
                    case 2: learnFromChats(); break;
                    case 3: undoLastChange(); break;
                    default: clearDialog();
                }
                return true;
            });
            pm.show();
        });
        header.addView(menuBtn);
        page.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(4), dp(16), dp(20));
        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        bottomBar = new LinearLayout(this);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setPadding(dp(14), dp(8), dp(14), dp(12));
        android.graphics.drawable.GradientDrawable bar = new android.graphics.drawable.GradientDrawable();
        bar.setColor(surface);
        bar.setCornerRadius(dp(26));
        LinearLayout pill = new LinearLayout(this);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setBackground(bar);
        pill.setPadding(dp(16), dp(6), dp(8), dp(6));
        instruction = new EditText(this);
        instruction.setHint("Indiquez à Krux ce qu'il faut modifier");
        instruction.setHintTextColor(text2);
        instruction.setTextColor(text);
        instruction.setBackground(null);
        instruction.setMaxLines(4);
        instruction.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        pill.addView(instruction, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView mic = new TextView(this);
        mic.setText("🎤");
        mic.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        mic.setPadding(dp(10), dp(6), dp(10), dp(6));
        mic.setOnClickListener(v -> {
            try {
                Intent i = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault());
                startActivityForResult(i, 71);
            } catch (Exception e) {
                android.widget.Toast.makeText(this, "Dictée indisponible sur cet appareil", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        pill.addView(mic);
        TextView send = new TextView(this);
        send.setText("↑");
        send.setGravity(Gravity.CENTER);
        send.setTextColor(buttonText);
        send.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        android.graphics.drawable.GradientDrawable sg = new android.graphics.drawable.GradientDrawable();
        sg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        sg.setColor(buttonBg);
        send.setBackground(sg);
        send.setOnClickListener(v -> sendInstruction());
        pill.addView(send, new LinearLayout.LayoutParams(dp(42), dp(42)));
        bottomBar.addView(pill, new LinearLayout.LayoutParams(-1, -2));
        page.addView(bottomBar);

        setContentView(page);
        render();
    }

    @Override
    public void onBackPressed() {
        if (openCat != null) { openCat = null; render(); } else super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 71 && res == RESULT_OK && data != null) {
            java.util.ArrayList<String> r = data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
            if (r != null && !r.isEmpty() && instruction != null) instruction.setText(r.get(0));
        }
    }

    private String ago(long ts) {
        long d = System.currentTimeMillis() / 1000L - ts;
        if (d < 60) return "à l'instant";
        if (d < 3600) return "il y a " + (d / 60) + (d / 60 > 1 ? " minutes" : " minute");
        if (d < 86400) return "il y a " + (d / 3600) + (d / 3600 > 1 ? " heures" : " heure");
        if (d < 30L * 86400) return "il y a " + (d / 86400) + (d / 86400 > 1 ? " jours" : " jour");
        return MemoryBook.date(ts);
    }

    private void trashDialog() {
        final String cat = openCat;
        if (cat == null) return;
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Supprimer « " + MemoryBook.label(cat) + " » ?")
                .setMessage("Tous les souvenirs de ce fichier seront effacés. Tu pourras annuler juste après.")
                .setPositiveButton("Supprimer", (x, w) -> {
                    for (MemoryBook.Entry e : new java.util.ArrayList<>(memory.all())) if (cat.equals(e.cat)) memory.delete(e.text);
                    openCat = null;
                    changed();
                })
                .setNegativeButton("Annuler", null)
                .create();
        d.show();
        KruxTheme.skin(d, this);
    }

    /** « Indiquez à Krux ce qu'il faut modifier » : l'IA applique la demande à la mémoire. */
    private void sendInstruction() {
        final String ask = instruction.getText().toString().trim();
        if (ask.length() < 3 || learning) return;
        learning = true;
        instruction.setText("");
        instruction.setHint("Krux met la mémoire à jour…");
        String where = openCat == null ? "" : " (fichier « " + MemoryBook.label(openCat) + " »)";
        String usr = MemoryBook.extractionUserPrompt(memory.all(),
                "DEMANDE DE MODIFICATION DE LA MÉMOIRE" + where + " : " + ask,
                "(l'utilisateur te demande de modifier sa mémoire : applique exactement sa demande, ajoute, corrige ou supprime)");
        com.virtixstudio.kruxai.api.ApiClient.sendQuiet(MemoryBook.extractionSystemPrompt(), usr, out -> {
            int n = 0;
            try {
                if (out != null) {
                    List<MemoryBook.Op> ops = MemoryBook.parseOps(out, System.currentTimeMillis() / 1000L);
                    if (!ops.isEmpty()) n = memory.apply(ops).size();
                }
            } catch (Exception ignore) { }
            final int fn = n;
            runOnUiThread(() -> {
                learning = false;
                instruction.setHint("Indiquez à Krux ce qu'il faut modifier");
                if (fn > 0) CloudSync.markMemoryReplace(this, user);
                render();
                android.widget.Toast.makeText(this, fn > 0 ? "Mémoire mise à jour" : "Je n'ai rien changé", android.widget.Toast.LENGTH_SHORT).show();
            });
        });
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
        if (undoBtn != null) undoBtn.setVisibility(memory.undoCount() > 0 ? android.view.View.VISIBLE : android.view.View.GONE);
        boolean file = openCat != null;
        titleView.setText(file ? MemoryBook.label(openCat) : "Fichiers mémoire");
        menuBtn.setText(file ? "🗑" : "⋯");
        menuBtn.setTextColor(file ? 0xFFE5736F : text);
        bottomBar.setVisibility(file ? android.view.View.VISIBLE : android.view.View.GONE);
        if (file) {
            boolean any = false;
            for (int i = all.size() - 1; i >= 0; i--) {
                final MemoryBook.Entry e = all.get(i);
                if (!openCat.equals(e.cat)) continue;
                any = true;
                TextView t = new TextView(this);
                t.setText("•  " + e.text);
                t.setTextColor(text);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                t.setLineSpacing(0, 1.1f);
                t.setPadding(dp(4), dp(10), dp(4), dp(10));
                t.setOnClickListener(v -> editDialog(e));
                list.addView(t);
            }
            if (!any) { openCat = null; render(); }
            return;
        }
        if (all.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Rien pour l'instant. KRUX retiendra ce que tu lui dis de toi, de tes projets et de tes goûts.");
            empty.setTextColor(text2);
            empty.setPadding(0, dp(24), 0, 0);
            list.addView(empty);
            return;
        }
        String[][] groups = {
                {"Toi", MemoryBook.PROFIL, MemoryBook.PREF},
                {"Projets et personnes", MemoryBook.PROJETS, MemoryBook.PERSONNES},
                {"Divers", MemoryBook.DIVERS}
        };
        for (String[] g : groups) {
            LinearLayout card = null;
            for (int gi = 1; gi < g.length; gi++) {
                final String cat = g[gi];
                long last = 0; int count = 0;
                for (MemoryBook.Entry e : all) if (cat.equals(e.cat)) { count++; last = Math.max(last, e.ts); }
                if (count == 0) continue;
                if (card == null) {
                    TextView sec = new TextView(this);
                    sec.setText(g[0]);
                    sec.setTextColor(text2);
                    sec.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                    sec.setPadding(dp(4), dp(18), 0, dp(8));
                    list.addView(sec);
                    card = new LinearLayout(this);
                    card.setOrientation(LinearLayout.VERTICAL);
                    android.graphics.drawable.GradientDrawable bgc = new android.graphics.drawable.GradientDrawable();
                    bgc.setColor(surface);
                    bgc.setCornerRadius(dp(20));
                    card.setBackground(bgc);
                    card.setClipToOutline(true);
                    list.addView(card, new LinearLayout.LayoutParams(-1, -2));
                }
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(18), dp(14), dp(18), dp(14));
                TextView n = new TextView(this);
                n.setText(MemoryBook.label(cat));
                n.setTextColor(text);
                n.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                row.addView(n);
                TextView d = new TextView(this);
                d.setText("Mis à jour : " + ago(last) + "  ·  " + count + (count > 1 ? " souvenirs" : " souvenir"));
                d.setTextColor(text2);
                d.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                d.setPadding(0, dp(2), 0, 0);
                row.addView(d);
                row.setOnClickListener(v -> { openCat = cat; render(); });
                if (card.getChildCount() > 0) {
                    android.view.View div = new android.view.View(this);
                    div.setBackgroundColor(ColorUtils.setAlphaComponent(text2, 40));
                    card.addView(div, new LinearLayout.LayoutParams(-1, 1));
                }
                card.addView(row);
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
