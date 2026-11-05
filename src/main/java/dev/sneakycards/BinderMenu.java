package dev.sneakycards;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Shows every card the player owns, laid out like an open card binder: two pages of 4 x 5 sleeves with the
 * ring spine down the middle column. The background comes from the resource pack (font sneakycards:gui).
 */
public final class BinderMenu implements InventoryHolder {
    private static final int[] SLOTS = buildSlots();   // container slots that are card sleeves (skips column 4)
    private static final int PER_PAGE = SLOTS.length;    // 40
    private static final int PREV = 45, INFO = 49, NEXT = 53;

    private static int[] buildSlots() {
        int[] out = new int[40]; int n = 0;
        for (int r = 0; r < 5; r++) for (int c = 0; c < 9; c++) if (c != 4) out[n++] = r * 9 + c;
        return out;
    }

    /** Binder background glyph, then the title text in gold on the leather header. */
    static Component title(String text) {
        Component bg = Component.text("\uF001\uE000\uF002").font(Key.key("sneakycards", "gui")).color(NamedTextColor.WHITE);
        return bg.append(Component.text(text, TextColor.color(0xE8C27A)));
    }
    private final SneakyCardsPlugin plugin;
    private final Player player;
    private final Inventory inv;
    private final List<Entry> shown = new ArrayList<>();
    private int page = 0;

    BinderMenu(SneakyCardsPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inv = Bukkit.createInventory(this, 54, title("Card Binder"));
        render();
    }

    public void open() { player.openInventory(inv); }
    @Override public Inventory getInventory() { return inv; }

    /** One binder entry: a character card id, or "item:NAME" for an item card. */
    private record Entry(String key, ItemStack icon, int rarity, String name, boolean item) { }

    public void render() {
        inv.clear();
        shown.clear();
        Items it = plugin.items();
        Map<String, Integer> own = plugin.binders().counts(player.getUniqueId());
        List<Entry> list = new ArrayList<>();
        for (var e : own.entrySet()) {
            if (e.getKey().startsWith("item:")) {
                ItemType t = ItemType.parse(e.getKey().substring(5));
                if (t != null) list.add(new Entry(e.getKey(), it.itemCard(t), t.rarity.ordinal(), t.label, true));
            } else {
                boolean holo = e.getKey().endsWith("#holo");
                Card c = plugin.registry().get(holo ? e.getKey().substring(0, e.getKey().length() - 5) : e.getKey());
                if (c != null) list.add(new Entry(e.getKey(), it.card(c, holo), c.rarity().ordinal(), c.name() + (holo ? " ~holo" : ""), false));
            }
        }
        // characters first, then items; each by rarity (highest first) and name
        list.sort(Comparator.comparing(Entry::item).thenComparing(Comparator.comparingInt(Entry::rarity).reversed()).thenComparing(Entry::name));
        int pages = Math.max(1, (int) Math.ceil(list.size() / (double) PER_PAGE));
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= list.size()) break;
            Entry en = list.get(idx);
            ItemStack stack = en.icon();
            stack.setAmount(Math.max(1, Math.min(64, own.get(en.key()))));
            inv.setItem(SLOTS[i], stack);
            shown.add(en);
        }
        if (page > 0) inv.setItem(PREV, it.menu("prev", "Previous page", NamedTextColor.WHITE, 1));
        inv.setItem(INFO, it.menu("info", "Page " + (page + 1) + " of " + pages, NamedTextColor.AQUA, 1,
                list.size() + " different cards", "Click a card to take one", "Click a card in your inventory to store it"));
        if (page < pages - 1) inv.setItem(NEXT, it.menu("next", "Next page", NamedTextColor.WHITE, 1));
    }

    public void click(int slot) {
        if (slot == PREV && page > 0) { page--; render(); return; }
        if (slot == NEXT) { page++; render(); return; }
        int idx = -1;
        for (int i = 0; i < SLOTS.length; i++) if (SLOTS[i] == slot) idx = i;
        if (idx < 0 || idx >= shown.size()) return;
        Entry en = shown.get(idx);
        if (!plugin.binders().take(player.getUniqueId(), en.key())) return;
        ItemStack one = en.icon().clone();
        one.setAmount(1);
        Items.give(player, one);
        render();
    }
}
