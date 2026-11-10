package com.virtixstudio.kruxai.utils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Choisit les souvenirs à donner à KRUX. Principe : la mémoire est courte, donc on lui donne TOUT ce qui tient dans un budget
 * (plus de « 3 souvenirs au hasard »). Les plus liés à la question passent en premier, puis les plus récents.
 */
public final class MemorySelector {

    private MemorySelector() { }

    private static String fold(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    /** facts : du plus ancien au plus récent (ordre de la base). */
    public static List<String> select(String prompt, List<String> facts, int budgetChars, int maxFacts) {
        List<String> out = new ArrayList<>();
        if (facts == null || facts.isEmpty()) return out;
        List<String> words = new ArrayList<>();
        for (String t : fold(prompt).split("[^\\p{L}\\p{N}]+")) if (t.length() >= 4 && !words.contains(t)) words.add(t);

        final int n = facts.size();
        final int[] score = new int[n];
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String f = fold(facts.get(i));
            int s = 0;
            for (String w : words) if (f.contains(w)) s++;
            score[i] = s;
            order.add(i);
        }
        // pertinence d'abord, puis le plus récent
        order.sort((a, b) -> score[a] != score[b] ? Integer.compare(score[b], score[a]) : Integer.compare(b, a));

        int used = 0;
        List<String> seen = new ArrayList<>();
        for (int idx : order) {
            String f = facts.get(idx);
            if (f == null) continue;
            f = f.trim();
            if (f.isEmpty()) continue;
            if (f.length() > 350) f = f.substring(0, 347) + "...";
            String key = fold(f);
            if (seen.contains(key)) continue;
            if (used + f.length() + 3 > budgetChars || out.size() >= maxFacts) continue;
            seen.add(key);
            out.add(f);
            used += f.length() + 3;
        }
        return out;
    }
}
