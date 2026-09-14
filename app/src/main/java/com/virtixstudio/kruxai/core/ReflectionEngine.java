package com.virtixstudio.kruxai.core;

import java.util.Locale;

/**
 * Couche de détection obligatoire avant l'exécution d'une requête.
 *
 * Cette couche ne génère pas la réponse.
 * Elle détermine ce que Krux doit faire avant de transmettre
 * la demande au modèle ou à un outil.
 */
public final class ReflectionEngine {

    private ReflectionEngine() {
    }

    public static ReflectionResult analyze(String prompt, boolean webEnabled) {

        if (prompt == null || prompt.trim().isEmpty()) {
            return new ReflectionResult(
                    ReflectionResult.Intent.CHAT,
                    ToolRouter.Tool.NONE,
                    1.0,
                    false,
                    false,
                    false,
                    false
            );
        }

        String p = prompt.trim().toLowerCase(Locale.ROOT);

        ToolRouter.Tool tool = ToolRouter.decide(p, webEnabled);

        // Outils déterministes prioritaires.
        if (tool == ToolRouter.Tool.MEMORY) {
            return result(
                    ReflectionResult.Intent.MEMORY,
                    tool,
                    0.98,
                    false,
                    false,
                    false,
                    false
            );
        }

        if (tool == ToolRouter.Tool.GITHUB) {
            return result(
                    ReflectionResult.Intent.GITHUB,
                    tool,
                    0.96,
                    false,
                    true,
                    looksLikeCode(p),
                    true
            );
        }

        if (tool == ToolRouter.Tool.CLOCK) {
            return result(
                    ReflectionResult.Intent.CLOCK,
                    tool,
                    0.99,
                    false,
                    false,
                    false,
                    false
            );
        }

        if (tool == ToolRouter.Tool.CALCULATOR) {
            return result(
                    ReflectionResult.Intent.CALCULATION,
                    tool,
                    0.99,
                    false,
                    false,
                    false,
                    false
            );
        }

        if (tool == ToolRouter.Tool.WEB_SEARCH) {
            return result(
                    ReflectionResult.Intent.WEB_RESEARCH,
                    tool,
                    0.95,
                    true,
                    false,
                    false,
                    false
            );
        }

        // Construction de projet / application / site.
        if (containsAny(p,
                "crée une application",
                "crée une app",
                "créer une application",
                "créer une app",
                "développe une application",
                "développe une app",
                "fais-moi une application",
                "fais moi une application",
                "crée un site",
                "créer un site",
                "développe un site",
                "crée un projet"
        )) {
            return result(
                    ReflectionResult.Intent.PROJECT_BUILD,
                    ToolRouter.Tool.NONE,
                    0.94,
                    false,
                    false,
                    true,
                    true
            );
        }

        // Comparaisons.
        if (containsAny(p,
                "compare ",
                "comparaison",
                "différence entre",
                "différences entre",
                "lequel est meilleur",
                "laquelle est meilleure",
                "vs ",
                " versus "
        )) {
            return result(
                    ReflectionResult.Intent.COMPARISON,
                    ToolRouter.Tool.NONE,
                    0.90,
                    webEnabled && containsAny(p,
                            "actuel",
                            "actuelle",
                            "aujourd'hui",
                            "récent",
                            "dernière version"
                    ),
                    false,
                    false,
                    true
            );
        }

        // Débogage.
        if (containsAny(p,
                "erreur",
                "error",
                "exception",
                "bug",
                "ça ne marche pas",
                "cela ne marche pas",
                "ne compile pas",
                "compilation échoue",
                "crash",
                "stacktrace",
                "stack trace",
                "corrige ce code",
                "debug"
        )) {
            return result(
                    ReflectionResult.Intent.CODE_DEBUG,
                    ToolRouter.Tool.NONE,
                    0.93,
                    false,
                    false,
                    true,
                    true
            );
        }

        // Génération de code.
        if (containsAny(p,
                "écris une fonction",
                "ecris une fonction",
                "écris du code",
                "ecris du code",
                "écris-moi du code",
                "génère du code",
                "genere du code",
                "code en java",
                "code en python",
                "code en javascript",
                "code android",
                "snippet",
                "implémente",
                "implemente"
        )) {
            return result(
                    ReflectionResult.Intent.CODE_GENERATION,
                    ToolRouter.Tool.NONE,
                    0.91,
                    false,
                    false,
                    true,
                    false
            );
        }

        // Analyse de fichiers.
        if (containsAny(p,
                "analyse ce fichier",
                "analyse le fichier",
                "analyse ce document",
                "analyse le document",
                "lis ce fichier",
                "regarde ce fichier",
                "dans ce fichier"
        )) {
            return result(
                    ReflectionResult.Intent.FILE_ANALYSIS,
                    ToolRouter.Tool.NONE,
                    0.92,
                    false,
                    false,
                    true,
                    true
            );
        }

        // Demandes manifestement multi-étapes.
        if (containsAny(p,
                "puis ",
                "ensuite ",
                "après ",
                "apres ",
                "et ensuite",
                "étape par étape",
                "etape par etape",
                "de a à z",
                "de a a z"
        )) {
            return result(
                    ReflectionResult.Intent.MULTI_STEP,
                    ToolRouter.Tool.NONE,
                    0.82,
                    false,
                    false,
                    looksLikeCode(p),
                    true
            );
        }

        // Détection code générale.
        if (looksLikeCode(p)) {
            return result(
                    ReflectionResult.Intent.CODE_GENERATION,
                    ToolRouter.Tool.NONE,
                    0.72,
                    false,
                    false,
                    true,
                    false
            );
        }

        // Par défaut : conversation générale.
        return result(
                ReflectionResult.Intent.CHAT,
                ToolRouter.Tool.NONE,
                0.70,
                false,
                false,
                false,
                false
        );
    }

    private static ReflectionResult result(
            ReflectionResult.Intent intent,
            ToolRouter.Tool tool,
            double confidence,
            boolean needsWeb,
            boolean needsGithub,
            boolean needsCode,
            boolean multiStep
    ) {
        return new ReflectionResult(
                intent,
                tool,
                confidence,
                needsWeb,
                needsGithub,
                needsCode,
                multiStep
        );
    }

    private static boolean looksLikeCode(String p) {
        return containsAny(p,
                "java",
                "kotlin",
                "python",
                "javascript",
                "typescript",
                "android",
                "xml",
                "json",
                "html",
                "css",
                "api",
                "sdk",
                "class ",
                "public static",
                "private ",
                "function ",
                "return ",
                "import ",
                "gradle"
        );
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }
}
