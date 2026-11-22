package com.virtixstudio.kruxai.ui;

import android.text.Spannable;
import android.text.style.ForegroundColorSpan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Coloration syntaxique légère (Java, Kotlin, JS/TS, Python, HTML/CSS simple) pour Krux Code. */
final class CodeHighlighter {
    private CodeHighlighter() { }

    static final int KEYWORD = 0xFFC792EA;
    static final int STRING = 0xFFC3E88D;
    static final int NUMBER = 0xFFF78C6C;
    static final int COMMENT = 0xFF6B7280;

    private static final String KEYWORDS = "abstract|as|async|await|boolean|break|case|catch|class|const|continue|def|default|do|else|enum|export|extends|false|final|finally|for|from|fun|function|if|implements|import|in|int|interface|is|lambda|let|new|null|of|override|package|private|protected|public|return|static|String|super|switch|this|throw|throws|true|try|val|var|void|when|while|with|yield";

    private static final Pattern TOKEN = Pattern.compile(
            "(//.*|/\\*.*?\\*/|^\\s*#\\s.*)"
            + "|(\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|`(?:\\\\.|[^`\\\\])*`)"
            + "|\\b(\\d+(?:\\.\\d+)?)\\b"
            + "|\\b(" + KEYWORDS + ")\\b");

    /** Colore la partie [from, to) d'un texte sur une seule ligne. */
    static void apply(Spannable s, int from, int to) {
        if (to <= from) return;
        Matcher m = TOKEN.matcher(s.subSequence(from, to));
        while (m.find()) {
            int color;
            if (m.group(1) != null) color = COMMENT;
            else if (m.group(2) != null) color = STRING;
            else if (m.group(3) != null) color = NUMBER;
            else color = KEYWORD;
            s.setSpan(new ForegroundColorSpan(color), from + m.start(), from + m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
}
