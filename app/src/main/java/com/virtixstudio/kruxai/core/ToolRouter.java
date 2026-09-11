package com.virtixstudio.kruxai.core;

import java.util.Locale;

public final class ToolRouter {

    public enum Tool {
        NONE,
        WEB_SEARCH,
        CALCULATOR,
        CLOCK,
        MEMORY,
        GITHUB
    }

    private ToolRouter() {}

    public static Tool decide(String prompt, boolean webEnabled) {
        if (prompt == null) return Tool.NONE;

        String p = prompt.trim().toLowerCase(Locale.ROOT);
        if (p.isEmpty()) return Tool.NONE;

        if (isMemoryRequest(p)) return Tool.MEMORY;
        if (isGithubRequest(p)) return Tool.GITHUB;
        if (isClockRequest(p)) return Tool.CLOCK;
        if (isCalculatorRequest(p)) return Tool.CALCULATOR;

        if (webEnabled && isWebRequest(p)) {
            return Tool.WEB_SEARCH;
        }

        return Tool.NONE;
    }

    private static boolean isMemoryRequest(String p) {
        return p.contains("tu te souviens")
                || p.contains("souviens-toi")
                || p.contains("souvenir")
                || p.contains("mémoire")
                || p.contains("memorise")
                || p.contains("mémorise")
                || p.contains("qu'est-ce que tu sais sur moi");
    }

    private static boolean isGithubRequest(String p) {
        return p.contains("github")
                || p.contains("mon dépôt")
                || p.contains("mon repo")
                || p.contains("repository")
                || p.contains("pull request")
                || p.contains("commit");
    }

    private static boolean isClockRequest(String p) {
        return p.contains("quelle heure")
                || p.contains("heure à")
                || p.contains("heure dans")
                || p.contains("heure actuelle")
                || p.contains("horloge")
                || p.contains("timezone")
                || p.contains("fuseau horaire");
    }

    private static boolean isCalculatorRequest(String p) {
        return p.matches(".*\\d+\\s*[+\\-*/x×÷]\\s*\\d+.*")
                || p.startsWith("calcule ")
                || p.startsWith("calcul ")
                || p.startsWith("combien font ")
                || p.startsWith("résous ")
                || p.startsWith("solve ");
    }

    private static boolean isWebRequest(String p) {
        return p.contains("dernière")
                || p.contains("dernier")
                || p.contains("aujourd'hui")
                || p.contains("actualités")
                || p.contains("actualité")
                || p.contains("news")
                || p.contains("récent")
                || p.contains("récente")
                || p.contains("actuellement")
                || p.contains("en ce moment")
                || p.contains("version actuelle")
                || p.contains("dernière version")
                || p.contains("sur internet")
                || p.contains("sur le web")
                || p.contains("cherche sur internet")
                || p.contains("sources");
    }
}
