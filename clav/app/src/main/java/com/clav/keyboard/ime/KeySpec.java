package com.clav.keyboard.ime;

/**
 * Immutable description of a single key on the keyboard.
 */
public final class KeySpec {

    /** Every behaviour a key can have. */
    public enum Type {
        LETTER,    // respects shift state, accents on long-press
        CHAR,      // always commits a fixed character
        SHIFT,
        DELETE,
        SPACE,
        ENTER,
        LANGUAGE,  // toggles layout (AZERTY / QWERTY)
        MODE,      // toggles page (letters / symbols)
        EMOJI
    }

    public final Type type;
    public final String label;     // display label; empty when an icon is used
    public final String output;    // base (lowercase) character emitted
    public final int iconRes;      // drawable resource, 0 = label only
    public final float weight;     // relative width inside the row
    public final String[] accents; // long-press alternatives for LETTER keys

    private KeySpec(Type type, String label, String output, int iconRes, float weight, String[] accents) {
        this.type = type;
        this.label = label;
        this.output = output;
        this.iconRes = iconRes;
        this.weight = weight;
        this.accents = accents;
    }

    /** A letter key; respects shift state and exposes accents on long-press. */
    public static KeySpec letter(String letter, String... accents) {
        return new KeySpec(Type.LETTER, letter, letter, 0, 1f, accents.length == 0 ? null : accents);
    }

    /** A key that always commits a fixed character (punctuation…). */
    public static KeySpec character(String c) {
        return new KeySpec(Type.CHAR, c, c, 0, 1f, null);
    }

    /** A functional key (shift, delete, enter, language, mode, emoji). */
    public static KeySpec function(Type type, String label, int iconRes, float weight) {
        return new KeySpec(type, label, null, iconRes, weight, null);
    }

    public boolean hasAccents() {
        return accents != null && accents.length > 0;
    }
}
