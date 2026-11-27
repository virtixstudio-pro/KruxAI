import com.virtixstudio.kruxai.builder.TsStringRepair;

/** Tests de la réparation locale des chaînes TypeScript. */
public class TsStringRepairTests {
    static int ok = 0, ko = 0;
    static void eq(String what, Object a, Object b) {
        if (String.valueOf(a).equals(String.valueOf(b))) ok++;
        else { ko++; System.out.println("ÉCHEC : " + what + " : " + a + " != " + b); }
    }
    static String r(String s) { return TsStringRepair.repair("src/data.ts", s); }
    public static void main(String[] x) {
        eq("apostrophe", r("  nom: 'l'application',"), "  nom: `l'application`,");
        eq("aujourd'hui", r("t: 'Aujourd'hui' ,"), "t: `Aujourd'hui` ,");
        eq("non fermée", r("  nom: 'Texte coupé,"), "  nom: `Texte coupé`,");
        eq("saine inchangée", r("a: 'ok', b: \"d'accord\","), "a: 'ok', b: \"d'accord\",");
        eq("échappée inchangée", r("a: 'l\\'app',"), "a: 'l\\'app',");
        eq("commentaire", r("// l'app\nconst a = 1;"), "// l'app\nconst a = 1;");
        eq("bloc commentaire", r("/* l'app\n c'est */\nconst a = 1;"), "/* l'app\n c'est */\nconst a = 1;");
        eq("regex ignorée", r("s.replace(/'/g, \"’\");"), "s.replace(/'/g, \"’\");");
        eq("tsx ignoré", TsStringRepair.repair("src/App.tsx", "<p>l'app</p>"), "<p>l'app</p>");
        eq("gabarit multi-lignes", r("const a = `x\n l'app\n`;"), "const a = `x\n l'app\n`;");
        System.out.println(ok + " ok, " + ko + " échec(s)");
        if (ko > 0) System.exit(1);
    }
}
