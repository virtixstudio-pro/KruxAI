package com.virtixstudio.kruxai.utils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mémoire automatique, sans dépendre du modèle : repère ce que l'utilisateur dit de lui
 * (prénom, lieu, métier, goûts, projet, « retiens que… ») et ce qu'il demande d'oublier.
 * Java pur, testé. Ignore les sujets sensibles (santé, argent, secrets, âge…).
 */
public final class MemoryExtractor {

    private static final Pattern EXPLICIT = Pattern.compile(
            "(?i)\\b(?:souviens[- ]toi|retiens|rappelle[- ]toi|n'oublie pas|n’oublie pas|m[ée]morise|garde en m[ée]moire|note bien)\\s*(?:que|qu'|qu’|:)?\\s*(.{3,200})");
    private static final Pattern NAME = Pattern.compile(
            "(?i)\\b(?:je m'appelle|je m’appelle|mon pr[ée]nom (?:est|c'est|c’est)|mon nom (?:est|c'est|c’est)|appelle[- ]moi)\\s+([\\p{L}][\\p{L}'’ -]{1,30})");
    private static final Pattern PLACE = Pattern.compile(
            "(?i)\\b(?:j'habite|j’habite|je vis|je viens)\\s+(?:à|a|en|au|aux|de|d'|d’)\\s*([\\p{L}][\\p{L}'’ -]{1,40})");
    private static final Pattern JOB = Pattern.compile(
            "(?i)\\bje suis (?:un |une |)(d[ée]veloppeu(?:r|se)|[ée]tudiant(?:e)?|designer|graphiste|enseignant(?:e)?|professeur|ing[ée]nieur(?:e)?|"
                    + "entrepreneu(?:r|se)|freelance|artiste|musicien(?:ne)?|youtubeu(?:r|se)|photographe|vid[ée]aste|r[ée]dacteur|r[ée]dactrice|comptable|m[ée]decin|avocat(?:e)?)\\b");
    private static final Pattern WORK = Pattern.compile(
            "(?i)\\bje travaille (?:comme|chez|dans|sur|pour)\\s+(.{2,80})");
    private static final Pattern LIKE = Pattern.compile(
            "(?i)\\b(j'aime(?: bien)?|j’aime(?: bien)?|j'adore|j’adore|je pr[ée]f[èe]re|je d[ée]teste|je n'aime pas|je n’aime pas)\\s+(.{3,80})");
    private static final Pattern PROJECT = Pattern.compile(
            "(?i)\\b(?:mon projet|mon application|mon app|mon site|mon jeu)\\s+(?:s'appelle|s’appelle|est|c'est|c’est)\\s+(.{2,100})");
    private static final Pattern BUILDING = Pattern.compile(
            "(?i)\\bje (?:d[ée]veloppe|cr[ée]e|construis|fabrique|fais)\\s+(une? (?:application|app|jeu|site|bot|outil)\\b.{0,90})");
    private static final Pattern FORGET = Pattern.compile(
            "(?i)(?<![n'’])\\b(?:oublie(?!\\s+pas)|efface de ta m[ée]moire|supprime de ta m[ée]moire|ne retiens plus)\\s*(?:que|qu'|qu’|:)?\\s*(.{2,120})");
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i)(mot de passe|password|code pin|\\bpin\\b|carte bancaire|iban|cvv|num[ée]ro de carte|secret|maladie|diab[èe]te|cancer|"
                    + "d[ée]pression|suicid|religion|musulman|chr[ée]tien|juif|bouddhiste|orientation sexuelle|gay|lesbienne|"
                    + "\\d+\\s*ans\\b|\\b[âa]ge\\b|mineur|salaire|dette|casier|garde [àa] vue|arrest|\\bviol|porn|drogue|\\bsexe\\b)");

    private MemoryExtractor() { }

    /** Vrai si le texte touche un sujet qu'on ne mémorise jamais (santé, argent, secrets, âge…). */
    public static boolean isSensitive(String s) {
        return s != null && SENSITIVE.matcher(s).find();
    }

    private static String clean(String s) {
        String t = s.trim().replaceAll("[\\s]+", " ");
        t = t.replaceAll("[\\s,;:.!?…]+$", "");
        // coupe à la première ponctuation de fin de phrase
        int cut = t.indexOf(". ");
        if (cut > 8) t = t.substring(0, cut);
        return t.trim();
    }

    private static String cutAtConjunction(String s) {
        return s.split("(?i)\\s+(?:mais|donc|et que|car|parce que|alors|sinon)\\s+")[0].trim();
    }

    private static void add(List<String> out, String fact) {
        if (fact == null) return;
        String f = clean(fact);
        if (f.length() < 5 || f.length() > 200) return;
        if (SENSITIVE.matcher(f).find()) return;
        for (String o : out) if (o.equalsIgnoreCase(f)) return;
        if (out.size() < 3) out.add(f);
    }

    /** Faits durables que l'utilisateur dit sur lui dans ce message (0 à 3). Une question n'en produit aucun. */
    public static List<String> extract(String message) {
        List<String> out = new ArrayList<>();
        if (message == null) return out;
        String m = message.trim();
        if (m.isEmpty() || m.length() > 1500) return out;
        if (FORGET.matcher(m).find()) return out;
        if (SENSITIVE.matcher(m).find()) return out; // un message qui touche un sujet sensible ne produit aucun souvenir
        // une phrase interrogative ne dit rien de durable
        if (m.endsWith("?") && !EXPLICIT.matcher(m).find()) return out;

        Matcher e = EXPLICIT.matcher(m);
        if (e.find()) {
            add(out, cutAtConjunction(e.group(1)).isEmpty() ? e.group(1) : e.group(1));
            return out;
        }
        Matcher x;
        x = NAME.matcher(m); if (x.find()) add(out, "S'appelle " + cutAtConjunction(x.group(1)));
        x = PLACE.matcher(m); if (x.find()) add(out, "Habite ou vient de " + cutAtConjunction(x.group(1)));
        x = JOB.matcher(m); if (x.find()) add(out, "Est " + x.group(1).toLowerCase(Locale.ROOT));
        x = WORK.matcher(m); if (x.find()) add(out, "Travaille " + cutAtConjunction(x.group(1)));
        x = LIKE.matcher(m); if (x.find()) {
            String v = x.group(1).toLowerCase(Locale.ROOT).replace('’', '\'');
            String label = v.startsWith("je pr") ? "Préfère " : (v.contains("déteste") || v.contains("n'aime pas") || v.contains("deteste")) ? "N'aime pas " : "Aime ";
            add(out, label + cutAtConjunction(x.group(2)));
        }
        x = PROJECT.matcher(m); if (x.find()) add(out, "Son projet : " + cutAtConjunction(x.group(1)));
        x = BUILDING.matcher(m); if (x.find()) add(out, "Développe " + cutAtConjunction(x.group(1)));
        return out;
    }

    /** Ce que l'utilisateur demande d'oublier (« oublie que j'habite à Paris »), ou null. */
    public static String forgetTarget(String message) {
        if (message == null) return null;
        Matcher f = FORGET.matcher(message);
        return f.find() ? clean(f.group(1)) : null;
    }

    static String fold(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    /** Mots significatifs d'une phrase à oublier, pour retrouver les souvenirs concernés. */
    public static List<String> forgetWords(String target) {
        List<String> w = new ArrayList<>();
        for (String t : fold(target).split("[^\\p{L}\\p{N}]+")) {
            if (t.length() >= 4 && !t.matches("jhabite|habite|suis|avec|dans|pour|mais|cela|cette|mon|mes|que|qui") && !w.contains(t)) w.add(t);
        }
        return w;
    }

    /** Vrai si le souvenir contient au moins la moitié des mots à oublier (et au moins un). */
    public static boolean matchesForget(String fact, List<String> words) {
        if (words.isEmpty()) return false;
        String f = fold(fact);
        int hit = 0;
        for (String w : words) if (f.contains(w)) hit++;
        return hit >= Math.max(1, (words.size() + 1) / 2);
    }
}
