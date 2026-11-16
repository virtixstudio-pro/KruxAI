package com.virtixstudio.kruxai.builder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nettoie la maquette HTML écrite par l'IA avant de l'afficher à l'utilisateur.
 * La maquette est interactive : un petit JavaScript en ligne est conservé (navigation entre écrans),
 * mais jamais de réseau, de stockage, de lien, de formulaire ni de ressource externe. Java pur.
 * Barrières en plus : politique CSP du document, et l'écran qui l'affiche bloque le réseau et les fichiers.
 */
public final class PreviewHtml {

    public static final int MAX_CHARS = 30_000;
    private static final int F = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;

    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", F);
    private static final Pattern SCRIPT = Pattern.compile("<script\\b([^>]*)>(.*?)</script\\s*>", F);
    private static final Pattern SCRIPT_LEFT = Pattern.compile("</?\\s*script\\b[^>]*>", F);
    /** Interdits dans le JavaScript de la maquette : réseau, stockage, navigation, code dynamique (sensible à la casse : « function » reste permis). */
    private static final Pattern BAD_JS = Pattern.compile(
        "\\b(fetch|XMLHttpRequest|WebSocket|EventSource|importScripts|sendBeacon|postMessage|Worker|SharedWorker|"
        + "localStorage|sessionStorage|indexedDB|openDatabase|cookie|eval|Function|require|opener|navigator)\\b"
        + "|\\bimport\\s*[({*\\w]|(window|document|self|top|parent)\\s*\\.\\s*(location|open|write|writeln)\\b"
        + "|\\blocation\\s*(=|\\.)|\\.\\s*open\\s*\\(|\\bhistory\\s*\\.|(?i:(javascript|vbscript)\\s*:)", Pattern.DOTALL);
    public static final int MAX_SCRIPT_CHARS = 12_000;
    private static final Pattern BAD_TAG = Pattern.compile(
        "</?\\s*(iframe|frame|frameset|object|embed|applet|link|meta|base|form|title|!doctype|html|head)\\b[^>]*>", F);
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

    /** Garde onclick="show('x')" et co. tant que leur code ne touche ni le réseau ni la navigation. */
    private static String filterEvents(String s) {
        Matcher m = EVENT.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String v = m.group(1).replaceAll("^[\"']|[\"']$", "");
            boolean safe = v.length() <= 400 && !BAD_JS.matcher(v).find();
            m.appendReplacement(sb, Matcher.quoteReplacement(safe ? m.group(0) : ""));
        }
        m.appendTail(sb);
        return sb.toString();
    }

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

        // Scripts : on garde ceux qui sont en ligne et sans réseau, remplacés par un repère le temps du nettoyage.
        java.util.List<String> kept = new java.util.ArrayList<>();
        Matcher sm = SCRIPT.matcher(s);
        StringBuffer sb = new StringBuffer();
        int budget = MAX_SCRIPT_CHARS;
        while (sm.find()) {
            String attrs = sm.group(1) == null ? "" : sm.group(1).toLowerCase();
            String code = sm.group(2) == null ? "" : sm.group(2).trim();
            String repl = "";
            if (!attrs.contains("src") && !code.isEmpty() && code.length() <= budget && !BAD_JS.matcher(code).find()) {
                budget -= code.length();
                repl = "@@KRUXJS" + kept.size() + "@@";
                kept.add(code);
            }
            sm.appendReplacement(sb, Matcher.quoteReplacement(repl));
        }
        sm.appendTail(sb);
        s = SCRIPT_LEFT.matcher(sb.toString()).replaceAll("");

        s = DOCTYPE.matcher(s).replaceAll("");
        s = BODY_OPEN.matcher(s).replaceAll("<div id=\"krux-body\"");
        s = BODY_CLOSE.matcher(s).replaceAll("</div>");
        s = BAD_TAG.matcher(s).replaceAll("");
        s = filterEvents(s);
        s = stripUrlAttrs(s);
        s = CSS_IMPORT.matcher(s).replaceAll("");
        s = CSS_URL.matcher(s).replaceAll("none");
        s = SCRIPT_URL.matcher(s).replaceAll("");
        s = EXPRESSION.matcher(s).replaceAll("(");
        if (s.replaceAll("@@KRUXJS\\d+@@", "").replaceAll("<[^>]*>", "").trim().isEmpty()) return "";
        for (int i = 0; i < kept.size(); i++) s = s.replace("@@KRUXJS" + i + "@@", "<script>" + kept.get(i) + "</script>");

        return "<!doctype html><html><head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; style-src 'unsafe-inline'; "
            + "img-src data:; font-src data:; script-src 'unsafe-inline'; connect-src 'none'; form-action 'none'; "
            + "base-uri 'none'; frame-src 'none'\">"
            + "<style>*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}html,body{margin:0;padding:0}"
            + "body{font-family:system-ui,-apple-system,'Segoe UI',Roboto,sans-serif;-webkit-font-smoothing:antialiased}"
            + "#krux-body{min-height:100vh}*{max-width:100%}</style></head><body>" + s + "</body></html>";
    }
}
