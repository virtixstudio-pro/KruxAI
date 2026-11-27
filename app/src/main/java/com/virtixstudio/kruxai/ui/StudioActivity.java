package com.virtixstudio.kruxai.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.virtixstudio.kruxai.R;
import com.virtixstudio.kruxai.api.ApiClient;
import com.virtixstudio.kruxai.models.KruxModel;
import com.virtixstudio.kruxai.models.StudioAgent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Studio : une équipe d'agents qui travaillent à la suite sur une tâche.
 * Trois écrans : Équipe (composition), Lancer (progression), Résultats (cartes, copier, partager).
 */
public class StudioActivity extends AppCompatActivity {

    private static final String PREFS = "krux_studio";

    /** Équipes prêtes : {nom de l'équipe, "Agent|rôle", ...}. */
    private static final String[][] TEAMS = {
        {"Création d'app", "Designer UX|Définit les écrans, le parcours et le style",
            "Développeur|Écrit le code et la structure", "Testeur|Cherche les bugs et les cas limites"},
        {"Rédaction", "Rédacteur|Écrit un premier jet clair",
            "Relecteur|Corrige le style, la clarté et les fautes", "Éditeur|Produit la version finale"},
        {"Recherche", "Chercheur|Rassemble les faits et les pistes",
            "Analyste|Compare et dégage les points clés", "Synthèse|Résume en peu de mots"},
        {"Business", "Stratège|Définit la cible et la proposition de valeur",
            "Marketeur|Imagine le message et les canaux", "Financier|Estime coûts, revenus et risques"}
    };

    private final List<StudioAgent> agents = new ArrayList<>();
    private final List<StudioAgent> runTeam = new ArrayList<>();
    private final List<String> states = new ArrayList<>();   // wait, run, ok, err, stop
    private final List<String> results = new ArrayList<>();

    private EditText etName, etObjective, etAgentName, etAgentRole, etTask;
    private TextView tvStatus, tvNoAgents, tvResultsEmpty, tabTeam, tabRun, tabResults;
    private View paneTeam, paneRun, paneResults, rowResultActions;
    private LinearLayout listAgents, listSteps, listResults, chipTemplates;
    private ProgressBar progress;
    private Button btnRun, btnStop;

    private boolean running;
    private int runToken;
    private String runTask = "";
    private String runName = "";
    private String runObjective = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_studio);
        KruxTheme.apply(this);

        etName = findViewById(R.id.etStudioName);
        etObjective = findViewById(R.id.etStudioObjective);
        etAgentName = findViewById(R.id.etAgentName);
        etAgentRole = findViewById(R.id.etAgentRole);
        etTask = findViewById(R.id.etStudioTask);
        tvStatus = findViewById(R.id.tvStudioStatus);
        tvNoAgents = findViewById(R.id.tvNoAgents);
        tvResultsEmpty = findViewById(R.id.tvResultsEmpty);
        tabTeam = findViewById(R.id.tabTeam);
        tabRun = findViewById(R.id.tabRun);
        tabResults = findViewById(R.id.tabResults);
        paneTeam = findViewById(R.id.paneTeam);
        paneRun = findViewById(R.id.paneRun);
        paneResults = findViewById(R.id.paneResults);
        rowResultActions = findViewById(R.id.rowResultActions);
        listAgents = findViewById(R.id.listAgents);
        listSteps = findViewById(R.id.listSteps);
        listResults = findViewById(R.id.listResults);
        chipTemplates = findViewById(R.id.chipTemplates);
        progress = findViewById(R.id.studioProgress);
        btnRun = findViewById(R.id.btnRunStudio);
        btnStop = findViewById(R.id.btnStopStudio);

        findViewById(R.id.btnStudioBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnAddAgent).setOnClickListener(v -> addAgent());
        btnRun.setOnClickListener(v -> startRun());
        btnStop.setOnClickListener(v -> stopRun());
        findViewById(R.id.btnCopyAll).setOnClickListener(v -> copy(buildReport(), "Résultats copiés"));
        findViewById(R.id.btnShareAll).setOnClickListener(v -> share(buildReport()));
        tabTeam.setOnClickListener(v -> showTab(0));
        tabRun.setOnClickListener(v -> showTab(1));
        tabResults.setOnClickListener(v -> showTab(2));

        load();
        buildTemplateChips();
        refreshAgents();
        refreshSteps();
        refreshResults();
        showTab(0);
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
    }

    // ------------------------------------------------------------------ onglets

    private void showTab(int t) {
        paneTeam.setVisibility(t == 0 ? View.VISIBLE : View.GONE);
        paneRun.setVisibility(t == 1 ? View.VISIBLE : View.GONE);
        paneResults.setVisibility(t == 2 ? View.VISIBLE : View.GONE);
        styleTab(tabTeam, t == 0);
        styleTab(tabRun, t == 1);
        styleTab(tabResults, t == 2);
    }

    private void styleTab(TextView tv, boolean on) {
        tv.setBackground(box(KruxTheme.get(this, on ? "button" : "input"), 20));
        tv.setTextColor(KruxTheme.get(this, on ? "buttontext" : "text2"));
    }

    // ------------------------------------------------------------------ équipe

    private void buildTemplateChips() {
        chipTemplates.removeAllViews();
        for (final String[] team : TEAMS) {
            TextView chip = new TextView(this);
            chip.setText(team[0]);
            chip.setTextSize(14);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setTextColor(KruxTheme.get(this, "text"));
            chip.setBackground(box(KruxTheme.get(this, "input"), 20));
            chip.setPadding(dp(16), dp(10), dp(16), dp(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, dp(8), 0);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> applyTeam(team));
            chipTemplates.addView(chip);
        }
    }

    private void applyTeam(String[] team) {
        if (running) {
            Toast.makeText(this, "Attends la fin de l'exécution ou appuie sur Stop.", Toast.LENGTH_SHORT).show();
            return;
        }
        agents.clear();
        for (int i = 1; i < team.length; i++) {
            String[] p = team[i].split("\\|", 2);
            agents.add(new StudioAgent(p[0], p.length > 1 ? p[1] : "", "", KruxModel.KRUX_33_70B));
        }
        if (etName.getText().toString().trim().isEmpty()) etName.setText(team[0]);
        save();
        refreshAgents();
        Toast.makeText(this, "Équipe « " + team[0] + " » chargée.", Toast.LENGTH_SHORT).show();
    }

    private void addAgent() {
        String name = etAgentName.getText().toString().trim();
        String role = etAgentRole.getText().toString().trim();
        if (name.isEmpty() || role.isEmpty()) {
            Toast.makeText(this, "Renseigne le nom et le rôle de l'agent.", Toast.LENGTH_SHORT).show();
            return;
        }
        agents.add(new StudioAgent(name, role, "", KruxModel.KRUX_33_70B));
        etAgentName.setText("");
        etAgentRole.setText("");
        save();
        refreshAgents();
    }

    private void refreshAgents() {
        listAgents.removeAllViews();
        tvNoAgents.setVisibility(agents.isEmpty() ? View.VISIBLE : View.GONE);
        for (int i = 0; i < agents.size(); i++) {
            final int idx = i;
            StudioAgent a = agents.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(6), dp(10));
            row.setBackground(box(KruxTheme.get(this, "input"), 14));
            row.setLayoutParams(cardParams());

            TextView badge = new TextView(this);
            badge.setText(String.valueOf(i + 1));
            badge.setGravity(Gravity.CENTER);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setTextColor(KruxTheme.get(this, "buttontext"));
            badge.setBackground(box(KruxTheme.get(this, "button"), 16));
            badge.setLayoutParams(new LinearLayout.LayoutParams(dp(32), dp(32)));
            row.addView(badge);

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            cp.setMargins(dp(10), 0, dp(6), 0);
            col.setLayoutParams(cp);
            col.addView(label(a.getName(), 15, true, "text"));
            col.addView(label(a.getRole(), 13, false, "text2"));
            row.addView(col);

            row.addView(iconButton("▲", v -> moveAgent(idx, -1)));
            row.addView(iconButton("▼", v -> moveAgent(idx, 1)));
            row.addView(iconButton("✕", v -> removeAgent(idx)));
            listAgents.addView(row);
        }
    }

    private void moveAgent(int i, int d) {
        int j = i + d;
        if (running || j < 0 || j >= agents.size()) return;
        Collections.swap(agents, i, j);
        save();
        refreshAgents();
    }

    private void removeAgent(int i) {
        if (running || i < 0 || i >= agents.size()) return;
        agents.remove(i);
        save();
        refreshAgents();
    }

    // ------------------------------------------------------------------ exécution

    private void startRun() {
        if (running) return;
        String task = etTask.getText().toString().trim();
        if (task.isEmpty()) {
            Toast.makeText(this, "Écris d'abord la tâche à confier.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (agents.isEmpty()) {
            Toast.makeText(this, "Ajoute au moins un agent dans l'onglet Équipe.", Toast.LENGTH_SHORT).show();
            return;
        }
        runTeam.clear();
        runTeam.addAll(agents);
        states.clear();
        results.clear();
        for (int i = 0; i < runTeam.size(); i++) { states.add("wait"); results.add(""); }
        runTask = task;
        runName = etName.getText().toString().trim();
        runObjective = etObjective.getText().toString().trim();
        running = true;
        runToken++;
        btnRun.setEnabled(false);
        btnStop.setVisibility(View.VISIBLE);
        progress.setMax(runTeam.size());
        progress.setProgress(0);
        progress.setVisibility(View.VISIBLE);
        refreshSteps();
        refreshResults();
        step(runToken, 0, new StringBuilder());
    }

    private void stopRun() {
        if (!running) return;
        runToken++;
        for (int i = 0; i < states.size(); i++) {
            String s = states.get(i);
            if (s.equals("run") || s.equals("wait")) states.set(i, "stop");
        }
        endRun("Arrêté");
    }

    private void step(final int token, final int index, final StringBuilder previous) {
        if (token != runToken || isFinishing()) return;
        if (index >= runTeam.size()) {
            endRun("Terminé ✓");
            showTab(2);
            return;
        }
        final StudioAgent agent = runTeam.get(index);
        states.set(index, "run");
        tvStatus.setText("Agent " + (index + 1) + "/" + runTeam.size() + " : " + agent.getName() + "…");
        progress.setProgress(index);
        refreshSteps();

        String systemPrompt = "Tu es " + agent.getName() + ", membre de l'équipe Virtix Studio. Ton rôle est : "
            + agent.getRole() + ". "
            + (runObjective.isEmpty() ? "" : "Objectif global du studio : " + runObjective + ". ")
            + "Sois concis et ultra-spécialisé. Appuie-toi sur le travail des agents précédents sans le répéter.";
        String prompt = previous.length() == 0
            ? runTask
            : "TÂCHE : " + runTask + "\n\nTRAVAIL DES AGENTS PRÉCÉDENTS :\n" + previous;

        ApiClient.sendRequest(KruxModel.KRUX_33_70B, systemPrompt, prompt, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(final String responseText, final String modelBrand) {
                runOnUiThread(() -> {
                    if (token != runToken || isFinishing()) return;
                    results.set(index, responseText);
                    states.set(index, "ok");
                    previous.append("[").append(agent.getName()).append("] ").append(responseText).append("\n\n");
                    if (previous.length() > 6000) previous.delete(0, previous.length() - 6000);
                    refreshResults();
                    step(token, index + 1, previous);
                });
            }

            @Override
            public void onError(final String errorMessage) {
                runOnUiThread(() -> {
                    if (token != runToken || isFinishing()) return;
                    results.set(index, "Erreur : " + errorMessage);
                    states.set(index, "err");
                    refreshResults();
                    step(token, index + 1, previous);
                });
            }
        });
    }

    private void endRun(String message) {
        running = false;
        btnRun.setEnabled(true);
        btnStop.setVisibility(View.GONE);
        progress.setVisibility(View.GONE);
        tvStatus.setText(message);
        refreshSteps();
        refreshResults();
    }

    private void refreshSteps() {
        listSteps.removeAllViews();
        for (int i = 0; i < runTeam.size(); i++) {
            String st = states.get(i);
            String mark = st.equals("ok") ? "✓" : st.equals("err") ? "✕" : st.equals("run") ? "…" : st.equals("stop") ? "■" : "•";
            String txt = st.equals("ok") ? "Terminé" : st.equals("err") ? "Erreur" : st.equals("run") ? "Au travail" : st.equals("stop") ? "Arrêté" : "En attente";
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            row.setBackground(box(KruxTheme.get(this, "input"), 14));
            row.setLayoutParams(cardParams());
            TextView m = label(mark, 18, true, st.equals("run") ? "accent" : "text");
            m.setLayoutParams(new LinearLayout.LayoutParams(dp(28), LinearLayout.LayoutParams.WRAP_CONTENT));
            row.addView(m);
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            col.addView(label(runTeam.get(i).getName(), 15, true, "text"));
            col.addView(label(txt, 13, false, st.equals("run") ? "accent" : "text2"));
            row.addView(col);
            listSteps.addView(row);
        }
    }

    private void refreshResults() {
        listResults.removeAllViews();
        boolean any = false;
        for (int i = 0; i < runTeam.size(); i++) {
            final String text = results.get(i);
            if (text.isEmpty()) continue;
            any = true;
            final StudioAgent a = runTeam.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            card.setBackground(box(KruxTheme.get(this, "input"), 14));
            card.setLayoutParams(cardParams());
            card.addView(label((i + 1) + ". " + a.getName() + " · " + a.getRole(), 14, true, "accent"));
            TextView body = label(text, 14, false, "text");
            body.setTextIsSelectable(true);
            body.setPadding(0, dp(8), 0, dp(8));
            card.addView(body);
            TextView copy = label("Copier", 13, true, "text2");
            copy.setPadding(0, dp(4), dp(8), dp(4));
            copy.setOnClickListener(v -> copy(text, "Copié"));
            card.addView(copy);
            listResults.addView(card);
        }
        tvResultsEmpty.setVisibility(any ? View.GONE : View.VISIBLE);
        rowResultActions.setVisibility(any ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------------ copier, partager

    private String buildReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(runName.isEmpty() ? "Studio Krux" : runName).append("\n");
        if (!runObjective.isEmpty()) sb.append("Objectif : ").append(runObjective).append("\n");
        sb.append("Tâche : ").append(runTask).append("\n\n");
        for (int i = 0; i < runTeam.size(); i++) {
            if (results.get(i).isEmpty()) continue;
            sb.append("## ").append(runTeam.get(i).getName()).append(" (").append(runTeam.get(i).getRole()).append(")\n")
              .append(results.get(i)).append("\n\n");
        }
        return sb.toString().trim();
    }

    private void copy(String text, String toast) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Studio Krux", text));
        Toast.makeText(this, toast, Toast.LENGTH_SHORT).show();
    }

    private void share(String text) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(i, "Partager les résultats"));
    }

    // ------------------------------------------------------------------ sauvegarde de l'équipe

    private void save() {
        try {
            JSONArray arr = new JSONArray();
            for (StudioAgent a : agents) arr.put(new JSONObject().put("n", a.getName()).put("r", a.getRole()));
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("agents", arr.toString())
                .putString("name", etName.getText().toString())
                .putString("objective", etObjective.getText().toString())
                .apply();
        } catch (Exception ignored) { }
    }

    private void load() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        etName.setText(p.getString("name", ""));
        etObjective.setText(p.getString("objective", ""));
        try {
            JSONArray arr = new JSONArray(p.getString("agents", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                agents.add(new StudioAgent(o.optString("n"), o.optString("r"), "", KruxModel.KRUX_33_70B));
            }
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------ petits outils d'interface

    private TextView label(String text, int sp, boolean bold, String colorKey) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(KruxTheme.get(this, colorKey));
        return t;
    }

    private TextView iconButton(String glyph, View.OnClickListener l) {
        TextView t = label(glyph, 16, true, "text2");
        t.setPadding(dp(10), dp(8), dp(10), dp(8));
        t.setOnClickListener(l);
        return t;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        return lp;
    }

    private GradientDrawable box(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
