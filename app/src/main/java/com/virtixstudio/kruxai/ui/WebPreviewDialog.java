package com.virtixstudio.kruxai.ui;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Aperçu d'une app React (mode web) : page assets/preview/index.html dans une WebView, avec retouches locales
 * (textes, couleurs) sans appel IA. La page ne reçoit aucun pont Java : Java lui donne les fichiers avec
 * evaluateJavascript, puis relit le résultat de la même façon quand l'utilisateur valide.
 */
final class WebPreviewDialog {

    private static final String PAGE = "file:///android_asset/preview/index.html";

    private WebPreviewDialog() { }

    /** À appeler hors du thread principal : bloque jusqu'au choix. Retourne les fichiers (retouchés ou non), ou null si annulé. */
    static Map<String, String> show(final Activity act, final Map<String, String> files) {
        return show(act, files, null);
    }

    /** refiner != null : ajoute « Demander à Krux » (modification écrite par l'IA) et la correction automatique des erreurs de l'aperçu. */
    static Map<String, String> show(final Activity act, final Map<String, String> files, final com.virtixstudio.kruxai.AppBuilder.Refiner refiner) {
        final AtomicReference<Map<String, String>> latest = new AtomicReference<>(files);
        final AtomicBoolean busy = new AtomicBoolean(false);
        final String json = new JSONObject(files).toString();
        final AtomicReference<Map<String, String>> result = new AtomicReference<>(null);
        final CountDownLatch decided = new CountDownLatch(1);
        final Dialog[] shown = {null};

        act.runOnUiThread(() -> {
            final int bg = KruxTheme.get(act, "bg");
            final int accent = KruxTheme.get(act, "accent");
            final int tx = KruxTheme.get(act, "text");
            final int tx2 = KruxTheme.get(act, "text2");
            final Dialog dlg = new Dialog(act);
            dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dlg.setCancelable(false);

            LinearLayout root = new LinearLayout(act);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(act, 16), dp(act, 16), dp(act, 16), dp(act, 14));
            GradientDrawable sheet = new GradientDrawable();
            sheet.setColor(bg);
            sheet.setCornerRadius(dp(act, 24));
            root.setBackground(sheet);

            TextView title = new TextView(act);
            title.setText("Aperçu de ton application");
            title.setTextColor(tx);
            title.setTextSize(18);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            root.addView(title);
            TextView hint = new TextView(act);
            hint.setText("Teste l'app. « Retoucher » et « Couleurs » : sans IA, gratuit. Ou écris à Krux ce que tu veux changer.");
            hint.setTextColor(tx2);
            hint.setTextSize(13);
            hint.setPadding(0, dp(act, 4), 0, dp(act, 12));
            root.addView(hint);

            // Page locale : JavaScript permis, mais réseau, fichiers externes et stockage bloqués, aucun pont Java.
            final WebView web = new WebView(act);
            WebSettings ws = web.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setJavaScriptCanOpenWindowsAutomatically(false);
            ws.setSupportMultipleWindows(false);
            ws.setBlockNetworkLoads(true);
            ws.setAllowFileAccess(false);
            ws.setAllowContentAccess(false);
            ws.setDomStorageEnabled(false);
            ws.setDatabaseEnabled(false);
            ws.setGeolocationEnabled(false);
            // Commandes de Krux venant de la page (listen / stop / ask) : schéma krux:// reçu UNIQUEMENT du cadre principal.
            // Aucun addJavascriptInterface : l'app générée (iframe isolée) n'a donc aucun accès à Java.
            final AtomicReference<Consumer<Uri>> bridge = new AtomicReference<>();
            final boolean[] loaded = {false};
            web.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                    Uri u = r.getUrl();
                    if (r.isForMainFrame() && u != null && "krux".equals(u.getScheme())) {
                        Consumer<Uri> b = bridge.get();
                        if (b != null) b.accept(u);
                    }
                    return true; // aucune navigation en dehors de l'aperçu
                }

                @Override
                public void onPageFinished(WebView v, String url) {
                    if (loaded[0]) return;
                    loaded[0] = true;
                    v.evaluateJavascript("KruxPreview.load(" + JSONObject.quote(json) + ")", null);
                    if (refiner != null) v.evaluateJavascript("KruxPhone.enableNative()", null);
                }
            });
            web.setBackgroundColor(Color.WHITE);
            web.loadUrl(PAGE);

            final FrameLayout frame = new FrameLayout(act);
            GradientDrawable fg = new GradientDrawable();
            fg.setColor(Color.WHITE);
            fg.setCornerRadius(dp(act, 20));
            fg.setStroke(dp(act, 2), (accent & 0x00FFFFFF) | 0x99000000);
            frame.setBackground(fg);
            frame.setClipToOutline(true);
            frame.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            root.addView(frame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            final TextView status = new TextView(act);
            status.setTextColor(tx2);
            status.setTextSize(12);
            status.setPadding(0, dp(act, 6), 0, 0);
            status.setVisibility(View.GONE);
            if (refiner != null) {
                LinearLayout askRow = new LinearLayout(act);
                askRow.setOrientation(LinearLayout.HORIZONTAL);
                askRow.setGravity(Gravity.CENTER_VERTICAL);
                askRow.setPadding(0, dp(act, 10), 0, 0);
                final EditText askBox = new EditText(act);
                askBox.setHint("Demander à Krux : « mets un bouton rouge »…");
                askBox.setTextColor(tx);
                askBox.setHintTextColor(tx2);
                askBox.setTextSize(14);
                askBox.setMaxLines(3);
                askRow.addView(askBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                int onAcc = androidx.core.graphics.ColorUtils.calculateLuminance(accent) > 0.5 ? Color.BLACK : Color.WHITE;
                TextView send = button(act, "Envoyer", onAcc, accent);
                send.setOnClickListener(v -> {
                    String req = askBox.getText().toString().trim();
                    if (req.isEmpty() || busy.get()) return;
                    askBox.setText("");
                    refine(act, web, refiner, latest, req, "Krux travaille sur ta demande…", status, busy, dlg, null);
                });
                LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                sLp.leftMargin = dp(act, 8);
                askRow.addView(send, sLp);
                root.addView(askRow);
            }
            root.addView(status);

            // Dictée de modifications depuis le visage de Krux : reconnaissance vocale -> refine() sur l'app ACTUELLE -> « C'est fait ».
            final SpeechRecognizer[] rec = {null};
            final TextToSpeech[] tts = {null};
            final boolean[] ttsReady = {false};
            if (refiner != null) {
                tts[0] = new TextToSpeech(act, st -> ttsReady[0] = (st == TextToSpeech.SUCCESS));
                bridge.set(uri -> {
                    String cmd = uri.getHost() == null ? "" : uri.getHost();
                    if ("stop".equals(cmd)) {
                        if (rec[0] != null) rec[0].stopListening();
                        return;
                    }
                    if ("ask".equals(cmd)) {
                        String text = uri.getQueryParameter("t");
                        if (busy.get()) return;
                        if (text == null || text.trim().isEmpty()) {
                            phoneJs(web, "KruxPhone.done(false," + JSONObject.quote("Je n'ai rien compris, réessaie.") + ")");
                            return;
                        }
                        refine(act, web, refiner, latest, text.trim(), "Krux travaille sur ta demande…", status, busy, dlg, null,
                                (ok, msg) -> {
                                    phoneJs(web, "KruxPhone.done(" + ok + "," + JSONObject.quote(ok ? "" : msg) + ")");
                                    if (ok && ttsReady[0] && tts[0] != null) {
                                        tts[0].setLanguage(Locale.FRENCH);
                                        tts[0].speak("C'est fait", TextToSpeech.QUEUE_FLUSH, null, "krux_done");
                                    }
                                });
                        return;
                    }
                    if (!"listen".equals(cmd) || busy.get()) return;
                    if (ContextCompat.checkSelfPermission(act, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(act, new String[]{Manifest.permission.RECORD_AUDIO}, 7731);
                        phoneJs(web, "KruxPhone.state(" + JSONObject.quote("Autorise le micro, puis touche le micro. Tu peux aussi écrire.") + ")");
                        return;
                    }
                    if (!SpeechRecognizer.isRecognitionAvailable(act)) {
                        phoneJs(web, "KruxPhone.state(" + JSONObject.quote("Dictée indisponible sur ce téléphone : écris ta demande.") + ")");
                        return;
                    }
                    if (rec[0] == null) {
                        rec[0] = SpeechRecognizer.createSpeechRecognizer(act);
                        rec[0].setRecognitionListener(new RecognitionListener() {
                            @Override public void onReadyForSpeech(Bundle p) { }
                            @Override public void onBeginningOfSpeech() { }
                            @Override public void onRmsChanged(float rms) { }
                            @Override public void onBufferReceived(byte[] buf) { }
                            @Override public void onEndOfSpeech() { }
                            @Override public void onEvent(int t, Bundle p) { }
                            @Override public void onError(int e) {
                                phoneJs(web, "KruxPhone.state(" + JSONObject.quote(e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                                        ? "Je n'ai rien entendu : touche le micro ou écris." : "Problème de micro : touche le micro ou écris.") + ")");
                            }
                            @Override public void onResults(Bundle r) {
                                ArrayList<String> m = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                                if (m == null || m.isEmpty() || m.get(0).trim().isEmpty()) {
                                    phoneJs(web, "KruxPhone.state(" + JSONObject.quote("Je n'ai rien entendu : touche le micro ou écris.") + ")");
                                    return;
                                }
                                phoneJs(web, "KruxPhone.heard(" + JSONObject.quote(m.get(0).trim()) + ",true)");
                            }
                            @Override public void onPartialResults(Bundle p) {
                                ArrayList<String> m = p.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                                if (m != null && !m.isEmpty()) phoneJs(web, "KruxPhone.heard(" + JSONObject.quote(m.get(0)) + ",false)");
                            }
                        });
                    }
                    Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR");
                    i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                    rec[0].startListening(i);
                });
            }

            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.END);
            row.setPadding(0, dp(act, 12), 0, 0);
            TextView cancel = button(act, "Annuler", tx2, Color.TRANSPARENT);
            int onAccent = androidx.core.graphics.ColorUtils.calculateLuminance(accent) > 0.5 ? Color.BLACK : Color.WHITE;
            TextView ok = button(act, "Créer l'app", onAccent, accent);
            cancel.setOnClickListener(v -> { decided.countDown(); dlg.dismiss(); });
            final int[] autoFixes = {0};
            ok.setOnClickListener(v -> {
                if (busy.get()) return; // Krux travaille encore : on attend sa réponse
                web.evaluateJavascript("KruxPreview.result()", value -> {
                    if (refiner != null && autoFixes[0] < 2 && hasErrors(value)) {
                        autoFixes[0]++;
                        refine(act, web, refiner, latest, "Corrige les erreurs d'exécution de l'aperçu (écran blanc ou message d'erreur) sans rien changer d'autre.",
                            "Erreur dans l'aperçu : Krux la corrige (" + autoFixes[0] + "/2)…", status, busy, dlg, null);
                        return;
                    }
                    result.set(parse(value, latest.get()));
                    decided.countDown();
                    dlg.dismiss();
                });
            });
            row.addView(cancel);
            LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            okLp.leftMargin = dp(act, 8);
            row.addView(ok, okLp);
            root.addView(row);

            dlg.setContentView(root);
            dlg.setOnDismissListener(d -> {
                bridge.set(null);
                if (rec[0] != null) { rec[0].destroy(); rec[0] = null; }
                if (tts[0] != null) { tts[0].stop(); tts[0].shutdown(); tts[0] = null; }
                web.stopLoading();
                frame.removeAllViews();
                web.destroy();
                decided.countDown(); // sécurité : jamais d'attente infinie
            });
            shown[0] = dlg;
            dlg.show();
            if (dlg.getWindow() != null) {
                DisplayMetrics dm = act.getResources().getDisplayMetrics();
                dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                dlg.getWindow().setLayout(dm.widthPixels - dp(act, 20), (int) (dm.heightPixels * 0.88f));
                dlg.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
        });

        try {
            if (!decided.await(20, TimeUnit.MINUTES)) {
                act.runOnUiThread(() -> { if (shown[0] != null) shown[0].dismiss(); });
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        return result.get();
    }

    /** Vrai si l'aperçu a signalé une erreur d'exécution (champ hasErrors de KruxPreview.result()). */
    private static boolean hasErrors(String value) {
        try {
            String inner = new JSONArray("[" + value + "]").getString(0);
            return new JSONObject(inner).optBoolean("hasErrors", false);
        } catch (Exception e) {
            return false;
        }
    }

    /** Demande à Krux (hors thread principal) puis recharge l'aperçu. En cas d'échec, l'aperçu reste tel quel et le message le dit. */
    private static void phoneJs(WebView web, String code) {
        web.evaluateJavascript(code, null);
    }

    private static void refine(final Activity act, final WebView web, final com.virtixstudio.kruxai.AppBuilder.Refiner refiner,
                               final AtomicReference<Map<String, String>> latest, final String request, final String working,
                               final TextView status, final AtomicBoolean busy, final Dialog dlg, final Runnable done) {
        refine(act, web, refiner, latest, request, working, status, busy, dlg, done, null);
    }

    /** result (ok, message) est appelé à la fin, pour que le faux téléphone dise « C'est fait » ou l'erreur. */
    private static void refine(final Activity act, final WebView web, final com.virtixstudio.kruxai.AppBuilder.Refiner refiner,
                               final AtomicReference<Map<String, String>> latest, final String request, final String working,
                               final TextView status, final AtomicBoolean busy, final Dialog dlg, final Runnable done,
                               final java.util.function.BiConsumer<Boolean, String> result) {
        if (!busy.compareAndSet(false, true)) return;
        status.setVisibility(View.VISIBLE);
        status.setText(working);
        web.evaluateJavascript("KruxPreview.result()", value -> new Thread(() -> {
            Map<String, String> out = null;
            String msg;
            try {
                out = refiner.refine(request, parse(value, latest.get()));
                msg = "Fait. Vérifie le résultat dans l'aperçu.";
            } catch (Exception e) {
                String m = e.getMessage();
                msg = "Krux n'a pas pu appliquer ça : " + (m == null || m.isEmpty() ? "erreur inconnue" : m) + " L'aperçu n'a pas changé.";
            }
            final Map<String, String> fin = out;
            final String fm = msg;
            act.runOnUiThread(() -> {
                busy.set(false);
                if (!dlg.isShowing()) return;
                status.setText(fm);
                if (fin != null) {
                    latest.set(fin);
                    web.evaluateJavascript("KruxPreview.load(" + JSONObject.quote(new JSONObject(fin).toString()) + ")", null);
                    if (done != null) done.run();
                }
                if (result != null) result.accept(fin != null, fm);
            });
        }).start());
    }

    /** Résultat de KruxPreview.result() : texte JSON entouré de guillemets (evaluateJavascript). Si illisible : fichiers d'origine. */
    private static Map<String, String> parse(String value, Map<String, String> fallback) {
        try {
            String inner = new JSONArray("[" + value + "]").getString(0);
            JSONObject f = new JSONObject(inner).getJSONObject("files");
            Map<String, String> out = new LinkedHashMap<>();
            JSONArray names = f.names();
            for (int i = 0; names != null && i < names.length(); i++) out.put(names.getString(i), f.getString(names.getString(i)));
            return out.isEmpty() ? new LinkedHashMap<>(fallback) : out;
        } catch (Exception e) {
            return new LinkedHashMap<>(fallback);
        }
    }

    private static int dp(Activity a, int v) { return Math.round(v * a.getResources().getDisplayMetrics().density); }

    private static TextView button(Activity a, String label, int textColor, int fill) {
        TextView b = new TextView(a);
        b.setText(label);
        b.setTextSize(15);
        b.setTypeface(null, android.graphics.Typeface.BOLD);
        b.setTextColor(textColor);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(a, 20), dp(a, 12), dp(a, 20), dp(a, 12));
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(a, 14));
        b.setBackground(g);
        b.setClickable(true);
        return b;
    }
}
