import com.virtixstudio.kruxai.core.WorkspaceStore;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;

public class WorkspaceStoreTests {
    static int ok = 0, ko = 0;
    static void check(boolean c, String n) { if (c) ok++; else { ko++; System.out.println("ÉCHEC : " + n); } }
    static ByteArrayInputStream in(String s) { return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)); }

    public static void main(String[] a) throws Exception {
        // noms
        check("passwd".equals(WorkspaceStore.safeName("../../etc/passwd")), "traversée /");
        check("c.txt".equals(WorkspaceStore.safeName("a/b\\c.txt")), "séparateurs");
        check("hidden".equals(WorkspaceStore.safeName(".hidden")), "point initial");
        check("fichier.txt".equals(WorkspaceStore.safeName("")), "vide");
        check("fichier.txt".equals(WorkspaceStore.safeName(null)), "null");
        check("ab.txt".equals(WorkspaceStore.safeName("a\u0000b:*.txt")), "contrôle et interdits");
        String longName = "x".repeat(200) + ".java";
        String cut = WorkspaceStore.safeName(longName);
        check(cut.length() == 80 && cut.endsWith(".java"), "nom long coupé, extension gardée");
        check("java".equals(WorkspaceStore.extension("Main.JAVA")), "extension minuscule");
        check("".equals(WorkspaceStore.extension("Makefile")), "sans extension");
        check("".equals(WorkspaceStore.extension(".bashrc")), "fichier caché sans extension");

        // tailles
        check("12 o".equals(WorkspaceStore.sizeLabel(12)), "octets");
        check("12 Ko".equals(WorkspaceStore.sizeLabel(12 * 1024)), "Ko");
        check(WorkspaceStore.sizeLabel(1500000).endsWith("Mo"), "Mo");

        File dir = java.nio.file.Files.createTempDirectory("ws").toFile();
        WorkspaceStore s = new WorkspaceStore(new File(dir, "workspace"));

        // import normal et doublons
        File f1 = s.importStream("a.txt", in("Bonjour é"));
        check(f1.getName().equals("a.txt") && f1.isFile(), "import");
        check("Bonjour é".equals(s.readText(f1)), "relecture UTF-8");
        File f2 = s.importStream("a.txt", in("deux"));
        check(f2.getName().equals("a (2).txt"), "doublon renommé : " + f2.getName());
        File f3 = s.importStream("a.txt", in("trois"));
        check(f3.getName().equals("a (3).txt"), "troisième doublon");
        File f4 = s.importStream("Makefile", in("all:"));
        File f5 = s.importStream("Makefile", in("all2:"));
        check(f5.getName().equals("Makefile (2)"), "doublon sans extension : " + f5.getName());

        // refus
        try { s.importStream("img.png", new ByteArrayInputStream(new byte[]{1, 2, 0, 3})); check(false, "binaire accepté"); }
        catch (java.io.IOException e) { check(e.getMessage().contains("texte"), "binaire refusé"); }
        try { s.importStream("gros.txt", new ByteArrayInputStream(new byte[1_000_001])); check(false, "gros accepté"); }
        catch (java.io.IOException e) { check(e.getMessage().contains("gros"), "trop gros refusé"); }
        File ok1 = s.importStream("limite.txt", new ByteArrayInputStream("x".repeat(1_000_000).getBytes()));
        check(ok1.length() == 1_000_000, "1 Mo pile accepté");

        // liste : le plus récent d'abord
        f1.setLastModified(1_000_000_000L);
        f2.setLastModified(2_000_000_000L);
        java.util.List<File> l = s.list();
        check(l.indexOf(f2) < l.indexOf(f1), "tri du plus récent au plus ancien");

        // écriture, résolution, suppression
        s.writeText(f1, "modifié");
        check("modifié".equals(s.readText(f1)), "écriture");
        check(s.resolve("a.txt") != null, "resolve existant");
        check(s.resolve("../x") == null, "resolve traversée");
        check(s.resolve("absent.txt") == null, "resolve absent");
        File outside = new File(dir, "dehors.txt");
        java.nio.file.Files.write(outside.toPath(), "x".getBytes());
        check(!s.delete(outside) && outside.exists(), "pas de suppression hors dossier");
        boolean blocked = false;
        try { s.writeText(outside, "y"); } catch (java.io.IOException e) { blocked = true; }
        check(blocked, "pas d'écriture hors dossier");
        check(s.delete(f1) && !f1.exists(), "suppression");

        // limite de 100 fichiers
        File dir2 = java.nio.file.Files.createTempDirectory("ws2").toFile();
        WorkspaceStore s2 = new WorkspaceStore(dir2);
        for (int i = 0; i < 100; i++) s2.importStream("f" + i + ".txt", in("x"));
        try { s2.importStream("de-trop.txt", in("x")); check(false, "101e accepté"); }
        catch (java.io.IOException e) { check(e.getMessage().contains("100"), "limite 100"); }

        System.out.println("WorkspaceStore : " + ok + " ok, " + ko + " échec(s)");
        if (ko > 0) System.exit(1);
    }
}
