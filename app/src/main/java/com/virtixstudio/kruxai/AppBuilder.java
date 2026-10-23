package com.virtixstudio.kruxai;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppBuilder {

    public static final int MAX_FIXES = 2;
    public static final int MAX_FILES = 25;
    public static final int MAX_FILE_CHARS = 15000;
    public static final int MAX_TOTAL_CHARS = 60000;

    public interface Generator { String generate(String system, String userPrompt) throws Exception; }

    public interface Listener {
        void onStatus(String status);
        void onDone(File apk, String repoUrl);
        void onError(String message);
    }

    public static final String SYSTEM =
        "Tu génères une application Android native (Java, vues XML, AppCompat). "
        + "Réponds UNIQUEMENT avec un objet JSON, sans texte autour ni balises markdown, au format : "
        + "{\"name\":\"nom-court-en-minuscules\",\"files\":[{\"path\":\"app/src/main/...\",\"content\":\"...\"}]}. "
        + "Règles : package obligatoire com.krux.generated ; chemins uniquement sous app/src/main/ "
        + "(AndroidManifest.xml, java/com/krux/generated/*.java, res/**) ; "
        + "l'activité principale s'appelle MainActivity avec intent-filter LAUNCHER ; "
        + "dépendances autorisées : appcompat, material, recyclerview, constraintlayout uniquement ; "
        + "ne fournis AUCUN fichier gradle ni workflow (déjà fournis) ; "
        + "thème défini dans res/values/themes.xml (parent Theme.MaterialComponents.DayNight.NoActionBar) ; "
        + "icônes en vector XML, pas d'emoji ; chaque ressource (@string, @color, @drawable, @id) utilisée doit exister ; "
        + "code complet qui compile, sans TODO. "
        + "LIMITES : application SIMPLE, 3 écrans maximum, " + MAX_FILES + " fichiers maximum, "
        + MAX_FILE_CHARS + " caractères maximum par fichier et " + MAX_TOTAL_CHARS + " au total. "
        + "INTERDIT : toute permission autre qu'INTERNET, ACCESS_NETWORK_STATE et VIBRATE ; services, receivers, providers ; "
        + "exécution de commandes ; chargement de code dynamique ; imitation d'une banque, d'une marque ou d'un écran de connexion ; "
        + "surveillance, collecte de données personnelles, contenu adulte, jeux d'argent, publicités. "
        + "Si la demande est interdite ou impossible dans ces limites, réponds UNIQUEMENT {\"refuse\":\"raison courte en français\"}.";

    public static final String FIX_SYSTEM =
        "Tu corriges une application Android (Java, XML) dont le build GitHub Actions a échoué. "
        + "On te donne la description de l'app, l'erreur réelle du build et les fichiers actuels. "
        + "Trouve la cause réelle de l'erreur et corrige-la sans réécrire ce qui fonctionne. "
        + "Réponds UNIQUEMENT avec un objet JSON, sans texte autour ni balises markdown : "
        + "{\"files\":[{\"path\":\"app/src/main/...\",\"content\":\"CONTENU COMPLET du fichier\"}]}. "
        + "Ne renvoie que les fichiers à créer ou remplacer, avec leur contenu complet (jamais de diff). "
        + "Mêmes règles : package com.krux.generated, chemins sous app/src/main/ uniquement, "
        + "dépendances limitées à appcompat, material, recyclerview, constraintlayout. "
        + "Respecte les mêmes limites de taille et les mêmes interdictions (permissions, services, code dangereux). "
        + "Vérifie que toute ressource référencée (@string, @color, @drawable, @id, R.xxx) existe vraiment.";

    static final Set<String> ALLOWED_PERMS = new HashSet<>(Arrays.asList(
        "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE", "android.permission.VIBRATE"));

    static final String[] BANNED_CODE = {
        "Runtime.getRuntime", "ProcessBuilder", "DexClassLoader", "PathClassLoader", "AccessibilityService",
        "DevicePolicyManager", "SmsManager", "TelephonyManager", "System.loadLibrary", "TYPE_APPLICATION_OVERLAY"};

    static void checkPolicy(Map<String, String> files) throws Exception {
        int total = 0;
        for (Map.Entry<String, String> e : files.entrySet()) {
            String c = e.getValue();
            if (c.length() > MAX_FILE_CHARS)
                throw new Exception("Application trop grosse : " + e.getKey() + " dépasse " + MAX_FILE_CHARS + " caractères");
            total += c.length();
            if (e.getKey().endsWith(".java")) {
                for (String b : BANNED_CODE)
                    if (c.contains(b)) throw new Exception("Application refusée : code non autorisé (" + b + ")");
            }
        }
        if (total > MAX_TOTAL_CHARS)
            throw new Exception("Application trop grosse : " + total + " caractères (maximum " + MAX_TOTAL_CHARS + ")");
        String m = files.get("app/src/main/AndroidManifest.xml");
        if (m != null) {
            Matcher mt = Pattern.compile("<uses-permission[^>]*android:name\\s*=\\s*\"([^\"]+)\"").matcher(m);
            while (mt.find())
                if (!ALLOWED_PERMS.contains(mt.group(1)))
                    throw new Exception("Application refusée : permission non autorisée (" + mt.group(1) + ")");
            if (m.contains("<service") || m.contains("<receiver") || m.contains("<provider") || m.contains("BOOT_COMPLETED"))
                throw new Exception("Application refusée : services et tâches de fond non autorisés");
        }
    }

    static class Parsed { String name = "app"; Map<String, String> files = new LinkedHashMap<>(); }

    static Parsed parse(String raw, boolean full) throws Exception {
        int a = raw.indexOf('{'), b = raw.lastIndexOf('}');
        if (a < 0 || b <= a) throw new Exception("Réponse IA sans JSON (réponse coupée ?)");
        JSONObject o = new JSONObject(raw.substring(a, b + 1));
        if (o.has("refuse"))
            throw new Exception("KRUX ne peut pas créer cette application : " + o.optString("refuse"));
        Parsed p = new Parsed();
        if (full) {
            p.name = o.optString("name", "app").toLowerCase().replaceAll("[^a-z0-9-]", "-");
            if (p.name.isEmpty() || p.name.equals("-")) p.name = "app";
        }
        JSONArray arr = o.getJSONArray("files");
        if (arr.length() == 0) throw new Exception("Aucun fichier proposé");
        if (arr.length() > MAX_FILES)
            throw new Exception("Application trop grosse : " + arr.length() + " fichiers (maximum " + MAX_FILES + ")");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject f = arr.getJSONObject(i);
            String path = f.getString("path");
            if (!path.matches("app/src/main/[A-Za-z0-9_./-]+") || path.contains(".."))
                throw new Exception("Chemin refusé : " + path);
            p.files.put(path, f.getString("content"));
        }
        if (full && !p.files.containsKey("app/src/main/AndroidManifest.xml"))
            throw new Exception("AndroidManifest.xml manquant");
        if (full) checkPolicy(p.files);
        return p;
    }

    static Map<String, String> templateFiles() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("settings.gradle",
            "pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }\n"
            + "dependencyResolutionManagement { repositories { google(); mavenCentral() } }\n"
            + "rootProject.name = \"GeneratedApp\"\ninclude ':app'\n");
        m.put("build.gradle", "plugins { id 'com.android.application' version '8.5.0' apply false }\n");
        m.put("gradle.properties", "org.gradle.jvmargs=-Xmx2g\nandroid.useAndroidX=true\n");
        m.put("app/build.gradle",
            "plugins { id 'com.android.application' }\n"
            + "android {\n namespace 'com.krux.generated'\n compileSdk 34\n"
            + " defaultConfig { applicationId 'com.krux.generated'; minSdk 24; targetSdk 34; versionCode 1; versionName '1.0' }\n"
            + " compileOptions { sourceCompatibility JavaVersion.VERSION_17; targetCompatibility JavaVersion.VERSION_17 }\n}\n"
            + "dependencies {\n implementation 'androidx.appcompat:appcompat:1.7.0'\n"
            + " implementation 'com.google.android.material:material:1.12.0'\n"
            + " implementation 'androidx.recyclerview:recyclerview:1.3.2'\n"
            + " implementation 'androidx.constraintlayout:constraintlayout:2.1.4'\n}\n");
        return m;
    }

    static final String WORKFLOW =
        "name: Build APK\non:\n  workflow_dispatch:\njobs:\n  build:\n    runs-on: ubuntu-latest\n    steps:\n"
        + "      - uses: actions/checkout@v4\n"
        + "      - uses: actions/setup-java@v4\n        with:\n          distribution: temurin\n          java-version: 17\n"
        + "      - uses: gradle/actions/setup-gradle@v4\n        with:\n          gradle-version: 8.7\n"
        + "      - run: gradle assembleDebug --no-daemon\n"
        + "      - uses: actions/upload-artifact@v4\n        with:\n          name: app-debug\n"
        + "          path: app/build/outputs/apk/debug/app-debug.apk\n"
        + "          retention-days: 1\n";

    static String buildFixPrompt(String description, Map<String, String> current, String log) {
        StringBuilder sb = new StringBuilder();
        sb.append("DESCRIPTION DE L'APP : ").append(description)
          .append("\n\nERREUR DU BUILD :\n").append(log)
          .append("\n\nFICHIERS ACTUELS :\n");
        int budget = 26000;
        for (int pass = 0; pass < 2; pass++) {
            for (Map.Entry<String, String> e : current.entrySet()) {
                String base = e.getKey().substring(e.getKey().lastIndexOf('/') + 1);
                boolean mentioned = log.contains(base);
                if ((pass == 0) != mentioned) continue;
                if (e.getValue().length() > budget) continue;
                sb.append("=== ").append(e.getKey()).append(" ===\n").append(e.getValue()).append("\n");
                budget -= e.getValue().length();
            }
        }
        return sb.toString();
    }

    public static void run(final String githubToken, final String description, final File outDir,
                           final Generator gen, final Listener l) {
        new Thread(() -> {
            try {
                l.onStatus("Génération du code…");
                Parsed p = parse(gen.generate(SYSTEM, description), true);

                GithubClient gh = new GithubClient(githubToken);
                gh.init();
                String repo = "krux-" + p.name + "-" + (System.currentTimeMillis() / 1000 % 100000);
                l.onStatus("Création du repo " + repo + "…");
                gh.createRepo(repo);
                String branch = gh.defaultBranch(repo);
                String url = "https://github.com/" + gh.owner + "/" + repo;

                Map<String, String> current = new LinkedHashMap<>(p.files);
                Map<String, String> all = new LinkedHashMap<>(templateFiles());
                all.put(".github/workflows/build.yml", WORKFLOW);
                all.putAll(current);
                int n = 0;
                for (Map.Entry<String, String> e : all.entrySet()) {
                    l.onStatus("Envoi " + (++n) + "/" + all.size() + " : " + e.getKey());
                    gh.putFile(repo, e.getKey(), e.getValue());
                }

                int fixes = 0;
                while (true) {
                    l.onStatus(fixes == 0
                        ? "Compilation sur GitHub (2 à 5 min)…"
                        : "Nouvelle compilation après correction " + fixes + "/" + MAX_FIXES + "…");
                    long prev = gh.latestRunId(repo);
                    gh.dispatch(repo, branch);
                    JSONObject run = gh.waitRun(repo, prev);

                    if ("success".equals(run.optString("conclusion"))) {
                        l.onStatus("Téléchargement de l'APK…");
                        File apk = gh.downloadApk(repo, run.getLong("id"), new File(outDir, repo));
                        l.onDone(apk, url);
                        return;
                    }
                    if (fixes >= MAX_FIXES)
                        throw new Exception("Build toujours en échec après " + MAX_FIXES + " corrections.");

                    fixes++;
                    l.onStatus("Build échoué : KRUX lit l'erreur et corrige (" + fixes + "/" + MAX_FIXES + ")…");
                    String log = gh.failureLog(repo, run.getLong("id"));
                    Parsed fx = parse(gen.generate(FIX_SYSTEM, buildFixPrompt(description, current, log)), false);
                    Map<String, String> merged = new LinkedHashMap<>(current);
                    merged.putAll(fx.files);
                    checkPolicy(merged);
                    for (Map.Entry<String, String> e : fx.files.entrySet()) {
                        l.onStatus("Correction : " + e.getKey());
                        gh.putFile(repo, e.getKey(), e.getValue());
                        current.put(e.getKey(), e.getValue());
                    }
                }
            } catch (Exception e) {
                l.onError(e.getMessage());
            }
        }).start();
    }
}
