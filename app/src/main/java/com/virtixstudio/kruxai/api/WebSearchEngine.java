package com.virtixstudio.kruxai.api;

import android.os.Handler;
import android.os.Looper;

import com.virtixstudio.kruxai.models.SearchResult;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class WebSearchEngine {

    private final OkHttpClient client;
    private final Handler mainHandler;

    public interface SearchCallback {
        void onSuccess(List<SearchResult> results, String formattedContext);
        void onError(String error);
    }

    public WebSearchEngine() {
        this.client = new OkHttpClient();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void search(String query, SearchCallback callback) {
        new Thread(() -> {
            try {
                String encodedQuery = URLEncoder.encode(query, "UTF-8");
                String searchUrl = "https://html.duckduckgo.com/html/?q=" + encodedQuery;

                Request request = new Request.Builder()
                        .url(searchUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String html = response.body().string();
                        Document doc = Jsoup.parse(html);
                        Elements links = doc.select("a.result__url");
                        Elements snippets = doc.select("a.result__snippet");
                        Elements titles = doc.select("h2.result__title");

                        List<SearchResult> resultsList = new ArrayList<>();
                        StringBuilder contextBuilder = new StringBuilder("=== RÉSULTATS DE RECHERCHE EN TEMPS RÉEL SUR LE WEB ===\n\n");

                        int count = Math.min(links.size(), 5);
                        for (int i = 0; i < count; i++) {
                            Element titleElem = titles.size() > i ? titles.get(i) : null;
                            Element linkElem = links.size() > i ? links.get(i) : null;
                            Element snippetElem = snippets.size() > i ? snippets.get(i) : null;

                            if (linkElem != null) {
                                String rawUrl = linkElem.attr("href");
                                // Décodage de l'URL de redirection DuckDuckGo
                                String actualUrl = rawUrl;
                                if (rawUrl.contains("uddg=")) {
                                    actualUrl = java.net.URLDecoder.decode(rawUrl.substring(rawUrl.indexOf("uddg=") + 5).split("&")[0], "UTF-8");
                                }

                                String titleText = titleElem != null ? titleElem.text() : "Source Web";
                                String snippetText = snippetElem != null ? snippetElem.text() : "";

                                SearchResult sr = new SearchResult(titleText, snippetText, actualUrl);
                                resultsList.add(sr);

                                contextBuilder.append("[").append(i + 1).append("] Titre : ").append(titleText).append("\n");
                                contextBuilder.append("    Extrait : ").append(snippetText).append("\n");
                                contextBuilder.append("    URL : ").append(actualUrl).append("\n\n");
                            }
                        }

                        String context = contextBuilder.toString();
                        mainHandler.post(() -> callback.onSuccess(resultsList, context));
                    } else {
                        mainHandler.post(() -> callback.onError("Impossible de joindre le moteur de recherche."));
                    }
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Erreur réseau search : " + e.getMessage()));
            }
        }).start();
    }
}
