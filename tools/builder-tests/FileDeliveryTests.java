import com.virtixstudio.kruxai.utils.FileDelivery;
import java.util.List;

public class FileDeliveryTests {
    static int ok = 0, ko = 0;
    static void check(boolean c, String n) { if (c) ok++; else { ko++; System.out.println("ÉCHEC : " + n); } }

    public static void main(String[] a) {
        String t = "Voici ton plan.\n<KRUX_FILE name=\"plan.md\">\n# Plan\n- un\n- deux\n</KRUX_FILE>\nBonne lecture.";
        List<FileDelivery.DFile> f = FileDelivery.parse(t);
        check(f.size() == 1 && f.get(0).name.equals("plan.md"), "un fichier");
        check(f.get(0).content.equals("# Plan\n- un\n- deux"), "contenu : [" + f.get(0).content + "]");
        check(f.get(0).mime().equals("text/markdown"), "mime md");
        check(FileDelivery.stripForDisplay(t).equals("Voici ton plan.\n\nBonne lecture."), "affichage : [" + FileDelivery.stripForDisplay(t) + "]");

        String partial = "Je prépare.\n<KRUX_FILE name=\"data.csv\">\na,b\n1,2";
        check(FileDelivery.parse(partial).isEmpty(), "bloc ouvert : pas encore de fichier");
        check(FileDelivery.stripForDisplay(partial).equals("Je prépare."), "bloc ouvert masqué : [" + FileDelivery.stripForDisplay(partial) + "]");
        check("data.csv".equals(FileDelivery.writingNow(partial)), "en cours d'écriture");
        check(FileDelivery.writingNow(t) == null, "rien en cours");
        check(FileDelivery.stripForDisplay("Salut <KRUX_FIL").equals("Salut"), "balise coupée");

        check(FileDelivery.safeName("../../etc/passwd").equals("passwd.txt"), "pas de dossier : " + FileDelivery.safeName("../../etc/passwd"));
        check(FileDelivery.safeName("C:\\Users\\x\\rapport.md").equals("rapport.md"), "chemin windows");
        check(FileDelivery.safeName("virus.exe").equals("virus.txt"), "extension dangereuse");
        check(FileDelivery.safeName("  mon fichier (1).json ").equals("mon_fichier__1_.json"), "espaces : " + FileDelivery.safeName("  mon fichier (1).json "));
        check(FileDelivery.safeName("").equals("fichier.txt"), "vide");
        check(FileDelivery.safeName(".hidden").equals("hidden.txt"), "caché");
        check(FileDelivery.safeName("a".repeat(200) + ".md").length() <= 80, "longueur");

        String two = "<KRUX_FILE name=\"a.txt\">1</KRUX_FILE><KRUX_FILE name=\"a.txt\">2</KRUX_FILE>";
        List<FileDelivery.DFile> g = FileDelivery.parse(two);
        check(g.size() == 2 && !g.get(0).name.equals(g.get(1).name), "noms uniques : " + g.get(1).name);

        String fenced = "<KRUX_FILE name=\"x.py\">\n```python\nprint(1)\n```\n</KRUX_FILE>";
        check(FileDelivery.parse(fenced).get(0).content.equals("print(1)"), "bloc de code retiré : [" + FileDelivery.parse(fenced).get(0).content + "]");

        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 12; i++) many.append("<KRUX_FILE name=\"f").append(i).append(".txt\">x</KRUX_FILE>");
        check(FileDelivery.parse(many.toString()).size() == FileDelivery.MAX_FILES, "max fichiers");
        check(FileDelivery.parse("pas de fichier").isEmpty() && FileDelivery.parse(null).isEmpty(), "sans fichier");
        check(FileDelivery.parse("<KRUX_FILE name=\"é.md\">ü</KRUX_FILE>").get(0).bytes() == 2, "octets UTF-8");

        System.out.println("Fichiers livrés : " + ok + "/" + (ok + ko) + (ko == 0 ? " OK" : " ÉCHECS"));
        if (ko > 0) System.exit(1);
    }
}
