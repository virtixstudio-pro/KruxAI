package com.virtixstudio.kruxai.router;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ModelRouter {

    public interface Callback {
        void onSuccess(String response, ModelRoute route);
        void onError(String error);
    }

    private static final MediaType JSON =
            MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final Handler mainHandler;

    private final String groqKey;
    private final String mistralKey;
    private final String hfKey;
    private final String geminiKey;

    public ModelRouter(
            String groqKey,
            String mistralKey,
            String hfKey,
            String geminiKey
    ) {
        this.groqKey = groqKey;
        this.mistralKey = mistralKey;
        this.hfKey = hfKey;
        this.geminiKey = geminiKey;

        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        mainHandler = new Handler(Looper.getMainLooper());
    }

    /*
     * MODE ÉCONOMIQUE
     *
     * Utilise en priorité les petits modèles.
     */
    public void askCheap(String prompt, Callback callback) {

        List<ModelRoute> routes = new ArrayList<>();

        routes.add(new ModelRoute(
                "Gemini Flash",
                "GEMINI",
                "gemini-3.6-flash",
                false,
                1
        ));

        routes.add(new ModelRoute(
                "HF Qwen Coder",
                "HUGGINGFACE",
                "Qwen/Qwen2.5-Coder-32B-Instruct:fastest",
                false,
                2
        ));

        routes.add(new ModelRoute(
                "Groq OSS",
                "GROQ",
                "openai/gpt-oss-120b",
                true,
                3
        ));

        executeRoutes(prompt, routes, callback);
    }

    /*
     * MODE PUISSANT
     *
     * Utilise d'abord les modèles destinés
     * aux tâches complexes.
     */
    public void askPowerful(String prompt, Callback callback) {

        List<ModelRoute> routes = new ArrayList<>();

        routes.add(new ModelRoute(
                "Groq OSS 120B",
                "GROQ",
                "openai/gpt-oss-120b",
                true,
                1
        ));

        routes.add(new ModelRoute(
                "Mistral Codestral",
                "MISTRAL",
                "codestral-latest",
                true,
                2
        ));

        routes.add(new ModelRoute(
                "Gemini Flash",
                "GEMINI",
                "gemini-3.6-flash",
                false,
                3
        ));

        routes.add(new ModelRoute(
                "HF Qwen Coder",
                "HUGGINGFACE",
                "Qwen/Qwen2.5-Coder-32B-Instruct:fastest",
                false,
                4
        ));

        executeRoutes(prompt, routes, callback);
    }

    private void executeRoutes(
            String prompt,
            List<ModelRoute> routes,
            Callback callback
    ) {

        new Thread(() -> {

            String lastError = "Aucun modèle disponible.";

            for (ModelRoute route : routes) {

                try {

                    if (!hasKey(route.getProvider())) {
                        lastError =
                                route.getProvider()
                                + " : clé API absente.";
                        continue;
                    }

                    String result = callProvider(route, prompt);

                    if (result != null && !result.trim().isEmpty()) {

                        final String finalResult = result;

                        mainHandler.post(() ->
                                callback.onSuccess(finalResult, route)
                        );

                        return;
                    }

                } catch (Exception e) {

                    lastError =
                            route.getProvider()
                            + " / "
                            + route.getModel()
                            + " : "
                            + e.getMessage();
                }
            }

            final String error = lastError;

            mainHandler.post(() ->
                    callback.onError(
                            "Tous les modèles ont échoué.\n" + error
                    )
            );

        }).start();
    }

    private boolean hasKey(String provider) {

        switch (provider) {

            case "GROQ":
                return groqKey != null && !groqKey.isEmpty();

            case "MISTRAL":
                return mistralKey != null && !mistralKey.isEmpty();

            case "HUGGINGFACE":
                return hfKey != null && !hfKey.isEmpty();

            case "GEMINI":
                return geminiKey != null && !geminiKey.isEmpty();

            default:
                return false;
        }
    }

    private String callProvider(
            ModelRoute route,
            String prompt
    ) throws Exception {

        switch (route.getProvider()) {

            case "GROQ":
                return callOpenAICompatible(
                        "https://api.groq.com/openai/v1/chat/completions",
                        groqKey,
                        route.getModel(),
                        prompt
                );

            case "MISTRAL":
                return callOpenAICompatible(
                        "https://api.mistral.ai/v1/chat/completions",
                        mistralKey,
                        route.getModel(),
                        prompt
                );

            case "HUGGINGFACE":
                return callOpenAICompatible(
                        "https://router.huggingface.co/v1/chat/completions",
                        hfKey,
                        route.getModel(),
                        prompt
                );

            case "GEMINI":
                return callGemini(
                        route.getModel(),
                        prompt
                );

            default:
                throw new IOException(
                        "Fournisseur inconnu : "
                        + route.getProvider()
                );
        }
    }

    private String callOpenAICompatible(
            String url,
            String apiKey,
            String model,
            String prompt
    ) throws Exception {

        JSONObject body = new JSONObject();

        body.put("model", model);

        JSONArray messages = new JSONArray();

        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", prompt);

        messages.put(message);

        body.put("messages", messages);

        /*
         * Petite sortie par défaut :
         * réduit la consommation inutile.
         */
        body.put("max_tokens", 1024);
        body.put("temperature", 0.7);

        Request request = new Request.Builder()
                .url(url)
                .addHeader(
                        "Authorization",
                        "Bearer " + apiKey
                )
                .addHeader(
                        "Content-Type",
                        "application/json"
                )
                .post(
                        RequestBody.create(
                                body.toString(),
                                JSON
                        )
                )
                .build();

        try (Response response =
                     client.newCall(request).execute()) {

            String data =
                    response.body() != null
                            ? response.body().string()
                            : "";

            if (!response.isSuccessful()) {

                throw new IOException(
                        "HTTP "
                        + response.code()
                        + " : "
                        + data
                );
            }

            JSONObject json =
                    new JSONObject(data);

            JSONArray choices =
                    json.optJSONArray("choices");

            if (choices == null ||
                    choices.length() == 0) {

                throw new IOException(
                        "Réponse sans choices."
                );
            }

            JSONObject choice =
                    choices.getJSONObject(0);

            JSONObject messageResponse =
                    choice.optJSONObject("message");

            if (messageResponse == null) {
                throw new IOException(
                        "Réponse sans message."
                );
            }

            return messageResponse.optString(
                    "content",
                    ""
            );
        }
    }

    private String callGemini(
            String model,
            String prompt
    ) throws Exception {

        JSONObject body = new JSONObject();

        body.put("model", model);
        body.put("input", prompt);

        String url =
                "https://generativelanguage.googleapis.com/v1beta/interactions";

        Request request = new Request.Builder()
                .url(url)
                .addHeader(
                        "x-goog-api-key",
                        geminiKey
                )
                .addHeader(
                        "Content-Type",
                        "application/json"
                )
                .post(
                        RequestBody.create(
                                body.toString(),
                                JSON
                        )
                )
                .build();

        try (Response response =
                     client.newCall(request).execute()) {

            String data =
                    response.body() != null
                            ? response.body().string()
                            : "";

            if (!response.isSuccessful()) {
                throw new IOException(
                        "HTTP "
                        + response.code()
                        + " : "
                        + data
                );
            }

            JSONObject json =
                    new JSONObject(data);

            String output = json.optString(
                    "output",
                    ""
            );

            if (!output.isEmpty()) {
                return output;
            }

            JSONArray outputs =
                    json.optJSONArray("outputs");

            if (outputs != null &&
                    outputs.length() > 0) {

                JSONObject first =
                        outputs.getJSONObject(0);

                String text =
                        first.optString("text", "");

                if (!text.isEmpty()) {
                    return text;
                }
            }

            throw new IOException(
                    "Gemini : réponse sans texte."
            );
        }
    }

}
