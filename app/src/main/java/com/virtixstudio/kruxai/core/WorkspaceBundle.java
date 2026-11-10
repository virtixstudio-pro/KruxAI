package com.virtixstudio.kruxai.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Archive d'un espace de travail (les fichiers que KRUX écrit) en un seul bloc compressé au maximum.
 * Format avant compression : pour chaque fichier "nom\n taille \n contenu". Java pur, testé.
 */
public final class WorkspaceBundle {

    public static final class Entry {
        public final String name; public final String content;
        public Entry(String n, String c) { name = n; content = c; }
    }

    private WorkspaceBundle() { }

    public static byte[] pack(List<Entry> entries) {
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries) {
            byte[] b = e.content.getBytes(StandardCharsets.UTF_8);
            sb.append(e.name.replace('\n', '_')).append('\n').append(b.length).append('\n').append(e.content);
        }
        return SyncCodec.deflate(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Retourne la liste des fichiers, ou null si l'archive est invalide. */
    public static List<Entry> unpack(byte[] packed) {
        byte[] raw = SyncCodec.inflate(packed);
        if (raw == null) return null;
        List<Entry> out = new ArrayList<>();
        int i = 0;
        try {
            while (i < raw.length) {
                int n1 = indexOf(raw, (byte) '\n', i);
                int n2 = n1 < 0 ? -1 : indexOf(raw, (byte) '\n', n1 + 1);
                if (n1 < 0 || n2 < 0) return null;
                String name = new String(raw, i, n1 - i, StandardCharsets.UTF_8);
                int len = Integer.parseInt(new String(raw, n1 + 1, n2 - n1 - 1, StandardCharsets.UTF_8));
                if (len < 0 || n2 + 1 + len > raw.length) return null;
                out.add(new Entry(name, new String(raw, n2 + 1, len, StandardCharsets.UTF_8)));
                i = n2 + 1 + len;
            }
        } catch (Exception e) {
            return null;
        }
        return out;
    }

    private static int indexOf(byte[] a, byte b, int from) {
        for (int i = from; i < a.length; i++) if (a[i] == b) return i;
        return -1;
    }
}
