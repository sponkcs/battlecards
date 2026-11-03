package dev.sneakycards;

import java.util.List;

public enum Stat {
    LUCK("Luck", "luck"),
    STRENGTH("Strength", "strength"),
    DEXTERITY("Dexterity", "dexterity"),
    MAGICING("Magicing", "magicing"),
    HEALTH("Health", "health");

    /** Stats a move can be powered by (Health is not one of them). */
    public static final List<Stat> MOVE_STATS = List.of(LUCK, STRENGTH, DEXTERITY, MAGICING);

    public final String label;
    public final String icon;

    Stat(String label, String icon) { this.label = label; this.icon = icon; }

    public int defaultValue() { return this == HEALTH ? 30 : 10; }
}
