import com.virtixstudio.kruxai.builder.BuildProgress;

public class BuildProgressTests {
    static int ok = 0, bad = 0;
    static void check(boolean c, String n) { if (c) ok++; else { bad++; System.out.println("ÉCHEC : " + n); } }

    public static void main(String[] a) {
        BuildProgress p = new BuildProgress();
        String r0 = p.render();
        check(r0.contains("J'écris ton application") && r0.contains("Démarrage"), "état initial");
        check(!r0.contains("✓"), "aucun fichier au départ");

        p.addFile("build.gradle", 1250, false);
        p.addFile("app/src/main/AndroidManifest.xml", 800, false);
        p.addFile("app/src/main/java/a/Main.java", 100, false);
        String r1 = p.render();
        check(r1.contains("✓ `build.gradle` · 1,2 Ko"), "taille en Ko avec virgule");
        check(r1.contains("· 800 o"), "taille en octets");
        check(r1.contains("3 fichiers"), "compteur");
        check(r1.indexOf("build.gradle") < r1.indexOf("AndroidManifest"), "ordre d'arrivée");

        p.addFile("app/src/main/java/a/Main.java", 5000, true);
        String r2 = p.render();
        check(r2.contains("Main.java` · 4,9 Ko · corrigé"), "corrigé marqué");
        check(p.fileCount() == 3, "mise à jour sans doublon");
        p.addFile("app/src/main/java/a/Main.java", 100, false);
        check(p.render().contains("corrigé"), "corrigé reste marqué");

        p.setTitle("Je compile ton application…"); p.setStep("Compilation (2 à 5 min)…");
        String r3 = p.render();
        check(r3.contains("**Je compile") && r3.contains("Compilation (2 à 5 min)"), "titre et étape");

        BuildProgress big = new BuildProgress();
        for (int i = 0; i < 30; i++) big.addFile("f" + i + ".java", 10, false);
        String rb = big.render();
        check(rb.contains("… 18 fichiers plus haut"), "liste limitée à 12");
        check(!rb.contains("`f0.java`") && rb.contains("`f29.java`") && rb.contains("30 fichiers"), "garde les derniers");

        p.finish("Ton application est prête", "Android va te proposer de l'installer.");
        p.setStep("ignoré"); p.setTitle("ignoré");
        String rd = p.render();
        check(rd.contains("Ton application est prête") && rd.contains("Android va te proposer"), "fin");
        check(!rd.contains("ignoré") && !rd.contains("Compilation (2"), "plus d'étape après la fin");

        BuildProgress f = new BuildProgress();
        f.addFile("x.xml", 5, false);
        f.fail("Le quota est atteint.");
        String rf = f.render();
        check(rf.contains("Je n'ai pas pu finir") && rf.contains("Le quota est atteint.") && rf.contains("x.xml"), "échec garde les fichiers");

        BuildProgress e = new BuildProgress();
        e.addFile(null, 1, false); e.addFile("", 1, false); e.addFile("a", -5, false);
        check(e.fileCount() == 1 && e.render().contains("0 o"), "entrées invalides");
        check(new BuildProgress().render().length() > 0, "rendu jamais vide");

        System.out.println("Progression : " + ok + "/" + (ok + bad) + (bad == 0 ? " OK" : " ÉCHECS"));
        if (bad > 0) System.exit(1);
    }
}
