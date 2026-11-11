import com.virtixstudio.kruxai.builder.TruncatedJson;
import java.util.*;

public class TruncatedJsonTests {
    static int ok = 0, ko = 0;
    static void check(boolean c, String n) { if (c) ok++; else { ko++; System.out.println("ÉCHEC : " + n); } }

    public static void main(String[] a) {
        String full = "{\"name\":\"shoppinglist\",\"preview\":\"<html>\\\"x\\\"</html>\",\"files\":[{\"path\":\"app/src/main/AndroidManifest.xml\",\"content\":\"<?xml version=\\\"1.0\\\"?>\\n<manifest/>\"},{\"path\":\"app/src/main/java/A.java\",\"content\":\"class A { String s = \\\"}\\\"; }\"}]}";
        Map<String, String> f = TruncatedJson.files(full);
        check(f.size() == 2, "complet : 2 fichiers " + f.keySet());
        check(f.get("app/src/main/AndroidManifest.xml").equals("<?xml version=\"1.0\"?>\n<manifest/>"), "échappements décodés");
        check(f.get("app/src/main/java/A.java").equals("class A { String s = \"}\"; }"), "accolade dans une chaîne");

        // coupé pendant le 2e fichier : on garde le 1er
        String cut = full.substring(0, full.indexOf("class A") + 6);
        Map<String, String> g = TruncatedJson.files(cut);
        check(g.size() == 1 && g.containsKey("app/src/main/AndroidManifest.xml"), "coupé dans le 2e : " + g.keySet());

        // coupé juste après le premier fichier (cas réel : tableau jamais fermé)
        String cut2 = full.substring(0, full.indexOf("},{\"path\":\"app/src/main/java/A.java") + 1);
        check(TruncatedJson.files(cut2).size() == 1, "tableau non fermé");

        // coupé au milieu d'un échappement ou d'un \\u
        check(TruncatedJson.files(full.substring(0, full.indexOf("\\n<manifest") + 1)).isEmpty(), "coupé dans un échappement");
        check(TruncatedJson.files("{\"files\":[{\"path\":\"x\",\"content\":\"\\u00e9\\u00").isEmpty(), "coupé dans \\u");
        check(TruncatedJson.files("{\"files\":[{\"path\":\"x\",\"content\":\"\\u00e9\"}").get("x").equals("é"), "\\u décodé");

        // ordre inversé des champs, autres champs ignorés
        Map<String, String> h = TruncatedJson.files("{\"files\":[{\"content\":\"C\",\"lang\":\"java\",\"n\":3,\"tags\":[1,{\"a\":\"}\"}],\"path\":\"P\"}");
        check(h.size() == 1 && h.get("P").equals("C"), "ordre/champs libres : " + h);

        // format objet
        Map<String, String> m = TruncatedJson.files("{\"files\":{\"a.xml\":\"1\",\"b.xml\":\"2\",\"c.xml\":\"3");
        check(m.size() == 2 && m.get("b.xml").equals("2"), "format objet coupé : " + m);

        // rien d'utilisable
        check(TruncatedJson.files(null).isEmpty() && TruncatedJson.files("bonjour").isEmpty() && TruncatedJson.files("{\"files\":[").isEmpty() && TruncatedJson.files("{\"files\":").isEmpty(), "rien");

        check("shoppinglist".equals(TruncatedJson.field(full, "name")), "name");
        check(TruncatedJson.field(full, "preview").equals("<html>\"x\"</html>"), "preview");
        check(TruncatedJson.field("{\"name\":\"abc", "name") == null, "name coupé");
        check(TruncatedJson.field(full, "zzz") == null, "champ absent");

        System.out.println("JSON coupé : " + ok + " OK, " + ko + " échec(s)");
        if (ko > 0) System.exit(1);
    }
}
