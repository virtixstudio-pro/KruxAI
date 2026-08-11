package com.virtixstudio.kruxai.utils;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.TypefaceSpan;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkdownCodeFormatter {

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```([a-zA-Z0-9_]*)\\n?([\\s\\S]*?)```");

    public static CharSequence format(String text) {
        if (text == null) return "";

        SpannableStringBuilder builder = new SpannableStringBuilder();
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(text);

        int lastIndex = 0;
        while (matcher.find()) {
            // Ajouter le texte normal avant le bloc de code
            builder.append(text.substring(lastIndex, matcher.start()));

            String lang = matcher.group(1);
            String code = matcher.group(2);

            int blockStart = builder.length();

            // Titre du langage si présent
            if (lang != null && !lang.isEmpty()) {
                String header = "[" + lang.toUpperCase() + "]\n";
                int headerStart = builder.length();
                builder.append(header);
                builder.setSpan(new ForegroundColorSpan(Color.parseColor("#4CAF50")), headerStart, builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            // Le code
            int codeStart = builder.length();
            builder.append(code);
            int codeEnd = builder.length();

            // Style pour le bloc de code : Fond sombre + Police Monospace
            builder.setSpan(new BackgroundColorSpan(Color.parseColor("#1E1E1E")), blockStart, codeEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new ForegroundColorSpan(Color.parseColor("#00FFC6")), codeStart, codeEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new TypefaceSpan("monospace"), blockStart, codeEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            builder.append("\n");
            lastIndex = matcher.end();
        }

        // Ajouter le reste du texte
        if (lastIndex < text.length()) {
            builder.append(text.substring(lastIndex));
        }

        return builder;
    }
}
