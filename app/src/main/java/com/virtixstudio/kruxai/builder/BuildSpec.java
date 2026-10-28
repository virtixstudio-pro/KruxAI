package com.virtixstudio.kruxai.builder;

import java.util.regex.Pattern;

/**
 * Paramètres de génération des applications créées par KRUX (versions, limites, reprises).
 * Java pur : aucune dépendance Android, donc testable sur n'importe quel JDK.
 * Les valeurs par défaut ci-dessous sont celles de krux_config.json : si le fichier est absent
 * ou contient une valeur invalide, KRUX retombe sur ces valeurs sûres.
 */
public final class BuildSpec {

    // --- Chaîne de build (vérifiée le 2026-10-25, voir krux_config.json pour les sources) ---
    public String agp = "8.13.0";
    public String gradle = "8.13";
    public int jdk = 17;
    public int compileSdk = 36;
    public int targetSdk = 36;
    public int minSdk = 24;
    public String buildTools = "35.0.0";

    // --- Bibliothèques autorisées dans les applications générées ---
    public String appcompat = "1.7.0";
    public String material = "1.12.0";
    public String recyclerview = "1.3.2";
    public String constraintlayout = "2.1.4";

    // --- Limites ---
    public int maxFixes = 2;          // corrections après un build GitHub en échec
    public int maxLocalFixes = 2;     // corrections demandées à l'IA avant d'envoyer le code
    public int maxFiles = 25;
    public int maxFileChars = 15000;
    public int maxTotalChars = 60000;

    // --- Reprise sur erreur ---
    public int generateRetries = 2;   // nouvelles tentatives si l'IA échoue (réseau, quota, JSON coupé)
    public int githubRetries = 4;     // nouvelles tentatives pour les appels GitHub (429, 403 limite, 5xx)

    public static final String NAMESPACE = "com.krux.generated";

    private static final Pattern VERSION = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){1,3}");

    public static boolean isVersion(String s) {
        return s != null && VERSION.matcher(s).matches();
    }

    /** Vrai si l'ensemble est cohérent. Utilisé pour refuser une configuration défectueuse. */
    public boolean isValid() {
        return isVersion(agp) && isVersion(gradle) && isVersion(buildTools)
                && isVersion(appcompat) && isVersion(material) && isVersion(recyclerview) && isVersion(constraintlayout)
                && jdk >= 17 && jdk <= 25
                && minSdk >= 21 && minSdk <= 36
                && compileSdk >= 34 && compileSdk <= 40
                && targetSdk >= minSdk && targetSdk <= compileSdk
                && maxFixes >= 0 && maxFixes <= 5
                && maxLocalFixes >= 0 && maxLocalFixes <= 4
                && maxFiles >= 5 && maxFiles <= 60
                && maxFileChars >= 2000 && maxFileChars <= 40000
                && maxTotalChars >= maxFileChars && maxTotalChars <= 200000
                && generateRetries >= 0 && generateRetries <= 5
                && githubRetries >= 0 && githubRetries <= 8;
    }

    public BuildSpec copy() {
        BuildSpec s = new BuildSpec();
        s.agp = agp; s.gradle = gradle; s.jdk = jdk; s.compileSdk = compileSdk; s.targetSdk = targetSdk;
        s.minSdk = minSdk; s.buildTools = buildTools;
        s.appcompat = appcompat; s.material = material; s.recyclerview = recyclerview; s.constraintlayout = constraintlayout;
        s.maxFixes = maxFixes; s.maxLocalFixes = maxLocalFixes; s.maxFiles = maxFiles;
        s.maxFileChars = maxFileChars; s.maxTotalChars = maxTotalChars;
        s.generateRetries = generateRetries; s.githubRetries = githubRetries;
        return s;
    }
}
