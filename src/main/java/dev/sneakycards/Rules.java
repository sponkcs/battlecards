package dev.sneakycards;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Battle and card-building rules in one place. Battle numbers come from config.yml (section "rules").
 *
 *  Mana: each turn you get 1 mana and attach it to one of your cards. Attached mana stays on that card until
 *  it is knocked out, retreats (retreating discards mana equal to its retreat cost) or a move/item removes it.
 *  A move can be used if its card has at least that much mana attached; using it doesn't spend the mana.
 *
 *  Card building (enforced in the card creator): Health and move damage are multiples of 10 with caps by rarity,
 *  and each move's damage is capped by its mana cost, minus what its effect uses up.
 */
public final class Rules {
    private final SneakyCardsPlugin plugin;
    Rules(SneakyCardsPlugin plugin) { this.plugin = plugin; }
    private FileConfiguration cfg() { return plugin.getConfig(); }

    public int weaknessBonus() { return cfg().getInt("rules.weakness-bonus", 10); }
    public int pointsToWin() { return cfg().getInt("rules.points-to-win", 3); }
    public int manaPerTurn() { return Math.max(1, cfg().getInt("rules.mana-per-turn", 1)); }
    public boolean firstTurnMana() { return cfg().getBoolean("rules.first-player-gets-mana", false); }
    public int handLimit() { return cfg().getInt("rules.hand-limit", 7); }

    /** Deck cost of a card by rarity: Common 1 ... Mythic 6. */
    public static int cost(Rarity r) { return r.ordinal() + 1; }

    public static final int MAX_MOVE_COST = 4, MAX_RETREAT = 3, MIN_HP = 30;
    public static int maxHp(Rarity r) { return new int[]{70, 80, 100, 110, 130, 150}[r.ordinal()]; }

    /** Most damage a move may deal: by mana cost, a little more for rarer cards, less if its effect is strong. */
    public static int maxDamage(int cost, Rarity r, MoveEffect e) {
        int base = new int[]{10, 30, 60, 90, 120}[Math.max(0, Math.min(MAX_MOVE_COST, cost))];
        return Math.max(0, base + 10 * (r.ordinal() / 2) - e.budget);
    }

    public static boolean legal(Card c) {
        if (c.hp() < MIN_HP || c.hp() > maxHp(c.rarity()) || c.retreat() < 0 || c.retreat() > MAX_RETREAT) return false;
        for (Move m : c.moves()) if (m.cost() < 0 || m.cost() > MAX_MOVE_COST || m.damage() < 0 || m.damage() > maxDamage(m.cost(), c.rarity(), m.effect())) return false;
        return true;
    }

    /** Damage of a move against a target, including weakness and this turn's item bonus. */
    public int damage(Card att, Move mv, Card tgt, int bonus) {
        if (mv.damage() <= 0) return 0;
        return mv.damage() + bonus + (att.type().beats(tgt.type()) ? weaknessBonus() : 0);
    }

    public static int round10(double v) { return (int) Math.round(v / 10.0) * 10; }
}
