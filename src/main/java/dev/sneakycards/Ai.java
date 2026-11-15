package dev.sneakycards;

import org.bukkit.Bukkit;
import java.util.*;

/**
 * The computer opponent for solo battles. It plays the same rules as a player: bench characters, attach its mana
 * where it does the most good, play one item, retreat a badly hurt Active, then use the best move it can afford.
 */
final class Ai {
    private Ai() { }

    /** The rival's default deck: steady Strength cards with heals and mana tricks (deck cost 29 of 35). */
    static final List<String> BEST_DECK = List.of(
            "tunnel_tess", "rusty_rourke", "rusty_rourke", "brick_bronson", "nyx_nightwire", "gorrim_deepdelve", "ember_jinx",
            "item:ENERGY_DRINK", "item:ENERGY_DRINK", "item:BANDAGE", "item:BANDAGE", "item:MENDING_POTION",
            "item:SCOUTS_MAP", "item:WHETSTONE", "item:SPIKE_TRAP");

    /** The rival's deck: solo-deck from config, else the default, else a cheap deck from the registry. */
    static List<String> deck(SneakyCardsPlugin plugin) {
        List<String> want = plugin.getConfig().getStringList("solo-deck");
        if (want.isEmpty()) want = BEST_DECK;
        List<String> ok = new ArrayList<>();
        for (String k : want) if (HandCard.parse(k, plugin.registry()) != null) ok.add(k);
        if (ok.size() == DeckStore.SIZE) return ok;
        // fall back: 8 cheap characters and 7 items
        Random r = new Random();
        List<Card> chars = new ArrayList<>(plugin.registry().all());
        chars.sort(Comparator.comparingInt(c -> c.rarity().ordinal()));
        List<String> out = new ArrayList<>();
        for (int i = 0; out.size() < 8 && !chars.isEmpty(); i++) out.add(chars.get(i % Math.min(chars.size(), 8)).id());
        ItemType[] items = ItemType.values();
        while (out.size() < DeckStore.SIZE) out.add("item:" + items[r.nextInt(Math.min(items.length, 8))].name());
        return out;
    }

    /** How good a card is to fight with: Health plus its best move's damage. */
    private static int score(Card c) {
        int best = 0; for (Move m : c.moves()) best = Math.max(best, m.damage());
        return c.hp() + best;
    }

    /** Mana a card still needs for its strongest move. */
    private static int need(DisplayManager.Placed p) {
        int top = 0; for (Move m : p.card.moves()) top = Math.max(top, m.cost());
        return Math.max(0, top - p.mana);
    }

    static void setup(BattleManager bm, Mat m, int side) {
        List<HandCard> chars = new ArrayList<>();
        for (HandCard h : m.hand[side]) if (h.isChar()) chars.add(h);
        chars.sort((a, b) -> score(b.card) - score(a.card));
        int[] order = {Mat.ACTIVE, Mat.BENCH[0], Mat.BENCH[1]};
        for (int i = 0; i < Math.min(3, chars.size()); i++) bm.setupPlace(m, side, m.hand[side].indexOf(chars.get(i)), order[i]);
        bm.markReady(m, side);
    }

    /** A new Active after a knockout: the benched card closest to attacking, then the toughest. */
    static DisplayManager.Placed pickActive(Mat m, int side) {
        DisplayManager.Placed best = null;
        for (var b : m.bench(side)) if (best == null || need(b) < need(best) || (need(b) == need(best) && b.hp > best.hp)) best = b;
        return best;
    }

    static void turn(BattleManager bm, Mat m, int side) {
        SneakyCardsPlugin plugin = bm.plugin();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (m.state != Mat.State.FIGHTING || m.turn != side) return;
            // 1. bench characters while there's room
            for (int guard = 0; guard < 3 && m.emptyBench(side) >= 0; guard++) {
                int best = -1;
                for (int i = 0; i < m.hand[side].size(); i++) {
                    HandCard h = m.hand[side].get(i);
                    if (h.isChar() && (best < 0 || score(h.card) > score(m.hand[side].get(best).card))) best = i;
                }
                if (best < 0 || !bm.playCharacter(m, side, best)) break;
            }
            // 2. attach mana
            if (m.mana[side] > 0) bm.attachMana(m, side, manaTarget(m, side));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (m.state != Mat.State.FIGHTING || m.turn != side) return;
                useItem(bm, m, side);
                maybeRetreat(bm, m, side);
                Bukkit.getScheduler().runTaskLater(plugin, () -> attackOrEnd(bm, m, side, 0), 25L);
            }, 25L);
        }, 30L);
    }

    /** The Active if it can't use its best move yet, else the benched card that needs mana most. */
    private static DisplayManager.Placed manaTarget(Mat m, int side) {
        var act = m.active(side);
        if (act != null && need(act) > 0) return act;
        DisplayManager.Placed best = null;
        for (var b : m.bench(side)) if (need(b) > 0 && (best == null || score(b.card) > score(best.card))) best = b;
        return best != null ? best : act;
    }

    private static void maybeRetreat(BattleManager bm, Mat m, int side) {
        var act = m.active(side);
        if (act == null || m.retreated[side] || act.mana < act.card.retreat() || BattleManager.dazed(m, act)) return;
        if (act.hp > act.maxHp * 0.3) return;
        DisplayManager.Placed sub = null;
        for (var b : m.bench(side)) if (b.hp > act.hp && need(b) <= 1 && (sub == null || b.hp > sub.hp)) sub = b;
        if (sub != null) bm.retreat(m, side, sub);
    }

    private static void attackOrEnd(BattleManager bm, Mat m, int side, int tries) {
        if (m.state != Mat.State.FIGHTING || m.turn != side) return;
        if (m.busy || m.promoting[1 - side] || m.active(1 - side) == null || m.pendingKo > 0) {
            // something is still playing out (a Thunder Scroll knockout, the rival picking a new Active)
            if (tries < 60) Bukkit.getScheduler().runTaskLater(bm.plugin(), () -> attackOrEnd(bm, m, side, tries + 1), 10L);
            return;
        }
        var att = m.active(side);
        var tgt = m.active(1 - side);
        int bestIdx = -1; double bestScore = -1;
        if (att != null) for (int k = 0; k < att.card.moves().size(); k++) {
            Move mv = att.card.moves().get(k);
            if (bm.cantAttack(m, side, mv) != null) continue;
            int d = bm.damage(m, side, att, mv, tgt);
            double s = d + (d >= tgt.hp ? 100 : 0) + (mv.effect() == MoveEffect.NONE ? 0 : 8) - (mv.effect() == MoveEffect.OVERLOAD ? 12 : 0);
            if (s > bestScore) { bestScore = s; bestIdx = k; }
        }
        if (bestIdx >= 0) bm.attack(m, side, bestIdx);
        else bm.endTurn(m, side);
    }

    private static void useItem(BattleManager bm, Mat m, int side) {
        if (m.itemUsed[side]) return;
        List<DisplayManager.Placed> mine = new ArrayList<>();
        for (var c : m.cards[side]) if (c != null && !c.ko) mine.add(c);
        mine.sort(Comparator.comparingInt(c -> c.hp - c.maxHp));   // most Health missing first
        DisplayManager.Placed hurt = mine.isEmpty() ? null : mine.get(0);
        DisplayManager.Placed weakest = null;
        for (var c : m.cards[1 - side]) if (c != null && !c.ko && (weakest == null || c.hp < weakest.hp)) weakest = c;
        DisplayManager.Placed hungryBench = null;
        for (var b : m.bench(side)) if (need(b) > 0 && (hungryBench == null || score(b.card) > score(hungryBench.card))) hungryBench = b;
        var act = m.active(side);
        int bestIdx = -1; double bestScore = 0; DisplayManager.Placed bestTarget = null;
        for (int i = 0; i < m.hand[side].size(); i++) {
            HandCard h = m.hand[side].get(i);
            if (h.isChar()) continue;
            ItemType t = h.item;
            double s = 0; DisplayManager.Placed target = null;
            if (t.heals() && hurt != null) { int missing = hurt.maxHp - hurt.hp; if (missing >= t.amount * 0.7) { s = Math.min(t.amount, missing) * 0.4; target = hurt; } }
            else if (t == ItemType.RALLY_HORN) { int sum = 0; for (var c : mine) sum += Math.min(t.amount, c.maxHp - c.hp); if (sum >= 30) s = sum * 0.4; }
            else if (t.kind == ItemType.Kind.TRAP && m.traps[side].size() < 2) s = 7 + t.rarity.ordinal();
            else if (t == ItemType.ENERGY_DRINK && hungryBench != null) { s = 10; target = hungryBench; }
            else if (t == ItemType.DRAGONS_HOARD) s = 14;
            else if (t == ItemType.SCOUTS_MAP && m.hand[side].size() <= 4 && m.deck[side].size() >= 3) s = 5;
            else if ((t == ItemType.WHETSTONE || t == ItemType.WAR_DRUM) && act != null && act.mana > 0) s = t.amount / 3.0;
            else if (t == ItemType.SPRINT_SHOES && act != null && act.hp <= act.maxHp * 0.3) {
                for (var b : m.bench(side)) if (b.hp > act.hp) { s = 9; target = b; break; }
            }
            else if (t == ItemType.RECALL_SCROLL && m.discard[side].stream().anyMatch(HandCard::isChar)) s = 6;
            else if (t == ItemType.THUNDER_SCROLL && weakest != null) { s = weakest.hp <= t.amount ? 20 : 6; target = weakest; }
            if (s > bestScore) { bestScore = s; bestIdx = i; bestTarget = target; }
        }
        if (bestIdx >= 0) bm.useItem(m, side, bestIdx, bestTarget);
    }
}
