package com.virtixstudio.kruxai.builder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nettoie la maquette HTML écrite par l'IA avant de l'afficher à l'utilisateur :
 * plus de script, de lien, d'image ou de ressource externe. Java pur, testé.
 * (L'écran qui l'affiche ajoute une seconde barrière : JavaScript coupé et réseau bloqué.)
 */
public final class PreviewHtml {

    public static final int MAX_CHARS = 30_000;
    private static final int F = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;

    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", F);
    private static final Pattern SCRIPT = Pattern.compile("<script\\b.*?</script\\s*>", F);
    private static final Pattern BAD_TAG = Pattern.compile(
        "</?\\s*(script|iframe|frame|frameset|object|embed|applet|link|meta|base|form|title|!doctype|html|head)\\b[^>]*>", F);
    private static final Pattern DOCTYPE = Pattern.compile("<!doctype[^>]*>", F);
    private static final Pattern EVENT = Pattern.compile("\\son[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)", F);
    private static final Pattern URL_ATTR = Pattern.compile(
        "\\s(src|href|action|formaction|xlink:href|poster|background|data)\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)", F);
    private static final Pattern CSS_URL = Pattern.compile("url\\(\\s*(?!['\"]?data:image/)[^)]*\\)", F);
    private static final Pattern CSS_IMPORT = Pattern.compile("@import[^;]*;?", F);
    private static final Pattern SCRIPT_URL = Pattern.compile("(javascript|vbscript)\\s*:", F);
    private static final Pattern EXPRESSION = Pattern.compile("expression\\s*\\(", F);
    private static final Pattern BODY_OPEN = Pattern.compile("<body\\b", F);
    private static final Pattern BODY_CLOSE = Pattern.compile("</body\\s*>", F);

    private PreviewHtml() { }

    private static String stripUrlAttrs(String s) {
        Matcher m = URL_ATTR.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String v = m.group(2).replaceAll("^[\"']|[\"']$", "").trim().toLowerCase();
            boolean safe = v.startsWith("#") || v.startsWith("data:image/");
            m.appendReplacement(sb, Matcher.quoteReplacement(safe ? m.group(0) : ""));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Retourne un document HTML complet et sûr, ou "" si la maquette est vide, absente ou trop longue. */
    public static String prepare(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.length() < 20 || s.length() > MAX_CHARS) return "";

        s = COMMENT.matcher(s).replaceAll("");
        s = SCRIPT.matcher(s).replaceAll("");
        s = DOCTYPE.matcher(s).replaceAll("");
        s = BODY_OPEN.matcher(s).replaceAll("<div id=\"krux-body\"");
        s = BODY_CLOSE.matcher(s).replaceAll("</div>");
        s = BAD_TAG.matcher(s).replaceAll("");
        s = EVENT.matcher(s).replaceAll("");
        s = stripUrlAttrs(s);
        s = CSS_IMPORT.matcher(s).replaceAll("");
        s = CSS_URL.matcher(s).replaceAll("none");
        s = SCRIPT_URL.matcher(s).replaceAll("");
        s = EXPRESSION.matcher(s).replaceAll("(");
        if (s.replaceAll("<[^>]*>", "").trim().isEmpty()) return "";

        return "<!doctype html><html><head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; style-src 'unsafe-inline'; img-src data:\">"
            + "<style>html,body{margin:0;padding:0}#krux-body{min-height:100vh;box-sizing:border-box}"
            + "*{max-width:100%}</style></head><body>" + s + "</body></html>";
    }
}
