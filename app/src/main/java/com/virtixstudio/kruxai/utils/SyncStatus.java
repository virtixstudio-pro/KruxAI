package com.virtixstudio.kruxai.utils;

/** Texte lisible sur l'état de la synchronisation (Paramètres). Java pur, testé. */
public final class SyncStatus {

    private SyncStatus() { }

    public static String ago(long then, long now) {
        long s = Math.max(0, (now - then) / 1000);
        if (s < 60) return "à l'instant";
        if (s < 3600) return "il y a " + (s / 60) + " min";
        if (s < 86400) return "il y a " + (s / 3600) + " h";
        return "il y a " + (s / 86400) + " j";
    }

    /** Traduit une erreur technique en phrase compréhensible, avec la cause probable. */
    public static String explain(String err) {
        if (err == null || err.trim().isEmpty()) return "";
        String e = err.toLowerCase();
        if (e.contains("non configur") || e.contains("adresse")) return "adresse du serveur absente dans l'app";
        if (e.contains("401") || e.contains("non connect")) return "le serveur refuse ton identifiant (variable FIREBASE_PROJECT_ID du Worker ?)";
        if (e.contains("500") || e.contains("liaison")) return "le serveur n'est pas réglé (liaison DB ou KV du Worker ?)";
        if (e.contains("413") || e.contains("quota")) return "quota de synchronisation dépassé";
        if (e.contains("unable to resolve") || e.contains("unknownhost") || e.contains("timed out") || e.contains("timeout") || e.contains("connect")) return "pas de connexion Internet";
        return err.length() > 120 ? err.substring(0, 117) + "…" : err;
    }

    public static String line(long lastOk, String lastErr, long now) {
        String ex = explain(lastErr);
        if (!ex.isEmpty()) return "Problème : " + ex + (lastOk > 0 ? " · dernier succès " + ago(lastOk, now) : "");
        if (lastOk <= 0) return "Pas encore synchronisé";
        return "À jour · " + ago(lastOk, now);
    }
}
