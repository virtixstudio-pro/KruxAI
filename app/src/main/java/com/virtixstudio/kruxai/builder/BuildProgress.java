package com.virtixstudio.kruxai.builder;

import java.util.LinkedHashMap;
import java.util.Map;

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

    public synchronized void setTitle(String t) {
        if (!done && !failed && t != null) title = t;
    }

    public synchronized void setStep(String s) {
        if (!done && !failed && s != null) step = s;
    }

    public synchronized void setPulse(int tickCount, int elapsedSeconds) {
        tick = Math.max(0, tickCount);
        elapsed = Math.max(0, elapsedSeconds);
    }

    static String clock(int s) {
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    public synchronized void addFile(String path, int bytes, boolean fixed) {
        if (path == null || path.isEmpty()) return;
        Entry e = files.get(path);
        if (e == null) {
            e = new Entry();
            files.put(path, e);
        }
        e.bytes = Math.max(0, bytes);
        e.fixed = e.fixed || fixed;
    }

    public synchronized void finish(String t, String n) {
        done = true;
        title = t;
        note = n == null ? "" : n;
    }

    public synchronized void fail(String message) {
        failed = true;
        title = "Je n'ai pas pu finir";
        note = message == null ? "" : message;
    }

    public synchronized int fileCount() {
        return files.size();
    }

    static String size(int b) {
        if (b < 1024) return b + " o";
        double k = b / 1024.0;
        return (k < 10
            ? String.format(java.util.Locale.FRANCE, "%.1f", k)
            : String.valueOf(Math.round(k))) + " Ko";
    }

    static String shortName(String path) {
        String p = path.replace('\\', '/');
        String[] seg = p.split("/");
        String name = seg[seg.length - 1];
        int ri = p.indexOf("/res/");
        if (ri >= 0 && seg.length >= 2)
            name = seg[seg.length - 2] + "/" + name;
        return name.length() > 28 ? "…" + name.substring(name.length() - 27) : name;
    }

    public synchronized String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(title).append("**\n\n");

        if (!done && !failed) {
            sb.append(step);
            if (elapsed >= 0) {
                sb.append(" ");
                for (int i = 0; i < Math.min(3, tick + 1); i++) {
                    sb.append(".");
                }
                sb.append("  ·  ⏱ ").append(clock(elapsed));
            }
            sb.append("\n\n");
        } else if (!note.isEmpty()) {
            sb.append(note).append("\n\n");
        }

        int total = files.size();
        if (total > 0) {
            int skip = Math.max(0, total - MAX_LINES);
            int i = 0;

            sb.append("```terminal\n");
            if (skip > 0) {
                sb.append("… ").append(skip)
                  .append(skip == 1 ? " fichier plus haut\n" : " fichiers plus haut\n");
            }

            for (Map.Entry<String, Entry> e : files.entrySet()) {
                if (i++ < skip) continue;

                Entry f = e.getValue();
                sb.append("✓ `").append(shortName(e.getKey()))
                  .append("` · ").append(size(f.bytes));

                if (f.fixed) sb.append(" · corrigé");
                sb.append('\n');
            }

            sb.append("```\n\n")
              .append(total)
              .append(total == 1 ? " fichier" : " fichiers");
        }

        return sb.toString().trim();
    }
}
