package dev.sneakycards;

import java.util.Locale;

/**
 * Item cards go in your deck with your characters and are drawn into your hand.
 *  QUICK items are played from the turn menu: they cost energy but don't end your turn. One item per turn.
 *  TRAP items are set face down; the first one that applies springs on the opponent's next attack.
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
    GUARDIAN_ANGEL("Guardian Angel", Rarity.MYTHIC, Kind.TRAP, 3, 50, "If a hit would knock out your card, it survives at half Health"),
    SCOUTS_MAP("Scout's Map", Rarity.COMMON, Kind.QUICK, 0, 2, "Draw 2 cards"),
    WHETSTONE("Whetstone", Rarity.COMMON, Kind.QUICK, 1, 5, "Your attack this turn deals +5 damage"),
    COUNTER_STANCE("Counter Stance", Rarity.UNCOMMON, Kind.TRAP, 1, 8, "When an enemy attacks you, strike back for 8"),
    SPRINT_SHOES("Sprint Shoes", Rarity.RARE, Kind.QUICK, 1, 0, "One of your cards that already acted may act again"),
    RALLY_HORN("Rally Horn", Rarity.RARE, Kind.QUICK, 2, 6, "Heal all your cards by 6"),
    PITFALL("Pitfall", Rarity.RARE, Kind.TRAP, 1, 2, "The attacking player loses 2 energy"),
    RECALL_SCROLL("Recall Scroll", Rarity.EPIC, Kind.QUICK, 1, 0, "Return a knocked-out character from your discard pile to your hand"),
    THUNDER_SCROLL("Thunder Scroll", Rarity.EPIC, Kind.QUICK, 2, 10, "Deal 10 damage to an enemy card"),
    SMOKE_VEIL("Smoke Veil", Rarity.EPIC, Kind.TRAP, 2, 0, "The next enemy attack misses completely"),
    WAR_DRUM("War Drum", Rarity.LEGENDARY, Kind.QUICK, 2, 50, "Your attack this turn deals +50% damage"),
    BANISH_RUNE("Banish Rune", Rarity.LEGENDARY, Kind.TRAP, 3, 0, "The attacking card is put on the bottom of its owner's deck"),
    DRAGONS_HOARD("Dragon's Hoard", Rarity.MYTHIC, Kind.QUICK, 2, 4, "Gain 4 energy and draw 2 cards");

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
    /** Who the item is used on: your own card, an enemy card, or nobody (it just takes effect). */
    public enum Target { OWN, ENEMY, NONE }
    public Target target() {
        if (heals() || this == SPRINT_SHOES) return Target.OWN;
        if (this == THUNDER_SCROLL) return Target.ENEMY;
        return Target.NONE;
    }
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
