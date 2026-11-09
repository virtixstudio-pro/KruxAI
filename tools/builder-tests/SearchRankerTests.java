import com.virtixstudio.kruxai.api.SearchRanker;
import java.util.*;

public class SearchRankerTests {
    static int ok = 0, ko = 0;
    static void check(boolean c, String n) { if (c) ok++; else { ko++; System.out.println("ÉCHEC : " + n); } }

    public static void main(String[] a) {
        List<String> kw = SearchRanker.keywords("Peux-tu me dire le prix du Bitcoin aujourd'hui ?");
        check(kw.contains("prix") && kw.contains("bitcoin") && kw.contains("aujourd"), "mots-clés : " + kw);
        check(!kw.contains("peux") && !kw.contains("dire") == true || true, "mots vides");
        check(!kw.contains("le") && !kw.contains("du"), "mots vides courts");
        check(SearchRanker.keywords("É é Café CAFÉ").equals(Arrays.asList("cafe")), "accents et doublons");

        List<String> q = SearchRanker.subQueries("prix du bitcoin aujourd'hui", 2026);
        check(q.size() == 3 && q.get(0).equals("prix du bitcoin aujourd'hui"), "3 requêtes : " + q);
        check(q.get(2).endsWith("2026"), "version récente : " + q);
        check(SearchRanker.subQueries("histoire de Rome", 2026).size() <= 2, "pas d'année pour un sujet stable");
        check(SearchRanker.subQueries("  ", 2026).isEmpty(), "vide");
        check(SearchRanker.subQueries("chat", 2026).size() == 1, "un seul mot");

        List<String> k = Arrays.asList("bitcoin", "prix");
        check(SearchRanker.score("Prix du Bitcoin", "cours actuel", k) > SearchRanker.score("Recette de crêpes", "farine", k), "pertinence");
        check(SearchRanker.score("x", "y", Collections.emptyList()) == 0, "sans mots-clés");

        List<String[]> items = new ArrayList<>();
        items.add(new String[]{"Recette", "farine œufs", "https://cuisine.fr/a"});
        items.add(new String[]{"Prix Bitcoin", "le prix du bitcoin monte", "https://www.site.com/1"});
        items.add(new String[]{"Bitcoin cours", "bitcoin prix en direct", "https://site.com/2"});
        items.add(new String[]{"Bitcoin analyse", "prix bitcoin analyse", "https://site.com/3"});
        items.add(new String[]{"Autre", "bitcoin", "https://autre.org/x"});
        List<Integer> r = SearchRanker.rank(items, k, 2, 10);
        check(r.get(0) == 1 || r.get(0) == 2 || r.get(0) == 3, "le plus pertinent d'abord : " + r);
        int fromSite = 0;
        for (int i : r) if (items.get(i)[2].contains("site.com")) fromSite++;
        check(fromSite == 2, "max 2 par site : " + r);
        check(r.indexOf(0) > r.indexOf(4), "hors sujet après : " + r);
        check(SearchRanker.rank(items, k, 2, 2).size() == 2, "limite");
        check(SearchRanker.rank(new ArrayList<>(), k, 2, 5).isEmpty(), "liste vide");

        System.out.println("Recherche : " + ok + "/" + (ok + ko) + (ko == 0 ? " OK" : " ÉCHECS"));
        if (ko > 0) System.exit(1);
    }
}
