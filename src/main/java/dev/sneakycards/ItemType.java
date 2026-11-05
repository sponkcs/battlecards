package dev.sneakycards;

import java.util.Locale;

/**
 * Item cards. Bring up to 2 to a battle (right-click your side of the mat with one before the battle starts).
 *  QUICK items are used from the turn menu: they cost energy but don't use a card's action. One item per turn.
 *  TRAP items are set face down from the turn menu; they spring on the opponent's next attack that matches.
 * Items you bring are returned after the battle, used or not.
 */
public enum ItemType {
    BANDAGE("Bandage", Rarity.COMMON, Kind.QUICK, 1, 6, "Heal one of your cards by 6"),
    SPIKE_TRAP("Spike Trap", Rarity.COMMON, Kind.TRAP, 1, 6, "When an enemy attacks you, it takes 6 damage"),
    HERBAL_TONIC("Herbal Tonic", Rarity.UNCOMMON, Kind.QUICK, 1, 10, "Heal one of your cards by 10"),
    DECOY("Decoy", Rarity.UNCOMMON, Kind.TRAP, 1, 50, "Halves the damage of the next enemy attack"),
    ENERGY_DRINK("Energy Drink", Rarity.UNCOMMON, Kind.QUICK, 0, 2, "Gain 2 energy"),
    MENDING_POTION("Mending Potion", Rarity.RARE, Kind.QUICK, 2, 16, "Heal one of your cards by 16"),
    SNARE("Snare", Rarity.RARE, Kind.TRAP, 2, 0, "The next enemy attacker is stunned and skips its next action"),
    MIRROR_WARD("Mirror Ward", Rarity.EPIC, Kind.TRAP, 2, 50, "The next enemy attacker takes half the damage it deals"),
    LAST_STAND("Last Stand", Rarity.LEGENDARY, Kind.TRAP, 2, 1, "If a hit would knock out your card, it survives with 1 Health"),
    GUARDIAN_ANGEL("Guardian Angel", Rarity.MYTHIC, Kind.TRAP, 3, 50, "If a hit would knock out your card, it survives at half Health");

    public enum Kind { QUICK, TRAP }

    public final String label, description;
    public final Rarity rarity;
    public final Kind kind;
    public final int cost, amount;

    ItemType(String label, Rarity rarity, Kind kind, int cost, int amount, String description) {
        this.label = label; this.rarity = rarity; this.kind = kind; this.cost = cost; this.amount = amount; this.description = description;
    }

    public String key() { return name().toLowerCase(Locale.ROOT); }
    public boolean heals() { return this == BANDAGE || this == HERBAL_TONIC || this == MENDING_POTION; }
    /** Traps that only spring when a hit would knock the card out. */
    public boolean savesFromKo() { return this == LAST_STAND || this == GUARDIAN_ANGEL; }

    public static ItemType parse(String s) {
        if (s == null) return null;
        try { return valueOf(s.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return null; }
    }

    public static ItemType draw(java.util.Random r) {
        double total = 0;
        for (ItemType t : values()) total += t.rarity.weight;
        double roll = r.nextDouble() * total;
        for (ItemType t : values()) { roll -= t.rarity.weight; if (roll < 0) return t; }
        return BANDAGE;
    }
}
