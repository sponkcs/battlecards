package dev.sneakycards;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** Submenu of /cardadmin: pick the backdrop for the card being created. */
public final class BackgroundMenu implements InventoryHolder {
    private static final int FIRST = 10, BACK = 22;
    private final SneakyCardsPlugin plugin;
    private final Player player;
    private final Draft draft;
    private final Inventory inv;

    BackgroundMenu(SneakyCardsPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.draft = plugin.draft(player);
        this.inv = Bukkit.createInventory(this, 27, Component.text("Card Background"));
        render();
    }

    public void open() { player.openInventory(inv); }
    @Override public Inventory getInventory() { return inv; }

    private void render() {
        inv.clear();
        Items it = plugin.items();
        ItemStack blank = it.menu("blank", " ", NamedTextColor.GRAY, 1);
        for (int i = 0; i < 27; i++) inv.setItem(i, blank);
        Background[] all = Background.values();
        for (int k = 0; k < all.length; k++) {
            Background b = all[k];
            boolean sel = draft.background == b;
            ItemStack icon = it.menu("bg_" + b.key(), b.label, sel ? NamedTextColor.GREEN : NamedTextColor.WHITE, 1,
                    sel ? "Selected" : "Click to select");
            if (sel) icon.editMeta(m -> m.setEnchantmentGlintOverride(true));
            inv.setItem(FIRST + k, icon);
        }
        inv.setItem(BACK, it.menu("prev", "Back to card creator", NamedTextColor.YELLOW, 1));
    }

    public void click(int slot) {
        if (slot == BACK) { new AdminMenu(plugin, player).open(); return; }
        int k = slot - FIRST;
        Background[] all = Background.values();
        if (k < 0 || k >= all.length) return;
        draft.background = all[k];
        new AdminMenu(plugin, player).open();
    }
}
