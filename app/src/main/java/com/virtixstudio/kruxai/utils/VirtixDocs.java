package com.virtixstudio.kruxai.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Documentation Virtix lue par Krux : le texte brut docs.txt du site est téléchargé en
 * arrière-plan (au plus toutes les 6 h), gardé sur le téléphone, puis ajouté au contexte
 * seulement quand la question parle de Virtix ou de Krux. Aucun appel réseau sur le fil principal.
 */
public final class VirtixDocs {

    public static final String SITE = "https://virtix-studio.kruxvirtix.workers.dev";
    private static final String DOCS_URL = SITE + "/docs.txt";
    private static final String PREFS = "virtix_docs";
    private static final long TTL_MS = 6L * 60 * 60 * 1000;
    private static final int MAX_CHARS = 6000;
    private static volatile boolean refreshing = false;

    private VirtixDocs() {}

    /** Vrai si la question concerne Virtix, Krux ou ses applications. */
    public static boolean concerns(String question) {
        if (question == null) return false;
        String q = Normalizer.normalize(question.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String[] keys = {"virtix", "krux", "documentation", "createur", "developpeur principal"};
        for (String k : keys) if (q.contains(k)) return true;
        return false;
    }

    /** Texte gardé sur le téléphone (vide si jamais téléchargé). Ne bloque jamais. */
    public static String cached(Context ctx) {
        return prefs(ctx).getString("text", "");
    }

    /** Télécharge docs.txt en arrière-plan si la copie a plus de 6 h. */
    public static void refreshAsync(final Context ctx) {
        final Context app = ctx.getApplicationContext();
        final SharedPreferences p = prefs(app);
        if (refreshing || System.currentTimeMillis() - p.getLong("at", 0) < TTL_MS) return;
        refreshing = true;
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(DOCS_URL).openConnection();
                    c.setConnectTimeout(6000);
                    c.setReadTimeout(6000);
                    c.setRequestProperty("User-Agent", "KruxAI/1.0");
                    if (c.getResponseCode() == 200) {
                        BufferedReader r = new BufferedReader(
                                new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                        StringBuilder sb = new StringBuilder();
                        char[] buf = new char[2048];
                        int n;
                        while ((n = r.read(buf)) > 0 && sb.length() < MAX_CHARS) sb.append(buf, 0, n);
                        r.close();
                        String text = sb.toString().trim();
                        if (!text.isEmpty()) {
                            p.edit().putString("text", text)
                                    .putLong("at", System.currentTimeMillis()).apply();
                        }
                    }
                } catch (Exception ignored) {
                    // hors ligne : on garde la copie précédente
                } finally {
                    refreshing = false;
                }
            }
        }, "virtix-docs");
        t.setDaemon(true);
        t.start();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
