package com.virtixstudio.kruxai.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Résultats de recherche d'images : stockés dans le texte du message (bloc invisible à l'affichage),
 * donc enregistrés et synchronisés comme n'importe quel message, sans changer la base de données.
 */
public final class ImageGallery {

    private ImageGallery() { }

    public static final class Hit {
        public final String thumb, full, title, creator, license, page, source;
        public Hit(String thumb, String full, String title, String creator, String license, String page, String source) {
            this.thumb = clean(thumb); this.full = clean(full); this.title = clean(title);
            this.creator = clean(creator); this.license = clean(license); this.page = clean(page); this.source = clean(source);
        }
        /** « © Auteur · CC BY 4.0 · via Openverse » */
        public String credit() {
            StringBuilder sb = new StringBuilder();
            if (!creator.isEmpty()) sb.append("© ").append(creator);
            if (!license.isEmpty()) { if (sb.length() > 0) sb.append(" · "); sb.append(license); }
            if (!source.isEmpty()) { if (sb.length() > 0) sb.append(" · "); sb.append("via ").append(source); }
            return sb.toString();
        }
    }

    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("<[^>]*>", "").replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static final Pattern BLOCK = Pattern.compile("<KRUX_IMAGES>\\r?\\n?(.*?)\\r?\\n?</KRUX_IMAGES>", Pattern.DOTALL);

    public static String encode(List<Hit> hits) {
        StringBuilder sb = new StringBuilder("<KRUX_IMAGES>\n");
        for (Hit h : hits) {
            sb.append(h.thumb).append('\t').append(h.full).append('\t').append(h.title).append('\t')
              .append(h.creator).append('\t').append(h.license).append('\t').append(h.page).append('\t').append(h.source).append('\n');
        }
        return sb.append("</KRUX_IMAGES>").toString();
    }

    public static List<Hit> parse(String text) {
        List<Hit> out = new ArrayList<>();
        if (text == null) return out;
        Matcher m = BLOCK.matcher(text);
        if (!m.find()) return out;
        for (String line : m.group(1).split("\n")) {
            String[] p = line.split("\t", -1);
            if (p.length >= 7 && p[0].startsWith("http")) out.add(new Hit(p[0], p[1], p[2], p[3], p[4], p[5], p[6]));
        }
        return out;
    }

    /** Texte sans le bloc d'images (y compris un bloc incomplet). */
    public static String strip(String text) {
        if (text == null || text.indexOf("<KRUX_IMAGES") < 0) return text == null ? "" : text;
        String s = BLOCK.matcher(text).replaceAll("");
        int i = s.indexOf("<KRUX_IMAGES");
        if (i >= 0) s = s.substring(0, i);
        return s.trim();
    }

    private static final Pattern ASK = Pattern.compile(
        "^(?:(?:peux-tu|pourrais-tu|tu peux|veux-tu)\\s+)?(?:me\\s+)?"
      + "(?:montre|montrer|cherche|chercher|trouve|trouver|recherche|rechercher|donne|donner|affiche|afficher|envoie|envoyer|fais voir|fait voir)(?:-moi|-nous)?\\s+"
      + "(?:me\\s+|moi\\s+)?(?:des|quelques|les|plein de|de belles|une? )?\\s*(?:belles?\\s+)?(?:images?|photos?|illustrations?)\\s+"
      + "(?:de |d'|d’|du |des |sur |avec )(.+)$");
    private static final Pattern DIRECT = Pattern.compile("^(?:images?|photos?)\\s+(?:de |d'|d’|du |des |sur )(.+)$");

    /** Sujet demandé si le message est une demande d'images (« montre-moi des photos de lions »), sinon null. */
    public static String extractQuery(String prompt) {
        if (prompt == null) return null;
        String p = prompt.trim().toLowerCase(java.util.Locale.FRENCH);
        if (p.length() > 140 || p.contains("\n")) return null;
        Matcher m = ASK.matcher(p);
        if (!m.find()) { m = DIRECT.matcher(p); if (!m.find()) return null; }
        String q = m.group(1).replaceAll("(?i)\\s*(s'il (te|vous) pla[iî]t|stp|svp)\\s*[.!?]*$", "").replaceAll("[?!.]+$", "").trim();
        return q.length() < 2 ? null : q;
    }
}
