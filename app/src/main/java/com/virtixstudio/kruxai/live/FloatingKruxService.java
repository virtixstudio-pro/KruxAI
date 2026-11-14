package com.virtixstudio.kruxai.live;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.virtixstudio.kruxai.api.ApiClient;
import com.virtixstudio.kruxai.models.KruxModel;
import java.util.ArrayList;
import java.util.Locale;

import com.virtixstudio.kruxai.R;
import com.virtixstudio.kruxai.ui.MainActivity;

/**
 * Visage de KRUX transparent qui flotte par-dessus les autres apps. Se déplace au doigt,
 * un appui ouvre KRUX. Une notification permet de le masquer à tout moment.
 * Demande l'autorisation « Afficher par-dessus les autres apps » (gérée par FloatingKrux.toggle).
 */
public class FloatingKruxService extends Service {

    static final String ACTION_STOP = "com.virtixstudio.kruxai.FLOAT_STOP";
    private static final String CHANNEL = "krux_floating";
    private static volatile boolean running = false;

    // --- Le visage flottant suit la voix : l'app lui dit quand KRUX réfléchit, parle ou écoute.
    private static volatile KruxLiveView liveFace;
    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());

    public static void setState(final LiveMotion.State st) {
        if (liveFace == null) return;
        MAIN.post(() -> { KruxLiveView f = liveFace; if (f != null) f.motion().setState(st); });
    }

    /** Niveau du micro, de 0 à 1 (bouche et yeux réagissent quand tu parles). */
    public static void level(final float v) {
        if (liveFace == null) return;
        MAIN.post(() -> { KruxLiveView f = liveFace; if (f != null) f.motion().setInputLevel(v); });
    }

    /** Un mot vient d'être prononcé : la bouche bouge. */
    public static void word() {
        if (liveFace == null) return;
        MAIN.post(() -> { KruxLiveView f = liveFace; if (f != null) f.motion().onWord(); });
    }

    public static void flash(final LiveMotion.Mood m, final float seconds) {
        if (liveFace == null) return;
        MAIN.post(() -> { KruxLiveView f = liveFace; if (f != null) f.motion().flash(m, seconds); });
    }

    private WindowManager wm;
    private KruxLiveView face;
    private WindowManager.LayoutParams lp;

    public static boolean isRunning() { return running; }

    /** Démarre ou arrête le visage ; retourne false si l'autorisation d'affichage manque (l'écran de réglage s'ouvre). */
    public static boolean toggle(Context c) {
        if (running) {
            c.stopService(new Intent(c, FloatingKruxService.class));
            return true;
        }
        if (!Settings.canDrawOverlays(c)) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + c.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return false;
        }
        if (ContextCompat.checkSelfPermission(c, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            if (c instanceof android.app.Activity) {
                ActivityCompat.requestPermissions((android.app.Activity) c, new String[]{Manifest.permission.RECORD_AUDIO}, 21);
            }
            Toast.makeText(c, "Autorise le micro pour que KRUX puisse t'écouter, puis réessaie.", Toast.LENGTH_LONG).show();
            return false;
        }
        Intent i = new Intent(c, FloatingKruxService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        return true;
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (face != null) return START_NOT_STICKY;
        startForegroundCompat();
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY; }
        addFace();
        running = true;
        return START_NOT_STICKY;
    }

    private void startForegroundCompat() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Visage de KRUX", NotificationManager.IMPORTANCE_LOW));
        }
        int imm = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), imm);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, FloatingKruxService.class).setAction(ACTION_STOP), imm);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_live).setContentTitle("KRUX est sur ton écran")
                .setContentText("Touche pour ouvrir. Masquer avec le bouton.").setContentIntent(open).setOngoing(true)
                .addAction(new Notification.Action.Builder((android.graphics.drawable.Icon) null, "Masquer", stop).build());
        if (Build.VERSION.SDK_INT >= 34) startForeground(7, b.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE | ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        else if (Build.VERSION.SDK_INT >= 30) startForeground(7, b.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        else startForeground(7, b.build());
    }

    private void addFace() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        face = new KruxLiveView(this);
        face.setTransparent(true);
        face.setMaxFps(30);
        float d = getResources().getDisplayMetrics().density;
        int size = Math.round(140 * d);
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        lp = new WindowManager.LayoutParams(size, size, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = getResources().getDisplayMetrics().widthPixels - size - Math.round(8 * d);
        lp.y = Math.round(220 * d);
        face.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY; int startX, startY; boolean moved, longFired;
            final Runnable longPress = () -> { longFired = true; openApp(); };
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX(); downY = e.getRawY(); startX = lp.x; startY = lp.y; moved = false; longFired = false;
                        MAIN.postDelayed(longPress, 700);
                        face.motion().flash(LiveMotion.Mood.SURPRISED, 0.6f);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                        if (Math.abs(dx) + Math.abs(dy) > 12 * d) { moved = true; MAIN.removeCallbacks(longPress); }
                        if (moved) { lp.x = Math.round(startX + dx); lp.y = Math.round(startY + dy); wm.updateViewLayout(face, lp); }
                        return true;
                    case MotionEvent.ACTION_UP:
                        MAIN.removeCallbacks(longPress);
                        if (!moved && !longFired) onFaceTapped();
                        return true;
                }
                return false;
            }
        });
        try {
            wm.addView(face, lp);
            liveFace = face;
        } catch (Exception e) {
            face = null;
            stopSelf();
        }
    }


    // ------------------------------------------------------------------ voix : le visage écoute, réfléchit et parle tout seul

    private static final String SYSTEM =
        "Tu es Krux, créé par Virtix Studio, en conversation VOCALE avec l'utilisateur, pendant qu'il utilise son téléphone. "
      + "Réponds en français, en 1 à 3 phrases courtes et naturelles, comme à l'oral. "
      + "Pas de markdown, pas de listes, pas d'émojis, pas de balises, pas d'outils. "
      + "Si le contenu de l'écran est fourni, appuie-toi dessus pour répondre. Si tu ne sais pas, dis-le simplement.";

    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady, listening;
    private final ArrayList<String> history = new ArrayList<>();

    private void openApp() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(i);
    }

    private void initTts() {
        if (tts != null) return;
        tts = new TextToSpeech(this, st -> {
            if (st == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.FRENCH);
                ttsReady = true;
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { setState(LiveMotion.State.SPEAKING); }
                    @Override public void onDone(String id) { setState(LiveMotion.State.IDLE); }
                    @Override public void onError(String id) { setState(LiveMotion.State.IDLE); }
                    @android.annotation.TargetApi(26) @Override public void onRangeStart(String id, int s, int e, int f) { word(); }
                });
            }
        });
    }

    private void onFaceTapped() {
        if (tts != null && tts.isSpeaking()) { tts.stop(); setState(LiveMotion.State.IDLE); return; }
        if (listening) { if (recognizer != null) recognizer.stopListening(); return; }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Autorise le micro dans KRUX, puis réessaie.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "La reconnaissance vocale n'est pas disponible sur ce téléphone.", Toast.LENGTH_LONG).show();
            return;
        }
        initTts();
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle p) { }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float rms) { level((rms + 2f) / 12f); }
                @Override public void onBufferReceived(byte[] b) { }
                @Override public void onEndOfSpeech() { }
                @Override public void onError(int e) {
                    listening = false; level(0); setState(LiveMotion.State.IDLE);
                    if (e != SpeechRecognizer.ERROR_NO_MATCH && e != SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                        Toast.makeText(FloatingKruxService.this, "Problème de micro, réessaie.", Toast.LENGTH_SHORT).show();
                }
                @Override public void onResults(Bundle r) {
                    listening = false; level(0);
                    ArrayList<String> m = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (m == null || m.isEmpty() || m.get(0).trim().isEmpty()) { setState(LiveMotion.State.IDLE); return; }
                    ask(m.get(0).trim());
                }
                @Override public void onPartialResults(Bundle p) { }
                @Override public void onEvent(int t, Bundle p) { }
            });
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR");
        listening = true;
        setState(LiveMotion.State.LISTENING);
        recognizer.startListening(i);
    }

    /** Vrai si la question parle de ce qui est affiché. */
    static boolean wantsScreen(String q) {
        String s = q.toLowerCase(Locale.FRENCH);
        return s.contains("écran") || s.contains("ecran") || s.contains("lis ") || s.contains("lis-moi")
            || s.contains("cette page") || s.contains("ce message") || s.contains("ce texte") || s.contains("ici")
            || s.contains("résume") || s.contains("resume") || s.contains("traduis") || s.contains("qu'est-ce que c'est");
    }

    private void ask(final String q) {
        setState(LiveMotion.State.THINKING);
        String screen = null;
        if (wantsScreen(q)) {
            if (!ScreenReaderService.isOn()) {
                say("Pour lire ton écran, active KRUX dans les réglages d'accessibilité.");
                Intent a = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                a.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { startActivity(a); } catch (Exception ignored) { }
                return;
            }
            screen = ScreenReaderService.read();
        }
        StringBuilder ctx = new StringBuilder();
        for (String h : history) ctx.append(h).append('\n');
        if (screen != null) ctx.append("[Contenu de l'écran]\n").append(screen.isEmpty() ? "(rien de lisible)" : screen).append("\n[Fin]\n");
        ctx.append("Utilisateur: ").append(q);
        ApiClient.sendRequest(KruxModel.KRUX_35_FLASH, SYSTEM, ctx.toString(), new ApiClient.ApiCallback() {
            @Override public void onSuccess(String response, String brand) {
                final String clean = LiveActivity.cleanForSpeech(response);
                MAIN.post(() -> {
                    history.add("Utilisateur: " + q); history.add("Krux: " + clean);
                    while (history.size() > 8) history.remove(0);
                    say(clean);
                });
            }
            @Override public void onError(String msg) {
                MAIN.post(() -> { setState(LiveMotion.State.IDLE); flash(LiveMotion.Mood.SURPRISED, 1.2f);
                    Toast.makeText(FloatingKruxService.this, "Je n'arrive pas à répondre : " + msg, Toast.LENGTH_LONG).show(); });
            }
            @Override public void onCancelled() { MAIN.post(() -> setState(LiveMotion.State.IDLE)); }
        });
    }

    private void say(String text) {
        initTts();
        if (text.isEmpty() || !ttsReady) { setState(LiveMotion.State.IDLE); if (!text.isEmpty()) Toast.makeText(this, text, Toast.LENGTH_LONG).show(); return; }
        flash(LiveMotion.Mood.HAPPY, 0.8f);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "krux_float");
        if (Build.VERSION.SDK_INT < 26) setState(LiveMotion.State.SPEAKING);
    }

    @Override
    public void onDestroy() {
        MAIN.removeCallbacksAndMessages(null);
        if (recognizer != null) { recognizer.destroy(); recognizer = null; }
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        running = false;
        liveFace = null;
        try { if (face != null && wm != null) wm.removeView(face); } catch (Exception ignored) { }
        face = null;
        super.onDestroy();
    }
}
