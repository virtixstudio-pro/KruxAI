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
    private static final ExecutorService POOL = Executors.newFixedThreadPool(6);
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
            android.widget.FrameLayout tile = new android.widget.FrameLayout(c);
            GradientDrawable ph = new GradientDrawable();
            ph.setColor(KruxTheme.get(c, "input"));
            ph.setCornerRadius(16 * d);
            tile.setBackground(ph);
            tile.setClipToOutline(true);
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setContentDescription(h.title);
            tile.addView(iv, new android.widget.FrameLayout.LayoutParams(-1, -1));
            android.widget.ProgressBar spin = new android.widget.ProgressBar(c);
            spin.setIndeterminate(true);
            spin.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(KruxTheme.get(c, "text2")));
            tile.addView(spin, new android.widget.FrameLayout.LayoutParams((int) (22 * d), (int) (22 * d), Gravity.CENTER));
            TextView fail = new TextView(c);
            fail.setText("Image\nindisponible");
            fail.setGravity(Gravity.CENTER);
            fail.setTextSize(11);
            fail.setTextColor(KruxTheme.get(c, "text2"));
            fail.setVisibility(View.GONE);
            tile.addView(fail, new android.widget.FrameLayout.LayoutParams(-1, -1));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (128 * d), (int) (128 * d));
            lp.rightMargin = (int) (8 * d);
            row.addView(tile, lp);
            load(h.thumb, 400, iv, h.full, spin, fail);
            tile.setOnClickListener(v -> showViewer(c, h));
        }
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = (int) (6 * d);
        host.addView(hs, hp);
        TextView note = new TextView(c);
        note.setText(hits.size() + " images · " + hits.get(0).source + " · touche pour agrandir");
        note.setTextSize(11);
        note.setTextColor(KruxTheme.get(c, "text2"));
        note.setPadding((int) (2 * d), (int) (6 * d), 0, 0);
        host.addView(note);
    }

    /** Charge une image en arrière-plan (miniature, puis l'image d'origine réduite si la miniature échoue). */
    private static void load(final String url, final int maxPx, final ImageView target, final String fallback,
                             final View spinner, final View failed) {
        target.setTag(url);
        Bitmap hit = CACHE.get(url + "@" + maxPx);
        if (hit != null) { target.setImageBitmap(hit); if (spinner != null) spinner.setVisibility(View.GONE); return; }
        target.setImageDrawable(null);
        POOL.execute(() -> {
            Bitmap bmp = fetch(url, maxPx);
            String used = url;
            if (bmp == null && fallback != null && !fallback.equals(url)) { bmp = fetch(fallback, maxPx); used = fallback; }
            final Bitmap result = bmp;
            if (result != null) CACHE.put(url + "@" + maxPx, result);
            MAIN.post(() -> {
                if (spinner != null) spinner.setVisibility(View.GONE);
                if (result == null) { if (failed != null) failed.setVisibility(View.VISIBLE); return; }
                if (url.equals(target.getTag())) {
                    target.setAlpha(0f);
                    target.setImageBitmap(result);
                    target.animate().alpha(1f).setDuration(200).start();
                }
            });
        });
    }

    private static Bitmap fetch(String url, int maxPx) {
        try {
            byte[] data = download(url);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= maxPx && o.outHeight / (sample * 2) >= maxPx) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o);
        } catch (Throwable t) {
            return null;
        }
    }

    private static byte[] download(String url) throws Exception {
        Request r = new Request.Builder().url(url).header("User-Agent", UA).build();
        try (Response resp = HTTP.newCall(r).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) throw new Exception("HTTP " + resp.code());
            return resp.body().bytes();
        }
    }

    private static ImageView iconButton(Context c, int res, String desc, float d) {
        ImageView b = new ImageView(c);
        b.setImageResource(res);
        b.setColorFilter(Color.WHITE);
        b.setContentDescription(desc);
        b.setPadding((int) (10 * d), (int) (10 * d), (int) (10 * d), (int) (10 * d));
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0x66000000);
        b.setBackground(g);
        b.setClickable(true);
        return b;
    }

    private static void copyText(Context c, String label, String text) {
        android.content.ClipboardManager cm = (android.content.ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text));
        Toast.makeText(c, "Copié", Toast.LENGTH_SHORT).show();
    }

    private static void showViewer(final Context c, final Hit h) {
        final float d = c.getResources().getDisplayMetrics().density;
        final Dialog dlg = new Dialog(c, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final android.widget.FrameLayout root = new android.widget.FrameLayout(c);
        root.setBackgroundColor(0xFF0A0A0D);

        final ZoomImageView iv = new ZoomImageView(c);
        root.addView(iv, new android.widget.FrameLayout.LayoutParams(-1, -1));
        Bitmap thumb = CACHE.get(h.thumb + "@400");
        if (thumb != null) iv.setImageBitmap(thumb);
        loadInto(h.full, 1800, iv);

        // légende en bas, discrète
        final TextView cap = new TextView(c);
        cap.setText(h.title + (h.credit().isEmpty() ? "" : "\n" + h.credit()));
        cap.setTextColor(0xFFE5E7EB);
        cap.setTextSize(12);
        cap.setPadding((int) (18 * d), (int) (28 * d), (int) (18 * d), (int) (22 * d));
        cap.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xCC000000, 0x00000000}));
        android.widget.FrameLayout.LayoutParams cp = new android.widget.FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        root.addView(cap, cp);

        // barre du haut : retour à gauche ; télécharger, partager, trois points à droite
        final LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding((int) (12 * d), (int) (34 * d), (int) (12 * d), (int) (8 * d));
        ImageView back = iconButton(c, com.virtixstudio.kruxai.R.drawable.ic_back_arrow, "Retour", d);
        ImageView dl = iconButton(c, com.virtixstudio.kruxai.R.drawable.ic_download, "Télécharger", d);
        ImageView share = iconButton(c, com.virtixstudio.kruxai.R.drawable.ic_share, "Partager", d);
        ImageView more = iconButton(c, com.virtixstudio.kruxai.R.drawable.ic_more, "Plus d'options", d);
        int sz = (int) (44 * d);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(sz, sz);
        ip.leftMargin = (int) (8 * d);
        bar.addView(back, new LinearLayout.LayoutParams(sz, sz));
        View spacer = new View(c);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        bar.addView(dl, ip);
        bar.addView(share, ip);
        bar.addView(more, ip);
        root.addView(bar, new android.widget.FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        // un appui sur l'image cache ou montre les barres
        iv.setOnSingleTap(() -> {
            boolean show = bar.getAlpha() < 0.5f;
            bar.animate().alpha(show ? 1f : 0f).setDuration(160).start();
            cap.animate().alpha(show ? 1f : 0f).setDuration(160).start();
        });

        back.setOnClickListener(v -> dlg.dismiss());
        dl.setOnClickListener(v -> save(c, h));
        share.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, h.title + "\n" + h.credit() + "\n" + h.page);
            c.startActivity(Intent.createChooser(i, "Partager").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        });
        more.setOnClickListener(v -> {
            LinearLayout box = new LinearLayout(c);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding((int) (6 * d), (int) (6 * d), (int) (6 * d), (int) (6 * d));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFF1B1B22);
            bg.setCornerRadius(16 * d);
            box.setBackground(bg);
            final android.widget.PopupWindow pw = new android.widget.PopupWindow(box, (int) (250 * d), ViewGroup.LayoutParams.WRAP_CONTENT, true);
            pw.setElevation(12 * d);
            pw.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            String[] names = {"Voir la source", "Copier le lien", "Copier le crédit", "Définir comme fond d'écran"};
            Runnable[] acts = {
                () -> { try { c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(h.page)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) { } },
                () -> copyText(c, "Lien", h.page),
                () -> copyText(c, "Crédit", h.title + " — " + h.credit()),
                () -> setWallpaper(c, h)
            };
            for (int k = 0; k < names.length; k++) {
                TextView t = new TextView(c);
                t.setText(names[k]);
                t.setTextSize(15);
                t.setTextColor(0xFFF3F4F6);
                t.setGravity(Gravity.CENTER_VERTICAL);
                t.setPadding((int) (14 * d), 0, (int) (14 * d), 0);
                final Runnable act = acts[k];
                t.setOnClickListener(x -> { pw.dismiss(); act.run(); });
                box.addView(t, new LinearLayout.LayoutParams(-1, (int) (48 * d)));
            }
            pw.showAsDropDown(more, 0, (int) (4 * d), Gravity.END);
        });

        dlg.setContentView(root);
        dlg.show();
    }

    private static void setWallpaper(final Context c, final Hit h) {
        Toast.makeText(c, "Je prépare le fond d'écran…", Toast.LENGTH_SHORT).show();
        POOL.execute(() -> {
            String msg;
            try {
                byte[] data = download(h.full);
                Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length);
                if (b == null) throw new Exception("image illisible");
                android.app.WallpaperManager.getInstance(c).setBitmap(b);
                msg = "Fond d'écran défini";
            } catch (Throwable e) {
                msg = "Impossible de définir le fond d'écran";
            }
            final String shown = msg;
            MAIN.post(() -> Toast.makeText(c, shown, Toast.LENGTH_LONG).show());
        });
    }

    /** Comme load(), mais pour la visionneuse zoomable (la matrice est recalculée à chaque image). */
    private static void loadInto(final String url, final int maxPx, final ZoomImageView target) {
        Bitmap hit = CACHE.get(url + "@" + maxPx);
        if (hit != null) { target.setImageBitmap(hit); return; }
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
            MAIN.post(() -> { if (result != null) target.setImageBitmap(result); });
        });
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
