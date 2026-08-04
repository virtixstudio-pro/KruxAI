package com.virtixstudio.kruxai.api;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class WebSearchService {

    private static final OkHttpClient client = new OkHttpClient();

    public interface SearchCallback {
        void onSuccess(String searchResultsContext);
        void onError(String errorMessage);
    }

    // Utilisation de l'API Tavily pour la recherche temps réel
    public static void performSearch(String query, String apiKey, SearchCallback callback) {
        if (apiKey == null || apiKey.isEmpty()) {
            callback.onError("Clé API Tavily manquante pour la recherche web.");
            return;
        }

        MediaType mediaType = MediaType.parse("application/json");
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("api_key", apiKey);
            jsonBody.put("query", query);
            jsonBody.put("search_depth", "basic");
            jsonBody.put("max_results", 5);
        } catch (Exception e) {
            callback.onError(e.getMessage());
            return;
        }

        RequestBody body = RequestBody.create(jsonBody.toString(), mediaType);
        Request request = new Request.Builder()
                .url("https://api.tavily.com/search")
                .post(body)
                .addHeader("content-type", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Échec de la recherche : " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onError("Erreur serveur de recherche : " + response.code());
                    return;
                }

                try {
                    String responseData = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseData);
                    JSONArray results = jsonResponse.getJSONArray("results");

                    StringBuilder context = new StringBuilder("Résultats de recherche en temps réel sur le Web :\n\n");
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject item = results.getJSONObject(i);
                        context.append("- **").append(item.getString("title")).append("**\n");
                        context.append("  Lien : ").append(item.getString("url")).append("\n");
                        context.append("  Extrait : ").append(item.getString("content")).append("\n\n");
                    }

                    callback.onSuccess(context.toString());
                } catch (Exception e) {
                    callback.onError("Erreur lors du traitement des résultats : " + e.getMessage());
                }
            }
        });
    }
}
