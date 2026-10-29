import com.virtixstudio.kruxai.builder.*;
import java.util.*;

/** Tests des classes pures du Builder. Lancer : tools/builder-tests/run.sh */
public class BuilderTests {
    static int fails = 0, total = 0;
    static void check(String name, boolean ok) { total++; if (!ok) { fails++; System.out.println("ECHEC : " + name); } }
    static boolean has(List<String> l, String frag) { for (String s : l) if (s.contains(frag)) return true; return false; }

    static final String J = "app/src/main/java/com/krux/generated/";
    static final String MAN = "app/src/main/AndroidManifest.xml";

    static Map<String, String> good() {
        Map<String, String> f = new LinkedHashMap<>();
        f.put(MAN, "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
            + "<uses-permission android:name=\"android.permission.INTERNET\"/>\n"
            + "<application android:label=\"@string/app_name\" android:theme=\"@style/AppTheme\">\n"
            + "<activity android:name=\".MainActivity\" android:exported=\"true\"><intent-filter>\n"
            + "<action android:name=\"android.intent.action.MAIN\"/><category android:name=\"android.intent.category.LAUNCHER\"/>\n"
            + "</intent-filter></activity></application></manifest>");
        f.put(J + "MainActivity.java", "package com.krux.generated;\n\nimport android.os.Bundle;\nimport android.widget.TextView;\n"
            + "import androidx.appcompat.app.AppCompatActivity;\n\npublic class MainActivity extends AppCompatActivity {\n"
            + "  @Override protected void onCreate(Bundle b) { super.onCreate(b); setContentView(R.layout.activity_main);\n"
            + "    TextView t = findViewById(R.id.title); t.setText(R.string.app_name); // { commentaire\n"
            + "    String s = \"}{\"; } }\n");
        f.put("app/src/main/res/layout/activity_main.xml", "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" "
            + "android:layout_width=\"match_parent\" android:layout_height=\"match_parent\" android:fitsSystemWindows=\"true\">"
            + "<TextView android:id=\"@+id/title\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" "
            + "android:text=\"@string/app_name\" android:textColor=\"@color/ink\"/></LinearLayout>");
        f.put("app/src/main/res/values/strings.xml", "<resources><string name=\"app_name\">Test</string></resources>");
        f.put("app/src/main/res/values/colors.xml", "<resources><color name=\"ink\">#111111</color></resources>");
        f.put("app/src/main/res/values/themes.xml", "<resources><style name=\"AppTheme\" parent=\"Theme.MaterialComponents.DayNight.NoActionBar\"/></resources>");
        return f;
    }

    static Map<String, String> copy(Map<String, String> m) { return new LinkedHashMap<>(m); }

    public static void main(String[] a) {
        // --- BuildSpec
        BuildSpec s = new BuildSpec();
        check("spec par défaut valide", s.isValid());
        BuildSpec bad = s.copy(); bad.targetSdk = 40; check("targetSdk > compileSdk refusé", !bad.isValid());
        bad = s.copy(); bad.agp = "8.x"; check("version invalide refusée", !bad.isValid());
        check("isVersion ok", BuildSpec.isVersion("8.13.0") && BuildSpec.isVersion("2.1") && !BuildSpec.isVersion("1.0'; rm") && !BuildSpec.isVersion(null));

        // --- Templates
        Map<String, String> g = BuildTemplates.gradleFiles(s);
        check("gradle: agp", g.get("build.gradle").contains("version '8.13.0'"));
        check("gradle: sdk", g.get("app/build.gradle").contains("compileSdk 36") && g.get("app/build.gradle").contains("targetSdk 36"));
        String wf = BuildTemplates.workflow(s);
        check("workflow: gradle 8.13 + artifact error", wf.contains("gradle-version: 8.13") && wf.contains("if-no-files-found: error"));

        // --- chemins
        check("chemin manifeste", BuildValidator.pathProblem(MAN) == null);
        check("chemin java ok", BuildValidator.pathProblem(J + "Foo.java") == null);
        check("chemin java sous-dossier refusé", BuildValidator.pathProblem(J + "sub/Foo.java") != null);
        check("chemin gradle refusé", BuildValidator.pathProblem("build.gradle") != null);
        check("chemin traversée refusé", BuildValidator.pathProblem("app/src/main/../../x.xml") != null);
        check("png refusé", BuildValidator.pathProblem("app/src/main/res/drawable/a.png") != null);
        check("res xml ok", BuildValidator.pathProblem("app/src/main/res/drawable/ic_a.xml") == null);
        check("res majuscule refusé", BuildValidator.pathProblem("app/src/main/res/layout/Main.xml") != null);
        check("values-night ok", BuildValidator.pathProblem("app/src/main/res/values-night/colors.xml") == null);
        check("assets html ok", BuildValidator.pathProblem("app/src/main/assets/index.html") == null);
        check("assets exe refusé", BuildValidator.pathProblem("app/src/main/assets/a.exe") != null);

        // --- app correcte : aucun problème
        List<String> p = BuildValidator.validate(good());
        check("app correcte sans problème " + p, p.isEmpty());

        // --- normalize : exported, package, imports, icône
        Map<String, String> f = copy(good());
        f.put(MAN, f.get(MAN).replace(" android:exported=\"true\"", "").replace("<manifest ", "<manifest package=\"foo.bar\" ")
            .replace("<application ", "<application android:icon=\"@mipmap/ic_launcher\" "));
        f.put(J + "MainActivity.java", f.get(J + "MainActivity.java").replace("package com.krux.generated;", "package foo.bar;")
            .replace("import android.os.Bundle;\n", "").replace("import android.widget.TextView;\n", ""));
        List<String> ap = new ArrayList<>();
        Map<String, String> n = BuildValidator.normalize(f, ap);
        check("normalize: exported ajouté", n.get(MAN).contains("android:exported=\"true\""));
        check("normalize: package retiré", !n.get(MAN).contains("package="));
        check("normalize: icône retirée", !n.get(MAN).contains("ic_launcher"));
        check("normalize: package java", n.get(J + "MainActivity.java").contains("package com.krux.generated;"));
        check("normalize: imports", n.get(J + "MainActivity.java").contains("import android.os.Bundle;") && n.get(J + "MainActivity.java").contains("import android.widget.TextView;"));
        check("après normalize: valide " + BuildValidator.validate(n), BuildValidator.validate(n).isEmpty());
        check("normalize idempotent", BuildValidator.normalize(n, new ArrayList<String>()).equals(n));

        // --- exported=false pour activité sans intent-filter
        f = copy(good());
        f.put(MAN, f.get(MAN).replace("</application>", "<activity android:name=\".Second\"/></application>"));
        f.put(J + "Second.java", "package com.krux.generated;\nimport androidx.appcompat.app.AppCompatActivity;\npublic class Second extends AppCompatActivity {}\n");
        n = BuildValidator.normalize(f, new ArrayList<String>());
        check("exported=false sans filtre", n.get(MAN).contains("<activity android:exported=\"false\" android:name=\".Second\"/>"));
        check("2e activité valide " + BuildValidator.validate(n), BuildValidator.validate(n).isEmpty());

        // --- validate : problèmes détectés
        f = copy(good()); f.remove(J + "MainActivity.java");
        check("activité sans fichier", has(BuildValidator.validate(f), "MainActivity.java n'existe pas"));

        f = copy(good()); f.put(J + "MainActivity.java", f.get(J + "MainActivity.java").replace("R.string.app_name", "R.string.manquant"));
        check("R.string manquant", has(BuildValidator.validate(f), "R.string.manquant"));

        f = copy(good()); f.put("app/src/main/res/values/colors.xml", "<resources></resources>");
        check("@color manquant", has(BuildValidator.validate(f), "@color/ink"));

        f = copy(good()); f.put(J + "MainActivity.java", f.get(J + "MainActivity.java").replace("}\n", ""));
        check("accolade manquante", has(BuildValidator.validate(f), "jamais fermé") || has(BuildValidator.validate(f), "accolades"));

        f = copy(good()); f.put(J + "MainActivity.java", f.get(J + "MainActivity.java").replace("import android.os.Bundle;", "import com.bumptech.glide.Glide;\nimport android.os.Bundle;"));
        check("import bibliothèque absente", has(BuildValidator.validate(f), "com.bumptech.glide.Glide"));

        f = copy(good()); f.put(J + "MainActivity.java", f.get(J + "MainActivity.java").replace("s = \"}{\";", "s = \"}{\"; switch (1) { case R.id.title: break; }"));
        check("switch sur R.id", has(BuildValidator.validate(f), "case R.id"));

        f = copy(good()); f.put(J + "MainActivity.java", f.get(J + "MainActivity.java").replace("@Override protected void onCreate", "@Override public void onBackPressed() {}\n @Override protected void onCreate"));
        check("onBackPressed", has(BuildValidator.validate(f), "onBackPressed"));

        f = copy(good()); f.put("app/src/main/res/layout/activity_main.xml", f.get("app/src/main/res/layout/activity_main.xml").replace("</LinearLayout>", "<com.bumptech.glide.Foo/></LinearLayout>"));
        check("vue bibliothèque absente", has(BuildValidator.validate(f), "com.bumptech.glide.Foo"));

        f = copy(good()); f.put("app/src/main/res/layout/activity_main.xml", "<LinearLayout><TextView></LinearLayout>");
        check("XML invalide", has(BuildValidator.validate(f), "XML invalide"));

        f = copy(good()); f.put("app/src/main/res/values/themes.xml", "<resources><style name=\"AppTheme\" parent=\"Inconnu\"/></resources>");
        check("parent de style inconnu", has(BuildValidator.validate(f), "parent"));

        f = copy(good()); f.put(J + "MainActivity.java", f.get(J + "MainActivity.java") + "\n// ... reste du code\n");
        check("placeholder", has(BuildValidator.validate(f), "remplacement"));

        f = copy(good()); f.put("app/src/main/res/layout/x.xml", "<!DOCTYPE a [<!ENTITY b \"c\">]><a/>");
        check("DOCTYPE refusé", has(BuildValidator.validate(f), "DOCTYPE"));

        f = copy(good()); f.remove(MAN);
        check("manifeste manquant", has(BuildValidator.validate(f), "manquant"));

        // faux positifs : commentaires/chaînes ne déclenchent rien
        f = copy(good()); f.put(J + "MainActivity.java", f.get(J + "MainActivity.java").replace("// { commentaire", "// R.string.fantome case R.id.x onBackPressed() List"));
        check("commentaires ignorés " + BuildValidator.validate(f), BuildValidator.validate(f).isEmpty());

        // ressources de bibliothèque
        f = copy(good()); f.put("app/src/main/res/layout/activity_main.xml", f.get("app/src/main/res/layout/activity_main.xml")
            .replace("android:fitsSystemWindows=\"true\"", "android:fitsSystemWindows=\"true\" android:background=\"?attr/colorPrimary\" android:foreground=\"@drawable/abc_ab_share_pack_mtrl_alpha\" style=\"@style/Widget.MaterialComponents.Button\""));
        check("ressources lib acceptées " + BuildValidator.validate(f), BuildValidator.validate(f).isEmpty());

        // limite de problèmes
        f = copy(good());
        StringBuilder many = new StringBuilder("package com.krux.generated;\npublic class Many { void f() {\n");
        for (int i = 0; i < 30; i++) many.append("int a").append(i).append(" = R.string.nope").append(i).append(";\n");
        many.append("} }\n"); f.put(J + "Many.java", many.toString());
        check("liste plafonnée", BuildValidator.validate(f).size() <= BuildValidator.MAX_PROBLEMS + 1);

        System.out.println((total - fails) + "/" + total + " tests réussis");
        if (fails > 0) System.exit(1);
    }
}
