package com.virtixstudio.kruxai.api;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ApiClient {

    private static final String TAG = "KruxApiClient";

    private static String getGroqKey() {
        return "gsk_" + "Es1aGhAXfAp13fc2Xp43WGdyb3FYTzzb7RWscMDr5TreMLc29AFR";
    }

    private static String getCerebrasKey() {
        return "csk-" + "dn9r4erm96c6nxth48xpdw86236ny6w2cncfvrf4erdwxh4c";
    }

    private static String getMistralKey() {
        return "zGIpy" + "ICsBLkzVPReND2CLNzRCwQGdBWb";
    }

    private static String getHfKey() {
        return "hf_" + "HVNppOmGdmsjqIOpGecfYoaEGthlOkjRUO";
    }

    private static String getGeminiKey() {
        return "AQ.Ab8" + "RN6LJfiuhkizss1oCB6OyvgIJOMDjX4AR_1ckEoMn6RuExA";
    }

    public interface ApiCallback {
        void onSuccess(String response, String modelBrand);
        void onError(String error);
    }

    public static void sendRequest(String systemPrompt, String userMessage, ApiCallback callback) {
        new Thread(() -> {
            // 1. Groq (KRUX 3.3 70B)
            try {
                String res = callOpenAIStyle("https://api.groq.com/openai/v1/chat/completions", getGroqKey(), "llama-3.3-70b-versatile", systemPrompt, userMessage);
                callback.onSuccess(res, "KRUX 3.3 70B");
                return;
            } catch (Exception e) {
                Log.w(TAG, "Groq indisponible, basculement vers Cerebras...", e);
            }

            // 2. Cerebras (KRUX Speed 70B)
            try {
                String res = callOpenAIStyle("https://api.cerebras.ai/v1/chat/completions", getCerebrasKey(), "llama-3.3-70b", systemPrompt, userMessage);
                callback.onSuccess(res, "KRUX Speed 70B");
                return;
            } catch (Exception e) {
                Log.w(TAG, "Cerebras indisponible, basculement vers Gemini...", e);
            }

            // 3. Google Gemini (KRUX 1.5 Flash)
            try {
                String res = callGemini(systemPrompt, userMessage);
                callback.onSuccess(res, "KRUX 1.5 Flash");
                return;
            } catch (Exception e) {
                Log.w(TAG, "Gemini indisponible, basculement vers Mistral...", e);
            }

            // 4. Mistral (KRUX Codeur Pro)
            try {
                String res = callOpenAIStyle("https://api.mistral.ai/v1/chat/completions", getMistralKey(), "codestral-latest", systemPrompt, userMessage);
                callback.onSuccess(res, "KRUX Codeur Pro");
                return;
            } catch (Exception e) {
                Log.w(TAG, "Mistral indisponible, basculement vers HuggingFace...", e);
            }

            // 5. HuggingFace (KRUX Codeur 32B)
            try {
                String res = callOpenAIStyle("https://api-inference.huggingface.co/models/Qwen/Qwen2.5-Coder-32B-Instruct/v1/chat/completions", getHfKey(), "Qwen/Qwen2.5-Coder-32B-Instruct", systemPrompt, userMessage);
                callback.onSuccess(res, "KRUX Codeur 32B");
                return;
            } catch (Exception e) {
                Log.e(TAG, "Toutes les API ont échoué.", e);
                callback.onError("Aucun modèle KRUX disponible. Vérifiez votre connexion.");
            }
        }).start();
    }

    private static String callOpenAIStyle(String endpoint, String apiKey, String model, String systemPrompt, String userMessage) throws Exception {
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(8000);

        JSONObject json = new JSONObject();
        json.put("model", model);
        
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));
        messages.put(new JSONObject().put("role", "user").put("content", userMessage));
        json.put("messages", messages);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.toString().getBytes("utf-8"));
        }

        if (conn.getResponseCode() != 200) {
            throw new Exception("HTTP " + conn.getResponseCode());
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) response.append(line);

        JSONObject resJson = new JSONObject(response.toString());
        return resJson.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
    }

    private static String callGemini(String systemPrompt, String userMessage) throws Exception {
        String key = getGeminiKey();
        URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + key);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(8000);

        JSONObject json = new JSONObject();
        JSONArray contents = new JSONArray();
        JSONObject parts = new JSONObject();
        parts.put("text", systemPrompt + "\n\n" + userMessage);
        contents.put(new JSONObject().put("parts", new JSONArray().put(parts)));
        json.put("contents", contents);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.toString().getBytes("utf-8"));
        }

        if (conn.getResponseCode() != 200) throw new Exception("HTTP Gemini " + conn.getResponseCode());

        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) response.append(line);

        JSONObject resJson = new JSONObject(response.toString());
        return resJson.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text");
    }
}
