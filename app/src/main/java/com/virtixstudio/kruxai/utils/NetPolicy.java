package com.virtixstudio.kruxai.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Règles hors connexion : quelles erreurs sont des coupures réseau, quand réessayer, quoi afficher. Java pur, testé. */
public final class NetPolicy {

    public static final int MAX_AUTO = 4;
    private static final int[] BACKOFF = { 3, 8, 20, 45 };

    private NetPolicy() { }

    /** Vrai si le message d'erreur (celui de l'API ou le texte lisible) vient d'une coupure réseau ou d'un délai dépassé. */
    public static boolean isNetworkMessage(String msg) {
        if (msg == null) return false;
        String m = msg.toLowerCase(Locale.ROOT);
        return m.contains("joindre le service") || m.contains("vérifie ta connexion") || m.contains("verifie ta connexion")
                || m.contains("unable to resolve") || m.contains("unknownhost") || m.contains("timed out") || m.contains("timeout")
                || m.contains("connection reset") || m.contains("connection refused") || m.contains("network is unreachable")
                || m.contains("failed to connect") || m.contains("software caused connection abort") || m.contains("broken pipe")
                || m.contains("prend plus de temps");
    }

    /** Secondes avant le nouvel essai numéro attempt (0, 1, 2…). */
    public static int backoffSeconds(int attempt) {
        return BACKOFF[Math.max(0, Math.min(attempt, BACKOFF.length - 1))];
    }

    public static String retryText(int secondsLeft, int attempt) {
        return "Connexion instable. Nouvel essai dans " + secondsLeft + " s (" + (attempt + 1) + "/" + MAX_AUTO + ")…";
    }

    public static String waitingText() {
        return "Hors connexion. Ton message est gardé : je le renverrai tout seul dès que le réseau revient.";
    }

    public static final String BANNER_OFFLINE = "Hors connexion · tes messages sont gardés et partiront au retour du réseau";
    public static final String BANNER_BACK = "De nouveau en ligne";

    // ---------- file d'attente persistée (texte simple : un message par entrée, échappé) ----------

    public static String encodeQueue(List<String> prompts) {
        StringBuilder sb = new StringBuilder();
        for (String p : prompts) {
            if (p == null || p.trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append('\u001e');
            sb.append(p.replace('\u001e', ' '));
        }
        return sb.toString();
    }

    public static List<String> decodeQueue(String s) {
        List<String> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        for (String p : s.split("\u001e")) if (!p.trim().isEmpty() && out.size() < 20) out.add(p);
        return out;
    }
}
