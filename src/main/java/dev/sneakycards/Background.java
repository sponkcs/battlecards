package dev.sneakycards;

import java.util.Locale;

/** Card backdrop art. The key matches the textures in the resource pack (card_<key>_<rarity>). */
public enum Background {
    SKYLINE("Skyline"),
    BLOSSOM("Blossom Orchard"),
    HELLFIRE("Hellfire"),
    GROVE("Grove Forest"),
    GOO("Goo Trees"),
    SLUMS("Brown Slums"),
    MINES("Dwarven Mines"),
    SPIRE("Wizard Spire");

    public final String label;

    Background(String label) { this.label = label; }

    public String key() { return name().toLowerCase(Locale.ROOT); }

    /** Reads a saved value; old names from earlier versions map to their replacements. */
    public static Background parse(String s) {
        if (s == null) return SKYLINE;
        String u = s.toUpperCase(Locale.ROOT);
        if (u.equals("SAKURA")) return BLOSSOM;
        if (u.equals("ARCANE")) return SPIRE;
        try { return valueOf(u); } catch (IllegalArgumentException e) { return SKYLINE; }
    }
}
