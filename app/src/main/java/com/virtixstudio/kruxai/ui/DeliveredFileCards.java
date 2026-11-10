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
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.virtixstudio.kruxai.utils.FileDelivery;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Cartes des fichiers livrés par KRUX : nom, taille, Enregistrer, Partager. Couleurs du thème choisi. */
public final class DeliveredFileCards {

    private DeliveredFileCards() { }

    public static void fill(LinearLayout host, List<FileDelivery.DFile> files) {
        host.removeAllViews();
        if (files == null || files.isEmpty()) { host.setVisibility(View.GONE); return; }
        host.setVisibility(View.VISIBLE);
        for (FileDelivery.DFile f : files) host.addView(card(host.getContext(), f));
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

    private static View card(final Context c, final FileDelivery.DFile f) {
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

        TextView name = new TextView(c);
        name.setText("📄  " + f.name);
        name.setTextColor(text);
        name.setTextSize(14);
        name.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        box.addView(name);

        int b = f.bytes();
        TextView meta = new TextView(c);
        meta.setText((b < 1024 ? b + " o" : String.format(java.util.Locale.FRANCE, "%.1f Ko", b / 1024.0)) + " · fichier créé par Krux");
        meta.setTextColor(text2);
        meta.setTextSize(11);
        meta.setPadding(0, Math.round(2 * d), 0, Math.round(8 * d));
        box.addView(meta);

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView save = button(c, "Enregistrer", accent, pale, d);
        TextView share = button(c, "Partager", accent, pale, d);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
        bp.rightMargin = Math.round(8 * d);
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
            byte[] data = f.content.getBytes(StandardCharsets.UTF_8);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, f.name);
                v.put(MediaStore.Downloads.MIME_TYPE, f.mime());
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) throw new Exception("insertion impossible");
                try (OutputStream os = c.getContentResolver().openOutputStream(uri)) { os.write(data); }
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                try (FileOutputStream os = new FileOutputStream(new File(dir, f.name))) { os.write(data); }
            }
            Toast.makeText(c, f.name + " enregistré dans Téléchargements", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(c, "Enregistrement impossible : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** Partage le vrai fichier (pas seulement du texte) vers une autre app. */
    public static void share(Context c, FileDelivery.DFile f) {
        try {
            File dir = new File(c.getCacheDir(), "shared");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, f.name);
            try (FileOutputStream os = new FileOutputStream(out)) { os.write(f.content.getBytes(StandardCharsets.UTF_8)); }
            Uri uri = FileProvider.getUriForFile(c, c.getPackageName() + ".fileprovider", out);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(f.mime());
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            c.startActivity(Intent.createChooser(i, "Partager " + f.name));
        } catch (Exception e) {
            Toast.makeText(c, "Partage impossible : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
