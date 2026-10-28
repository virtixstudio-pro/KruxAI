package com.virtixstudio.kruxai.core;

import android.content.Context;

import com.virtixstudio.kruxai.builder.BuildSpec;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Configuration universelle de KRUX : un seul fichier (assets/krux_config.json) pour les versions,
 * les limites et la politique de reprise sur erreur.
 *
 * Règles de sécurité :
 *  - le fichier ne contient JAMAIS de clé d'API (seulement des noms et des versions) ;
 *  - si le fichier est absent, illisible ou incohérent, on garde les valeurs par défaut de BuildSpec ;
 *  - une valeur invalide est ignorée individuellement, elle ne casse pas le reste.
 */
public final class KruxConfig {

    private static volatile BuildSpec build = new BuildSpec();
    private static volatile String loadedFrom = "valeurs par défaut";

    private KruxConfig() { }

    public static BuildSpec build() { return build; }

    public static String source() { return loadedFrom; }

    /** À appeler une fois avant d'utiliser le builder. Sans danger si appelé plusieurs fois. */
    public static synchronized void init(Context ctx) {
        try (InputStream in = ctx.getApplicationContext().getAssets().open("krux_config.json")) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            String text = new String(bo.toByteArray(), StandardCharsets.UTF_8);
            BuildSpec s = parse(new JSONObject(text));
            build = s;
            loadedFrom = "assets/krux_config.json";
        } catch (Exception e) {
            build = new BuildSpec();
            loadedFrom = "valeurs par défaut (" + e.getClass().getSimpleName() + ")";
        }
    }

    /** Lit le JSON ; chaque champ invalide est ignoré, puis l'ensemble est revérifié. */
    static BuildSpec parse(JSONObject root) {
        BuildSpec s = new BuildSpec();
        JSONObject b = root.optJSONObject("build");
        if (b != null) {
            s.agp = ver(b, "agp", s.agp);
            s.gradle = ver(b, "gradle", s.gradle);
            s.buildTools = ver(b, "build_tools", s.buildTools);
            s.jdk = num(b, "jdk", s.jdk);
            s.compileSdk = num(b, "compile_sdk", s.compileSdk);
            s.targetSdk = num(b, "target_sdk", s.targetSdk);
            s.minSdk = num(b, "min_sdk", s.minSdk);
            JSONObject libs = b.optJSONObject("libs");
            if (libs != null) {
                s.appcompat = ver(libs, "appcompat", s.appcompat);
                s.material = ver(libs, "material", s.material);
                s.recyclerview = ver(libs, "recyclerview", s.recyclerview);
                s.constraintlayout = ver(libs, "constraintlayout", s.constraintlayout);
            }
        }
        JSONObject l = root.optJSONObject("limits");
        if (l != null) {
            s.maxFixes = num(l, "max_fixes", s.maxFixes);
            s.maxLocalFixes = num(l, "max_local_fixes", s.maxLocalFixes);
            s.maxFiles = num(l, "max_files", s.maxFiles);
            s.maxFileChars = num(l, "max_file_chars", s.maxFileChars);
            s.maxTotalChars = num(l, "max_total_chars", s.maxTotalChars);
        }
        JSONObject r = root.optJSONObject("retries");
        if (r != null) {
            s.generateRetries = num(r, "generate", s.generateRetries);
            s.githubRetries = num(r, "github", s.githubRetries);
        }
        return s.isValid() ? s : new BuildSpec();
    }

    private static String ver(JSONObject o, String key, String def) {
        String v = o.optString(key, def);
        return BuildSpec.isVersion(v) ? v : def;
    }

    private static int num(JSONObject o, String key, int def) {
        return o.optInt(key, def);
    }
}
