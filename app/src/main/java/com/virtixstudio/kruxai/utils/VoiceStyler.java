package com.virtixstudio.kruxai.utils;

import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Rend la voix native Android plus vivante : meilleure voix installée, texte découpé en phrases,
 * pauses naturelles, intonation légèrement différente selon la ponctuation (question, exclamation,
 * suspension).
 *
 * Utilisation : VoiceStyler.prepare(tts, Locale.FRENCH) à l'init, puis VoiceStyler.speak(tts, texte, "id").
 * Dans le listener : ignorer onDone/onError des ids pour lesquels VoiceStyler.isPart(id) est vrai,
 * sinon l'état repasse à IDLE entre deux phrases.
 */
public final class VoiceStyler {

    private VoiceStyler() {}

    private static final float BASE_RATE = 1.04f;
    private static final float BASE_PITCH = 1.0f;
    private static final int MAX_CHUNK = 220;

    private static final Pattern EMOJI = Pattern.compile(
            "[\\x{1F000}-\\x{1FAFF}\\x{2190}-\\x{21FF}\\x{2300}-\\x{23FF}\\x{2600}-\\x{27BF}"
          + "\\x{2B00}-\\x{2BFF}\\x{FE0F}\\x{200D}\\x{20E3}\\x{E0020}-\\x{E007F}]+");

    /** À appeler une fois le moteur prêt : langue + meilleure voix disponible. */
    public static void prepare(TextToSpeech tts, Locale locale) {
        if (tts == null) return;
        tts.setLanguage(locale);
        tts.setSpeechRate(BASE_RATE);
        tts.setPitch(BASE_PITCH);
        if (Build.VERSION.SDK_INT < 21) return;
        try {
            Voice best = null;
            int bestScore = Integer.MIN_VALUE;
            for (Voice v : tts.getVoices()) {
                if (v == null || v.getLocale() == null) continue;
                if (!locale.getLanguage().equals(v.getLocale().getLanguage())) continue;
                if (v.getFeatures() != null && v.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) continue;
                int score = v.getQuality() * 10;
                if (!v.isNetworkConnectionRequired()) score += 15;      // fiable hors ligne
                if (locale.getCountry().equalsIgnoreCase(v.getLocale().getCountry())) score += 5;
                if (score > bestScore) { bestScore = score; best = v; }
            }
            if (best != null) tts.setVoice(best);
        } catch (Exception ignored) {
            // un moteur capricieux ne doit jamais empêcher de parler
        }
    }

    /** Vrai pour les phrases intermédiaires (id du type "base#2") : à ignorer dans onDone/onError. */
    public static boolean isPart(String id) {
        return id != null && id.contains("#");
    }

    /** Parle le texte phrase par phrase, avec pauses et intonation. */
    public static void speak(TextToSpeech tts, String raw, String baseId) {
        if (tts == null) return;
        List<String> chunks = split(clean(raw));
        if (chunks.isEmpty()) return;

        for (int i = 0; i < chunks.size(); i++) {
            String c = chunks.get(i);
            boolean last = i == chunks.size() - 1;
            String id = last ? baseId : baseId + "#" + i;

            float pitch = BASE_PITCH;
            float rate = BASE_RATE;
            int pause = 160;
            if (c.endsWith("?")) { pitch += 0.07f; pause = 120; }
            else if (c.endsWith("!")) { pitch += 0.04f; rate += 0.05f; pause = 140; }
            else if (c.endsWith("...") || c.endsWith("…")) { rate -= 0.06f; pause = 320; }
            else if (c.length() < 25) { rate += 0.02f; }

            tts.setPitch(pitch);
            tts.setSpeechRate(rate);
            tts.speak(c, i == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, params(), id);
            if (!last && Build.VERSION.SDK_INT >= 21) {
                tts.playSilentUtterance(pause, TextToSpeech.QUEUE_ADD, baseId + "#p" + i);
            }
        }
        tts.setPitch(BASE_PITCH);
        tts.setSpeechRate(BASE_RATE);
    }

    private static Bundle params() { return new Bundle(); }

    /** Retire emojis et symboles qui seraient lus à voix haute. */
    static String clean(String s) {
        if (s == null) return "";
        String t = LinkButtons.stripForSpeech(s)
                .replaceAll("(?s)<REFLEXION>.*?</REFLEXION>", " ")
                .replaceAll("(?s)<KRUX_TOOL>.*?</KRUX_TOOL>", " ")
                .replaceAll("(?s)```.*?```", " ")                       // blocs de code
                .replaceAll("!?\\[([^\\]]*)\\]\\([^)]*\\)", "$1")        // [texte](lien) -> texte
                .replaceAll("https?://\\S+", " ")                       // liens nus
                .replaceAll("(?m)^\\s*([-*+•]|\\d+[.)])\\s+", "")        // puces et numéros de liste
                .replaceAll("(?m)^\\s*>+\\s?", "")                      // citations
                .replaceAll("[*_~`#|]+", "");                           // gras, italique, titres, code, tableaux
        return EMOJI.matcher(t).replaceAll(" ")
                .replaceAll("[ \\t]+", " ")
                .trim();
    }

    /** Découpe en phrases, puis recoupe celles qui dépassent MAX_CHUNK à la virgule ou à l'espace. */
    static List<String> split(String text) {
        List<String> out = new ArrayList<>();
        if (text.isEmpty()) return out;
        // coupe après . ! ? … suivis d'un espace (les décimaux comme 3.5 restent entiers) ou sur retour ligne
        for (String part : text.split("(?<=[.!?…])\\s+|\\n+")) {
            String s = part.trim();
            if (s.isEmpty() || s.matches("[.!?…]+")) continue;
            while (s.length() > MAX_CHUNK) {
                int cut = s.lastIndexOf(',', MAX_CHUNK);
                if (cut < 60) cut = s.lastIndexOf(' ', MAX_CHUNK);
                if (cut < 1) cut = MAX_CHUNK;
                out.add(s.substring(0, cut + 1).trim());
                s = s.substring(cut + 1).trim();
            }
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }
}
