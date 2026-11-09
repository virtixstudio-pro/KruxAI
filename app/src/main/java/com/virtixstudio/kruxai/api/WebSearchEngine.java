package com.virtixstudio.kruxai.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.virtixstudio.kruxai.models.SearchResult;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class WebSearchEngine {

    private final OkHttpClient client;
    private final Handler mainHandler;
    private final ExecutorService executorService;

    public interface SearchCallback {
        void onSuccess(List<SearchResult> results, String formattedContext);
        void onError(String error);

        /** Étapes en direct (pour le terminal de KRUX) : requêtes lancées, pages lues. Appelé sur le thread principal. */
        default void onProgress(String line) { }
    }

    public WebSearchEngine() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .build();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executorService = Executors.newFixedThreadPool(6); // Traitement parallèle
    }

    private void progress(SearchCallback cb, String line) {
        mainHandler.post(() -> cb.onProgress(line));
    }

    public void search(String userQuery, SearchCallback callback) {
        executorService.execute(() -> {
            try {
                final int year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
                final List<String> subQueries = SearchRanker.subQueries(userQuery, year);
                if (subQueries.isEmpty()) subQueries.add(userQuery);
                final List<String> kw = SearchRanker.keywords(userQuery);

                final List<SearchResult> aggregatedResults = Collections.synchronizedList(new ArrayList<>());
                final Set<String> seenUrls = Collections.synchronizedSet(new HashSet<>());

                ExecutorService pool = Executors.newFixedThreadPool(subQueries.size());
                for (final String query : subQueries) {
                    progress(callback, "search \"" + query + "\"");
                    pool.execute(() -> {
                        int n = fetchResultsForQuery(query, aggregatedResults, seenUrls);
                        progress(callback, "  " + n + " résultat" + (n > 1 ? "s" : "") + " pour \"" + query + "\"");
                    });
                }
                pool.shutdown();
                pool.awaitTermination(10, TimeUnit.SECONDS);

                if (aggregatedResults.isEmpty()) {
                    progress(callback, "  moteur principal vide, j'essaie Wikipédia");
                    fetchWikipedia(userQuery, aggregatedResults, seenUrls);
                }

                // Classement par pertinence, 2 pages maximum par site, 8 sources au plus
                List<SearchResult> all;
                synchronized (aggregatedResults) { all = new ArrayList<>(aggregatedResults); }
                List<String[]> raw = new ArrayList<>();
                for (SearchResult r : all) raw.add(new String[]{r.getTitle(), r.getSnippet(), r.getUrl()});
                final List<SearchResult> finalSources = new ArrayList<>();
                for (int idx : SearchRanker.rank(raw, kw, 2, 8)) finalSources.add(all.get(idx));

                if (finalSources.isEmpty()) {
                    mainHandler.post(() ->
                            callback.onError("Recherche Web indisponible : aucune source n'a pu être récupérée."));
                    return;
                }
                progress(callback, "  " + finalSources.size() + " sources retenues");

                // Lecture des 3 meilleures pages (plus riche que de simples extraits)
                final String[] pageText = new String[Math.min(3, finalSources.size())];
                ExecutorService reader = Executors.newFixedThreadPool(pageText.length);
                for (int i = 0; i < pageText.length; i++) {
                    final int k = i;
                    reader.execute(() -> {
                        SearchResult sr = finalSources.get(k);
                        progress(callback, "read " + sr.getDomain());
                        pageText[k] = readPage(sr.getUrl());
                    });
                }
                reader.shutdown();
                reader.awaitTermination(8, TimeUnit.SECONDS);

                StringBuilder contextBuilder = new StringBuilder();
                contextBuilder.append("=== RÉSULTATS DE RECHERCHE WEB EN TEMPS RÉEL (")
                              .append(finalSources.size()).append(" sources, classées par pertinence) ===\n")
                              .append("Appuie-toi sur ces sources, cite-les par leur numéro [n], et signale ce qui est incertain.\n\n");
                for (int i = 0; i < finalSources.size(); i++) {
                    SearchResult sr = finalSources.get(i);
                    contextBuilder.append("[").append(i + 1).append("] ").append(sr.getTitle()).append("\n");
                    contextBuilder.append("    Source : ").append(sr.getDomain()).append(" (").append(sr.getUrl()).append(")\n");
                    contextBuilder.append("    Extrait : ").append(sr.getSnippet()).append("\n");
                    if (i < pageText.length && pageText[i] != null && !pageText[i].isEmpty()) {
                        contextBuilder.append("    Contenu de la page : ").append(pageText[i]).append("\n");
                    }
                    contextBuilder.append("\n");
                }

                final String formattedContext = contextBuilder.toString();
                mainHandler.post(() -> callback.onSuccess(finalSources, formattedContext));

            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Erreur réseau globale : " + e.getMessage()));
            }
        });
    }

    /** Texte principal d'une page (2000 caractères au plus), ou "" si illisible. */
    private String readPage(String url) {
        try {
            Request request = new Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                    .build();
            OkHttpClient fast = client.newBuilder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).build();
            try (Response r = fast.newCall(request).execute()) {
                String type = r.header("Content-Type", "");
                if (!r.isSuccessful() || r.body() == null || (type != null && !type.contains("text/html"))) return "";
                String html = r.peekBody(400_000).string();
                Document doc = Jsoup.parse(html, url);
                doc.select("script,style,nav,footer,header,aside,form,noscript,iframe").remove();
                StringBuilder sb = new StringBuilder();
                for (Element p : doc.select("p, h1, h2, h3, li")) {
                    String t = p.text().trim();
                    if (t.length() < 40) continue;
                    sb.append(t).append(' ');
                    if (sb.length() > 2000) break;
                }
                return sb.length() > 2000 ? sb.substring(0, 2000) : sb.toString().trim();
            }
        } catch (Exception e) {
            return "";
        }
    }

    /** Secours si le moteur principal ne répond pas : l'API de recherche de Wikipédia (gratuite, stable). */
    private void fetchWikipedia(String query, List<SearchResult> out, Set<String> seen) {
        try {
            String u = "https://fr.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=5&utf8=1&srsearch="
                    + URLEncoder.encode(query, "UTF-8");
            Request request = new Request.Builder().url(u).header("User-Agent", "KruxAI/1.0 (Android)").build();
            try (Response r = client.newCall(request).execute()) {
                if (!r.isSuccessful() || r.body() == null) return;
                org.json.JSONArray arr = new org.json.JSONObject(r.body().string()).getJSONObject("query").getJSONArray("search");
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject o = arr.getJSONObject(i);
                    String title = o.getString("title");
                    String url = "https://fr.wikipedia.org/wiki/" + URLEncoder.encode(title.replace(' ', '_'), "UTF-8");
                    if (seen.add(url)) out.add(new SearchResult(title, Jsoup.parse(o.optString("snippet", "")).text(), url));
                }
            }
        } catch (Exception e) {
            Log.e("KRUX_WEB_SEARCH", "Wikipédia : " + e.getMessage());
        }
    }

    private int fetchResultsForQuery(String query, List<SearchResult> aggregatedResults, Set<String> seenUrls) {
        int added = 0;
        try {
            String encodedQuery = URLEncoder.encode(query, "UTF-8");
            String searchUrl = "https://html.duckduckgo.com/html/?q=" + encodedQuery;

            Request request = new Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String html = response.body().string();
                    Document doc = Jsoup.parse(html);
                    Elements links = doc.select("a.result__url");
                    Elements snippets = doc.select("a.result__snippet");
                    Elements titles = doc.select("h2.result__title");

                    for (int i = 0; i < links.size(); i++) {
                        Element linkElem = links.get(i);
                        Element titleElem = titles.size() > i ? titles.get(i) : null;
                        Element snippetElem = snippets.size() > i ? snippets.get(i) : null;

                        String rawUrl = linkElem.attr("href");
                        String actualUrl = rawUrl;
                        if (rawUrl.contains("uddg=")) {
                            actualUrl = URLDecoder.decode(rawUrl.substring(rawUrl.indexOf("uddg=") + 5).split("&")[0], "UTF-8");
                        }

                        if (!actualUrl.startsWith("http") || seenUrls.contains(actualUrl)) {
                            continue;
                        }

                        seenUrls.add(actualUrl);

                        String titleText = titleElem != null ? titleElem.text() : "Source Web";
                        String snippetText = snippetElem != null ? snippetElem.text() : "";

                        SearchResult result = new SearchResult(titleText, snippetText, actualUrl);
                        aggregatedResults.add(result);
                        added++;
                    }
                }
            }
        } catch (Exception e) {
            Log.e("KRUX_WEB_SEARCH",
                    "Échec sous-requête: " + query +
                    " | " + e.getClass().getName() +
                    " | " + e.getMessage(), e);
        }
        return added;
    }
}
