package dev.sneakycards;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.ArrayList;
import java.util.List;

/** /cardadmin cards: every card in the game. Click to edit, shift-click twice to delete. */
public final class CardListMenu implements InventoryHolder {
    private static final int PER_PAGE = 45;
    private final SneakyCardsPlugin plugin;
    private final Player player;
    private final Inventory inv;
    private final List<Card> shown = new ArrayList<>();
    private int page = 0;
    private String armedId = null;
    private long armedAt = 0;

    CardListMenu(SneakyCardsPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inv = Bukkit.createInventory(this, 54, Component.text("All cards"));
        render();
    }

    public void open() { player.openInventory(inv); }
    @Override public Inventory getInventory() { return inv; }

    private void render() {
        inv.clear();
        shown.clear();
        Items it = plugin.items();
        List<Card> all = new ArrayList<>(plugin.registry().all());
        int pages = Math.max(1, (int) Math.ceil(all.size() / (double) PER_PAGE));
        page = Math.max(0, Math.min(page, pages - 1));
        boolean armedLive = System.currentTimeMillis() - armedAt < 5000;
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= all.size()) break;
            Card c = all.get(idx);
            ItemStack stack = it.card(c);
            ItemMeta m = stack.getItemMeta();
            List<Component> lore = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
            lore.add(Component.empty());
            lore.add(line("id: " + c.id(), NamedTextColor.DARK_GRAY));
            lore.add(line(c.inPacks() ? "In the pack pool" : "Not in packs", c.inPacks() ? NamedTextColor.GREEN : NamedTextColor.RED));
            lore.add(line("Click to edit", NamedTextColor.YELLOW));
            boolean armed = armedLive && c.id().equals(armedId);
            lore.add(line(armed ? "Shift-click again to DELETE" : "Shift-click twice to delete", NamedTextColor.RED));
            m.lore(lore);
            stack.setItemMeta(m);
            inv.setItem(i, stack);
            shown.add(c);
        }
        if (page > 0) inv.setItem(48, it.menu("prev", "Previous page", NamedTextColor.WHITE, 1));
        inv.setItem(49, it.menu("info", "Page " + (page + 1) + " of " + pages, NamedTextColor.AQUA, 1,
                all.size() + " cards", "Click a card to edit it", "Shift-click twice to delete"));
        if (page < pages - 1) inv.setItem(50, it.menu("next", "Next page", NamedTextColor.WHITE, 1));
        inv.setItem(53, it.menu("create", "New card", NamedTextColor.GREEN, 1, "Opens the card creator"));
    }

    private static Component line(String s, NamedTextColor c) { return Component.text(s, c).decoration(TextDecoration.ITALIC, false); }

    public void click(int slot, ClickType type) {
        if (slot == 48 && page > 0) { page--; render(); return; }
        if (slot == 50) { page++; render(); return; }
        if (slot == 53) {
            plugin.draft(player).reset();
            Bukkit.getScheduler().runTask(plugin, () -> new AdminMenu(plugin, player).open());
            return;
        }
        if (slot < 0 || slot >= shown.size()) return;
        Card c = shown.get(slot);
        if (type.isShiftClick()) {
            if (c.id().equals(armedId) && System.currentTimeMillis() - armedAt < 5000) {
                CardAdmin.delete(plugin, player, c.id());
                armedId = null;
            } else { armedId = c.id(); armedAt = System.currentTimeMillis(); }
            render();
            return;
        }
        plugin.draft(player).load(c);
        Bukkit.getScheduler().runTask(plugin, () -> new AdminMenu(plugin, player).open());
    }
}
