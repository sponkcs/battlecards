package dev.sneakycards;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Shows every card the player owns; the card model has a rarity-coloured border. */
public final class BinderMenu implements InventoryHolder {
    private static final int PER_PAGE = 45;
    private final SneakyCardsPlugin plugin;
    private final Player player;
    private final Inventory inv;
    private final List<Card> shown = new ArrayList<>();
    private int page = 0;

    BinderMenu(SneakyCardsPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inv = Bukkit.createInventory(this, 54, Component.text("Card Binder"));
        render();
    }

    public void open() { player.openInventory(inv); }
    @Override public Inventory getInventory() { return inv; }

    public void render() {
        inv.clear();
        shown.clear();
        Items it = plugin.items();
        Map<String, Integer> own = plugin.binders().counts(player.getUniqueId());
        List<Card> list = own.keySet().stream().map(plugin.registry()::get).filter(c -> c != null)
                .sorted(Comparator.comparing((Card c) -> c.rarity().ordinal()).reversed().thenComparing(Card::name)).toList();
        int pages = Math.max(1, (int) Math.ceil(list.size() / (double) PER_PAGE));
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= list.size()) break;
            Card c = list.get(idx);
            ItemStack stack = it.card(c);
            stack.setAmount(Math.min(64, own.get(c.id())));
            inv.setItem(i, stack);
            shown.add(c);
        }
        ItemStack blank = it.menu("blank", " ", NamedTextColor.GRAY, 1);
        for (int i = PER_PAGE; i < 54; i++) inv.setItem(i, blank);
        if (page > 0) inv.setItem(48, it.menu("prev", "Previous page", NamedTextColor.WHITE, 1));
        inv.setItem(49, it.menu("info", "Page " + (page + 1) + " of " + pages, NamedTextColor.AQUA, 1,
                list.size() + " different cards", "Click a card to take one", "Click a card in your inventory to store it"));
        if (page < pages - 1) inv.setItem(50, it.menu("next", "Next page", NamedTextColor.WHITE, 1));
    }

    public void click(int slot) {
        if (slot == 48 && page > 0) { page--; render(); return; }
        if (slot == 50) { page++; render(); return; }
        if (slot < 0 || slot >= shown.size()) return;
        Card c = shown.get(slot);
        if (!plugin.binders().take(player.getUniqueId(), c.id())) return;
        var left = player.getInventory().addItem(plugin.items().card(c));
        left.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
        render();
    }
}
