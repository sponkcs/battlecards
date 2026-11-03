package dev.sneakycards;

import java.util.Locale;

/** Game mat looks. The key matches the textures in the resource pack (mat_<key>). */
public enum MatVariant {
    CLASSIC("Classic Felt"),
    PURPLE("Royal Purple"),
    NEON("Neon Gradient");

    public final String label;

    MatVariant(String label) { this.label = label; }

    public String key() { return name().toLowerCase(Locale.ROOT); }

    /** Returns null when the text is not a mat name. */
    public static MatVariant parse(String s) {
        if (s == null) return null;
        try { return valueOf(s.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return null; }
    }
}
