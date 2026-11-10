import com.virtixstudio.kruxai.core.SyncCodec;

public class SyncCodecTests {
    static int ok = 0, ko = 0;
    static void check(boolean c, String n) { if (c) ok++; else { ko++; System.out.println("ÉCHEC : " + n); } }

    public static void main(String[] a) throws Exception {
        String[] samples = {"", "a", "ab", "abc", "Bonjour é à ü 😀 \n\"x\"", "x".repeat(100000)};
        for (String s : samples) {
            String p = SyncCodec.pack(s);
            check(p.startsWith("g2:"), "préfixe");
            check(s.equals(SyncCodec.unpack(p)), "aller-retour " + s.length());
        }
        check(SyncCodec.pack("x".repeat(100000)).length() < 400, "compression forte");
        // anciennes données (version 1) toujours lisibles
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream z = new java.util.zip.GZIPOutputStream(bo)) { z.write("ancien".getBytes("UTF-8")); }
        check("ancien".equals(SyncCodec.unpack("g1:" + java.util.Base64.getEncoder().encodeToString(bo.toByteArray()))), "lecture de g1");
        // gain réel sur un petit chat, comparé à gzip + base64
        String chat = "{\"sid\":\"session_1731000000000\",\"title\":\"Mon projet Android\",\"pin\":0,\"msgs\":[{\"s\":\"user\",\"m\":\"Bonjour, peux-tu m'aider avec mon application ?\",\"t\":1731000001000},"
                + "{\"s\":\"ai\",\"m\":\"Bonjour ! Voici ce que j'ai trouvé. Je peux t'aider avec ton application Android : quel est le problème ?\",\"t\":1731000009000}]}";
        java.io.ByteArrayOutputStream bg = new java.io.ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream z = new java.util.zip.GZIPOutputStream(bg)) { z.write(chat.getBytes("UTF-8")); }
        int v1 = java.util.Base64.getEncoder().encodeToString(bg.toByteArray()).length() + 3;
        int v2 = SyncCodec.pack(chat).length();
        System.out.println("Petit chat : brut " + chat.length() + " → g1 " + v1 + " → g2 " + v2 + " (" + (100 - 100 * v2 / chat.length()) + " % de gain)");
        check(v2 < v1, "g2 plus petit que g1 : " + v2 + " < " + v1);
        byte[] bin = SyncCodec.deflate("abc abc abc".getBytes("UTF-8"));
        check("abc abc abc".equals(new String(SyncCodec.inflate(bin), "UTF-8")), "octets aller-retour");
        check(SyncCodec.inflate(new byte[]{1, 2, 3, 4}) == null, "octets invalides");
        check(SyncCodec.unpack(null) == null, "null");
        check(SyncCodec.unpack("abc") == null, "sans préfixe");
        check(SyncCodec.unpack("g1:@@@@") == null, "base64 invalide");
        check(SyncCodec.unpack("g1:AAAA") == null, "gzip invalide");
        check(SyncCodec.unpack("g2:AAAA") == null, "deflate invalide");
        check(SyncCodec.unpack("g1:AAA") == null, "longueur invalide");
        System.out.println("Synchro (codec) : " + ok + "/" + (ok + ko) + (ko == 0 ? " OK" : " ÉCHECS"));
        if (ko > 0) System.exit(1);
    }
}
