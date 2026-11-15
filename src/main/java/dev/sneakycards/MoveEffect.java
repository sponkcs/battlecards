package dev.sneakycards;

import java.util.Locale;

/**
 * An optional extra on a move. Each effect costs some of the move's damage budget (see {@link Rules#maxDamage});
 * Overload gives extra budget in return for discarding mana.
 */
public enum MoveEffect {
    NONE("No effect", "", 0),
    HEAL("Heal", "Heal 20 from this card", 10),
    DRAIN("Drain", "Remove 1 mana from the defending card", 10),
    SPLASH("Splash", "Also deal 10 damage to each of the rival's benched cards", 10),
    DRAW("Draw", "Draw a card", 10),
    CHARGE("Charge", "Attach 1 extra mana to this card", 20),
    DAZE("Daze", "Flip a coin. Heads: the defending card can't attack or retreat next turn", 20),
    OVERLOAD("Overload", "Discard 1 mana from this card", -20);

    public final String label, description;
    /** Damage budget this effect uses up (negative = gives budget). */
    public final int budget;

    MoveEffect(String label, String description, int budget) { this.label = label; this.description = description; this.budget = budget; }

    public MoveEffect next() { return values()[(ordinal() + 1) % values().length]; }

    public static MoveEffect parse(String s) {
        if (s == null) return NONE;
        try { return valueOf(s.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return NONE; }
    }
}
