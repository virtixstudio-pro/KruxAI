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

    /** Nom court affiché : le fichier seul, ou « dossier/fichier » pour les ressources (layout/activity_main.xml). */
    static String shortName(String path) {
        String p = path.replace('\\', '/');
        String[] seg = p.split("/");
        String name = seg[seg.length - 1];
        int ri = p.indexOf("/res/");
        if (ri >= 0 && seg.length >= 2) name = seg[seg.length - 2] + "/" + name;
        return name.length() > 28 ? "…" + name.substring(name.length() - 27) : name;
    }

    private static String pad(String s, int w, boolean left) {
        StringBuilder sb = new StringBuilder();
        for (int i = s.length(); i < w; i++) sb.append(' ');
        return left ? sb + s : s + sb;
    }

    public synchronized String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(title).append("**\n\n");
        if (!done && !failed) {
            sb.append(step);
            if (elapsed >= 0) sb.append("  ·  ⏱ ").append(clock(elapsed));
            sb.append("\n\n");
        }
        else if (!note.isEmpty()) sb.append(note).append("\n\n");

        int total = files.size();
        if (total > 0) {
            int skip = Math.max(0, total - MAX_LINES), i = 0, width = 0;
            for (Map.Entry<String, Entry> e : files.entrySet()) {
                if (i++ < skip) continue;
                width = Math.max(width, shortName(e.getKey()).length());
            }
            sb.append("```terminal\n");
            if (skip > 0) sb.append("… ").append(skip).append(skip == 1 ? " fichier plus haut\n" : " fichiers plus haut\n");
            i = 0;
            for (Map.Entry<String, Entry> e : files.entrySet()) {
                if (i++ < skip) continue;
                sb.append(e.getValue().fixed ? "↻ " : "✓ ")
                  .append(pad(shortName(e.getKey()), width, false)).append("  ")
                  .append(pad(size(e.getValue().bytes), 7, true)).append('\n');
            }
            sb.append("```\n\n").append(total).append(total == 1 ? " fichier" : " fichiers");
        }
        return sb.toString().trim();
    }
}
