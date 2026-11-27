package com.virtixstudio.kruxai.ui;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.virtixstudio.kruxai.api.ApiClient;
import com.virtixstudio.kruxai.core.StudioStore;
import com.virtixstudio.kruxai.core.StudioStore.Msg;
import com.virtixstudio.kruxai.core.StudioStore.Studio;
import com.virtixstudio.kruxai.models.KruxModel;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Studio : des équipes d'agents dans une vraie conversation.
 * Trois écrans : liste des studios, formulaires de création (3 étapes), chat du studio.
 * Pour les studios « Application » et « Musique », le Développeur écrit une app HTML qui est
 * testée pour de bon (WebView caché, boutons touchés, erreurs relevées) puis corrigée si besoin.
 */
public class StudioActivity extends AppCompatActivity {

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private static final class Kind {
        final String id, label, desc, hint;
        final boolean real;
        final String[][] team;
        Kind(String id, String label, String desc, String hint, boolean real, String[][] team) {
            this.id = id; this.label = label; this.desc = desc; this.hint = hint; this.real = real; this.team = team;
        }
    }

    private static final Kind[] KINDS = {
        new Kind("app", "Application", "Des agents créent ton app et la testent en direct.",
            "Décris l'app à créer, par exemple : un minuteur de cuisine avec trois présélections.", true, new String[][]{
                {"Designer", "Imagine l'écran, les couleurs et le parcours"},
                {"Développeur", "Écrit l'app complète en HTML, CSS et JavaScript"},
                {"Testeur", "Teste l'app en direct et relève les erreurs"}}),
        new Kind("musique", "Musique", "Compose, puis joue et teste un instrument ou un séquenceur.",
            "Décris la musique ou l'instrument, par exemple : un séquenceur de batterie à 8 pas.", true, new String[][]{
                {"Compositeur", "Choisit le tempo, la gamme et les motifs"},
                {"Développeur", "Crée l'instrument ou le séquenceur jouable avec Web Audio"},
                {"Testeur", "Teste les boutons et le son en direct"}}),
        new Kind("redaction", "Rédaction", "Un premier jet, une relecture, une version finale.",
            "Dis ce qu'il faut écrire, par exemple : une annonce de lancement en 5 lignes.", false, new String[][]{
                {"Rédacteur", "Écrit un premier jet clair"},
                {"Relecteur", "Corrige le style, la clarté et les fautes"},
                {"Éditeur", "Produit la version finale"}}),
        new Kind("recherche", "Recherche", "Des faits, une analyse, une synthèse courte.",
            "Pose ta question, par exemple : compare trois façons de stocker des photos.", false, new String[][]{
                {"Chercheur", "Rassemble les faits et les pistes"},
                {"Analyste", "Compare et dégage les points clés"},
                {"Synthèse", "Résume en peu de mots"}}),
        new Kind("libre", "Libre", "Tu composes toi-même l'équipe.",
            "Dis à ton équipe ce que tu veux.", false, new String[][]{})
    };

    private List<Studio> studios;
    private Studio cur;
    private FrameLayout root;
    private int screen;

    private String cKind = "app", cName = "", cGoal = "";
    private int cStep = 1;
    private final List<String[]> cAgents = new ArrayList<>();

    private LinearLayout msgBox, pills;
    private ScrollView msgScroll;
    private EditText input;
    private TextView btnSend, typing, emptyView;
    private boolean running;
    private int token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = new FrameLayout(this);
        root.setFitsSystemWindows(true);
        root.setBackgroundColor(KruxTheme.get(this, "bg"));
        setContentView(root);
        studios = StudioStore.load(this);
        showList();
    }

    @Override
    public void onBackPressed() {
        if (screen == 2) { stopRun(); showList(); }
        else if (screen == 1 && cStep > 1) { cStep--; renderCreate(); }
        else if (screen == 1) showList();
        else super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        StudioStore.save(this, studios);
    }

    // ------------------------------------------------------------------ liste des studios

    private void showList() {
        screen = 0;
        LinearLayout col = vbox();
        col.addView(header("Studios", "Tes équipes d'agents", v -> finish(), null));
        ScrollView sv = new ScrollView(this);
        LinearLayout body = vbox();
        body.setPadding(dp(16), dp(8), dp(16), dp(24));
        sv.addView(body);
        col.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        TextView create = pillButton("Créer un studio", true);
        create.setOnClickListener(v -> showCreate());
        body.addView(create, margin(0, 0, 0, 14));

        if (studios.isEmpty()) {
            body.addView(label("Crée ton premier studio", 18, true, "text"));
            TextView t = label("Une équipe d'agents qui travaille, teste et corrige pour toi, dans une conversation.", 14, false, "text2");
            t.setPadding(0, dp(6), 0, 0);
            body.addView(t);
        }
        for (final Studio s : studios) {
            LinearLayout card = vbox();
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            card.setBackground(box(KruxTheme.get(this, "input"), 14));
            card.addView(label(s.name, 16, true, "text"));
            card.addView(label(kindOf(s.kind).label + " · " + s.agents.size() + " agent(s)", 12, false, "accent"));
            String last = s.msgs.isEmpty() ? "Aucun message" : s.msgs.get(s.msgs.size() - 1).text;
            TextView p = label(last, 13, false, "text2");
            p.setMaxLines(2);
            p.setEllipsize(TextUtils.TruncateAt.END);
            p.setPadding(0, dp(4), 0, 0);
            card.addView(p);
            card.setOnClickListener(v -> showChat(s));
            body.addView(card, margin(0, 0, 0, 8));
        }
        setScreen(col);
    }

    // ------------------------------------------------------------------ formulaires de création

    private void showCreate() {
        screen = 1;
        cStep = 1;
        cKind = "app";
        cName = "";
        cGoal = "";
        applyKindTeam();
        renderCreate();
    }

    private void applyKindTeam() {
        cAgents.clear();
        for (String[] a : kindOf(cKind).team) cAgents.add(new String[]{a[0], a[1]});
    }

    private void renderCreate() {
        LinearLayout col = vbox();
        col.addView(header("Nouveau studio", "Étape " + cStep + " sur 3", v -> onBackPressed(), null));
        ScrollView sv = new ScrollView(this);
        LinearLayout body = vbox();
        body.setPadding(dp(16), dp(8), dp(16), dp(24));
        sv.addView(body);
        col.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        if (cStep == 1) {
            body.addView(label("Quel genre de studio ?", 18, true, "text"), margin(0, 0, 0, 10));
            for (final Kind k : KINDS) {
                LinearLayout card = vbox();
                card.setPadding(dp(14), dp(12), dp(14), dp(12));
                card.setBackground(box(KruxTheme.get(this, "input"), 14));
                card.addView(label(k.label, 16, true, "text"));
                card.addView(label(k.desc, 13, false, "text2"));
                card.setOnClickListener(v -> { cKind = k.id; applyKindTeam(); cStep = 2; renderCreate(); });
                body.addView(card, margin(0, 0, 0, 8));
            }
        } else if (cStep == 2) {
            body.addView(label("Donne-lui un nom", 18, true, "text"), margin(0, 0, 0, 10));
            final EditText name = field("Nom du studio", cName, false);
            final EditText goal = field("Ton projet en une phrase (facultatif)", cGoal, true);
            body.addView(name, margin(0, 0, 0, 8));
            body.addView(goal, margin(0, 0, 0, 8));
            body.addView(label("Si tu décris ton projet, l'équipe commence à travailler dès l'ouverture du studio.", 13, false, "text2"), margin(0, 0, 0, 14));
            TextView next = pillButton("Suivant", true);
            next.setOnClickListener(v -> {
                cName = name.getText().toString().trim();
                cGoal = goal.getText().toString().trim();
                cStep = 3;
                renderCreate();
            });
            body.addView(next);
        } else {
            body.addView(label("Ton équipe", 18, true, "text"), margin(0, 0, 0, 4));
            if (kindOf(cKind).real) {
                body.addView(label("Le Développeur écrit l'app, puis elle est testée pour de vrai et corrigée si besoin.", 13, false, "text2"), margin(0, 0, 0, 10));
            }
            if (cAgents.isEmpty()) {
                body.addView(label("Aucun agent pour l'instant. Ajoute le premier ci-dessous.", 14, false, "text2"), margin(0, 0, 0, 8));
            }
            for (int i = 0; i < cAgents.size(); i++) {
                final int idx = i;
                String[] a = cAgents.get(i);
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(14), dp(10), dp(8), dp(10));
                row.setBackground(box(KruxTheme.get(this, "input"), 14));
                LinearLayout c2 = vbox();
                c2.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP, 1f));
                c2.addView(label(a[0], 15, true, "text"));
                c2.addView(label(a[1], 13, false, "text2"));
                row.addView(c2);
                TextView x = label("✕", 16, true, "text2");
                x.setPadding(dp(12), dp(8), dp(12), dp(8));
                x.setOnClickListener(v -> { cAgents.remove(idx); renderCreate(); });
                row.addView(x);
                body.addView(row, margin(0, 0, 0, 6));
            }
            body.addView(label("Ajouter un agent", 13, true, "text2"), margin(0, 10, 0, 6));
            final EditText an = field("Nom (ex. Producteur)", "", false);
            final EditText ar = field("Ce qu'il fait", "", false);
            body.addView(an, margin(0, 0, 0, 6));
            body.addView(ar, margin(0, 0, 0, 6));
            TextView add = pillButton("Ajouter l'agent", false);
            add.setOnClickListener(v -> {
                String n = an.getText().toString().trim();
                String r = ar.getText().toString().trim();
                if (n.isEmpty() || r.isEmpty()) {
                    Toast.makeText(this, "Renseigne le nom et ce que fait l'agent.", Toast.LENGTH_SHORT).show();
                    return;
                }
                cAgents.add(new String[]{n, r});
                renderCreate();
            });
            body.addView(add, margin(0, 0, 0, 16));
            TextView done = pillButton("Créer le studio", true);
            done.setOnClickListener(v -> createStudio());
            body.addView(done);
        }
        setScreen(col);
    }

    private void createStudio() {
        if (cAgents.isEmpty()) {
            Toast.makeText(this, "Ajoute au moins un agent.", Toast.LENGTH_SHORT).show();
            return;
        }
        Studio s = new Studio();
        s.id = String.valueOf(System.currentTimeMillis());
        s.kind = cKind;
        s.name = cName.isEmpty() ? "Studio " + kindOf(cKind).label.toLowerCase() : cName;
        s.goal = cGoal;
        for (String[] a : cAgents) s.agents.add(new String[]{a[0], a[1]});
        studios.add(0, s);
        StudioStore.save(this, studios);
        showChat(s);
        if (!s.goal.isEmpty()) send(s.goal);
    }

    // ------------------------------------------------------------------ chat

    private void showChat(Studio st) {
        screen = 2;
        cur = st;
        running = false;
        token++;
        typing = null;
        LinearLayout col = vbox();
        col.addView(header(st.name, kindOf(st.kind).label, v -> { stopRun(); showList(); }, this::chatMenu));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        pills = new LinearLayout(this);
        pills.setOrientation(LinearLayout.HORIZONTAL);
        pills.setPadding(dp(12), dp(4), dp(12), dp(8));
        hs.addView(pills);
        col.addView(hs);
        refreshPills(null);

        msgScroll = new ScrollView(this);
        msgBox = vbox();
        msgBox.setPadding(dp(12), dp(4), dp(12), dp(12));
        msgScroll.addView(msgBox);
        col.addView(msgScroll, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), dp(6), dp(10), dp(10));
        input = field(kindOf(st.kind).hint, "", true);
        input.setMaxLines(4);
        bar.addView(input, new LinearLayout.LayoutParams(0, WRAP, 1f));
        btnSend = pillButton("Envoyer", true);
        btnSend.setOnClickListener(v -> {
            if (running) { stopRun(); return; }
            String t = input.getText().toString().trim();
            if (t.isEmpty()) return;
            input.setText("");
            send(t);
        });
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(WRAP, WRAP);
        bl.leftMargin = dp(8);
        bar.addView(btnSend, bl);
        col.addView(bar);
        setScreen(col);

        if (st.msgs.isEmpty()) {
            emptyView = label("Dis à ton équipe ce que tu veux. " + kindOf(st.kind).hint, 14, false, "text2");
            emptyView.setPadding(dp(4), dp(12), dp(4), 0);
            msgBox.addView(emptyView);
        } else {
            for (Msg m : st.msgs) addBubble(m);
        }
    }

    private void chatMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, "Voir l'équipe");
        pm.getMenu().add(0, 2, 1, "Effacer la conversation");
        pm.getMenu().add(0, 3, 2, "Supprimer le studio");
        pm.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                StringBuilder sb = new StringBuilder();
                for (String[] a : cur.agents) sb.append(a[0]).append(" : ").append(a[1]).append("\n\n");
                new AlertDialog.Builder(this).setTitle("Équipe").setMessage(sb.toString().trim())
                    .setPositiveButton("Fermer", null).show();
            } else if (item.getItemId() == 2) {
                stopRun();
                cur.msgs.clear();
                cur.lastHtml = "";
                StudioStore.save(this, studios);
                showChat(cur);
            } else {
                new AlertDialog.Builder(this).setTitle("Supprimer ce studio ?")
                    .setMessage("La conversation et l'équipe seront effacées.")
                    .setNegativeButton("Annuler", null)
                    .setPositiveButton("Supprimer", (d, w) -> {
                        stopRun();
                        studios.remove(cur);
                        StudioStore.save(this, studios);
                        showList();
                    }).show();
            }
            return true;
        });
        pm.show();
    }

    private void refreshPills(String active) {
        pills.removeAllViews();
        for (String[] a : cur.agents) {
            boolean on = a[0].equals(active);
            TextView p = label(a[0], 13, true, on ? "buttontext" : "text2");
            p.setPadding(dp(14), dp(7), dp(14), dp(7));
            p.setBackground(box(KruxTheme.get(this, on ? "button" : "input"), 20));
            pills.addView(p, margin(0, 0, 6, 0));
        }
    }

    private void setSendLabel() {
        btnSend.setText(running ? "Stop" : "Envoyer");
    }

    // ------------------------------------------------------------------ messages

    private void addBubble(Msg m) {
        if (emptyView != null) { msgBox.removeView(emptyView); emptyView = null; }
        boolean user = m.who.equals("user");
        LinearLayout wrap = vbox();
        wrap.setGravity(user ? Gravity.END : Gravity.START);
        int maxW = getResources().getDisplayMetrics().widthPixels * 85 / 100;

        if (!user) {
            boolean isTest = !m.test.isEmpty();
            TextView who = label(isTest ? "Test réel" : m.who, 12, true, "accent");
            if (isTest) who.setTextColor(Color.parseColor(m.ok ? "#34D399" : "#F87171"));
            who.setPadding(dp(4), dp(8), 0, dp(2));
            wrap.addView(who);
        }
        TextView b = label(m.test.isEmpty() ? m.text : m.test, 15, false, "text");
        b.setTextIsSelectable(true);
        b.setMaxWidth(maxW);
        b.setPadding(dp(14), dp(10), dp(14), dp(10));
        b.setBackground(box(KruxTheme.get(this, user ? "bubble_user" : "bubble_ai"), 16));
        wrap.addView(b);

        if (!m.html.isEmpty()) {
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            final String code = m.html;
            TextView open = pillButton("Ouvrir l'app", true);
            open.setOnClickListener(v -> openApp(code));
            TextView share = pillButton("Partager le code", false);
            share.setOnClickListener(v -> {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, code);
                startActivity(Intent.createChooser(i, "Partager le code"));
            });
            actions.addView(open, margin(0, 6, 8, 0));
            actions.addView(share, margin(0, 6, 0, 0));
            wrap.addView(actions);
        }
        msgBox.addView(wrap, new LinearLayout.LayoutParams(MATCH, WRAP));
        scrollDown();
    }

    private void showTyping(String text) {
        clearTyping();
        typing = label(text, 13, false, "text2");
        typing.setPadding(dp(4), dp(10), 0, 0);
        msgBox.addView(typing);
        scrollDown();
    }

    private void clearTyping() {
        if (typing != null) { msgBox.removeView(typing); typing = null; }
    }

    private void scrollDown() {
        msgScroll.post(() -> msgScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void post(Msg m) {
        cur.msgs.add(m);
        StudioStore.save(this, studios);
        addBubble(m);
    }

    // ------------------------------------------------------------------ travail de l'équipe

    private static boolean isBuilder(String name) {
        return name.toLowerCase().contains("dévelop") || name.toLowerCase().contains("develop");
    }

    private static boolean isTester(String name) {
        return name.toLowerCase().contains("test");
    }

    private void send(String text) {
        Msg u = new Msg("user", text);
        post(u);
        final Kind kind = kindOf(cur.kind);
        boolean hasBuilder = false;
        for (String[] a : cur.agents) if (isBuilder(a[0])) hasBuilder = true;
        final boolean realMode = kind.real && hasBuilder;
        final boolean iterate = realMode && !cur.lastHtml.isEmpty();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < cur.agents.size(); i++) {
            String n = cur.agents.get(i)[0];
            if (realMode && isTester(n)) continue;
            if (iterate && !isBuilder(n)) continue;
            order.add(i);
        }
        running = true;
        setSendLabel();
        token++;
        step(token, order, 0, text, new StringBuilder(), realMode, iterate, 0, null);
    }

    private void step(final int tk, final List<Integer> order, final int pos, final String request,
                      final StringBuilder previous, final boolean realMode, final boolean iterate,
                      final int fixes, final String fixErrors) {
        if (tk != token || isFinishing()) return;
        if (pos >= order.size()) { endRun(); return; }
        final String[] agent = cur.agents.get(order.get(pos));
        final boolean builder = realMode && isBuilder(agent[0]);
        refreshPills(agent[0]);
        showTyping(agent[0] + (builder ? " écrit l'app…" : " travaille…"));

        StringBuilder sys = new StringBuilder("Tu es ").append(agent[0]).append(", membre du studio « ").append(cur.name)
            .append(" » (").append(kindOf(cur.kind).label).append("). Ton rôle : ").append(agent[1]).append(". ");
        if (!cur.goal.isEmpty()) sys.append("Projet : ").append(cur.goal).append(". ");
        sys.append("Réponds en français, de façon courte et concrète. Appuie-toi sur le travail de tes collègues sans le répéter. ");
        if (builder) {
            sys.append("Tu écris UNE application complète dans un seul fichier index.html (HTML, CSS et JavaScript dans le même fichier, "
                + "aucune bibliothèque ni requête réseau), pensée pour un écran de téléphone. "
                + "Réponds par une phrase courte, puis UN SEUL bloc de code ```html. ");
            if (cur.kind.equals("musique")) {
                sys.append("L'app produit du vrai son avec l'API Web Audio : crée l'AudioContext au premier toucher, "
                    + "mets des boutons bien visibles et n'utilise aucun fichier audio externe. ");
            }
        }
        String prompt;
        if (builder && fixErrors != null) {
            prompt = "Le test réel de l'app a relevé ces problèmes :\n" + fixErrors
                + "\n\nCorrige-les sans rien changer d'autre. Code actuel :\n" + cut(cur.lastHtml, 14000);
        } else if (builder && iterate) {
            prompt = "DEMANDE : " + request + "\n\nCode actuel de l'app, à modifier selon la demande :\n" + cut(cur.lastHtml, 14000);
        } else {
            prompt = previous.length() == 0 ? request : "DEMANDE : " + request + "\n\nTRAVAIL DES COLLÈGUES :\n" + previous;
        }

        ApiClient.sendRequest(KruxModel.KRUX_33_70B, sys.toString(), prompt, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(final String responseText, final String modelBrand) {
                runOnUiThread(() -> {
                    if (tk != token || isFinishing()) return;
                    clearTyping();
                    String html = builder ? extractHtml(responseText) : null;
                    String shown = builder ? responseText.replaceAll("(?s)```html.*?```", "").replaceAll("(?s)```html.*$", "").trim() : responseText;
                    if (builder && shown.isEmpty()) shown = html != null ? "Voilà l'app." : responseText;
                    Msg m = new Msg(agent[0], shown);
                    if (html != null) { m.html = html; cur.lastHtml = html; }
                    post(m);
                    if (!builder) {
                        previous.append("[").append(agent[0]).append("] ").append(shown).append("\n\n");
                        if (previous.length() > 6000) previous.delete(0, previous.length() - 6000);
                        step(tk, order, pos + 1, request, previous, realMode, iterate, 0, null);
                    } else if (html == null) {
                        post(new Msg(agent[0], "Je n'ai pas reçu de code, je ne peux pas tester."));
                        step(tk, order, pos + 1, request, previous, realMode, iterate, 0, null);
                    } else {
                        testBuild(tk, order, pos, request, previous, realMode, iterate, fixes, html);
                    }
                });
            }

            @Override
            public void onError(final String errorMessage) {
                runOnUiThread(() -> {
                    if (tk != token || isFinishing()) return;
                    clearTyping();
                    post(new Msg(agent[0], "Erreur : " + errorMessage));
                    step(tk, order, pos + 1, request, previous, realMode, iterate, 0, null);
                });
            }
        });
    }

    private void testBuild(final int tk, final List<Integer> order, final int pos, final String request,
                           final StringBuilder previous, final boolean realMode, final boolean iterate,
                           final int fixes, String html) {
        String found = "Testeur";
        for (String[] a : cur.agents) if (isTester(a[0])) found = a[0];
        final String tester = found;
        refreshPills(tester);
        showTyping(tester + " teste l'app en direct…");
        StudioTester.run(this, html, r -> {
            if (tk != token || isFinishing()) return;
            clearTyping();
            Msg t = new Msg(tester, r.text);
            t.test = r.text;
            t.ok = r.ok;
            post(t);
            if (!r.ok && fixes < 2) {
                post(new Msg("Studio", "Correction automatique " + (fixes + 1) + " sur 2…"));
                StringBuilder errs = new StringBuilder();
                for (String e : r.errors) errs.append("- ").append(e).append("\n");
                step(tk, order, pos, request, previous, realMode, iterate, fixes + 1, errs.toString());
            } else {
                step(tk, order, pos + 1, request, previous, realMode, iterate, 0, null);
            }
        });
    }

    private void stopRun() {
        if (!running) return;
        token++;
        endRun();
    }

    private void endRun() {
        running = false;
        clearTyping();
        if (btnSend != null) setSendLabel();
        if (pills != null && cur != null) refreshPills(null);
        StudioStore.save(this, studios);
    }

    private static String extractHtml(String reply) {
        Matcher m = Pattern.compile("(?s)```html\\s*(.*?)```").matcher(reply);
        if (m.find()) return m.group(1).trim();
        Matcher open = Pattern.compile("(?s)```html\\s*(.*)$").matcher(reply);
        if (open.find() && open.group(1).toLowerCase().contains("</html>")) return open.group(1).trim();
        int i = reply.toLowerCase().indexOf("<!doctype html");
        if (i < 0) i = reply.toLowerCase().indexOf("<html");
        if (i >= 0 && reply.toLowerCase().contains("</html>")) return reply.substring(i).trim();
        return null;
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    // ------------------------------------------------------------------ ouvrir l'app générée

    private void openApp(String html) {
        final Dialog dlg = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout box = vbox();
        box.setBackgroundColor(KruxTheme.get(this, "bg"));
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(8), dp(12), dp(8));
        TextView title = label("Aperçu en direct", 15, true, "text");
        title.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP, 1f));
        bar.addView(title);
        TextView close = pillButton("Fermer", false);
        close.setOnClickListener(v -> dlg.dismiss());
        bar.addView(close);
        box.addView(bar);
        final WebView web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        web.loadDataWithBaseURL("https://krux.local/", html, "text/html", "utf-8", null);
        box.addView(web, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        dlg.setContentView(box);
        dlg.setOnDismissListener(d -> web.destroy());
        dlg.show();
    }

    // ------------------------------------------------------------------ outils d'interface

    private Kind kindOf(String id) {
        for (Kind k : KINDS) if (k.id.equals(id)) return k;
        return KINDS[KINDS.length - 1];
    }

    private void setScreen(View v) {
        root.removeAllViews();
        root.addView(v, new FrameLayout.LayoutParams(MATCH, MATCH));
    }

    private LinearLayout vbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout header(String title, String sub, View.OnClickListener back, View.OnClickListener menu) {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(dp(8), dp(10), dp(8), dp(6));
        TextView b = label("←", 24, false, "text");
        b.setPadding(dp(12), dp(6), dp(12), dp(6));
        b.setOnClickListener(back);
        h.addView(b);
        LinearLayout c = vbox();
        c.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView t = label(title, 18, true, "text");
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(t);
        if (sub != null) c.addView(label(sub, 12, false, "text2"));
        h.addView(c);
        if (menu != null) {
            final TextView m = label("⋮", 22, true, "text");
            m.setPadding(dp(14), dp(6), dp(14), dp(6));
            m.setOnClickListener(v -> menu.onClick(m));
            h.addView(m);
        }
        return h;
    }

    private TextView label(String text, int sp, boolean bold, String colorKey) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(KruxTheme.get(this, colorKey));
        return t;
    }

    private TextView pillButton(String text, boolean filled) {
        TextView t = label(text, 14, true, filled ? "buttontext" : "text");
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(18), dp(12), dp(18), dp(12));
        t.setBackground(box(KruxTheme.get(this, filled ? "button" : "input"), 12));
        return t;
    }

    private EditText field(String hint, String value, boolean multi) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setTextSize(15);
        e.setTextColor(KruxTheme.get(this, "text"));
        e.setHintTextColor(KruxTheme.get(this, "hint"));
        e.setBackground(box(KruxTheme.get(this, "input"), 12));
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        if (multi) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        } else {
            e.setSingleLine(true);
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        }
        return e;
    }

    private LinearLayout.LayoutParams margin(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(dp(l), dp(t), dp(r), dp(b));
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
