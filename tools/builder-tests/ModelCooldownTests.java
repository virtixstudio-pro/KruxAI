import com.virtixstudio.kruxai.core.ModelCooldown;
import java.util.Arrays;

public class ModelCooldownTests {
    static int ok = 0, bad = 0;
    static void check(boolean c, String name) { if (c) ok++; else { bad++; System.out.println("ÉCHEC : " + name); } }

    public static void main(String[] a) {
        ModelCooldown.clear();
        long t = 1_000_000L;
        check(!ModelCooldown.isCooling("A", t), "modèle neuf disponible");
        ModelCooldown.markLimited("A", t, 0);
        check(ModelCooldown.isCooling("A", t + 1), "limité juste après");
        check(ModelCooldown.readyIn("A", t) == 60_000, "défaut = 60 s");
        check(!ModelCooldown.isCooling("A", t + 60_001), "libéré après 60 s");
        check(!ModelCooldown.isCooling("B", t), "autre modèle non touché");

        ModelCooldown.clear();
        ModelCooldown.markLimited("A", t, 1000);
        check(ModelCooldown.readyIn("A", t) == 5_000, "plancher 5 s");
        ModelCooldown.markLimited("B", t, 999_000);
        check(ModelCooldown.readyIn("B", t) == 120_000, "plafond 120 s");
        ModelCooldown.markLimited("A", t, 30_000);
        check(ModelCooldown.readyIn("A", t) == 30_000, "pénalité plus longue conservée");
        ModelCooldown.markLimited("A", t, 6_000);
        check(ModelCooldown.readyIn("A", t) == 30_000, "pénalité plus courte n'écrase pas");

        ModelCooldown.clear();
        ModelCooldown.markLimited("A", t, 20_000);
        ModelCooldown.markLimited("B", t, 40_000);
        check(ModelCooldown.soonestReadyMs(Arrays.asList("A", "B"), t) == 20_000, "premier prêt = A");
        check(ModelCooldown.soonestReadyMs(Arrays.asList("A", "B", "C"), t) == 0, "C libre : pas d'attente");
        check(ModelCooldown.soonestReadyMs(Arrays.asList(), t) == 0, "liste vide");
        check(ModelCooldown.soonestReadyMs(Arrays.asList("A", "B"), t + 25_000) == 0, "A libéré");

        check(ModelCooldown.isRateLimit("HTTP 429"), "429");
        check(ModelCooldown.isRateLimit("HTTP 429 retry-after=12"), "429 avec délai");
        check(ModelCooldown.isRateLimit("Rate limit reached for model"), "rate limit texte");
        check(ModelCooldown.isRateLimit("RESOURCE_EXHAUSTED"), "gemini");
        check(ModelCooldown.isRateLimit("You exceeded your current quota"), "quota");
        check(!ModelCooldown.isRateLimit("HTTP 500"), "500 n'est pas un quota");
        check(!ModelCooldown.isRateLimit("HTTP 401"), "401 n'est pas un quota");
        check(!ModelCooldown.isRateLimit("Réponse vide."), "réponse vide");
        check(!ModelCooldown.isRateLimit(null), "null");

        check(ModelCooldown.parseRetryAfterMs("HTTP 429 retry-after=12") == 12_000, "12 s");
        check(ModelCooldown.parseRetryAfterMs("HTTP 429 retry-after=7.5") == 7_500, "7,5 s");
        check(ModelCooldown.parseRetryAfterMs("HTTP 429") == 0, "absent");
        check(ModelCooldown.parseRetryAfterMs("HTTP 429 retry-after=abc") == 0, "invalide");
        check(ModelCooldown.parseRetryAfterMs("HTTP 429 retry-after=Wed, 21 Oct") == 0, "date HTTP ignorée");
        check(ModelCooldown.parseRetryAfterMs(null) == 0, "null retry");

        System.out.println("ModelCooldown : " + ok + "/" + (ok + bad) + (bad == 0 ? " OK" : " ÉCHECS"));
        if (bad > 0) System.exit(1);
    }
}
