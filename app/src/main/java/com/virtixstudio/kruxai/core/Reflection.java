package com.virtixstudio.kruxai.core;

import com.virtixstudio.kruxai.api.ApiClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyse préalable d'une demande : avant de répondre, Krux comprend ce qu'on lui demande
 * (type, complexité, besoin de recherche web, plan en étapes, pièges) et en tire des consignes
 * ajoutées au prompt. L'analyse est faite par un petit modèle rapide (ApiClient.sendQuiet) et
 * seulement pour les demandes qui le méritent, pour ne pas gaspiller de quota.
 * Si l'analyse échoue ou dépasse le délai, la réponse part normalement, sans consigne en plus.
 */
public final class Reflection {

    private Reflection() {}

    /** Appelé une seule fois, avec les consignes à ajouter au prompt (chaîne vide = rien à ajouter). */
    public interface Ready { void go(String guidance); }

    private static final long TIMEOUT_MS = 6000L;
    private static final int MIN_CHARS = 60;
    private static final int MIN_CHARS_THINKING = 25;
    private static final int LONG_REQUEST = 220;

    private static final Pattern COMPLEX = Pattern.compile(
            "\\b(site|app|application|projet|cr[ée]e|cr[ée]er|g[ée]n[èe]re|d[ée]veloppe|code|bug|erreur|plante|"
          + "compar|analyse|architecture|strat[ée]gie|plan|explique|pourquoi|comment|recherche|cherche|actualit|"
          + "dernier|derni[èe]re|version|prix|meilleur|innov|optimis|am[ée]liore|refactor)",
            Pattern.CASE_INSENSITIVE);

    private static final String ANALYZER_PROMPT =
            "Tu analyses la demande d'un utilisateur à l'assistant Krux, avant qu'il ne réponde. "
          + "Réponds UNIQUEMENT par un objet JSON valide, sans texte autour ni balises. Champs : "
          + "\"intent\" (un seul parmi : chat, code, debug, web, comparaison, projet, fichier, calcul, github, memoire), "
          + "\"complexity\" (simple, moyenne ou complexe), "
          + "\"needs_web\" (true si la réponse dépend d'informations récentes, de prix, de versions ou de faits précis à vérifier), "
          + "\"needs_plan\" (true si la demande exige plusieurs étapes ou un livrable important), "
          + "\"steps\" (3 à 6 étapes très courtes si needs_plan, sinon liste vide), "
          + "\"risks\" (0 à 2 pièges ou ambiguïtés à surveiller, très courts).";

    /** Décision locale, sans réseau : la demande mérite-t-elle une analyse ? */
    public static boolean worthAnalyzing(String prompt, boolean hasFile, boolean thinking) {
        if (prompt == null) return false;
        String p = prompt.trim();
        if (hasFile && p.length() >= 12) return true;      // un fichier joint : la demande est presque toujours de fond
        if (p.length() < (thinking ? MIN_CHARS_THINKING : MIN_CHARS)) return false;
        if (thinking || p.length() >= LONG_REQUEST) return true;
        return COMPLEX.matcher(p).find();
    }

    /** Lance l'analyse si elle est utile, puis appelle ready.go(...) une seule fois, au plus tard après le délai. */
    public static void analyze(String prompt, boolean hasFile, boolean thinking, final Ready ready) {
        if (!worthAnalyzing(prompt, hasFile, thinking)) {
            ready.go("");
            return;
        }
        final AtomicBoolean fired = new AtomicBoolean(false);
        final Runnable[] fire = new Runnable[1];

        Thread timer = new Thread(() -> {
            try { Thread.sleep(TIMEOUT_MS); } catch (InterruptedException e) { return; }
            if (fired.compareAndSet(false, true)) ready.go("");
        }, "krux-reflection-timeout");
        timer.setDaemon(true);
        timer.start();

        String shown = prompt.trim();
        if (shown.length() > 1500) shown = shown.substring(0, 1500);
        String message = "Demande de l'utilisateur :\n" + shown + (hasFile ? "\n(Un fichier est joint à cette demande.)" : "");

        ApiClient.sendQuiet(ANALYZER_PROMPT, message, text -> {
            timer.interrupt();
            if (!fired.compareAndSet(false, true)) return;
            String g;
            try { g = guidance(text); } catch (Exception e) { g = ""; }
            ready.go(g);
        });
    }

    // ------------------------------------------------------------------ lecture de la réponse du modèle

    private static final Pattern P_INTENT = Pattern.compile("\"intent\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern P_COMPLEXITY = Pattern.compile("\"complexity\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern P_WEB = Pattern.compile("\"needs_web\"\\s*:\\s*(true|false)");
    private static final Pattern P_PLAN = Pattern.compile("\"needs_plan\"\\s*:\\s*(true|false)");
    private static final Pattern P_STEPS = Pattern.compile("\"steps\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
    private static final Pattern P_RISKS = Pattern.compile("\"risks\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
    private static final Pattern P_STR = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    /** Transforme la réponse de l'analyseur en consignes pour le prompt. "" si rien d'utile ou réponse illisible. */
    static String guidance(String analyzerText) {
        if (analyzerText == null) return "";
        String t = analyzerText;
        String intent = first(P_INTENT, t).toLowerCase(Locale.ROOT);
        String complexity = first(P_COMPLEXITY, t).toLowerCase(Locale.ROOT);
        if (intent.isEmpty() && complexity.isEmpty()) return "";

        boolean web = first(P_WEB, t).equals("true");
        boolean plan = first(P_PLAN, t).equals("true");
        List<String> steps = strings(first(P_STEPS, t), 6);
        List<String> risks = strings(first(P_RISKS, t), 2);
        boolean simple = complexity.equals("simple");

        if (simple && !web && risks.isEmpty()) return "";

        StringBuilder g = new StringBuilder();
        g.append("\n\nANALYSE PRÉALABLE DE CETTE DEMANDE (faite par l'app ; suis-la, sans la citer ni la recopier) :");
        g.append("\n- Type : ").append(label(intent));
        if (!complexity.isEmpty()) g.append(", complexité ").append(clean(complexity, 20));
        g.append('.');
        if (web) {
            g.append("\n- Elle dépend de faits récents ou vérifiables : cherche avec web_search avant de répondre. ")
             .append("Si la recherche échoue, dis-le au lieu de deviner.");
        }
        if (plan && steps.size() >= 2) {
            g.append("\n- Annonce d'abord un plan court (").append(steps.size()).append(" étapes) puis exécute-le : ");
            for (int i = 0; i < steps.size(); i++) {
                if (i > 0) g.append(" ; ");
                g.append(i + 1).append(") ").append(steps.get(i));
            }
            g.append('.');
        }
        if (!risks.isEmpty()) {
            g.append("\n- À surveiller : ");
            for (int i = 0; i < risks.size(); i++) {
                if (i > 0) g.append(" ; ");
                g.append(risks.get(i));
            }
            g.append('.');
        }
        g.append("\n- Avant d'envoyer, relis ta réponse : elle doit couvrir tout ce qui est demandé et ne contenir aucune information inventée.");
        return g.toString();
    }

    private static String label(String intent) {
        switch (intent) {
            case "code": return "écriture de code";
            case "debug": return "correction de bug";
            case "web": return "recherche d'informations";
            case "comparaison": return "comparaison";
            case "projet": return "création de projet";
            case "fichier": return "analyse de fichier";
            case "calcul": return "calcul";
            case "github": return "GitHub";
            case "memoire": return "mémoire";
            case "chat": return "conversation";
            default: return "demande générale";
        }
    }

    private static String first(Pattern p, String t) {
        Matcher m = p.matcher(t);
        return m.find() ? m.group(1) : "";
    }

    private static List<String> strings(String arrayBody, int max) {
        List<String> out = new ArrayList<>();
        Matcher m = P_STR.matcher(arrayBody);
        while (m.find() && out.size() < max) {
            String s = clean(m.group(1), 120);
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    /** Texte venant du modèle : une seule ligne, longueur bornée, sans balises ni guillemets parasites. */
    private static String clean(String s, int max) {
        String c = s.replace("\\n", " ").replace("\\\"", "\"").replaceAll("[\\r\\n<>]+", " ").replaceAll("\\s+", " ").trim();
        return c.length() > max ? c.substring(0, max).trim() : c;
    }
}
