package com.virtixstudio.kruxai.ui;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.virtixstudio.kruxai.utils.FileDelivery;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Cartes des fichiers livrés par KRUX : nom, taille, Enregistrer, Partager. Couleurs du thème choisi. */
public final class DeliveredFileCards {

    private DeliveredFileCards() { }

    public static void fill(LinearLayout host, List<FileDelivery.DFile> files) {
        fill(host, files, true);
    }

    /** finished = false : Krux écrit encore, on montre une ligne compacte (nom à gauche, taille à droite) sans boutons. */
    public static void fill(LinearLayout host, List<FileDelivery.DFile> files, boolean finished) {
        host.removeAllViews();
        if (files == null || files.isEmpty()) { host.setVisibility(View.GONE); return; }
        host.setVisibility(View.VISIBLE);
        // Plusieurs fichiers livrés : un seul bloc compact avec « Tout enregistrer » (ZIP). Un seul fichier : la carte habituelle.
        if (finished && files.size() > 1) { host.addView(group(host.getContext(), files)); return; }
        for (FileDelivery.DFile f : files) host.addView(card(host.getContext(), f, finished));
    }

    private static final int VISIBLE_ROWS = 5;

    private static String sizeOf(int b) {
        return b < 1024 ? b + " o" : String.format(Locale.FRANCE, "%.1f Ko", b / 1024.0);
    }

    private static boolean isHtml(String name) {
        String l = name.toLowerCase(Locale.ROOT);
        return l.endsWith(".html") || l.endsWith(".htm");
    }

    /** Bloc unique : « N fichiers », taille totale, Tout enregistrer (ZIP), Partager, puis liste compacte (touche = menu du fichier). */
    private static View group(final Context c, final List<FileDelivery.DFile> files) {
        final float d = c.getResources().getDisplayMetrics().density;
        int bg = KruxTheme.get(c, "input"), text = KruxTheme.get(c, "text"), text2 = KruxTheme.get(c, "text2"), accent = KruxTheme.get(c, "accent");
        int pale = (accent & 0x00FFFFFF) | 0x26000000;

        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Math.round(12 * d), Math.round(10 * d), Math.round(12 * d), Math.round(10 * d));
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(14 * d);
        g.setStroke(Math.round(d), pale);
        box.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Math.round(6 * d);
        box.setLayoutParams(lp);

        int total = 0;
        for (FileDelivery.DFile f : files) total += f.bytes();

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(c);
        title.setText("📦  " + files.size() + " fichiers");
        title.setTextColor(text);
        title.setTextSize(14);
        title.setTypeface(null, Typeface.BOLD);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView tot = new TextView(c);
        tot.setText(sizeOf(total));
        tot.setTextColor(text2);
        tot.setTextSize(11);
        head.addView(tot, new LinearLayout.LayoutParams(-2, -2));
        box.addView(head);

        LinearLayout actions = new LinearLayout(c);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, Math.round(8 * d), 0, Math.round(6 * d));
        TextView saveAll = button(c, "Tout enregistrer (ZIP)", accent, pale, d);
        TextView shareAll = button(c, "Partager", accent, pale, d);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
        bp.rightMargin = Math.round(8 * d);
        actions.addView(saveAll, bp);
        actions.addView(shareAll);
        box.addView(actions);
        saveAll.setOnClickListener(v -> saveZip(c, files));
        shareAll.setOnClickListener(v -> shareZip(c, files));

        final LinearLayout more = new LinearLayout(c);
        more.setOrientation(LinearLayout.VERTICAL);
        more.setVisibility(View.GONE);
        for (int i = 0; i < files.size(); i++) {
            View row = row(c, files.get(i), text, text2, d);
            if (i < VISIBLE_ROWS) box.addView(row); else more.addView(row);
        }
        if (files.size() > VISIBLE_ROWS) {
            box.addView(more);
            final TextView toggle = new TextView(c);
            final String closed = "Voir les " + (files.size() - VISIBLE_ROWS) + " autres  ▾";
            toggle.setText(closed);
            toggle.setTextColor(accent);
            toggle.setTextSize(12);
            toggle.setTypeface(null, Typeface.BOLD);
            toggle.setPadding(0, Math.round(8 * d), 0, Math.round(2 * d));
            toggle.setOnClickListener(v -> {
                boolean open = more.getVisibility() == View.VISIBLE;
                more.setVisibility(open ? View.GONE : View.VISIBLE);
                toggle.setText(open ? closed : "Réduire  ▴");
            });
            box.addView(toggle);
        }
        return box;
    }

    /** Ligne compacte : nom à gauche, taille à droite. Touche : Visualiser (HTML), Enregistrer, Partager. */
    private static View row(final Context c, final FileDelivery.DFile f, int text, int text2, float d) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Math.round(7 * d), 0, Math.round(7 * d));
        String full = f.name.replace('\\', '/');
        String base = full.substring(full.lastIndexOf('/') + 1);
        TextView name = new TextView(c);
        name.setText("📄  " + base);
        name.setTextColor(text);
        name.setTextSize(13);
        name.setTypeface(Typeface.MONOSPACE);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView size = new TextView(c);
        size.setText(sizeOf(f.bytes()));
        size.setTextColor(text2);
        size.setTextSize(11);
        size.setPadding(Math.round(10 * d), 0, 0, 0);
        row.addView(size, new LinearLayout.LayoutParams(-2, -2));
        row.setClickable(true);
        row.setOnClickListener(v -> {
            PopupMenu pm = new PopupMenu(c, v);
            if (isHtml(f.name)) pm.getMenu().add(0, 3, 0, "Visualiser");
            pm.getMenu().add(0, 1, 1, "Enregistrer");
            pm.getMenu().add(0, 2, 2, "Partager");
            pm.setOnMenuItemClickListener(it -> {
                int id = it.getItemId();
                if (id == 1) save(c, f);
                else if (id == 2) share(c, f);
                else if (id == 3) HtmlPreviewActivity.open(c, f.name, f.content);
                return true;
            });
            pm.show();
        });
        return row;
    }

    /** ZIP en mémoire : chemins relatifs conservés, noms en double renommés, aucun « ../ ». */
    static byte[] zipBytes(List<FileDelivery.DFile> files) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        HashSet<String> used = new HashSet<>();
        try (ZipOutputStream z = new ZipOutputStream(bo)) {
            for (FileDelivery.DFile f : files) {
                String n = f.name.replace('\\', '/');
                while (n.startsWith("/")) n = n.substring(1);
                while (n.contains("../")) n = n.replace("../", "");
                if (n.isEmpty() || n.equals("..")) n = "fichier.txt";
                String uniq = n;
                int k = 2;
                while (!used.add(uniq)) {
                    int dot = n.lastIndexOf('.');
                    int sl = n.lastIndexOf('/');
                    uniq = dot > sl + 1 ? n.substring(0, dot) + "-" + k + n.substring(dot) : n + "-" + k;
                    k++;
                }
                z.putNextEntry(new ZipEntry(uniq));
                z.write(f.content.getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return bo.toByteArray();
    }

    private static String zipName() {
        return "krux-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(new Date()) + ".zip";
    }

    public static void saveZip(Context c, List<FileDelivery.DFile> files) {
        try {
            saveBytes(c, zipName(), "application/zip", zipBytes(files));
        } catch (Exception e) {
            Toast.makeText(c, "Enregistrement impossible : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public static void shareZip(Context c, List<FileDelivery.DFile> files) {
        try {
            shareBytes(c, zipName(), "application/zip", zipBytes(files));
        } catch (Exception e) {
            Toast.makeText(c, "Partage impossible : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static TextView button(Context c, String label, int accent, int fill, float d) {
        TextView b = new TextView(c);
        b.setText(label);
        b.setTextSize(12);
        b.setTypeface(null, Typeface.BOLD);
        b.setTextColor(accent);
        b.setGravity(Gravity.CENTER);
        b.setPadding(Math.round(12 * d), Math.round(6 * d), Math.round(12 * d), Math.round(6 * d));
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(16 * d);
        b.setBackground(g);
        b.setClickable(true);
        return b;
    }

    private static View card(final Context c, final FileDelivery.DFile f, boolean finished) {
        final float d = c.getResources().getDisplayMetrics().density;
        int bg = KruxTheme.get(c, "input"), text = KruxTheme.get(c, "text"), text2 = KruxTheme.get(c, "text2"), accent = KruxTheme.get(c, "accent");
        int pale = (accent & 0x00FFFFFF) | 0x26000000;

        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Math.round(12 * d), Math.round(10 * d), Math.round(12 * d), Math.round(10 * d));
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(14 * d);
        g.setStroke(Math.round(d), pale);
        box.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Math.round(6 * d);
        box.setLayoutParams(lp);

        // Le long chemin (app/src/main/java/com/…) est masqué : on garde le nom du fichier, le dossier est résumé.
        String full = f.name.replace('\\', '/');
        int slash = full.lastIndexOf('/');
        String base = slash >= 0 ? full.substring(slash + 1) : full;
        String dir = slash > 0 ? full.substring(0, slash) : "";

        int b = f.bytes();
        String size = b < 1024 ? b + " o" : String.format(java.util.Locale.FRANCE, "%.1f Ko", b / 1024.0);

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(c);
        name.setText("📄  " + base);
        name.setTextColor(text);
        name.setTextSize(14);
        name.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        head.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView sizeView = new TextView(c);
        sizeView.setText(size);
        sizeView.setTextColor(text2);
        sizeView.setTextSize(11);
        sizeView.setGravity(Gravity.END);
        sizeView.setPadding(Math.round(10 * d), 0, 0, 0);
        head.addView(sizeView, new LinearLayout.LayoutParams(-2, -2));
        box.addView(head);

        // Pendant que Krux écrit : aucune action (Enregistrer, Partager…), seulement la ligne alignée.
        if (!finished) return box;

        String[] seg = dir.split("/");
        String shortDir = seg.length > 2 ? "…/" + seg[seg.length - 2] + "/" + seg[seg.length - 1] : dir;
        TextView meta = new TextView(c);
        meta.setText(dir.isEmpty() ? "fichier créé par Krux" : shortDir);
        meta.setTextColor(text2);
        meta.setTextSize(10.5f);
        meta.setSingleLine(true);
        meta.setEllipsize(android.text.TextUtils.TruncateAt.START);
        meta.setPadding(0, Math.round(2 * d), 0, Math.round(8 * d));
        box.addView(meta);

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView save = button(c, "Enregistrer", accent, pale, d);
        TextView share = button(c, "Partager", accent, pale, d);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
        bp.rightMargin = Math.round(8 * d);
        String lower = f.name.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            TextView prev = button(c, "Visualiser", accent, pale, d);
            prev.setOnClickListener(v -> HtmlPreviewActivity.open(c, f.name, f.content));
            row.addView(prev, bp);
            bp = new LinearLayout.LayoutParams(-2, -2);
            bp.rightMargin = Math.round(8 * d);
        }
        row.addView(save, bp);
        row.addView(share);
        box.addView(row);

        save.setOnClickListener(v -> save(c, f));
        share.setOnClickListener(v -> share(c, f));
        return box;
    }

    /** Enregistre dans Téléchargements, sous le nom du fichier. */
    public static void save(Context c, FileDelivery.DFile f) {
        try {
            saveBytes(c, f.name, f.mime(), f.content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Toast.makeText(c, "Enregistrement impossible : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static void saveBytes(Context c, String name, String mime, byte[] data) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            v.put(MediaStore.Downloads.MIME_TYPE, mime);
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new Exception("insertion impossible");
            try (OutputStream os = c.getContentResolver().openOutputStream(uri)) { os.write(data); }
        } else {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!dir.exists()) dir.mkdirs();
            try (FileOutputStream os = new FileOutputStream(new File(dir, name))) { os.write(data); }
        }
        Toast.makeText(c, name + " enregistré dans Téléchargements", Toast.LENGTH_LONG).show();
    }

    /** Partage le vrai fichier (pas seulement du texte) vers une autre app. */
    public static void share(Context c, FileDelivery.DFile f) {
        try {
            shareBytes(c, f.name, f.mime(), f.content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Toast.makeText(c, "Partage impossible : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static void shareBytes(Context c, String name, String mime, byte[] data) throws Exception {
        File dir = new File(c.getCacheDir(), "shared");
        if (!dir.exists()) dir.mkdirs();
        File out = new File(dir, name);
        try (FileOutputStream os = new FileOutputStream(out)) { os.write(data); }
        Uri uri = FileProvider.getUriForFile(c, c.getPackageName() + ".fileprovider", out);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        c.startActivity(Intent.createChooser(i, "Partager " + name));
    }
}
