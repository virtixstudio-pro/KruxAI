package com.virtixstudio.kruxai.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Boutons de lien écrits par l'IA : [btn:Texte du bouton|https://adresse]
 * Seules les adresses http(s) sont acceptées. Le libellé est limité à 40 caractères.
 */
public final class LinkButtons {

    private LinkButtons() {}

    private static final String TOKEN = "\\[btn:([^|\\]\\n]{1,40})\\|(https?://[^\\]\\s]+)\\]";
    private static final Pattern TOKEN_RE = Pattern.compile(TOKEN);
    private static final Pattern LINE_RE = Pattern.compile("^\\s*(?:" + TOKEN + "\\s*)+$");

    /** Vrai si la ligne ne contient que des boutons complets. */
    public static boolean isButtonLine(String line) {
        return line != null && LINE_RE.matcher(line).matches();
    }

    /** Vrai pour un bouton encore en cours d'écriture (streaming) : on le masque en attendant. */
    public static boolean isPartial(String line) {
        return line != null && line.trim().startsWith("[btn:") && !isButtonLine(line);
    }

    /** Liste de {libellé, url} trouvée dans la ligne. */
    public static List<String[]> parse(String line) {
        List<String[]> out = new ArrayList<>();
        if (line == null) return out;
        Matcher m = TOKEN_RE.matcher(line);
        while (m.find()) out.add(new String[]{m.group(1).trim(), m.group(2).trim()});
        return out;
    }

    /** Version texte brut pour copier/partager : « Libellé : url ». */
    public static String toPlain(String text) {
        if (text == null) return "";
        return TOKEN_RE.matcher(text).replaceAll("$1 : $2");
    }

    /** Version pour la lecture vocale : les boutons ne sont pas lus. */
    public static String stripForSpeech(String text) {
        if (text == null) return "";
        return text.replaceAll(TOKEN, " ").replaceAll("\\[btn:[^\\]]*$", " ");
    }
}
