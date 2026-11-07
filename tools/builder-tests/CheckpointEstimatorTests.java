import com.virtixstudio.kruxai.builder.*;
import java.io.File;
import java.nio.file.Files;
import java.util.*;

public class CheckpointEstimatorTests {
    static int ok = 0, bad = 0;
    static void check(boolean c, String n) { if (c) ok++; else { bad++; System.out.println("ÉCHEC : " + n); } }

    public static void main(String[] a) throws Exception {
        File dir = Files.createTempDirectory("krx").toFile();
        Map<String, String> files = new LinkedHashMap<>();
        files.put("app/src/main/AndroidManifest.xml", "<manifest/>");
        files.put("app/src/main/java/a/Main.java", "class Main { String s = \"é à ü ✓ 中\"; }\n" + "x".repeat(200000));
        long t = 1_000_000_000L;

        check(BuildCheckpoint.save(dir, "ma calculatrice", "calc", files, t), "sauvegarde");
        BuildCheckpoint.Saved s = BuildCheckpoint.load(dir, "ma calculatrice", t + 1000);
        check(s != null && s.name.equals("calc"), "relecture nom");
        check(s != null && s.files.equals(files), "contenu identique (accents, gros fichier)");
        check(s != null && new ArrayList<>(s.files.keySet()).equals(new ArrayList<>(files.keySet())), "ordre conservé");
        check(BuildCheckpoint.load(dir, "  ma calculatrice \n", t + 1000) != null, "espaces ignorés");
        check(BuildCheckpoint.load(dir, "autre demande", t + 1000) == null, "autre description refusée");
        check(BuildCheckpoint.load(dir, "ma calculatrice", t + BuildCheckpoint.MAX_AGE_MS + 1) == null, "expiré après 24 h");
        check(BuildCheckpoint.load(dir, "ma calculatrice", t - 3_600_000) == null, "horloge incohérente");
        check(BuildCheckpoint.load(new File(dir, "vide"), "x", t) == null, "dossier inexistant");

        File f = new File(dir, "krux-checkpoint.bin");
        byte[] raw = Files.readAllBytes(f.toPath());
        Files.write(f.toPath(), Arrays.copyOf(raw, raw.length / 2));
        check(BuildCheckpoint.load(dir, "ma calculatrice", t + 1) == null, "fichier tronqué refusé");
        Files.write(f.toPath(), "n'importe quoi".getBytes());
        check(BuildCheckpoint.load(dir, "ma calculatrice", t + 1) == null, "fichier corrompu refusé");

        Map<String, String> noManifest = new LinkedHashMap<>(); noManifest.put("a.txt", "x");
        BuildCheckpoint.save(dir, "d", "n", noManifest, t);
        check(BuildCheckpoint.load(dir, "d", t) == null, "sans manifeste refusé");
        check(!BuildCheckpoint.save(dir, "d", "n", new LinkedHashMap<>(), t), "rien à sauver");

        BuildCheckpoint.save(dir, "ma calculatrice", "calc", files, t);
        BuildCheckpoint.clear(dir);
        check(BuildCheckpoint.load(dir, "ma calculatrice", t) == null, "effacé");
        check(!new File(dir, "krux-checkpoint.bin.tmp").exists(), "pas de fichier temporaire");

        BuildEstimator.Estimate small = BuildEstimator.estimate("Une calculatrice simple");
        check(!small.large && small.files < 15, "calculatrice = petite");
        BuildEstimator.Estimate mid = BuildEstimator.estimate("Une app de notes avec un calendrier");
        check(!mid.large, "notes + calendrier = moyenne");
        BuildEstimator.Estimate big = BuildEstimator.estimate(
            "Une app de réseau social avec connexion, compte utilisateur, chat en temps réel, notifications, "
            + "partage de photos avec la caméra, carte avec GPS, paiement, boutique avec panier, statistiques et synchronisation firebase");
        check(big.large, "réseau social = ambitieuse");
        check(big.files <= 40 && big.files > small.files, "fichiers bornés et croissants");
        check(big.minutesMax > big.minutesMin && big.minutesMin >= 2, "durée cohérente");
        check(big.message().contains("simplifiée"), "message propose la version simplifiée");
        check(!BuildEstimator.estimate("").large && !BuildEstimator.estimate(null).large, "vide / null");
        check(BuildEstimator.simplified("mon app").contains("VERSION SIMPLIFIÉE"), "demande simplifiée");

        System.out.println("Reprise + estimation : " + ok + "/" + (ok + bad) + (bad == 0 ? " OK" : " ÉCHECS"));
        if (bad > 0) System.exit(1);
    }
}
