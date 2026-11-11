import com.virtixstudio.kruxai.utils.NetPolicy;
import java.util.*;

public class NetPolicyTests {
    static int ok = 0, ko = 0;
    static void check(boolean c, String n) { if (c) ok++; else { ko++; System.out.println("ÉCHEC : " + n); } }

    public static void main(String[] a) {
        check(NetPolicy.isNetworkMessage("Krux ne parvient pas à joindre le service. Vérifie ta connexion Internet."), "message KRUX réseau");
        check(NetPolicy.isNetworkMessage("La réponse prend plus de temps que prévu. Vérifie ta connexion et réessaie."), "délai");
        check(NetPolicy.isNetworkMessage("java.net.UnknownHostException: Unable to resolve host \"api.groq.com\""), "DNS");
        check(NetPolicy.isNetworkMessage("java.net.SocketTimeoutException: timeout"), "timeout");
        check(!NetPolicy.isNetworkMessage("Ce moteur reçoit actuellement trop de demandes. Réessaie dans quelques instants."), "429 n'est pas réseau");
        check(!NetPolicy.isNetworkMessage("Le service a renvoyé une réponse inattendue.") && !NetPolicy.isNetworkMessage(null) && !NetPolicy.isNetworkMessage(""), "autres erreurs");
        check(NetPolicy.backoffSeconds(0) == 3 && NetPolicy.backoffSeconds(1) == 8 && NetPolicy.backoffSeconds(3) == 45 && NetPolicy.backoffSeconds(99) == 45 && NetPolicy.backoffSeconds(-2) == 3, "attentes croissantes");
        check(NetPolicy.retryText(5, 1).equals("Connexion instable. Nouvel essai dans 5 s (2/4)…"), "texte d'essai");
        List<String> q = Arrays.asList("Bonjour\nça va ?", "  ", "Deuxième message");
        List<String> back = NetPolicy.decodeQueue(NetPolicy.encodeQueue(q));
        check(back.size() == 2 && back.get(0).equals("Bonjour\nça va ?") && back.get(1).equals("Deuxième message"), "file persistée : " + back);
        check(NetPolicy.decodeQueue(null).isEmpty() && NetPolicy.decodeQueue("").isEmpty(), "file vide");
        System.out.println("Hors connexion : " + ok + " OK, " + ko + " échec(s)");
        if (ko > 0) System.exit(1);
    }
}
