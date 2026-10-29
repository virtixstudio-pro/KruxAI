package com.virtixstudio.kruxai;

import com.virtixstudio.kruxai.builder.BuildSpec;
import com.virtixstudio.kruxai.builder.BuildTemplates;
import com.virtixstudio.kruxai.builder.BuildValidator;
import com.virtixstudio.kruxai.core.KruxConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pipeline "KRUX crée une application" :
 * IA -> JSON de fichiers -> politique de sécurité -> réparations automatiques + vérification locale
 * (corrections IA si besoin) -> repo GitHub privé -> build GitHub Actions -> corrections sur erreur réelle -> APK.
 *
 * Versions, limites et reprises viennent de assets/krux_config.json (voir KruxConfig / BuildSpec).
 */
public class AppBuilder {

    public interface Generator { String generate(String system, String userPrompt) throws Exception; }

    public interface Listener {
        void onStatus(String status);
        void onDone(File apk, String repoUrl);
        void onError(String message);
    }

    /** Refus volontaire de l'IA ou de la politique : inutile de réessayer. */
    static class Refusal extends Exception {
        Refusal(String m) { super(m); }
    }

    // ------------------------------------------------------------------ prompts

    static String systemPrompt(BuildSpec s) {
        return "Tu génères une application Android native (Java, vues XML, AppCompat). "
            + "Réponds UNIQUEMENT avec un objet JSON, sans texte autour ni balises markdown, au format : "
            + "{\"name\":\"nom-court-en-minuscules\",\"files\":[{\"path\":\"app/src/main/...\",\"content\":\"...\"}]}. "
            + "RÈGLES DU PROJET : package obligatoire " + BuildSpec.NAMESPACE + " (ligne package en première instruction de chaque .java, "
            + "TOUS les .java dans app/src/main/java/com/krux/generated/, aucun sous-dossier) ; "
            + "chemins uniquement sous app/src/main/ (AndroidManifest.xml, java/com/krux/generated/*.java, res/<dossier>/<fichier>.xml, assets/) ; "
            + "dans res/, uniquement des fichiers .xml aux noms en minuscules/chiffres/_ (pas d'image png/jpg : dessine avec des vector drawables XML ou des formes XML) ; "
            + "l'activité principale s'appelle MainActivity, étend AppCompatActivity, et a un intent-filter MAIN + LAUNCHER ; "
            + "dans le manifeste : PAS d'attribut package, android:exported=\"true\" ou \"false\" sur CHAQUE activité, "
            + "et pas d'android:icon (ni roundIcon) sauf si tu crées la ressource ; "
            + "dépendances autorisées : AppCompat, Material Components, RecyclerView, ConstraintLayout, org.json et le SDK Android uniquement "
            + "(pas de Glide, Picasso, Retrofit, Gson, Kotlin, Compose, Room, Firebase…) ; "
            + "ne fournis AUCUN fichier gradle ni workflow (déjà fournis) ; "
            + "thème défini dans res/values/themes.xml (parent Theme.MaterialComponents.DayNight.NoActionBar) ; "
            + "écris tous les import nécessaires ; "
            + "n'utilise JAMAIS switch/case sur R.id.xxx (ne compile plus) : utilise des if/else ; "
            + "n'utilise pas onBackPressed() : utilise getOnBackPressedDispatcher().addCallback(...) ; "
            + "l'affichage est bord à bord (targetSdk " + s.targetSdk + ") : mets android:fitsSystemWindows=\"true\" sur la vue racine de chaque écran ; "
            + "chaque ressource (@string, @color, @drawable, @id, @style, R.xxx) utilisée doit être définie dans tes fichiers ; "
            + "code COMPLET qui compile, sans TODO, sans \"...\" ni commentaire du type \"reste du code\". "
            + "LIMITES : application SIMPLE, 3 écrans maximum, " + s.maxFiles + " fichiers maximum, "
            + s.maxFileChars + " caractères maximum par fichier et " + s.maxTotalChars + " au total. "
            + "INTERDIT : toute permission autre qu'INTERNET, ACCESS_NETWORK_STATE et VIBRATE ; services, receivers, providers ; "
            + "exécution de commandes ; chargement de code dynamique ; imitation d'une banque, d'une marque ou d'un écran de connexion ; "
            + "surveillance, collecte de données personnelles, contenu adulte, jeux d'argent, publicités. "
            + "Si la demande est interdite ou impossible dans ces limites, réponds UNIQUEMENT {\"refuse\":\"raison courte en français\"}.";
    }

    static String fixSystemPrompt(BuildSpec s) {
        return "Tu corriges une application Android (Java, XML) qui ne compile pas. "
            + "On te donne la description de l'app, la liste des problèmes (détectés avant l'envoi ou lus dans le log réel du build) "
            + "et les fichiers actuels. "
            + "Trouve la cause réelle et corrige-la sans réécrire ce qui fonctionne. "
            + "Réponds UNIQUEMENT avec un objet JSON, sans texte autour ni balises markdown : "
            + "{\"files\":[{\"path\":\"app/src/main/...\",\"content\":\"CONTENU COMPLET du fichier\"}]}. "
            + "Ne renvoie que les fichiers à créer ou remplacer, avec leur contenu complet (jamais de diff, jamais de \"...\"). "
            + "Mêmes règles : package " + BuildSpec.NAMESPACE + ", tous les .java dans app/src/main/java/com/krux/generated/, "
            + "chemins sous app/src/main/ uniquement, res/ en .xml seulement, "
            + "dépendances limitées à AppCompat, Material, RecyclerView, ConstraintLayout, org.json et le SDK Android. "
            + "Pas de switch/case sur R.id, pas d'onBackPressed(), android:exported sur chaque activité, pas d'attribut package dans le manifeste, tous les import écrits. "
            + "Respecte les mêmes limites de taille (" + s.maxFileChars + " caractères par fichier) et les mêmes interdictions (permissions, services, code dangereux). "
            + "Vérifie que toute ressource référencée (@string, @color, @drawable, @id, R.xxx) existe vraiment.";
    }

    // ------------------------------------------------------------------ politique de sécurité (bloquante)

    static final Set<String> ALLOWED_PERMS = new HashSet<>(Arrays.asList(
        "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE", "android.permission.VIBRATE"));

    static final String[] BANNED_CODE = {
        "Runtime.getRuntime", "ProcessBuilder", "DexClassLoader", "PathClassLoader", "InMemoryDexClassLoader",
        "AccessibilityService", "DevicePolicyManager", "DeviceAdminReceiver", "SmsManager", "TelephonyManager",
        "System.loadLibrary", "System.load(", "TYPE_APPLICATION_OVERLAY", "SYSTEM_ALERT_WINDOW",
        "Class.forName", "getDeclaredMethod", "getMethod(", "setAccessible", "BOOT_COMPLETED",
        "WorkManager", "AlarmManager", "JobScheduler", "ContentResolver", "READ_CONTACTS", "ACCESS_FINE_LOCATION"};

    /** Retire commentaires et chaînes pour que la détection ne soit pas contournée ni déclenchée à tort. */
    static String codeOnly(String src) {
        return src.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }

    static void checkPolicy(Map<String, String> files, BuildSpec s) throws Exception {
        if (files.size() > s.maxFiles)
            throw new Refusal("Application trop grosse : " + files.size() + " fichiers (maximum " + s.maxFiles + ")");
        int total = 0;
        for (Map.Entry<String, String> e : files.entrySet()) {
            String c = e.getValue();
            if (c.length() > s.maxFileChars)
                throw new Refusal("Application trop grosse : " + e.getKey() + " dépasse " + s.maxFileChars + " caractères");
            total += c.length();
            if (e.getKey().endsWith(".java")) {
                String code = codeOnly(c);
                for (String b : BANNED_CODE)
                    if (code.contains(b)) throw new Refusal("Application refusée : code non autorisé (" + b + ")");
            }
        }
        if (total > s.maxTotalChars)
            throw new Refusal("Application trop grosse : " + total + " caractères (maximum " + s.maxTotalChars + ")");
        String m = files.get(BuildValidator.MANIFEST);
        if (m != null) {
            String mm = m.replaceAll("(?s)<!--.*?-->", " ");
            Matcher mt = Pattern.compile("<uses-permission[^>]*android:name\\s*=\\s*\"([^\"]+)\"").matcher(mm);
            while (mt.find())
                if (!ALLOWED_PERMS.contains(mt.group(1)))
                    throw new Refusal("Application refusée : permission non autorisée (" + mt.group(1) + ")");
            if (mm.contains("<service") || mm.contains("<receiver") || mm.contains("<provider") || mm.contains("BOOT_COMPLETED"))
                throw new Refusal("Application refusée : services et tâches de fond non autorisés");
            if (mm.contains("<uses-permission-sdk-23") || mm.contains("<queries"))
                throw new Refusal("Application refusée : déclaration non autorisée dans le manifeste");
        }
    }

    // ------------------------------------------------------------------ lecture de la réponse IA

    static class Parsed { String name = "app"; Map<String, String> files = new LinkedHashMap<>(); }

    /** Extrait l'objet JSON même entouré de texte ou de balises ```json. */
    static String extractJson(String raw) throws Exception {
        if (raw == null) throw new Exception("Réponse IA vide");
        String t = raw.trim().replaceAll("(?s)^```[a-zA-Z]*\\s*", "").replaceAll("(?s)```\\s*$", "");
        int a = t.indexOf('{'), b = t.lastIndexOf('}');
        if (a < 0 || b <= a) throw new Exception("Réponse IA sans JSON (réponse coupée ?)");
        return t.substring(a, b + 1);
    }

    static Parsed parse(String raw, boolean full, BuildSpec s) throws Exception {
        JSONObject o = new JSONObject(extractJson(raw));
        if (o.has("refuse"))
            throw new Refusal("KRUX ne peut pas créer cette application : " + o.optString("refuse"));
        Parsed p = new Parsed();
        if (full) {
            p.name = o.optString("name", "app").toLowerCase().replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-");
            if (p.name.length() > 30) p.name = p.name.substring(0, 30);
            p.name = p.name.replaceAll("^-|-$", "");
            if (p.name.isEmpty()) p.name = "app";
        }
        Object fo = o.get("files");
        if (fo instanceof JSONArray) {
            JSONArray arr = (JSONArray) fo;
            if (arr.length() > s.maxFiles)
                throw new Refusal("Application trop grosse : " + arr.length() + " fichiers (maximum " + s.maxFiles + ")");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject f = arr.getJSONObject(i);
                put(p, f.getString("path"), f.get("content"));
            }
        } else if (fo instanceof JSONObject) { // format {"chemin": "contenu"} accepté aussi
            JSONObject m = (JSONObject) fo;
            JSONArray names = m.names();
            for (int i = 0; names != null && i < names.length(); i++) put(p, names.getString(i), m.get(names.getString(i)));
        } else {
            throw new Exception("Champ files invalide");
        }
        if (p.files.isEmpty()) throw new Exception("Aucun fichier proposé");
        if (full && !p.files.containsKey(BuildValidator.MANIFEST)) throw new Exception("AndroidManifest.xml manquant");
        return p;
    }

    private static void put(Parsed p, String path, Object content) throws Exception {
        String path2 = path == null ? "" : path.trim().replace('\\', '/');
        String problem = BuildValidator.pathProblem(path2);
        if (problem != null) throw new Exception(problem);
        if (!(content instanceof String)) throw new Exception("Contenu invalide pour " + path2);
        p.files.put(path2, (String) content);
    }

    // ------------------------------------------------------------------ génération avec reprises

    /** Appelle l'IA ; réessaie si le réseau, le quota ou le JSON coupé font échouer. Les refus ne sont pas réessayés. */
    static Parsed generateParsed(Generator gen, String system, String prompt, boolean full, BuildSpec s, Listener l) throws Exception {
        Exception last = null;
        String p = prompt;
        for (int attempt = 0; attempt <= s.generateRetries; attempt++) {
            if (attempt > 0) {
                l.onStatus("Génération du code (nouvel essai " + (attempt + 1) + "/" + (s.generateRetries + 1) + ")…");
                Thread.sleep(1500L * attempt);
            }
            try {
                Parsed r = parse(gen.generate(system, p), full, s);
                if (full) checkPolicy(r.files, s);
                return r;
            } catch (Refusal r) {
                throw r;
            } catch (Exception e) {
                last = e;
                String msg = String.valueOf(e.getMessage());
                p = prompt + "\n\nATTENTION : ta réponse précédente était inutilisable (" + msg
                    + "). Réponds avec UN SEUL objet JSON valide et complet, sans texte autour.";
            }
        }
        throw new Exception("L'IA n'a pas produit de code exploitable : " + (last == null ? "?" : last.getMessage()));
    }

    // ------------------------------------------------------------------ prompts de correction

    static String buildFixPrompt(String description, Map<String, String> current, String log) {
        StringBuilder sb = new StringBuilder();
        sb.append("DESCRIPTION DE L'APP : ").append(description)
          .append("\n\nPROBLÈMES À CORRIGER :\n").append(log)
          .append("\n\nFICHIERS ACTUELS :\n");
        int budget = 30000;
        for (int pass = 0; pass < 2; pass++) {
            for (Map.Entry<String, String> e : current.entrySet()) {
                String base = e.getKey().substring(e.getKey().lastIndexOf('/') + 1);
                boolean mentioned = log.contains(base) || log.contains(e.getKey());
                if ((pass == 0) != mentioned) continue;
                if (e.getValue().length() > budget) continue;
                sb.append("=== ").append(e.getKey()).append(" ===\n").append(e.getValue()).append("\n");
                budget -= e.getValue().length();
            }
        }
        return sb.toString();
    }

    /** Applique une correction : fusionne, répare automatiquement, revérifie la politique. Retourne les fichiers modifiés. */
    static Map<String, String> applyFix(Map<String, String> current, Map<String, String> fix, BuildSpec s) throws Exception {
        Map<String, String> merged = new LinkedHashMap<>(current);
        merged.putAll(fix);
        Map<String, String> norm = BuildValidator.normalize(merged, new ArrayList<String>());
        checkPolicy(norm, s);
        Map<String, String> changed = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : norm.entrySet()) {
            String old = current.get(e.getKey());
            if (old == null || !old.equals(e.getValue())) changed.put(e.getKey(), e.getValue());
        }
        current.clear();
        current.putAll(norm);
        return changed;
    }

    // ------------------------------------------------------------------ pipeline

    public static void run(final String githubToken, final String description, final File outDir,
                           final Generator gen, final Listener l) {
        new Thread(() -> {
            try {
                final BuildSpec s = KruxConfig.build();

                l.onStatus("Génération du code…");
                Parsed p = generateParsed(gen, systemPrompt(s), description, true, s, l);

                // Réparations automatiques puis vérification locale (aucun quota GitHub utilisé)
                l.onStatus("Vérification du code…");
                Map<String, String> current = BuildValidator.normalize(p.files, new ArrayList<String>());
                checkPolicy(current, s);
                for (int round = 0; round < s.maxLocalFixes; round++) {
                    List<String> problems = BuildValidator.validate(current);
                    if (problems.isEmpty()) break;
                    l.onStatus("Vérification du code : correction " + (round + 1) + "/" + s.maxLocalFixes + "…");
                    try {
                        Parsed fx = generateParsed(gen, fixSystemPrompt(s),
                            buildFixPrompt(description, current, BuildValidator.format(problems)), false, s, l);
                        applyFix(current, fx.files, s);
                    } catch (Refusal r) {
                        throw r;
                    } catch (Exception e) {
                        break; // vérification locale = aide, pas un blocage : le build GitHub tranchera
                    }
                }

                GithubClient gh = new GithubClient(githubToken, s.githubRetries);
                gh.init();
                String repo = "krux-" + p.name + "-" + (System.currentTimeMillis() / 1000 % 100000);
                l.onStatus("Création du repo " + repo + "…");
                gh.createRepo(repo);
                String branch = gh.defaultBranch(repo);
                String url = "https://github.com/" + gh.owner + "/" + repo;

                Map<String, String> all = new LinkedHashMap<>(BuildTemplates.gradleFiles(s));
                all.put(".github/workflows/build.yml", BuildTemplates.workflow(s));
                all.putAll(current);
                int n = 0;
                for (Map.Entry<String, String> e : all.entrySet()) {
                    l.onStatus("Envoi " + (++n) + "/" + all.size() + " : " + e.getKey());
                    gh.putFile(repo, e.getKey(), e.getValue());
                }

                int fixes = 0;
                String lastLog = "";
                while (true) {
                    l.onStatus(fixes == 0
                        ? "Compilation sur GitHub (2 à 5 min)…"
                        : "Nouvelle compilation après correction " + fixes + "/" + s.maxFixes + "…");
                    long prev = gh.latestRunId(repo);
                    gh.dispatch(repo, branch);
                    JSONObject run = gh.waitRun(repo, prev);

                    if ("success".equals(run.optString("conclusion"))) {
                        l.onStatus("Téléchargement de l'APK…");
                        File apk = gh.downloadApk(repo, run.getLong("id"), new File(outDir, repo));
                        l.onDone(apk, url);
                        return;
                    }
                    String conclusion = run.optString("conclusion");
                    if ("cancelled".equals(conclusion) || "timed_out".equals(conclusion))
                        throw new Exception("Le build GitHub a été interrompu (" + conclusion + "). Réessaie dans un instant.");
                    if (fixes >= s.maxFixes)
                        throw new Exception("Build toujours en échec après " + s.maxFixes + " corrections. Dernière erreur : "
                            + shorten(lastLog, 400));

                    fixes++;
                    l.onStatus("Build échoué : KRUX lit l'erreur et corrige (" + fixes + "/" + s.maxFixes + ")…");
                    String log = gh.failureLog(repo, run.getLong("id"));
                    lastLog = log;
                    Parsed fx = generateParsed(gen, fixSystemPrompt(s), buildFixPrompt(description, current, log), false, s, l);
                    Map<String, String> changed = applyFix(current, fx.files, s);
                    for (Map.Entry<String, String> e : changed.entrySet()) {
                        l.onStatus("Correction : " + e.getKey());
                        gh.putFile(repo, e.getKey(), e.getValue());
                    }
                }
            } catch (Exception e) {
                String m = e.getMessage();
                l.onError(m == null || m.isEmpty() ? e.getClass().getSimpleName() : m);
            }
        }).start();
    }

    static String shorten(String t, int max) {
        if (t == null) return "";
        String x = t.replaceAll("\\s+", " ").trim();
        return x.length() <= max ? x : x.substring(0, max) + "…";
    }
}
