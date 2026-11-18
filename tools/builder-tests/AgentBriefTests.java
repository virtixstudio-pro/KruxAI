import com.virtixstudio.kruxai.builder.AgentBrief;

/** Tests de la réflexion préalable (brief de conception) : lecture tolérante, limites, aucune répétition. */
public class AgentBriefTests {
    static int ok = 0, ko = 0;
    static void check(String what, boolean cond) {
        if (cond) ok++; else { ko++; System.out.println("ÉCHEC : " + what); }
    }

    public static void main(String[] x) {
        String good = "{\"concept\":\"Carnet de labo pour étudiants\",\"audience\":\"étudiants en physique\","
            + "\"screens\":[\"Accueil : mesures récentes\",\"Courbe : graphique\",\"Accueil : mesures récentes\",\"Réglages : unités\"],"
            + "\"data\":\"mesures sur le téléphone\",\"identity\":\"bleu nuit, formes arrondies, ton sérieux\","
            + "\"signature\":\"ajustement automatique de courbe\",\"empty_states\":\"illustration et bouton Ajouter\","
            + "\"assumptions\":[\"hors connexion\",\"hors connexion\",\"unités SI\"],"
            + "\"needs\":[{\"what\":\"icône\",\"why\":\"identité\",\"how\":\"fournir un PNG ou garder l'icône dessinée\"},"
            + "{\"what\":\"Icône\",\"why\":\"doublon\",\"how\":\"x\"},"
            + "{\"what\":\"base de données\",\"why\":\"partage entre appareils\",\"how\":\"créer un projet Supabase gratuit\"}]}";
        AgentBrief.Brief b = AgentBrief.parse(good);
        check("brief lu", b != null);
        check("concept", b != null && b.concept.startsWith("Carnet de labo"));
        check("écrans sans doublon (3 attendus)", b != null && b.screens.size() == 3);
        check("hypothèses sans doublon (2 attendues)", b != null && b.assumptions.size() == 2);
        check("note : doublon de besoin retiré (2 puces attendues)", b != null && b.userNote().split("•").length - 1 == 2);
        String g = b == null ? "" : b.guidance();
        check("consigne contient les écrans", g.contains("écrans : Accueil : mesures récentes | Courbe : graphique | Réglages : unités"));
        check("consigne contient l'identité", g.contains("identité visuelle : bleu nuit"));
        check("consigne bornée", g.length() <= 1800);
        String note = b == null ? "" : b.userNote();
        check("note : commence par Pour aller plus loin", note.startsWith("Pour aller plus loin :"));
        check("note : Supabase cité", note.contains("Supabase"));
        check("note : hypothèses", note.contains("Hypothèses retenues : hors connexion ; unités SI"));

        AgentBrief.Brief f = AgentBrief.parse("Voici :\n```json\n" + good + "\n```\nBonne création !");
        check("JSON entouré de texte et de balises", f != null && f.screens.size() == 3);

        String cut = "{\"concept\":\"App météo\",\"screens\":[\"Accueil : météo du jour\",\"Prévisions : 7 jours\"";
        AgentBrief.Brief c = AgentBrief.parse(cut);
        check("réponse coupée : ce qui est lisible est gardé", c != null && c.screens.size() == 2 && c.concept.equals("App météo"));

        check("texte sans JSON : pas de brief", AgentBrief.parse("Je ne sais pas quoi répondre") == null);
        check("JSON vide : pas de brief", AgentBrief.parse("{}") == null);
        check("null : pas de brief", AgentBrief.parse(null) == null);

        AgentBrief.Brief e = AgentBrief.parse("{\"concept\":\"Ligne 1\\nligne 2 \\\"citée\\\" \\u00e9\"}");
        check("échappements lus", e != null && e.concept.equals("Ligne 1 ligne 2 \"citée\" é"));

        AgentBrief.Brief lim = AgentBrief.parse("{\"concept\":\"" + "a".repeat(600) + "\",\"screens\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\"],"
            + "\"needs\":[{\"what\":\"a\",\"how\":\"1\"},{\"what\":\"b\",\"how\":\"2\"},{\"what\":\"c\",\"how\":\"3\"},{\"what\":\"d\",\"how\":\"4\"}]}");
        check("concept borné", lim != null && lim.concept.length() <= 240);
        check("5 écrans au plus", lim != null && lim.screens.size() == 5);
        check("3 besoins au plus", lim != null && lim.needs.size() == 3);

        AgentBrief.Brief none = AgentBrief.parse("{\"concept\":\"Simple\",\"needs\":[]}");
        check("aucun besoin : note vide", none != null && none.userNote().isEmpty());

        String before = AgentBrief.systemPrompt();
        AgentBrief.setPlaybook("trop court");
        check("playbook trop court ignoré", AgentBrief.systemPrompt().equals(before));
        AgentBrief.setPlaybook("# AGENT.md\nRègle de test : poser des questions à soi-même avant de coder, une seule fois.");
        check("playbook valide pris en compte", AgentBrief.systemPrompt().contains("Règle de test"));
        check("schéma toujours présent", AgentBrief.systemPrompt().contains("\"needs\""));
        check("demande reprise dans le prompt utilisateur", AgentBrief.userPrompt("une app de notes").contains("une app de notes"));

        System.out.println("AgentBrief : " + ok + "/" + (ok + ko) + (ko == 0 ? " OK" : " ÉCHECS"));
        if (ko > 0) System.exit(1);
    }
}
