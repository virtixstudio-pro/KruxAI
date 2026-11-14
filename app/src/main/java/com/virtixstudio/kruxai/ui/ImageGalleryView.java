package com.virtixstudio.kruxai.ui;

import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.virtixstudio.kruxai.utils.ImageGallery.Hit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Galerie d'images dans le chat + visionneuse plein écran avec Télécharger, Partager, Source. */
public final class ImageGalleryView {

    private ImageGalleryView() { }

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(16 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };
    private static final String UA = "KruxAI/2.10 (application Android ; Virtix Studio)";

    /** Ajoute la galerie sous les cartes de fichiers du message. */
    public static void append(LinearLayout host, List<Hit> hits) {
        if (hits == null || hits.isEmpty()) return;
        final Context c = host.getContext();
        final float d = c.getResources().getDisplayMetrics().density;
        host.setVisibility(View.VISIBLE);
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(row);
        for (final Hit h : hits) {
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable ph = new GradientDrawable();
            ph.setColor(KruxTheme.get(c, "input"));
            ph.setCornerRadius(14 * d);
            iv.setBackground(ph);
            iv.setClipToOutline(true);
            iv.setContentDescription(h.title);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (150 * d), (int) (150 * d));
            lp.rightMargin = (int) (8 * d);
            row.addView(iv, lp);
            load(h.thumb, 400, iv);
            iv.setOnClickListener(v -> showViewer(c, h));
        }
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = (int) (6 * d);
        host.addView(hs, hp);
    }

    /** Charge une image en arrière-plan, la réduit à maxPx, la garde en cache mémoire. */
    private static void load(final String url, final int maxPx, final ImageView target) {
        target.setTag(url);
        Bitmap hit = CACHE.get(url + "@" + maxPx);
        if (hit != null) { target.setImageBitmap(hit); return; }
        target.setImageDrawable(null);
        POOL.execute(() -> {
            Bitmap bmp = null;
            try {
                byte[] data = download(url);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, o);
                int sample = 1;
                while (o.outWidth / (sample * 2) >= maxPx && o.outHeight / (sample * 2) >= maxPx) sample *= 2;
                o = new BitmapFactory.Options();
                o.inSampleSize = sample;
                bmp = BitmapFactory.decodeByteArray(data, 0, data.length, o);
            } catch (Throwable ignored) { }
            final Bitmap result = bmp;
            if (result != null) CACHE.put(url + "@" + maxPx, result);
            MAIN.post(() -> { if (result != null && url.equals(target.getTag())) target.setImageBitmap(result); });
        });
    }

    private static byte[] download(String url) throws Exception {
        Request r = new Request.Builder().url(url).header("User-Agent", UA).build();
        try (Response resp = HTTP.newCall(r).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) throw new Exception("HTTP " + resp.code());
            return resp.body().bytes();
        }
    }

    private static TextView button(Context c, String label, int fill, int color, float d) {
        TextView b = new TextView(c);
        b.setText(label);
        b.setTextSize(13);
        b.setTypeface(null, Typeface.BOLD);
        b.setTextColor(color);
        b.setGravity(Gravity.CENTER);
        b.setPadding((int) (14 * d), 0, (int) (14 * d), 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(KruxTheme.radiusDp(c) * d);
        b.setBackground(g);
        b.setClickable(true);
        return b;
    }

    private static void showViewer(final Context c, final Hit h) {
        final float d = c.getResources().getDisplayMetrics().density;
        final Dialog dlg = new Dialog(c, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        int bg = KruxTheme.get(c, "bg"), tx = KruxTheme.get(c, "text"), tx2 = KruxTheme.get(c, "text2"),
                btn = KruxTheme.get(c, "button"), btnTx = KruxTheme.get(c, "buttontext"), soft = KruxTheme.get(c, "input");

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setPadding((int) (16 * d), (int) (36 * d), (int) (16 * d), (int) (16 * d));

        final ImageView iv = new ImageView(c);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(iv, new LinearLayout.LayoutParams(-1, 0, 1f));
        // aperçu net tout de suite (miniature déjà en cache), puis l'image complète
        Bitmap thumb = CACHE.get(h.thumb + "@400");
        if (thumb != null) iv.setImageBitmap(thumb);
        load(h.full, 1600, iv);

        TextView cap = new TextView(c);
        cap.setText(h.title + (h.credit().isEmpty() ? "" : "\n" + h.credit()));
        cap.setTextColor(tx2);
        cap.setTextSize(12);
        cap.setPadding(0, (int) (10 * d), 0, (int) (10 * d));
        root.addView(cap, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView dl = button(c, "Télécharger", btn, btnTx, d);
        TextView share = button(c, "Partager", soft, tx, d);
        TextView src = button(c, "Source", soft, tx, d);
        TextView close = button(c, "Fermer", soft, tx, d);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, (int) (44 * d), 1f);
        bp.rightMargin = (int) (6 * d);
        row.addView(dl, bp);
        row.addView(share, bp);
        row.addView(src, bp);
        row.addView(close, new LinearLayout.LayoutParams(0, (int) (44 * d), 1f));
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));

        dl.setOnClickListener(v -> save(c, h));
        share.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, h.title + "\n" + h.credit() + "\n" + h.page);
            c.startActivity(Intent.createChooser(i, "Partager").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        });
        src.setOnClickListener(v -> {
            try { c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(h.page)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            catch (Exception ignored) { }
        });
        close.setOnClickListener(v -> dlg.dismiss());
        dlg.setContentView(root);
        dlg.show();
    }

    /** Traitement final avant l'enregistrement. Point d'accroche du futur filigrane. */
    static byte[] finalizeImage(byte[] data) { return data; }

    private static void save(final Context c, final Hit h) {
        Toast.makeText(c, "Téléchargement…", Toast.LENGTH_SHORT).show();
        POOL.execute(() -> {
            String msg;
            try {
                byte[] data = finalizeImage(download(h.full));
                String low = h.full.toLowerCase(java.util.Locale.ROOT);
                String ext = low.contains(".png") ? "png" : low.contains(".webp") ? "webp" : "jpg";
                String mime = ext.equals("jpg") ? "image/jpeg" : "image/" + ext;
                String base = h.title.replaceAll("[^\\p{L}\\p{N}]+", "_");
                if (base.length() > 40) base = base.substring(0, 40);
                String name = "krux_" + (base.isEmpty() ? "image" : base) + "_" + System.currentTimeMillis() % 100000 + "." + ext;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                    v.put(MediaStore.Images.Media.MIME_TYPE, mime);
                    v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/KRUX");
                    Uri uri = c.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                    if (uri == null) throw new Exception("insertion impossible");
                    try (OutputStream os = c.getContentResolver().openOutputStream(uri)) { os.write(data); }
                    msg = "Image enregistrée dans Images/KRUX";
                } else {
                    File dir = c.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                    File f = new File(dir, name);
                    try (FileOutputStream os = new FileOutputStream(f)) { os.write(data); }
                    msg = "Image enregistrée : " + f.getAbsolutePath();
                }
            } catch (Exception e) {
                msg = "Téléchargement impossible : " + e.getMessage();
            }
            final String shown = msg;
            MAIN.post(() -> Toast.makeText(c, shown, Toast.LENGTH_LONG).show());
        });
    }
}
