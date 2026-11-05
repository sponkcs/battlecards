package dev.sneakycards;

import org.bukkit.Bukkit;
import java.util.*;

/**
 * The computer opponent for solo battles. It plays the same rules as a player: one character and one item per
 * turn, then the attack that scores best (a knockout first, then the most damage for the energy).
 */
final class Ai {
    private Ai() { }

    /** "Brick Wall": the strongest deck found by simulating thousands of games with the current roster. */
    static final List<String> BEST_DECK = List.of(
            "brick_bronson", "brick_bronson", "rusty_rourke", "rusty_rourke", "tunnel_tess", "nyx_nightwire",
            "item:ENERGY_DRINK", "item:ENERGY_DRINK", "item:HERBAL_TONIC", "item:HERBAL_TONIC", "item:BANDAGE",
            "item:DECOY", "item:SPIKE_TRAP", "item:GUARDIAN_ANGEL", "item:WHETSTONE");

    /** The rival's deck: solo-deck from config, else Brick Wall, else a random legal deck from the registry. */
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

    private static int score(HandCard h) { return h.rarity().ordinal() * 100 + h.card.stat(Stat.HEALTH); }

    static void setup(BattleManager bm, Mat m, int side) {
        List<Integer> chars = new ArrayList<>();
        for (int i = 0; i < m.hand[side].size(); i++) if (m.hand[side].get(i).isChar()) chars.add(i);
        chars.sort((a, b) -> score(m.hand[side].get(b)) - score(m.hand[side].get(a)));
        List<HandCard> pick = new ArrayList<>();
        for (int i = 0; i < Math.min(3, chars.size()); i++) pick.add(m.hand[side].get(chars.get(i)));
        int col = 0;
        for (HandCard h : pick) bm.setupPlace(m, side, m.hand[side].indexOf(h), col++);
        bm.markReady(m, side);
    }

    static void turn(BattleManager bm, Mat m, int side) {
        SneakyCardsPlugin plugin = bm.plugin();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (m.state != Mat.State.FIGHTING || m.turn != side) return;
            // 1. play a character if there's a free slot
            int slot = m.emptySlot(side);
            if (slot >= 0) {
                int best = -1;
                for (int i = 0; i < m.hand[side].size(); i++) {
                    HandCard h = m.hand[side].get(i);
                    if (h.isChar() && (best < 0 || score(h) > score(m.hand[side].get(best)))) best = i;
                }
                if (best >= 0) bm.playCharacter(m, side, best, slot);
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (m.state != Mat.State.FIGHTING || m.turn != side) return;
                useItem(bm, m, side);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (m.state != Mat.State.FIGHTING || m.turn != side || m.busy) {
                        if (m.state == Mat.State.FIGHTING && m.turn == side && m.busy)   // a Thunder Scroll knockout is still playing
                            Bukkit.getScheduler().runTaskLater(plugin, () -> attackOrEnd(bm, m, side), 30L);
                        return;
                    }
                    attackOrEnd(bm, m, side);
                }, 25L);
            }, 25L);
        }, 30L);
    }

    private static void attackOrEnd(BattleManager bm, Mat m, int side) {
        if (m.state != Mat.State.FIGHTING || m.turn != side || m.busy) return;
        DisplayManager.Placed ba = null, bt = null; Move bmv = null; double bs = -1e9;
        for (var a : m.cards[side]) {
            if (a == null || a.ko || a.acted) continue;
            for (Move mv : a.card.moves()) {
                int cost = Rules.moveCost(a.card, mv);
                if (cost > m.energy[side]) continue;
                for (var t : m.cards[1 - side]) {
                    if (t == null || t.ko) continue;
                    int d = bm.damage(m, side, a, mv, t, false);
                    double s = d + (d >= t.hp ? 100 : 0) - cost * 2;
                    if (s > bs) { bs = s; ba = a; bt = t; bmv = mv; }
                }
            }
        }
        if (ba != null) bm.attack(m, side, ba, bmv, bt);
        else bm.endTurn(m, side);
    }

    private static void useItem(BattleManager bm, Mat m, int side) {
        if (m.itemUsed[side]) return;
        List<DisplayManager.Placed> mine = new ArrayList<>();
        for (var c : m.cards[side]) if (c != null && !c.ko) mine.add(c);
        mine.sort(Comparator.comparingDouble(c -> c.hp / (double) c.maxHp));
        DisplayManager.Placed hurt = mine.isEmpty() ? null : mine.get(0);
        DisplayManager.Placed weakest = null;
        for (var c : m.cards[1 - side]) if (c != null && !c.ko && (weakest == null || c.hp < weakest.hp)) weakest = c;
        int bestIdx = -1; double bestScore = 0; DisplayManager.Placed bestTarget = null;
        for (int i = 0; i < m.hand[side].size(); i++) {
            HandCard h = m.hand[side].get(i);
            if (h.isChar() || h.item.cost > m.energy[side]) continue;
            ItemType t = h.item;
            double s = 0; DisplayManager.Placed target = null;
            if (t.heals() && hurt != null) { int missing = hurt.maxHp - hurt.hp; if (missing >= t.amount * 0.7) { s = Math.min(t.amount, missing) * 1.2; target = hurt; } }
            else if (t == ItemType.RALLY_HORN) { int sum = 0; for (var c : mine) sum += Math.min(6, c.maxHp - c.hp); if (sum >= 12) s = sum; }
            else if (t.kind == ItemType.Kind.TRAP && m.traps[side].size() < 2) s = 7 + t.rarity.ordinal();
            else if (t == ItemType.ENERGY_DRINK && m.energy[side] <= 3) s = 6;
            else if ((t == ItemType.SCOUTS_MAP || t == ItemType.DRAGONS_HOARD) && m.hand[side].size() <= 4 && m.deck[side].size() >= 5) s = t == ItemType.DRAGONS_HOARD ? 9 : 5;
            else if (t == ItemType.WHETSTONE) s = 4;
            else if (t == ItemType.WAR_DRUM) s = 9;
            else if (t == ItemType.SPRINT_SHOES) { for (var c : mine) if (c.acted) { s = 5; target = c; break; } }
            else if (t == ItemType.RECALL_SCROLL && m.discard[side].stream().anyMatch(HandCard::isChar)) s = 6;
            else if (t == ItemType.THUNDER_SCROLL && weakest != null) { s = weakest.hp <= t.amount ? 20 : 8; target = weakest; }
            if (s > bestScore) { bestScore = s; bestIdx = i; bestTarget = target; }
        }
        if (bestIdx >= 0) bm.useItem(m, side, bestIdx, bestTarget);
    }
}
