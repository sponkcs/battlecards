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
 * The turn controls for a battle on the mat, laid out like the mat from your seat (6 rows):
 *
 *   row 0  [rival info] . [rival bench] . [RIVAL ACTIVE] . [rival bench] . [rival traps]
 *   row 1  . . [your bench] . [YOUR ACTIVE] . [your bench] . .
 *   row 2  . . . [move 1] . [move 2] . . .          your Active's moves
 *   row 3  . . . . [retreat] . . . .
 *   row 4  . [hand] [hand] [hand] [hand] [hand] [hand] [hand] .
 *   row 5  [mana] . [points] . [what to do next] . [end turn / ready] . [forfeit]
 *
 * Setup: click a character in your hand, then a slot (the middle one is your Active). Click Ready.
 * Your turn: click Mana then one of your cards to attach it, bench characters from your hand, play 1 item or trap,
 * retreat (Retreat, then a benched card), and attack by clicking one of your Active's moves.
 */
public final class BattleMenu implements InventoryHolder {
    private static final int[] COLS = {2, 4, 6};
    private static final int FOE_INFO = 0, FOE_TRAPS = 8, OWN_ROW = 9, MOVE1 = 21, MOVE2 = 23, RETREAT = 31, HAND = 37;
    private static final int MANA = 45, POINTS = 47, HINT = 49, ACTION = 51, FORFEIT = 53;

    private enum Mode { NONE, MANA, RETREAT, ITEM, SETUP_PLACE }

    private final SneakyCardsPlugin plugin;
    private final Player player;
    private final Mat mat;
    private final int side;
    private final Inventory inv;
    private Mode mode = Mode.NONE;
    private int selHand = -1;

    BattleMenu(SneakyCardsPlugin plugin, Player player, Mat mat) {
        this.plugin = plugin;
        this.player = player;
        this.mat = mat;
        this.side = mat.sideOf(player.getUniqueId());
        String title = mat.state == Mat.State.SETUP ? "Set up your cards"
                : mat.promoting[side] ? "Pick your new Active"
                : mat.turn == side ? "Your turn" + (mat.mana[side] > 0 ? "  ◆ mana to attach" : "") : "Rival's turn";
        this.inv = Bukkit.createInventory(this, 54, Component.text(title));
        render();
    }

    public void open() { player.openInventory(inv); }
    @Override public Inventory getInventory() { return inv; }
    void refresh() { render(); }

    private BattleManager bm() { return plugin.battles(); }
    private boolean setup() { return mat.state == Mat.State.SETUP; }
    private boolean myTurn() { return bm().myTurn(mat, side); }
    private boolean promoting() { return mat.state == Mat.State.FIGHTING && mat.promoting[side]; }
    private int enemyCol(int i) { return 2 - i; }   // the rival's cards appear mirrored from your seat
    private HandCard selectedHand() { return selHand >= 0 && selHand < mat.hand[side].size() ? mat.hand[side].get(selHand) : null; }
    private ItemType selectedItem() { HandCard h = selectedHand(); return mode == Mode.ITEM && h != null && !h.isChar() ? h.item : null; }

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
        int foe = 1 - side, toWin = plugin.rules().pointsToWin();

        // rival row
        inv.setItem(FOE_INFO, it.menu("info", BattleManager.nameOf(mat.players[foe]), NamedTextColor.RED, Math.max(1, mat.points[foe]),
                "Points: " + mat.points[foe] + "/" + toWin,
                "Hand: " + mat.hand[foe].size() + "   Deck: " + mat.deck[foe].size() + "   Discard: " + mat.discard[foe].size()));
        inv.setItem(FOE_TRAPS, it.menu("info", "Rival traps: " + mat.traps[foe].size(), NamedTextColor.LIGHT_PURPLE, Math.max(1, mat.traps[foe].size()),
                "Hidden until they spring", "Your traps: " + mat.traps[side].size()));
        for (int i = 0; i < 3; i++) inv.setItem(COLS[i], enemyIcon(mat.cards[foe][enemyCol(i)], enemyCol(i)));

        // your slots, your Active's moves and retreat
        for (int i = 0; i < 3; i++) inv.setItem(OWN_ROW + COLS[i], ownIcon(mat.cards[side][i], i));
        if (!setup()) {
            var act = mat.active(side);
            inv.setItem(MOVE1, moveIcon(act, 0));
            inv.setItem(MOVE2, moveIcon(act, 1));
            inv.setItem(RETREAT, retreatIcon(act));
        }

        // hand
        List<HandCard> hand = mat.hand[side];
        for (int i = 0; i < 7; i++) {
            if (i >= hand.size()) { inv.setItem(HAND + i, it.menu("blank", "Empty hand slot", NamedTextColor.DARK_GRAY, 1)); continue; }
            inv.setItem(HAND + i, handIcon(hand.get(i), i));
        }

        // bottom bar
        if (!setup()) {
            int mn = mat.turn == side ? mat.mana[side] : 0;
            boolean canMana = myTurn() && mn > 0;
            inv.setItem(MANA, named(it.menu(canMana ? "mana" : "blank", canMana ? "Attach mana ◆" : "Mana attached", canMana ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY, Math.max(1, mn)),
                    line(canMana ? (mode == Mode.MANA ? "Now click one of your cards" : "Attach mana ◆") : "Mana", canMana ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY),
                    List.of(line(canMana ? "Click, then click one of your cards" : "You get 1 mana each turn", NamedTextColor.GRAY),
                            line("Mana stays on the card it's attached to", NamedTextColor.GRAY),
                            line("Moves need that much mana on your Active", NamedTextColor.GRAY)), mode == Mode.MANA));
        }
        inv.setItem(POINTS, it.menu("gem_legendary", "Your points: " + mat.points[side] + "/" + toWin, NamedTextColor.GOLD,
                Math.max(1, mat.points[side]), "Deck: " + mat.deck[side].size() + "   Discard: " + mat.discard[side].size(), "Each knockout scores 1 point"));
        inv.setItem(HINT, it.menu("info", hint(), NamedTextColor.YELLOW, 1,
                "Each turn: attach 1 mana, bench characters,", "play 1 item, retreat once, then attack.",
                "Attacking ends your turn.", "Strength > Magicing > Dexterity > Strength (+10)"));
        if (setup()) inv.setItem(ACTION, it.menu("create", mat.ready[side] ? "Ready (waiting for rival)" : "Ready", mat.ready[side] ? NamedTextColor.GRAY : NamedTextColor.GREEN, 1,
                "Starts the battle once both players are ready", "You need a character in the middle (Active)"));
        else inv.setItem(ACTION, it.menu("reset", "End turn", myTurn() ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY, 1, "Pass without attacking"));
        inv.setItem(FORFEIT, it.menu("close", "Forfeit", NamedTextColor.RED, 1, "Give up and let your rival win"));
    }

    private String hint() {
        if (setup()) {
            if (mat.ready[side]) return "Waiting for your rival to get ready";
            if (selectedHand() != null) return "Now click a slot (middle = Active)";
            return mat.cards[side][Mat.ACTIVE] == null ? "Put a character in the middle slot" : "Bench up to 2 more, then Ready";
        }
        if (promoting()) return "Click a benched card to make it Active";
        if (!myTurn()) return mat.turn == side ? "Wait for the move to finish" : "Waiting for your rival";
        if (mode == Mode.MANA) return "Click one of your cards to attach mana";
        if (mode == Mode.RETREAT) return "Click a benched card to swap in";
        ItemType t = selectedItem();
        if (t != null) return switch (t.target()) {
            case OWN -> "Click one of YOUR cards for " + t.label;
            case BENCH -> "Click one of your BENCHED cards for " + t.label;
            case ENEMY -> "Click a RIVAL card for " + t.label;
            default -> "Using " + t.label;
        };
        if (mat.mana[side] > 0) return "Attach your mana: click Mana ◆";
        return "Attack with a move, or end your turn";
    }

    private List<Component> cardLore(DisplayManager.Placed pl, boolean mine) {
        List<Component> lore = new ArrayList<>();
        lore.add(line(pl.col == Mat.ACTIVE ? "Active" : "Bench", pl.col == Mat.ACTIVE ? NamedTextColor.GOLD : NamedTextColor.GRAY)
                .append(line("   " + pl.card.type().label, pl.card.type().color)));
        lore.add(line("❤ " + pl.hp + "/" + pl.maxHp, hpColor(pl)).append(line("   Mana " + (pl.mana == 0 ? "none" : "◆".repeat(pl.mana)), NamedTextColor.AQUA)));
        if (pl.dazed) lore.add(line("Dazed: can't attack or retreat on its next turn", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.addAll(Items.moveLore(pl.card));
        lore.add(line("Retreat " + (pl.card.retreat() == 0 ? "free" : "◆".repeat(pl.card.retreat())) + "   Weak to " + pl.card.type().weakness().label, NamedTextColor.DARK_GRAY));
        return lore;
    }

    private ItemStack enemyIcon(DisplayManager.Placed tgt, int col) {
        Items it = plugin.items();
        if (tgt == null) return it.menu("blank", col == Mat.ACTIVE ? "No Active" : "Empty bench", NamedTextColor.DARK_GRAY, 1);
        if (tgt.faceDown) return named(it.cardBack(), line("Face-down card", NamedTextColor.GRAY), List.of(), false);
        if (tgt.ko) return it.menu("close", tgt.card.name() + " (knocked out)", NamedTextColor.DARK_GRAY, 1);
        List<Component> lore = cardLore(tgt, false);
        boolean glint = false;
        ItemType t = selectedItem();
        if (myTurn() && t != null && t.target() == ItemType.Target.ENEMY) {
            lore.add(0, line("Click to use " + t.label + " here", NamedTextColor.YELLOW)); glint = true;
        }
        return named(it.card(tgt.card, tgt.holo), line(tgt.card.name(), tgt.card.rarity().color()), lore, glint);
    }

    private ItemStack ownIcon(DisplayManager.Placed pl, int col) {
        Items it = plugin.items();
        HandCard sel = selectedHand();
        if (pl == null) {
            boolean target = setup() && sel != null && sel.isChar() && !mat.ready[side];
            String what = col == Mat.ACTIVE ? "Active slot" : "Bench slot";
            return it.menu(target ? "create" : "blank", target ? "Put " + sel.name() + " here" : what, target ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY, 1,
                    target ? "Click to place it face down" : col == Mat.ACTIVE ? "Your Active fights; the middle of your mat" : "Benched cards wait and can hold mana");
        }
        if (pl.ko) return it.menu("close", pl.card.name() + " (knocked out)", NamedTextColor.DARK_GRAY, 1);
        List<Component> lore = setup() ? new ArrayList<>(List.of(line((col == Mat.ACTIVE ? "Active" : "Bench") + ", face down", NamedTextColor.GRAY),
                line(mat.ready[side] ? "Ready" : "Click to take it back", NamedTextColor.GRAY))) : cardLore(pl, true);
        boolean target = false;
        String action = null;
        if (promoting() && col != Mat.ACTIVE) action = "Click to make it your Active";
        else if (myTurn()) {
            ItemType t = selectedItem();
            if (mode == Mode.MANA) action = "Click to attach mana here";
            else if (mode == Mode.RETREAT && col != Mat.ACTIVE) action = "Click to swap it in";
            else if (t != null && (t.target() == ItemType.Target.OWN || (t.target() == ItemType.Target.BENCH && col != Mat.ACTIVE))) action = "Click to use " + t.label + " here";
        }
        if (action != null) { lore.add(0, line(action, NamedTextColor.GREEN)); target = true; }
        Component name = line((pl.holo ? "✦ " : "") + pl.card.name() + (col == Mat.ACTIVE && !setup() ? "  (Active)" : ""), pl.card.rarity().color());
        return named(it.card(pl.card, pl.holo), name, lore, target);
    }

    private ItemStack moveIcon(DisplayManager.Placed act, int k) {
        Items it = plugin.items();
        if (act == null || act.ko || act.faceDown) return blank();
        Move mv = act.card.moves().get(k);
        String why = bm().cantAttack(mat, side, mv);
        boolean can = why == null && myTurn();
        var foe = mat.active(1 - side);
        List<String> lore = new ArrayList<>();
        lore.add("Needs " + (mv.cost() == 0 ? "no mana" : mv.costText() + " (" + mv.cost() + ")") + " on " + act.card.name());
        if (mv.damage() > 0) lore.add(mv.damage() + " damage" + (foe != null && act.card.type().beats(foe.card.type()) ? ", +" + plugin.rules().weaknessBonus() + " weakness" : ""));
        if (mv.effect() != MoveEffect.NONE) lore.add(mv.effect().description);
        if (can && foe != null && mv.damage() > 0) {
            int d = bm().damage(mat, side, act, mv, foe);
            lore.add("Click: hit " + foe.card.name() + " for " + d + (d >= foe.hp ? " (knocks it out!)" : ""));
        } else lore.add(can ? "Click to use it" : why == null ? "Not your turn" : why);
        String title = mv.costText() + "  " + mv.name() + (mv.damage() > 0 ? "  " + mv.damage() : "");
        return it.menu(can ? act.card.type().icon : "blank", title, can ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY, 1, lore.toArray(new String[0]));
    }

    private ItemStack retreatIcon(DisplayManager.Placed act) {
        Items it = plugin.items();
        if (act == null) return blank();
        int cost = act.card.retreat();
        String why = retreatWhy(act);
        boolean can = why == null;
        return named(it.menu(can ? "reset" : "blank", "Retreat", can ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY, 1),
                line(mode == Mode.RETREAT ? "Retreat: now click a benched card" : "Retreat " + act.card.name(), can ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY),
                List.of(line("Costs " + (cost == 0 ? "nothing" : cost + " mana (discarded from " + act.card.name() + ")"), NamedTextColor.GRAY),
                        line("Swaps your Active with a benched card, once per turn", NamedTextColor.GRAY),
                        line(can ? "Click, then click a benched card" : why, can ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)), mode == Mode.RETREAT);
    }

    private String retreatWhy(DisplayManager.Placed act) {
        int cost = act.card.retreat();
        return !myTurn() ? "Not your turn" : mat.retreated[side] ? "You already retreated this turn"
                : BattleManager.dazed(mat, act) ? act.card.name() + " is dazed"
                : mat.bench(side).isEmpty() ? "No benched card to swap in"
                : act.mana < cost ? "Needs " + cost + " mana on " + act.card.name() : null;
    }

    private boolean playable(HandCard h) {
        if (setup()) return h.isChar() && !mat.ready[side] && mat.emptySlot(side) >= 0;
        if (!myTurn()) return false;
        if (h.isChar()) return mat.emptyBench(side) >= 0;
        if (mat.itemUsed[side]) return false;
        if (h.item.kind == ItemType.Kind.TRAP) return mat.traps[side].size() < 2;
        return switch (h.item.target()) {
            case BENCH -> !mat.bench(side).isEmpty();
            case ENEMY -> mat.hasCards(1 - side);
            default -> h.item != ItemType.RECALL_SCROLL || mat.discard[side].stream().anyMatch(HandCard::isChar);
        };
    }

    private String whyNot(HandCard h) {
        if (setup()) return h.isChar() ? "Your 3 slots are full" : "Items are played during the battle";
        if (!myTurn()) return "Not your turn";
        if (h.isChar()) return "Your Bench is full";
        if (mat.itemUsed[side]) return "1 item per turn";
        if (h.item.kind == ItemType.Kind.TRAP) return "You already have 2 traps set";
        return switch (h.item.target()) { case BENCH -> "You have no benched card"; case ENEMY -> "No rival card to target"; default -> "No character in your discard pile"; };
    }

    private ItemStack handIcon(HandCard h, int i) {
        ItemStack s = plugin.items().of(h);
        boolean can = playable(h);
        List<Component> lore = new ArrayList<>();
        if (h.isChar()) {
            lore.add(line(h.rarity().label + " · " + h.card.type().label + " · ❤ " + h.card.hp(), h.rarity().color()));
            lore.addAll(Items.moveLore(h.card));
        } else {
            lore.add(line(h.rarity().label + (h.item.kind == ItemType.Kind.TRAP ? " trap" : " item"), h.rarity().color()));
            lore.add(line(h.item.description, NamedTextColor.WHITE));
        }
        String act = setup() ? "click, then a slot" : h.isChar() ? "click to put it on your Bench" : h.item.kind == ItemType.Kind.TRAP ? "click to set it face down" : "click to use";
        lore.add(line(can ? (selHand == i ? "Selected" : "PLAY: " + act) : whyNot(h), can ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
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

    private void clearSel() { mode = Mode.NONE; selHand = -1; }

    public void click(int slot) {
        if (slot == FORFEIT) { player.closeInventory(); bm().forfeit(player); return; }
        int own = colAt(slot, OWN_ROW);
        int hi = slot >= HAND && slot < HAND + 7 ? slot - HAND : -1;

        if (setup()) {
            if (slot == ACTION) { bm().setupReady(player, mat); render(); return; }
            if (hi >= 0 && hi < mat.hand[side].size() && playable(mat.hand[side].get(hi))) { selHand = selHand == hi ? -1 : hi; mode = Mode.SETUP_PLACE; render(); return; }
            if (own >= 0) {
                if (mat.cards[side][own] == null && selectedHand() != null) bm().setupPlace(mat, side, selHand, own);
                else if (mat.cards[side][own] != null) bm().setupTakeBack(mat, side, own);
                clearSel(); render();
            }
            return;
        }
        if (mat.state != Mat.State.FIGHTING) return;
        if (promoting()) {
            if (own >= 0 && own != Mat.ACTIVE && mat.cards[side][own] != null) bm().promote(mat, side, mat.cards[side][own]);
            return;
        }
        if (!myTurn()) { BattleManager.say(player, "Wait for your turn.", NamedTextColor.YELLOW); return; }
        if (slot == ACTION) { player.closeInventory(); bm().endTurn(player); return; }
        if (slot == MANA) {
            if (mat.mana[side] <= 0) { BattleManager.say(player, "You've attached your mana for this turn.", NamedTextColor.YELLOW); return; }
            selHand = -1; mode = mode == Mode.MANA ? Mode.NONE : Mode.MANA; render(); return;
        }
        if (slot == RETREAT) {
            var act = mat.active(side);
            if (act == null) return;
            String why = retreatWhy(act);
            if (why != null) { BattleManager.say(player, why + ".", NamedTextColor.YELLOW); return; }
            selHand = -1; mode = mode == Mode.RETREAT ? Mode.NONE : Mode.RETREAT; render(); return;
        }
        if (slot == MOVE1 || slot == MOVE2) {
            var act = mat.active(side);
            if (act == null) return;
            String why = bm().cantAttack(mat, side, act.card.moves().get(slot == MOVE1 ? 0 : 1));
            if (why != null) { BattleManager.say(player, why + ".", NamedTextColor.YELLOW); return; }
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            bm().attack(player, mat, slot == MOVE1 ? 0 : 1);
            return;
        }
        if (hi >= 0) {
            if (hi >= mat.hand[side].size()) return;
            HandCard h = mat.hand[side].get(hi);
            if (!playable(h)) { BattleManager.say(player, whyNot(h) + ".", NamedTextColor.YELLOW); return; }
            if (h.isChar()) { bm().playCharacter(mat, side, hi); clearSel(); render(); return; }
            if (h.item.kind == ItemType.Kind.TRAP || h.item.target() == ItemType.Target.NONE) { bm().useItem(mat, side, hi, null); clearSel(); render(); return; }
            if (selHand == hi) clearSel(); else { selHand = hi; mode = Mode.ITEM; }
            render(); return;
        }
        if (own >= 0) {
            var pl = mat.cards[side][own];
            if (pl == null || pl.ko) return;
            ItemType t = selectedItem();
            if (mode == Mode.MANA) { bm().attachMana(mat, side, pl); clearSel(); render(); return; }
            if (mode == Mode.RETREAT) { if (own != Mat.ACTIVE) bm().retreat(mat, side, pl); clearSel(); render(); return; }
            if (t != null && (t.target() == ItemType.Target.OWN || t.target() == ItemType.Target.BENCH)) { bm().useItem(mat, side, selHand, pl); clearSel(); render(); return; }
            // shortcut: with mana to attach and nothing selected, clicking your card attaches it
            if (mode == Mode.NONE && mat.mana[side] > 0) { bm().attachMana(mat, side, pl); render(); }
            return;
        }
        int en = colAt(slot, 0);
        if (en >= 0) {
            var tgt = mat.cards[1 - side][enemyCol(en)];
            if (tgt == null || tgt.ko || tgt.faceDown) return;
            ItemType t = selectedItem();
            if (t != null && t.target() == ItemType.Target.ENEMY) { bm().useItem(mat, side, selHand, tgt); clearSel(); render(); }
        }
    }
}
