package com.virtixstudio.kruxai.ui;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Environment;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Recherche d'images et de vidéos libres de droits (Pexels) + galerie avec téléchargement. */
public final class MediaGallery {
    private MediaGallery() {}

    static class Item {
        boolean video;
        String thumb, url, credit, page;
    }

    private static String key(Context c) {
        try {
            InputStream is = c.getAssets().open("krux_media.json");
            byte[] buf = new byte[is.available()];
            is.read(buf);
            is.close();
            return new JSONObject(new String(buf, StandardCharsets.UTF_8)).optString("pexels_key", "").trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static List<Item> search(String key, boolean video, String query) throws Exception {
        String q = URLEncoder.encode(query, "UTF-8");
        String url = video
                ? "https://api.pexels.com/videos/search?per_page=6&query=" + q
                : "https://api.pexels.com/v1/search?per_page=8&query=" + q;
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("Authorization", key);
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        int code = c.getResponseCode();
        InputStream is = code < 400 ? c.getInputStream() : c.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (is != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String l;
                while ((l = br.readLine()) != null) sb.append(l);
            }
        }
        if (code >= 400) throw new Exception("Pexels HTTP " + code);
        JSONObject o = new JSONObject(sb.toString());
        List<Item> out = new ArrayList<>();
        if (!video) {
            JSONArray arr = o.optJSONArray("photos");
            for (int i = 0; arr != null && i < arr.length(); i++) {
                JSONObject p = arr.getJSONObject(i);
                JSONObject src = p.optJSONObject("src");
                if (src == null) continue;
                Item it = new Item();
                it.thumb = src.optString("medium");
                it.url = src.optString("large2x", src.optString("large"));
                it.credit = p.optString("photographer");
                it.page = p.optString("url");
                out.add(it);
            }
        } else {
            JSONArray arr = o.optJSONArray("videos");
            for (int i = 0; arr != null && i < arr.length(); i++) {
                JSONObject v = arr.getJSONObject(i);
                JSONArray files = v.optJSONArray("video_files");
                String best = null;
                int bestW = -1;
                for (int j = 0; files != null && j < files.length(); j++) {
                    JSONObject f = files.getJSONObject(j);
                    if (!"video/mp4".equals(f.optString("file_type"))) continue;
                    int w = f.optInt("width", 0);
                    if (w <= 1280 && w > bestW) {
                        bestW = w;
                        best = f.optString("link");
                    }
                }
                if (best == null && files != null && files.length() > 0) best = files.getJSONObject(0).optString("link");
                if (best == null || best.isEmpty()) continue;
                Item it = new Item();
                it.video = true;
                it.thumb = v.optString("image");
                it.url = best;
                JSONObject user = v.optJSONObject("user");
                it.credit = user != null ? user.optString("name") : "";
                it.page = v.optString("url");
                out.add(it);
            }
        }
        return out;
    }

    public static void show(final Activity a, final boolean video, final String query) {
        final String k = key(a);
        if (k.isEmpty()) {
            Toast.makeText(a, "La recherche d'images n'est pas configurée dans cette version de KRUX.", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(a, video ? "Recherche de vidéos…" : "Recherche d'images…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                final List<Item> items = search(k, video, query);
                a.runOnUiThread(() -> {
                    if (items.isEmpty()) {
                        Toast.makeText(a, "Aucun résultat pour « " + query + " ».", Toast.LENGTH_LONG).show();
                    } else {
                        display(a, video, query, items);
                    }
                });
            } catch (final Exception e) {
                a.runOnUiThread(() -> Toast.makeText(a, "Recherche impossible : " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private static void display(final Activity a, boolean video, String query, List<Item> items) {
        final float d = a.getResources().getDisplayMetrics().density;
        int bg = KruxTheme.get(a, "bg"), text = KruxTheme.get(a, "text"), text2 = KruxTheme.get(a, "text2"), accent = KruxTheme.get(a, "accent"), surface = KruxTheme.get(a, "input");

        BottomSheetDialog dialog = new BottomSheetDialog(a);
        ScrollView sv = new ScrollView(a);
        sv.setBackgroundColor(bg);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (16 * d), (int) (16 * d), (int) (16 * d), (int) (24 * d));
        sv.addView(root);

        TextView title = new TextView(a);
        title.setText((video ? "Vidéos : " : "Images : ") + query);
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(text);
        root.addView(title);

        TextView credit = new TextView(a);
        credit.setText("Libres de droits, fournis par Pexels (pexels.com)");
        credit.setTextSize(12);
        credit.setTextColor(accent);
        credit.setPadding(0, (int) (4 * d), 0, (int) (12 * d));
        credit.setOnClickListener(v -> a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.pexels.com"))));
        root.addView(credit);

        for (int i = 0; i < items.size(); i += 2) {
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = 0; j < 2; j++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                lp.setMargins((int) (4 * d), (int) (4 * d), (int) (4 * d), (int) (8 * d));
                if (i + j < items.size()) {
                    row.addView(card(a, items.get(i + j), surface, text2, d), lp);
                } else {
                    row.addView(new android.view.View(a), lp);
                }
            }
            root.addView(row);
        }
        dialog.setContentView(sv);
        dialog.show();
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    private static LinearLayout card(final Activity a, final Item it, int surface, int text2, float d) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(surface);
        gd.setCornerRadius(12 * d);
        card.setBackground(gd);
        card.setClipToOutline(true);

        final ImageView iv = new ImageView(a);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackgroundColor(0x22888888);
        iv.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) (150 * d)));
        iv.setOnClickListener(v -> a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(it.page))));
        card.addView(iv);
        loadThumb(a, iv, it.thumb);

        TextView by = new TextView(a);
        by.setText((it.video ? "Vidéo · " : "Photo · ") + (it.credit == null || it.credit.isEmpty() ? "Pexels" : it.credit));
        by.setTextSize(11);
        by.setTextColor(text2);
        by.setPadding((int) (8 * d), (int) (6 * d), (int) (8 * d), 0);
        by.setSingleLine(true);
        card.addView(by);

        Button dl = new Button(a);
        dl.setText("Télécharger");
        dl.setAllCaps(false);
        dl.setOnClickListener(v -> download(a, it));
        card.addView(dl);
        return card;
    }

    private static void loadThumb(final Activity a, final ImageView iv, final String url) {
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                final Bitmap b = BitmapFactory.decodeStream(c.getInputStream());
                if (b != null) a.runOnUiThread(() -> iv.setImageBitmap(b));
            } catch (Exception ignored) { }
        }).start();
    }

    private static void download(Context c, Item it) {
        try {
            String name = "krux-" + System.currentTimeMillis() + (it.video ? ".mp4" : ".jpg");
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(it.url));
            r.setTitle(name);
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            ((DownloadManager) c.getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(r);
            Toast.makeText(c, "Téléchargement lancé : dossier Téléchargements", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(c, "Téléchargement impossible : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
