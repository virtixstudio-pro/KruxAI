package com.virtixstudio.kruxai.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fichiers que KRUX livre à l'utilisateur. KRUX écrit dans sa réponse :
 * <KRUX_FILE name="rapport.md"> ...contenu... </KRUX_FILE>
 * Java pur (testé). Le texte stocké garde les balises ; l'affichage les remplace par des cartes de fichier.
 */
public final class FileDelivery {

    public static final int MAX_FILE_CHARS = 200_000;
    public static final int MAX_FILES = 8;

    public static final class DFile {
        public final String name;
        public final String content;
        public DFile(String n, String c) { name = n; content = c; }
        public int bytes() { return content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length; }
        public String mime() { return mimeOf(name); }
    }

    private static final Pattern BLOCK = Pattern.compile("<KRUX_FILE\\s+name\\s*=\\s*\"([^\"]{1,200})\"\\s*>\\r?\\n?(.*?)\\r?\\n?</KRUX_FILE>", Pattern.DOTALL);
    private static final Pattern OPEN = Pattern.compile("<KRUX_FILE\\s+name\\s*=\\s*\"([^\"]{1,200})\"\\s*>");
    private static final String[] OK_EXT = {"md", "txt", "csv", "json", "html", "htm", "xml", "css", "js", "ts", "py", "java", "kt", "sql",
            "sh", "yml", "yaml", "ini", "toml", "svg", "tex", "log", "gradle", "properties", "c", "h", "cpp", "cs", "go", "rs", "php", "rb", "swift"};

    private FileDelivery() { }

    /** Nom sûr : sans dossier ni caractères dangereux, extension texte connue (sinon .txt), 80 caractères au plus. */
    public static String safeName(String raw) {
        String n = raw == null ? "" : raw.trim().replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1);
        n = n.replaceAll("[^\\p{L}\\p{N}._ -]", "_").replaceAll("\\s+", "_").replaceAll("^[.\\-_]+", "");
        if (n.isEmpty()) n = "fichier";
        int dot = n.lastIndexOf('.');
        String ext = dot > 0 ? n.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        boolean okExt = false;
        for (String e : OK_EXT) if (e.equals(ext)) { okExt = true; break; }
        if (!okExt) { n = (dot > 0 ? n.substring(0, dot) : n) + ".txt"; }
        if (n.length() > 80) {
            int d2 = n.lastIndexOf('.');
            String e2 = n.substring(d2);
            n = n.substring(0, 80 - e2.length()) + e2;
        }
        return n;
    }

    public static String mimeOf(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".html") || n.endsWith(".htm")) return "text/html";
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".csv")) return "text/csv";
        if (n.endsWith(".xml") || n.endsWith(".svg")) return n.endsWith(".svg") ? "image/svg+xml" : "text/xml";
        if (n.endsWith(".md")) return "text/markdown";
        return "text/plain";
    }

    /** Fichiers complets trouvés dans le texte (au plus MAX_FILES ; contenu tronqué à MAX_FILE_CHARS ; noms uniques). */
    public static List<DFile> parse(String text) {
        List<DFile> out = new ArrayList<>();
        if (text == null || text.indexOf("<KRUX_FILE") < 0) return out;
        Matcher m = BLOCK.matcher(text);
        while (m.find() && out.size() < MAX_FILES) {
            String name = safeName(m.group(1));
            String c = m.group(2);
            if (c.length() > MAX_FILE_CHARS) c = c.substring(0, MAX_FILE_CHARS);
            // enlève un éventuel bloc de code Markdown englobant
            String t = c.trim();
            if (t.startsWith("```") && t.endsWith("```") && t.length() > 6) {
                int nl = t.indexOf('\n');
                if (nl > 0) c = t.substring(nl + 1, t.length() - 3).replaceAll("\\s+$", "");
            }
            out.add(new DFile(uniqueName(out, name), c));
        }
        return out;
    }

    private static String uniqueName(List<DFile> existing, String name) {
        String n = name;
        int k = 2;
        while (true) {
            boolean clash = false;
            for (DFile f : existing) if (f.name.equals(n)) { clash = true; break; }
            if (!clash) return n;
            int dot = name.lastIndexOf('.');
            n = (dot > 0 ? name.substring(0, dot) : name) + "_" + k++ + (dot > 0 ? name.substring(dot) : "");
        }
    }

    /** Texte à afficher : blocs complets retirés, bloc encore en cours d'écriture masqué (pas de contenu brut qui défile). */
    public static String stripForDisplay(String text) {
        if (text == null || text.indexOf("<KRUX_FIL") < 0) return text == null ? "" : text;
        String s = BLOCK.matcher(text).replaceAll("");
        Matcher o = OPEN.matcher(s);
        if (o.find()) s = s.substring(0, o.start());
        else {
            int i = s.lastIndexOf("<KRUX_FIL"); // balise ouvrante encore incomplète
            if (i >= 0 && s.indexOf('>', i) < 0) s = s.substring(0, i);
        }
        return s.replaceAll("\\n{3,}", "\n\n").trim();
    }

    /** Nom du fichier en cours d'écriture (balise ouverte, pas encore fermée), sinon null. */
    public static String writingNow(String text) {
        if (text == null) return null;
        String s = BLOCK.matcher(text).replaceAll("");
        Matcher o = OPEN.matcher(s);
        return o.find() ? safeName(o.group(1)) : null;
    }
}
