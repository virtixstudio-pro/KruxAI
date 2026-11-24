package com.virtixstudio.kruxai.ui;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.virtixstudio.kruxai.api.ApiClient;
import com.virtixstudio.kruxai.core.WorkspaceStore;
import com.virtixstudio.kruxai.models.KruxModel;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Krux Code : un écran réservé au code. On ouvre un fichier de l'onglet Fichiers (ou on en crée un),
 * on décrit la modification, Krux répond seulement par du code. Les changements s'affichent comme un diff
 * (rouge = retiré, vert = ajouté) et ne sont écrits qu'après « Appliquer ». « Annuler » revient en arrière.
 */
public class KruxCodeActivity extends AppCompatActivity {

    public static final String EXTRA_FILE = "file";

    private static final int MAX_CHARS = 40000;
    private static final int MAX_DIFF_LINES = 140;

    private static final int BG = 0xFF0B0A12;
    private static final int PANEL = 0xFF16131F;
    private static final int TXT = 0xFFE8E4F2;
    private static final int DIM = 0xFF8B849C;
    private static final int GREEN = 0xFF3DDC84;
    private static final int RED = 0xFFFF6B6B;

    private static final String SYSTEM =
            "Tu es Krux Code, un outil de modification de code. Tu ne bavardes pas. "
          + "Pour un fichier EXISTANT, tu ne renvoies QUE les modifications, sous forme de blocs, sans réécrire le reste : une ligne <<<<<<< CHERCHER, puis les lignes exactes à remplacer copiées du fichier (juste assez pour être uniques, jamais tout le fichier), puis une ligne =======, puis les nouvelles lignes, puis une ligne >>>>>>> REMPLACER. Un bloc par endroit modifié. Pour insérer, recopie une ligne voisine dans CHERCHER et remets-la dans REMPLACER avec les lignes ajoutées. "
          + "Pour un NOUVEAU FICHIER, tu réponds par le fichier COMPLET dans un seul bloc de code entre ``` et ```. "
          + "Aucune phrase avant ni après le bloc. Ne change que ce qui est demandé : garde le style, l'indentation, les noms et les commentaires existants. "
          + "Ne raccourcis jamais le fichier, n'écris jamais « le reste est inchangé ». "
          + "Si la demande est impossible ou trop floue, réponds seulement par une ligne qui commence par IMPOSSIBLE: et la raison en une phrase. "
          + "Si on te demande un NOUVEAU FICHIER, écris d'abord une ligne FICHIER: nom.extension, puis le bloc de code. "
          + "Tu peux aussi consulter le web : écris SEULEMENT une ligne OUVRIR: https://adresse (la page s'ouvre et tu en reçois le texte) "
          + "ou CLIQUER: texte du lien ou du bouton, ou TAPER: nom du champ | texte à écrire (jamais de mot de passe), ou DEFILER: bas (ou haut). "
          + "Une seule action par réponse, sans autre texte, et seulement si la demande en a besoin "
          + "(documentation, exemple, contenu d'un site, remplir un formulaire). Avance pas à pas comme une personne, une action à la fois. Ensuite réponds comme d'habitude.";

    private WorkspaceStore store;
    private com.virtixstudio.kruxai.core.ProjectStore projects;
    private File current;
    private String currentText = "";
    private String pendingText;
    private String pendingName;
    private String undoText;
    private File undoFile;
    private boolean busy;
    private SharedPreferences sp;
    private static final int PAGE_CHARS = 2000;   // texte d'une page envoyé à l'IA (quotas gratuits)
    private static final int OBS_CHARS = 3500;    // total des pages gardées pour une demande
    private TextView webTitle, webToggle;
    private android.widget.HorizontalScrollView tabsScroll;
    private LinearLayout tabsRow;
    private boolean webMin = true;
    private String pendingWebUrl = "";
    private String lastNoted = "";
    private JSONArray sessions = new JSONArray();
    private String sessId = "";
    private boolean replaying;
    private WebView web;
    private LinearLayout webBox;
    private Runnable pageWait;
    private TextView sendBtn;
    private TextView currentStatus;
    private int runId;

    private LinearLayout log;
    private ScrollView scroll;
    private EditText input;
    private TextView fileLabel;
    private View actionsRow;
    private float d;
    private int accent;

    @Override
    protected void onDestroy() {
        if (web != null) { try { web.destroy(); } catch (Exception ignored) { } }
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        d = getResources().getDisplayMetrics().density;
        accent = KruxTheme.get(this, "accent");
        store = new WorkspaceStore(new File(getFilesDir(), "workspace"));
        projects = new com.virtixstudio.kruxai.core.ProjectStore(new File(getFilesDir(), "projects"));
        store.adoptSubfolders();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(px(10), px(6), px(10), px(6));
        TextView close = pill("Fermer", TXT, PANEL);
        close.setOnClickListener(v -> finish());
        TextView title = new TextView(this);
        title.setText("Krux Code");
        title.setTextColor(TXT);
        title.setTextSize(16);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        final TextView pick = pill("Fichier ▾", BG, accent);
        pick.setOnClickListener(v -> fileMenu(v));
        title.setText("Krux Code ▾");
        title.setOnClickListener(v -> sessionMenu(v));
        final TextView plus = pill("＋", BG, accent);
        plus.setOnClickListener(v -> newSession(true));
        bar.addView(close);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-2, -2);
        pl.rightMargin = px(6);
        bar.addView(plus, pl);
        bar.addView(pick);
        root.addView(bar, new LinearLayout.LayoutParams(-1, px(56)));

        fileLabel = new TextView(this);
        fileLabel.setTextColor(DIM);
        fileLabel.setTextSize(12);
        fileLabel.setTypeface(Typeface.MONOSPACE);
        fileLabel.setPadding(px(16), px(2), px(16), px(8));
        root.addView(fileLabel, new LinearLayout.LayoutParams(-1, -2));

        webBox = new LinearLayout(this);
        webBox.setOrientation(LinearLayout.VERTICAL);
        webBox.setVisibility(View.GONE);
        LinearLayout webHead = new LinearLayout(this);
        webHead.setGravity(Gravity.CENTER_VERTICAL);
        webTitle = new TextView(this);
        webTitle.setTextColor(DIM);
        webTitle.setTextSize(12);
        webTitle.setTypeface(Typeface.MONOSPACE);
        webTitle.setSingleLine(true);
        webTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        webTitle.setPadding(px(16), 0, px(8), 0);
        webToggle = pill("Agrandir", TXT, PANEL);
        webToggle.setTextSize(11);
        webToggle.setMinHeight(px(28));
        webToggle.setOnClickListener(v -> setWebMin(!webMin));
        TextView webClose = pill("✕", TXT, PANEL);
        webClose.setTextSize(11);
        webClose.setMinHeight(px(28));
        webClose.setOnClickListener(v -> closeWeb());
        webHead.addView(webTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, px(28));
        tp.rightMargin = px(6);
        webHead.addView(webToggle, tp);
        webHead.addView(webClose, new LinearLayout.LayoutParams(px(40), px(28)));
        webBox.addView(webHead, new LinearLayout.LayoutParams(-1, px(34)));
        tabsScroll = new android.widget.HorizontalScrollView(this);
        tabsScroll.setHorizontalScrollBarEnabled(false);
        tabsScroll.setVisibility(View.GONE);
        tabsRow = new LinearLayout(this);
        tabsRow.setPadding(px(12), 0, px(12), px(4));
        tabsScroll.addView(tabsRow);
        webBox.addView(tabsScroll, new LinearLayout.LayoutParams(-1, -2));
        web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) {
                rememberPage(url, v.getTitle());
                final Runnable r = pageWait;
                if (r != null) { pageWait = null; v.postDelayed(r, 900); }
            }
        });
        webBox.addView(web, new LinearLayout.LayoutParams(-1, px(64)));
        root.addView(webBox, new LinearLayout.LayoutParams(-1, -2));

        scroll = new ScrollView(this);
        log = new LinearLayout(this);
        log.setOrientation(LinearLayout.VERTICAL);
        log.setPadding(px(12), 0, px(12), px(12));
        scroll.addView(log, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout inputBar = new LinearLayout(this);
        inputBar.setGravity(Gravity.CENTER_VERTICAL);
        inputBar.setPadding(px(10), px(6), px(10), px(8));
        input = new EditText(this);
        input.setHint("Décris la modification…");
        input.setHintTextColor(DIM);
        input.setTextColor(TXT);
        input.setTextSize(14);
        input.setTypeface(Typeface.MONOSPACE);
        input.setMaxLines(5);
        input.setPadding(px(14), px(10), px(14), px(10));
        input.setBackground(round(PANEL, 20));
        TextView send = pill("▶", BG, accent);
        sendBtn = send;
        send.setOnClickListener(v -> { if (busy) stopAgent(); else submit(); });
        inputBar.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(px(52), px(46));
        sendLp.leftMargin = px(8);
        inputBar.addView(send, sendLp);
        root.addView(inputBar, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets s = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            v.setPadding(s.left, s.top, s.right, s.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        sp = getSharedPreferences("krux_code", MODE_PRIVATE);
        loadSessions();
        restoreSession();
        if (log.getChildCount() == 0) {
            replaying = true;
            line("Krux Code", accent);
            line("Ouvre un fichier avec « Fichier », ou décris ce que tu veux créer. Rien n'est écrit tant que tu n'as pas appuyé sur Appliquer.", DIM);
            replaying = false;
        }
        updateLabel();

        String wanted = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_FILE);
        if (wanted != null) openFile(store.resolve(wanted));
    }

    // ------------------------------------------------------------------ interface

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
        t.setTextSize(13);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(px(14), 0, px(14), 0);
        t.setBackground(round(fill, 14));
        t.setMinHeight(px(38));
        t.setClickable(true);
        return t;
    }

    private TextView line(String text, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(12);
        t.setTypeface(Typeface.MONOSPACE);
        t.setPadding(px(4), px(5), px(4), px(5));
        log.addView(t);
        scrollDown();
        record(text, color);
        return t;
    }

    private void scrollDown() {
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void updateLabel() {
        setSessFile(current == null ? "" : current.getName());
        if (current == null) {
            fileLabel.setText("nouveau fichier");
        } else {
            int lines = currentText.isEmpty() ? 0 : currentText.split("\n", -1).length;
            fileLabel.setText(current.getName() + "  ·  " + lines + " lignes");
        }
    }


    // ------------------------------------------------------------------ sessions (conservées entre deux ouvertures)

    private static String trimObs(String s) {
        return s.length() <= OBS_CHARS ? s : s.substring(s.length() - OBS_CHARS);
    }

    private static String hostOf(String url) {
        try {
            String h = android.net.Uri.parse(url).getHost();
            return h == null ? url : h.replaceFirst("^www\\.", "");
        } catch (Exception e) { return url; }
    }

    /** Navigateur réduit (petite fenêtre) ou agrandi ; l'état est gardé par session. */
    private void setWebMin(boolean min) {
        webMin = min;
        web.setLayoutParams(new LinearLayout.LayoutParams(-1, px(min ? 64 : 230)));
        webToggle.setText(min ? "Agrandir" : "Réduire");
        if (!min) loadPending();
        JSONObject o = curSession();
        if (o != null) {
            try { o.put("web_min", min); } catch (Exception ignored) { }
            saveSessions();
        }
    }

    private void loadPending() {
        if (pendingWebUrl.isEmpty()) return;
        String u = pendingWebUrl;
        pendingWebUrl = "";
        web.loadUrl(u);
    }

    private void closeWeb() {
        JSONObject o = curSession();
        if (o != null) { o.remove("tabs"); saveSessions(); }
        pendingWebUrl = "";
        lastNoted = "";
        try { web.stopLoading(); web.loadUrl("about:blank"); } catch (Exception ignored) { }
        webBox.setVisibility(View.GONE);
        refreshTabs();
    }

    /** Chaque page visitée s'ajoute à la session courante (6 au plus) et à son historique. */
    private void rememberPage(String url, String title) {
        if (url == null || url.isEmpty() || url.startsWith("about:")) return;
        JSONObject o = curSession();
        if (o == null) return;
        String ti = (title == null || title.trim().isEmpty()) ? hostOf(url) : title.trim();
        if (ti.length() > 40) ti = ti.substring(0, 40) + "…";
        try {
            JSONArray old = o.optJSONArray("tabs");
            JSONArray next = new JSONArray();
            for (int i = 0; old != null && i < old.length(); i++) {
                JSONObject t = old.optJSONObject(i);
                if (t != null && !url.equals(t.optString("url"))) next.put(t);
            }
            next.put(new JSONObject().put("url", url).put("title", ti));
            while (next.length() > 6) next.remove(0);
            o.put("tabs", next);
        } catch (Exception ignored) { }
        saveSessions();
        webTitle.setText("🌐 " + ti);
        refreshTabs();
        if (!url.equals(lastNoted)) {
            lastNoted = url;
            line("🌐 " + ti + " (" + hostOf(url) + ")", DIM);
        }
    }

    private void refreshTabs() {
        if (tabsRow == null) return;
        tabsRow.removeAllViews();
        JSONObject o = curSession();
        JSONArray tabs = o == null ? null : o.optJSONArray("tabs");
        if (tabs == null || tabs.length() < 2) { tabsScroll.setVisibility(View.GONE); return; }
        for (int i = 0; i < tabs.length(); i++) {
            JSONObject t = tabs.optJSONObject(i);
            if (t == null) continue;
            final String u = t.optString("url");
            TextView chip = pill(t.optString("title"), TXT, PANEL);
            chip.setTextSize(11);
            chip.setMinHeight(px(28));
            chip.setSingleLine(true);
            chip.setOnClickListener(v -> { pendingWebUrl = ""; web.loadUrl(u); });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, px(28));
            cp.rightMargin = px(6);
            tabsRow.addView(chip, cp);
        }
        tabsScroll.setVisibility(View.VISIBLE);
    }

    /** Au changement de session : le navigateur de cette session (réduit, page rechargée seulement si on l'agrandit). */
    private void showSessionWeb() {
        JSONObject o = curSession();
        JSONArray tabs = o == null ? null : o.optJSONArray("tabs");
        if (tabs == null || tabs.length() == 0) {
            pendingWebUrl = "";
            lastNoted = "";
            try { web.stopLoading(); web.loadUrl("about:blank"); } catch (Exception ignored) { }
            webBox.setVisibility(View.GONE);
            refreshTabs();
            return;
        }
        JSONObject last = tabs.optJSONObject(tabs.length() - 1);
        webBox.setVisibility(View.VISIBLE);
        webTitle.setText("🌐 " + (last == null ? "" : last.optString("title")));
        pendingWebUrl = last == null ? "" : last.optString("url");
        lastNoted = pendingWebUrl;
        setWebMin(o.optBoolean("web_min", true));
        refreshTabs();
    }

    private JSONObject curSession() {
        for (int i = 0; i < sessions.length(); i++) {
            JSONObject o = sessions.optJSONObject(i);
            if (o != null && sessId.equals(o.optString("id"))) return o;
        }
        return null;
    }

    private void saveSessions() {
        if (sp == null) return;
        sp.edit().putString("sessions", sessions.toString()).putString("cur", sessId).apply();
    }

    private void loadSessions() {
        try { sessions = new JSONArray(sp.getString("sessions", "[]")); } catch (Exception e) { sessions = new JSONArray(); }
        sessId = sp.getString("cur", "");
        if (curSession() == null) {
            if (sessions.length() > 0) sessId = sessions.optJSONObject(sessions.length() - 1).optString("id");
            else newSession(false);
        }
    }

    private void newSession(boolean show) {
        try {
            JSONObject o = new JSONObject();
            o.put("id", String.valueOf(System.currentTimeMillis()));
            o.put("title", "Session " + (sessions.length() + 1));
            o.put("file", "");
            o.put("lines", new JSONArray());
            sessions.put(o);
            sessId = o.optString("id");
        } catch (Exception ignored) { }
        saveSessions();
        if (show) switchTo(sessId);
    }

    private void switchTo(String id) {
        discardPending();
        sessId = id;
        saveSessions();
        log.removeAllViews();
        current = null;
        currentText = "";
        restoreSession();
        updateLabel();
    }

    /** Rejoue les lignes enregistrées de la session courante et rouvre son fichier. */
    private void restoreSession() {
        JSONObject o = curSession();
        if (o == null) return;
        replaying = true;
        JSONArray ls = o.optJSONArray("lines");
        for (int i = 0; ls != null && i < ls.length(); i++) {
            JSONArray one = ls.optJSONArray(i);
            if (one != null) line(one.optString(0), one.optInt(1, DIM));
        }
        if (ls == null || ls.length() == 0) {
            line("Krux Code", accent);
            line("Décris ce que tu veux créer, ou ouvre un fichier avec « Fichier ». Rien n'est écrit tant que tu n'as pas appuyé sur Appliquer.", DIM);
        }
        String fn = o.optString("file", "");
        if (!fn.isEmpty()) {
            try {
                File f = store.resolve(fn);
                if (f != null && f.exists()) {
                    currentText = store.readText(f);
                    current = f;
                }
            } catch (Exception ignored) { }
        }
        replaying = false;
        if (fileLabel != null) updateLabel();
        if (web != null && webBox != null) showSessionWeb();
    }

    private void record(String text, int color) {
        if (replaying || text.startsWith("· Krux travaille")) return;
        JSONObject o = curSession();
        if (o == null) return;
        try {
            JSONArray ls = o.optJSONArray("lines");
            if (ls == null) { ls = new JSONArray(); o.put("lines", ls); }
            ls.put(new JSONArray().put(text).put(color));
            while (ls.length() > 300) ls.remove(0);
            if (o.optString("title", "").startsWith("Session ") && text.startsWith("› ")) {
                String t = text.substring(2).trim();
                o.put("title", t.length() > 28 ? t.substring(0, 28) + "…" : t);
            }
        } catch (Exception ignored) { }
        saveSessions();
    }

    private void setSessFile(String name) {
        JSONObject o = curSession();
        if (o == null) return;
        try { o.put("file", name); } catch (Exception ignored) { }
        saveSessions();
    }

    /** Les dernières demandes de la session, pour que Krux garde le fil. */
    private String recentAsks() {
        JSONObject o = curSession();
        if (o == null) return "";
        JSONArray ls = o.optJSONArray("lines");
        ArrayList<String> asks = new ArrayList<>();
        for (int i = 0; ls != null && i < ls.length(); i++) {
            JSONArray one = ls.optJSONArray(i);
            if (one != null && one.optString(0).startsWith("› ")) asks.add(one.optString(0).substring(2));
        }
        if (asks.size() < 1) return "";
        StringBuilder sb = new StringBuilder("DEMANDES PRÉCÉDENTES DE CETTE SESSION :\n");
        for (int i = Math.max(0, asks.size() - 5); i < asks.size(); i++) sb.append("- ").append(asks.get(i)).append('\n');
        return sb.append('\n').toString();
    }

    private void sessionMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        for (int i = 0; i < sessions.length(); i++) {
            JSONObject o = sessions.optJSONObject(i);
            if (o == null) continue;
            pm.getMenu().add(0, i, i, (sessId.equals(o.optString("id")) ? "✓ " : "") + o.optString("title", "Session"));
        }
        pm.getMenu().add(0, 9000, 9000, "Supprimer cette session");
        pm.setOnMenuItemClickListener(it -> {
            int id = it.getItemId();
            if (id == 9000) {
                JSONArray keep = new JSONArray();
                for (int i = 0; i < sessions.length(); i++) {
                    JSONObject o = sessions.optJSONObject(i);
                    if (o != null && !sessId.equals(o.optString("id"))) keep.put(o);
                }
                sessions = keep;
                if (sessions.length() == 0) newSession(true);
                else switchTo(sessions.optJSONObject(sessions.length() - 1).optString("id"));
            } else if (id >= 0 && id < sessions.length()) {
                JSONObject o = sessions.optJSONObject(id);
                if (o != null) switchTo(o.optString("id"));
            }
            return true;
        });
        pm.show();
    }

    // ------------------------------------------------------------------ fichiers

    private void fileMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        final List<File> files = store.list();
        int n = Math.min(files.size(), 15);
        for (int i = 0; i < n; i++) pm.getMenu().add(0, i, i, files.get(i).getName());
        pm.getMenu().add(0, 100, 100, "Nouveau fichier");
        pm.getMenu().add(0, 101, 101, "Ajouter des fichiers…");
        pm.getMenu().add(0, 102, 102, "Importer un projet (zip)…");
        pm.setOnMenuItemClickListener(it -> {
            int id = it.getItemId();
            if (id == 100) newFile();
            else if (id == 101) pickFiles();
            else if (id == 102) pickProjectZip();
            else if (id >= 0 && id < files.size()) openFile(files.get(id));
            return true;
        });
        pm.show();
    }

    private void newFile() {
        discardPending();
        current = null;
        currentText = "";
        updateLabel();
        line("· Nouveau fichier : décris ce que Krux doit écrire.", DIM);
    }

    private void openFile(File f) {
        if (f == null) return;
        try {
            String text = store.readText(f);
            discardPending();
            current = f;
            currentText = text;
            updateLabel();
            line("· Fichier ouvert : " + f.getName(), DIM);
            showFileView(text);
            if (text.length() > MAX_CHARS) {
                line("· Ce fichier est long (" + text.length() + " caractères) : Krux Code accepte " + MAX_CHARS + " caractères au maximum pour l'instant.", RED);
            }
        } catch (IOException e) {
            Toast.makeText(this, "Lecture impossible : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }


    // ------------------------------------------------------------------ modifications ciblées (seules les lignes concernées changent)

    private String editError = "";

    /** Applique les blocs CHERCHER / REMPLACER au texte ; null (avec editError) si un bloc est introuvable ou ambigu. */
    private String applyEdits(String text, String answer) {
        String t = text.replace("\r\n", "\n");
        Matcher m = Pattern.compile("(?s)<<<<<<< CHERCHER[ \\t]*\\n(.*?)\\n=======[ \\t]*\\n(.*?)\\n?>>>>>>> REMPLACER").matcher(answer);
        int n = 0;
        while (m.find()) {
            n++;
            String search = m.group(1), repl = m.group(2);
            int at = t.indexOf(search);
            if (at >= 0 && t.indexOf(search, at + 1) >= 0) { editError = "Le bloc " + n + " correspond à plusieurs endroits."; return null; }
            int len = search.length();
            if (at < 0) { // tolérance : espaces de fin de ligne
                String[] tl = t.split("\n", -1), sl = search.split("\n", -1);
                int found = -1;
                for (int i = 0; i + sl.length <= tl.length && found < 0; i++) {
                    boolean ok = true;
                    for (int j = 0; j < sl.length && ok; j++) ok = tl[i + j].replaceAll("\\s+$", "").equals(sl[j].replaceAll("\\s+$", ""));
                    if (ok) found = i;
                }
                if (found < 0) { editError = "Le bloc " + n + " est introuvable dans le fichier."; return null; }
                int start = 0;
                for (int i = 0; i < found; i++) start += tl[i].length() + 1;
                int end = start;
                for (int j = 0; j < sl.length; j++) end += tl[found + j].length() + (j < sl.length - 1 ? 1 : 0);
                at = start; len = end - start;
            }
            t = t.substring(0, at) + repl + t.substring(at + len);
        }
        if (n == 0) { editError = "Aucun bloc de modification lisible."; return null; }
        return t;
    }

    // ------------------------------------------------------------------ fichier ouvert coloré

    /** Affiche le début du fichier avec numéros de ligne et coloration syntaxique. */
    private void showFileView(String text) {
        String[] ls = text.replace("\r\n", "\n").split("\n", -1);
        int shown = Math.min(ls.length, 60);
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        for (int i = 0; i < shown; i++) {
            int a = sb.length();
            String num = String.format(java.util.Locale.US, "%3d ", i + 1);
            sb.append(num);
            sb.setSpan(new android.text.style.ForegroundColorSpan(DIM), a, a + num.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            int cs = sb.length();
            String code = ls[i].length() > 160 ? ls[i].substring(0, 160) + "…" : ls[i];
            sb.append(code);
            sb.setSpan(new android.text.style.ForegroundColorSpan(TXT), cs, sb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            CodeHighlighter.apply(sb, cs, sb.length());
            sb.append('\n');
        }
        if (ls.length > shown) {
            int a = sb.length();
            String more = "… " + (ls.length - shown) + " lignes de plus\n";
            sb.append(more);
            sb.setSpan(new android.text.style.ForegroundColorSpan(DIM), a, sb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        TextView tv = new TextView(this);
        tv.setText(sb);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setTextSize(11.5f);
        tv.setBackgroundColor(0xFF0F0D17);
        tv.setPadding(px(10), px(8), px(10), px(8));
        tv.setHorizontallyScrolling(true);
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.addView(tv);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, px(4), 0, px(8));
        log.addView(hs, lp);
        scrollDown();
    }

    // ------------------------------------------------------------------ ajout de fichiers depuis le téléphone

    private void pickFiles() {
        try {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            i.putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(i, 72);
        } catch (Exception e) {
            Toast.makeText(this, "Sélecteur de fichiers indisponible", Toast.LENGTH_SHORT).show();
        }
    }

    private void pickProjectZip() {
        try {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            i.putExtra(android.content.Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
            startActivityForResult(i, 73);
        } catch (Exception e) {
            Toast.makeText(this, "Sélecteur de fichiers indisponible", Toast.LENGTH_SHORT).show();
        }
    }

    /** Importe le zip en arrière-plan (jusqu'à 60 Mo) sans appeler l'IA. */
    private void importProjectZip(final android.net.Uri u) {
        String nm = "projet.zip";
        try (android.database.Cursor c = getContentResolver().query(u, null, null, null, null)) {
            if (c != null && c.moveToFirst()) { int ix = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); if (ix >= 0) nm = c.getString(ix); }
        } catch (Exception ignored) { }
        final String zipName = nm;
        line("· Import du projet " + zipName + "…", DIM);
        new Thread(() -> {
            try (java.io.InputStream in = getContentResolver().openInputStream(u)) {
                final com.virtixstudio.kruxai.core.ProjectStore.Result r = projects.importZip(zipName, in);
                runOnUiThread(() -> {
                    line("· Projet « " + r.dir.getName() + " » importé : " + r.files + " fichiers, "
                            + WorkspaceStore.sizeLabel(r.bytes)
                            + (r.skipped > 0 ? " (" + r.skipped + " ignorés : .git, build, local.properties…)" : "") + ".", DIM);
                    line(r.androidGradle ? "· Projet Android (Gradle) reconnu." : "· Pas de projet Android Gradle reconnu (settings.gradle + app/).", DIM);
                });
            } catch (Exception e) {
                runOnUiThread(() -> line("· Import impossible : " + e.getMessage(), RED));
            }
        }).start();
    }

    @Override
    protected void onActivityResult(int req, int res, android.content.Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 73 && res == RESULT_OK && data != null && data.getData() != null) { importProjectZip(data.getData()); return; }
        if (req != 72 || res != RESULT_OK || data == null) return;
        java.util.ArrayList<android.net.Uri> uris = new java.util.ArrayList<>();
        if (data.getClipData() != null) for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
        else if (data.getData() != null) uris.add(data.getData());
        File last = null;
        int ok = 0;
        for (android.net.Uri u : uris) {
            String name = "fichier";
            try (android.database.Cursor c = getContentResolver().query(u, null, null, null, null)) {
                if (c != null && c.moveToFirst()) { int ix = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); if (ix >= 0) name = c.getString(ix); }
            } catch (Exception ignored) { }
            try (java.io.InputStream in = getContentResolver().openInputStream(u)) {
                last = store.importStream(name, in);
                ok++;
            } catch (Exception e) {
                line("· Import impossible : " + name + " (" + e.getMessage() + ")", RED);
            }
        }
        if (ok > 0) {
            line("· " + ok + (ok > 1 ? " fichiers ajoutés" : " fichier ajouté") + " à l'espace de travail.", DIM);
            if (last != null) openFile(last);
        }
    }

    // ------------------------------------------------------------------ demande

    private void submit() {
        if (busy) return;
        final String ask = input.getText().toString().trim();
        if (ask.isEmpty()) return;
        discardPending();
        if (currentText.length() > MAX_CHARS) {
            line("· Fichier trop long pour Krux Code (" + MAX_CHARS + " caractères maximum).", RED);
            return;
        }
        input.setText("");
        line("› " + ask, TXT);
        if (ask.matches("(?is)^\\s*(?:peux-tu |tu peux |s'il te pla[iî]t )?(?:cr[ée]+r?e?|fais(?:-moi)?|construis|g[ée]n[èe]re|d[ée]veloppe)\\b.{0,25}\\b(?:app|application|appli)\\b.*")) {
            line("· Création d'application : j'ouvre le créateur de Krux (questions, puis compilation).", accent);
            startActivity(new android.content.Intent(this, MainActivity.class)
                    .putExtra("create_app", ask)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP));
            return;
        }
        setBusy(true);
        agentStep(ask, "", 0);
    }

    private void setBusy(boolean b) {
        busy = b;
        if (sendBtn != null) sendBtn.setText(b ? "■" : "▶");
    }

    /** Bouton Stop : coupe la requête en cours, le navigateur et la suite des actions. */
    private void stopAgent() {
        runId++;
        try { ApiClient.cancelCurrentRequest(); } catch (Exception ignored) { }
        pageWait = null;
        if (web != null) { try { web.stopLoading(); } catch (Exception ignored) { } }
        if (currentStatus != null) { log.removeView(currentStatus); currentStatus = null; }
        setBusy(false);
        line("· Arrêté.", DIM);
    }

    private interface ToolDone { void done(String result); }

    private String actionOf(String response) {
        String r = response == null ? "" : response.replaceAll("(?s)<REFLEXION>.*?</REFLEXION>", "").trim();
        Matcher m = Pattern.compile("(?m)^\\s*(OUVRIR|CLIQUER|TAPER|DEFILER)\\s*:\\s*(.+?)\\s*$").matcher(r);
        return m.find() ? m.group(1) + "|" + m.group(2) : null;
    }

    /** Exécute une action du navigateur de Krux puis rend le texte de la page. */
    private void runTool(String act, final ToolDone cb) {
        int bar = act.indexOf('|');
        String kind = act.substring(0, bar);
        String arg = act.substring(bar + 1).trim();
        webBox.setVisibility(AgentPrefs.showBrowser(this) ? View.VISIBLE : View.GONE);
        if (webTitle != null) webTitle.setText("🌐 chargement…");
        final Runnable[] finish = new Runnable[1];
        final boolean[] done = new boolean[1];
        finish[0] = () -> {
            if (done[0]) return;
            done[0] = true;
            pageWait = null;
            web.evaluateJavascript("(function(){return document.body?document.body.innerText:''})()", val -> {
                String t = "";
                try { t = new JSONArray("[" + val + "]").optString(0); } catch (Exception ignored) { }
                t = t.replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
                if (t.length() > PAGE_CHARS) t = t.substring(0, PAGE_CHARS) + "\n[…texte coupé]";
                cb.done("PAGE " + web.getUrl() + " :\n" + t + "\n\n");
            });
        };
        final int myRun = runId;
        final long pause = 450 + (long) (Math.random() * 850);   // une personne ne réagit pas instantanément
        web.postDelayed(() -> {
            if (myRun != runId) return;
            if ("OUVRIR".equals(kind)) {
                String u = arg.matches("(?i)^https?://.*") ? arg : (arg.matches("(?i)^[a-z][a-z0-9+.-]*:.*") ? "" : "https://" + arg);
                if (u.isEmpty()) { line("· Adresse refusée : " + arg, RED); cb.done("ERREUR : adresse refusée (seulement http/https).\n\n"); return; }
                line("· ouvre " + u.replaceFirst("(?i)^https?://", ""), DIM);
                pageWait = finish[0];
                web.loadUrl(u);
            } else if ("TAPER".equals(kind)) {
                int b2 = arg.indexOf('|');
                final String field = (b2 < 0 ? arg : arg.substring(0, b2)).trim();
                final String value = b2 < 0 ? "" : arg.substring(b2 + 1).trim();
                line("· écrit dans « " + field + " »", DIM);
                typeInto(field, value, myRun, () -> {
                    if (myRun != runId) return;
                    pageWait = finish[0];
                    web.postDelayed(finish[0], 1200);
                });
            } else if ("DEFILER".equals(kind)) {
                boolean up = arg.toLowerCase().startsWith("haut");
                line("· fait défiler la page " + (up ? "vers le haut" : "vers le bas"), DIM);
                web.evaluateJavascript("window.scrollBy({top:" + (up ? "-" : "") + "Math.round(window.innerHeight*0.8),behavior:'smooth'})", v -> {
                    pageWait = finish[0];
                    web.postDelayed(finish[0], 900 + (long) (Math.random() * 400));
                });
            } else {
                if (arg.toLowerCase().matches(".*(payer|paiement|acheter|commander|pay now|purchase|buy now|checkout|supprimer le compte|delete account).*")) {
                    new android.app.AlertDialog.Builder(KruxCodeActivity.this)
                            .setTitle("Krux veut cliquer")
                            .setMessage("« " + arg + " »")
                            .setCancelable(false)
                            .setPositiveButton("Autoriser", (dd, w) -> clickText(arg, finish[0]))
                            .setNegativeButton("Refuser", (dd, w) -> { line("· clic refusé : " + arg, DIM); finish[0].run(); })
                            .show();
                } else {
                    clickText(arg, finish[0]);
                }
            }
        }, pause);
        web.postDelayed(finish[0], 15000 + pause);
    }

    /** Clique sur le lien ou le bouton qui contient ce texte, après l'avoir amené à l'écran. */
    private void clickText(final String arg, final Runnable finish) {
        line("· clique « " + arg + " »", DIM);
        String js = "(function(t){t=t.toLowerCase();var e=document.querySelectorAll('a,button,[role=button],input[type=submit]');"
                + "for(var i=0;i<e.length;i++){var x=(e[i].innerText||e[i].value||'').toLowerCase();"
                + "if(x.indexOf(t)>=0){e[i].scrollIntoView({block:'center',behavior:'smooth'});"
                + "(function(el){setTimeout(function(){el.click();},450);})(e[i]);return 'ok';}}return 'introuvable';})(" + JSONObject.quote(arg) + ")";
        web.evaluateJavascript(js, val -> {
            if (val != null && val.contains("introuvable")) { line("· introuvable : " + arg, DIM); }
            pageWait = finish;
            web.postDelayed(finish, 2500);
        });
    }

    /** Écrit dans un champ, caractère par caractère (35 à 95 ms), sans jamais toucher à un mot de passe. */
    private void typeInto(final String field, final String value, final int run, final Runnable done) {
        String findJs = "(function(t){t=t.toLowerCase();var e=document.querySelectorAll("
                + "'input:not([type=hidden]):not([type=checkbox]):not([type=radio]):not([type=submit]):not([type=button]),textarea,[contenteditable=true]');"
                + "for(var i=0;i<e.length;i++){var el=e[i];var l=((el.placeholder||'')+' '+(el.name||'')+' '+(el.id||'')+' '+(el.getAttribute('aria-label')||'')"
                + "+' '+((el.labels&&el.labels[0])?el.labels[0].innerText:'')).toLowerCase();"
                + "if(t===''||l.indexOf(t)>=0){if(el.type==='password')return 'mdp';el.scrollIntoView({block:'center'});el.focus();window.__kx=el;return 'ok';}}"
                + "return 'introuvable';})(" + JSONObject.quote(field) + ")";
        web.evaluateJavascript(findJs, val -> {
            if (run != runId) return;
            if (val != null && val.contains("mdp")) { line("· champ mot de passe : tape-le toi-même.", RED); done.run(); return; }
            if (val == null || !val.contains("ok")) { line("· champ introuvable : " + field, DIM); done.run(); return; }
            typeChars(value, 0, run, done);
        });
    }

    private void typeChars(final String value, final int from, final int run, final Runnable done) {
        if (run != runId) return;
        if (from >= value.length()) {
            web.evaluateJavascript("(function(){var el=window.__kx;if(el)el.dispatchEvent(new Event('change',{bubbles:true}));})()", v -> done.run());
            return;
        }
        int chunk = Math.max(1, value.length() / 60);
        final int to = Math.min(value.length(), from + chunk);
        String js = "(function(s){var el=window.__kx;if(!el)return;if(el.isContentEditable){el.textContent+=s;}else{"
                + "var d=Object.getOwnPropertyDescriptor(Object.getPrototypeOf(el),'value');var nv=(el.value||'')+s;"
                + "if(d&&d.set)d.set.call(el,nv);else el.value=nv;}el.dispatchEvent(new Event('input',{bubbles:true}));})("
                + JSONObject.quote(value.substring(from, to)) + ")";
        web.evaluateJavascript(js, null);
        web.postDelayed(() -> typeChars(value, to, run, done), 35 + (long) (Math.random() * 60));
    }

    private void agentStep(final String ask, final String observations, final int step) {
        final int myRun = runId;
        final TextView status = line("· Krux travaille…", DIM);
        currentStatus = status;
        String hist = recentAsks();
        String user = hist + (current == null
                ? "NOUVEAU FICHIER.\nDEMANDE : " + ask
                : "FICHIER : " + current.getName() + "\n```\n" + currentText + "\n```\n\nDEMANDE : " + ask);
        if (!observations.isEmpty()) user = user + "\n\nOBSERVATIONS (pages consultées) :\n" + observations;
        ApiClient.sendRequest(KruxModel.KRUX_CODEUR_PRO, SYSTEM, user, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(String response, String modelBrand) {
                runOnUiThread(() -> {
                    if (myRun != runId) return;
                    setBusy(false);
                    log.removeView(status);
                    String act = (AgentPrefs.agentOn(this) && step < AgentPrefs.maxSteps(this)) ? actionOf(response) : null;
                    if (act == null) { handleAnswer(response); return; }
                    setBusy(true);
                    runTool(act, result -> { if (myRun == runId) agentStep(ask, trimObs(observations + result), step + 1); });
                });
            }

            @Override
            public void onError(String friendlyMessage) {
                runOnUiThread(() -> {
                    if (myRun != runId) return;
                    setBusy(false);
                    status.setText("· " + friendlyMessage);
                    status.setTextColor(RED);
                });
            }

            @Override
            public void onCancelled() {
                runOnUiThread(() -> { if (myRun == runId) setBusy(false); });
            }
        });
    }

    private void handleAnswer(String response) {
        String r = response == null ? "" : response.replaceAll("(?s)<REFLEXION>.*?</REFLEXION>", "").trim();
        if (r.regionMatches(true, 0, "IMPOSSIBLE", 0, 10)) {
            line("· " + r.replaceFirst("(?i)^IMPOSSIBLE\\s*:?\\s*", ""), DIM);
            return;
        }
        String name = null;
        Matcher mn = Pattern.compile("(?m)^\\s*FICHIER\\s*:\\s*(\\S+)\\s*$").matcher(r);
        if (mn.find()) name = mn.group(1);

        if (current != null && r.contains("<<<<<<< CHERCHER")) {
            String res = applyEdits(currentText, r);
            if (res == null) { line("· " + editError + " Rien n'a été modifié.", RED); return; }
            pendingName = current.getName();
            if (res.equals(currentText)) { line("· Aucun changement.", DIM); return; }
            pendingText = res;
            showDiff(currentText, res);
            return;
        }
        int open = r.indexOf("```");
        int nl = open < 0 ? -1 : r.indexOf('\n', open);
        int close = r.lastIndexOf("```");
        if (open < 0) {
            line("· Krux n'a pas renvoyé de code exploitable. Rien n'a été modifié.", RED);
            return;
        }
        if (nl < 0 || close <= nl) {
            line("· La réponse a été coupée avant la fin : rien n'a été modifié. Essaie avec une demande plus courte.", RED);
            return;
        }
        String code = r.substring(nl + 1, close).replaceAll("[\\r\\n]+$", "");
        if (currentText.endsWith("\n")) code += "\n";

        pendingName = current != null ? current.getName() : (name != null ? name : "krux-code.txt");
        if (current != null && code.equals(currentText)) {
            line("· Aucun changement.", DIM);
            return;
        }
        if (code.trim().isEmpty()) {
            line("· Krux a renvoyé un fichier vide : rien n'a été modifié.", RED);
            return;
        }
        pendingText = code;
        showDiff(current == null ? "" : currentText, code);
    }

    // ------------------------------------------------------------------ diff

    /** Lignes précédées de « ' ' » (inchangée), « - » (retirée) ou « + » (ajoutée). */
    static ArrayList<String> diff(String a, String b) {
        String[] x = a.isEmpty() ? new String[0] : a.split("\n", -1);
        String[] y = b.isEmpty() ? new String[0] : b.split("\n", -1);
        int pre = 0;
        while (pre < x.length && pre < y.length && x[pre].equals(y[pre])) pre++;
        int sx = x.length;
        int sy = y.length;
        while (sx > pre && sy > pre && x[sx - 1].equals(y[sy - 1])) { sx--; sy--; }
        ArrayList<String> out = new ArrayList<>();
        for (int i = 0; i < pre; i++) out.add(" " + x[i]);
        int n = sx - pre;
        int m = sy - pre;
        if ((long) n * m > 1_500_000L) {
            for (int i = 0; i < n; i++) out.add("-" + x[pre + i]);
            for (int j = 0; j < m; j++) out.add("+" + y[pre + j]);
        } else {
            int[][] l = new int[n + 1][m + 1];
            for (int i = n - 1; i >= 0; i--) {
                for (int j = m - 1; j >= 0; j--) {
                    l[i][j] = x[pre + i].equals(y[pre + j]) ? l[i + 1][j + 1] + 1 : Math.max(l[i + 1][j], l[i][j + 1]);
                }
            }
            int i = 0;
            int j = 0;
            while (i < n && j < m) {
                if (x[pre + i].equals(y[pre + j])) { out.add(" " + x[pre + i]); i++; j++; }
                else if (l[i + 1][j] >= l[i][j + 1]) { out.add("-" + x[pre + i]); i++; }
                else { out.add("+" + y[pre + j]); j++; }
            }
            while (i < n) { out.add("-" + x[pre + i]); i++; }
            while (j < m) { out.add("+" + y[pre + j]); j++; }
        }
        for (int i = sx; i < x.length; i++) out.add(" " + x[i]);
        return out;
    }

    private void showDiff(String oldText, String newText) {
        ArrayList<String> ops = diff(oldText, newText);
        int added = 0;
        int removed = 0;
        boolean[] show = new boolean[ops.size()];
        for (int i = 0; i < ops.size(); i++) {
            char c = ops.get(i).charAt(0);
            if (c == ' ') continue;
            if (c == '+') added++; else removed++;
            for (int k = Math.max(0, i - 2); k <= Math.min(ops.size() - 1, i + 2); k++) show[k] = true;
        }

        SpannableStringBuilder sb = new SpannableStringBuilder();
        int shownLines = 0;
        boolean gap = false;
        for (int i = 0; i < ops.size(); i++) {
            if (!show[i]) { gap = true; continue; }
            if (shownLines >= MAX_DIFF_LINES) {
                sb.append("… (suite non affichée)\n");
                break;
            }
            if (gap && sb.length() > 0) sb.append("···\n");
            gap = false;
            String op = ops.get(i);
            char c = op.charAt(0);
            int start = sb.length();
            sb.append(c == '+' ? "+ " : c == '-' ? "- " : "  ").append(op.substring(1)).append('\n');
            int cs = start + 2, ce = sb.length() - 1;
            if (c == '+') {
                sb.setSpan(new BackgroundColorSpan(0x3328C76F), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new ForegroundColorSpan(GREEN), start, cs, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (c == '-') {
                sb.setSpan(new BackgroundColorSpan(0x33FF5C5C), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new ForegroundColorSpan(RED), start, cs, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                sb.setSpan(new ForegroundColorSpan(DIM), start, cs, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (ce > cs) CodeHighlighter.apply(sb, cs, ce);
            shownLines++;
        }

        line("· " + pendingName + " : +" + added + " −" + removed, TXT);
        TextView body = new TextView(this);
        body.setText(sb);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextSize(11);
        body.setTextColor(TXT);
        body.setTextIsSelectable(true);
        body.setBackground(round(PANEL, 12));
        body.setPadding(px(10), px(8), px(10), px(8));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = px(4);
        log.addView(body, bp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView apply = pill("Appliquer", BG, accent);
        apply.setOnClickListener(v -> applyPending());
        TextView cancel = pill("Annuler", TXT, PANEL);
        cancel.setOnClickListener(v -> {
            discardPending();
            line("· Modification abandonnée, le fichier n'a pas changé.", DIM);
        });
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, px(42), 1f);
        p1.rightMargin = px(6);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, px(42), 1f);
        row.addView(apply, p1);
        row.addView(cancel, p2);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = px(8);
        log.addView(row, rp);
        actionsRow = row;
        scrollDown();
    }

    // ------------------------------------------------------------------ appliquer / annuler

    private void discardPending() {
        pendingText = null;
        if (actionsRow != null) {
            log.removeView(actionsRow);
            actionsRow = null;
        }
    }

    private void applyPending() {
        if (pendingText == null) return;
        try {
            File target = current;
            File prevFile = current;
            String prevText = current == null ? null : currentText;
            if (current == null) target = store.saveGenerated(pendingName, pendingText);
            else store.writeText(current, pendingText);
            undoFile = prevFile;
            undoText = prevText;
            current = target;
            currentText = pendingText;
            discardPending();
            updateLabel();
            line("✓ " + current.getName() + " enregistré dans Fichiers.", GREEN);
            addUndoButton();
        } catch (IOException e) {
            line("· Écriture impossible : " + e.getMessage(), RED);
        }
    }

    private void addUndoButton() {
        TextView undo = pill("↶ Annuler la modification", TXT, PANEL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, px(40));
        lp.topMargin = px(4);
        log.addView(undo, lp);
        actionsRow = undo;
        undo.setOnClickListener(v -> undoLast());
        scrollDown();
    }

    private void undoLast() {
        if (actionsRow != null) {
            log.removeView(actionsRow);
            actionsRow = null;
        }
        try {
            if (undoFile == null) {
                if (current != null) store.delete(current);
                current = null;
                currentText = "";
            } else {
                store.writeText(undoFile, undoText == null ? "" : undoText);
                current = undoFile;
                currentText = undoText == null ? "" : undoText;
            }
            undoFile = null;
            undoText = null;
            updateLabel();
            line("↶ Modification annulée.", DIM);
        } catch (IOException e) {
            line("· Annulation impossible : " + e.getMessage(), RED);
        }
    }
}
