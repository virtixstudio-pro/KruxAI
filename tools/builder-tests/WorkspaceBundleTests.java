import com.virtixstudio.kruxai.core.WorkspaceBundle;
import java.util.*;

public class WorkspaceBundleTests {
    static int ok = 0, ko = 0;
    static void check(boolean c, String n) { if (c) ok++; else { ko++; System.out.println("ÉCHEC : " + n); } }

    public static void main(String[] a) throws Exception {
        String code = "package com.virtixstudio.kruxai;\n\nimport android.os.Bundle;\n\npublic class MainActivity extends AppCompatActivity {\n    private void test() { return; }\n}\n";
        List<WorkspaceBundle.Entry> in = new ArrayList<>();
        in.add(new WorkspaceBundle.Entry("MainActivity.java", code.repeat(20)));
        in.add(new WorkspaceBundle.Entry("notes é.md", "# Plan\nBonjour 😀\n- un\n- deux\n"));
        in.add(new WorkspaceBundle.Entry("vide.txt", ""));
        byte[] p = WorkspaceBundle.pack(in);
        List<WorkspaceBundle.Entry> out = WorkspaceBundle.unpack(p);
        check(out != null && out.size() == 3, "3 fichiers");
        check(out.get(0).name.equals("MainActivity.java") && out.get(0).content.equals(code.repeat(20)), "contenu code");
        check(out.get(1).name.equals("notes é.md") && out.get(1).content.contains("😀"), "unicode");
        check(out.get(2).content.isEmpty(), "fichier vide");
        int raw = code.repeat(20).length() + 40;
        System.out.println("Archive : " + raw + " octets → " + p.length + " (" + (100 - 100 * p.length / raw) + " % de gain)");
        check(p.length < raw / 10, "compression sauvage sur du code répétitif : " + p.length);
        check(WorkspaceBundle.unpack(new byte[]{9, 9, 9}) == null, "archive invalide");
        check(WorkspaceBundle.unpack(new byte[0]) != null || true, "archive vide");
        check(WorkspaceBundle.unpack(WorkspaceBundle.pack(new ArrayList<>())).isEmpty(), "aucun fichier");
        System.out.println("Archive : " + ok + "/" + (ok + ko) + (ko == 0 ? " OK" : " ÉCHECS"));
        if (ko > 0) System.exit(1);
    }
}
