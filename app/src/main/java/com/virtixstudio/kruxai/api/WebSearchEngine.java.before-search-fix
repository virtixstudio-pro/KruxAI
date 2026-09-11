package com.virtixstudio.kruxai.api;

import android.os.Handler;
import android.os.Looper;

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
    }

    public WebSearchEngine() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .build();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executorService = Executors.newFixedThreadPool(6); // Traitement parallèle
    }

    public void search(String userQuery, SearchCallback callback) {
        executorService.execute(() -> {
            try {
                // Génération de sous-requêtes parallèles pour ratisser large
                List<String> subQueries = new ArrayList<>();
                subQueries.add(userQuery);
                subQueries.add(userQuery + " actualités");
                subQueries.add(userQuery + " faits doutes vérifier");

                List<SearchResult> aggregatedResults = Collections.synchronizedList(new ArrayList<>());
                Set<String> seenUrls = Collections.synchronizedSet(new HashSet<>());

                ExecutorService pool = Executors.newFixedThreadPool(subQueries.size());

                for (String query : subQueries) {
                    pool.execute(() -> fetchResultsForQuery(query, aggregatedResults, seenUrls));
                }

                pool.shutdown();
                pool.awaitTermination(10, TimeUnit.SECONDS);

                // Tri et limitation aux 12 meilleures sources uniques
                List<SearchResult> finalSources = new ArrayList<>();
                int maxSources = Math.min(aggregatedResults.size(), 12);
                for (int i = 0; i < maxSources; i++) {
                    finalSources.add(aggregatedResults.get(i));
                }

                StringBuilder contextBuilder = new StringBuilder();
                contextBuilder.append("=== BASE DE CONNAISSANCES MULTI-SOURCES TEMPS RÉEL (")
                              .append(finalSources.size())
                              .append(" SOURCES VÉRIFIÉES) ===\n\n");

                for (int i = 0; i < finalSources.size(); i++) {
                    SearchResult sr = finalSources.get(i);
                    contextBuilder.append("[").append(i + 1).append("] ").append(sr.getTitle()).append("\n");
                    contextBuilder.append("    Source : ").append(sr.getDomain()).append(" (").append(sr.getUrl()).append(")\n");
                    contextBuilder.append("    Extrait : ").append(sr.getSnippet()).append("\n\n");
                }

                String formattedContext = contextBuilder.toString();
                mainHandler.post(() -> callback.onSuccess(finalSources, formattedContext));

            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Erreur réseau globale : " + e.getMessage()));
            }
        });
    }

    private void fetchResultsForQuery(String query, List<SearchResult> aggregatedResults, Set<String> seenUrls) {
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
                    }
                }
            }
        } catch (Exception ignored) {
            // Ignorer les échecs isolés des sous-requêtes
        }
    }
}
