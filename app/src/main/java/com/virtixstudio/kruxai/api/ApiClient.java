package com.virtixstudio.kruxai.api;

import android.util.Log;

import com.virtixstudio.kruxai.BuildConfig;
import com.virtixstudio.kruxai.models.KruxModel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ApiClient {

    private static final String TAG = "KruxApiClient";

    public interface ApiCallback {
        void onSuccess(String response, String modelBrand);
        void onError(String friendlyMessage);
    }

    /*
     * Compatibilité avec MainActivity actuelle.
     *
     * Ordre de secours :
     * 1. Krux 3.3 70B
     * 2. Krux Speed 70B
     * 3. Krux 3.5 Flash
     * 4. Krux Codeur Pro
     * 5. Krux Codeur 32B
     */
    public static void sendRequest(
            String systemPrompt,
            String userMessage,
            ApiCallback callback
    ) {
        new Thread(() -> {

            KruxModel[] fallbackModels = {
                    KruxModel.KRUX_33_70B,
                    KruxModel.KRUX_SPEED_70B,
                    KruxModel.KRUX_35_FLASH,
                    KruxModel.KRUX_CODEUR_PRO,
                    KruxModel.KRUX_CODEUR_32B
            };

            for (KruxModel model : fallbackModels) {
                try {
                    String response = sendWithModel(
                            model,
                            systemPrompt,
                            userMessage
                    );

                    callback.onSuccess(
                            response,
                            model.getDisplayName()
                    );

                    return;

                } catch (Exception e) {
                    Log.w(
                            TAG,
                            model.getDisplayName() + " indisponible",
                            e
                    );
                }
            }

            callback.onError(
                    "Les serveurs KRUX sont temporairement indisponibles. " +
                    "Veuillez réessayer dans un instant."
            );

        }).start();
    }

    /*
     * Méthode pour utiliser un modèle précis.
     *
     * Exemple :
     *
     * ApiClient.sendRequest(
     *     KruxModel.KRUX_35_FLASH,
     *     systemPrompt,
     *     userMessage,
     *     callback
     * );
     */
    public static void sendRequest(
            KruxModel model,
            String systemPrompt,
            String userMessage,
            ApiCallback callback
    ) {
        new Thread(() -> {

            try {
                String response = sendWithModel(
                        model,
                        systemPrompt,
                        userMessage
                );

                callback.onSuccess(
                        response,
                        model.getDisplayName()
                );

            } catch (Exception e) {

                Log.e(
                        TAG,
                        "Erreur avec " + model.getDisplayName(),
                        e
                );

                callback.onError(
                        "Impossible d'utiliser " +
                        model.getDisplayName() +
                        " pour le moment."
                );
            }

        }).start();
    }

    private static String sendWithModel(
            KruxModel model,
            String systemPrompt,
            String userMessage
    ) throws Exception {

        switch (model.getProvider()) {

            case "GROQ":
                return callOpenAIStyle(
                        "https://api.groq.com/openai/v1/chat/completions",
                        BuildConfig.GROQ_API_KEY,
                        model.getModelId(),
                        systemPrompt,
                        userMessage
                );

            case "CEREBRAS":
                return callOpenAIStyle(
                        "https://api.cerebras.ai/v1/chat/completions",
                        BuildConfig.CEREBRAS_API_KEY,
                        model.getModelId(),
                        systemPrompt,
                        userMessage
                );

            case "MISTRAL":
                return callOpenAIStyle(
                        "https://api.mistral.ai/v1/chat/completions",
                        BuildConfig.MISTRAL_API_KEY,
                        model.getModelId(),
                        systemPrompt,
                        userMessage
                );

            case "HUGGINGFACE":
                return callOpenAIStyle(
                        "https://api-inference.huggingface.co/models/"
                                + model.getModelId()
                                + "/v1/chat/completions",
                        BuildConfig.HF_API_KEY,
                        model.getModelId(),
                        systemPrompt,
                        userMessage
                );

            case "GEMINI":
                return callGemini(
                        BuildConfig.GEMINI_API_KEY,
                        model.getModelId(),
                        systemPrompt,
                        userMessage
                );

            default:
                throw new Exception(
                        "Provider inconnu : " + model.getProvider()
                );
        }
    }

    private static String callOpenAIStyle(
            String endpoint,
            String apiKey,
            String model,
            String systemPrompt,
            String userMessage
    ) throws Exception {

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new Exception("Clé API absente");
        }

        URL url = new URL(endpoint);

        HttpURLConnection conn =
                (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setRequestProperty(
                "Authorization",
                "Bearer " + apiKey
        );
        conn.setRequestProperty(
                "Content-Type",
                "application/json"
        );

        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);

        JSONObject json = new JSONObject();

        json.put("model", model);

        JSONArray messages = new JSONArray();

        messages.put(
                new JSONObject()
                        .put("role", "system")
                        .put("content", systemPrompt)
        );

        messages.put(
                new JSONObject()
                        .put("role", "user")
                        .put("content", userMessage)
        );

        json.put("messages", messages);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(
                    json.toString().getBytes("UTF-8")
            );
        }

        int responseCode = conn.getResponseCode();

        if (responseCode < 200 || responseCode >= 300) {
            throw new Exception(
                    "HTTP " + responseCode
            );
        }

        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                conn.getInputStream(),
                                "UTF-8"
                        )
                );

        StringBuilder response =
                new StringBuilder();

        String line;

        while ((line = reader.readLine()) != null) {
            response.append(line);
        }

        reader.close();
        conn.disconnect();

        JSONObject result =
                new JSONObject(response.toString());

        return result
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");
    }

    private static String callGemini(
            String apiKey,
            String model,
            String systemPrompt,
            String userMessage
    ) throws Exception {

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new Exception("Clé Gemini absente");
        }

        URL url = new URL(
                "https://generativelanguage.googleapis.com/"
                        + "v1beta/models/"
                        + model
                        + ":generateContent?key="
                        + apiKey
        );

        HttpURLConnection conn =
                (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");

        conn.setRequestProperty(
                "Content-Type",
                "application/json"
        );

        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);

        JSONObject json = new JSONObject();

        JSONArray contents = new JSONArray();

        JSONObject content = new JSONObject();

        JSONArray parts = new JSONArray();

        parts.put(
                new JSONObject()
                        .put(
                                "text",
                                systemPrompt
                                        + "\n\n"
                                        + userMessage
                        )
        );

        content.put("parts", parts);

        contents.put(content);

        json.put("contents", contents);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(
                    json.toString().getBytes("UTF-8")
            );
        }

        int responseCode = conn.getResponseCode();

        if (responseCode < 200 || responseCode >= 300) {
            throw new Exception(
                    "HTTP " + responseCode
            );
        }

        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                conn.getInputStream(),
                                "UTF-8"
                        )
                );

        StringBuilder response =
                new StringBuilder();

        String line;

        while ((line = reader.readLine()) != null) {
            response.append(line);
        }

        reader.close();
        conn.disconnect();

        JSONObject result =
                new JSONObject(response.toString());

        return result
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text");
    }
}
