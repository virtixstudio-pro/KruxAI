package com.virtixstudio.kruxai.builder;

/**
 * Répare localement (sans appel à l'IA) les chaînes TypeScript cassées des fichiers .ts :
 * apostrophe non échappée dans une chaîne à guillemets simples ('l'app') et chaîne non fermée
 * en fin de ligne. La chaîne devient un gabarit à backticks. Les lignes saines ne sont jamais
 * modifiées ; les .tsx (texte JSX) et les lignes qui ressemblent à une regex sont ignorés.
 */
public final class TsStringRepair {
    private TsStringRepair() { }

    private static final String VALID_AFTER = ",;:)]}.+?=!<>&|*/-";

    public static String repair(String path, String src) {
        if (src == null || path == null || !path.endsWith(".ts")) return src;
        String[] lines = src.split("\n", -1);
        boolean inBlock = false, inTpl = false, changed = false;
        for (int n = 0; n < lines.length; n++) {
            String line = lines[n];
            boolean regexish = looksLikeRegex(line);
            StringBuilder out = new StringBuilder();
            int i = 0, len = line.length();
            while (i < len) {
                char c = line.charAt(i);
                if (inBlock) {
                    if (c == '*' && i + 1 < len && line.charAt(i + 1) == '/') { out.append("*/"); i += 2; inBlock = false; }
                    else { out.append(c); i++; }
                    continue;
                }
                if (inTpl) {
                    if (c == '\\' && i + 1 < len) { out.append(c).append(line.charAt(i + 1)); i += 2; continue; }
                    if (c == '`') inTpl = false;
                    out.append(c); i++;
                    continue;
                }
                if (c == '/' && i + 1 < len) {
                    char d = line.charAt(i + 1);
                    if (d == '/') { out.append(line, i, len); i = len; continue; }
                    if (d == '*') { out.append("/*"); i += 2; inBlock = true; continue; }
                }
                if (c == '`') { inTpl = true; out.append(c); i++; continue; }
                if ((c == '\'' || c == '"') && !regexish) {
                    int end = -1;
                    boolean embedded = false;
                    int j = i + 1;
                    while (j < len) {
                        char d = line.charAt(j);
                        if (d == '\\') { j += 2; continue; }
                        if (d == c) {
                            if (validAfter(line, j + 1)) { end = j; break; }
                            embedded = true;
                        }
                        j++;
                    }
                    if (end >= 0 && !embedded) {
                        out.append(line, i, end + 1);
                        i = end + 1;
                    } else if (end >= 0) {
                        out.append('`').append(escapeTpl(line.substring(i + 1, end))).append('`');
                        i = end + 1;
                        changed = true;
                    } else {
                        String rest = line.substring(i + 1);
                        if (rest.endsWith("\\")) { out.append(line, i, len); i = len; continue; }
                        int k = rest.length();
                        while (k > 0 && ",;)]}".indexOf(rest.charAt(k - 1)) >= 0 || (k > 0 && rest.charAt(k - 1) == ' ')) k--;
                        out.append('`').append(escapeTpl(rest.substring(0, k))).append('`').append(rest.substring(k));
                        i = len;
                        changed = true;
                    }
                    continue;
                }
                out.append(c);
                i++;
            }
            lines[n] = out.toString();
        }
        return changed ? String.join("\n", lines) : src;
    }

    private static boolean validAfter(String line, int k) {
        if (k >= line.length()) return true;
        char ch = line.charAt(k);
        return Character.isWhitespace(ch) || VALID_AFTER.indexOf(ch) >= 0;
    }

    private static boolean looksLikeRegex(String line) {
        return line.contains(".replace(/") || line.contains(".match(/") || line.contains(".test(")
            || line.contains(".split(/") || line.contains("RegExp") || line.contains(".replaceAll(/");
    }

    private static String escapeTpl(String s) {
        return s.replace("`", "\\`").replace("${", "\\${");
    }
}
