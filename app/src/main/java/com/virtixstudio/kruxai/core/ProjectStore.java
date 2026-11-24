package com.virtixstudio.kruxai.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Projets importés (zip) avec leurs dossiers conservés : filesDir/projects/&lt;nom&gt;/.
 * Java pur (aucune classe Android) pour pouvoir être testé hors téléphone.
 */
public final class ProjectStore {

    public static final long MAX_BYTES = 60L * 1024 * 1024; // taille décompressée totale
    public static final int MAX_FILES = 4000;

    /** Résultat d'un import. */
    public static final class Result {
        public final File dir;
        public final int files;
        public final long bytes;
        public final int skipped;
        public final boolean androidGradle;
        Result(File dir, int files, long bytes, int skipped, boolean androidGradle) {
            this.dir = dir; this.files = files; this.bytes = bytes; this.skipped = skipped; this.androidGradle = androidGradle;
        }
    }

    private final File root;

    public ProjectStore(File root) {
        this.root = root;
        //noinspection ResultOfMethodCallIgnored
        root.mkdirs();
    }

    public File root() { return root; }

    /** Nom de dossier sûr (lettres, chiffres, - _ .), 40 caractères au plus. */
    public static String safeProjectName(String raw) {
        String n = raw == null ? "" : raw;
        int dot = n.toLowerCase(java.util.Locale.ROOT).lastIndexOf(".zip");
        if (dot > 0 && dot == n.length() - 4) n = n.substring(0, dot);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            sb.append(Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.' ? c : '_');
        }
        String s = sb.toString();
        while (s.startsWith(".")) s = s.substring(1);
        if (s.isEmpty()) s = "projet";
        return s.length() > 40 ? s.substring(0, 40) : s;
    }

    /** Dossiers jamais copiés : inutiles, lourds ou sensibles. */
    static boolean ignored(String path) {
        String p = "/" + path;
        return p.contains("/.git/") || p.contains("/build/") || p.contains("/.gradle/")
                || p.contains("/node_modules/") || p.contains("/.idea/") || p.contains("/__MACOSX/")
                || p.endsWith("/local.properties") || p.endsWith("/.DS_Store");
    }

    /** Chemin propre ou null s'il sort du dossier (zip-slip) ou est vide. */
    static String cleanPath(String raw) {
        if (raw == null) return null;
        String p = raw.replace('\\', '/');
        while (p.startsWith("/")) p = p.substring(1);
        if (p.isEmpty()) return null;
        for (String part : p.split("/")) {
            if (part.equals("..")) return null;
        }
        return p;
    }

    /** Importe un zip : dossier unique pris pour racine, nom de projet unique, limites de taille. */
    public synchronized Result importZip(String zipName, InputStream in) throws IOException {
        // 1re passe en mémoire : liste des chemins pour détecter une racine unique n'est pas possible en flux ;
        // on extrait donc dans un dossier temporaire, puis on remonte la racine unique si besoin.
        String base = safeProjectName(zipName);
        File dir = new File(root, base);
        int k = 2;
        while (dir.exists()) dir = new File(root, base + "-" + (k++));
        File tmp = new File(root, ".import-" + System.nanoTime());
        if (!tmp.mkdirs()) throw new IOException("Dossier de travail impossible à créer");

        int files = 0, skipped = 0;
        long bytes = 0;
        try (ZipInputStream zin = new ZipInputStream(in)) {
            ZipEntry e;
            byte[] buf = new byte[16384];
            while ((e = zin.getNextEntry()) != null) {
                String rel = cleanPath(e.getName());
                if (rel == null) { skipped++; continue; }
                if (e.isDirectory()) continue;
                if (ignored(rel)) { skipped++; continue; }
                if (++files > MAX_FILES) throw new IOException("Projet trop gros : plus de " + MAX_FILES + " fichiers");
                File out = new File(tmp, rel);
                String canon = out.getCanonicalPath();
                if (!canon.startsWith(tmp.getCanonicalPath() + File.separator)) { skipped++; files--; continue; }
                File parent = out.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Dossier impossible : " + rel);
                try (FileOutputStream fo = new FileOutputStream(out)) {
                    int n;
                    while ((n = zin.read(buf)) > 0) {
                        bytes += n;
                        if (bytes > MAX_BYTES) throw new IOException("Projet trop gros : plus de " + (MAX_BYTES / (1024 * 1024)) + " Mo");
                        fo.write(buf, 0, n);
                    }
                }
            }
        } catch (IOException | RuntimeException ex) {
            deleteTree(tmp);
            throw ex instanceof IOException ? (IOException) ex : new IOException(ex.getMessage(), ex);
        }
        if (files == 0) {
            deleteTree(tmp);
            throw new IOException("Le zip est vide (ou ne contient que des fichiers ignorés)");
        }

        // Racine unique (ex. MonApp-main/) : on remonte son contenu.
        File[] top = tmp.listFiles();
        File content = tmp;
        if (top != null && top.length == 1 && top[0].isDirectory()) content = top[0];
        if (!content.renameTo(dir)) {
            deleteTree(tmp);
            throw new IOException("Impossible de ranger le projet");
        }
        deleteTree(tmp); // reste éventuel (dossier tmp vide)
        boolean gradle = (new File(dir, "settings.gradle").isFile() || new File(dir, "settings.gradle.kts").isFile())
                && new File(dir, "app").isDirectory();
        return new Result(dir, files, bytes, skipped, gradle);
    }

    /** Liste des projets importés, le plus récent d'abord. */
    public List<File> list() {
        List<File> out = new ArrayList<>();
        File[] all = root.listFiles();
        if (all == null) return out;
        for (File f : all) if (f.isDirectory() && !f.getName().startsWith(".")) out.add(f);
        out.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return out;
    }

    public static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
