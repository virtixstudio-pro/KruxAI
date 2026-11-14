package com.virtixstudio.kruxai.utils;

import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;

import androidx.core.graphics.ColorUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Coloration syntaxique légère (sans bibliothèque) pour les blocs de code du chat.
 * Les couleurs s'adaptent au thème de l'app : fond clair ou sombre, accent de l'utilisateur
 * utilisé pour les mots-clés.
 */
public final class SyntaxHighlighter {

    private SyntaxHighlighter() { }

    /** Au-delà, on n'applique pas de couleurs pour ne jamais bloquer l'interface. */
    private static final int MAX_CHARS = 20000;

    /** Couleurs par type de jeton. */
    public static final class Palette {
        public int text, comment, string, number, keyword, type, function, annotation, tag, attr;
    }

    /**
     * Palette dérivée du thème : fond du bloc, texte principal et accent de l'app.
     * Chaque couleur est ajustée pour rester lisible sur le fond choisi.
     */
    public static Palette paletteFor(int bg, int text, int accent) {
        boolean light = ColorUtils.calculateLuminance(bg) > 0.5;
        Palette p = new Palette();
        p.text = readable(text, bg);
        p.keyword = readable(accent, bg);
        p.comment = readable(ColorUtils.blendARGB(text, bg, 0.5f), bg, 2.6);
        if (light) {
            p.string = readable(0xFF2E7D32, bg);
            p.number = readable(0xFFC2410C, bg);
            p.type = readable(0xFF0E7490, bg);
            p.function = readable(0xFF1D4ED8, bg);
            p.annotation = readable(0xFF92400E, bg);
            p.tag = readable(0xFFBE123C, bg);
            p.attr = readable(0xFF6D28D9, bg);
        } else {
            p.string = readable(0xFF9ECE6A, bg);
            p.number = readable(0xFFFF9E64, bg);
            p.type = readable(0xFF2AC3DE, bg);
            p.function = readable(0xFF7AA2F7, bg);
            p.annotation = readable(0xFFE0AF68, bg);
            p.tag = readable(0xFFF7768E, bg);
            p.attr = readable(0xFFBB9AF7, bg);
        }
        return p;
    }

    private static int readable(int color, int bg) {
        return readable(color, bg, 4.0);
    }

    /** Éclaircit ou assombrit la couleur jusqu'à un contraste suffisant avec le fond. */
    private static int readable(int color, int bg, double minContrast) {
        int opaqueBg = ColorUtils.setAlphaComponent(bg, 255);
        int c = ColorUtils.setAlphaComponent(color, 255);
        if (ColorUtils.calculateContrast(c, opaqueBg) >= minContrast) return c;
        int target = ColorUtils.calculateLuminance(opaqueBg) > 0.5 ? Color.BLACK : Color.WHITE;
        for (int i = 1; i <= 10; i++) {
            int t = ColorUtils.blendARGB(c, target, i / 10f);
            if (ColorUtils.calculateContrast(t, opaqueBg) >= minContrast) return t;
        }
        return target;
    }

    /** Retourne le code avec ses couleurs. En cas de doute, le code brut est renvoyé sans couleurs. */
    public static CharSequence highlight(String code, String language, Palette p) {
        if (code == null) return "";
        SpannableStringBuilder out = new SpannableStringBuilder(code);
        out.setSpan(new ForegroundColorSpan(p.text), 0, out.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (code.isEmpty() || code.length() > MAX_CHARS) return out;
        try {
            Family f = familyOf(language, code);
            Pattern pattern = patternFor(f);
            Matcher m = pattern.matcher(code);
            while (m.find()) {
                int color;
                boolean bold = false;
                if (m.group(G_COMMENT) != null) color = p.comment;
                else if (m.group(G_STRING) != null) color = f == Family.JSON && isJsonKey(code, m.end()) ? p.attr : p.string;
                else if (m.group(G_ANNOT) != null) color = p.annotation;
                else if (m.group(G_NUMBER) != null) color = p.number;
                else if (m.group(G_TAG) != null) { color = p.tag; }
                else if (m.group(G_ATTR) != null) color = p.attr;
                else if (m.group(G_KEYWORD) != null) { color = p.keyword; bold = true; }
                else if (m.group(G_TYPE) != null) color = p.type;
                else if (m.group(G_FUNC) != null) color = p.function;
                else continue;
                out.setSpan(new ForegroundColorSpan(color), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (bold) out.setSpan(new StyleSpan(Typeface.BOLD), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } catch (Throwable ignored) {
            // Une regex trop gourmande ne doit jamais faire planter le chat : code non coloré.
        }
        return out;
    }

    /** En JSON, une chaîne suivie de « : » est une clé. */
    private static boolean isJsonKey(String code, int end) {
        int i = end;
        while (i < code.length() && (code.charAt(i) == ' ' || code.charAt(i) == '\t')) i++;
        return i < code.length() && code.charAt(i) == ':';
    }

    // ---------------------------------------------------------------- familles de langages

    private enum Family { CLIKE, HASH, SQL, JSON, MARKUP, CSS, PLAIN }

    private static final int G_COMMENT = 1, G_STRING = 2, G_ANNOT = 3, G_NUMBER = 4,
            G_TAG = 5, G_ATTR = 6, G_KEYWORD = 7, G_TYPE = 8, G_FUNC = 9;

    private static Family familyOf(String language, String code) {
        String l = language == null ? "" : language.toLowerCase(Locale.ROOT).trim();
        switch (l) {
            case "java": case "kotlin": case "kt": case "kts": case "javascript": case "js": case "jsx":
            case "typescript": case "ts": case "tsx": case "c": case "cpp": case "c++": case "h":
            case "csharp": case "cs": case "c#": case "go": case "rust": case "rs": case "swift":
            case "php": case "dart": case "gradle": case "groovy": case "scala":
                return Family.CLIKE;
            case "python": case "py": case "bash": case "sh": case "shell": case "zsh": case "yaml":
            case "yml": case "ruby": case "rb": case "toml": case "properties": case "dockerfile":
            case "makefile": case "r":
                return Family.HASH;
            case "sql": case "mysql": case "postgresql": case "sqlite":
                return Family.SQL;
            case "json": case "jsonc":
                return Family.JSON;
            case "xml": case "html": case "htm": case "svg": case "xhtml":
                return Family.MARKUP;
            case "css": case "scss": case "less":
                return Family.CSS;
            case "":
                return guess(code);
            default:
                return Family.PLAIN;
        }
    }

    /** Sans langage indiqué, on devine d'après le début du code. */
    private static Family guess(String code) {
        String t = code.trim();
        if (t.startsWith("<")) return Family.MARKUP;
        if (t.startsWith("{") || t.startsWith("[")) return Family.JSON;
        if (t.startsWith("#!") || t.startsWith("$ ")) return Family.HASH;
        return Family.CLIKE;
    }

    // ---------------------------------------------------------------- mots-clés

    private static final Set<String> KW_CLIKE = words(
            "abstract assert async await boolean break byte case catch char class const continue "
            + "data default defer delete do double else enum export extends extern false final finally "
            + "float for fn fun func function goto if impl implements import in include inline instanceof "
            + "int interface internal is lateinit let long match mod mut namespace new nil null object "
            + "open operator override package private protected pub public readonly ref return sealed "
            + "self short signed sizeof static string struct super suspend switch synchronized this "
            + "throw throws trait true try type typealias typeof union unsigned use val var virtual "
            + "void volatile when where while with yield");

    private static final Set<String> KW_HASH = words(
            "and as assert async await break case class continue def del do done elif else end esac "
            + "except export False fi finally for from function global if import in is lambda local "
            + "nil None not or pass print raise require return self then True try unless until while "
            + "with yield echo cd sudo pkg apt git npm pip gradle");

    private static final Set<String> KW_SQL = words(
            "select from where insert into values update set delete create table drop alter add "
            + "index primary key foreign references join inner left right outer on group by order "
            + "having limit offset as and or not null is in like between distinct union all case when "
            + "then else end default unique constraint view database asc desc count sum avg min max");

    private static final Set<String> KW_JSON = words("true false null");

    private static final Set<String> KW_CSS = words("important inherit initial unset auto none");

    private static Set<String> words(String s) {
        return new HashSet<>(Arrays.asList(s.split("\\s+")));
    }

    // ---------------------------------------------------------------- expressions régulières

    private static final Map<Family, Pattern> CACHE = new HashMap<>();

    private static synchronized Pattern patternFor(Family f) {
        Pattern p = CACHE.get(f);
        if (p == null) {
            p = build(f);
            CACHE.put(f, p);
        }
        return p;
    }

    private static final String STR_DQ = "\"(?:\\\\.|[^\"\\\\\\n])*\"?";
    private static final String STR_SQ = "'(?:\\\\.|[^'\\\\\\n])*'?";
    private static final String NUM = "\\b(?:0[xX][0-9a-fA-F_]+|\\d[\\d_]*(?:\\.\\d+)?(?:[eE][+-]?\\d+)?[fFlLdD]?)\\b";

    /**
     * Ordre des groupes (fixe) : 1 commentaire, 2 chaîne, 3 annotation, 4 nombre, 5 balise,
     * 6 attribut, 7 mot-clé, 8 type, 9 appel de fonction. Un groupe inutilisé reste vide mais présent.
     */
    private static Pattern build(Family f) {
        String none = "(?!)";
        String comment, string, annot = none, number = NUM, tag = none, attr = none, kw = none,
                type = none, func = none;
        int flags = 0;
        switch (f) {
            case CLIKE:
                comment = "//[^\\n]*|/\\*[\\s\\S]*?(?:\\*/|$)";
                string = "\"\"\"[\\s\\S]*?(?:\"\"\"|$)|" + STR_DQ + "|" + STR_SQ + "|`(?:\\\\.|[^`\\\\])*`?";
                annot = "@[A-Za-z_][\\w.]*";
                kw = "\\b(?:" + join(KW_CLIKE) + ")\\b";
                type = "\\b[A-Z][A-Za-z0-9_]*\\b";
                func = "\\b[a-z_][A-Za-z0-9_]*(?=\\s*\\()";
                break;
            case HASH:
                comment = "(?m)#[^\\n]*";
                string = "\"\"\"[\\s\\S]*?(?:\"\"\"|$)|'''[\\s\\S]*?(?:'''|$)|" + STR_DQ + "|" + STR_SQ;
                annot = "(?m)^[ \\t]*@[A-Za-z_][\\w.]*";
                kw = "\\b(?:" + join(KW_HASH) + ")\\b";
                type = "\\b[A-Z][A-Za-z0-9_]*\\b";
                func = "\\b[a-z_][A-Za-z0-9_]*(?=\\s*\\()";
                break;
            case SQL:
                flags = Pattern.CASE_INSENSITIVE;
                comment = "--[^\\n]*|/\\*[\\s\\S]*?(?:\\*/|$)";
                string = STR_SQ + "|" + STR_DQ;
                kw = "\\b(?:" + join(KW_SQL) + ")\\b";
                func = "\\b[a-z_][A-Za-z0-9_]*(?=\\s*\\()";
                break;
            case JSON:
                comment = "//[^\\n]*|/\\*[\\s\\S]*?(?:\\*/|$)";
                string = STR_DQ;
                kw = "\\b(?:" + join(KW_JSON) + ")\\b";
                break;
            case MARKUP:
                comment = "<!--[\\s\\S]*?(?:-->|$)";
                string = STR_DQ + "|" + STR_SQ;
                number = none;
                tag = "</?[A-Za-z][\\w:.-]*|/?>";
                attr = "\\b[A-Za-z_:][\\w:.-]*(?=\\s*=)";
                break;
            case CSS:
                comment = "/\\*[\\s\\S]*?(?:\\*/|$)";
                string = STR_DQ + "|" + STR_SQ;
                annot = "@[A-Za-z-]+";
                number = "#[0-9a-fA-F]{3,8}\\b|-?\\b\\d+(?:\\.\\d+)?(?:px|em|rem|vh|vw|%|s|ms|deg)?";
                attr = "[A-Za-z-]+(?=\\s*:)";
                kw = "\\b(?:" + join(KW_CSS) + ")\\b";
                type = "(?<![\\w-])[.#][A-Za-z_][\\w-]*";
                break;
            default:
                comment = none;
                string = STR_DQ + "|" + STR_SQ;
                break;
        }
        String regex = "(" + comment + ")|(" + string + ")|(" + annot + ")|(" + number + ")|("
                + tag + ")|(" + attr + ")|(" + kw + ")|(" + type + ")|(" + func + ")";
        return Pattern.compile(regex, flags);
    }

    private static String join(Set<String> set) {
        StringBuilder sb = new StringBuilder();
        for (String s : set) {
            if (sb.length() > 0) sb.append('|');
            sb.append(Pattern.quote(s));
        }
        return sb.toString();
    }
}
