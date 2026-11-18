import com.virtixstudio.kruxai.builder.BlockFiles;

/** Tests du lecteur de réponses en blocs de fichiers (sans JSON). */
public class BlockFilesTests {
    static int ok = 0, ko = 0;
    static void eq(String what, Object a, Object b) {
        if (String.valueOf(a).equals(String.valueOf(b))) ok++;
        else { ko++; System.out.println("ÉCHEC : " + what + " : " + a + " != " + b); }
    }
    public static void main(String[] x) {
        BlockFiles.Result r = BlockFiles.parse("NAME: compteur\n=== src/main.tsx ===\n```tsx\nconst a = {b: 1};\n```\n=== src/App.css ===\nbody { color: red; }\n\n=== FIN ===\n");
        eq("blocs : fichiers", r.files.keySet(), "[src/main.tsx, src/App.css]");
        eq("blocs : complet", r.complete, true);
        eq("blocs : nom", r.name, "compteur");
        eq("blocs : clôture retirée", r.files.get("src/main.tsx"), "const a = {b: 1};\n");
        eq("blocs : contenu css", r.files.get("src/App.css"), "body { color: red; }\n");
        r = BlockFiles.parse("=== src/main.tsx ===\nline1\n=== src/App.tsx ===\nline2 coupé");
        eq("coupé : dernier bloc écarté", r.files.keySet(), "[src/main.tsx]");
        eq("coupé : incomplet", r.complete, false);
        r = BlockFiles.parse("Voici :\n```tsx src/main.tsx\nA\n```\n\n### `src/App.tsx`\n```tsx\nB\n```\nfin");
        eq("clôtures : fichiers", r.files.keySet(), "[src/main.tsx, src/App.tsx]");
        eq("clôtures : complet", r.complete, true);
        r = BlockFiles.parse("```tsx src/main.tsx\nA\n```\n```tsx src/App.tsx\nB sans fin");
        eq("clôture coupée : écartée", r.files.keySet(), "[src/main.tsx]");
        eq("clôture coupée : incomplet", r.complete, false);
        r = BlockFiles.parse("```json title=\"package.json\"\n{}\n```");
        eq("title=", r.files.keySet(), "[package.json]");
        eq("json sans fichier", BlockFiles.parse("{\"files\": []}").files.size(), 0);
        eq("null", BlockFiles.parse(null).files.size(), 0);
        eq("ligne de =", BlockFiles.parse("=======\n```\nx\n```").files.size(), 0);
        System.out.println("BlockFiles : " + ok + "/" + (ok + ko) + (ko == 0 ? " OK" : " ÉCHECS"));
        if (ko > 0) System.exit(1);
    }
}
