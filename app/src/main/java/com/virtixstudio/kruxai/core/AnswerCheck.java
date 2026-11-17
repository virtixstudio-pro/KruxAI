package com.virtixstudio.kruxai.core;

import com.virtixstudio.kruxai.api.ApiClient;
import com.virtixstudio.kruxai.models.KruxModel;
import com.virtixstudio.kruxai.models.SearchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vérification avant l'envoi : quand la demande a été jugée « de fond » par l'analyse préalable,
 * un relecteur léger relit la réponse de Krux. S'il y trouve un vrai problème (fait inventé, code laissé
 * incomplet, consigne ignorée, contradiction), Krux réécrit sa réponse une fois, puis l'envoie.
 *
 * Règles de sécurité : la réponse d'origine part toujours si la relecture échoue, dépasse le délai ou si
 * la révision échoue ; les réponses qui contiennent des fichiers ou des outils ne sont jamais relues
 * (elles ont leurs propres contrôles) ; une seule révision au maximum.
 */
public final class AnswerCheck {

    private AnswerCheck() {}

    private static final long CHECK_TIMEOUT_MS = 8000L;
    private static final int MIN_CHARS = 400;
    private static final int MAX_SHOWN_ANSWER = 7000;
    private static final int MAX_SHOWN_REQUEST = 1500;

    private static final String CHECK_PROMPT =
            "Tu es le relecteur de Krux. On te donne la demande de l'utilisateur et la réponse que Krux s'apprête à envoyer. "
          + "Cherche UNIQUEMENT des problèmes réels et graves : "
          + "(1) informations factuelles précises inventées ou affirmées sans preuve alors qu'elles devraient être vérifiées (chiffres, versions, dates, noms, prix) ; "
          + "(2) code ou texte laissé incomplet (« ... », TODO, parties manquantes) alors qu'il était demandé complet ; "
          + "(3) consigne explicite de l'utilisateur ignorée (langue, format, quantité, contrainte) ; "
          + "(4) contradiction ou erreur manifeste. "
          + "Ne critique ni le style, ni la longueur, ni le ton. "
          + "Réponds UNIQUEMENT par un objet JSON valide, sans texte autour : "
          + "{\"ok\":true ou false,\"severity\":\"minor\" ou \"major\",\"problems\":[0 à 3 problèmes très courts]}. "
          + "ok=true s'il n'y a rien de grave.";

    /** Résultat de la relecture. */
    static final class Verdict {
        final boolean major;
        final List<String> problems;
        Verdict(boolean major, List<String> problems) { this.major = major; this.problems = problems; }
    }

    /** Vrai si cette réponse peut être relue (assez longue, sans fichiers ni outils). */
    static boolean shouldCheck(String answer) {
        if (answer == null || answer.trim().length() < MIN_CHARS) return false;
        return !(answer.contains("<KRUX_FIL") || answer.contains("<KRUX_TOOL") || answer.contains("<REMEMBER"));
    }

    private static final Pattern P_OK = Pattern.compile("\"ok\"\\s*:\\s*(true|false)");
    private static final Pattern P_SEV = Pattern.compile("\"severity\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern P_PROBS = Pattern.compile("\"problems\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
    private static final Pattern P_STR = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    /** Lit la réponse du relecteur ; null si illisible. */
    static Verdict parse(String text) {
        if (text == null) return null;
        Matcher ok = P_OK.matcher(text);
        if (!ok.find()) return null;
        boolean fine = ok.group(1).equals("true");
        Matcher sev = P_SEV.matcher(text);
        boolean major = sev.find() && sev.group(1).toLowerCase(Locale.ROOT).equals("major");
        List<String> problems = new ArrayList<>();
        Matcher pb = P_PROBS.matcher(text);
        if (pb.find()) {
            Matcher s = P_STR.matcher(pb.group(1));
            while (s.find() && problems.size() < 3) {
                String c = clean(s.group(1), 160);
                if (!c.isEmpty()) problems.add(c);
            }
        }
        // on ne révise que si le relecteur dit « pas ok », « grave », et a nommé au moins un problème
        return new Verdict(!fine && major && !problems.isEmpty(), problems);
    }

    private static String clean(String s, int max) {
        String c = s.replace("\\n", " ").replace("\\\"", "\"").replaceAll("[\\r\\n<>]+", " ").replaceAll("\\s+", " ").trim();
        return c.length() > max ? c.substring(0, max).trim() : c;
    }

    static String revisionTail(List<String> problems) {
        StringBuilder sb = new StringBuilder("\n\nRÉVISION : un relecteur a relevé des problèmes dans ta réponse précédente :");
        for (String p : problems) sb.append("\n- ").append(p);
        sb.append("\nRéécris ta réponse complète en les corrigeant. Ne mentionne pas cette révision. ")
          .append("N'invente rien : si une information ne peut pas être vérifiée, dis-le clairement.");
        return sb.toString();
    }

    /**
     * Point d'accroche pour l'interface : appelé seulement quand une relecture a vraiment lieu
     * (jamais pour une réponse trop courte ou contenant des fichiers/outils). Appels depuis un thread d'arrière-plan.
     */
    public interface Hook {
        /** La relecture démarre. */
        void onChecking();
        /** Le relecteur a trouvé un problème grave : Krux réécrit sa réponse. */
        void onRevising();
    }

    public static ApiClient.ApiCallback wrap(
            final ApiClient.ApiCallback delegate,
            final KruxModel model,
            final String system,
            final String user) {
        return wrap(delegate, model, system, user, null);
    }

    /**
     * Enveloppe le callback de la réponse : la réponse est relue avant d'être livrée.
     * model, system et user sont ceux de la demande d'origine (pour la révision éventuelle).
     */
    public static ApiClient.ApiCallback wrap(
            final ApiClient.ApiCallback delegate,
            final KruxModel model,
            final String system,
            final String user,
            final Hook hook) {
        return new ApiClient.ApiCallback() {
            private final AtomicBoolean cancelled = new AtomicBoolean(false);

            @Override
            public void onSuccess(final String response, final String modelBrand) {
                if (!shouldCheck(response)) {
                    delegate.onSuccess(response, modelBrand);
                    return;
                }
                final AtomicBoolean done = new AtomicBoolean(false);
                if (hook != null) { try { hook.onChecking(); } catch (Exception ignored) {} }
                Thread timer = new Thread(() -> {
                    try { Thread.sleep(CHECK_TIMEOUT_MS); } catch (InterruptedException e) { return; }
                    if (done.compareAndSet(false, true) && !cancelled.get()) delegate.onSuccess(response, modelBrand);
                }, "krux-check-timeout");
                timer.setDaemon(true);
                timer.start();

                String shownAnswer = response.length() > MAX_SHOWN_ANSWER ? response.substring(0, MAX_SHOWN_ANSWER) : response;
                String shownRequest = user.length() > MAX_SHOWN_REQUEST ? user.substring(user.length() - MAX_SHOWN_REQUEST) : user;
                String message = "DEMANDE DE L'UTILISATEUR :\n" + shownRequest + "\n\nRÉPONSE DE KRUX À RELIRE :\n" + shownAnswer;

                ApiClient.sendQuiet(CHECK_PROMPT, message, text -> {
                    timer.interrupt();
                    if (!done.compareAndSet(false, true)) return;
                    if (cancelled.get()) return;
                    Verdict v;
                    try { v = parse(text); } catch (Exception e) { v = null; }
                    if (v == null || !v.major) {
                        delegate.onSuccess(response, modelBrand);
                        return;
                    }
                    revise(response, modelBrand, v.problems);
                });
            }

            private void revise(final String original, final String brand, List<String> problems) {
                if (hook != null) { try { hook.onRevising(); } catch (Exception ignored) {} }
                String previous = original.length() > 12000 ? original.substring(0, 12000) : original;
                String reviseUser = user + "\n\nTa réponse précédente (à corriger) :\n" + previous;
                ApiClient.sendRequest(model, system + revisionTail(problems), reviseUser, new ApiClient.ApiCallback() {
                    @Override public void onSuccess(String revised, String revisedBrand) {
                        if (cancelled.get()) return;
                        boolean usable = revised != null && !revised.trim().isEmpty();
                        delegate.onSuccess(usable ? revised : original, usable ? revisedBrand : brand);
                    }
                    @Override public void onError(String friendlyMessage) {
                        if (!cancelled.get()) delegate.onSuccess(original, brand);   // la révision a échoué : on garde la réponse d'origine
                    }
                    @Override public void onCancelled() { delegate.onCancelled(); }
                    @Override public void onFallback() { delegate.onFallback(); }
                    @Override public void onWaiting(int seconds) { delegate.onWaiting(seconds); }
                    @Override public void onWebSearchStarted(String query) { delegate.onWebSearchStarted(query); }
                    @Override public void onWebSearchFinished(List<SearchResult> results) { delegate.onWebSearchFinished(results); }
                    // pas de onPartialResponse : la réponse d'origine reste affichée jusqu'à la version corrigée
                });
            }

            @Override public void onError(String friendlyMessage) { delegate.onError(friendlyMessage); }
            @Override public void onCancelled() { cancelled.set(true); delegate.onCancelled(); }
            @Override public void onPartialResponse(String response, String modelBrand) { delegate.onPartialResponse(response, modelBrand); }
            @Override public void onFallback() { delegate.onFallback(); }
            @Override public void onWaiting(int seconds) { delegate.onWaiting(seconds); }
            @Override public void onWebSearchStarted(String query) { delegate.onWebSearchStarted(query); }
            @Override public void onWebSearchFinished(List<SearchResult> results) { delegate.onWebSearchFinished(results); }
        };
    }
}
