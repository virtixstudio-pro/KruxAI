package com.virtixstudio.kruxai.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.virtixstudio.kruxai.core.ProjectStore;
import com.virtixstudio.kruxai.core.WorkspaceStore;

import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Fichiers de travail de Krux : on importe des fichiers de code ou de texte, on les ouvre, on les partage ou on les supprime.
 * Ce sont les mêmes fichiers que Krux pourra lire et modifier. Interface construite en code, aux couleurs du thème.
 */
public class FilesActivity extends AppCompatActivity {

    private WorkspaceStore store;
    private ProjectStore projects;
    private LinearLayout list;
    private TextView summary;
    private float d;
    private int bg, tx, tx2, ac, fill;

    private final ActivityResultLauncher<String[]> picker = registerForActivityResult(
            new ActivityResultContracts.OpenMultipleDocuments(),
            uris -> { if (uris != null && !uris.isEmpty()) importAll(uris); });

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        d = getResources().getDisplayMetrics().density;
        bg = KruxTheme.get(this, "bg");
        tx = KruxTheme.get(this, "text");
        tx2 = KruxTheme.get(this, "text2");
        ac = KruxTheme.get(this, "accent");
        fill = KruxTheme.get(this, "input");
        store = new WorkspaceStore(new File(getFilesDir(), "workspace"));
        store.adoptSubfolders();
        projects = new ProjectStore(new File(getFilesDir(), "projects"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding((int) (10 * d), (int) (6 * d), (int) (10 * d), (int) (6 * d));
        TextView close = pill("Fermer", tx, fill);
        close.setOnClickListener(v -> finish());
        TextView title = new TextView(this);
        title.setText("Fichiers");
        title.setTextColor(tx);
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        TextView add = pill("Importer", bg, ac);
        add.setOnClickListener(v -> picker.launch(new String[]{"*/*"}));
        bar.addView(close);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        bar.addView(add);
        root.addView(bar, new LinearLayout.LayoutParams(-1, (int) (56 * d)));

        summary = new TextView(this);
        summary.setTextColor(tx2);
        summary.setTextSize(12);
        summary.setPadding((int) (18 * d), (int) (2 * d), (int) (18 * d), (int) (8 * d));
        root.addView(summary, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding((int) (12 * d), 0, (int) (12 * d), (int) (24 * d));
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets s = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(s.left, s.top, s.right, s.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        refresh();
    }

    private TextView pill(String label, int color, int fillColor) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(13);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding((int) (14 * d), 0, (int) (14 * d), 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(fillColor);
        g.setCornerRadius(KruxTheme.radiusDp(this) * d);
        t.setBackground(g);
        t.setMinHeight((int) (38 * d));
        t.setClickable(true);
        return t;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (list != null && projects != null) refresh(); // un projet importé dans Krux Code apparaît au retour
    }

    private void refresh() {
        list.removeAllViews();
        List<File> files = store.list();
        List<File> projs = projects.list();
        long total = 0;
        for (File f : files) total += f.length();
        for (File pr : projs) total += ProjectStore.treeSize(pr);
        String sum = "";
        if (!projs.isEmpty()) sum += projs.size() + (projs.size() > 1 ? " projets" : " projet");
        if (!files.isEmpty()) sum += (sum.isEmpty() ? "" : "  ·  ") + files.size() + (files.size() > 1 ? " fichiers" : " fichier");
        summary.setText(sum.isEmpty() ? "" : sum + "  ·  " + WorkspaceStore.sizeLabel(total));
        if (files.isEmpty() && projs.isEmpty()) { list.addView(emptyState()); return; }
        SimpleDateFormat fmt = new SimpleDateFormat("d MMM · HH:mm", Locale.FRENCH);
        for (File pr : projs) list.addView(projectCard(pr, fmt));
        for (File f : files) list.addView(card(f, fmt));
    }

    /** Un projet importé (zip ou dossier) : s'ouvre dans le navigateur de Krux Code. */
    private View projectCard(final File dir, SimpleDateFormat fmt) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding((int) (12 * d), (int) (10 * d), (int) (12 * d), (int) (10 * d));
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(16 * d);
        row.setBackground(g);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.bottomMargin = (int) (8 * d);
        row.setLayoutParams(rp);

        TextView badge = new TextView(this);
        badge.setText("DIR");
        badge.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        badge.setTextSize(11);
        badge.setTextColor(bg);
        badge.setGravity(Gravity.CENTER);
        GradientDrawable bgd = new GradientDrawable();
        bgd.setColor(ac);
        bgd.setCornerRadius(12 * d);
        badge.setBackground(bgd);
        row.addView(badge, new LinearLayout.LayoutParams((int) (46 * d), (int) (46 * d)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding((int) (12 * d), 0, 0, 0);
        TextView name = new TextView(this);
        name.setText(dir.getName() + "/");
        name.setTextColor(tx);
        name.setTextSize(14);
        name.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        TextView det = new TextView(this);
        int n = ProjectStore.treeFiles(dir);
        det.setText(n + (n > 1 ? " fichiers" : " fichier") + "  ·  " + WorkspaceStore.sizeLabel(ProjectStore.treeSize(dir)) + "  ·  " + fmt.format(new Date(dir.lastModified())));
        det.setTextColor(tx2);
        det.setTextSize(11);
        col.addView(name);
        col.addView(det);
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));

        row.setClickable(true);
        row.setOnClickListener(v -> projectMenu(v, dir));
        return row;
    }

    private void projectMenu(View anchor, final File dir) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, "Ouvrir dans Krux Code");
        pm.getMenu().add(0, 2, 1, "Supprimer le projet");
        pm.setOnMenuItemClickListener(it -> {
            if (it.getItemId() == 1) {
                startActivity(new Intent(this, KruxCodeActivity.class).putExtra(KruxCodeActivity.EXTRA_PROJECT, projects.relative(dir)));
            } else {
                confirmDeleteProject(dir);
            }
            return true;
        });
        pm.show();
    }

    private void confirmDeleteProject(final File dir) {
        KruxTheme.skin(new AlertDialog.Builder(this)
                .setTitle("Supprimer ce projet ?")
                .setMessage(dir.getName() + " et tous ses fichiers seront supprimés de Krux. Cette action est définitive.")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Supprimer", (dd, w) -> { projects.delete(dir); refresh(); })
                .show(), this);
    }

    private View emptyState() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding((int) (24 * d), (int) (70 * d), (int) (24 * d), 0);
        TextView mark = new TextView(this);
        mark.setText("{ }");
        mark.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        mark.setTextSize(34);
        mark.setTextColor(ac);
        mark.setGravity(Gravity.CENTER);
        TextView t = new TextView(this);
        t.setText("Aucun fichier");
        t.setTextColor(tx);
        t.setTextSize(17);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, (int) (10 * d), 0, (int) (6 * d));
        TextView s = new TextView(this);
        s.setText("Importe un fichier de code ou de texte (1 Mo maximum). Krux pourra le lire et le modifier.");
        s.setTextColor(tx2);
        s.setTextSize(13);
        s.setGravity(Gravity.CENTER);
        TextView go = pill("Importer un fichier", bg, ac);
        go.setOnClickListener(v -> picker.launch(new String[]{"*/*"}));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-2, -2);
        gp.topMargin = (int) (18 * d);
        box.addView(mark);
        box.addView(t);
        box.addView(s);
        box.addView(go, gp);
        return box;
    }

    private View card(final File f, SimpleDateFormat fmt) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding((int) (12 * d), (int) (10 * d), (int) (12 * d), (int) (10 * d));
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(16 * d);
        row.setBackground(g);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.bottomMargin = (int) (8 * d);
        row.setLayoutParams(rp);

        String ext = WorkspaceStore.extension(f.getName());
        TextView badge = new TextView(this);
        badge.setText(ext.isEmpty() ? "TXT" : (ext.length() > 4 ? ext.substring(0, 4) : ext).toUpperCase(Locale.ROOT));
        badge.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        badge.setTextSize(11);
        badge.setTextColor(bg);
        badge.setGravity(Gravity.CENTER);
        GradientDrawable bgd = new GradientDrawable();
        bgd.setColor(ac);
        bgd.setCornerRadius(12 * d);
        badge.setBackground(bgd);
        row.addView(badge, new LinearLayout.LayoutParams((int) (46 * d), (int) (46 * d)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding((int) (12 * d), 0, 0, 0);
        TextView name = new TextView(this);
        name.setText(f.getName());
        name.setTextColor(tx);
        name.setTextSize(14);
        name.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        TextView det = new TextView(this);
        det.setText(WorkspaceStore.sizeLabel(f.length()) + "  ·  " + fmt.format(new Date(f.lastModified())));
        det.setTextColor(tx2);
        det.setTextSize(11);
        col.addView(name);
        col.addView(det);
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));

        row.setClickable(true);
        row.setOnClickListener(v -> menu(v, f));
        return row;
    }

    private void menu(View anchor, final File f) {
        PopupMenu pm = new PopupMenu(this, anchor);
        String ext = WorkspaceStore.extension(f.getName());
        boolean html = ext.equals("html") || ext.equals("htm");
        pm.getMenu().add(0, 1, 0, "Ouvrir");
        if (html) pm.getMenu().add(0, 2, 1, "Visualiser la page");
        pm.getMenu().add(0, 3, 2, "Partager");
        pm.getMenu().add(0, 4, 3, "Supprimer");
        pm.setOnMenuItemClickListener(it -> {
            int id = it.getItemId();
            if (id == 1) open(f);
            else if (id == 2) preview(f);
            else if (id == 3) share(f);
            else confirmDelete(f);
            return true;
        });
        pm.show();
    }

    private String read(File f) {
        try { return store.readText(f); }
        catch (Exception e) {
            Toast.makeText(this, "Lecture impossible : " + e.getMessage(), Toast.LENGTH_LONG).show();
            return null;
        }
    }

    private void open(File f) {
        String text = read(f);
        if (text == null) return;
        boolean cut = text.length() > 60000;
        TextView body = new TextView(this);
        body.setText(cut ? text.substring(0, 60000) + "\n\n… (suite non affichée)" : text);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextSize(12);
        body.setTextColor(tx);
        body.setTextIsSelectable(true);
        body.setPadding((int) (16 * d), (int) (8 * d), (int) (16 * d), (int) (8 * d));
        ScrollView sv = new ScrollView(this);
        sv.addView(body);
        KruxTheme.skin(new AlertDialog.Builder(this)
                .setTitle(f.getName())
                .setView(sv)
                .setPositiveButton("Fermer", null)
                .show(), this);
    }

    private void preview(File f) {
        String text = read(f);
        if (text == null) return;
        String page = com.virtixstudio.kruxai.builder.PreviewHtml.prepare(text);
        if (page.isEmpty()) {
            Toast.makeText(this, "Page vide ou trop longue pour l'aperçu (30 000 caractères maximum)", Toast.LENGTH_LONG).show();
            return;
        }
        HtmlPreviewActivity.open(this, f.getName(), page);
    }

    private void share(File f) {
        String text = read(f);
        if (text == null) return;
        if (text.length() > 200000) {
            Toast.makeText(this, "Fichier trop gros pour être partagé en texte", Toast.LENGTH_LONG).show();
            return;
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, f.getName());
        i.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(i, "Partager " + f.getName()));
    }

    private void confirmDelete(final File f) {
        KruxTheme.skin(new AlertDialog.Builder(this)
                .setTitle("Supprimer ce fichier ?")
                .setMessage(f.getName() + " sera supprimé de Krux. Cette action est définitive.")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Supprimer", (dd, w) -> { store.delete(f); refresh(); })
                .show(), this);
    }

    private void importAll(final List<Uri> uris) {
        new Thread(() -> {
            int done = 0;
            String error = "";
            for (Uri u : uris) {
                try (InputStream in = getContentResolver().openInputStream(u)) {
                    if (in == null) throw new java.io.IOException("lecture impossible");
                    store.importStream(nameOf(u), in);
                    done++;
                } catch (Exception e) {
                    error = e.getMessage() == null ? "erreur" : e.getMessage();
                }
            }
            final int n = done;
            final String err = error;
            runOnUiThread(() -> {
                refresh();
                String msg = n > 0 ? (n + (n > 1 ? " fichiers importés" : " fichier importé")) : "";
                if (!err.isEmpty()) msg = (msg.isEmpty() ? "" : msg + ". ") + "Refusé : " + err;
                if (!msg.isEmpty()) Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            });
        }, "krux-import").start();
    }

    private String nameOf(Uri u) {
        try (android.database.Cursor c = getContentResolver().query(u, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (i >= 0 && c.getString(i) != null) return c.getString(i);
            }
        } catch (Exception ignored) { }
        String last = u.getLastPathSegment();
        return last == null ? "fichier.txt" : last;
    }
}
