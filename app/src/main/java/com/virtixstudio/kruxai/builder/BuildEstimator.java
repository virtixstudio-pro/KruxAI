package com.virtixstudio.kruxai.builder;

import java.util.Locale;

/** Estime l'ampleur d'une demande d'app avant de lancer la création. Java pur. */
public final class BuildEstimator {

    public static final class Estimate {
        public final double score;
        public final int files, minutesMin, minutesMax;
        public final boolean large;
        Estimate(double score, int files, int minutesMin, int minutesMax, boolean large) {
            this.score = score; this.files = files; this.minutesMin = minutesMin; this.minutesMax = minutesMax; this.large = large;
        }
        public String message() {
            return "Cette application est ambitieuse : environ " + files + " fichiers et " + minutesMin + " à " + minutesMax
                + " minutes. Plus c'est gros, plus le risque d'échec ou de limite des moteurs augmente.\n\n"
                + "Je te conseille la version simplifiée (fonctions essentielles d'abord). Tu pourras ajouter le reste ensuite.";
        }
    }

    private static final String[] HEAVY = {
        "connexion", "compte", "login", "base de donn", "firebase", "paiement", "chat", "messagerie", "carte", "gps",
        "bluetooth", "cam", "notification", "synchro", "multijoueur", "jeu", "api", "serveur", "vidéo", "video",
        "audio", "ia ", "intelligence", "boutique", "panier", "calendrier", "graphique", "statistique"
    };

    private BuildEstimator() { }

    public static Estimate estimate(String description) {
        String d = description == null ? "" : description.toLowerCase(Locale.ROOT);
        int words = d.trim().isEmpty() ? 0 : d.trim().split("\\s+").length;
        int hits = 0;
        for (String k : HEAVY) if (d.contains(k)) hits++;
        int parts = 0;
        for (String p : d.split("[\\n;,]|\\bet\\b|\\bavec\\b|\\bplus\\b")) if (p.trim().length() > 3) parts++;
        double score = words / 25.0 + hits * 1.2 + Math.max(0, parts - 1) * 0.4;
        int files = (int) Math.round(Math.min(40, 8 + score * 3));
        int min = 2 + (int) Math.round(score), max = min + 3 + (int) Math.round(score / 2);
        return new Estimate(score, files, min, max, score >= 6.0);
    }

    /** Même demande, réduite aux fonctions essentielles. */
    public static String simplified(String description) {
        return description.trim()
            + "\n\nVERSION SIMPLIFIÉE : ne fais que les fonctions essentielles, 3 écrans maximum, aucune bibliothèque externe, "
            + "code court. Les fonctions secondaires seront ajoutées plus tard.";
    }
}
