package dev.sneakycards;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import java.util.ArrayList;
import java.util.List;

/** Builds every custom item. Models come from the resource pack (namespace "sneakycards"). */
public final class Items {
    final NamespacedKey cardKey, packKey, binderKey, matKey;

    Items(SneakyCardsPlugin plugin) {
        cardKey = new NamespacedKey(plugin, "card");
        packKey = new NamespacedKey(plugin, "pack");
        binderKey = new NamespacedKey(plugin, "binder");
        matKey = new NamespacedKey(plugin, "mat");
    }

    static Component t(String s, TextColor c) { return Component.text(s, c).decoration(TextDecoration.ITALIC, false); }

    private static void model(ItemMeta m, String path) { m.setItemModel(NamespacedKey.fromString("sneakycards:" + path)); }

    public ItemStack card(Card c) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        model(m, "card_" + c.background().key() + "_" + c.rarity().key());
        m.displayName(t(c.name(), c.rarity().color()).decorate(TextDecoration.BOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(t(c.rarity().label, c.rarity().color()));
        lore.add(Component.text(c.collection(), NamedTextColor.GRAY).decorate(TextDecoration.ITALIC));
        lore.add(t(c.background().label, NamedTextColor.DARK_GRAY));
        lore.add(Component.empty());
        for (Stat s : Stat.values()) lore.add(t(s.label + ": " + c.stat(s), NamedTextColor.WHITE));
        lore.add(Component.empty());
        for (Move mv : c.moves()) lore.add(t(mv.name() + " (" + mv.stat().label + " " + c.power(mv) + ")", NamedTextColor.YELLOW));
        lore.add(Component.empty());
        lore.add(t("Right-click a block to play", NamedTextColor.DARK_GRAY));
        m.lore(lore);
        m.getPersistentDataContainer().set(cardKey, PersistentDataType.STRING, c.id());
        it.setItemMeta(m);
        return it;
    }

    public ItemStack pack(int amount) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        model(m, "pack");
        m.displayName(t("Card Pack", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        m.lore(List.of(t("Right-click to open", NamedTextColor.GRAY)));
        m.getPersistentDataContainer().set(packKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        it.setAmount(amount);
        return it;
    }

    public ItemStack binder() {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        model(m, "binder");
        m.displayName(t("Card Binder", NamedTextColor.RED).decorate(TextDecoration.BOLD));
        m.lore(List.of(t("Right-click to open your collection", NamedTextColor.GRAY)));
        m.getPersistentDataContainer().set(binderKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    public ItemStack mat(MatVariant v) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        model(m, "mat_" + v.key());
        m.displayName(t(v.label + " Game Mat", NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
        m.lore(List.of(t("Right-click the top of a block to place it", NamedTextColor.GRAY),
                t("Needs a flat area 5 blocks wide and 7 long", NamedTextColor.DARK_GRAY)));
        m.getPersistentDataContainer().set(matKey, PersistentDataType.STRING, v.name());
        it.setItemMeta(m);
        return it;
    }

    /** The mat variant of a mat item, or null if the item is not a mat. */
    public MatVariant matVariant(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return MatVariant.parse(it.getItemMeta().getPersistentDataContainer().get(matKey, PersistentDataType.STRING));
    }

    /** Icon item for menus. The amount doubles as a number badge (1 to 99). */
    public ItemStack menu(String icon, String name, TextColor color, int amount, String... lore) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        model(m, "menu/" + icon);
        m.setMaxStackSize(99);
        m.displayName(t(name, color));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(t(s, NamedTextColor.GRAY));
        m.lore(l);
        it.setItemMeta(m);
        it.setAmount(Math.max(1, Math.min(99, amount)));
        return it;
    }

    public String cardId(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(cardKey, PersistentDataType.STRING);
    }
    public boolean isPack(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(packKey, PersistentDataType.BYTE);
    }
    public boolean isBinder(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(binderKey, PersistentDataType.BYTE);
    }
}
