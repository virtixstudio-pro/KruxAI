package com.virtixstudio.kruxai.utils;

import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mémoire v2 : des souvenirs rangés par catégorie, datés, et qui se METTENT À JOUR au lieu de s'empiler.
 * Un souvenir a une « clé » (prenom, ville, metier…) : un nouveau souvenir avec la même clé remplace l'ancien.
 * Sans clé, on remplace quand c'est le même fait, un quasi-doublon, ou l'inverse d'un goût (« aime X » ↔ « n'aime pas X »).
 * Java pur, testé. Les souvenirs voyagent en texte : « §catégorie|clé|date|texte » (un texte simple reste lisible).
 */
public final class MemoryBook {

    public static final String PROFIL = "profil", PREF = "preferences", PROJETS = "projets", PERSONNES = "personnes", DIVERS = "divers";
    public static final String[] CATEGORIES = { PROFIL, PREF, PROJETS, PERSONNES, DIVERS };
    public static final int MAX_ENTRIES = 150;

    public static final class Entry {
        public String cat, slot, text;
        public long ts; // secondes
        public String id; // identité stable : suit le souvenir quand son texte change
        public Entry(String cat, String slot, String text, long ts) {
            this.cat = cat; this.slot = slot == null ? "" : slot; this.text = text; this.ts = ts;
            this.id = stableId(this.cat, this.slot, this.text);
        }
        /** Copie détachée (l'identité est conservée). */
        public Entry(Entry other) {
            this.cat = other.cat; this.slot = other.slot; this.text = other.text; this.ts = other.ts; this.id = other.id;
        }
        @Override public String toString() { return cat + "/" + slot + ": " + text; }
    }

    /** Une décision du modèle : retenir (remove=false) ou retirer (remove=true). */
    public static final class Op {
        public final boolean remove;
        public final Entry entry;
        Op(boolean remove, Entry e) { this.remove = remove; this.entry = e; }
    }

    private MemoryBook() { }

    // ---------- utilitaires ----------

    static String fold(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    /**
     * Identité stable d'un souvenir : dépend de la catégorie, de la clé et du texte normalisé.
     * Sert à mettre à jour une ligne au lieu de tout réécrire. Ne change pas quand seul l'accent
     * ou la casse change.
     */
    public static String stableId(String cat, String slot, String text) {
        String raw = fold(cat) + "|" + fold(slot == null ? "" : slot) + "|" + fold(text);
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(raw.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) sb.append(String.format(Locale.ROOT, "%02x", d[i]));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    private static List<String> tokens(String s) {
        List<String> t = new ArrayList<>();
        for (String w : fold(s).split("[^\\p{L}\\p{N}]+")) if (w.length() >= 3 && !t.contains(w)) t.add(w);
        return t;
    }

    static double similarity(String a, String b) {
        List<String> x = tokens(a), y = tokens(b);
        if (x.isEmpty() || y.isEmpty()) return 0;
        int inter = 0;
        for (String w : x) if (y.contains(w)) inter++;
        return inter / (double) (x.size() + y.size() - inter);
    }

    private static final Pattern PREF_PREFIX = Pattern.compile("(?i)^(aime|pr[ée]f[èe]re|n'aime pas|n’aime pas|adore|d[ée]teste)\\s+(.+)$");

    /** +1 (aime), -1 (n'aime pas), 0 (pas un goût). */
    private static int polarity(String text) {
        Matcher m = PREF_PREFIX.matcher(text.trim());
        if (!m.find()) return 0;
        String p = fold(m.group(1));
        return (p.startsWith("n'aime") || p.startsWith("n’aime") || p.startsWith("deteste")) ? -1 : 1;
    }

    private static String prefObject(String text) {
        Matcher m = PREF_PREFIX.matcher(text.trim());
        return m.find() ? fold(m.group(2)) : "";
    }

    private static final Pattern PERSON = Pattern.compile("(?i)\\b(fr[èe]re|s[œo]eur|m[èe]re|p[èe]re|maman|papa|ami|amie|copain|copine|cousin|cousine|femme|mari|fils|fille|prof|coll[èe]gue)\\b");

    /** Range un souvenir simple (sans catégorie connue) : catégorie + clé. */
    public static Entry classify(String text, long ts) {
        String t = text == null ? "" : text.trim();
        String f = fold(t);
        if (f.startsWith("s'appelle ") || f.startsWith("s’appelle ")) return new Entry(PROFIL, "prenom", t, ts);
        if (f.startsWith("habite") || f.startsWith("vit a") || f.startsWith("vit en")) return new Entry(PROFIL, "ville", t, ts);
        if (f.startsWith("est ") && f.length() < 40) return new Entry(PROFIL, "metier", t, ts);
        if (f.startsWith("travaille")) return new Entry(PROFIL, "metier", t, ts);
        if (PERSON.matcher(t).find()) return new Entry(PERSONNES, "", t, ts);
        if (polarity(t) != 0) return new Entry(PREF, "", t, ts);
        if (f.startsWith("son projet") || f.startsWith("developpe") || f.startsWith("cree ") || f.contains("application") || f.contains("projet")) return new Entry(PROJETS, "", t, ts);
        return new Entry(DIVERS, "", t, ts);
    }

    // ---------- fusion ----------

    private static int indexOfSlot(List<Entry> l, String slot) {
        for (int i = 0; i < l.size(); i++) if (slot.equals(l.get(i).slot)) return i;
        return -1;
    }

    /** Ajoute ou met à jour. Retourne vrai si la mémoire a changé. */
    public static boolean merge(List<Entry> list, Entry e) {
        if (e == null || e.text == null || e.text.trim().isEmpty()) return false;
        e.text = e.text.trim();
        if (e.cat == null || e.cat.isEmpty()) e.cat = DIVERS;
        if (e.slot == null) e.slot = "";
        boolean changed;
        if (!e.slot.isEmpty()) {
            int i = indexOfSlot(list, e.slot);
            if (i >= 0) {
                Entry old = list.get(i);
                if (old.ts > e.ts) return false;                      // information plus ancienne que celle qu'on a
                if (fold(old.text).equals(fold(e.text))) {
                    if (old.ts == e.ts) return false;
                    old.ts = e.ts; return true;                       // même fait revu : on rafraîchit la date
                }
                e.id = old.id;                                         // l'identité suit la clé
                list.remove(i);
            }
            list.add(e);
            changed = true;
        } else {
            int hit = -1;
            String fe = fold(e.text);
            int pe = polarity(e.text);
            String oe = prefObject(e.text);
            for (int i = 0; i < list.size() && hit < 0; i++) {
                Entry o = list.get(i);
                if (!o.slot.isEmpty()) continue;
                if (fold(o.text).equals(fe)) hit = i;
                else if (pe != 0 && polarity(o.text) != 0 && !oe.isEmpty() && similarity(prefObject(o.text), oe) >= 0.6) hit = i; // même objet, goût (re)dit ou inversé
                else if (similarity(o.text, e.text) >= 0.75) hit = i;
            }
            if (hit >= 0) {
                Entry old = list.get(hit);
                if (old.ts > e.ts) return false;
                if (old.text.equals(e.text) && old.ts == e.ts) return false;
                e.id = old.id;                                        // même fait reformulé : on garde l'identité
                list.remove(hit);
            }
            list.add(e);
            changed = true;
        }
        trim(list);
        return changed;
    }

    /** Garde au plus MAX_ENTRIES souvenirs : on retire d'abord les plus anciens hors profil. */
    static void trim(List<Entry> list) {
        while (list.size() > MAX_ENTRIES) {
            int victim = -1;
            for (int i = 0; i < list.size(); i++) {
                if (PROFIL.equals(list.get(i).cat)) continue;
                if (victim < 0 || list.get(i).ts < list.get(victim).ts) victim = i;
            }
            if (victim < 0) victim = 0;
            list.remove(victim);
        }
    }

    // ---------- transport (synchro) ----------

    public static String encode(Entry e) {
        return "§" + e.cat + "|" + e.slot + "|" + e.ts + "|" + e.text;
    }

    /** Lit un souvenir encodé, ou un texte simple (ancienne mémoire) qu'on range tout seul. */
    public static Entry decode(String s, long nowTs) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        if (t.startsWith("§")) {
            String[] p = t.substring(1).split("\\|", 4);
            if (p.length == 4 && p[3].trim().length() > 0) {
                long ts;
                try { ts = Long.parseLong(p[2].trim()); } catch (NumberFormatException ex) { ts = nowTs; }
                String cat = p[0].trim();
                boolean ok = false;
                for (String c : CATEGORIES) if (c.equals(cat)) ok = true;
                String slot = p[1].trim();
                if (!slot.matches("[a-z_]{0,20}")) slot = "";
                return new Entry(ok ? cat : DIVERS, slot, p[3].trim(), ts);
            }
        }
        return classify(t, nowTs);
    }

    // ---------- affichage pour KRUX ----------

    public static String date(long ts) {
        SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(ts * 1000L));
    }

    public static String label(String cat) {
        switch (cat) {
            case PROFIL: return "Profil";
            case PREF: return "Goûts et préférences";
            case PROJETS: return "Projets";
            case PERSONNES: return "Personnes";
            default: return "Autres";
        }
    }

    /**
     * Texte à donner à KRUX : le profil toujours, puis les souvenirs les plus liés à la question, rangés par catégorie,
     * chacun avec sa date. Vide s'il n'y a rien.
     */
    public static String formatForPrompt(List<Entry> entries, String prompt, int budgetChars, int maxFacts) {
        if (entries == null || entries.isEmpty()) return "";
        List<Entry> chosen = new ArrayList<>();
        int used = 0;
        for (Entry e : entries) {
            if (PROFIL.equals(e.cat) && used + e.text.length() < budgetChars / 2) { chosen.add(e); used += e.text.length() + 16; }
        }
        List<String> others = new ArrayList<>();
        List<Entry> otherEntries = new ArrayList<>();
        for (Entry e : entries) if (!chosen.contains(e)) { others.add(e.text); otherEntries.add(e); }
        List<String> picked = MemorySelector.select(prompt, others, Math.max(200, budgetChars - used), Math.max(1, maxFacts - chosen.size()));
        for (String p : picked) {
            for (Entry e : otherEntries) if (e.text.equals(p) && !chosen.contains(e)) { chosen.add(e); break; }
        }
        Map<String, List<Entry>> by = new LinkedHashMap<>();
        for (String c : CATEGORIES) by.put(c, new ArrayList<Entry>());
        for (Entry e : chosen) by.get(e.cat).add(e);
        StringBuilder sb = new StringBuilder();
        for (String c : CATEGORIES) {
            List<Entry> l = by.get(c);
            if (l.isEmpty()) continue;
            sb.append(label(c)).append(" :\n");
            for (Entry e : l) sb.append("- ").append(e.text).append(" (").append(date(e.ts)).append(")\n");
        }
        return sb.toString();
    }

    // ---------- extraction par le modèle ----------

    private static final Pattern FIRST_PERSON = Pattern.compile("(?i)(\\bje\\b|\\bj'|\\bj’|\\bmon\\b|\\bma\\b|\\bmes\\b|\\bm'|\\bm’|souviens|retiens|oublie|d[ée]sormais|maintenant)");

    /** Vrai si le message peut contenir quelque chose à retenir (évite d'appeler le modèle pour rien). */
    public static boolean worthExtracting(String userMsg) {
        if (userMsg == null) return false;
        String m = userMsg.trim();
        if (m.length() < 12 || m.length() > 1500) return false;
        return FIRST_PERSON.matcher(m).find();
    }

    public static String extractionSystemPrompt() {
        return "Tu es le gestionnaire de mémoire de Krux. On te donne les souvenirs actuels sur l'utilisateur, son dernier message et la réponse de KRUX.\n"
                + "Ta seule tâche : dire ce qu'il faut RETENIR, METTRE À JOUR ou RETIRER. Réponds uniquement par des lignes, sans autre texte :\n"
                + "categorie|cle|souvenir   → retenir ou mettre à jour\n"
                + "SUPPR|cle|               → retirer un souvenir devenu faux (ou SUPPR||texte exact)\n"
                + "RIEN                     → rien à retenir (cas le plus fréquent)\n"
                + "Catégories : profil, preferences, projets, personnes, divers.\n"
                + "La clé est un mot court en minuscules pour une info qui n'existe qu'en un exemplaire (prenom, ville, metier, langue, appareil, ecole, projet_principal). "
                + "Si l'info change (il déménage, change de métier), réutilise la MÊME clé : l'ancien souvenir sera remplacé. Laisse la clé vide pour les autres souvenirs.\n"
                + "Règles : uniquement des faits durables que l'UTILISATEUR dit sur lui, ses projets, ses goûts ou ses proches. Une phrase courte à la 3e personne (« Habite à Lyon »). "
                + "Jamais de santé, argent, âge, religion, politique, mots de passe, secrets. Jamais ce que KRUX a dit. Pas de demandes ponctuelles (« fais-moi une app » n'est pas un souvenir, sauf si c'est son projet de fond). "
                + "5 lignes au maximum.";
    }

    public static String extractionUserPrompt(List<Entry> current, String userMsg, String aiReply) {
        StringBuilder sb = new StringBuilder("SOUVENIRS ACTUELS :\n");
        if (current == null || current.isEmpty()) sb.append("(aucun)\n");
        else {
            int n = 0;
            for (int i = current.size() - 1; i >= 0 && n < 40; i--, n++) {
                Entry e = current.get(i);
                sb.append("- [").append(e.cat).append(e.slot.isEmpty() ? "" : "/" + e.slot).append("] ").append(e.text).append("\n");
            }
        }
        sb.append("\nMESSAGE DE L'UTILISATEUR :\n").append(cut(userMsg, 800));
        sb.append("\n\nRÉPONSE DE KRUX (pour le contexte) :\n").append(cut(aiReply, 400));
        return sb.toString();
    }

    private static String cut(String s, int n) {
        if (s == null) return "";
        s = s.trim();
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }

    /** Lit la réponse du modèle (lignes « categorie|cle|texte »). Tolère puces, balises de code, bavardage. */
    public static List<Op> parseOps(String out, long ts) {
        List<Op> ops = new ArrayList<>();
        if (out == null) return ops;
        for (String raw : out.split("\\r?\\n")) {
            if (ops.size() >= 5) break;
            String line = raw.trim().replaceAll("^[-*•\\d.)\\s]+", "").replace("`", "").trim();
            if (line.isEmpty() || line.equalsIgnoreCase("RIEN")) continue;
            String[] p = line.split("\\|", 3);
            if (p.length < 3) continue;
            String a = fold(p[0]).trim(), slot = p[1].trim().toLowerCase(Locale.ROOT), text = p[2].trim();
            if (!slot.matches("[a-z_]{0,20}")) slot = "";
            if (a.equals("suppr") || a.equals("supprimer") || a.equals("del")) {
                if (!slot.isEmpty() || !text.isEmpty()) ops.add(new Op(true, new Entry(DIVERS, slot, text, ts)));
                continue;
            }
            String cat = DIVERS;
            for (String c : CATEGORIES) if (c.equals(a)) cat = c;
            else if (a.startsWith(c.substring(0, 4))) cat = c;
            text = text.replaceAll("[\\s]+", " ").replaceAll("[\\s,;.!]+$", "");
            if (text.length() < 4 || text.length() > 200) continue;
            if (MemoryExtractor.isSensitive(text)) continue;
            ops.add(new Op(false, new Entry(cat, slot, text, ts)));
        }
        return ops;
    }

    /** Applique les décisions. Retourne ce qu'on peut afficher (« mémorisé : … », « oublié : … »). */
    public static List<String> apply(List<Entry> list, List<Op> ops) {
        List<String> log = new ArrayList<>();
        for (Op op : ops) {
            if (op.remove) {
                for (int i = list.size() - 1; i >= 0; i--) {
                    Entry o = list.get(i);
                    boolean bySlot = !op.entry.slot.isEmpty() && op.entry.slot.equals(o.slot);
                    boolean byText = op.entry.slot.isEmpty() && !op.entry.text.isEmpty() && fold(o.text).equals(fold(op.entry.text));
                    if (bySlot || byText) { log.add("oublié : " + o.text); list.remove(i); }
                }
            } else if (merge(list, op.entry)) {
                log.add("mémorisé : " + op.entry.text);
            }
        }
        return log;
    }

    // ---------- résumé lisible (« ce que je sais de toi ») ----------

    private static String you(String t) {
        String f = fold(t);
        if (f.startsWith("s'appelle ") || f.startsWith("s’appelle ")) return "tu t'appelles " + t.substring(t.indexOf(' ') + 1);
        if (f.startsWith("habite ou vient de ")) return "tu habites ou viens de " + t.substring("Habite ou vient de ".length());
        if (f.startsWith("habite ")) return "tu habites " + t.substring(7);
        if (f.startsWith("est ")) return "tu es " + t.substring(4);
        if (f.startsWith("travaille ")) return "tu travailles " + t.substring(10);
        if (f.startsWith("n'aime pas ") || f.startsWith("n’aime pas ")) return "tu n'aimes pas " + t.substring(11);
        if (f.startsWith("aime ")) return "tu aimes " + t.substring(5);
        if (f.startsWith("adore ")) return "tu adores " + t.substring(6);
        if (f.startsWith("prefere ")) return "tu préfères " + t.substring(8);
        if (f.startsWith("developpe ")) return "tu développes " + t.substring(10);
        if (f.startsWith("son projet : ")) return "ton projet : " + t.substring(13);
        return t;
    }

    private static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    /** Un court texte qui dit ce que KRUX sait de toi, à la 2e personne. Vide s'il n'y a rien. */
    public static String summary(List<Entry> entries, int maxChars) {
        if (entries == null || entries.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        String[] order = { PROFIL, PREF, PROJETS, PERSONNES, DIVERS };
        for (String c : order) {
            List<String> parts = new ArrayList<>();
            for (int i = entries.size() - 1; i >= 0; i--) if (c.equals(entries.get(i).cat)) parts.add(you(entries.get(i).text));
            if (parts.isEmpty()) continue;
            StringBuilder sentence = new StringBuilder();
            for (int k = 0; k < parts.size(); k++) {
                if (k > 0) sentence.append(k == parts.size() - 1 ? " et " : ", ");
                sentence.append(parts.get(k));
            }
            String piece = cap(sentence.toString()) + ". ";
            if (sb.length() + piece.length() > maxChars) {
                if (sb.length() == 0) sb.append(piece, 0, Math.max(0, maxChars - 1)).append("…");
                break;
            }
            sb.append(piece);
        }
        return sb.toString().trim();
    }

    /** Messages de l'utilisateur à relire pour apprendre : seulement ceux qui parlent de lui, en lots d'environ maxChars. */
    public static List<String> learnBatches(List<String> userMessages, int maxChars, int maxBatches) {
        List<String> batches = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String m : userMessages) {
            if (!worthExtracting(m)) continue;
            String line = "- " + cut(m.replaceAll("\\s+", " "), 300) + "\n";
            if (cur.length() + line.length() > maxChars && cur.length() > 0) {
                batches.add(cur.toString());
                cur = new StringBuilder();
                if (batches.size() >= maxBatches) return batches;
            }
            cur.append(line);
        }
        if (cur.length() > 0 && batches.size() < maxBatches) batches.add(cur.toString());
        return batches;
    }
}
