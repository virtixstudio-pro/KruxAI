package com.clav.keyboard.ime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lightweight offline suggester: prefix matching over {@link Dictionary},
 * frequency-ordered, case-aware (capitalizes when the user did).
 */
public class WordSuggester {

    public List<String> suggest(String prefix, int max) {
        List<String> suggestions = new ArrayList<>();
        if (prefix == null || prefix.length() < 2) {
            return suggestions;
        }
        String lower = prefix.toLowerCase(Locale.FRENCH);
        boolean capitalized = Character.isUpperCase(prefix.charAt(0));
        for (String word : Dictionary.WORDS) {
            if (word.startsWith(lower) && word.length() > lower.length()) {
                suggestions.add(capitalized
                        ? Character.toUpperCase(word.charAt(0)) + word.substring(1)
                        : word);
                if (suggestions.size() >= max) {
                    break;
                }
            }
        }
        return suggestions;
    }
}
