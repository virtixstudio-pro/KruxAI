package com.virtixstudio.kruxai.api;

import java.net.URI;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Outils de recherche en Java pur (testables) : mots-clés, sous-requêtes utiles, classement par pertinence,
 * et pas plus de N pages par site pour varier les sources.
 */
public final class SearchRanker {

    private static final Set<String> STOP = new HashSet<>();
    static {
        String[] w = ("le la les un une des de du d l et ou à a au aux en dans sur sous pour par avec sans que qui quoi quel quelle quels quelles "
                + "est sont ce cet cette ces mon ma mes ton ta tes son sa ses je tu il elle on nous vous ils elles me te se ne pas plus "
                + "the a an of and or to in on for with is are what which who how why when where do does did can could i you it this that "
                + "peux peut dis moi donne explique cherche recherche parle sur propos stp svp krux").split(" ");
        for (String s : w) STOP.add(s);
    }

    private SearchRanker() { }

    static String fold(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT);
    }

    /** Mots importants de la question (sans mots vides), dans l'ordre, sans doublon. */
    public static List<String> keywords(String query) {
        List<String> out = new ArrayList<>();
        for (String t : fold(query).split("[^\\p{L}\\p{N}]+")) {
            if (t.length() >= 3 && !STOP.contains(t) && !out.contains(t)) out.add(t);
        }
        return out;
    }

    /** Jusqu'à 3 requêtes complémentaires : la question, ses mots-clés seuls, et une version récente si utile. */
    public static List<String> subQueries(String query, int year) {
        List<String> q = new ArrayList<>();
        String base = query == null ? "" : query.trim();
        if (base.isEmpty()) return q;
        q.add(base);
        List<String> kw = keywords(base);
        if (kw.size() >= 2) {
            String joined = String.join(" ", kw.subList(0, Math.min(kw.size(), 6)));
            if (!joined.equalsIgnoreCase(base)) q.add(joined);
        }
        String f = fold(base);
        boolean fresh = f.matches(".*\\b(actu|actualite|actualites|news|dernier|derniere|derniers|recent|recente|nouveau|nouveaute|aujourd|prix|cours|resultat|resultats|score|meteo|sortie)\\b.*");
        if (fresh && !f.contains(String.valueOf(year))) q.add(base + " " + year);
        return q.size() > 3 ? q.subList(0, 3) : q;
    }

    /** Score de pertinence : mots-clés trouvés dans le titre (compte double) et l'extrait, petit bonus aux extraits riches. */
    public static double score(String title, String snippet, List<String> kw) {
        if (kw == null || kw.isEmpty()) return 0;
        String t = fold(title), s = fold(snippet);
        double hit = 0;
        for (String k : kw) {
            if (t.contains(k)) hit += 2;
            if (s.contains(k)) hit += 1;
        }
        double base = hit / (3.0 * kw.size());
        double rich = Math.min(1.0, (snippet == null ? 0 : snippet.length()) / 200.0) * 0.1;
        return base + rich;
    }

    static String domainOf(String url) {
        try {
            String h = new URI(url).getHost();
            if (h == null) return "";
            return h.startsWith("www.") ? h.substring(4) : h;
        } catch (Exception e) {
            return "";
        }
    }

    /** Éléments {titre, extrait, url}. Retourne les indices triés par score décroissant, au plus maxPerDomain par site, limités à limit. */
    public static List<Integer> rank(List<String[]> items, List<String> kw, int maxPerDomain, int limit) {
        final double[] sc = new double[items.size()];
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            String[] it = items.get(i);
            // petit bonus de position : les premiers résultats du moteur restent favorisés à score égal
            sc[i] = score(it[0], it[1], kw) + Math.max(0, 0.05 - i * 0.002);
            order.add(i);
        }
        order.sort((a, b) -> Double.compare(sc[b], sc[a]));
        List<Integer> out = new ArrayList<>();
        Map<String, Integer> perDomain = new HashMap<>();
        for (int idx : order) {
            String dom = domainOf(items.get(idx)[2]);
            int n = perDomain.getOrDefault(dom, 0);
            if (n >= maxPerDomain) continue;
            perDomain.put(dom, n + 1);
            out.add(idx);
            if (out.size() >= limit) break;
        }
        return out;
    }
}
