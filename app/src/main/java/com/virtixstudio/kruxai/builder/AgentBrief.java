package com.virtixstudio.kruxai.builder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Réflexion préalable de Krux avant de créer une application : il se pose des questions à lui-même
 * (selon assets/AGENT.md), répond par un petit brief, et ce brief guide la génération du code.
 * Java pur, sans Android : testable sur n'importe quel JDK. Tout échec est silencieux côté appelant.
 */
public final class AgentBrief {
    private AgentBrief() {}

    private static final String DEFAULT_PLAYBOOK =
        "Tu es Krux. Avant d'écrire du code, tu te poses des questions et tu y réponds toi-même : qui utilisera l'application, "
        + "sa tâche principale, ses 3 à 5 écrans, ses données, son identité visuelle, sa fonction originale, ses états vides. "
        + "Tu ne répètes jamais une idée.";
    private static volatile String playbook = DEFAULT_PLAYBOOK;

    private static final String SCHEMA =
        "Réponds UNIQUEMENT avec un objet JSON, sans texte autour : "
        + "{\"concept\":\"une phrase\",\"audience\":\"...\",\"screens\":[\"écran : rôle\"],\"data\":\"...\","
        + "\"identity\":\"palette, formes, ton\",\"signature\":\"fonction originale\",\"empty_states\":\"...\","
        + "\"assumptions\":[\"...\"],\"needs\":[{\"what\":\"icône | base de données | clé API | autre\",\"why\":\"...\","
        + "\"how\":\"action concrète pour l'utilisateur, service gratuit sans carte\"}]}. "
        + "Listes courtes (5 écrans, 2 hypothèses, 3 besoins au plus), phrases courtes, rien de répété.";

    /** Contenu d'assets/AGENT.md ; une valeur absente ou aberrante garde la version intégrée. */
    public static void setPlaybook(String md) {
        if (md == null) return;
        String t = md.trim();
        if (t.length() >= 40 && t.length() <= 8000) playbook = t;
    }

    public static String systemPrompt() { return playbook + "\n\n" + SCHEMA; }

    public static String userPrompt(String description) {
        return "Demande de l'utilisateur : " + (description == null ? "" : description.trim());
    }

    public static final class Need {
        public final String what, why, how;
        Need(String what, String why, String how) { this.what = what; this.why = why; this.how = how; }
    }

    public static final class Brief {
        public String concept = "", audience = "", data = "", identity = "", signature = "", emptyStates = "";
        public final List<String> screens = new ArrayList<>();
        public final List<String> assumptions = new ArrayList<>();
        public final List<Need> needs = new ArrayList<>();

        /** Consigne ajoutée au prompt de génération (1800 caractères au plus). */
        public String guidance() {
            List<String> parts = new ArrayList<>();
            add(parts, "concept", concept);
            add(parts, "public", audience);
            if (!screens.isEmpty()) parts.add("écrans : " + join(screens, " | "));
            add(parts, "données", data);
            add(parts, "identité visuelle", identity);
            add(parts, "fonction originale", signature);
            add(parts, "états vides", emptyStates);
            if (parts.isEmpty()) return "";
            String g = "BRIEF DE CONCEPTION issu de ton analyse préalable (respecte-le, sans le répéter dans l'interface) : " + join(parts, " ; ") + ".";
            return g.length() > 1800 ? g.substring(0, 1799) + "…" : g;
        }

        /** Texte pour l'utilisateur à la fin de la création : ce qu'il peut ajouter. Vide s'il n'y a rien à dire. */
        public String userNote() {
            StringBuilder sb = new StringBuilder();
            Set<String> seen = new LinkedHashSet<>();
            int n = 0;
            for (Need nd : needs) {
                if (n >= 3) break;
                String line = nd.what.isEmpty() ? "" : nd.what + (nd.how.isEmpty() ? "" : " : " + nd.how) + (nd.why.isEmpty() ? "" : " (" + nd.why + ")");
                if (nd.what.isEmpty() || (nd.how.isEmpty() && nd.why.isEmpty())) continue;
                if (!seen.add(nd.what.toLowerCase())) continue;
                if (n == 0) sb.append("Pour aller plus loin :");
                sb.append("\n• ").append(clip(line, 220));
                n++;
            }
            if (!assumptions.isEmpty()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append("Hypothèses retenues : ").append(join(assumptions, " ; "));
            }
            return sb.toString();
        }
    }

    /** Lit la réponse de l'IA ; null si rien d'utilisable (on génère alors sans brief). */
    public static Brief parse(String raw) {
        if (raw == null) return null;
        String json = raw;
        int a = raw.indexOf('{'), b = raw.lastIndexOf('}');
        if (a >= 0 && b > a) json = raw.substring(a, b + 1);
        else if (a >= 0) json = raw.substring(a);
        Brief br = new Brief();
        br.concept = clip(str(json, "concept"), 240);
        br.audience = clip(str(json, "audience"), 200);
        br.data = clip(str(json, "data"), 240);
        br.identity = clip(str(json, "identity"), 300);
        br.signature = clip(str(json, "signature"), 240);
        br.emptyStates = clip(str(json, "empty_states"), 200);
        br.screens.addAll(strings(arrayBody(json, "screens"), 5));
        br.assumptions.addAll(strings(arrayBody(json, "assumptions"), 2));
        for (String obj : objects(arrayBody(json, "needs"))) {
            String what = clip(str(obj, "what"), 60), why = clip(str(obj, "why"), 140), how = clip(str(obj, "how"), 180);
            if (!what.isEmpty()) br.needs.add(new Need(what, why, how));
            if (br.needs.size() >= 3) break;
        }
        boolean usable = !br.concept.isEmpty() || !br.screens.isEmpty() || !br.identity.isEmpty();
        return usable ? br : null;
    }

    // ------------------------------------------------------------------ petits outils JSON (sans bibliothèque)

    private static void add(List<String> parts, String label, String value) {
        if (value != null && !value.isEmpty()) parts.add(label + " : " + value);
    }

    private static String join(List<String> l, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String s : l) { if (sb.length() > 0) sb.append(sep); sb.append(s); }
        return sb.toString();
    }

    static String clip(String s, int max) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > max ? t.substring(0, max - 1).trim() + "…" : t;
    }

    static String str(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", Pattern.DOTALL).matcher(json);
        return m.find() ? unescape(m.group(1)).trim() : "";
    }

    private static final Pattern QUOTED = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    static List<String> strings(String body, int max) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Matcher m = QUOTED.matcher(body);
        while (m.find() && out.size() < max) {
            String v = clip(unescape(m.group(1)), 160);
            if (!v.isEmpty() && seen.add(v.toLowerCase())) out.add(v);
        }
        return out;
    }

    /** Contenu entre crochets du tableau "key" (guillemets respectés) ; ce qui est lisible si la réponse est coupée. */
    static String arrayBody(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[").matcher(json);
        if (!m.find()) return "";
        int start = m.end(), depth = 1;
        boolean inStr = false;
        for (int j = start; j < json.length(); j++) {
            char c = json.charAt(j);
            if (inStr) { if (c == '\\') j++; else if (c == '"') inStr = false; continue; }
            if (c == '"') inStr = true;
            else if (c == '[') depth++;
            else if (c == ']') { depth--; if (depth == 0) return json.substring(start, j); }
        }
        return json.substring(start);
    }

    static List<String> objects(String body) {
        List<String> out = new ArrayList<>();
        int depth = 0, from = -1;
        boolean inStr = false;
        for (int j = 0; j < body.length(); j++) {
            char c = body.charAt(j);
            if (inStr) { if (c == '\\') j++; else if (c == '"') inStr = false; continue; }
            if (c == '"') inStr = true;
            else if (c == '{') { if (depth++ == 0) from = j; }
            else if (c == '}') { if (--depth == 0 && from >= 0) { out.add(body.substring(from, j + 1)); from = -1; } }
        }
        return out;
    }

    static String unescape(String s) {
        if (s.indexOf('\\') < 0) return s;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) { sb.append(c); continue; }
            char n = s.charAt(++i);
            switch (n) {
                case 'n': sb.append(' '); break;
                case 't': sb.append(' '); break;
                case 'r': break;
                case 'u':
                    if (i + 4 < s.length()) {
                        try { sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16)); i += 4; break; }
                        catch (NumberFormatException e) { /* garde le texte tel quel */ }
                    }
                    sb.append('u'); break;
                default: sb.append(n);
            }
        }
        return sb.toString();
    }
}
