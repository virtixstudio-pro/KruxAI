package com.virtixstudio.kruxai;

import com.virtixstudio.kruxai.builder.BuildCheckpoint;
import com.virtixstudio.kruxai.builder.PreviewHtml;
import com.virtixstudio.kruxai.builder.BuildSpec;
import com.virtixstudio.kruxai.builder.BuildTemplates;
import com.virtixstudio.kruxai.builder.BuildValidator;
import com.virtixstudio.kruxai.builder.WebBuilder;
import com.virtixstudio.kruxai.builder.WebTemplates;
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
        /** Un fichier vient d'être envoyé (corrigé = vrai s'il a été réécrit après une erreur). */
        default void onFile(String path, int bytes, boolean fixed) {}
        /** Montre la maquette (HTML déjà nettoyé) ; vrai = continuer la création, faux = annuler. */
        default boolean confirmPreview(String html) { return true; }
    }

    /** Refus volontaire de l'IA ou de la politique : inutile de réessayer. */
    static class Refusal extends Exception {
        Refusal(String m) { super(m); }
    }

    // ------------------------------------------------------------------ prompts

    static String systemPrompt(BuildSpec s) {
        return "Tu génères une application Android native (Java, vues XML, AppCompat). "
            + "Réponds UNIQUEMENT avec un objet JSON, sans texte autour ni balises markdown, au format : "
            + "{\"name\":\"nom-court-en-minuscules\",\"preview\":\"<maquette HTML>\",\"files\":[{\"path\":\"app/src/main/...\",\"content\":\"...\"}]}. "
            + "MAQUETTE (champ preview) : une page HTML INTERACTIVE qui montre l'application telle qu'elle sera : tous les écrans principaux "
            + "(mêmes textes, mêmes couleurs, 360 px de large), un seul visible à la fois, avec une navigation qui fonctionne "
            + "(barre du bas, onglets ou boutons) grâce à un court JavaScript pur dans une balise <script>. "
            + "Style moderne façon Material 3 : coins arrondis, ombres douces, espacements généreux, police système, transitions légères, "
            + "données d'exemple réalistes. 9000 caractères maximum, CSS dans une balise <style>, aucune bibliothèque. "
            + "INTERDIT dans la maquette : réseau (fetch, XMLHttpRequest, WebSocket), stockage (localStorage, cookie), location, window.open, eval, "
            + "AUCUNE image externe, AUCUN lien externe. "
            + "RÈGLES DU PROJET : package obligatoire " + BuildSpec.NAMESPACE + " (ligne package en première instruction de chaque .java, "
            + "TOUS les .java dans app/src/main/java/com/krux/generated/, aucun sous-dossier) ; "
            + "chemins uniquement sous app/src/main/ (AndroidManifest.xml, java/com/krux/generated/*.java, res/<dossier>/<fichier>.xml, assets/) ; "
            + "dans res/, uniquement des fichiers .xml aux noms en minuscules/chiffres/_ (pas d'image png/jpg : dessine avec des vector drawables XML ou des formes XML) ; "
            + "l'activité principale s'appelle MainActivity, étend AppCompatActivity, et a un intent-filter MAIN + LAUNCHER ; "
            + "dans le manifeste : PAS d'attribut package, android:exported=\"true\" ou \"false\" sur CHAQUE activité, "
            + "et pas d'android:icon (ni roundIcon) sauf si tu crées la ressource ; "
            + "dépendances autorisées : AppCompat, Material Components (qui apporte aussi CardView, CoordinatorLayout, DrawerLayout, ViewPager2 et Transition : "
            + "utilise-les librement pour un rendu professionnel), RecyclerView, ConstraintLayout, org.json et le SDK Android uniquement "
            + "(pas de Glide, Picasso, Retrofit, Gson, Kotlin, Compose, Room, Firebase, bibliothèque de graphiques…) ; "
            + "GRAPHIQUES, courbes, jauges et schémas scientifiques : dessine-les toi-même dans une vue personnalisée (Canvas, Paint, Path) avec axes, graduations et unités ; "
            + "calculs avec java.lang.Math et java.math.BigDecimal, arrondis maîtrisés, saisies validées ; "
            + "ne fournis AUCUN fichier gradle ni workflow (déjà fournis) ; "
            + "thème défini dans res/values/themes.xml (parent Theme.Material3.DayNight.NoActionBar pour un rendu moderne) ; "
            + "écris tous les import nécessaires ; "
            + "n'utilise JAMAIS switch/case sur R.id.xxx (ne compile plus) : utilise des if/else ; "
            + "n'utilise pas onBackPressed() : utilise getOnBackPressedDispatcher().addCallback(...) ; "
            + "l'affichage est bord à bord (targetSdk " + s.targetSdk + ") : mets android:fitsSystemWindows=\"true\" sur la vue racine de chaque écran ; "
            + "chaque ressource (@string, @color, @drawable, @id, @style, R.xxx) utilisée doit être définie dans tes fichiers ; "
            + "code COMPLET qui compile, sans TODO, sans \"...\" ni commentaire du type \"reste du code\". "
            + "AMBITION : fais une application complète, soignée et distinctive, pas une démo : plusieurs écrans avec une navigation claire "
            + "(BottomNavigationView, TabLayout ou ViewPager2 de Material), vraie logique métier, données conservées (SharedPreferences ou SQLiteOpenHelper), "
            + "états vides et messages d'erreur utiles, validation des saisies, thème clair et sombre, textes réels (jamais de lorem ipsum), "
            + "une identité visuelle cohérente (couleurs, formes, espacements) et au moins une fonction originale qui la distingue d'un modèle générique. "
            + "Réseau possible avec HttpURLConnection et org.json si la demande l'exige (permission INTERNET). "
            + "LIMITES : " + s.maxFiles + " fichiers maximum, "
            + s.maxFileChars + " caractères maximum par fichier et " + s.maxTotalChars + " au total. "
            + "PERMISSIONS AUTORISÉES : INTERNET, ACCESS_NETWORK_STATE, VIBRATE, POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM, "
            + "ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION, CAMERA, READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_AUDIO, "
            + "READ_EXTERNAL_STORAGE, RECORD_AUDIO, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK, WAKE_LOCK. Ne déclare que celles dont l'app a besoin et demande les permissions sensibles à l'exécution "
            + "(registerForActivityResult), avec une explication à l'écran et un comportement correct en cas de refus. "
            + "Alarmes et notifications : AlarmManager et NotificationManager, avec un BroadcastReceiver déclaré android:exported=\"false\". "
            + "Services (musique, minuteur, suivi) et providers autorisés s'ils sont déclarés android:exported=\"false\" ; avec un service au premier plan, déclare FOREGROUND_SERVICE et un type. "
            + "INTERDIT : toute autre permission ; WorkManager et JobScheduler (bibliothèque absente) ; démarrage au démarrage de l'appareil ; "
            + "contacts, SMS et journal d'appels ; "
            + "exécution de commandes ; chargement de code dynamique ; imitation d'une banque, d'une marque ou d'un écran de connexion ; "
            + "surveillance cachée de personnes, collecte de données personnelles à leur insu, contenu adulte, jeux d'argent avec de l'argent réel. Les publicités sont permises si elles n'exigent aucune bibliothèque. "
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
        "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE", "android.permission.VIBRATE",
        "android.permission.POST_NOTIFICATIONS", "android.permission.SCHEDULE_EXACT_ALARM",
        "android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.CAMERA", "android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO",
        "android.permission.READ_MEDIA_AUDIO", "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.RECORD_AUDIO", "android.permission.FOREGROUND_SERVICE",
        "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK", "android.permission.WAKE_LOCK"));

    static final String[] BANNED_CODE = {
        "Runtime.getRuntime", "ProcessBuilder", "DexClassLoader", "PathClassLoader", "InMemoryDexClassLoader",
        "AccessibilityService", "DevicePolicyManager", "DeviceAdminReceiver", "SmsManager", "TelephonyManager",
        "System.loadLibrary", "System.load(", "TYPE_APPLICATION_OVERLAY", "SYSTEM_ALERT_WINDOW",
        "Class.forName", "getDeclaredMethod", "getMethod(", "setAccessible", "BOOT_COMPLETED",
        "WorkManager", "JobScheduler", "READ_CONTACTS", "ContactsContract", "content://sms", "content://contacts"};

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
            Matcher rc = Pattern.compile("<receiver(?=[\\s/>])([^>]*)>").matcher(mm);
            while (rc.find())
                if (!rc.group(1).matches("(?s).*android:exported\\s*=\\s*\"false\".*"))
                    throw new Refusal("Application refusée : un récepteur doit être déclaré avec android:exported=\"false\"");
            for (String tag : new String[]{"service", "provider"}) {
                Matcher sv = Pattern.compile("<" + tag + "(?=[\\s/>])([^>]*)>").matcher(mm);
                while (sv.find())
                    if (!sv.group(1).matches("(?s).*android:exported\\s*=\\s*\"false\".*"))
                        throw new Refusal("Application refusée : un " + tag + " doit être déclaré avec android:exported=\"false\"");
            }
            if (mm.contains("BOOT_COMPLETED"))
                throw new Refusal("Application refusée : démarrage automatique non autorisé");
            if (mm.contains("<uses-permission-sdk-23") || mm.contains("<queries"))
                throw new Refusal("Application refusée : déclaration non autorisée dans le manifeste");
        }
    }

    static void checkWebPolicy(Map<String, String> files, BuildSpec s) throws Exception {
        String problem = WebBuilder.policyProblem(files, s);
        if (problem != null) throw new Refusal(problem);
    }

    // ------------------------------------------------------------------ lecture de la réponse IA

    static class Parsed { String name = "app"; String preview = ""; Map<String, String> files = new LinkedHashMap<>(); }

    /** Extrait l'objet JSON même entouré de texte ou de balises ```json. */
    static String extractJson(String raw) throws Exception {
        if (raw == null) throw new Exception("Réponse IA vide");
        String t = raw.trim().replaceAll("(?s)^```[a-zA-Z]*\\s*", "").replaceAll("(?s)```\\s*$", "");
        int a = t.indexOf('{'), b = t.lastIndexOf('}');
        if (a < 0 || b <= a) throw new Exception("Réponse IA sans JSON (réponse coupée ?)");
        return t.substring(a, b + 1);
    }

    /** Réponse coupée en plein milieu : on garde ce qui est complet (partial) et on demandera la suite. */
    static class Truncated extends Exception {
        final Parsed partial;
        Truncated(Parsed partial) { super("réponse coupée après " + partial.files.size() + " fichier(s)"); this.partial = partial; }
    }

    static Parsed parse(String raw, boolean full, BuildSpec s, boolean web) throws Exception {
        JSONObject o;
        try {
            o = new JSONObject(extractJson(raw));
        } catch (Exception je) {
            Map<String, String> got = com.virtixstudio.kruxai.builder.TruncatedJson.files(raw);
            if (got.isEmpty()) {
                com.virtixstudio.kruxai.builder.BlockFiles.Result br = com.virtixstudio.kruxai.builder.BlockFiles.parse(raw);
                if (!br.files.isEmpty()) return fromBlocks(br, full, s, web);
                throw je;
            }
            Parsed part = new Parsed();
            if (full) {
                String n = com.virtixstudio.kruxai.builder.TruncatedJson.field(raw, "name");
                if (n != null) {
                    part.name = n.toLowerCase().replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-");
                    if (part.name.length() > 30) part.name = part.name.substring(0, 30);
                    part.name = part.name.replaceAll("^-|-$", "");
                    if (part.name.isEmpty()) part.name = "app";
                }
                String pv = com.virtixstudio.kruxai.builder.TruncatedJson.field(raw, "preview");
                if (pv != null) part.preview = pv;
            }
            for (Map.Entry<String, String> e : got.entrySet()) put(part, e.getKey(), e.getValue(), web);
            throw new Truncated(part);
        }
        if (o.has("refuse"))
            throw new Refusal("KRUX ne peut pas créer cette application : " + o.optString("refuse"));
        Parsed p = new Parsed();
        if (full) {
            p.name = o.optString("name", "app").toLowerCase().replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-");
            if (p.name.length() > 30) p.name = p.name.substring(0, 30);
            p.name = p.name.replaceAll("^-|-$", "");
            if (p.name.isEmpty()) p.name = "app";
        }
        if (full) p.preview = o.optString("preview", "");
        Object fo = o.get("files");
        if (fo instanceof JSONArray) {
            JSONArray arr = (JSONArray) fo;
            if (arr.length() > s.maxFiles)
                throw new Refusal("Application trop grosse : " + arr.length() + " fichiers (maximum " + s.maxFiles + ")");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject f = arr.getJSONObject(i);
                put(p, f.getString("path"), f.get("content"), web);
            }
        } else if (fo instanceof JSONObject) { // format {"chemin": "contenu"} accepté aussi
            JSONObject m = (JSONObject) fo;
            JSONArray names = m.names();
            for (int i = 0; names != null && i < names.length(); i++) put(p, names.getString(i), m.get(names.getString(i)), web);
        } else {
            throw new Exception("Champ files invalide");
        }
        if (p.files.isEmpty()) throw new Exception("Aucun fichier proposé");
        if (full && web && !p.files.containsKey(WebBuilder.ENTRY)) throw new Exception("src/main.tsx manquant");
        if (full && !web && !p.files.containsKey(BuildValidator.MANIFEST)) throw new Exception("AndroidManifest.xml manquant");
        return p;
    }

    /** Réponse en blocs "=== chemin ===" (ou ``` avec chemin) au lieu d'un JSON. Un dernier bloc coupé est écarté. */
    private static Parsed fromBlocks(com.virtixstudio.kruxai.builder.BlockFiles.Result br, boolean full, BuildSpec s, boolean web) throws Exception {
        if (br.files.size() > s.maxFiles)
            throw new Refusal("Application trop grosse : " + br.files.size() + " fichiers (maximum " + s.maxFiles + ")");
        Parsed p = new Parsed();
        if (full && br.name != null) {
            String n = br.name.toLowerCase().replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-");
            if (n.length() > 30) n = n.substring(0, 30);
            n = n.replaceAll("^-|-$", "");
            if (!n.isEmpty()) p.name = n;
        }
        for (Map.Entry<String, String> e : br.files.entrySet()) put(p, e.getKey(), e.getValue(), web);
        if (!br.complete) throw new Truncated(p);
        if (full && web && !p.files.containsKey(WebBuilder.ENTRY)) throw new Exception("src/main.tsx manquant");
        if (full && !web && !p.files.containsKey(BuildValidator.MANIFEST)) throw new Exception("AndroidManifest.xml manquant");
        return p;
    }

    private static void put(Parsed p, String path, Object content, boolean web) throws Exception {
        String path2 = path == null ? "" : path.trim().replace('\\', '/');
        if (web) path2 = path2.replaceFirst("^\\./", "");
        String problem = web ? WebBuilder.pathProblem(path2) : BuildValidator.pathProblem(path2);
        if (problem != null) throw new Exception(problem);
        if (!(content instanceof String)) throw new Exception("Contenu invalide pour " + path2);
        p.files.put(path2, (String) content);
    }

    // ------------------------------------------------------------------ génération avec reprises

    /** Reçoit les fichiers déjà écrits dès qu'une réponse est coupée (pour les garder si tout s'arrête). */
    interface PartialSink { void keep(Parsed partial); }

    static Parsed generateParsed(Generator gen, String system, String prompt, boolean full, BuildSpec s, Listener l, boolean web) throws Exception {
        return generateParsed(gen, system, prompt, full, s, l, web, null, null);
    }

    /**
     * Appelle l'IA ; réessaie si le réseau, le quota ou le JSON coupé font échouer. Les refus ne sont pas réessayés.
     * Les fichiers déjà reçus (réponse coupée, ou "seed" retrouvé sur le disque) sont conservés d'un essai à l'autre :
     * on ne redemande que ceux qui manquent au lieu de tout recommencer.
     */
    static Parsed generateParsed(Generator gen, String system, String prompt, boolean full, BuildSpec s, Listener l, boolean web,
                                 Parsed seed, PartialSink sink) throws Exception {
        Exception last = null;
        StringBuilder why = new StringBuilder();
        String p = prompt;
        Parsed carry = (seed != null && !seed.files.isEmpty()) ? seed : null;
        for (int attempt = 0; attempt <= s.generateRetries; attempt++) {
            if (attempt > 0) {
                l.onStatus("Génération du code (nouvel essai " + (attempt + 1) + "/" + (s.generateRetries + 1) + ")…");
                Thread.sleep(1500L * attempt);
            }
            try {
                Parsed r;
                if (carry != null) {
                    r = completeTruncated(gen, system, prompt, carry, full, s, l, web, sink);
                } else {
                    try {
                        r = parse(gen.generate(system, p), full, s, web);
                    } catch (Truncated t) {
                        carry = t.partial;
                        if (sink != null) sink.keep(carry);
                        r = completeTruncated(gen, system, prompt, carry, full, s, l, web, sink);
                    }
                }
                checkRequired(r, full, web);
                if (full) { if (web) checkWebPolicy(r.files, s); else checkPolicy(r.files, s); }
                return r;
            } catch (Refusal r) {
                throw r;
            } catch (Exception e) {
                last = e;
                String msg = String.valueOf(e.getMessage());
                String shortMsg = msg.length() > 90 ? msg.substring(0, 90) + "…" : msg;
                if (why.length() > 0) why.append(" | ");
                why.append(attempt + 1).append(") ").append(shortMsg);
                p = prompt + "\n\nATTENTION : ta réponse précédente était inutilisable (" + msg
                    + "). Réponds avec UN SEUL objet JSON valide et complet, sans texte autour. "
                    + "Si l'échappement JSON te pose problème, réponds plutôt par blocs : pour chaque fichier une ligne "
                    + "=== chemin/du/fichier === puis le code brut (sans JSON, sans échappement), et termine par la ligne === FIN ===.";
            }
        }
        throw new Exception("L'IA n'a pas produit de code exploitable : " + (last == null ? "?" : last.getMessage())
            + (why.length() > 0 && s.generateRetries > 0 ? " [essais : " + why + "]" : ""));
    }

    /** Après une reprise, la liste de fichiers doit contenir le point d'entrée obligatoire. */
    private static void checkRequired(Parsed r, boolean full, boolean web) throws Exception {
        if (!full) return;
        if (web && !r.files.containsKey(WebBuilder.ENTRY)) throw new Exception("src/main.tsx manquant");
        if (!web && !r.files.containsKey(BuildValidator.MANIFEST)) throw new Exception("AndroidManifest.xml manquant");
    }

    /** La réponse a été coupée : on redemande uniquement les fichiers qui manquent, jusqu'à 3 fois, et on assemble. */
    static Parsed completeTruncated(Generator gen, String system, String prompt, Parsed partial, boolean full,
                                    BuildSpec s, Listener l, boolean web) throws Exception {
        return completeTruncated(gen, system, prompt, partial, full, s, l, web, null);
    }

    static Parsed completeTruncated(Generator gen, String system, String prompt, Parsed partial, boolean full,
                                    BuildSpec s, Listener l, boolean web, PartialSink sink) throws Exception {
        for (int round = 0; round < 3; round++) {
            l.onStatus("La réponse de l'IA était coupée, je demande la suite (" + (round + 1) + "/3)…");
            StringBuilder have = new StringBuilder();
            for (String path : partial.files.keySet()) have.append("\n- ").append(path);
            String p2 = prompt + "\n\nATTENTION : ta réponse précédente a été COUPÉE avant la fin. Fichiers déjà reçus (complets) :" + have
                + "\nRéponds avec UN SEUL objet JSON {\"files\":[{\"path\":\"…\",\"content\":\"…\"}]} contenant UNIQUEMENT les fichiers qui manquent encore, "
                + "sans répéter ceux déjà reçus. Reste COMPACT : code court, peu de commentaires, 3 ou 4 fichiers par réponse au maximum.";
            String raw = gen.generate(system, p2);
            try {
                Parsed more = parse(raw, false, s, web);
                partial.files.putAll(more.files);
                if (sink != null) sink.keep(partial);
                return partial;
            } catch (Truncated t2) {
                int before = partial.files.size();
                partial.files.putAll(t2.partial.files);
                if (sink != null) sink.keep(partial);
                if (partial.files.size() == before) throw new Exception("réponse coupée sans rien de nouveau");
            }
        }
        return partial;
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
    static Map<String, String> applyFix(Map<String, String> current, Map<String, String> fix, BuildSpec s, boolean web) throws Exception {
        Map<String, String> merged = new LinkedHashMap<>(current);
        merged.putAll(fix);
        Map<String, String> norm = web ? WebBuilder.normalize(merged) : BuildValidator.normalize(merged, new ArrayList<String>());
        if (web) checkWebPolicy(norm, s); else checkPolicy(norm, s);
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
        run(githubToken, description, outDir, null, gen, l);
    }

    /** modifyFrom != null : modification d'une app existante (dossier de son APK) ; description = la modification demandée. */
    public static void run(final String githubToken, final String description, final File outDir, final File modifyFrom,
                           final Generator gen, final Listener l) {
        new Thread(() -> {
            try {
                final BuildSpec s = KruxConfig.build();
                boolean web = s.web; // nouvelle app : réglage de krux_config.json ; reprise ou modification : mode de l'app existante

                String name;
                Map<String, String> current;
                String metaDesc = description;
                BuildCheckpoint.Saved saved = modifyFrom != null ? null : BuildCheckpoint.load(outDir, description, System.currentTimeMillis());
                if (modifyFrom != null) {
                    BuildCheckpoint.Saved src = BuildCheckpoint.loadAny(modifyFrom);
                    if (src == null) throw new Refusal("Le code de cette app n'est plus sur ce téléphone : impossible de la modifier.");
                    String before = readMeta(modifyFrom, "description");
                    l.onStatus("Génération du code : j'applique ta modification…");
                    name = src.name;
                    current = new LinkedHashMap<>(src.files);
                    web = current.containsKey(WebBuilder.ENTRY);
                    Parsed fx = generateParsed(gen, web ? WebBuilder.modifySystemPrompt(s) : modifySystemPrompt(s),
                        buildModifyPrompt(before, description, current), false, s, l, web);
                    applyFix(current, fx.files, s, web);
                    l.onStatus("Vérification du code…");
                    localFixes(gen, s, description, current, l, null, web);
                    metaDesc = before.isEmpty() ? description : before + "\nMODIFICATION : " + description;
                } else if (saved != null) {
                    l.onStatus("Reprise : le code déjà écrit est retrouvé…");
                    name = saved.name;
                    current = new LinkedHashMap<>(saved.files);
                    web = current.containsKey(WebBuilder.ENTRY);
                } else {
                    l.onStatus("Génération du code…");
                    com.virtixstudio.kruxai.builder.DesignDirection direction = com.virtixstudio.kruxai.builder.DesignDirection.pick(new java.util.Random());
                    String design = web ? direction.toWebPrompt() : direction.toPrompt();
                    final File partialDir = new File(outDir, "partial");
                    BuildCheckpoint.Saved part = BuildCheckpoint.load(partialDir, description, System.currentTimeMillis());
                    Parsed seed = null;
                    if (part != null && !part.files.isEmpty()) {
                        seed = new Parsed();
                        seed.name = part.name;
                        seed.files.putAll(part.files);
                        l.onStatus("Reprise : " + seed.files.size() + " fichier(s) déjà écrit(s) retrouvé(s), je demande la suite…");
                    }
                    Parsed p = generateParsed(gen, (web ? WebBuilder.systemPrompt(s) : systemPrompt(s)) + " " + design, description, true, s, l, web,
                        seed, pp -> BuildCheckpoint.save(partialDir, description, pp.name, pp.files, System.currentTimeMillis()));
                    BuildCheckpoint.clear(partialDir);

                    // Réparations automatiques puis vérification locale (aucun quota GitHub utilisé)
                    l.onStatus("Vérification du code…");
                    current = web ? WebBuilder.normalize(p.files) : BuildValidator.normalize(p.files, new ArrayList<String>());
                    if (web) checkWebPolicy(current, s); else checkPolicy(current, s);
                    localFixes(gen, s, description, current, l, direction.accentHex(), web);
                    name = p.name;
                    BuildCheckpoint.save(outDir, description, name, current, System.currentTimeMillis());
                    String previewHtml = PreviewHtml.prepare(p.preview);
                    if (!previewHtml.isEmpty() && !l.confirmPreview(previewHtml)) {
                        BuildCheckpoint.clear(outDir);
                        throw new Refusal("Création annulée après l'aperçu. Rien n'a été envoyé et ton quota n'est pas utilisé.");
                    }
                }

                deliver(githubToken, metaDesc, name, outDir, current, s, gen, l, web);
            } catch (Exception e) {
                String m = e.getMessage();
                l.onError(m == null || m.isEmpty() ? e.getClass().getSimpleName() : m);
            }
        }).start();
    }

    // ------------------------------------------------------------------ modification d'une app existante

    static String modifySystemPrompt(BuildSpec s) {
        String fix = fixSystemPrompt(s);
        return "Tu modifies une application Android existante (Java, XML) selon la demande de l'utilisateur. "
            + "On te donne la description d'origine, la modification demandée et les fichiers actuels. "
            + "Applique la modification demandée et rien d'autre : garde l'identité visuelle, les couleurs et la navigation existantes sauf si la demande les change. "
            + fix.substring(fix.indexOf("Réponds UNIQUEMENT"));
    }

    static String buildModifyPrompt(String before, String request, Map<String, String> current) {
        StringBuilder sb = new StringBuilder();
        sb.append("DESCRIPTION D'ORIGINE : ").append(before.isEmpty() ? "(inconnue)" : before)
          .append("\n\nMODIFICATION DEMANDÉE : ").append(request)
          .append("\n\nFICHIERS ACTUELS :\n");
        int budget = 60000;
        StringBuilder skipped = new StringBuilder();
        for (Map.Entry<String, String> e : current.entrySet()) {
            if (e.getValue().length() > budget) { skipped.append("\n- ").append(e.getKey()); continue; }
            sb.append("=== ").append(e.getKey()).append(" ===\n").append(e.getValue()).append("\n");
            budget -= e.getValue().length();
        }
        if (skipped.length() > 0) sb.append("\nFICHIERS NON MONTRÉS (trop longs, ne les réécris pas) :").append(skipped);
        return sb.toString();
    }

    static void writeMeta(File dir, String description, String name, String appId) {
        try {
            JSONObject o = new JSONObject().put("description", description).put("name", name).put("appId", appId);
            try (java.io.FileOutputStream os = new java.io.FileOutputStream(new File(dir, "krux-app.json"))) {
                os.write(o.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) { }
    }

    static String readMeta(File dir, String key) {
        try {
            File f = new File(dir, "krux-app.json");
            if (!f.isFile()) return "";
            byte[] b = new byte[(int) Math.min(f.length(), 200000)];
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) { in.read(b); }
            return new JSONObject(new String(b, java.nio.charset.StandardCharsets.UTF_8)).optString(key, "");
        } catch (Exception e) {
            return "";
        }
    }

    /** Vérification locale puis corrections par l'IA (aucun quota GitHub utilisé). */
    /** Vrai si la couleur d'accent imposée apparaît dans au moins un fichier (couleurs, thème ou écrans). */
    static boolean usesColor(Map<String, String> files, String hex) {
        if (hex == null) return true;
        for (Map.Entry<String, String> e : files.entrySet())
            if (e.getKey().contains("/res/") && e.getValue().toUpperCase(java.util.Locale.ROOT).contains(hex)) return true;
        return false;
    }

    /** accent != null : la couleur d'accent de la direction visuelle imposée doit être réellement utilisée dans res/. */
    static void localFixes(Generator gen, BuildSpec s, String description, Map<String, String> current, Listener l, String accent, boolean web) throws Exception {
        for (int round = 0; round < s.maxLocalFixes; round++) {
            List<String> problems = new ArrayList<>(web ? WebBuilder.validate(current) : BuildValidator.validate(current));
            if (web && !WebBuilder.usesColor(current, accent))
                problems.add("DIRECTION VISUELLE : la couleur d'accent imposée " + accent + " n'est utilisée dans aucun fichier src/. "
                    + "Définis la palette imposée en variables CSS dans src/styles.css (--primary, --secondary, --surface) et utilise-la dans les écrans.");
            if (!web && !usesColor(current, accent))
                problems.add("DIRECTION VISUELLE : la couleur d'accent imposée " + accent + " n'est utilisée dans aucun fichier res/. "
                    + "Définis la palette imposée dans res/values/colors.xml, applique-la dans themes.xml (colorPrimary, colorSecondary, colorSurface) "
                    + "et utilise ces couleurs dans les écrans, à la place du violet par défaut.");
            if (problems.isEmpty()) break;
            l.onStatus("Vérification du code : correction " + (round + 1) + "/" + s.maxLocalFixes + "…");
            try {
                Parsed fx = generateParsed(gen, web ? WebBuilder.fixSystemPrompt(s) : fixSystemPrompt(s),
                    buildFixPrompt(description, current, BuildValidator.format(problems)), false, s, l, web);
                applyFix(current, fx.files, s, web);
            } catch (Refusal r) {
                throw r;
            } catch (Exception e) {
                break; // vérification locale = aide, pas un blocage : le build GitHub tranchera
            }
        }
    }

    /** Envoi sur GitHub, compilation, corrections automatiques, téléchargement de l'APK. */
    private static void deliver(String githubToken, String description, String name, File outDir,
                                Map<String, String> current, BuildSpec s, Generator gen, Listener l, boolean web) throws Exception {
                GithubClient gh = new GithubClient(githubToken, s.githubRetries);
                gh.init();
                String repo = "krux-" + name + "-" + (System.currentTimeMillis() / 1000 % 100000);
                l.onStatus("Création du repo " + repo + "…");
                gh.createRepo(repo);
                String branch = gh.defaultBranch(repo);
                String url = "https://github.com/" + gh.owner + "/" + repo;

                final String appId = BuildTemplates.uniqueAppId(name);
                Map<String, String> all = new LinkedHashMap<>(BuildTemplates.gradleFiles(s, appId));
                if (web) {
                    all.putAll(WebTemplates.shellFiles(name.replace('-', ' ')));
                    all.put(".github/workflows/build.yml", WebTemplates.workflow(s));
                } else {
                    all.put(".github/workflows/build.yml", BuildTemplates.workflow(s));
                }
                all.putAll(current);
                int n = 0;
                for (Map.Entry<String, String> e : all.entrySet()) {
                    l.onStatus("Envoi " + (++n) + "/" + all.size() + " : " + e.getKey());
                    gh.putFile(repo, e.getKey(), e.getValue());
                    l.onFile(e.getKey(), e.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8).length, false);
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
                        BuildCheckpoint.clear(outDir);
                        // Code source compressé (quelques Ko) rangé à côté de l'APK, pour pouvoir reconstruire l'app.
                        BuildCheckpoint.save(new File(outDir, repo), description, name, current, System.currentTimeMillis());
                        writeMeta(new File(outDir, repo), description, name, appId);
                        l.onDone(apk, url);
                        return;
                    }
                    String conclusion = run.optString("conclusion");
                    if ("cancelled".equals(conclusion) || "timed_out".equals(conclusion))
                        throw new Exception("Le build a été interrompu (" + conclusion + "). Réessaie dans un instant.");
                    if (fixes >= s.maxFixes)
                        throw new Exception("Build toujours en échec après " + s.maxFixes + " corrections. Dernière erreur : "
                            + shorten(lastLog, 400));

                    fixes++;
                    l.onStatus("Build échoué : KRUX lit l'erreur et corrige (" + fixes + "/" + s.maxFixes + ")…");
                    String log = gh.failureLog(repo, run.getLong("id"));
                    lastLog = log;
                    Parsed fx = generateParsed(gen, web ? WebBuilder.fixSystemPrompt(s) : fixSystemPrompt(s),
                        buildFixPrompt(description, current, log), false, s, l, web);
                    Map<String, String> changed = applyFix(current, fx.files, s, web);
                    for (Map.Entry<String, String> e : changed.entrySet()) {
                        l.onStatus("Correction : " + e.getKey());
                        gh.putFile(repo, e.getKey(), e.getValue());
                        l.onFile(e.getKey(), e.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8).length, true);
                    }
                }
    }

    static String shorten(String t, int max) {
        if (t == null) return "";
        String x = t.replaceAll("\\s+", " ").trim();
        return x.length() <= max ? x : x.substring(0, max) + "…";
    }
}
