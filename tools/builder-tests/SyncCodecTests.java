import com.virtixstudio.kruxai.core.SyncCodec;

public class SyncCodecTests {
    static int ok = 0, ko = 0;
    static void check(boolean c, String n) { if (c) ok++; else { ko++; System.out.println("ÉCHEC : " + n); } }

    public static void main(String[] a) throws Exception {
        String[] samples = {"", "a", "ab", "abc", "Bonjour é à ü 😀 \n\"x\"", "x".repeat(100000)};
        for (String s : samples) {
            String p = SyncCodec.pack(s);
            check(p.startsWith("g1:"), "préfixe");
            check(s.equals(SyncCodec.unpack(p)), "aller-retour " + s.length());
        }
        check(SyncCodec.pack("x".repeat(100000)).length() < 2000, "compression forte");
        check(SyncCodec.unpack(null) == null, "null");
        check(SyncCodec.unpack("abc") == null, "sans préfixe");
        check(SyncCodec.unpack("g1:@@@@") == null, "base64 invalide");
        check(SyncCodec.unpack("g1:AAAA") == null, "gzip invalide");
        check(SyncCodec.unpack("g1:AAA") == null, "longueur invalide");
        System.out.println("Synchro (codec) : " + ok + "/" + (ok + ko) + (ko == 0 ? " OK" : " ÉCHECS"));
        if (ko > 0) System.exit(1);
    }
}
