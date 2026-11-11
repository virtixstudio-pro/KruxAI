package com.virtixstudio.kruxai.api;

import android.util.Log;

import com.virtixstudio.kruxai.BuildConfig;
import com.virtixstudio.kruxai.core.ModelCooldown;
import com.virtixstudio.kruxai.models.KruxModel;
import com.virtixstudio.kruxai.models.SearchResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ApiClient {

    private static final String TAG = "KruxApiClient";
    private static volatile Thread activeRequestThread;
    private static volatile HttpURLConnection activeConnection;
    private static volatile ApiCallback activeCallback;
    private static volatile boolean cancellationNotified;
    /** Appels de fond (mémoire) : ils ne touchent jamais à la requête annulable du chat. */
    private static final ThreadLocal<Boolean> QUIET = new ThreadLocal<Boolean>() {
        @Override protected Boolean initialValue() { return Boolean.FALSE; }
    };

    /** Petit appel en arrière-plan, avec un moteur léger, sans attente ni message à l'utilisateur. null si échec. */
    public interface QuietCallback { void done(String textOrNull); }

    public static void sendQuiet(final String systemPrompt, final String userMessage, final QuietCallback cb) {
        new Thread(() -> {
            QUIET.set(Boolean.TRUE);
            String result = null;
            KruxModel[] cheap = { KruxModel.GEMINI_FLASH_LITE, KruxModel.GROQ_GPT_OSS_20B, KruxModel.MISTRAL_MINISTRAL_3B };
            long now = System.currentTimeMillis();
            for (KruxModel m : cheap) {
                if (ModelCooldown.isCooling(m.name(), now)) continue;
                try {
                    String r = sendWithModel(m, systemPrompt, userMessage, new ApiCallback() {
                        @Override public void onSuccess(String response, String modelBrand) { }
                        @Override public void onError(String friendlyMessage) { }
                    });
                    if (r != null && !r.trim().isEmpty()) { result = r; break; }
                } catch (Exception e) {
                    if (ModelCooldown.isRateLimit(e.getMessage())) {
                        ModelCooldown.markLimited(m.name(), System.currentTimeMillis(), ModelCooldown.parseRetryAfterMs(e.getMessage()));
                    }
                }
            }
            try { cb.done(result); } catch (Exception ignored) { }
        }).start();
    }

    public interface ApiCallback {
        void onSuccess(String response, String modelBrand);
        void onError(String friendlyMessage);
        default void onCancelled() {}
        default void onPartialResponse(String response, String modelBrand) {}
        /** Un moteur est saturé (quota par minute) : KRUX passe sur un autre. */
        default void onFallback() {}
        /** Tous les moteurs sont saturés : KRUX attend la fin de la limite puis reprend. */
        default void onWaiting(int seconds) {}
        default void onWebSearchStarted(String query) {}
        default void onWebSearchFinished(List<SearchResult> results) {}
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
        /* Fallback global utilisé lorsqu'aucun modèle précis n'est imposé. */
        KruxModel[] fallbackModels = {
            KruxModel.GEMINI_FLASH_LITE,
            KruxModel.GROQ_GPT_OSS_20B,
            KruxModel.MISTRAL_MINISTRAL_3B,
            KruxModel.MISTRAL_MINISTRAL_8B,
            KruxModel.HF_QWEN_CODER_7B,
            KruxModel.GEMINI_36_FLASH,
            KruxModel.GROQ_GPT_OSS_120B,
            KruxModel.CEREBRAS_GPT_OSS_120B,
            KruxModel.MISTRAL_CODESTRAL,
            KruxModel.HF_QWEN_CODER_32B
            };
        runChain(fallbackModels, systemPrompt, userMessage, callback);
    }

    /*
     * Appel avec un modèle sélectionné par l'utilisateur.
     *
     * Ordre :
     * 1. modèle sélectionné
     * 2. autre modèle du même fournisseur
     * 3. modèles des autres fournisseurs
     *
     * Les noms affichés restent exclusivement KRUX.
     * Les vrais IDs API restent dans KruxModel.
     */
    public static void sendRequest(
            KruxModel selectedModel,
            String systemPrompt,
            String userMessage,
            ApiCallback callback
    ) {
        runChain(buildFallbackModels(selectedModel), systemPrompt, userMessage, callback);
    }

    private static final long MAX_WAIT_MS = 75_000L;
    private static final int MAX_ROUNDS = 4;

    /*
     * Essaie les moteurs dans l'ordre. Un moteur limité (429) est mis de côté pour la durée
     * demandée par le fournisseur (par défaut une minute) et KRUX passe sur un autre.
     * Si tous les moteurs sont limités, KRUX attend la fin de la limite (au plus 75 s)
     * puis reprend, au lieu d'interrompre la création.
     */
    private static void runChain(
            final KruxModel[] models,
            final String systemPrompt,
            final String userMessage,
            final ApiCallback callback
    ) {
        new Thread(() -> {
            activeRequestThread = Thread.currentThread();
            activeCallback = callback;
            cancellationNotified = false;

            Exception lastError = null;
            java.util.HashSet<KruxModel> failedHard = new java.util.HashSet<>();

            for (int round = 0; round < MAX_ROUNDS; round++) {
                long now = System.currentTimeMillis();
                java.util.ArrayList<KruxModel> candidates = new java.util.ArrayList<>();
                java.util.ArrayList<KruxModel> ready = new java.util.ArrayList<>();
                java.util.ArrayList<String> keys = new java.util.ArrayList<>();
                for (KruxModel m : models) {
                    if (failedHard.contains(m)) continue;
                    candidates.add(m);
                    keys.add(m.name());
                    if (!ModelCooldown.isCooling(m.name(), now)) ready.add(m);
                }
                if (candidates.isEmpty()) break;

                if (ready.isEmpty()) {
                    long wait = ModelCooldown.soonestReadyMs(keys, now);
                    if (wait > MAX_WAIT_MS) break;
                    Log.w(TAG, "Tous les moteurs sont limités, attente " + wait + " ms");
                    callback.onWaiting((int) ((wait + 999) / 1000));
                    try {
                        Thread.sleep(wait + 500);
                    } catch (InterruptedException e) {
                        notifyCancelled(callback);
                        return;
                    }
                    continue;
                }

                boolean limitedNow = false;
                for (KruxModel model : ready) {
                    try {
                        Log.d(TAG, "Tentative avec " + model.getDisplayName());

                        String response = sendWithWebLoop(model, systemPrompt, userMessage, callback);

                        if (Thread.currentThread().isInterrupted()) {
                            notifyCancelled(callback);
                            return;
                        }

                        if (response != null && !response.trim().isEmpty()) {
                            Log.d(TAG, "Réponse obtenue avec " + model.getDisplayName());
                            callback.onSuccess(response, model.getDisplayName());
                            return;
                        }

                        lastError = new Exception("Réponse vide.");
                        failedHard.add(model);

                    } catch (Exception e) {
                        if (Thread.currentThread().isInterrupted()) {
                            notifyCancelled(callback);
                            return;
                        }
                        lastError = e;
                        Log.w(TAG, model.getDisplayName() + " indisponible : " + e.getMessage(), e);

                        if (ModelCooldown.isRateLimit(e.getMessage())) {
                            ModelCooldown.markLimited(
                                    model.name(),
                                    System.currentTimeMillis(),
                                    ModelCooldown.parseRetryAfterMs(e.getMessage()));
                            limitedNow = true;
                            callback.onFallback();
                        } else {
                            failedHard.add(model);
                        }
                    }
                }

                /* Échec sans rapport avec le quota : inutile d'attendre. */
                if (!limitedNow) break;
            }

            callback.onError(buildFriendlyError(lastError));
        }).start();
    }

    public static void cancelCurrentRequest() {
        Thread requestThread = activeRequestThread;
        if (requestThread != null) {
            requestThread.interrupt();
        }

        HttpURLConnection connection = activeConnection;
        if (connection != null) {
            connection.disconnect();
        }

        notifyCancelled(activeCallback);
    }

    private static synchronized void notifyCancelled(ApiCallback callback) {
        if (!cancellationNotified && callback != null) {
            cancellationNotified = true;
            callback.onCancelled();
        }
    }

    private static String retryAfterSuffix(HttpURLConnection c) {
        String v = c.getHeaderField("Retry-After");
        return v == null ? "" : " retry-after=" + v.trim();
    }

    private static KruxModel[] buildFallbackModels(
            KruxModel selectedModel
    ) {

        java.util.ArrayList<KruxModel> ordered =
                new java.util.ArrayList<>();

        if (selectedModel != null) {

            ordered.add(selectedModel);

            /*
             * Même fournisseur + taille opposée en priorité.
             */
            for (KruxModel candidate : KruxModel.values()) {

                if (candidate == selectedModel) {
                    continue;
                }

                if (candidate.getProvider().equals(
                        selectedModel.getProvider()
                )
                        && candidate.getSize()
                        != selectedModel.getSize()) {

                    ordered.add(candidate);
                }
            }

            /*
             * Puis les autres modèles du même fournisseur.
             */
            for (KruxModel candidate : KruxModel.values()) {

                if (ordered.contains(candidate)) {
                    continue;
                }

                if (candidate.getProvider().equals(
                        selectedModel.getProvider()
                )) {

                    ordered.add(candidate);
                }
            }
        }

        /*
         * Enfin, autres fournisseurs.
         */
        for (KruxModel candidate : KruxModel.values()) {

            if (!ordered.contains(candidate)) {
                ordered.add(candidate);
            }
        }

        return ordered.toArray(
                new KruxModel[0]
        );
    }

    /*
     * Transforme les erreurs techniques en messages compréhensibles
     * pour l utilisateur.
     */
    private static String buildFriendlyError(Exception e) {
        if (e == null) {
            return "Krux a rencontré un problème inattendu. Réessaie dans quelques instants.";
        }

        String message = e.getMessage();
        if (message == null) {
            message = "";
        }

        String lower = message.toLowerCase();

        if (lower.contains("clé api")
                || lower.contains("api key")
                || lower.contains("apikey")) {
            return "Ce moteur n est pas encore disponible. Krux peut essayer une autre configuration.";
        }

        if (lower.contains("401") || lower.contains("403")) {
            return "L accès à ce moteur n est pas disponible actuellement. Krux peut essayer une autre option.";
        }

        if (lower.contains("404")) {
            return "Le service demandé n est pas disponible actuellement.";
        }

        if (lower.contains("429")) {
            return "Ce moteur reçoit actuellement trop de demandes. Réessaie dans quelques instants.";
        }

        if (lower.contains("500")
                || lower.contains("502")
                || lower.contains("503")
                || lower.contains("504")) {
            return "Le service rencontre actuellement un problème. Réessaie dans quelques instants.";
        }

        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "La réponse prend plus de temps que prévu. Vérifie ta connexion et réessaie.";
        }

        if (lower.contains("connect")
                || lower.contains("network")
                || lower.contains("unable to resolve")
                || lower.contains("connection")) {
            return "Krux ne parvient pas à joindre le service. Vérifie ta connexion Internet.";
        }

        if (lower.contains("json") || lower.contains("parse")) {
            return "Le service a renvoyé une réponse inattendue. Réessaie dans quelques instants.";
        }

        return "Krux a rencontré un problème inattendu. Réessaie dans quelques instants.";
    }


    private static String sendWithWebLoop(
            KruxModel model,
            String systemPrompt,
            String userMessage,
            ApiCallback callback
    ) throws Exception {

        String currentPrompt = userMessage;

        for (int attempt = 0; attempt < 2; attempt++) {

            String response = sendWithModel(
                    model,
                    systemPrompt,
                    currentPrompt,
                    callback
            );

            if (response == null || response.trim().isEmpty()) {
                return response;
            }

            String query = extractWebQuery(response);

            if (query == null || query.trim().isEmpty()) {
                return response;
            }

            Log.d(TAG, "Demande Web du modèle : " + query);

            if (callback != null) {
                callback.onWebSearchStarted(query);
            }

            WebSearchEngine engine = new WebSearchEngine();

            CountDownLatch latch = new CountDownLatch(1);
            final String[] webContext = {""};
            final List<SearchResult>[] webResults = new List[]{null};
            final String[] webError = {null};

            engine.search(query, new WebSearchEngine.SearchCallback() {
                @Override
                public void onSuccess(
                        List<SearchResult> results,
                        String formattedContext
                ) {
                    webResults[0] = results;
                    webContext[0] = formattedContext;
                    latch.countDown();
                }

                @Override
                public void onError(String error) {
                    webError[0] = error;
                    latch.countDown();
                }
            });

            if (!latch.await(15, TimeUnit.SECONDS)) {
                Log.w(TAG, "Recherche Web expirée.");
                return response;
            }

            if (webError[0] != null
                    || webContext[0] == null
                    || webContext[0].trim().isEmpty()) {
                Log.w(TAG, "Recherche Web échouée.");
                return response;
            }

            if (callback != null && webResults[0] != null) {
                callback.onWebSearchFinished(webResults[0]);
            }

            currentPrompt =
                    userMessage
                    + "\n\n"
                    + "Résultats de la recherche Web effectuée par Krux :\n"
                    + webContext[0]
                    + "\n\n"
                    + "Utilise ces résultats pour répondre à la demande initiale. "
                    + "Ne demande pas une nouvelle recherche pour cette même demande. "
                    + "Réponds directement à l'utilisateur.";
        }

        return sendWithModel(model, systemPrompt, currentPrompt, callback);
    }

    private static String extractWebQuery(String response) {
        if (response == null) {
            return null;
        }

        String startTag = "<KRUX_TOOL>";
        String endTag = "</KRUX_TOOL>";

        int start = response.indexOf(startTag);
        int end = response.indexOf(endTag);

        if (start < 0 || end <= start) {
            return null;
        }

        String block = response.substring(
                start + startTag.length(),
                end
        ).trim();

        String[] lines = block.split("\\r?\\n", 3);

        if (lines.length < 2) {
            return null;
        }

        if (!"web_search".equalsIgnoreCase(lines[0].trim())) {
            return null;
        }

        String query = lines[1].trim();

        return query.isEmpty() ? null : query;
    }

    private static String sendWithModel(
            KruxModel model,
            String systemPrompt,
            String userMessage,
            ApiCallback callback
    ) throws Exception {

        switch (model.getProvider()) {

            case "GROQ":
                return callOpenAIStyle(
                        "https://api.groq.com/openai/v1/chat/completions",
                        BuildConfig.GROQ_API_KEY,
                        model.getModelId(),
                        systemPrompt,
                        userMessage,
                        callback,
                        model.getDisplayName()
                );

            case "CEREBRAS":
                return callOpenAIStyle(
                        "https://api.cerebras.ai/v1/chat/completions",
                        BuildConfig.CEREBRAS_API_KEY,
                        model.getModelId(),
                        systemPrompt,
                        userMessage,
                        callback,
                        model.getDisplayName()
                );

            case "MISTRAL":
                return callOpenAIStyle(
                        "https://api.mistral.ai/v1/chat/completions",
                        BuildConfig.MISTRAL_API_KEY,
                        model.getModelId(),
                        systemPrompt,
                        userMessage,
                        callback,
                        model.getDisplayName()
                );

            case "HUGGINGFACE":
                return callOpenAIStyle(
                        "https://api-inference.huggingface.co/models/"
                                + model.getModelId()
                                + "/v1/chat/completions",
                        BuildConfig.HF_API_KEY,
                        model.getModelId(),
                        systemPrompt,
                        userMessage,
                        callback,
                        model.getDisplayName()
                );

            case "GEMINI":
                return callGemini(
                        BuildConfig.GEMINI_API_KEY,
                        model.getModelId(),
                        systemPrompt,
                        userMessage,
                        callback,
                        model.getDisplayName()
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
            String userMessage,
            ApiCallback callback,
            String modelBrand
    ) throws Exception {

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new Exception("Clé API absente");
        }

        URL url = new URL(endpoint);

        HttpURLConnection conn =
                (HttpURLConnection) url.openConnection();
        if (!QUIET.get()) activeConnection = conn;

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
        json.put("stream", true);
        // sans limite explicite, certains moteurs coupent la réponse très court (surtout un gros JSON d'application)
        json.put("max_tokens", 8192);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(
                    json.toString().getBytes("UTF-8")
            );
        }

        int responseCode = conn.getResponseCode();

        if (responseCode < 200 || responseCode >= 300) {
            throw new Exception(
                    "HTTP " + responseCode + retryAfterSuffix(conn)
            );
        }

        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                conn.getInputStream(),
                                "UTF-8"
                        )
                );

        StringBuilder response = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            if (!line.startsWith("data:")) {
                continue;
            }

            String data = line.substring(5).trim();
            if (data.isEmpty() || "[DONE]".equals(data)) {
                continue;
            }

            JSONObject event = new JSONObject(data);
            JSONArray choices = event.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                continue;
            }

            JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
            if (delta == null) {
                continue;
            }

            String fragment = delta.optString("content", "");
            if (!fragment.isEmpty()) {
                response.append(fragment);
                if (callback != null) {
                    callback.onPartialResponse(response.toString(), modelBrand);
                }
            }
        }

        reader.close();
        conn.disconnect();
        if (!QUIET.get()) activeConnection = null;

        return response.toString();
    }

    private static String callGemini(
            String apiKey,
            String model,
            String systemPrompt,
            String userMessage,
            ApiCallback callback,
            String modelBrand
    ) throws Exception {

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new Exception("Clé Gemini absente");
        }

        URL url = new URL(
                "https://generativelanguage.googleapis.com/"
                        + "v1beta/models/"
                        + model
                        + ":streamGenerateContent?alt=sse&key="
                        + apiKey
        );

        HttpURLConnection conn =
                (HttpURLConnection) url.openConnection();
        if (!QUIET.get()) activeConnection = conn;

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
        json.put("generationConfig", new JSONObject().put("maxOutputTokens", 16384));

        try (OutputStream os = conn.getOutputStream()) {
            os.write(
                    json.toString().getBytes("UTF-8")
            );
        }

        int responseCode = conn.getResponseCode();

        if (responseCode < 200 || responseCode >= 300) {
            throw new Exception(
                    "HTTP " + responseCode + retryAfterSuffix(conn)
            );
        }

        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                conn.getInputStream(),
                                "UTF-8"
                        )
                );

        StringBuilder response = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            if (!line.startsWith("data:")) {
                continue;
            }

            String data = line.substring(5).trim();
            if (data.isEmpty()) {
                continue;
            }

            JSONObject event = new JSONObject(data);
            JSONArray candidates = event.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) {
                continue;
            }

            JSONObject candidateContent = candidates.getJSONObject(0)
                    .optJSONObject("content");
            if (candidateContent == null) {
                continue;
            }

            JSONArray candidateParts = candidateContent.optJSONArray("parts");
            if (candidateParts == null || candidateParts.length() == 0) {
                continue;
            }

            String fragment = candidateParts.getJSONObject(0)
                    .optString("text", "");
            if (!fragment.isEmpty()) {
                response.append(fragment);
                if (callback != null) {
                    callback.onPartialResponse(response.toString(), modelBrand);
                }
            }
        }

        reader.close();
        conn.disconnect();
        if (!QUIET.get()) activeConnection = null;

        return response.toString();
    }
}
