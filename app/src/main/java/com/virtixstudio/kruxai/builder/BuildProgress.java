package com.virtixstudio.kruxai.builder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Texte de progression affiché dans la bulle de KRUX pendant la création d'une app :
 * un titre, l'étape en cours et la liste des fichiers déjà écrits. Java pur, thread-safe.
 */
public final class BuildProgress {

    public static final int MAX_LINES = 12;

    private static final class Entry {
        int bytes;
        boolean fixed;
    }

    private final Map<String, Entry> files = new LinkedHashMap<>();
    private String title = "J'écris ton application…";
    private String step = "Démarrage…";
    private String note = "";
    private boolean done, failed;
    private int tick = -1;
    private int elapsed = -1;

    public synchronized void setTitle(String t) { if (!done && !failed && t != null) title = t; }
    public synchronized void setStep(String s) { if (!done && !failed && s != null) step = s; }

    /** Animation « vivante » : points qui avancent (tick) et temps écoulé en secondes. Sans appel, rien ne change. */
    public synchronized void setPulse(int tickCount, int elapsedSeconds) { tick = Math.max(0, tickCount); elapsed = Math.max(0, elapsedSeconds); }

    static String clock(int s) { return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60); }

    /** Ajoute un fichier, ou met à jour sa taille s'il a été corrigé. */
    public synchronized void addFile(String path, int bytes, boolean fixed) {
        if (path == null || path.isEmpty()) return;
        Entry e = files.get(path);
        if (e == null) { e = new Entry(); files.put(path, e); }
        e.bytes = Math.max(0, bytes);
        e.fixed = e.fixed || fixed;
    }

    public synchronized void finish(String t, String n) { done = true; title = t; note = n == null ? "" : n; }
    public synchronized void fail(String message) { failed = true; title = "Je n'ai pas pu finir"; note = message == null ? "" : message; }

    public synchronized int fileCount() { return files.size(); }

    static String size(int b) {
        if (b < 1024) return b + " o";
        double k = b / 1024.0;
        return (k < 10 ? String.format(java.util.Locale.FRANCE, "%.1f", k) : String.valueOf(Math.round(k))) + " Ko";
    }

    public synchronized String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(title).append("**\n\n");
        if (!done && !failed) {
            sb.append(step);
            if (tick >= 0) sb.append(" ").append(".".repeat(1 + tick % 3));
            if (elapsed >= 0) sb.append("  ·  ⏱ ").append(clock(elapsed));
            sb.append("\n\n");
        }
        else if (!note.isEmpty()) sb.append(note).append("\n\n");

        int total = files.size(), skip = Math.max(0, total - MAX_LINES), i = 0;
        if (skip > 0) sb.append("… ").append(skip).append(skip == 1 ? " fichier plus haut\n" : " fichiers plus haut\n");
        for (Map.Entry<String, Entry> e : files.entrySet()) {
            if (i++ < skip) continue;
            sb.append("✓ `").append(e.getKey()).append("` · ").append(size(e.getValue().bytes));
            if (e.getValue().fixed) sb.append(" · corrigé");
            sb.append('\n');
        }
        if (total > 0) sb.append('\n').append(total).append(total == 1 ? " fichier" : " fichiers").append('\n');
        return sb.toString().trim();
    }
}
