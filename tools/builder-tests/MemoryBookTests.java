import com.virtixstudio.kruxai.utils.MemoryBook;
import com.virtixstudio.kruxai.utils.MemoryBook.Entry;
import java.util.*;

public class MemoryBookTests {
    static int ok = 0, ko = 0;
    static void check(boolean c, String n) { if (c) ok++; else { ko++; System.out.println("ÉCHEC : " + n); } }
    static Entry e(String cat, String slot, String t, long ts) { return new Entry(cat, slot, t, ts); }

    public static void main(String[] a) {
        // classement automatique
        check(MemoryBook.classify("S'appelle Jo", 1).slot.equals("prenom"), "prénom");
        check(MemoryBook.classify("Habite ou vient de Lyon", 1).slot.equals("ville"), "ville");
        check(MemoryBook.classify("Aime le manga", 1).cat.equals("preferences"), "goût");
        check(MemoryBook.classify("Son projet : KRUX AI", 1).cat.equals("projets"), "projet");
        check(MemoryBook.classify("Son frère s'appelle Paul", 1).cat.equals("personnes"), "personne");

        // remplacement par clé : la ville change
        List<Entry> l = new ArrayList<>();
        check(MemoryBook.merge(l, MemoryBook.classify("Habite ou vient de Paris", 100)), "ajout ville");
        check(MemoryBook.merge(l, MemoryBook.classify("Habite ou vient de Lyon", 200)), "ville remplacée");
        check(l.size() == 1 && l.get(0).text.endsWith("Lyon"), "une seule ville : " + l);
        check(!MemoryBook.merge(l, MemoryBook.classify("Habite ou vient de Paris", 50)), "info plus ancienne ignorée");
        check(l.get(0).text.endsWith("Lyon"), "Lyon gardé");
        check(!MemoryBook.merge(l, MemoryBook.classify("Habite ou vient de Lyon", 200)), "même fait même date = rien");

        // goûts : inversion remplace
        MemoryBook.merge(l, MemoryBook.classify("Aime les longs textes", 300));
        MemoryBook.merge(l, MemoryBook.classify("N'aime pas les longs textes", 400));
        int prefs = 0; for (Entry x : l) if (x.cat.equals("preferences")) prefs++;
        check(prefs == 1 && l.get(l.size() - 1).text.startsWith("N'aime pas"), "goût inversé : " + l);
        // goûts différents cohabitent
        MemoryBook.merge(l, MemoryBook.classify("Aime le manga noir et blanc", 500));
        prefs = 0; for (Entry x : l) if (x.cat.equals("preferences")) prefs++;
        check(prefs == 2, "deux goûts : " + l);

        // quasi-doublon
        List<Entry> q = new ArrayList<>();
        MemoryBook.merge(q, e("projets", "", "Développe une application Android avec Firebase", 10));
        MemoryBook.merge(q, e("projets", "", "Développe une application Android avec Firebase et Java", 20));
        check(q.size() == 1 && q.get(0).ts == 20, "quasi-doublon fusionné : " + q);

        // identité stable d'un souvenir
        Entry id1 = e("profil", "ville", "Habite à Lyon", 10);
        check(id1.id != null && id1.id.equals(MemoryBook.stableId("profil", "ville", "Habite à Lyon")), "id déterministe");
        check(!id1.id.equals(e("profil", "ville", "Habite à Paris", 10).id), "id différent si texte différent");
        check(id1.id.equals(MemoryBook.stableId("PROFIL", "Ville", "habite a lyon")), "id insensible casse/accents");
        List<Entry> ids = new ArrayList<>();
        Entry first = e("profil", "ville", "Habite à Lyon", 10);
        MemoryBook.merge(ids, first);
        MemoryBook.merge(ids, e("profil", "ville", "Habite à Paris", 20));
        check(ids.size() == 1 && ids.get(0).id.equals(first.id), "id conservé quand la valeur change");
        check(new Entry(first).id.equals(first.id), "copie garde l'id");

        // encodage / décodage
        Entry x = e("profil", "ville", "Habite à Lyon | centre", 1234);
        Entry y = MemoryBook.decode(MemoryBook.encode(x), 9);
        check(y.cat.equals("profil") && y.slot.equals("ville") && y.ts == 1234 && y.text.equals("Habite à Lyon | centre"), "aller-retour");
        Entry z = MemoryBook.decode("Aime le café", 77);
        check(z.cat.equals("preferences") && z.ts == 77, "texte simple classé");
        check(MemoryBook.decode("§n'importe|quoi", 5).text.startsWith("§"), "encodage cassé = texte simple");
        check(MemoryBook.decode("   ", 1) == null && MemoryBook.decode(null, 1) == null, "vide");

        // affichage : profil toujours, datation
        List<Entry> big = new ArrayList<>();
        big.add(e("profil", "prenom", "S'appelle Jo", 86400));
        for (int i = 0; i < 60; i++) big.add(e("divers", "", "Fait numéro " + i + " sur un sujet très différent " + (i * 7919), 100 + i));
        big.add(e("preferences", "", "Aime le manga noir et blanc", 90000));
        String out = MemoryBook.formatForPrompt(big, "Comment je m'appelle ?", 600, 10);
        check(out.contains("Profil :") && out.contains("S'appelle Jo (02/01/1970)"), "profil + date : " + out);
        String out2 = MemoryBook.formatForPrompt(big, "recommande un manga", 600, 10);
        check(out2.contains("Aime le manga noir et blanc") && out2.indexOf("Profil") < out2.indexOf("Goûts"), "pertinent + ordre : " + out2);
        check(out2.length() < 900, "budget respecté : " + out2.length());
        check(MemoryBook.formatForPrompt(new ArrayList<Entry>(), "x", 600, 10).isEmpty(), "vide");

        // plafond
        List<Entry> cap = new ArrayList<>();
        cap.add(e("profil", "prenom", "S'appelle Jo", 1));
        for (int i = 0; i < 200; i++) MemoryBook.merge(cap, e("divers", "", "Souvenir unique numéro " + (char) ('a' + i % 26) + (char) ('a' + i / 26) + " zzz" + i * 31, 10 + i));
        check(cap.size() <= MemoryBook.MAX_ENTRIES && cap.get(0).cat.equals("profil"), "plafond " + cap.size());

        // extraction par le modèle
        check(MemoryBook.worthExtracting("Je viens de déménager à Lyon") && !MemoryBook.worthExtracting("Fais-moi une app de météo") && !MemoryBook.worthExtracting("ok"), "worthExtracting");
        String model = "```\n- profil|ville|Habite à Lyon\nSUPPR|ecole|\npreferences||Aime le thé\nprofil|metier|Prend 10 000 de salaire\nRIEN\nbla bla\n```";
        List<MemoryBook.Op> ops = MemoryBook.parseOps(model, 500);
        check(ops.size() == 3, "3 décisions (salaire refusé) : " + ops.size());
        check(!ops.get(0).remove && ops.get(0).entry.slot.equals("ville") && ops.get(0).entry.cat.equals("profil"), "op1");
        check(ops.get(1).remove && ops.get(1).entry.slot.equals("ecole"), "op2");
        check(MemoryBook.parseOps("RIEN", 1).isEmpty() && MemoryBook.parseOps(null, 1).isEmpty(), "rien");

        List<Entry> db = new ArrayList<>();
        MemoryBook.merge(db, e("profil", "ville", "Habite à Paris", 100));
        MemoryBook.merge(db, e("profil", "ecole", "Étudie au lycée X", 100));
        List<String> log = MemoryBook.apply(db, ops);
        check(db.size() == 2 && log.size() == 3, "application : " + db + " / " + log);
        boolean lyon = false, ecole = false;
        for (Entry d : db) { if (d.text.equals("Habite à Lyon")) lyon = true; if (d.slot.equals("ecole")) ecole = true; }
        check(lyon && !ecole, "ville remplacée, école retirée");
        String up = MemoryBook.extractionUserPrompt(db, "Je suis à Lyon", "Super !");
        check(up.contains("[profil/ville] Habite à Lyon") && up.contains("Je suis à Lyon"), "prompt d'extraction");
        check(MemoryBook.extractionSystemPrompt().contains("RIEN"), "consigne");

        // résumé à la 2e personne
        List<Entry> sm = new ArrayList<>();
        MemoryBook.merge(sm, MemoryBook.classify("S'appelle Jo", 1));
        MemoryBook.merge(sm, MemoryBook.classify("Habite ou vient de Lyon", 2));
        MemoryBook.merge(sm, MemoryBook.classify("Est développeur", 3));
        MemoryBook.merge(sm, MemoryBook.classify("Aime le manga", 4));
        MemoryBook.merge(sm, MemoryBook.classify("N'aime pas les longs textes", 5));
        MemoryBook.merge(sm, MemoryBook.classify("Son projet : KRUX AI", 6));
        String su = MemoryBook.summary(sm, 700);
        check(su.contains("Tu es développeur") && su.contains("tu t'appelles Jo") && su.contains("tu habites ou viens de Lyon"), "résumé profil : " + su);
        check(su.contains("tu aimes le manga") && su.contains("n'aimes pas les longs textes") && su.contains("Ton projet : KRUX AI"), "résumé goûts/projet : " + su);
        check(MemoryBook.summary(new ArrayList<Entry>(), 700).isEmpty(), "résumé vide");
        check(MemoryBook.summary(sm, 30).length() <= 31, "résumé borné");

        // lots d'apprentissage
        List<String> msgs = new ArrayList<>();
        msgs.add("ok"); msgs.add("Fais-moi une app de météo");
        for (int i = 0; i < 20; i++) msgs.add("Je travaille sur mon projet numéro " + i + " avec beaucoup de détails inutiles pour remplir le lot");
        List<String> bt = MemoryBook.learnBatches(msgs, 500, 2);
        check(bt.size() == 2 && !bt.get(0).contains("météo") && bt.get(0).startsWith("- Je travaille"), "lots : " + bt.size());
        check(MemoryBook.learnBatches(new ArrayList<String>(), 500, 2).isEmpty(), "lots vides");

        System.out.println(ok + " OK, " + ko + " échec(s)");
        if (ko > 0) System.exit(1);
    }
}
