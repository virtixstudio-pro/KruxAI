package com.virtixstudio.kruxai.builder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

/**
 * Vérifications faites AVANT d'envoyer le code d'une application générée sur GitHub.
 *
 * Objectif : attraper localement (en quelques millisecondes, sans quota IA ni build GitHub)
 * les erreurs de compilation les plus fréquentes du code généré par un modèle :
 * imports manquants, ressources inexistantes, android:exported absent, switch sur R,
 * XML cassé, accolades déséquilibrées, bibliothèques non disponibles, fichiers tronqués...
 *
 * Deux étapes :
 *  1. normalize() répare automatiquement ce qui peut l'être sans risque (aucun appel IA) ;
 *  2. validate() liste ce qui reste, en français, pour que l'IA le corrige précisément.
 *
 * Java pur (compatible Java 8) : testable sur un simple JDK.
 */
public final class BuildValidator {

    public static final String MANIFEST = "app/src/main/AndroidManifest.xml";
    static final String JAVA_DIR = "app/src/main/java/com/krux/generated/";
    static final String RES_DIR = "app/src/main/res/";
    static final String ASSETS_DIR = "app/src/main/assets/";
    static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    static final String PKG = BuildSpec.NAMESPACE;
    public static final int MAX_PROBLEMS = 12;

    private BuildValidator() { }

    // ------------------------------------------------------------------ chemins

    private static final Pattern RES_DIR_NAME = Pattern.compile(
        "(layout|drawable|menu|anim|animator|color|xml|raw|font|values|mipmap|navigation|transition)(-[a-z0-9]+)*");
    private static final Pattern RES_FILE = Pattern.compile("[a-z][a-z0-9_]*\\.xml");
    private static final Pattern RAW_FILE = Pattern.compile("[a-z][a-z0-9_]*\\.(txt|json|html|css|js|csv|xml)");
    private static final Pattern ASSET_FILE = Pattern.compile("[A-Za-z0-9_.-]+(/[A-Za-z0-9_.-]+)*\\.(html|css|js|json|txt|csv)");
    private static final Pattern JAVA_FILE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*\\.java");

    /** Retourne null si le chemin est autorisé, sinon la raison du refus. */
    public static String pathProblem(String path) {
        if (path == null || path.isEmpty()) return "Chemin vide";
        if (!path.matches("app/src/main/[A-Za-z0-9_./-]+") || path.contains("..") || path.contains("//") || path.contains("/."))
            return "Chemin refusé : " + path;
        if (path.equals(MANIFEST)) return null;
        if (path.startsWith(JAVA_DIR)) {
            String f = path.substring(JAVA_DIR.length());
            return JAVA_FILE.matcher(f).matches() ? null
                : "Chemin Java refusé (un seul dossier com/krux/generated, extension .java) : " + path;
        }
        if (path.startsWith(RES_DIR)) {
            String rest = path.substring(RES_DIR.length());
            int i = rest.indexOf('/');
            if (i <= 0 || rest.indexOf('/', i + 1) >= 0) return "Ressource refusée (forme res/<dossier>/<fichier>) : " + path;
            String dir = rest.substring(0, i), file = rest.substring(i + 1);
            if (!RES_DIR_NAME.matcher(dir).matches()) return "Dossier de ressources inconnu : " + dir;
            boolean ok = dir.startsWith("raw") ? RAW_FILE.matcher(file).matches() : RES_FILE.matcher(file).matches();
            return ok ? null : "Nom de fichier de ressource invalide (minuscules, chiffres, _ ; .xml seulement, pas d'image) : " + path;
        }
        if (path.startsWith(ASSETS_DIR)) {
            return ASSET_FILE.matcher(path.substring(ASSETS_DIR.length())).matches() ? null
                : "Fichier assets refusé (html, css, js, json, txt, csv seulement) : " + path;
        }
        return "Chemin refusé : " + path + " (autorisés : AndroidManifest.xml, java/com/krux/generated, res, assets)";
    }

    // ------------------------------------------------------------------ analyse du Java

    /** Remplace commentaires et contenus de chaînes par des espaces (garde les retours à la ligne). */
    static String strip(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int n = src.length(), i = 0;
        while (i < n) {
            char c = src.charAt(i);
            char d = i + 1 < n ? src.charAt(i + 1) : 0;
            if (c == '/' && d == '/') {
                while (i < n && src.charAt(i) != '\n') { out.append(' '); i++; }
            } else if (c == '/' && d == '*') {
                out.append("  "); i += 2;
                while (i < n && !(src.charAt(i) == '*' && i + 1 < n && src.charAt(i + 1) == '/')) {
                    out.append(src.charAt(i) == '\n' ? '\n' : ' '); i++;
                }
                if (i < n) { out.append("  "); i += 2; }
            } else if (c == '"' && src.startsWith("\"\"\"", i)) {
                out.append("   "); i += 3;
                while (i < n && !src.startsWith("\"\"\"", i)) {
                    if (src.charAt(i) == '\\' && i + 1 < n) { out.append("  "); i += 2; continue; }
                    out.append(src.charAt(i) == '\n' ? '\n' : ' '); i++;
                }
                if (i < n) { out.append("   "); i += 3; }
            } else if (c == '"' || c == '\'') {
                out.append(c); i++;
                while (i < n && src.charAt(i) != c && src.charAt(i) != '\n') {
                    if (src.charAt(i) == '\\' && i + 1 < n && src.charAt(i + 1) != '\n') { out.append("  "); i += 2; continue; }
                    out.append(' '); i++;
                }
                if (i < n && src.charAt(i) == c) { out.append(c); i++; }
            } else {
                out.append(c); i++;
            }
        }
        return out.toString();
    }

    /** Vérifie l'équilibre des (), [] et {} sur du code déjà passé par strip(). */
    static String balance(String s) {
        Deque<int[]> st = new ArrayDeque<>();
        int line = 1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n') line++;
            else if (c == '(' || c == '[' || c == '{') st.push(new int[]{c, line});
            else if (c == ')' || c == ']' || c == '}') {
                if (st.isEmpty()) return "'" + c + "' en trop ligne " + line;
                int[] o = st.pop();
                char oc = (char) o[0];
                if ((oc == '(' && c != ')') || (oc == '[' && c != ']') || (oc == '{' && c != '}'))
                    return "'" + oc + "' ouvert ligne " + o[1] + " mais fermé par '" + c + "' ligne " + line;
            }
        }
        if (!st.isEmpty()) {
            int[] o = st.peek();
            return "'" + (char) o[0] + "' ouvert ligne " + o[1] + " et jamais fermé (fichier coupé ?)";
        }
        return null;
    }

    private static final String[] ALLOWED_IMPORTS = {
        "java.", "javax.", "android.", "androidx.appcompat.", "androidx.recyclerview.", "androidx.constraintlayout.",
        "androidx.core.", "androidx.annotation.", "androidx.activity.", "androidx.fragment.", "androidx.lifecycle.",
        "com.google.android.material.", "org.json.", "org.w3c.", "org.xml.", PKG + "."
    };

    /** Classes courantes : nom simple -> nom complet (sert à ajouter les imports oubliés). */
    private static final Map<String, String> KNOWN = new LinkedHashMap<>();
    static {
        String[][] k = {
            {"AppCompatActivity", "androidx.appcompat.app.AppCompatActivity"},
            {"Bundle", "android.os.Bundle"}, {"Handler", "android.os.Handler"}, {"Looper", "android.os.Looper"},
            {"Intent", "android.content.Intent"}, {"Context", "android.content.Context"},
            {"SharedPreferences", "android.content.SharedPreferences"},
            {"View", "android.view.View"}, {"ViewGroup", "android.view.ViewGroup"}, {"LayoutInflater", "android.view.LayoutInflater"},
            {"Menu", "android.view.Menu"}, {"MenuItem", "android.view.MenuItem"}, {"MotionEvent", "android.view.MotionEvent"},
            {"Gravity", "android.view.Gravity"},
            {"TextView", "android.widget.TextView"}, {"Button", "android.widget.Button"}, {"EditText", "android.widget.EditText"},
            {"ImageView", "android.widget.ImageView"}, {"Toast", "android.widget.Toast"}, {"LinearLayout", "android.widget.LinearLayout"},
            {"FrameLayout", "android.widget.FrameLayout"}, {"ProgressBar", "android.widget.ProgressBar"},
            {"CheckBox", "android.widget.CheckBox"}, {"ScrollView", "android.widget.ScrollView"}, {"SeekBar", "android.widget.SeekBar"},
            {"Spinner", "android.widget.Spinner"}, {"ArrayAdapter", "android.widget.ArrayAdapter"},
            {"AdapterView", "android.widget.AdapterView"}, {"RadioButton", "android.widget.RadioButton"},
            {"RadioGroup", "android.widget.RadioGroup"}, {"ListView", "android.widget.ListView"},
            {"Color", "android.graphics.Color"}, {"Canvas", "android.graphics.Canvas"}, {"Paint", "android.graphics.Paint"},
            {"Bitmap", "android.graphics.Bitmap"}, {"Log", "android.util.Log"},
            {"RecyclerView", "androidx.recyclerview.widget.RecyclerView"},
            {"LinearLayoutManager", "androidx.recyclerview.widget.LinearLayoutManager"},
            {"GridLayoutManager", "androidx.recyclerview.widget.GridLayoutManager"},
            {"List", "java.util.List"}, {"ArrayList", "java.util.ArrayList"}, {"Map", "java.util.Map"}, {"HashMap", "java.util.HashMap"},
            {"Set", "java.util.Set"}, {"HashSet", "java.util.HashSet"}, {"Random", "java.util.Random"}, {"Locale", "java.util.Locale"},
            {"Collections", "java.util.Collections"}, {"Arrays", "java.util.Arrays"}, {"Date", "java.util.Date"},
            {"Iterator", "java.util.Iterator"}, {"SimpleDateFormat", "java.text.SimpleDateFormat"},
            {"IOException", "java.io.IOException"}, {"InputStream", "java.io.InputStream"},
            {"JSONObject", "org.json.JSONObject"}, {"JSONArray", "org.json.JSONArray"}, {"JSONException", "org.json.JSONException"},
            {"MaterialButton", "com.google.android.material.button.MaterialButton"},
            {"TextInputEditText", "com.google.android.material.textfield.TextInputEditText"}
        };
        for (String[] e : k) KNOWN.put(e[0], e[1]);
    }

    private static final Pattern IMPORT_LINE = Pattern.compile("(?m)^[ \\t]*import[ \\t]+(static[ \\t]+)?([\\w.]+?)(\\.\\*)?[ \\t]*;");
    private static final Pattern PACKAGE_LINE = Pattern.compile("(?m)^[ \\t]*package[ \\t]+([\\w.]+)[ \\t]*;");
    private static final Pattern PUBLIC_TYPE = Pattern.compile("\\bpublic\\s+(?:final\\s+|abstract\\s+)*(?:class|interface|enum)\\s+(\\w+)");
    private static final Pattern R_REF = Pattern.compile("(?<![\\w.])R\\.(\\w+)\\.(\\w+)");
    private static final Pattern CASE_R = Pattern.compile("\\bcase\\s+(?:[\\w.]+\\.)?R\\.\\w+\\.\\w+");
    private static final Pattern BACK_OVERRIDE = Pattern.compile("\\bvoid\\s+onBackPressed\\s*\\(");
    private static final Pattern PLACEHOLDER = Pattern.compile(
        "(?im)^\\s*(//|/\\*|\\*|<!--).*(\\.\\.\\.\\s*(\\*/|-->)?\\s*$|rest of (the )?(code|file|class)|remaining (code|methods)|(le )?reste (du|de la|des) (code|classe|fichier|m[ée]thodes)|code inchang|same as (above|before)|unchanged)");

    /** Code sans lignes package/import et sans chaînes ni commentaires : sert à repérer les usages de classes. */
    private static String codeBody(String stripped) {
        return stripped.replaceAll("(?m)^[ \\t]*(import|package)\\b[^;]*;", "");
    }

    private static boolean isImported(String simple, String fqcn, String stripped) {
        Matcher m = IMPORT_LINE.matcher(stripped);
        String pkg = fqcn.substring(0, fqcn.lastIndexOf('.'));
        while (m.find()) {
            String name = m.group(2);
            if (m.group(3) != null) { if (name.equals(pkg)) return true; }
            else if (name.equals(fqcn) || name.endsWith("." + simple)) return true;
        }
        return false;
    }

    private static boolean usesName(String body, String simple) {
        return Pattern.compile("(?<![\\w.$])" + simple + "(?![\\w$])").matcher(body).find();
    }

    private static boolean declaresName(String body, String simple) {
        return Pattern.compile("\\b(class|interface|enum)\\s+" + simple + "\\b").matcher(body).find();
    }

    // ------------------------------------------------------------------ XML

    private static Document parseXml(String xml, String path, List<String> problems) {
        if (xml.contains("<!DOCTYPE") || xml.contains("<!ENTITY")) {
            problems.add(path + " : DOCTYPE et ENTITY sont interdits dans les fichiers XML");
            return null;
        }
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            try { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Exception ignore) { /* non supporté */ }
            try { f.setExpandEntityReferences(false); } catch (Exception ignore) { /* non supporté */ }
            DocumentBuilder b = f.newDocumentBuilder();
            b.setErrorHandler(new ErrorHandler() {
                @Override public void warning(SAXParseException e) { }
                @Override public void error(SAXParseException e) throws SAXParseException { throw e; }
                @Override public void fatalError(SAXParseException e) throws SAXParseException { throw e; }
            });
            return b.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (SAXParseException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().replaceAll("\\s+", " ");
            if (msg.length() > 110) msg = msg.substring(0, 110);
            problems.add(path + " : XML invalide ligne " + e.getLineNumber() + " (" + msg + ")");
        } catch (Exception e) {
            problems.add(path + " : XML illisible (" + e.getClass().getSimpleName() + ")");
        }
        return null;
    }

    // ------------------------------------------------------------------ ressources

    static String norm(String n) { return n.replace('.', '_'); }

    private static final Set<String> CHECKED_TYPES = new HashSet<>(java.util.Arrays.asList(
        "string", "color", "drawable", "layout", "style", "dimen", "menu", "mipmap", "anim", "xml", "id",
        "integer", "bool", "array", "plurals", "raw", "font", "animator", "navigation", "transition"));

    private static final String[] LIB_PREFIX = {
        "abc_", "appbar_", "material_", "mtrl_", "m3_", "design_", "bottom_sheet_", "character_counter_", "fab_transformation_",
        "password_toggle_", "searchview_", "side_sheet_", "hide_bottom_view_", "error_icon_", "clear_text_", "exposed_dropdown_",
        "item_view_", "path_password_", "icon_content_", "call_notification_", "status_bar_notification_", "notification_",
        "tooltip_", "ic_arrow_back_black", "ic_clear_black", "ic_search_black", "ic_menu_arrow", "ic_mtrl", "highlighted_text_",
        "primary_text_", "secondary_text_", "switch_thumb_", "browser_actions_", "androidx_", "primary_material", "secondary_material",
        "background_material", "accent_material", "foreground_material", "button_material", "ripple_material", "error_color_material"
    };

    private static final String[] LIB_STYLE = {
        "Theme.AppCompat", "Theme.MaterialComponents", "Theme.Material3", "Theme.Material", "Theme.DeviceDefault", "Theme.Holo",
        "Theme.Black", "Theme.Light", "Theme.Translucent", "Theme.NoTitleBar", "Theme.Dialog", "Theme.Wallpaper", "Theme.Panel",
        "ThemeOverlay.", "Widget.", "TextAppearance.", "Base.", "Platform.", "Animation.", "ShapeAppearance", "RtlOverlay.",
        "Preference", "AlertDialog", "MaterialAlertDialog", "CardView", "ExpandedMenuView"
    };

    static boolean isLibStyle(String name) {
        for (String p : LIB_STYLE) if (name.startsWith(p)) return true;
        return false;
    }

    static boolean isLibRes(String type, String name) {
        if (type.equals("style")) return isLibStyle(name);
        String n = norm(name);
        for (String p : LIB_PREFIX) if (n.startsWith(p)) return true;
        return false;
    }

    static final class Res {
        final Map<String, Set<String>> def = new HashMap<>();
        void add(String type, String name) {
            Set<String> s = def.get(type);
            if (s == null) { s = new HashSet<>(); def.put(type, s); }
            s.add(norm(name));
        }
        boolean has(String type, String name) {
            Set<String> s = def.get(type);
            return s != null && s.contains(norm(name));
        }
    }

    private static final Pattern REF = Pattern.compile("@(\\+?)(\\w+)/([\\w.]+)");
    private static final Pattern XML_COMMENT = Pattern.compile("(?s)<!--.*?-->");

    private static String dirOf(String path) {
        String rest = path.substring(RES_DIR.length());
        return rest.substring(0, rest.indexOf('/'));
    }

    private static String typeOfDir(String dir) {
        int i = dir.indexOf('-');
        return i < 0 ? dir : dir.substring(0, i);
    }

    private static String valuesType(Element e) {
        String t = e.getTagName();
        switch (t) {
            case "string": case "color": case "dimen": case "integer": case "bool": case "style": case "plurals":
            case "fraction": case "drawable":
                return t;
            case "string-array": case "integer-array": case "array":
                return "array";
            case "item": {
                String ty = e.getAttribute("type");
                return ty.isEmpty() ? null : ty;
            }
            default:
                return null;
        }
    }

    static Res collectResources(Map<String, String> files, List<String> problems) {
        Res res = new Res();
        for (Map.Entry<String, String> en : files.entrySet()) {
            String path = en.getKey();
            if (!path.startsWith(RES_DIR) || !path.endsWith(".xml") && !path.contains("/raw/")) continue;
            if (pathProblem(path) != null) continue;
            String dir = dirOf(path), type = typeOfDir(dir);
            String file = path.substring(path.lastIndexOf('/') + 1);
            String base = file.contains(".") ? file.substring(0, file.lastIndexOf('.')) : file;
            if (type.equals("values")) {
                Document d = parseXml(en.getValue(), path, problems);
                if (d == null || d.getDocumentElement() == null) continue;
                NodeList kids = d.getDocumentElement().getChildNodes();
                for (int i = 0; i < kids.getLength(); i++) {
                    Node nd = kids.item(i);
                    if (nd.getNodeType() != Node.ELEMENT_NODE) continue;
                    Element e = (Element) nd;
                    String t = valuesType(e), name = e.getAttribute("name");
                    if (t != null && !name.isEmpty()) res.add(t, name);
                }
            } else {
                res.add(type, base);
            }
        }
        Matcher m = Pattern.compile("@\\+id/([\\w.]+)").matcher(allXml(files));
        while (m.find()) res.add("id", m.group(1));
        return res;
    }

    private static String allXml(Map<String, String> files) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : files.entrySet())
            if (e.getKey().endsWith(".xml")) sb.append(XML_COMMENT.matcher(e.getValue()).replaceAll(" ")).append('\n');
        return sb.toString();
    }

    // ------------------------------------------------------------------ réparations automatiques

    /**
     * Répare sans IA ce qui se répare sans risque. Retourne une copie ; la liste "applied"
     * reçoit une phrase par réparation (utile pour les journaux).
     */
    public static Map<String, String> normalize(Map<String, String> in, List<String> applied) {
        Map<String, String> files = new LinkedHashMap<>(in);

        // 1. XML : espaces ou BOM avant la déclaration (AAPT refuse)
        for (Map.Entry<String, String> e : files.entrySet()) {
            if (!e.getKey().endsWith(".xml")) continue;
            String c = e.getValue();
            if (!c.isEmpty() && (c.charAt(0) == '\uFEFF' || Character.isWhitespace(c.charAt(0)))) {
                String t = c.replaceFirst("^[\\s\\uFEFF]+", "");
                if (t.startsWith("<")) { e.setValue(t); applied.add(e.getKey() + " : espaces avant le XML retirés"); }
            }
        }

        // 2. Java : package et imports
        for (Map.Entry<String, String> e : files.entrySet()) {
            if (!e.getKey().startsWith(JAVA_DIR) || !e.getKey().endsWith(".java")) continue;
            String c = e.getValue();
            String stripped = strip(c);
            Matcher pm = PACKAGE_LINE.matcher(stripped);
            if (pm.find()) {
                String old = pm.group(1);
                if (!old.equals(PKG)) {
                    c = c.substring(0, pm.start()) + "package " + PKG + ";" + c.substring(pm.end());
                    c = c.replaceAll("(?m)^([ \\t]*import[ \\t]+(?:static[ \\t]+)?)" + Pattern.quote(old) + "\\.", "$1" + PKG + ".");
                    applied.add(e.getKey() + " : package corrigé (" + old + " -> " + PKG + ")");
                }
            } else {
                c = "package " + PKG + ";\n\n" + c;
                applied.add(e.getKey() + " : ligne package ajoutée");
            }
            stripped = strip(c);
            String body = codeBody(stripped);
            Set<String> add = new LinkedHashSet<>();
            for (Map.Entry<String, String> k : KNOWN.entrySet()) {
                String simple = k.getKey(), fq = k.getValue();
                if (usesName(body, simple) && !declaresName(body, simple) && !isImported(simple, fq, stripped)) add.add(fq);
            }
            if (!add.isEmpty()) {
                Matcher p2 = PACKAGE_LINE.matcher(stripped);
                if (p2.find()) {
                    StringBuilder ins = new StringBuilder();
                    for (String fq : add) ins.append("\nimport ").append(fq).append(';');
                    c = c.substring(0, p2.end()) + ins + c.substring(p2.end());
                    applied.add(e.getKey() + " : " + add.size() + " import(s) ajouté(s)");
                }
            }
            e.setValue(c);
        }

        // 3. Manifeste
        String m = files.get(MANIFEST);
        if (m != null) {
            String before = m;
            m = m.replaceFirst("(<manifest\\b[^>]*?)\\s+package\\s*=\\s*\"[^\"]*\"", "$1");
            if (!m.equals(before)) applied.add("AndroidManifest.xml : attribut package retiré (le namespace est dans build.gradle)");

            Matcher am = Pattern.compile("<activity(?=[\\s/>])([^>]*?)(/?)>").matcher(m);
            StringBuffer sb = new StringBuffer();
            int fixed = 0;
            while (am.find()) {
                String attrs = am.group(1);
                if (attrs.contains("android:exported")) { am.appendReplacement(sb, Matcher.quoteReplacement(am.group())); continue; }
                boolean self = !am.group(2).isEmpty();
                boolean hasFilter = false;
                if (!self) {
                    int end = m.indexOf("</activity>", am.end());
                    String bodyA = end < 0 ? "" : m.substring(am.end(), end);
                    hasFilter = bodyA.contains("<intent-filter");
                }
                am.appendReplacement(sb, Matcher.quoteReplacement("<activity android:exported=\"" + hasFilter + "\"" + attrs + am.group(2) + ">"));
                fixed++;
            }
            am.appendTail(sb);
            m = sb.toString();
            if (fixed > 0) applied.add("AndroidManifest.xml : android:exported ajouté sur " + fixed + " activité(s)");
            files.put(MANIFEST, m);

            // icônes qui pointent vers des ressources absentes
            List<String> ignore = new ArrayList<>();
            Res res = collectResources(files, ignore);
            Matcher im = Pattern.compile("\\s+android:(?:icon|roundIcon)\\s*=\\s*\"@(\\w+)/([\\w.]+)\"").matcher(m);
            StringBuffer sb2 = new StringBuffer();
            int removed = 0;
            while (im.find()) {
                if (!res.has(im.group(1), im.group(2))) { im.appendReplacement(sb2, ""); removed++; }
                else im.appendReplacement(sb2, Matcher.quoteReplacement(im.group()));
            }
            im.appendTail(sb2);
            if (removed > 0) { files.put(MANIFEST, sb2.toString()); applied.add("AndroidManifest.xml : " + removed + " référence(s) d'icône inexistante(s) retirée(s)"); }
        }
        return files;
    }

    // ------------------------------------------------------------------ validation

    /** Liste les problèmes restants (vide = rien à signaler). Chaque phrase est exploitable par l'IA. */
    public static List<String> validate(Map<String, String> files) {
        List<String> problems = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Map<String, String> javaFiles = new LinkedHashMap<>();

        for (Map.Entry<String, String> e : files.entrySet()) {
            String path = e.getKey(), c = e.getValue();
            String pp = pathProblem(path);
            if (pp != null) { problems.add(pp); continue; }
            if (c == null || c.trim().isEmpty()) { problems.add(path + " : fichier vide"); continue; }
            if (PLACEHOLDER.matcher(c).find())
                problems.add(path + " : contient un commentaire de remplacement (\"...\", \"reste du code\") ; écris le code COMPLET");
            if (path.endsWith(".java")) javaFiles.put(path, c);
        }

        String manifest = files.get(MANIFEST);
        if (manifest == null) problems.add("AndroidManifest.xml manquant");

        // ---- XML bien formé (une seule fois par fichier)
        for (Map.Entry<String, String> e : files.entrySet())
            if (e.getKey().endsWith(".xml") && pathProblem(e.getKey()) == null && e.getValue() != null && !e.getValue().trim().isEmpty())
                parseXml(e.getValue(), e.getKey(), problems);

        Res res = collectResources(files, new ArrayList<String>());

        // ---- XML bien formé + références de ressources
        for (Map.Entry<String, String> e : files.entrySet()) {
            String path = e.getKey();
            if (!path.endsWith(".xml") || pathProblem(path) != null) continue;
            String text = XML_COMMENT.matcher(e.getValue()).replaceAll(" ");
            Matcher m = REF.matcher(text);
            while (m.find()) {
                boolean def = !m.group(1).isEmpty();
                String type = m.group(2), name = m.group(3);
                if (def || !CHECKED_TYPES.contains(type)) continue;
                if (res.has(type, name) || isLibRes(type, name)) continue;
                String key = path + "|" + type + "|" + name;
                if (seen.add(key))
                    problems.add(path + " référence @" + type + "/" + name + " qui n'existe pas (crée-la ou retire la référence)");
            }
        }

        // ---- styles : parents
        for (Map.Entry<String, String> e : files.entrySet()) {
            String path = e.getKey();
            if (!path.startsWith(RES_DIR) || !path.endsWith(".xml") || pathProblem(path) != null) continue;
            if (!typeOfDir(dirOf(path)).equals("values")) continue;
            Document d = parseXml(e.getValue(), path, new ArrayList<String>());
            if (d == null) continue;
            NodeList styles = d.getElementsByTagName("style");
            for (int i = 0; i < styles.getLength(); i++) {
                Element s = (Element) styles.item(i);
                String parent = s.getAttribute("parent");
                if (parent.isEmpty() || parent.startsWith("@") || parent.contains(":")) continue;
                if (!res.has("style", parent) && !isLibStyle(parent))
                    problems.add(path + " : le style " + s.getAttribute("name") + " a pour parent " + parent + " qui n'existe pas");
            }
        }

        // ---- layouts : classes de vues utilisables
        for (Map.Entry<String, String> e : files.entrySet()) {
            String path = e.getKey();
            if (!path.startsWith(RES_DIR) || !path.endsWith(".xml") || pathProblem(path) != null) continue;
            if (!typeOfDir(dirOf(path)).equals("layout")) continue;
            Document d = parseXml(e.getValue(), path, new ArrayList<String>());
            if (d == null) continue;
            NodeList all = d.getElementsByTagName("*");
            Set<String> bad = new LinkedHashSet<>();
            for (int i = 0; i < all.getLength(); i++) {
                String tag = ((Element) all.item(i)).getTagName();
                if (!tag.contains(".")) continue;
                if (tag.startsWith(PKG + ".")) {
                    String cls = tag.substring(PKG.length() + 1);
                    if (!files.containsKey(JAVA_DIR + cls + ".java")) bad.add(tag + " (classe absente)");
                    continue;
                }
                boolean ok = false;
                for (String p : ALLOWED_IMPORTS) if (tag.startsWith(p)) { ok = true; break; }
                if (!ok) bad.add(tag);
            }
            for (String b : bad)
                problems.add(path + " utilise la vue " + b + " : bibliothèque non disponible (seuls appcompat, material, recyclerview, constraintlayout existent)");
        }

        // ---- manifeste
        if (manifest != null) checkManifest(manifest, files, problems);

        // ---- Java
        for (Map.Entry<String, String> e : javaFiles.entrySet()) {
            String path = e.getKey(), c = e.getValue();
            String base = path.substring(path.lastIndexOf('/') + 1, path.length() - 5);
            String stripped = strip(c);
            String bal = balance(stripped);
            if (bal != null) problems.add(path + " : accolades/parenthèses incorrectes : " + bal);
            Matcher pm = PACKAGE_LINE.matcher(stripped);
            if (!pm.find() || !pm.group(1).equals(PKG)) problems.add(path + " : la première instruction doit être \"package " + PKG + ";\"");
            Matcher tm = PUBLIC_TYPE.matcher(stripped);
            if (tm.find() && !tm.group(1).equals(base))
                problems.add(path + " : la classe publique " + tm.group(1) + " doit s'appeler " + base + " (comme le fichier)");

            Matcher im = IMPORT_LINE.matcher(stripped);
            while (im.find()) {
                String name = im.group(2);
                boolean ok = false;
                for (String p : ALLOWED_IMPORTS) if ((name + (im.group(3) != null ? ".*" : "")).startsWith(p) || (name + ".").startsWith(p)) { ok = true; break; }
                if (!ok) {
                    String key = path + "|imp|" + name;
                    if (seen.add(key)) problems.add(path + " importe " + name + " : bibliothèque non disponible (autorisées : android, androidx.appcompat/recyclerview/constraintlayout/core, material, org.json)");
                }
            }
            if (CASE_R.matcher(stripped).find())
                problems.add(path + " : \"case R.id.xxx\" ne compile plus (les R ne sont pas constants) ; remplace le switch par des if/else sur l'id");
            if (BACK_OVERRIDE.matcher(stripped).find())
                problems.add(path + " : onBackPressed() n'est plus appelé avec targetSdk 36 ; utilise getOnBackPressedDispatcher().addCallback(...)");

            String body = codeBody(stripped);
            for (Map.Entry<String, String> k : KNOWN.entrySet()) {
                if (usesName(body, k.getKey()) && !declaresName(body, k.getKey()) && !isImported(k.getKey(), k.getValue(), stripped)) {
                    String key = path + "|noimp|" + k.getKey();
                    if (seen.add(key)) problems.add(path + " utilise " + k.getKey() + " sans l'importer (import " + k.getValue() + ";)");
                }
            }

            Matcher rm = R_REF.matcher(stripped);
            while (rm.find()) {
                String type = rm.group(1), name = rm.group(2);
                if (!CHECKED_TYPES.contains(type)) continue;
                if (res.has(type, name) || isLibRes(type, name)) continue;
                String key = path + "|R|" + type + "|" + name;
                if (seen.add(key)) problems.add(path + " utilise R." + type + "." + name + " qui n'existe pas dans les ressources");
            }
        }

        if (problems.size() > MAX_PROBLEMS) {
            List<String> cut = new ArrayList<>(problems.subList(0, MAX_PROBLEMS));
            cut.add("... et " + (problems.size() - MAX_PROBLEMS) + " autre(s) problème(s) du même genre");
            return cut;
        }
        return problems;
    }

    private static String resolveActivity(String name) {
        if (name.startsWith(".")) return PKG + name;
        if (name.contains(".")) return name;
        return PKG + "." + name;
    }

    private static void checkManifest(String manifest, Map<String, String> files, List<String> problems) {
        Document d = parseXml(manifest, MANIFEST, new ArrayList<String>());
        if (d == null) return;
        NodeList apps = d.getElementsByTagName("application");
        if (apps.getLength() == 0) { problems.add("AndroidManifest.xml : balise <application> manquante"); return; }
        NodeList acts = d.getElementsByTagName("activity");
        boolean launcher = false;
        for (int i = 0; i < acts.getLength(); i++) {
            Element a = (Element) acts.item(i);
            String name = a.getAttributeNS(ANDROID_NS, "name");
            if (name.isEmpty()) { problems.add("AndroidManifest.xml : une <activity> n'a pas d'android:name"); continue; }
            String cls = resolveActivity(name);
            if (!cls.startsWith(PKG + ".") || cls.substring(PKG.length() + 1).contains(".")) {
                problems.add("AndroidManifest.xml : l'activité " + name + " doit être dans le package " + PKG);
            } else if (!files.containsKey(JAVA_DIR + cls.substring(PKG.length() + 1) + ".java")) {
                problems.add("AndroidManifest.xml déclare l'activité " + name + " mais le fichier " + cls.substring(PKG.length() + 1) + ".java n'existe pas");
            }
            NodeList filters = a.getElementsByTagName("intent-filter");
            if (filters.getLength() > 0 && !a.hasAttributeNS(ANDROID_NS, "exported"))
                problems.add("AndroidManifest.xml : l'activité " + name + " a un intent-filter sans android:exported");
            for (int j = 0; j < filters.getLength(); j++) {
                Element f = (Element) filters.item(j);
                boolean main = false, cat = false;
                NodeList acs = f.getElementsByTagName("action"), cts = f.getElementsByTagName("category");
                for (int k = 0; k < acs.getLength(); k++)
                    if ("android.intent.action.MAIN".equals(((Element) acs.item(k)).getAttributeNS(ANDROID_NS, "name"))) main = true;
                for (int k = 0; k < cts.getLength(); k++)
                    if ("android.intent.category.LAUNCHER".equals(((Element) cts.item(k)).getAttributeNS(ANDROID_NS, "name"))) cat = true;
                if (main && cat) launcher = true;
            }
        }
        if (!launcher) problems.add("AndroidManifest.xml : aucune activité de lancement (intent-filter MAIN + LAUNCHER)");
        if (d.getDocumentElement().hasAttribute("package"))
            problems.add("AndroidManifest.xml : l'attribut package est interdit (retire-le)");
    }

    /** Met les problèmes en liste à puces pour le prompt de correction envoyé à l'IA. */
    public static String format(List<String> problems) {
        StringBuilder sb = new StringBuilder();
        for (String p : problems) sb.append("- ").append(p).append('\n');
        return sb.toString();
    }
}
