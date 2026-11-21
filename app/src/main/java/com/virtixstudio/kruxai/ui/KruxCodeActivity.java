package com.virtixstudio.kruxai.ui;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
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
          + "Tu réponds UNIQUEMENT par le fichier COMPLET après modification, dans un seul bloc de code entre ``` et ```. "
          + "Aucune phrase avant ni après le bloc. Ne change que ce qui est demandé : garde le style, l'indentation, les noms et les commentaires existants. "
          + "Ne raccourcis jamais le fichier, n'écris jamais « le reste est inchangé ». "
          + "Si la demande est impossible ou trop floue, réponds seulement par une ligne qui commence par IMPOSSIBLE: et la raison en une phrase. "
          + "Si on te demande un NOUVEAU FICHIER, écris d'abord une ligne FICHIER: nom.extension, puis le bloc de code.";

    private WorkspaceStore store;
    private File current;
    private String currentText = "";
    private String pendingText;
    private String pendingName;
    private String undoText;
    private File undoFile;
    private boolean busy;

    private LinearLayout log;
    private ScrollView scroll;
    private EditText input;
    private TextView fileLabel;
    private View actionsRow;
    private float d;
    private int accent;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        d = getResources().getDisplayMetrics().density;
        accent = KruxTheme.get(this, "accent");
        store = new WorkspaceStore(new File(getFilesDir(), "workspace"));
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
        bar.addView(close);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        bar.addView(pick);
        root.addView(bar, new LinearLayout.LayoutParams(-1, px(56)));

        fileLabel = new TextView(this);
        fileLabel.setTextColor(DIM);
        fileLabel.setTextSize(12);
        fileLabel.setTypeface(Typeface.MONOSPACE);
        fileLabel.setPadding(px(16), px(2), px(16), px(8));
        root.addView(fileLabel, new LinearLayout.LayoutParams(-1, -2));

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
        send.setOnClickListener(v -> submit());
        inputBar.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(px(52), px(46));
        sp.leftMargin = px(8);
        inputBar.addView(send, sp);
        root.addView(inputBar, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets s = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            v.setPadding(s.left, s.top, s.right, s.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        line("Krux Code", accent);
        line("Ouvre un fichier avec « Fichier », ou décris ce que tu veux créer. Rien n'est écrit tant que tu n'as pas appuyé sur Appliquer.", DIM);
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
        return t;
    }

    private void scrollDown() {
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void updateLabel() {
        if (current == null) {
            fileLabel.setText("nouveau fichier");
        } else {
            int lines = currentText.isEmpty() ? 0 : currentText.split("\n", -1).length;
            fileLabel.setText(current.getName() + "  ·  " + lines + " lignes");
        }
    }

    // ------------------------------------------------------------------ fichiers

    private void fileMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        final List<File> files = store.list();
        int n = Math.min(files.size(), 15);
        for (int i = 0; i < n; i++) pm.getMenu().add(0, i, i, files.get(i).getName());
        pm.getMenu().add(0, 100, 100, "Nouveau fichier");
        pm.setOnMenuItemClickListener(it -> {
            int id = it.getItemId();
            if (id == 100) newFile();
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
            if (text.length() > MAX_CHARS) {
                line("· Ce fichier est long (" + text.length() + " caractères) : Krux Code accepte " + MAX_CHARS + " caractères au maximum pour l'instant.", RED);
            }
        } catch (IOException e) {
            Toast.makeText(this, "Lecture impossible : " + e.getMessage(), Toast.LENGTH_LONG).show();
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
        busy = true;
        final TextView status = line("· Krux travaille…", DIM);
        String user = current == null
                ? "NOUVEAU FICHIER.\nDEMANDE : " + ask
                : "FICHIER : " + current.getName() + "\n```\n" + currentText + "\n```\n\nDEMANDE : " + ask;
        ApiClient.sendRequest(KruxModel.KRUX_CODEUR_PRO, SYSTEM, user, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(String response, String modelBrand) {
                runOnUiThread(() -> {
                    busy = false;
                    log.removeView(status);
                    handleAnswer(response);
                });
            }

            @Override
            public void onError(String friendlyMessage) {
                runOnUiThread(() -> {
                    busy = false;
                    status.setText("· " + friendlyMessage);
                    status.setTextColor(RED);
                });
            }

            @Override
            public void onCancelled() {
                runOnUiThread(() -> busy = false);
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
            if (c == '+') {
                sb.setSpan(new BackgroundColorSpan(0x3328C76F), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new ForegroundColorSpan(GREEN), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (c == '-') {
                sb.setSpan(new BackgroundColorSpan(0x33FF5C5C), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new ForegroundColorSpan(RED), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                sb.setSpan(new ForegroundColorSpan(DIM), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
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
