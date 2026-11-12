package com.clav.keyboard.ime;

import com.clav.keyboard.R;

import java.util.Arrays;
import java.util.List;

/**
 * Static, immutable layout definitions: AZERTY (default), QWERTY and the
 * symbols page. Key weights are relative within each row.
 */
public final class KeyboardLayouts {

    public static final String AZERTY = "azerty";
    public static final String QWERTY = "qwerty";

    private static final float SHIFT_WEIGHT = 1.5f;
    private static final float LANGUAGE_WEIGHT = 1.2f;
    private static final float MODE_WEIGHT = 1.2f;
    private static final float EMOJI_WEIGHT = 1.2f;
    private static final float SPACE_WEIGHT = 4.4f;
    private static final float ENTER_WEIGHT = 1.6f;

    private KeyboardLayouts() {
    }

    public static List<List<KeySpec>> letters(String language) {
        return AZERTY.equals(language) ? azerty() : qwerty();
    }

    public static List<List<KeySpec>> symbols() {
        return Arrays.asList(
                row(KeySpec.character("1"), KeySpec.character("2"), KeySpec.character("3"),
                        KeySpec.character("4"), KeySpec.character("5"), KeySpec.character("6"),
                        KeySpec.character("7"), KeySpec.character("8"), KeySpec.character("9"),
                        KeySpec.character("0")),
                row(KeySpec.character("-"), KeySpec.character("/"), KeySpec.character(":"),
                        KeySpec.character(";"), KeySpec.character("("), KeySpec.character(")"),
                        KeySpec.character("€"), KeySpec.character("&"), KeySpec.character("\"")),
                row(KeySpec.character("."), KeySpec.character(","), KeySpec.character("?"),
                        KeySpec.character("!"), KeySpec.character("'"), KeySpec.character("@"),
                        KeySpec.character("#"), KeySpec.character("+"),
                        KeySpec.function(KeySpec.Type.DELETE, "", R.drawable.ic_backspace, SHIFT_WEIGHT)),
                bottomRow());
    }

    private static List<List<KeySpec>> azerty() {
        return Arrays.asList(
                row(KeySpec.letter("a", "à", "â", "ä"),
                        KeySpec.letter("z"),
                        KeySpec.letter("e", "é", "è", "ê", "ë"),
                        KeySpec.letter("r"),
                        KeySpec.letter("t"),
                        KeySpec.letter("y"),
                        KeySpec.letter("u", "ù", "û", "ü"),
                        KeySpec.letter("i", "î", "ï"),
                        KeySpec.letter("o", "ô", "ö"),
                        KeySpec.letter("p")),
                row(KeySpec.letter("q"),
                        KeySpec.letter("s"),
                        KeySpec.letter("d"),
                        KeySpec.letter("f"),
                        KeySpec.letter("g"),
                        KeySpec.letter("h"),
                        KeySpec.letter("j"),
                        KeySpec.letter("k"),
                        KeySpec.letter("l"),
                        KeySpec.letter("m")),
                row(KeySpec.function(KeySpec.Type.SHIFT, "", R.drawable.ic_shift, SHIFT_WEIGHT),
                        KeySpec.letter("w"),
                        KeySpec.letter("x"),
                        KeySpec.letter("c", "ç"),
                        KeySpec.letter("v"),
                        KeySpec.letter("b"),
                        KeySpec.letter("n"),
                        KeySpec.character("'"),
                        KeySpec.function(KeySpec.Type.DELETE, "", R.drawable.ic_backspace, SHIFT_WEIGHT)),
                bottomRow());
    }

    private static List<List<KeySpec>> qwerty() {
        return Arrays.asList(
                row(KeySpec.letter("q"), KeySpec.letter("w"),
                        KeySpec.letter("e", "é", "è", "ê", "ë"),
                        KeySpec.letter("r"), KeySpec.letter("t"), KeySpec.letter("y"),
                        KeySpec.letter("u", "ù", "û", "ü"),
                        KeySpec.letter("i", "î", "ï"),
                        KeySpec.letter("o", "ô", "ö"),
                        KeySpec.letter("p")),
                row(KeySpec.letter("a", "à", "â", "ä"),
                        KeySpec.letter("s"), KeySpec.letter("d"), KeySpec.letter("f"),
                        KeySpec.letter("g"), KeySpec.letter("h"), KeySpec.letter("j"),
                        KeySpec.letter("k"), KeySpec.letter("l")),
                row(KeySpec.function(KeySpec.Type.SHIFT, "", R.drawable.ic_shift, SHIFT_WEIGHT),
                        KeySpec.letter("z"), KeySpec.letter("x"),
                        KeySpec.letter("c", "ç"),
                        KeySpec.letter("v"), KeySpec.letter("b"), KeySpec.letter("n"), KeySpec.letter("m"),
                        KeySpec.function(KeySpec.Type.DELETE, "", R.drawable.ic_backspace, SHIFT_WEIGHT)),
                bottomRow());
    }

    private static List<KeySpec> bottomRow() {
        return row(
                KeySpec.function(KeySpec.Type.LANGUAGE, "", R.drawable.ic_globe, LANGUAGE_WEIGHT),
                KeySpec.function(KeySpec.Type.MODE, "#+=", 0, MODE_WEIGHT),
                KeySpec.function(KeySpec.Type.EMOJI, "", R.drawable.ic_emoji, EMOJI_WEIGHT),
                KeySpec.function(KeySpec.Type.SPACE, "", 0, SPACE_WEIGHT),
                KeySpec.function(KeySpec.Type.ENTER, "", R.drawable.ic_enter, ENTER_WEIGHT));
    }

    private static List<KeySpec> row(KeySpec... specs) {
        return Arrays.asList(specs);
    }
}
