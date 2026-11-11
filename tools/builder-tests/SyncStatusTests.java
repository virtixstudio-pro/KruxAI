import com.virtixstudio.kruxai.utils.SyncStatus;

public class SyncStatusTests {
    static int ok = 0, ko = 0;
    static void check(boolean c, String n) { if (c) ok++; else { ko++; System.out.println("ÉCHEC : " + n); } }
    public static void main(String[] a) {
        long now = 1_000_000_000L;
        check(SyncStatus.ago(now - 5_000, now).equals("à l'instant"), "instant");
        check(SyncStatus.ago(now - 5 * 60_000, now).equals("il y a 5 min"), "min");
        check(SyncStatus.ago(now - 3 * 3600_000L, now).equals("il y a 3 h"), "h");
        check(SyncStatus.ago(now - 2 * 86400_000L, now).equals("il y a 2 j"), "j");
        check(SyncStatus.line(0, "", now).equals("Pas encore synchronisé"), "jamais");
        check(SyncStatus.line(now - 120_000, null, now).equals("À jour · il y a 2 min"), "à jour");
        check(SyncStatus.line(0, "HTTP 401: Non connecté", now).contains("FIREBASE_PROJECT_ID"), "401");
        check(SyncStatus.line(0, "HTTP 500: Base D1 non liée (liaison DB manquante)", now).contains("liaison"), "500");
        check(SyncStatus.line(now - 60_000, "java.net.UnknownHostException: Unable to resolve host", now).contains("pas de connexion") , "réseau");
        check(SyncStatus.line(0, "HTTP 413 quota", now).contains("quota"), "quota");
        check(SyncStatus.line(0, "Stockage Virtix Studio non configuré", now).contains("adresse"), "non configuré");
        System.out.println("Statut synchro : " + ok + " OK, " + ko + " échec(s)");
        if (ko > 0) System.exit(1);
    }
}
