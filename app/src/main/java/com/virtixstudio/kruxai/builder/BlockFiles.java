package com.virtixstudio.kruxai.builder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lit une réponse d'IA sans JSON : des blocs de fichiers.
 * Format 1 : une ligne "=== chemin/du/fichier ===" puis le code brut, fin par "=== FIN ===".
 * Format 2 : blocs ``` dont le chemin est dans la ligne d'ouverture ou sur la ligne juste avant.
 * Un dernier bloc non terminé est écarté (réponse probablement coupée) : complete reste false.
 */
public final class BlockFiles {
    private BlockFiles() {}

    public static final class Result {
        public final Map<String, String> files = new LinkedHashMap<>();
        public String name = null;
        public boolean complete = false;
    }

    private static final Pattern HEADER = Pattern.compile("^\\s*={3,}\\s*(.+?)\\s*={3,}\\s*$");
    private static final Pattern PATH = Pattern.compile("^[\\w@./-]+\\.[A-Za-z0-9]{1,8}$");
    private static final Pattern NAME = Pattern.compile("^\\s*NAME\\s*:\\s*([A-Za-z0-9 _-]{1,40})\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PREV = Pattern.compile(
            "^[#*`\\s]*(?:(?:File|Fichier)\\s*:?\\s*)?[`*]*([\\w@./-]+\\.[A-Za-z0-9]{1,8})[`*:]*\\s*$", Pattern.CASE_INSENSITIVE);

    public static Result parse(String raw) {
        Result r = new Result();
        if (raw == null) return r;
        String[] lines = raw.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        parseHeaders(lines, r);
        if (r.files.isEmpty()) parseFences(lines, r);
        return r;
    }

    private static void parseHeaders(String[] lines, Result r) {
        String path = null;
        StringBuilder body = null;
        for (String line : lines) {
            Matcher m = HEADER.matcher(line);
            if (m.matches()) {
                String h = m.group(1).trim();
                boolean end = h.equalsIgnoreCase("FIN") || h.equalsIgnoreCase("END");
                if (end || PATH.matcher(h).matches()) {
                    if (path != null) keep(r, path, body);
                    if (end) { path = null; body = null; r.complete = true; break; }
                    path = h;
                    body = new StringBuilder();
                    continue;
                }
            }
            if (path == null) {
                Matcher n = NAME.matcher(line);
                if (r.name == null && n.matches()) r.name = n.group(1).trim();
            } else {
                body.append(line).append('\n');
            }
        }
        // un dernier bloc sans "=== FIN ===" est écarté : il est probablement coupé
    }

    private static void keep(Result r, String path, StringBuilder body) {
        String c = clean(body.toString());
        if (!c.isEmpty()) r.files.put(path, c);
    }

    private static String clean(String s) {
        String[] ls = s.split("\n", -1);
        int a = 0, b = ls.length - 1;
        while (a <= b && ls[a].trim().isEmpty()) a++;
        while (b >= a && ls[b].trim().isEmpty()) b--;
        if (a <= b && ls[a].trim().startsWith("```")) a++;
        if (b >= a && ls[b].trim().equals("```")) b--;
        StringBuilder o = new StringBuilder();
        for (int i = a; i <= b; i++) o.append(ls[i]).append('\n');
        return o.toString();
    }

    private static void parseFences(String[] lines, Result r) {
        boolean allClosed = true;
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].trim();
            if (!t.startsWith("```")) continue;
            String path = pathFromInfo(t.substring(3).trim());
            if (path == null) {
                for (int k = i - 1; k >= 0; k--) {
                    String pv = lines[k].trim();
                    if (pv.isEmpty()) continue;
                    Matcher m = PREV.matcher(pv);
                    if (m.matches()) path = m.group(1);
                    break;
                }
            }
            StringBuilder b = new StringBuilder();
            boolean closed = false;
            int j = i + 1;
            for (; j < lines.length; j++) {
                if (lines[j].trim().equals("```")) { closed = true; break; }
                b.append(lines[j]).append('\n');
            }
            if (!closed) { allClosed = false; break; }
            i = j;
            if (path != null && b.length() > 0) r.files.put(path, b.toString());
        }
        r.complete = allClosed && !r.files.isEmpty();
    }

    private static String pathFromInfo(String info) {
        if (info.isEmpty()) return null;
        for (String tok : info.split("[\\s\"'=:]+")) {
            if (PATH.matcher(tok).matches()) return tok;
        }
        return null;
    }
}
