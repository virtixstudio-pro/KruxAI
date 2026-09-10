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
     * Moteur de secours KRUX.
     *
     * Krux essaie automatiquement plusieurs moteurs.
     * Les erreurs internes restent dans Logcat.
     * L'utilisateur ne reçoit qu'un message propre.
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

                    Log.d(
                            TAG,
                            "Tentative avec " + model.getDisplayName()
                    );

                    String response = sendWithModel(
                            model,
                            systemPrompt,
                            userMessage
                    );

                    Log.d(
                            TAG,
                            "Réponse obtenue avec " + model.getDisplayName()
                    );

                    callback.onSuccess(
                            response,
                            model.getDisplayName()
                    );

                    return;

                } catch (Exception e) {

                    /*
                     * Détail réservé au développeur.
                     * Rien de technique n'est envoyé à l'utilisateur.
                     */
                    Log.w(
                            TAG,
                            model.getDisplayName()
                                    + " indisponible : "
                                    + e.getMessage(),
                            e
                    );
                }
            }

            /*
             * Tous les moteurs ont échoué.
             */
            callback.onError(
                    "Krux ne peut pas répondre pour le moment. "
                    + "Vérifie ta connexion Internet et réessaie dans quelques instants."
            );

        }).start();
    }

    /*
     * Appel direct d'un modèle précis.
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

                /*
                 * Détails techniques uniquement dans Logcat.
                 */
                Log.e(
                        TAG,
                        "Erreur API avec "
                                + model.getDisplayName()
                                + " : "
                                + e.getMessage(),
                        e
                );

                callback.onError(
                        buildFriendlyError(e)
                );
            }

        }).start();
    }

    /*
     * Transforme les erreurs techniques en messages
     * compréhensibles pour l'utilisateur.
     */
    private static String buildFriendlyError(Exception e) {

        if (e == null) {
            return "Krux a rencontré un problème inattendu. "
                    + "Réessaie dans quelques instants.";
        }

        String message = e.getMessage();

        if (message == null) {
            message = "";
        }

        String lower = message.toLowerCase();

        if (lower.contains("absente")
                || lower.contains("api key")
                || lower.contains("apikey")) {

            return "Ce moteur n'est pas encore disponible. "
                    + "Krux peut essayer une autre configuration.";
        }

        if (lower.contains("401")
                || lower.contains("403")) {

            return "L'accès à ce moteur n'est pas disponible actuellement. "
                    + "Krux peut essayer une autre option.";
        }

        if (lower.contains("404")) {

            return "Le service demandé n'est pas disponible actuellement.";
        }

        if (lower.contains("429")) {

            return "Ce moteur reçoit actuellement trop de demandes. "
                    + "Réessaie dans quelques instants.";
        }

        if (lower.contains("500")
                || lower.contains("502")
                || lower.contains("503")
                || lower.contains("504")) {

            return "Le service rencontre actuellement un problème. "
                    + "Réessaie dans quelques instants.";
        }

        if (lower.contains("timeout")
                || lower.contains("timed out")) {

            return "La réponse prend plus de temps que prévu. "
                    + "Vérifie ta connexion et réessaie.";
        }

        if (lower.contains("connect")
                || lower.contains("network")
                || lower.contains("unable to resolve")
                || lower.contains("connection")) {

            return "Krux ne parvient pas à joindre le service. "
                    + "Vérifie ta connexion Internet.";
        }

        if (lower.contains("json")
                || lower.contains("parse")) {

            return "Le service a renvoyé une réponse inattendue. "
                    + "Réessaie dans quelques instants.";
        }

        return "Krux a rencontré un problème inattendu. "
                + "Réessaie dans quelques instants.";
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
