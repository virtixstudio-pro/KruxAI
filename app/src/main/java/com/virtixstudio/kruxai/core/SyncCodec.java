package com.virtixstudio.kruxai.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Compresse un texte JSON pour la synchronisation : "g1:" + base64(gzip(texte)).
 * Java pur (pas d'Android) pour pouvoir être testé ; base64 écrit à la main.
 */
public final class SyncCodec {
    public static final String PREFIX = "g1:";
    private static final String ABC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    private SyncCodec() { }

    public static String pack(String text) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (GZIPOutputStream z = new GZIPOutputStream(bo)) { z.write(text.getBytes(StandardCharsets.UTF_8)); }
        return PREFIX + b64(bo.toByteArray());
    }

    /** Retourne le texte d'origine, ou null si la donnée est invalide. */
    public static String unpack(String data) {
        try {
            if (data == null || !data.startsWith(PREFIX)) return null;
            byte[] raw = unb64(data.substring(PREFIX.length()));
            if (raw == null) return null;
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            try (GZIPInputStream z = new GZIPInputStream(new ByteArrayInputStream(raw))) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = z.read(buf)) > 0) {
                    if (bo.size() + n > 8 * 1024 * 1024) return null; // garde-fou contre une bombe de décompression
                    bo.write(buf, 0, n);
                }
            }
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    static String b64(byte[] b) {
        StringBuilder sb = new StringBuilder((b.length + 2) / 3 * 4);
        for (int i = 0; i < b.length; i += 3) {
            int v = (b[i] & 255) << 16 | (i + 1 < b.length ? (b[i + 1] & 255) << 8 : 0) | (i + 2 < b.length ? b[i + 2] & 255 : 0);
            sb.append(ABC.charAt(v >> 18 & 63)).append(ABC.charAt(v >> 12 & 63));
            sb.append(i + 1 < b.length ? ABC.charAt(v >> 6 & 63) : '=');
            sb.append(i + 2 < b.length ? ABC.charAt(v & 63) : '=');
        }
        return sb.toString();
    }

    static byte[] unb64(String s) {
        if (s.length() % 4 != 0) return null;
        int pad = s.endsWith("==") ? 2 : s.endsWith("=") ? 1 : 0;
        byte[] out = new byte[s.length() / 4 * 3 - pad];
        int o = 0;
        for (int i = 0; i < s.length(); i += 4) {
            int v = 0;
            for (int j = 0; j < 4; j++) {
                char c = s.charAt(i + j);
                int d = c == '=' ? 0 : ABC.indexOf(c);
                if (d < 0) return null;
                v = v << 6 | d;
            }
            if (o < out.length) out[o++] = (byte) (v >> 16);
            if (o < out.length) out[o++] = (byte) (v >> 8);
            if (o < out.length) out[o++] = (byte) v;
        }
        return out;
    }
}
