package com.virtixstudio.kruxai.api;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TavilyApiClient {

    private static final String TAVILY_URL = "https://api.tavily.com/search";
    private final String apiKey;
    private final OkHttpClient client;
    private final Handler mainHandler;

    public interface SearchCallback {
        void onSuccess(String searchContext);
        void onError(String error);
    }

    public TavilyApiClient(String apiKey) {
        this.apiKey = apiKey;
        this.client = new OkHttpClient();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void searchWeb(String query, SearchCallback callback) {
        new Thread(() -> {
            try {
                JSONObject jsonBody = new JSONObject();
                jsonBody.put("api_key", apiKey);
                jsonBody.put("query", query);
                jsonBody.put("search_depth", "basic");
                jsonBody.put("max_results", 3);

                RequestBody body = RequestBody.create(
                        jsonBody.toString(),
                        MediaType.get("application/json; charset=utf-8")
                );

                Request request = new Request.Builder()
                        .url(TAVILY_URL)
                        .post(body)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseData = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseData);
                        JSONArray results = jsonResponse.optJSONArray("results");

                        StringBuilder contextBuilder = new StringBuilder("Résultats de recherche Web en temps réel :\n");
                        if (results != null) {
                            for (int i = 0; i < results.length(); i++) {
                                JSONObject item = results.getJSONObject(i);
                                contextBuilder.append("- ").append(item.optString("title"))
                                        .append(" : ").append(item.optString("content"))
                                        .append(" (Source: ").append(item.optString("url")).append(")\n\n");
                            }
                        }
                        
                        String finalContext = contextBuilder.toString();
                        mainHandler.post(() -> callback.onSuccess(finalContext));
                    } else {
                        mainHandler.post(() -> callback.onError("Erreur lors de la recherche Web."));
                    }
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        }).start();
    }
}
