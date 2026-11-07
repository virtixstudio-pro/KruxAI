package com.virtixstudio.kruxai.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Mémoire des modèles IA temporairement limités (erreur 429, quota par minute).
 * Un modèle limité est sauté par les appels suivants jusqu'à la fin de sa pénalité,
 * au lieu d'être réessayé à chaque fois. Java pur : testé sans Android.
 */
public final class ModelCooldown {

    public static final long DEFAULT_MS = 60_000L;
    public static final long MIN_MS = 5_000L;
    public static final long MAX_MS = 120_000L;

    private static final Map<String, Long> UNTIL = new HashMap<>();

    private ModelCooldown() { }

    /** Marque un modèle comme limité. retryAfterMs <= 0 : durée par défaut (une minute). */
    public static synchronized void markLimited(String key, long now, long retryAfterMs) {
        long d = retryAfterMs <= 0 ? DEFAULT_MS : Math.max(MIN_MS, Math.min(MAX_MS, retryAfterMs));
        long end = now + d;
        Long old = UNTIL.get(key);
        if (old == null || old < end) UNTIL.put(key, end);
    }

    public static synchronized boolean isCooling(String key, long now) {
        return readyIn(key, now) > 0;
    }

    /** Millisecondes avant que le modèle soit de nouveau utilisable (0 = disponible). */
    public static synchronized long readyIn(String key, long now) {
        Long end = UNTIL.get(key);
        if (end == null) return 0;
        if (end <= now) { UNTIL.remove(key); return 0; }
        return end - now;
    }

    /** 0 si au moins un modèle est disponible, sinon le délai avant que le premier le soit. */
    public static synchronized long soonestReadyMs(Collection<String> keys, long now) {
        long best = Long.MAX_VALUE;
        for (String k : keys) best = Math.min(best, readyIn(k, now));
        return best == Long.MAX_VALUE ? 0 : best;
    }

    public static synchronized void clear() { UNTIL.clear(); }

    /** Vrai si le message d'erreur indique une limite de débit ou de quota. */
    public static boolean isRateLimit(String message) {
        if (message == null) return false;
        String m = message.toLowerCase(Locale.ROOT);
        return m.contains("http 429") || m.contains("rate limit") || m.contains("rate_limit")
            || m.contains("too many requests") || m.contains("resource_exhausted") || m.contains("quota");
    }

    /** Lit "retry-after=NN" (secondes, décimales permises) dans le message ; 0 si absent ou invalide. */
    public static long parseRetryAfterMs(String message) {
        if (message == null) return 0;
        int i = message.toLowerCase(Locale.ROOT).indexOf("retry-after=");
        if (i < 0) return 0;
        StringBuilder sb = new StringBuilder();
        for (int j = i + 12; j < message.length(); j++) {
            char c = message.charAt(j);
            if ((c >= '0' && c <= '9') || c == '.') sb.append(c); else break;
        }
        try {
            double s = Double.parseDouble(sb.toString());
            return s > 0 && s < 100000 ? (long) (s * 1000) : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
