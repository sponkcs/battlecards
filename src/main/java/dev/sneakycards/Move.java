package dev.sneakycards;

/** A card move. Damage equals the card's value in the linked stat. */
public record Move(String name, Stat stat) {}
