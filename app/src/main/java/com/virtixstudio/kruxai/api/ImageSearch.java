package com.virtixstudio.kruxai.api;

import com.virtixstudio.kruxai.utils.ImageGallery.Hit;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Recherche d'images libres : Openverse puis Wikimedia Commons. Aucune clé, aucun compte. */
public final class ImageSearch {

    private ImageSearch() { }

    public interface Callback { void onResult(List<Hit> hits); void onError(String message); }

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build();
    private static final String UA = "KruxAI/2.10 (application Android ; Virtix Studio)";
    private static final int MAX = 8;

    /** Openverse et Wikimedia sont interrogés en même temps : on répond dès qu'une source donne assez d'images. */
    public static void search(final String query, final Callback cb) {
        final Object lock = new Object();
        final List<Hit>[] res = new List[]{null, null};   // 0 : Openverse, 1 : Wikimedia (null = pas encore fini)
        Thread t1 = new Thread(() -> {
            List<Hit> r = new ArrayList<>();
            try { r = openverse(query); } catch (Exception ignored) { }
            synchronized (lock) { res[0] = r; lock.notifyAll(); }
        }, "krux-img-openverse");
        Thread t2 = new Thread(() -> {
            List<Hit> r = new ArrayList<>();
            try { r = wikimedia(query); } catch (Exception ignored) { }
            synchronized (lock) { res[1] = r; lock.notifyAll(); }
        }, "krux-img-wikimedia");
        t1.start();
        t2.start();
        new Thread(() -> {
            List<Hit> out = new ArrayList<>();
            long end = System.currentTimeMillis() + 10000;
            synchronized (lock) {
                while (true) {
                    boolean ov = res[0] != null, wk = res[1] != null;
                    if (ov && res[0].size() >= 4) { out.addAll(res[0]); break; }
                    if (wk && res[1].size() >= 4) { out.addAll(res[1]); break; }
                    if (ov && wk) { out.addAll(res[0]); out.addAll(res[1]); break; }
                    long left = end - System.currentTimeMillis();
                    if (left <= 0) {
                        if (ov) out.addAll(res[0]);
                        if (wk) out.addAll(res[1]);
                        break;
                    }
                    try { lock.wait(left); } catch (InterruptedException e) { break; }
                }
            }
            if (out.size() > MAX) out = new ArrayList<>(out.subList(0, MAX));
            if (out.isEmpty()) cb.onError("aucune image trouvée"); else cb.onResult(out);
        }, "krux-image-search").start();
    }

    private static String get(String url) throws Exception {
        Request r = new Request.Builder().url(url).header("User-Agent", UA).build();
        try (Response resp = HTTP.newCall(r).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) throw new Exception("HTTP " + resp.code());
            return resp.body().string();
        }
    }

    private static List<Hit> openverse(String q) throws Exception {
        String url = "https://api.openverse.org/v1/images/?q=" + URLEncoder.encode(q, "UTF-8")
                + "&page_size=" + MAX + "&mature=false";
        JSONArray arr = new JSONObject(get(url)).optJSONArray("results");
        List<Hit> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            String full = o.optString("url", "");
            String thumb = o.optString("thumbnail", full);
            if (!full.startsWith("https")) continue;
            String lic = o.optString("license", "").toUpperCase(java.util.Locale.ROOT);
            String ver = o.optString("license_version", "");
            out.add(new Hit(thumb, full, o.optString("title", q), o.optString("creator", ""),
                    lic.isEmpty() ? "" : ("CC " + lic + (ver.isEmpty() ? "" : " " + ver)),
                    o.optString("foreign_landing_url", full), "Openverse"));
        }
        return out;
    }

    private static List<Hit> wikimedia(String q) throws Exception {
        String url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search&gsrnamespace=6&gsrlimit="
                + MAX + "&gsrsearch=" + URLEncoder.encode(q + " filetype:bitmap", "UTF-8")
                + "&prop=imageinfo&iiprop=url%7Cextmetadata&iiurlwidth=400";
        JSONObject pages = new JSONObject(get(url)).optJSONObject("query");
        List<Hit> out = new ArrayList<>();
        if (pages == null || pages.optJSONObject("pages") == null) return out;
        JSONObject all = pages.getJSONObject("pages");
        java.util.Iterator<String> it = all.keys();
        while (it.hasNext()) {
            JSONObject p = all.getJSONObject(it.next());
            JSONArray ii = p.optJSONArray("imageinfo");
            if (ii == null || ii.length() == 0) continue;
            JSONObject info = ii.getJSONObject(0);
            String full = info.optString("url", "");
            String low = full.toLowerCase(java.util.Locale.ROOT);
            if (!(low.endsWith(".jpg") || low.endsWith(".jpeg") || low.endsWith(".png") || low.endsWith(".webp"))) continue;
            JSONObject meta = info.optJSONObject("extmetadata");
            String artist = meta == null || meta.optJSONObject("Artist") == null ? "" : meta.getJSONObject("Artist").optString("value", "");
            String lic = meta == null || meta.optJSONObject("LicenseShortName") == null ? "" : meta.getJSONObject("LicenseShortName").optString("value", "");
            String title = p.optString("title", q).replaceFirst("^File:", "").replaceFirst("\\.[A-Za-z]{3,4}$", "");
            out.add(new Hit(info.optString("thumburl", full), full, title, artist, lic,
                    info.optString("descriptionurl", full), "Wikimedia Commons"));
        }
        return out;
    }
}
