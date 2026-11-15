package dev.sneakycards;

import java.util.Locale;

/**
 * Item cards go in your deck with your characters and are drawn into your hand. They cost nothing to play;
 * you can play one item or trap per turn.
 *  QUICK items take effect straight away (some ask you to pick a card).
 *  TRAP items are set face down (2 at most); the first one that applies springs when the rival attacks you.
 */
public enum ItemType {
    BANDAGE("Bandage", Rarity.COMMON, Kind.QUICK, 20, "Heal 20 from one of your cards"),
    SPIKE_TRAP("Spike Trap", Rarity.COMMON, Kind.TRAP, 20, "When the rival attacks, the attacker takes 20 damage"),
    HERBAL_TONIC("Herbal Tonic", Rarity.UNCOMMON, Kind.QUICK, 30, "Heal 30 from one of your cards"),
    DECOY("Decoy", Rarity.UNCOMMON, Kind.TRAP, 30, "The next attack against you deals 30 less damage"),
    ENERGY_DRINK("Energy Drink", Rarity.UNCOMMON, Kind.QUICK, 1, "Attach 1 extra mana to one of your benched cards"),
    MENDING_POTION("Mending Potion", Rarity.RARE, Kind.QUICK, 50, "Heal 50 from one of your cards"),
    SNARE("Snare", Rarity.RARE, Kind.TRAP, 0, "The attacker is dazed: it can't attack or retreat next turn"),
    MIRROR_WARD("Mirror Ward", Rarity.EPIC, Kind.TRAP, 50, "The attacker takes half the damage it deals"),
    LAST_STAND("Last Stand", Rarity.LEGENDARY, Kind.TRAP, 10, "If a hit would knock out your card, it survives with 10 Health"),
    GUARDIAN_ANGEL("Guardian Angel", Rarity.MYTHIC, Kind.TRAP, 50, "If a hit would knock out your card, it survives at half Health"),
    SCOUTS_MAP("Scout's Map", Rarity.COMMON, Kind.QUICK, 2, "Draw 2 cards"),
    WHETSTONE("Whetstone", Rarity.COMMON, Kind.QUICK, 10, "Your attack this turn deals +10 damage"),
    COUNTER_STANCE("Counter Stance", Rarity.UNCOMMON, Kind.TRAP, 30, "If your card survives the attack, it strikes back for 30"),
    SPRINT_SHOES("Sprint Shoes", Rarity.RARE, Kind.QUICK, 0, "Switch your Active card with a benched card for free"),
    RALLY_HORN("Rally Horn", Rarity.RARE, Kind.QUICK, 20, "Heal 20 from each of your cards"),
    PITFALL("Pitfall", Rarity.RARE, Kind.TRAP, 1, "The attacker loses 1 mana"),
    RECALL_SCROLL("Recall Scroll", Rarity.EPIC, Kind.QUICK, 0, "Return a character from your discard pile to your hand"),
    THUNDER_SCROLL("Thunder Scroll", Rarity.EPIC, Kind.QUICK, 20, "Deal 20 damage to any rival card"),
    SMOKE_VEIL("Smoke Veil", Rarity.EPIC, Kind.TRAP, 0, "The next attack against you misses completely"),
    WAR_DRUM("War Drum", Rarity.LEGENDARY, Kind.QUICK, 30, "Your attack this turn deals +30 damage"),
    BANISH_RUNE("Banish Rune", Rarity.LEGENDARY, Kind.TRAP, 0, "The attacker goes to the bottom of its owner's deck (only if they have a benched card)"),
    DRAGONS_HOARD("Dragon's Hoard", Rarity.MYTHIC, Kind.QUICK, 1, "Draw 2 cards and attach 1 mana to each of your cards");

    public enum Kind { QUICK, TRAP }

    public final String label, description;
    public final Rarity rarity;
    public final Kind kind;
    public final int amount;

    ItemType(String label, Rarity rarity, Kind kind, int amount, String description) {
        this.label = label; this.rarity = rarity; this.kind = kind; this.amount = amount; this.description = description;
    }

    public String key() { return name().toLowerCase(Locale.ROOT); }
    public boolean heals() { return this == BANDAGE || this == HERBAL_TONIC || this == MENDING_POTION; }
    /** Who the item is used on: any of your cards, one of your benched cards, a rival card, or nobody (it just takes effect). */
    public enum Target { OWN, BENCH, ENEMY, NONE }
    public Target target() {
        if (heals()) return Target.OWN;
        if (this == SPRINT_SHOES || this == ENERGY_DRINK) return Target.BENCH;
        if (this == THUNDER_SCROLL) return Target.ENEMY;
        return Target.NONE;
    }
    /** Traps that only spring when a hit would knock the card out. */
    public boolean savesFromKo() { return this == LAST_STAND || this == GUARDIAN_ANGEL; }

    public static ItemType parse(String s) {
        if (s == null) return null;
        try { return valueOf(s.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return null; }
    }

    /** An item of one kind (QUICK or TRAP), weighted by rarity. */
    public static ItemType draw(java.util.Random r, Kind kind) {
        double total = 0;
        for (ItemType t : values()) if (t.kind == kind) total += t.rarity.weight;
        double roll = r.nextDouble() * total;
        for (ItemType t : values()) { if (t.kind != kind) continue; roll -= t.rarity.weight; if (roll < 0) return t; }
        return kind == Kind.TRAP ? SPIKE_TRAP : BANDAGE;
    }

    public static ItemType draw(java.util.Random r) {
        double total = 0;
        for (ItemType t : values()) total += t.rarity.weight;
        double roll = r.nextDouble() * total;
        for (ItemType t : values()) { roll -= t.rarity.weight; if (roll < 0) return t; }
        return BANDAGE;
    }
}
