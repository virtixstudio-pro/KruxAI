package com.virtixstudio.kruxai.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Dossier de travail de Krux : les fichiers de code ou de texte importés par l'utilisateur.
 * Java pur (aucune classe Android) pour pouvoir être testé avec un simple JDK.
 * Garde-fous : noms nettoyés, aucun chemin hors du dossier, 1 Mo maximum par fichier, 100 fichiers, texte seulement.
 */
public final class WorkspaceStore {

    public static final long MAX_BYTES = 1_000_000L;
    public static final int MAX_FILES = 100;
    private static final int MAX_NAME = 80;

    private final File root;

    public WorkspaceStore(File root) {
        this.root = root;
        //noinspection ResultOfMethodCallIgnored
        root.mkdirs();
    }

    public File root() { return root; }

    /** Nom de fichier sans chemin, sans caractères de contrôle, sans point initial, 80 caractères au plus (extension gardée). */
    public static String safeName(String raw) {
        String n = raw == null ? "" : raw;
        int cut = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (cut >= 0) n = n.substring(cut + 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (c < 32 || c == 127 || c == ':' || c == '*' || c == '?' || c == '"' || c == '<' || c == '>' || c == '|') continue;
            sb.append(c);
        }
        n = sb.toString().trim();
        while (n.startsWith(".")) n = n.substring(1);
        if (n.isEmpty()) return "fichier.txt";
        if (n.length() > MAX_NAME) {
            String ext = extension(n);
            String keep = ext.isEmpty() ? "" : "." + ext;
            if (keep.length() > 16) keep = "";
            n = n.substring(0, MAX_NAME - keep.length()) + keep;
        }
        return n;
    }

    /** Extension en minuscules, sans le point ("" s'il n'y en a pas). */
    public static String extension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }

    /** Vrai si le contenu a une octet nul dans ses premiers 4 Ko : ce n'est pas du texte. */
    public static boolean looksBinary(byte[] data, int length) {
        int n = Math.min(length, 4096);
        for (int i = 0; i < n; i++) if (data[i] == 0) return true;
        return false;
    }

    public static String sizeLabel(long bytes) {
        if (bytes < 1024) return bytes + " o";
        if (bytes < 1024 * 1024) return Math.max(1, Math.round(bytes / 1024f)) + " Ko";
        return String.format(java.util.Locale.FRENCH, "%.1f Mo", bytes / 1048576f);
    }

    /** Nom libre dans le dossier : « a.txt », puis « a (2).txt », « a (3).txt »… */
    private File uniqueFile(String name) {
        File f = new File(root, name);
        if (!f.exists()) return f;
        String ext = extension(name);
        String base = ext.isEmpty() ? name : name.substring(0, name.length() - ext.length() - 1);
        for (int i = 2; i < 1000; i++) {
            File c = new File(root, base + " (" + i + ")" + (ext.isEmpty() ? "" : "." + ext));
            if (!c.exists()) return c;
        }
        return new File(root, base + " (" + System.currentTimeMillis() + ")" + (ext.isEmpty() ? "" : "." + ext));
    }

    /** Copie un flux dans le dossier. Refuse le binaire, les fichiers trop gros et un dossier plein. */
    public synchronized File importStream(String rawName, InputStream in) throws IOException {
        if (list().size() >= MAX_FILES) throw new IOException("Limite de " + MAX_FILES + " fichiers atteinte");
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int r;
        while ((r = in.read(buf)) > 0) {
            total += r;
            if (total > MAX_BYTES) throw new IOException("Fichier trop gros (1 Mo maximum)");
            bo.write(buf, 0, r);
        }
        byte[] data = bo.toByteArray();
        if (looksBinary(data, data.length)) throw new IOException("Ce n'est pas un fichier texte");
        File f = uniqueFile(safeName(rawName));
        try (FileOutputStream out = new FileOutputStream(f)) { out.write(data); }
        return f;
    }

    /** Fichiers du dossier, du plus récent au plus ancien. */
    public List<File> list() {
        File[] all = root.listFiles();
        List<File> out = new ArrayList<>();
        if (all == null) return out;
        for (File f : all) if (f.isFile()) out.add(f);
        Collections.sort(out, new Comparator<File>() {
            @Override public int compare(File a, File b) { return Long.compare(b.lastModified(), a.lastModified()); }
        });
        return out;
    }

    /** Le fichier de ce nom s'il existe dans le dossier, sinon null (jamais un chemin hors du dossier). */
    public File resolve(String name) {
        if (name == null || name.isEmpty()) return null;
        File f = new File(root, name);
        return isInside(f) && f.isFile() ? f : null;
    }

    private boolean isInside(File f) {
        try {
            File parent = f.getCanonicalFile().getParentFile();
            return parent != null && parent.equals(root.getCanonicalFile());
        } catch (IOException e) {
            return false;
        }
    }

    public String readText(File f) throws IOException {
        if (!isInside(f)) throw new IOException("Fichier hors du dossier de travail");
        if (f.length() > MAX_BYTES) throw new IOException("Fichier trop gros (1 Mo maximum)");
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (InputStream in = new java.io.FileInputStream(f)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
        }
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }

    public void writeText(File f, String text) throws IOException {
        if (!isInside(f)) throw new IOException("Fichier hors du dossier de travail");
        byte[] data = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_BYTES) throw new IOException("Fichier trop gros (1 Mo maximum)");
        try (FileOutputStream out = new FileOutputStream(f)) { out.write(data); }
    }

    public boolean delete(File f) {
        return isInside(f) && f.isFile() && f.delete();
    }
}
