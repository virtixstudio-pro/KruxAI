package com.virtixstudio.kruxai.builder;

import java.util.*;

public final class RelevantFiles {

    private static final Set<String> STOP = new HashSet<>(Arrays.asList(
        "avec", "dans", "pour", "sans", "plus", "moins",
        "cette", "faire", "ajoute", "ajouter", "modifie",
        "modifier", "change", "changer", "application",
        "fichier", "code", "projet", "the", "and", "for",
        "with", "this", "that", "into"
    ));

    private RelevantFiles() {}

    private static List<String> words(String value) {
        List<String> result = new ArrayList<>();
        if (value == null) return result;

        for (String word : value.toLowerCase(Locale.ROOT)
                .split("[^\\p{L}\\p{N}_]+")) {
            if (word.length() >= 3 && !STOP.contains(word)
                    && !result.contains(word)) {
                result.add(word);
            }
        }
        return result;
    }

    public static Map<String, String> select(
            Map<String, String> files,
            String request,
            int maxFiles,
            int maxChars) {

        Map<String, String> selected = new LinkedHashMap<>();
        if (files == null || maxFiles <= 0 || maxChars <= 0) {
            return selected;
        }

        List<String> terms = words(request);
        List<Map.Entry<String, String>> entries =
                new ArrayList<>(files.entrySet());

        entries.sort((a, b) -> Integer.compare(
                score(b, terms), score(a, terms)));

        int remaining = maxChars;

        for (Map.Entry<String, String> entry : entries) {
            if (selected.size() >= maxFiles || remaining <= 0) break;

            String content = entry.getValue();
            if (content == null) continue;

            int relevance = score(entry, terms);

            if (!entries.isEmpty() && score(entries.get(0), terms) > 0
                    && relevance == 0) break;

            if (content.length() > remaining) continue;

            selected.put(entry.getKey(), content);
            remaining -= content.length();
        }

        return selected;
    }

    private static int score(
            Map.Entry<String, String> entry,
            List<String> terms) {

        String path = entry.getKey().toLowerCase(Locale.ROOT);
        String content = entry.getValue() == null
                ? ""
                : entry.getValue().toLowerCase(Locale.ROOT);

        int score = 0;

        for (String term : terms) {
            if (path.contains(term)) score += 8;

            int index = 0;
            int count = 0;

            while ((index = content.indexOf(term, index)) >= 0
                    && count < 3) {
                score++;
                count++;
                index += term.length();
            }
        }

        return score;
    }
}
