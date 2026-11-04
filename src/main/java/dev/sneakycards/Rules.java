package dev.sneakycards;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Balance rules in one place. Numbers come from config.yml (section "rules") so they can be tuned without a rebuild.
 *
 *  - Team cost: each rarity costs points; a team of 3 may cost at most team-cost-cap. No duplicate cards.
 *  - Stat budget: the five stats of a card add up to at most the rarity's budget (enforced in /cardadmin).
 *  - Type triangle: a card's type is its highest of Strength / Dexterity / Magicing.
 *    Strength beats Magicing, Magicing beats Dexterity, Dexterity beats Strength.
 *  - Energy: each side gains energy at the start of its turn; moves cost 1-3 by power; Luck moves refund 1.
 *  - Each card acts once per round: all living cards on a side must act before any can act again.
 */
public final class Rules {
    private final SneakyCardsPlugin plugin;
    Rules(SneakyCardsPlugin plugin) { this.plugin = plugin; }
    private FileConfiguration cfg() { return plugin.getConfig(); }

    public int teamCostCap() { return cfg().getInt("rules.team-cost-cap", 10); }
    public int energyPerTurn() { return cfg().getInt("rules.energy-per-turn", 2); }
    public int energyMax() { return cfg().getInt("rules.energy-max", 6); }
    public int secondPlayerBonus() { return cfg().getInt("rules.second-player-bonus-energy", 1); }
    public int restBonus() { return cfg().getInt("rules.rest-bonus-energy", 1); }
    public double damageScale() { return cfg().getDouble("rules.damage-scale", 0.6); }
    public double critMultiplier() { return cfg().getDouble("rules.crit-multiplier", 1.5); }
    public double advantage() { return cfg().getDouble("rules.type-advantage", 1.5); }
    public double disadvantage() { return cfg().getDouble("rules.type-disadvantage", 0.75); }

    public static int cost(Rarity r) { return r.ordinal() + 1; }
    public static int budget(Rarity r) { return new int[]{72, 78, 84, 90, 96, 104}[r.ordinal()]; }
    public static int min(Stat s) { return s == Stat.HEALTH ? 15 : 1; }
    public static int max(Stat s) { return s == Stat.HEALTH ? 60 : s == Stat.LUCK ? 30 : 40; }

    public static int total(java.util.Map<Stat, Integer> stats) {
        int t = 0; for (Stat s : Stat.values()) t += stats.getOrDefault(s, 0); return t;
    }

    /** Highest of Strength, Dexterity, Magicing (ties: Strength, then Dexterity). */
    public static Stat type(Card c) {
        Stat best = Stat.STRENGTH;
        for (Stat s : new Stat[]{Stat.DEXTERITY, Stat.MAGICING}) if (c.stat(s) > c.stat(best)) best = s;
        return best;
    }

    /** True if attacking type a is strong against defending type d. */
    public static boolean beats(Stat a, Stat d) {
        return (a == Stat.STRENGTH && d == Stat.MAGICING) || (a == Stat.MAGICING && d == Stat.DEXTERITY) || (a == Stat.DEXTERITY && d == Stat.STRENGTH);
    }

    public double typeMultiplier(Move mv, Card target) {
        if (mv.stat() == Stat.LUCK) return 1.0;
        Stat t = type(target);
        if (beats(mv.stat(), t)) return advantage();
        if (beats(t, mv.stat())) return disadvantage();
        return 1.0;
    }

    /** Energy cost by move power: 1 up to 12, 2 up to 24, 3 above. */
    public static int moveCost(Card c, Move mv) {
        int p = c.power(mv);
        return p <= 12 ? 1 : p <= 24 ? 2 : 3;
    }

    public static int refund(Move mv) { return mv.stat() == Stat.LUCK ? 1 : 0; }

    public int damage(Card att, Move mv, Card tgt, boolean crit) {
        double d = att.power(mv) * damageScale() * typeMultiplier(mv, tgt) * (crit ? critMultiplier() : 1.0);
        return Math.max(1, (int) Math.round(d));
    }

    public static String typeName(Stat s) { return s.label; }
}
