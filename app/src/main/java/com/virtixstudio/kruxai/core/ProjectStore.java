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

    /** Dossier à ne même pas parcourir (import d'un dossier : évite de lire .git ou build, très lourds). */
    public static boolean skipDir(String name) {
        return name.equals(".git") || name.equals("build") || name.equals(".gradle") || name.equals("node_modules")
                || name.equals(".idea") || name.equals("__MACOSX");
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

    /** Import en cours (dossier de travail temporaire + compteurs). */
    public static final class Import {
        final String base;
        final File tmp;
        int files, skipped;
        long bytes;
        Import(String base, File tmp) { this.base = base; this.tmp = tmp; }
    }

    /** Démarre un import (zip ou dossier) : crée un dossier de travail temporaire. */
    public synchronized Import begin(String name) throws IOException {
        File tmp = new File(root, ".import-" + System.nanoTime());
        if (!tmp.mkdirs()) throw new IOException("Dossier de travail impossible à créer");
        return new Import(safeProjectName(name), tmp);
    }

    /** Ajoute un fichier au chemin relatif donné ; ignore les chemins dangereux ou inutiles. Limites de taille. */
    public void add(Import imp, String relRaw, InputStream in) throws IOException {
        String rel = cleanPath(relRaw);
        if (rel == null) { imp.skipped++; return; }
        if (ignored(rel)) { imp.skipped++; return; }
        if (imp.files + 1 > MAX_FILES) throw new IOException("Projet trop gros : plus de " + MAX_FILES + " fichiers");
        File out = new File(imp.tmp, rel);
        if (!out.getCanonicalPath().startsWith(imp.tmp.getCanonicalPath() + File.separator)) { imp.skipped++; return; }
        File parent = out.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Dossier impossible : " + rel);
        byte[] buf = new byte[16384];
        try (FileOutputStream fo = new FileOutputStream(out)) {
            int n;
            while ((n = in.read(buf)) > 0) {
                imp.bytes += n;
                if (imp.bytes > MAX_BYTES) throw new IOException("Projet trop gros : plus de " + (MAX_BYTES / (1024 * 1024)) + " Mo");
                fo.write(buf, 0, n);
            }
        }
        imp.files++;
    }

    /** Abandonne un import (erreur) : supprime le dossier de travail. */
    public void abort(Import imp) { deleteTree(imp.tmp); }

    /** Termine : racine unique remontée, nom de projet unique, détection Android Gradle. */
    public synchronized Result finish(Import imp) throws IOException {
        if (imp.files == 0) {
            deleteTree(imp.tmp);
            throw new IOException("Rien à importer (vide, ou uniquement des fichiers ignorés)");
        }
        File dir = new File(root, imp.base);
        int k = 2;
        while (dir.exists()) dir = new File(root, imp.base + "-" + (k++));
        File[] top = imp.tmp.listFiles();
        File content = imp.tmp;
        if (top != null && top.length == 1 && top[0].isDirectory()) content = top[0];
        if (!content.renameTo(dir)) {
            deleteTree(imp.tmp);
            throw new IOException("Impossible de ranger le projet");
        }
        deleteTree(imp.tmp);
        boolean gradle = (new File(dir, "settings.gradle").isFile() || new File(dir, "settings.gradle.kts").isFile())
                && new File(dir, "app").isDirectory();
        return new Result(dir, imp.files, imp.bytes, imp.skipped, gradle);
    }

    /** Importe un zip. */
    public Result importZip(String zipName, InputStream in) throws IOException {
        Import imp = begin(zipName);
        try (ZipInputStream zin = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                add(imp, e.getName(), zin); // lit jusqu'à la fin de l'entrée
            }
        } catch (IOException | RuntimeException ex) {
            abort(imp);
            throw ex instanceof IOException ? (IOException) ex : new IOException(ex.getMessage(), ex);
        }
        return finish(imp);
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
