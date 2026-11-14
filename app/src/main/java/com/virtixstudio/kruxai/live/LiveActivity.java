package com.virtixstudio.kruxai.live;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import com.virtixstudio.kruxai.utils.VoiceStyler;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.virtixstudio.kruxai.api.ApiClient;
import com.virtixstudio.kruxai.models.KruxModel;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Mode Live : écran noir, le visage de KRUX bouge, t'écoute, réfléchit et te répond à voix haute.
 * Touche le micro pour parler. Touche-le pendant qu'il parle pour l'arrêter.
 */
public class LiveActivity extends AppCompatActivity {

    public static final String EXTRA_MODEL = "model";

    private static final String SYSTEM =
        "Tu es Krux, créé par Virtix Studio, en conversation VOCALE avec l'utilisateur. "
      + "Réponds en français, en 1 à 3 phrases courtes et naturelles, comme à l'oral. "
      + "Pas de markdown, pas de listes, pas d'émojis, pas de balises, pas d'outils. "
      + "Si tu ne sais pas, dis-le simplement.";

    private KruxLiveView face;
    private TextView status;
    private TextView mic;
    private LiveMotion motion;
    private KruxModel model = KruxModel.KRUX_35_FLASH;

    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady;
    private boolean listening;
    private final ArrayList<String> history = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try { model = KruxModel.valueOf(getIntent().getStringExtra(EXTRA_MODEL)); } catch (Exception ignore) { /* modèle par défaut */ }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        WindowInsetsControllerCompat ic = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        ic.hide(WindowInsetsCompat.Type.systemBars());
        ic.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        face = new KruxLiveView(this);
        motion = face.motion();
        root.addView(face, new FrameLayout.LayoutParams(-1, -1));

        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextColor(0xFF9CA3AF);
        close.setTextSize(22);
        close.setPadding(dp(20), dp(28), dp(20), dp(20));
        close.setOnClickListener(v -> finish());
        close.setContentDescription("Fermer");
        root.addView(close, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));

        status = new TextView(this);
        status.setTextColor(0xFFBFA8D6);
        status.setTextSize(15);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(32), 0, dp(32), 0);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        sp.bottomMargin = dp(150);
        root.addView(status, sp);

        mic = new TextView(this);
        mic.setText("🎙");
        mic.setTextSize(30);
        mic.setGravity(Gravity.CENTER);
        mic.setTypeface(null, Typeface.BOLD);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Color.BLACK);
        g.setStroke(dp(3), 0xFF22D3EE);
        mic.setBackground(g);
        mic.setContentDescription("Parler à Krux");
        mic.setOnClickListener(v -> onMicTapped());
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(dp(84), dp(84), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        mp.bottomMargin = dp(40);
        root.addView(mic, mp);

        setContentView(root);
        setStatus("Touche le micro pour parler");

        tts = new TextToSpeech(this, st -> {
            if (st == TextToSpeech.SUCCESS) {
                VoiceStyler.prepare(tts, Locale.FRENCH);
                ttsReady = true;
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { ui(() -> { motion.setState(LiveMotion.State.SPEAKING); setStatus(""); }); }
                    @Override public void onDone(String id) { if (VoiceStyler.isPart(id)) return; ui(() -> { motion.setState(LiveMotion.State.IDLE); setStatus("Touche le micro pour parler"); }); }
                    @Override public void onError(String id) { onDone(id); }
                    @android.annotation.TargetApi(26) @Override public void onRangeStart(String id, int start, int end, int frame) { ui(motion::onWord); }
                });
            }
        });
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private void ui(Runnable r) { runOnUiThread(r); }

    private void setStatus(String s) { status.setText(s); }

    // ------------------------------------------------------------------ écoute

    private void onMicTapped() {
        if (tts != null && tts.isSpeaking()) {
            tts.stop();
            motion.setState(LiveMotion.State.IDLE);
            setStatus("Touche le micro pour parler");
            return;
        }
        if (listening) { stopListening(); return; }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 7);
            return;
        }
        startListening();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == 7 && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) startListening();
        else setStatus("Autorise le micro pour parler à Krux");
    }

    private void startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { setStatus("La reconnaissance vocale n'est pas disponible sur ce téléphone"); return; }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle p) { }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float rms) { motion.setInputLevel((rms + 2f) / 12f); }
                @Override public void onBufferReceived(byte[] buf) { }
                @Override public void onEndOfSpeech() { }
                @Override public void onError(int e) {
                    listening = false;
                    motion.setInputLevel(0);
                    motion.setState(LiveMotion.State.IDLE);
                    setStatus(e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                        ? "Je n'ai rien entendu, réessaie" : "Problème de micro, réessaie");
                }
                @Override public void onResults(Bundle r) {
                    listening = false;
                    motion.setInputLevel(0);
                    ArrayList<String> m = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (m == null || m.isEmpty() || m.get(0).trim().isEmpty()) { motion.setState(LiveMotion.State.IDLE); setStatus("Je n'ai rien entendu, réessaie"); return; }
                    ask(m.get(0).trim());
                }
                @Override public void onPartialResults(Bundle p) {
                    ArrayList<String> m = p.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (m != null && !m.isEmpty()) setStatus(m.get(0));
                }
                @Override public void onEvent(int t, Bundle p) { }
            });
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR");
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        listening = true;
        motion.setState(LiveMotion.State.LISTENING);
        setStatus("Je t'écoute…");
        recognizer.startListening(i);
    }

    private void stopListening() {
        if (recognizer != null) recognizer.stopListening();
    }

    // ------------------------------------------------------------------ réponse

    private void ask(final String userText) {
        motion.setState(LiveMotion.State.THINKING);
        setStatus("Je réfléchis…");
        StringBuilder ctx = new StringBuilder();
        for (String h : history) ctx.append(h).append('\n');
        ctx.append("Utilisateur: ").append(userText);

        ApiClient.sendRequest(model, SYSTEM, ctx.toString(), new ApiClient.ApiCallback() {
            @Override public void onSuccess(String response, String brand) {
                final String clean = cleanForSpeech(response);
                ui(() -> {
                    history.add("Utilisateur: " + userText);
                    history.add("Krux: " + clean);
                    while (history.size() > 8) history.remove(0);
                    speak(clean);
                });
            }
            @Override public void onError(String msg) {
                ui(() -> { motion.setState(LiveMotion.State.IDLE); motion.flash(LiveMotion.Mood.SURPRISED, 1.2f); setStatus("Je n'arrive pas à répondre : " + msg); });
            }
            @Override public void onCancelled() { ui(() -> motion.setState(LiveMotion.State.IDLE)); }
        });
    }

    /** Retire ce qu'on ne doit pas lire à voix haute (réflexion, balises d'outils, markdown). */
    static String cleanForSpeech(String s) {
        if (s == null) return "";
        String t = s.replaceAll("(?s)<REFLEXION>.*?</REFLEXION>", " ")
                    .replaceAll("(?s)<KRUX_TOOL>.*?</KRUX_TOOL>", " ")
                    .replaceAll("(?s)```.*?```", " ")
                    .replaceAll("[*_#`>]+", "")
                    .replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1")
                    .replaceAll("\\s+", " ").trim();
        return t.length() > 3000 ? t.substring(0, 3000) : t;
    }

    private void speak(String text) {
        if (text.isEmpty()) { motion.setState(LiveMotion.State.IDLE); setStatus("Touche le micro pour parler"); return; }
        if (!ttsReady) { motion.setState(LiveMotion.State.IDLE); setStatus(text); return; }
        motion.flash(LiveMotion.Mood.HAPPY, 0.8f);
        VoiceStyler.speak(tts, text, "krux_live");
        if (Build.VERSION.SDK_INT < 26) motion.setState(LiveMotion.State.SPEAKING);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (tts != null) tts.stop();
        if (recognizer != null && listening) { recognizer.cancel(); listening = false; }
        motion.setState(LiveMotion.State.IDLE);
    }

    @Override
    protected void onDestroy() {
        if (recognizer != null) recognizer.destroy();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
