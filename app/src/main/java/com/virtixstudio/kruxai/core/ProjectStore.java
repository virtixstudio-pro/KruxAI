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

    // ------------------------------------------------------------------ navigation et édition dans un projet

    /** Vrai si le fichier (ou dossier) est dans projects/ (chemin canonique : pas d'évasion par « .. » ni lien). */
    public boolean isInside(File f) {
        if (f == null) return false;
        try {
            String base = root.getCanonicalPath() + File.separator;
            String p = f.getCanonicalPath();
            return p.startsWith(base);
        } catch (IOException e) {
            return false;
        }
    }

    /** Chemin relatif à projects/ avec des « / » (vide pour projects/ lui-même). */
    public String relative(File f) {
        try {
            String base = root.getCanonicalPath();
            String p = f.getCanonicalPath();
            if (p.equals(base)) return "";
            if (!p.startsWith(base + File.separator)) return f.getName();
            return p.substring(base.length() + 1).replace(File.separatorChar, '/');
        } catch (IOException e) {
            return f.getName();
        }
    }

    /** Le fichier ou dossier existant à ce chemin relatif, ou null s'il n'existe pas ou sort de projects/. */
    public File resolveRel(String rel) {
        if (rel == null) return null;
        String clean = rel.isEmpty() ? "" : cleanPath(rel);
        if (clean == null) return null;
        File f = clean.isEmpty() ? root : new File(root, clean);
        return (f.equals(root) || isInside(f)) && f.exists() ? f : null;
    }

    /** Contenu d'un dossier : sous-dossiers d'abord, puis fichiers, par ordre alphabétique. */
    public List<File> listDir(File dir) {
        List<File> out = new ArrayList<>();
        if (!(dir.equals(root) || isInside(dir))) return out;
        File[] all = dir.listFiles();
        if (all == null) return out;
        for (File f : all) if (!f.getName().startsWith(".import-")) out.add(f);
        out.sort((a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        return out;
    }

    /** Fichier texte (pas binaire) : on regarde les premiers octets. */
    public boolean isText(File f) {
        if (!f.isFile()) return false;
        try (InputStream in = new java.io.FileInputStream(f)) {
            byte[] b = new byte[4096];
            int n = in.read(b);
            return n <= 0 || !WorkspaceStore.looksBinary(b, n);
        } catch (IOException e) {
            return false;
        }
    }

    public String readText(File f) throws IOException {
        if (!isInside(f)) throw new IOException("Fichier hors des projets");
        if (f.length() > WorkspaceStore.MAX_BYTES) throw new IOException("Fichier trop gros (1 Mo maximum)");
        return new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8);
    }

    public void writeText(File f, String text) throws IOException {
        if (!isInside(f)) throw new IOException("Fichier hors des projets");
        byte[] data = (text == null ? "" : text).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (data.length > WorkspaceStore.MAX_BYTES) throw new IOException("Fichier trop gros (1 Mo maximum)");
        try (FileOutputStream out = new FileOutputStream(f)) { out.write(data); }
    }

    /** Crée un fichier dans ce dossier de projet ; jamais d'écrasement (« nom (2).ext »). */
    public synchronized File createFile(File dir, String rawName, String text) throws IOException {
        if (!isInside(dir) || !dir.isDirectory()) throw new IOException("Dossier de projet introuvable");
        String name = WorkspaceStore.safeName(rawName);
        String ext = WorkspaceStore.extension(name);
        String stem = ext.isEmpty() ? name : name.substring(0, name.length() - ext.length() - 1);
        File f = new File(dir, name);
        int k = 2;
        while (f.exists()) f = new File(dir, stem + " (" + (k++) + ")" + (ext.isEmpty() ? "" : "." + ext));
        writeText(f, text);
        return f;
    }

    /** Supprime un fichier ou tout un projet/dossier (seulement s'il est dans projects/). */
    public boolean delete(File f) {
        if (f == null || f.equals(root) || !isInside(f)) return false;
        deleteTree(f);
        return !f.exists();
    }

    /** Taille totale (octets) d'un dossier. */
    public static long treeSize(File f) {
        if (f == null) return 0;
        if (f.isFile()) return f.length();
        long t = 0;
        File[] k = f.listFiles();
        if (k != null) for (File c : k) t += treeSize(c);
        return t;
    }

    /** Nombre de fichiers d'un dossier. */
    public static int treeFiles(File f) {
        if (f == null) return 0;
        if (f.isFile()) return 1;
        int t = 0;
        File[] k = f.listFiles();
        if (k != null) for (File c : k) t += treeFiles(c);
        return t;
    }

    public static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
