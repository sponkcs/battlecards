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
 * Turn menu laid out like the mat seen from your seat (6 rows):
 *
 *   row 0  . . [enemy] . [enemy] . [enemy] . .      enemy cards, in the order you see them on the mat
 *   row 1  . . . . [what to do next] . . . .        a single hint that says what to click
 *   row 2  . . [you] . [you] . [you] . .            your cards
 *   row 3  . . [move 1] . [move 1] . [move 1] . .   each card's moves sit right under it
 *   row 4  . . [move 2] . [move 2] . [move 2] . .
 *   row 5  [energy] . [item] [item] [traps] . [rest] . [forfeit]
 *
 * Attacking is two clicks: a move under one of your cards, then an enemy card.
 */
public final class BattleMenu implements InventoryHolder {
    private static final int[] COLS = {2, 4, 6};
    private static final int ENEMY_ROW = 0, HINT = 13, OWN_ROW = 18, MOVE1_ROW = 27, MOVE2_ROW = 36;
    private static final int ENERGY = 45, ITEM = 47, TRAPS = 49, REST = 51, FORFEIT = 53;

    private final SneakyCardsPlugin plugin;
    private final Player player;
    private final Mat mat;
    private final int side;
    private final Inventory inv;
    private int selCard = -1, selMove = -1, selItem = -1;

    BattleMenu(SneakyCardsPlugin plugin, Player player, Mat mat) {
        this.plugin = plugin;
        this.player = player;
        this.mat = mat;
        this.side = mat.sideOf(player.getUniqueId());
        boolean mine = mat.turn == side;
        this.inv = Bukkit.createInventory(this, 54, Component.text(mine
                ? "Your turn  ⚡ " + mat.energy[side] + "/" + plugin.rules().energyMax()
                : "Opponent's turn"));
        render();
    }

    public void open() { player.openInventory(inv); }
    @Override public Inventory getInventory() { return inv; }

    /** Enemy column shown in menu position i: the enemy's cards appear mirrored from your seat. */
    private int enemyCol(int i) { return 2 - i; }

    private DisplayManager.Placed selectedCard() { return selCard < 0 ? null : mat.cards[side][selCard]; }
    private Move selectedMove() { var c = selectedCard(); return c == null || selMove < 0 ? null : c.card.moves().get(selMove); }
    private ItemType selectedItem() { return selItem >= 0 && selItem < mat.hand[side].size() ? mat.hand[side].get(selItem) : null; }

    private static Component line(String s, TextColor c) { return Items.t(s, c); }

    private ItemStack blank() { return plugin.items().menu("blank", " ", NamedTextColor.GRAY, 1); }

    private ItemStack named(ItemStack base, Component name, List<Component> lore, boolean glint) {
        base.editMeta(m -> { m.displayName(name); m.lore(lore); if (glint) m.setEnchantmentGlintOverride(true); });
        return base;
    }

    private void render() {
        inv.clear();
        for (int i = 0; i < 54; i++) inv.setItem(i, blank());
        boolean myTurn = mat.turn == side && !mat.busy && mat.state == Mat.State.FIGHTING;
        Move mv = selectedMove();
        ItemType item = selectedItem();

        // enemy row
        for (int i = 0; i < 3; i++) {
            var tgt = mat.cards[1 - side][enemyCol(i)];
            inv.setItem(ENEMY_ROW + COLS[i], enemyIcon(tgt, mv));
        }
        // your cards and their moves
        for (int i = 0; i < 3; i++) {
            var pl = mat.cards[side][i];
            inv.setItem(OWN_ROW + COLS[i], ownIcon(pl, item));
            for (int k = 0; k < 2; k++) inv.setItem((k == 0 ? MOVE1_ROW : MOVE2_ROW) + COLS[i], moveIcon(pl, i, k, myTurn));
        }
        // hint
        String hint; NamedTextColor hc = NamedTextColor.YELLOW;
        if (!myTurn) { hint = mat.turn == side ? "Wait for the move to finish" : "Waiting for your opponent"; hc = NamedTextColor.GRAY; }
        else if (item != null) hint = "Click one of YOUR cards to heal it";
        else if (mv != null) hint = "Now click an ENEMY card to use " + mv.name();
        else hint = "Click a move under one of your cards";
        inv.setItem(HINT, plugin.items().menu("info", hint, hc, 1,
                "Attack: click a move, then an enemy card.",
                "Items (bottom row) don't end your turn.",
                "Strength > Magicing > Dexterity > Strength",
                "Each card acts once per round."));

        // bottom bar
        int e = mat.energy[side], max = plugin.rules().energyMax();
        inv.setItem(ENERGY, plugin.items().menu("random", "Energy " + e + "/" + max, NamedTextColor.AQUA, Math.max(1, e),
                "⚡".repeat(e) + "·".repeat(Math.max(0, max - e)),
                "+" + plugin.rules().energyPerTurn() + " at the start of each of your turns",
                "Enemy energy: " + mat.energy[1 - side]));
        for (int k = 0; k < 2; k++) inv.setItem(ITEM + k, itemIcon(k, myTurn));
        inv.setItem(TRAPS, plugin.items().menu("info", "Traps: you " + mat.traps[side].size() + "  ·  enemy " + mat.traps[1 - side].size(),
                NamedTextColor.LIGHT_PURPLE, Math.max(1, mat.traps[1 - side].size()), "Enemy traps stay hidden until they spring"));
        inv.setItem(REST, plugin.items().menu("reset", "Rest (end turn)", myTurn ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY, 1,
                "Skip your turn and gain +" + plugin.rules().restBonus() + " extra energy"));
        inv.setItem(FORFEIT, plugin.items().menu("close", "Forfeit", NamedTextColor.RED, 1, "Give up and let your opponent win"));
    }

    private ItemStack enemyIcon(DisplayManager.Placed tgt, Move mv) {
        Items it = plugin.items();
        if (tgt == null) return blank();
        if (tgt.ko) return it.menu("close", tgt.card.name() + " (knocked out)", NamedTextColor.DARK_GRAY, 1);
        List<Component> lore = new ArrayList<>();
        lore.add(line("Health " + tgt.hp + "/" + tgt.maxHp, hpColor(tgt)));
        lore.add(line("Type: " + Rules.type(tgt.card).label, NamedTextColor.AQUA));
        if (mv != null) {
            var att = selectedCard();
            double mult = plugin.rules().typeMultiplier(mv, tgt.card);
            int dmg = plugin.rules().damage(att.card, mv, tgt.card, false);
            lore.add(Component.empty());
            if (mult > 1) lore.add(line("Super effective!", NamedTextColor.GREEN));
            else if (mult < 1) lore.add(line("Not very effective", NamedTextColor.RED));
            lore.add(line("Click to hit for " + dmg + (dmg >= tgt.hp ? " (knocks it out!)" : ""), NamedTextColor.YELLOW));
        } else lore.add(line("Pick a move first", NamedTextColor.DARK_GRAY));
        return named(it.card(tgt.card), line(tgt.card.name(), tgt.card.rarity().color()), lore, mv != null);
    }

    private ItemStack ownIcon(DisplayManager.Placed pl, ItemType healing) {
        Items it = plugin.items();
        if (pl == null) return blank();
        if (pl.ko) return it.menu("close", pl.card.name() + " (knocked out)", NamedTextColor.DARK_GRAY, 1);
        List<Component> lore = new ArrayList<>();
        lore.add(line("Health " + pl.hp + "/" + pl.maxHp, hpColor(pl)));
        lore.add(line("Type: " + Rules.type(pl.card).label, NamedTextColor.AQUA));
        if (pl.acted) lore.add(line("Already acted this round", NamedTextColor.GRAY));
        if (pl.stunned) lore.add(line("Stunned: skips its next action", NamedTextColor.RED));
        if (healing != null) lore.add(line("Click to heal +" + Math.min(healing.amount, pl.maxHp - pl.hp), NamedTextColor.GREEN));
        Component name = line(pl.card.name() + (pl.acted ? "  (done)" : ""), pl.acted ? NamedTextColor.DARK_GRAY : pl.card.rarity().color());
        return named(it.card(pl.card), name, lore, healing != null);
    }

    private ItemStack moveIcon(DisplayManager.Placed pl, int col, int k, boolean myTurn) {
        Items it = plugin.items();
        if (pl == null || pl.ko) return blank();
        Move mv = pl.card.moves().get(k);
        int cost = Rules.moveCost(pl.card, mv);
        int base = (int) Math.round(pl.card.power(mv) * plugin.rules().damageScale());
        boolean can = myTurn && !pl.acted && mat.energy[side] >= cost;
        boolean sel = selCard == col && selMove == k;
        String why = !myTurn ? "Not your turn" : pl.acted ? pl.card.name() + " already acted this round"
                : mat.energy[side] < cost ? "Needs " + cost + " energy (you have " + mat.energy[side] + ")"
                : sel ? "Selected: now click an enemy card" : "Click to choose";
        return it.menu(can ? mv.stat().icon : "blank", mv.name() + "  ⚡" + cost, can ? (sel ? NamedTextColor.GOLD : NamedTextColor.YELLOW) : NamedTextColor.DARK_GRAY, 1,
                base + " damage (" + mv.stat().label + ")" + (Rules.refund(mv) > 0 ? ", refunds 1 energy" : ""),
                "Critical chance " + Math.min(100, pl.card.stat(Stat.LUCK)) + "%",
                why);
    }

    private ItemStack itemIcon(int k, boolean myTurn) {
        Items it = plugin.items();
        if (k >= mat.hand[side].size()) return it.menu("blank", "No item", NamedTextColor.DARK_GRAY, 1, "Bring item cards before a battle:", "right-click your side of the mat");
        ItemType t = mat.hand[side].get(k);
        boolean can = myTurn && !mat.itemUsedThisTurn && mat.energy[side] >= t.cost;
        String how = !myTurn ? "Not your turn" : mat.itemUsedThisTurn ? "One item per turn" : mat.energy[side] < t.cost ? "Not enough energy"
                : t.heals() ? (selItem == k ? "Now click one of your cards" : "Click, then click your card")
                : t.kind == ItemType.Kind.TRAP ? "Click to set it face down" : "Click to use";
        List<Component> lore = List.of(
                line(t.rarity.label + (t.kind == ItemType.Kind.TRAP ? " trap" : " item") + "  ⚡" + t.cost, t.rarity.color()),
                line(t.description, NamedTextColor.WHITE),
                line(how, can ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY),
                line("Doesn't end your turn", NamedTextColor.DARK_GRAY));
        return named(it.itemCard(t), line(t.label, can ? t.rarity.color() : NamedTextColor.DARK_GRAY), lore, selItem == k);
    }

    private static NamedTextColor hpColor(DisplayManager.Placed p) {
        double r = p.hp / (double) Math.max(1, p.maxHp);
        return r > 0.5 ? NamedTextColor.GREEN : r > 0.25 ? NamedTextColor.YELLOW : NamedTextColor.RED;
    }

    private int colAt(int slot, int rowStart) {
        for (int i = 0; i < 3; i++) if (slot == rowStart + COLS[i]) return i;
        return -1;
    }

    public void click(int slot) {
        if (mat.state != Mat.State.FIGHTING) return;
        if (slot == FORFEIT) { player.closeInventory(); plugin.battles().forfeit(player); return; }
        if (mat.turn != side || mat.busy) { BattleManager.say(player, "Wait for your turn.", NamedTextColor.YELLOW); return; }
        if (slot == REST) { player.closeInventory(); plugin.battles().rest(player); return; }

        if (slot == ITEM || slot == ITEM + 1) {
            int k = slot - ITEM;
            if (k >= mat.hand[side].size()) return;
            ItemType t = mat.hand[side].get(k);
            if (t.heals()) { selItem = selItem == k ? -1 : k; selCard = -1; selMove = -1; render(); return; }
            plugin.battles().useItem(player, mat, k, null);
            selItem = -1; render(); return;
        }

        int own = colAt(slot, OWN_ROW);
        if (own >= 0 && selItem >= 0) {
            plugin.battles().useItem(player, mat, selItem, mat.cards[side][own]);
            selItem = -1; render(); return;
        }

        int m1 = colAt(slot, MOVE1_ROW), m2 = colAt(slot, MOVE2_ROW);
        if (m1 >= 0 || m2 >= 0) {
            int col = m1 >= 0 ? m1 : m2, k = m1 >= 0 ? 0 : 1;
            var pl = mat.cards[side][col];
            if (pl == null || pl.ko) return;
            if (pl.acted) { BattleManager.say(player, pl.card.name() + " already acted this round.", NamedTextColor.YELLOW); return; }
            if (mat.energy[side] < Rules.moveCost(pl.card, pl.card.moves().get(k))) { BattleManager.say(player, "Not enough energy for that move.", NamedTextColor.YELLOW); return; }
            boolean same = selCard == col && selMove == k;
            selCard = same ? -1 : col; selMove = same ? -1 : k; selItem = -1;
            render(); return;
        }

        int en = colAt(slot, ENEMY_ROW);
        if (en >= 0) {
            var tgt = mat.cards[1 - side][enemyCol(en)];
            if (tgt == null || tgt.ko) return;
            var att = selectedCard();
            Move mv = selectedMove();
            if (att == null || mv == null) { BattleManager.say(player, "First click a move under one of your cards.", NamedTextColor.YELLOW); return; }
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            plugin.battles().attack(player, mat, att, mv, tgt);
        }
    }
}
