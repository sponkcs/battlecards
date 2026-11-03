package dev.sneakycards;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;

/**
 * Turn menu. Row 1: your three cards. Row 2: the selected card's two moves. Row 3: the enemy cards.
 * Click a card, then a move, then the enemy card to attack.
 */
public final class BattleMenu implements InventoryHolder {
    private static final int OWN = 1, MOVE = 10, ENEMY = 19, INFO = 5, FORFEIT = 7;
    private final SneakyCardsPlugin plugin;
    private final Player player;
    private final Mat mat;
    private final int side;
    private final Inventory inv;
    private int selCard = -1, selMove = -1;

    BattleMenu(SneakyCardsPlugin plugin, Player player, Mat mat) {
        this.plugin = plugin;
        this.player = player;
        this.mat = mat;
        this.side = mat.sideOf(player.getUniqueId());
        this.inv = Bukkit.createInventory(this, 27, Component.text("Your turn"));
        render();
    }

    public void open() { player.openInventory(inv); }
    @Override public Inventory getInventory() { return inv; }

    private ItemStack cardItem(DisplayManager.Placed pl, boolean selected, String hint) {
        Items it = plugin.items();
        if (pl == null) return it.menu("blank", " ", NamedTextColor.GRAY, 1);
        if (pl.ko) return it.menu("close", pl.card.name() + " (knocked out)", NamedTextColor.DARK_GRAY, 1);
        ItemStack s = it.card(pl.card);
        s.editMeta(m -> {
            List<Component> lore = new ArrayList<>(m.lore() == null ? List.<Component>of() : m.lore());
            lore.add(Component.empty());
            lore.add(Items.t("Health: " + pl.hp + "/" + pl.maxHp, pl.hp * 2 > pl.maxHp ? NamedTextColor.GREEN : NamedTextColor.RED));
            if (hint != null) lore.add(Items.t(hint, NamedTextColor.AQUA));
            m.lore(lore);
            if (selected) m.setEnchantmentGlintOverride(true);
        });
        return s;
    }

    private void render() {
        inv.clear();
        Items it = plugin.items();
        ItemStack blank = it.menu("blank", " ", NamedTextColor.GRAY, 1);
        for (int i = 0; i < 27; i++) inv.setItem(i, blank);
        int enemy = 1 - side;
        for (int i = 0; i < 3; i++) {
            inv.setItem(OWN + i, cardItem(mat.cards[side][i], selCard == i, "Click to choose this card"));
            inv.setItem(ENEMY + i, cardItem(mat.cards[enemy][i], false, "Click to attack this card"));
        }
        if (selCard >= 0 && mat.cards[side][selCard] != null) {
            Card c = mat.cards[side][selCard].card;
            for (int k = 0; k < 2; k++) {
                Move mv = c.moves().get(k);
                ItemStack icon = it.menu(mv.stat().icon, mv.name(), NamedTextColor.YELLOW, 1,
                        "Damage: " + c.power(mv) + " (" + mv.stat().label + ")",
                        "Critical chance: " + Math.min(100, c.stat(Stat.LUCK)) + "% for double damage",
                        selMove == k ? "Selected. Now click an enemy card." : "Click to choose this move");
                if (selMove == k) icon.editMeta(m -> m.setEnchantmentGlintOverride(true));
                inv.setItem(MOVE + k, icon);
            }
        } else {
            inv.setItem(MOVE, it.menu("move", "Moves", NamedTextColor.GRAY, 1, "Choose one of your cards first"));
        }
        inv.setItem(INFO, it.menu("info", "How to attack", NamedTextColor.AQUA, 1, "1. Click one of your cards", "2. Click one of its moves", "3. Click an enemy card"));
        inv.setItem(FORFEIT, it.menu("close", "Forfeit", NamedTextColor.RED, 1, "Give up and let your opponent win"));
    }

    public void click(int slot) {
        if (mat.state != Mat.State.FIGHTING) return;
        if (slot == FORFEIT) { player.closeInventory(); plugin.battles().forfeit(player); return; }
        if (mat.turn != side || mat.busy) { BattleManager.say(player, "Wait for your turn.", NamedTextColor.YELLOW); return; }
        if (slot >= OWN && slot < OWN + 3) {
            var pl = mat.cards[side][slot - OWN];
            if (pl != null && !pl.ko) { selCard = slot - OWN; selMove = -1; }
        } else if (slot >= MOVE && slot < MOVE + 2) {
            if (selCard >= 0) selMove = slot - MOVE;
        } else if (slot >= ENEMY && slot < ENEMY + 3) {
            var tgt = mat.cards[1 - side][slot - ENEMY];
            if (tgt == null || tgt.ko) return;
            if (selCard < 0 || selMove < 0) { BattleManager.say(player, "Choose one of your cards and a move first.", NamedTextColor.YELLOW); return; }
            var att = mat.cards[side][selCard];
            if (att == null || att.ko) return;
            Move mv = att.card.moves().get(selMove);
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            plugin.battles().attack(player, mat, att, mv, tgt);
            return;
        }
        render();
    }
}
