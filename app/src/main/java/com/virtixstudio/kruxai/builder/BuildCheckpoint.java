package com.virtixstudio.kruxai.builder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Sauvegarde du code déjà écrit par l'IA, pour reprendre une création interrompue
 * (réseau coupé, quota saturé, app fermée) sans tout réécrire. Java pur.
 */
public final class BuildCheckpoint {

    public static final long MAX_AGE_MS = 24L * 3600 * 1000;
    static final String FILE = "krux-checkpoint.bin";
    private static final int MAGIC = 0x4B525831; // "KRX1"
    private static final int MAX_FILES = 200, MAX_BYTES = 8 * 1024 * 1024;

    public static final class Saved {
        public final String name;
        public final Map<String, String> files;
        Saved(String name, Map<String, String> files) { this.name = name; this.files = files; }
    }

    private BuildCheckpoint() { }

    static String hash(String description) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(description.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(description.hashCode());
        }
    }

    private static void str(DataOutputStream o, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        o.writeInt(b.length);
        o.write(b);
    }

    private static String str(DataInputStream in) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > MAX_BYTES) throw new IOException("taille invalide");
        byte[] b = new byte[n];
        in.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    /** Écrit (via un fichier temporaire, puis renommage) ; ne lève jamais d'exception. */
    public static boolean save(File dir, String description, String name, Map<String, String> files, long now) {
        try {
            if (dir == null || files == null || files.isEmpty()) return false;
            if (!dir.isDirectory() && !dir.mkdirs()) return false;
            File tmp = new File(dir, FILE + ".tmp");
            try (DataOutputStream o = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(tmp)))) {
                o.writeInt(MAGIC);
                o.writeLong(now);
                str(o, hash(description));
                str(o, name == null ? "app" : name);
                o.writeInt(files.size());
                for (Map.Entry<String, String> e : files.entrySet()) {
                    str(o, e.getKey());
                    str(o, e.getValue());
                }
            }
            File dst = new File(dir, FILE);
            if (dst.exists() && !dst.delete()) return false;
            return tmp.renameTo(dst);
        } catch (Exception e) {
            return false;
        }
    }

    /** Retourne le code sauvegardé pour CETTE description, s'il est récent et intact ; sinon null. */
    public static Saved load(File dir, String description, long now) {
        File f = new File(dir, FILE);
        if (!f.isFile()) return null;
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new FileInputStream(f)))) {
            if (in.readInt() != MAGIC) return null;
            long at = in.readLong();
            if (now - at > MAX_AGE_MS || now < at - 60_000) return null;
            if (!hash(description).equals(str(in))) return null;
            String name = str(in);
            int n = in.readInt();
            if (n <= 0 || n > MAX_FILES) return null;
            Map<String, String> files = new LinkedHashMap<>();
            for (int i = 0; i < n; i++) files.put(str(in), str(in));
            if (!files.containsKey(BuildValidator.MANIFEST)) return null;
            return new Saved(name, files);
        } catch (Exception e) {
            return null;
        }
    }

    public static void clear(File dir) {
        if (dir == null) return;
        new File(dir, FILE).delete();
        new File(dir, FILE + ".tmp").delete();
    }
}
