package com.virtixstudio.kruxai.builder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mode « web » : règles, prompts et vérifications locales pour les apps React + TypeScript (src/**).
 * Java pur, sans Android. Les méthodes qui refusent retournent un texte (null = autorisé) : AppBuilder en fait une Refusal.
 */
public final class WebBuilder {

    private WebBuilder() { }

    public static final String ENTRY = "src/main.tsx";

    private static final Pattern SRC_FILE = Pattern.compile("src/[A-Za-z0-9_.-]+(/[A-Za-z0-9_.-]+){0,2}\\.(tsx|ts|css|svg|json)");
    private static final Pattern IMPORT = Pattern.compile(
        "(?:\\bfrom\\s*|\\bimport\\s*)['\"]([^'\"\\n]+)['\"]");
    private static final Pattern BANNED = Pattern.compile(
        "\\beval\\s*\\(|\\bnew\\s+Function\\b|\\bdocument\\s*\\.\\s*write|dangerouslySetInnerHTML|\\.\\s*innerHTML\\s*=|\\bimportScripts\\b"
        + "|\\bimport\\s*\\(|createElement\\s*\\(\\s*['\"]script['\"]|<script\\b");
    private static final String[] BARE_OK = {"react", "react-dom", "react-dom/client", "react/jsx-runtime"};

    // ------------------------------------------------------------------ chemins

    /** Seulement les chemins dangereux ou absurdes sont refusés ; le reste est filtré par normalize. */
    public static String pathProblem(String path) {
        if (path == null || path.isEmpty()) return "Chemin vide";
        if (path.contains("..") || path.contains("//") || path.startsWith("/") || path.length() > 120)
            return "Chemin refusé : " + path;
        return null;
    }

    /** Garde uniquement les fichiers que l'IA a le droit d'écrire : src/** (hors fichiers fournis par KRUX). */
    public static Map<String, String> normalize(Map<String, String> in) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : in.entrySet()) {
            String path = e.getKey().replaceFirst("^\\./", "");
            if (!SRC_FILE.matcher(path).matches() || path.endsWith(".d.ts") || WebTemplates.isShellPath(path)) continue;
            String c = e.getValue() == null ? "" : e.getValue().replace("\r\n", "\n");
            if (c.startsWith("\uFEFF")) c = c.substring(1);
            out.put(path, c);
        }
        return out;
    }

    // ------------------------------------------------------------------ politique (bloquante)

    private static String codeOnly(String src) {
        return src.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)(?<![:\"'])//.*$", " ");
    }

    /** null si tout va bien, sinon la raison du refus. */
    public static String policyProblem(Map<String, String> files, BuildSpec s) {
        if (files.size() > s.maxFiles)
            return "Application trop grosse : " + files.size() + " fichiers (maximum " + s.maxFiles + ")";
        int total = 0;
        for (Map.Entry<String, String> e : files.entrySet()) {
            String c = e.getValue();
            if (c.length() > s.maxFileChars)
                return "Application trop grosse : " + e.getKey() + " dépasse " + s.maxFileChars + " caractères";
            total += c.length();
            if (e.getKey().endsWith(".ts") || e.getKey().endsWith(".tsx")) {
                Matcher m = BANNED.matcher(codeOnly(c));
                if (m.find()) return "Application refusée : code non autorisé (" + m.group().trim() + ")";
            }
        }
        if (total > s.maxTotalChars)
            return "Application trop grosse : " + total + " caractères (maximum " + s.maxTotalChars + ")";
        return null;
    }

    // ------------------------------------------------------------------ vérification locale (aucun quota GitHub)

    private static String dirOf(String path) {
        int i = path.lastIndexOf('/');
        return i < 0 ? "" : path.substring(0, i);
    }

    /** Résout ./x et ../x par rapport au dossier, sans sortir de src/. Retourne null si ça sort. */
    private static String resolve(String dir, String rel) {
        List<String> parts = new ArrayList<>();
        for (String p : dir.split("/")) if (!p.isEmpty()) parts.add(p);
        for (String p : rel.split("/")) {
            if (p.isEmpty() || p.equals(".")) continue;
            if (p.equals("..")) { if (parts.isEmpty()) return null; parts.remove(parts.size() - 1); }
            else parts.add(p);
        }
        return String.join("/", parts);
    }

    private static boolean importExists(Map<String, String> files, String base) {
        if (base == null) return false;
        if (files.containsKey(base)) return true;
        for (String ext : new String[]{".tsx", ".ts", ".json", ".css", ".svg"})
            if (files.containsKey(base + ext)) return true;
        return files.containsKey(base + "/index.tsx") || files.containsKey(base + "/index.ts");
    }

    public static List<String> validate(Map<String, String> files) {
        List<String> problems = new ArrayList<>();
        String main = files.get(ENTRY);
        if (main == null) {
            problems.add("src/main.tsx manquant : c'est le point d'entrée (createRoot sur l'élément #root, import de ./styles.css).");
        } else if (!main.contains("createRoot") || !main.contains("getElementById")) {
            problems.add("src/main.tsx doit appeler createRoot(document.getElementById('root')!).render(<App />).");
        }
        for (Map.Entry<String, String> e : files.entrySet()) {
            String path = e.getKey();
            if (!(path.endsWith(".ts") || path.endsWith(".tsx"))) continue;
            Matcher m = IMPORT.matcher(codeOnly(e.getValue()));
            while (m.find()) {
                String spec = m.group(1);
                if (spec.startsWith(".")) {
                    if (!importExists(files, resolve(dirOf(path), spec)))
                        problems.add(path + " importe '" + spec + "' mais ce fichier n'existe pas dans src/.");
                } else {
                    boolean ok = false;
                    for (String b : BARE_OK) if (spec.equals(b)) ok = true;
                    if (!ok) problems.add(path + " importe le paquet '" + spec + "' : seuls react et react-dom sont installés. Retire-le ou écris le code toi-même.");
                }
            }
        }
        return problems;
    }

    /** Vrai si la couleur d'accent imposée apparaît dans un fichier src/ (CSS ou TSX). */
    public static boolean usesColor(Map<String, String> files, String hex) {
        if (hex == null) return true;
        String h = hex.toUpperCase(Locale.ROOT);
        for (String v : files.values()) if (v.toUpperCase(Locale.ROOT).contains(h)) return true;
        return false;
    }

    // ------------------------------------------------------------------ prompts

    public static String systemPrompt(BuildSpec s) {
        return "Tu génères une application mobile en React + TypeScript qui tourne dans une WebView Android. "
            + "Réponds UNIQUEMENT avec un objet JSON, sans texte autour ni balises markdown, au format : "
            + "{\"name\":\"nom-court-en-minuscules\",\"files\":[{\"path\":\"src/...\",\"content\":\"...\"}]}. "
            + "RÈGLES DU PROJET : fichiers uniquement sous src/ (.tsx, .ts, .css, .svg, .json), 2 niveaux de sous-dossiers au maximum ; "
            + "point d'entrée OBLIGATOIRE src/main.tsx : import { createRoot } from 'react-dom/client'; import './styles.css'; "
            + "createRoot(document.getElementById('root')!).render(<App />) ; "
            + "React 19 et TypeScript, composants fonctionnels et hooks, transformation JSX automatique (n'importe pas React pour le JSX) ; "
            + "AUCUN autre paquet que react et react-dom (ni routeur, ni kit UI, ni icônes, ni framework CSS, ni CDN) : écris le CSS toi-même dans src/styles.css "
            + "(variables CSS, thème clair et sombre avec prefers-color-scheme, mobile d'abord, zones tactiles d'au moins 44 px) ; "
            + "navigation entre écrans avec un état React (pas de bibliothèque de routage) ; "
            + "ne fournis AUCUN fichier hors de src/ (package.json, tsconfig, index.html, projet Android et workflow sont déjà fournis) et ne touche pas à src/krux.d.ts ; "
            + "chaque import relatif doit pointer vers un fichier que tu écris, tout ce qui est importé doit être exporté, "
            + "code COMPLET qui passe la vérification des types (mode non strict, mais les noms et les types doivent exister), sans TODO ni \"...\" ni commentaire du type \"reste du code\" ; "
            + "images : uniquement SVG en ligne, fichiers .svg ou emoji (aucune image externe) ; "
            + "données conservées avec localStorage (entouré de try/catch, avec une valeur par défaut) ; "
            + "PONT NATIF optionnel, toujours appelé avec ?. : window.Krux?.toast(texte), window.Krux?.share(texte), window.Krux?.copy(texte), window.Krux?.vibrate(ms) ; "
            + "l'affichage est bord à bord et les marges système sont déjà gérées ; la touche retour revient à l'écran précédent seulement si tu utilises history.pushState et l'événement popstate, sinon elle quitte l'app. "
            + "INTERDIT : eval, new Function, import() dynamique, dangerouslySetInnerHTML, innerHTML, document.write, balises script, chargement de code externe, "
            + "imitation d'une banque, d'une marque ou d'un écran de connexion, surveillance cachée de personnes, collecte de données personnelles à leur insu, "
            + "contenu adulte, jeux d'argent avec de l'argent réel. Réseau : fetch vers des adresses https uniquement, seulement si la demande l'exige. "
            + "AMBITION : une application complète, soignée et distinctive, pas une démo : plusieurs écrans avec une navigation claire, vraie logique métier, "
            + "états vides et messages d'erreur utiles, validation des saisies, textes réels (jamais de lorem ipsum), identité visuelle cohérente "
            + "et au moins une fonction originale qui la distingue d'un modèle générique. "
            + "LIMITES : " + s.maxFiles + " fichiers maximum, " + s.maxFileChars + " caractères maximum par fichier et " + s.maxTotalChars + " au total. "
            + "Si la demande est interdite ou impossible dans ces limites, réponds UNIQUEMENT {\"refuse\":\"raison courte en français\"}.";
    }

    public static String fixSystemPrompt(BuildSpec s) {
        return "Tu corriges une application React + TypeScript (WebView Android) dont la vérification ou la compilation échoue. "
            + "On te donne la description de l'app, la liste des problèmes (détectés avant l'envoi, ou lus dans le log réel : erreurs tsc « src/Fichier.tsx(ligne,colonne): error TSxxxx », erreurs esbuild) "
            + "et les fichiers actuels. Trouve la cause réelle et corrige-la sans réécrire ce qui fonctionne. "
            + "Réponds UNIQUEMENT avec un objet JSON, sans texte autour ni balises markdown : "
            + "{\"files\":[{\"path\":\"src/...\",\"content\":\"CONTENU COMPLET du fichier\"}]}. "
            + "Ne renvoie que les fichiers à créer ou remplacer, avec leur contenu complet (jamais de diff, jamais de \"...\"). "
            + "Mêmes règles : fichiers sous src/ uniquement, point d'entrée src/main.tsx, aucun paquet autre que react et react-dom, "
            + "chaque import relatif pointe vers un fichier existant, mêmes interdictions de code, "
            + "au maximum " + s.maxFileChars + " caractères par fichier.";
    }

    public static String modifySystemPrompt(BuildSpec s) {
        String fix = fixSystemPrompt(s);
        return "Tu modifies une application React + TypeScript existante selon la demande de l'utilisateur. "
            + "On te donne la description d'origine, la modification demandée et les fichiers actuels. "
            + "Applique la modification demandée et rien d'autre : garde l'identité visuelle, les couleurs et la navigation existantes sauf si la demande les change. "
            + fix.substring(fix.indexOf("Réponds UNIQUEMENT"));
    }
}
