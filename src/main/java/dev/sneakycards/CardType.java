package dev.sneakycards;

import net.kyori.adventure.text.format.TextColor;
import java.util.Locale;

/**
 * A character's type. Strength beats Magicing, Magicing beats Dexterity, Dexterity beats Strength.
 * A move that hits the type it beats deals a little extra damage (rules.weakness-bonus, default +10).
 */
public enum CardType {
    STRENGTH("Strength", "strength", 0xE0533D),
    DEXTERITY("Dexterity", "dexterity", 0x55C455),
    MAGICING("Magicing", "magicing", 0xA866FF);

    public final String label, icon;
    public final TextColor color;

    CardType(String label, String icon, int rgb) { this.label = label; this.icon = icon; this.color = TextColor.color(rgb); }

    public String key() { return name().toLowerCase(Locale.ROOT); }

    /** True if this type hits the other type for extra damage. */
    public boolean beats(CardType o) {
        return (this == STRENGTH && o == MAGICING) || (this == MAGICING && o == DEXTERITY) || (this == DEXTERITY && o == STRENGTH);
    }

    /** The type this one is weak to. */
    public CardType weakness() { for (CardType t : values()) if (t.beats(this)) return t; return this; }

    public CardType next() { return values()[(ordinal() + 1) % values().length]; }

    public static CardType parse(String s, CardType fallback) {
        if (s == null) return fallback;
        try { return valueOf(s.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return fallback; }
    }
}
