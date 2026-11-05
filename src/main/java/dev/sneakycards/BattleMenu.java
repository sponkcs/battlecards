package dev.sneakycards;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;

/**
 * Battle menu, laid out like the mat from your seat (6 rows):
 *
 *   row 0  [rival info] . [enemy] . [enemy] . [enemy] . [rival traps]
 *   row 1  . . [you] . [you] . [you] . .            your three slots
 *   row 2  . . [move 1] . [move 1] . [move 1] . .    each card's moves sit right under it
 *   row 3  . . [move 2] . [move 2] . [move 2] . .
 *   row 4  . [hand] [hand] [hand] [hand] [hand] [hand] [hand] .
 *   row 5  [energy] . [points] . [what to do next] . [end turn / ready] . [forfeit]
 *
 * Setup: click a character in your hand, then one of your slots, to put it face down. Click Ready.
 * Your turn: play a character (hand, then an empty slot), use an item (hand; some then ask for a card),
 * and attack (a move, then an enemy card). Attacking ends your turn.
 */
public final class BattleMenu implements InventoryHolder {
    private static final int[] COLS = {2, 4, 6};
    private static final int FOE_INFO = 0, FOE_TRAPS = 8, OWN_ROW = 9, MOVE1_ROW = 18, MOVE2_ROW = 27, HAND = 37;
    private static final int ENERGY = 45, POINTS = 47, HINT = 49, ACTION = 51, FORFEIT = 53;

    private final SneakyCardsPlugin plugin;
    private final Player player;
    private final Mat mat;
    private final int side;
    private final Inventory inv;
    private int selCard = -1, selMove = -1, selHand = -1;

    BattleMenu(SneakyCardsPlugin plugin, Player player, Mat mat) {
        this.plugin = plugin;
        this.player = player;
        this.mat = mat;
        this.side = mat.sideOf(player.getUniqueId());
        String title = mat.state == Mat.State.SETUP ? "Set up your cards"
                : mat.turn == side ? "Your turn  ⚡ " + mat.energy[side] + "/" + plugin.rules().energyMax() : "Rival's turn";
        this.inv = Bukkit.createInventory(this, 54, Component.text(title));
        render();
    }

    public void open() { player.openInventory(inv); }
    @Override public Inventory getInventory() { return inv; }

    private BattleManager bm() { return plugin.battles(); }
    private boolean setup() { return mat.state == Mat.State.SETUP; }
    private boolean myTurn() { return mat.state == Mat.State.FIGHTING && mat.turn == side && !mat.busy; }
    private int enemyCol(int i) { return 2 - i; }   // the rival's cards appear mirrored from your seat
    private DisplayManager.Placed selectedCard() { return selCard < 0 ? null : mat.cards[side][selCard]; }
    private Move selectedMove() { var c = selectedCard(); return c == null || selMove < 0 ? null : c.card.moves().get(selMove); }
    private HandCard selectedHand() { return selHand >= 0 && selHand < mat.hand[side].size() ? mat.hand[side].get(selHand) : null; }

    private static Component line(String s, TextColor c) { return Items.t(s, c); }
    private ItemStack blank() { return plugin.items().menu("blank", " ", NamedTextColor.GRAY, 1); }
    private ItemStack named(ItemStack base, Component name, List<Component> lore, boolean glint) {
        base.editMeta(m -> { m.displayName(name); m.lore(lore); if (glint) m.setEnchantmentGlintOverride(true); });
        return base;
    }

    private void render() {
        inv.clear();
        for (int i = 0; i < 54; i++) inv.setItem(i, blank());
        Items it = plugin.items();
        Move mv = selectedMove();
        HandCard sel = selectedHand();
        int foe = 1 - side;

        // rival row
        inv.setItem(FOE_INFO, it.menu("info", BattleManager.nameOf(mat.players[foe]), NamedTextColor.RED, Math.max(1, mat.points[foe]),
                "Points: " + mat.points[foe] + "/" + plugin.getConfig().getInt("rules.points-to-win", 3),
                "Hand: " + mat.hand[foe].size() + "   Deck: " + mat.deck[foe].size() + "   Discard: " + mat.discard[foe].size(),
                "Energy: " + mat.energy[foe]));
        inv.setItem(FOE_TRAPS, it.menu("info", "Rival traps: " + mat.traps[foe].size(), NamedTextColor.LIGHT_PURPLE, Math.max(1, mat.traps[foe].size()),
                "Hidden until they spring", "Your traps: " + mat.traps[side].size()));
        for (int i = 0; i < 3; i++) inv.setItem(COLS[i], enemyIcon(mat.cards[foe][enemyCol(i)], mv, sel));

        // your slots and moves
        for (int i = 0; i < 3; i++) {
            var pl = mat.cards[side][i];
            inv.setItem(OWN_ROW + COLS[i], ownIcon(pl, sel));
            if (!setup()) for (int k = 0; k < 2; k++) inv.setItem((k == 0 ? MOVE1_ROW : MOVE2_ROW) + COLS[i], moveIcon(pl, i, k));
        }

        // hand
        List<HandCard> hand = mat.hand[side];
        for (int i = 0; i < 7; i++) {
            if (i >= hand.size()) { inv.setItem(HAND + i, it.menu("blank", "Empty hand slot", NamedTextColor.DARK_GRAY, 1)); continue; }
            inv.setItem(HAND + i, handIcon(hand.get(i), i));
        }

        // bottom bar
        int e = mat.energy[side], max = plugin.rules().energyMax();
        inv.setItem(ENERGY, it.menu("random", "Energy " + e + "/" + max, NamedTextColor.AQUA, Math.max(1, e),
                "⚡".repeat(Math.max(0, e)) + "·".repeat(Math.max(0, max - e)), "+" + plugin.rules().energyPerTurn() + " at the start of each of your turns"));
        inv.setItem(POINTS, it.menu("gem_legendary", "Your points: " + mat.points[side] + "/" + plugin.getConfig().getInt("rules.points-to-win", 3), NamedTextColor.GOLD,
                Math.max(1, mat.points[side]), "Deck: " + mat.deck[side].size() + "   Discard: " + mat.discard[side].size(), "Each knockout scores 1 point"));
        inv.setItem(HINT, it.menu("info", hint(sel, mv), NamedTextColor.YELLOW, 1,
                "Attack: a move under your card, then an enemy card.", "Attacking ends your turn.",
                "1 character and 1 item per turn.", "Strength > Magicing > Dexterity > Strength"));
        if (setup()) inv.setItem(ACTION, it.menu("create", mat.ready[side] ? "Ready (waiting for rival)" : "Ready", mat.ready[side] ? NamedTextColor.GRAY : NamedTextColor.GREEN, 1,
                "Starts the battle once both players are ready", "You need at least 1 character face down"));
        else inv.setItem(ACTION, it.menu("reset", "End turn", myTurn() ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY, 1, "Pass without attacking"));
        inv.setItem(FORFEIT, it.menu("close", "Forfeit", NamedTextColor.RED, 1, "Give up and let your rival win"));
    }

    private String hint(HandCard sel, Move mv) {
        if (setup()) return mat.ready[side] ? "Waiting for your rival to get ready" : sel != null ? "Now click one of your 3 slots" : "Click a character in your hand, then a slot";
        if (!myTurn()) return mat.turn == side ? "Wait for the move to finish" : "Waiting for your rival";
        if (sel != null) {
            if (sel.isChar()) return "Click an empty slot on your side to play " + sel.name();
            return switch (sel.item.target()) { case OWN -> "Click one of YOUR cards for " + sel.name(); case ENEMY -> "Click an ENEMY card for " + sel.name(); default -> "Using " + sel.name(); };
        }
        if (mv != null) return "Now click an ENEMY card to use " + mv.name();
        return "Play a card from your hand, or click a move to attack";
    }

    private ItemStack enemyIcon(DisplayManager.Placed tgt, Move mv, HandCard sel) {
        Items it = plugin.items();
        if (tgt == null) return it.menu("blank", "Empty slot", NamedTextColor.DARK_GRAY, 1);
        if (tgt.faceDown) return named(it.cardBack(), line("Face-down card", NamedTextColor.GRAY), List.of(), false);
        if (tgt.ko) return it.menu("close", tgt.card.name() + " (knocked out)", NamedTextColor.DARK_GRAY, 1);
        List<Component> lore = new ArrayList<>();
        lore.add(line("Health " + tgt.hp + "/" + tgt.maxHp, hpColor(tgt)));
        lore.add(line("Type: " + Rules.type(tgt.card).label, NamedTextColor.AQUA));
        boolean glint = false;
        if (mv != null) {
            var att = selectedCard();
            double mult = plugin.rules().typeMultiplier(mv, tgt.card);
            int dmg = bm().damage(mat, side, att, mv, tgt, false);
            lore.add(Component.empty());
            if (mult > 1) lore.add(line("Super effective!", NamedTextColor.GREEN));
            else if (mult < 1) lore.add(line("Not very effective", NamedTextColor.RED));
            lore.add(line("Click to hit for " + dmg + (dmg >= tgt.hp ? " (knocks it out!)" : ""), NamedTextColor.YELLOW));
            glint = true;
        } else if (sel != null && !sel.isChar() && sel.item.target() == ItemType.Target.ENEMY) {
            lore.add(line("Click to use " + sel.name() + " here", NamedTextColor.YELLOW)); glint = true;
        }
        return named(it.card(tgt.card, tgt.holo), line(tgt.card.name(), tgt.card.rarity().color()), lore, glint);
    }

    private ItemStack ownIcon(DisplayManager.Placed pl, HandCard sel) {
        Items it = plugin.items();
        if (pl == null) {
            boolean target = sel != null && sel.isChar() && (setup() || (myTurn() && !mat.playedChar[side]));
            return it.menu(target ? "create" : "blank", target ? "Play " + sel.name() + " here" : "Empty slot", target ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY, 1,
                    target ? "Click to place it" : "Play a character here from your hand");
        }
        if (pl.ko) return it.menu("close", pl.card.name() + " (knocked out)", NamedTextColor.DARK_GRAY, 1);
        List<Component> lore = new ArrayList<>();
        lore.add(line("Health " + pl.hp + "/" + pl.maxHp, hpColor(pl)));
        lore.add(line("Type: " + Rules.type(pl.card).label, NamedTextColor.AQUA));
        if (setup()) lore.add(line(mat.ready[side] ? "Face down" : "Face down. Click to take it back", NamedTextColor.GRAY));
        if (pl.acted && !setup()) lore.add(line("Already acted this round", NamedTextColor.GRAY));
        if (pl.stunned) lore.add(line("Stunned: skips its next action", NamedTextColor.RED));
        boolean target = sel != null && !sel.isChar() && sel.item.target() == ItemType.Target.OWN;
        if (target) lore.add(line("Click to use " + sel.name() + " here", NamedTextColor.GREEN));
        Component name = line((pl.holo ? "✦ " : "") + pl.card.name() + (pl.acted && !setup() ? "  (done)" : ""), pl.acted && !setup() ? NamedTextColor.DARK_GRAY : pl.card.rarity().color());
        return named(it.card(pl.card, pl.holo), name, lore, target);
    }

    private ItemStack moveIcon(DisplayManager.Placed pl, int col, int k) {
        Items it = plugin.items();
        if (pl == null || pl.ko || pl.faceDown) return blank();
        Move mv = pl.card.moves().get(k);
        int cost = Rules.moveCost(pl.card, mv);
        int base = (int) Math.round(pl.card.power(mv) * plugin.rules().damageScale());
        boolean can = myTurn() && !pl.acted && mat.energy[side] >= cost;
        boolean sel = selCard == col && selMove == k;
        String why = !myTurn() ? "Not your turn" : pl.acted ? pl.card.name() + " already acted this round"
                : mat.energy[side] < cost ? "Needs " + cost + " energy (you have " + mat.energy[side] + ")"
                : sel ? "Selected: now click an enemy card" : "Click to choose";
        return it.menu(can ? mv.stat().icon : "blank", mv.name() + "  ⚡" + cost, can ? (sel ? NamedTextColor.GOLD : NamedTextColor.YELLOW) : NamedTextColor.DARK_GRAY, 1,
                base + " damage (" + mv.stat().label + ")" + (Rules.refund(mv) > 0 ? ", refunds 1 energy" : ""),
                "Critical chance " + Math.min(100, pl.card.stat(Stat.LUCK)) + "%", why);
    }

    private boolean playable(HandCard h) {
        if (setup()) return h.isChar() && !mat.ready[side] && mat.emptySlot(side) >= 0;
        if (!myTurn()) return false;
        if (h.isChar()) return !mat.playedChar[side] && mat.emptySlot(side) >= 0;
        if (mat.itemUsed[side] || mat.energy[side] < h.item.cost) return false;
        if (h.item.kind == ItemType.Kind.TRAP) return mat.traps[side].size() < 2;
        return true;
    }

    private String whyNot(HandCard h) {
        if (setup()) return h.isChar() ? "Your 3 slots are full" : "Items are played during the battle";
        if (!myTurn()) return "Not your turn";
        if (h.isChar()) return mat.playedChar[side] ? "1 character per turn" : "No empty slot";
        if (mat.itemUsed[side]) return "1 item per turn";
        if (mat.energy[side] < h.item.cost) return "Needs " + h.item.cost + " energy";
        return "You already have 2 traps set";
    }

    private ItemStack handIcon(HandCard h, int i) {
        ItemStack s = plugin.items().of(h);
        boolean can = playable(h);
        List<Component> lore = new ArrayList<>();
        if (h.isChar()) {
            lore.add(line(h.rarity().label + " · " + Rules.type(h.card).label + " · Health " + h.card.stat(Stat.HEALTH), h.rarity().color()));
            for (Move mv : h.card.moves()) lore.add(line(mv.name() + " (" + mv.stat().label + " " + h.card.power(mv) + ", ⚡" + Rules.moveCost(h.card, mv) + ")", NamedTextColor.GRAY));
        } else {
            lore.add(line(h.rarity().label + (h.item.kind == ItemType.Kind.TRAP ? " trap" : " item") + "  ⚡" + h.item.cost, h.rarity().color()));
            lore.add(line(h.item.description, NamedTextColor.WHITE));
        }
        lore.add(line(can ? (selHand == i ? "Selected" : "PLAY: click to " + (h.isChar() ? "play" : h.item.kind == ItemType.Kind.TRAP ? "set face down" : "use")) : whyNot(h),
                can ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
        Component name = line((h.holo ? "✦ " : "") + h.name(), can ? h.rarity().color() : NamedTextColor.DARK_GRAY);
        return named(s, name, lore, selHand == i);
    }

    private static NamedTextColor hpColor(DisplayManager.Placed p) {
        double r = p.hp / (double) Math.max(1, p.maxHp);
        return r > 0.5 ? NamedTextColor.GREEN : r > 0.25 ? NamedTextColor.YELLOW : NamedTextColor.RED;
    }

    private int colAt(int slot, int rowStart) {
        for (int i = 0; i < 3; i++) if (slot == rowStart + COLS[i]) return i;
        return -1;
    }

    private void clearSel() { selCard = -1; selMove = -1; selHand = -1; }

    public void click(int slot) {
        if (slot == FORFEIT) { player.closeInventory(); bm().forfeit(player); return; }
        int own = colAt(slot, OWN_ROW);
        int hi = slot >= HAND && slot < HAND + 7 ? slot - HAND : -1;

        if (setup()) {
            if (slot == ACTION) { bm().setupReady(player, mat); render(); return; }
            if (hi >= 0 && hi < mat.hand[side].size() && playable(mat.hand[side].get(hi))) { selHand = selHand == hi ? -1 : hi; render(); return; }
            if (own >= 0) {
                if (mat.cards[side][own] == null && selectedHand() != null) bm().setupPlace(mat, side, selHand, own);
                else if (mat.cards[side][own] != null) bm().setupTakeBack(mat, side, own);
                clearSel(); render();
            }
            return;
        }
        if (mat.state != Mat.State.FIGHTING) return;
        if (!myTurn()) { BattleManager.say(player, "Wait for your turn.", NamedTextColor.YELLOW); return; }
        if (slot == ACTION) { player.closeInventory(); bm().endTurn(player); return; }

        if (hi >= 0) {
            if (hi >= mat.hand[side].size()) return;
            HandCard h = mat.hand[side].get(hi);
            if (!playable(h)) { BattleManager.say(player, whyNot(h) + ".", NamedTextColor.YELLOW); return; }
            if (!h.isChar() && h.item.target() == ItemType.Target.NONE) { bm().useItem(mat, side, hi, null); clearSel(); render(); return; }
            selHand = selHand == hi ? -1 : hi; selCard = -1; selMove = -1; render(); return;
        }
        HandCard sel = selectedHand();
        if (own >= 0) {
            var pl = mat.cards[side][own];
            if (sel != null && sel.isChar() && pl == null) { bm().playCharacter(mat, side, selHand, own); clearSel(); render(); return; }
            if (sel != null && !sel.isChar() && sel.item.target() == ItemType.Target.OWN && pl != null) { bm().useItem(mat, side, selHand, pl); clearSel(); render(); return; }
            return;
        }
        int m1 = colAt(slot, MOVE1_ROW), m2 = colAt(slot, MOVE2_ROW);
        if (m1 >= 0 || m2 >= 0) {
            int col = m1 >= 0 ? m1 : m2, k = m1 >= 0 ? 0 : 1;
            var pl = mat.cards[side][col];
            if (pl == null || pl.ko || pl.faceDown) return;
            if (pl.acted) { BattleManager.say(player, pl.card.name() + " already acted this round.", NamedTextColor.YELLOW); return; }
            if (mat.energy[side] < Rules.moveCost(pl.card, pl.card.moves().get(k))) { BattleManager.say(player, "Not enough energy for that move.", NamedTextColor.YELLOW); return; }
            boolean same = selCard == col && selMove == k;
            clearSel();
            if (!same) { selCard = col; selMove = k; }
            render(); return;
        }
        int en = colAt(slot, 0);
        if (en >= 0) {
            var tgt = mat.cards[1 - side][enemyCol(en)];
            if (tgt == null || tgt.ko || tgt.faceDown) return;
            if (sel != null && !sel.isChar() && sel.item.target() == ItemType.Target.ENEMY) { bm().useItem(mat, side, selHand, tgt); clearSel(); render(); return; }
            var att = selectedCard();
            Move mv = selectedMove();
            if (att == null || mv == null) { BattleManager.say(player, "First click a move under one of your cards.", NamedTextColor.YELLOW); return; }
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            bm().attack(player, mat, att, mv, tgt);
        }
    }
}
