package com.virtixstudio.kruxai.builder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Récupère ce qui est utilisable dans une réponse JSON de l'IA COUPÉE en plein milieu
 * (limite de sortie du moteur, coupure réseau). Retourne les fichiers complets déjà reçus ;
 * le fichier en cours d'écriture est abandonné. Java pur, testé.
 */
public final class TruncatedJson {

    private TruncatedJson() { }

    private static final class R {
        final String s; int i;
        R(String s, int i) { this.s = s; this.i = i; }
        boolean end() { return i >= s.length(); }
        char c() { return s.charAt(i); }
        void ws() { while (i < s.length() && (Character.isWhitespace(s.charAt(i)) || s.charAt(i) == ',')) i++; }
    }

    /** Lit une chaîne JSON à partir du guillemet ouvrant. null si elle est coupée. */
    private static String str(R r) {
        if (r.end() || r.c() != '"') return null;
        r.i++;
        StringBuilder sb = new StringBuilder();
        while (!r.end()) {
            char ch = r.c();
            if (ch == '"') { r.i++; return sb.toString(); }
            if (ch == '\\') {
                if (r.i + 1 >= r.s.length()) return null;
                char n = r.s.charAt(r.i + 1);
                r.i += 2;
                switch (n) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u':
                        if (r.i + 4 > r.s.length()) return null;
                        try { sb.append((char) Integer.parseInt(r.s.substring(r.i, r.i + 4), 16)); }
                        catch (NumberFormatException e) { return null; }
                        r.i += 4;
                        break;
                    default: sb.append(n);
                }
            } else { sb.append(ch); r.i++; }
        }
        return null;
    }

    /** Saute une valeur JSON quelconque. Faux si elle est coupée. */
    private static boolean skip(R r) {
        r.ws();
        if (r.end()) return false;
        char ch = r.c();
        if (ch == '"') return str(r) != null;
        if (ch == '{' || ch == '[') {
            int depth = 0;
            while (!r.end()) {
                char d = r.c();
                if (d == '"') { if (str(r) == null) return false; continue; }
                if (d == '{' || d == '[') depth++;
                if (d == '}' || d == ']') { depth--; r.i++; if (depth == 0) return true; continue; }
                r.i++;
            }
            return false;
        }
        int start = r.i;
        while (!r.end() && ",}] \n\r\t".indexOf(r.c()) < 0) r.i++;
        return r.i > start && !r.end();
    }

    /** Fichiers complets (chemin → contenu) trouvés dans la réponse, dans l'ordre. */
    public static Map<String, String> files(String raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw == null) return out;
        int k = raw.indexOf("\"files\"");
        if (k < 0) return out;
        R r = new R(raw, k + 7);
        r.ws();
        if (r.end() || r.c() != ':') return out;
        r.i++;
        r.ws();
        if (r.end()) return out;
        if (r.c() == '[') {
            r.i++;
            while (true) {
                r.ws();
                if (r.end() || r.c() != '{') break;
                r.i++;
                String path = null, content = null;
                boolean complete = false;
                while (true) {
                    r.ws();
                    if (r.end()) break;
                    if (r.c() == '}') { r.i++; complete = true; break; }
                    String key = str(r);
                    if (key == null) break;
                    r.ws();
                    if (r.end() || r.c() != ':') break;
                    r.i++;
                    r.ws();
                    if (r.end()) break;
                    if (r.c() == '"') {
                        String v = str(r);
                        if (v == null) break;
                        if (key.equals("path")) path = v; else if (key.equals("content")) content = v;
                    } else if (!skip(r)) break;
                }
                if (!complete) break;
                if (path != null && content != null) out.put(path, content);
            }
        } else if (r.c() == '{') {
            r.i++;
            while (true) {
                r.ws();
                if (r.end() || r.c() == '}') break;
                String key = str(r);
                if (key == null) break;
                r.ws();
                if (r.end() || r.c() != ':') break;
                r.i++;
                r.ws();
                if (r.end() || r.c() != '"') break;
                String v = str(r);
                if (v == null) break;
                out.put(key, v);
            }
        }
        return out;
    }

    /** Valeur texte d'un champ simple (name, preview) si elle est complète, sinon null. */
    public static String field(String raw, String name) {
        if (raw == null) return null;
        int k = raw.indexOf("\"" + name + "\"");
        if (k < 0) return null;
        R r = new R(raw, k + name.length() + 2);
        r.ws();
        if (r.end() || r.c() != ':') return null;
        r.i++;
        r.ws();
        return str(r);
    }

    /** Chemins reçus, pour dire à l'IA ce qu'elle a déjà écrit. */
    public static List<String> paths(Map<String, String> files) {
        return new ArrayList<>(files.keySet());
    }
}
