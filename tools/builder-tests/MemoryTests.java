import com.virtixstudio.kruxai.utils.MemoryExtractor;
import com.virtixstudio.kruxai.utils.MemorySelector;
import java.util.*;

public class MemoryTests {
    static int ok = 0, ko = 0;
    static void check(boolean c, String n) { if (c) ok++; else { ko++; System.out.println("ÉCHEC : " + n); } }
    static List<String> ex(String s) { return MemoryExtractor.extract(s); }

    public static void main(String[] a) {
        check(ex("Je m'appelle Jonathan et je code").equals(Arrays.asList("S'appelle Jonathan et je code")) || ex("Je m'appelle Jonathan").get(0).equals("S'appelle Jonathan"), "prénom : " + ex("Je m'appelle Jonathan"));
        check(ex("Appelle-moi Jo").get(0).equals("S'appelle Jo"), "appelle-moi : " + ex("Appelle-moi Jo"));
        check(ex("J'habite à Lubumbashi").get(0).equals("Habite ou vient de Lubumbashi"), "lieu : " + ex("J'habite à Lubumbashi"));
        check(ex("Je suis développeur Android").get(0).equals("Est développeur"), "métier : " + ex("Je suis développeur Android"));
        check(ex("Je suis fatigué").isEmpty(), "état passager ignoré");
        check(ex("J'adore le manga noir et blanc").get(0).equals("Aime le manga noir et blanc"), "goût : " + ex("J'adore le manga noir et blanc"));
        check(ex("Je préfère les réponses courtes").get(0).equals("Préfère les réponses courtes"), "préférence");
        check(ex("Je n'aime pas les longs textes").get(0).equals("N'aime pas les longs textes"), "dégoût : " + ex("Je n'aime pas les longs textes"));
        check(ex("Mon projet s'appelle KRUX AI").get(0).equals("Son projet : KRUX AI"), "projet : " + ex("Mon projet s'appelle KRUX AI"));
        check(ex("Je développe une application Android avec Firebase").get(0).startsWith("Développe une application Android"), "développe : " + ex("Je développe une application Android avec Firebase"));
        check(ex("Retiens que je préfère le mode sombre").get(0).equals("je préfère le mode sombre"), "explicite : " + ex("Retiens que je préfère le mode sombre"));
        check(ex("N'oublie pas : réunion à 15h").get(0).equals("réunion à 15h"), "explicite 2 : " + ex("N'oublie pas : réunion à 15h"));

        check(ex("Comment je m'appelle ?").isEmpty(), "question ignorée");
        check(ex("Tu sais où j'habite ?").isEmpty(), "question 2");
        check(ex("Mon mot de passe est abc123, retiens-le").isEmpty(), "mot de passe refusé");
        check(ex("Retiens que j'ai 15 ans").isEmpty(), "âge refusé");
        check(ex("J'ai le diabète, retiens que je dois manger léger").isEmpty(), "santé refusée");
        check(ex("Retiens que mon salaire est de 500").isEmpty(), "argent refusé");
        check(ex("Bonjour").isEmpty() && ex(null).isEmpty() && ex("").isEmpty(), "rien à retenir");
        check(ex("Je m'appelle Jo. J'habite à Goma. J'adore le foot. Je suis designer. Je travaille chez Virtix").size() == 3, "3 faits maximum");

        check("j'habite à Paris".equals("j'habite à Paris") && MemoryExtractor.forgetTarget("Oublie que j'habite à Paris").equals("j'habite à Paris"), "oublie : " + MemoryExtractor.forgetTarget("Oublie que j'habite à Paris"));
        check(MemoryExtractor.forgetTarget("Bonjour") == null, "pas d'oubli");
        check(ex("Oublie que j'habite à Paris").isEmpty(), "oublier n'ajoute rien");
        List<String> w = MemoryExtractor.forgetWords("j'habite à Paris");
        check(MemoryExtractor.matchesForget("Habite ou vient de Paris", w), "retrouve le souvenir : " + w);
        check(!MemoryExtractor.matchesForget("Aime le manga", w), "ne touche pas aux autres");
        check(!MemoryExtractor.matchesForget("Habite ou vient de Paris", new ArrayList<>()), "sans mots, rien");

        List<String> facts = Arrays.asList("S'appelle Jonathan", "Habite ou vient de Lubumbashi", "Aime le manga", "Développe KRUX AI", "Préfère le mode sombre");
        List<String> s1 = MemorySelector.select("Comment je m'appelle ?", facts, 2000, 20);
        check(s1.size() == 5, "tout ce qui tient est donné : " + s1.size());
        check(s1.get(0).equals("S'appelle Jonathan") || s1.get(0).contains("appelle"), "le plus lié d'abord : " + s1);
        check(MemorySelector.select("manga", facts, 2000, 20).get(0).equals("Aime le manga"), "pertinence manga");
        check(MemorySelector.select("salut", facts, 2000, 20).get(0).equals("Préfère le mode sombre"), "sinon le plus récent");
        check(MemorySelector.select("x", facts, 40, 20).size() == 1 || MemorySelector.select("x", facts, 40, 20).size() == 2, "budget respecté");
        check(MemorySelector.select("x", facts, 2000, 2).size() == 2, "max");
        check(MemorySelector.select("x", Arrays.asList("A a a", "a a a", "  ", null), 2000, 9).size() == 1, "doublons et vides");
        check(MemorySelector.select("x", null, 2000, 9).isEmpty() && MemorySelector.select("x", new ArrayList<>(), 2000, 9).isEmpty(), "vide");

        System.out.println("Mémoire : " + ok + "/" + (ok + ko) + (ko == 0 ? " OK" : " ÉCHECS"));
        if (ko > 0) System.exit(1);
    }
}
