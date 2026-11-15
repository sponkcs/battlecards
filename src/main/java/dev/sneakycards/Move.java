package dev.sneakycards;

/** A card move: needs at least {@code cost} mana attached to the card, deals {@code damage}, plus an optional effect. */
public record Move(String name, int cost, int damage, MoveEffect effect) {
    /** "◆◆ 50" style summary. */
    public String costText() { return cost == 0 ? "free" : "◆".repeat(cost); }
}
