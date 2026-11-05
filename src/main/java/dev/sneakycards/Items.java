package dev.sneakycards;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.UUID;
import java.util.List;

/** Builds every custom item. Models come from the resource pack (namespace "sneakycards"). */
public final class Items {
    final NamespacedKey cardKey, packKey, binderKey, matKey, itemKey, holoKey, godKey;

    private final SneakyCardsPlugin plugin;

    Items(SneakyCardsPlugin plugin) {
        this.plugin = plugin;
        cardKey = new NamespacedKey(plugin, "card");
        packKey = new NamespacedKey(plugin, "pack");
        binderKey = new NamespacedKey(plugin, "binder");
        matKey = new NamespacedKey(plugin, "mat");
        itemKey = new NamespacedKey(plugin, "item");
        holoKey = new NamespacedKey(plugin, "holo");
        godKey = new NamespacedKey(plugin, "godpack");
    }

    /** The card's skin as a profile: used by the mannequin and by the head drawn on the card item. */
    public ResolvableProfile profile(Card c) {
        var b = ResolvableProfile.resolvableProfile();
        String s = c.skin();
        if (s.equalsIgnoreCase("placeholder")) s = plugin.getConfig().getString("placeholder-skin", "Steve");
        if (s.startsWith("http")) {
            String meta = c.slim() ? ",\"metadata\":{\"model\":\"slim\"}" : "";
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + s + "\"" + meta + "}}}";
            String value = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
            b.uuid(UUID.nameUUIDFromBytes((s + c.slim()).getBytes(StandardCharsets.UTF_8)));
            b.addProperty(new ProfileProperty("textures", value));
        } else {
            b.name(s); // the server resolves the username
        }
        return b.build();
    }

    /** Puts items in the player's inventory; anything that doesn't fit drops at their feet. Returns true if something dropped. */
    public static boolean give(org.bukkit.entity.Player p, ItemStack... items) {
        var left = p.getInventory().addItem(items);
        left.values().forEach(x -> p.getWorld().dropItem(p.getLocation(), x, d -> d.setPickupDelay(10)));
        if (!left.isEmpty()) p.sendActionBar(Component.text("Inventory full: items dropped at your feet.", NamedTextColor.YELLOW));
        return !left.isEmpty();
    }

    static Component t(String s, TextColor c) { return Component.text(s, c).decoration(TextDecoration.ITALIC, false); }

    private static void model(ItemMeta m, String path) { m.setItemModel(NamespacedKey.fromString("sneakycards:" + path)); }

    private final java.util.Map<Card, ItemStack> cardCache = new java.util.HashMap<>(), holoCache = new java.util.HashMap<>();

    /** Card items are cached per card (the Card record changes when edited, so edits get a fresh item). */
    public ItemStack card(Card c) { return card(c, false); }

    /** holo: the holographic version (rainbow foil on the card, floating sparkles around its mannequin). */
    public ItemStack card(Card c, boolean holo) {
        var cache = holo ? holoCache : cardCache;
        if (cache.size() > 512) cache.clear();
        return cache.computeIfAbsent(c, k -> buildCard(k, holo)).clone();
    }

    private ItemStack buildCard(Card c, boolean holo) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        model(m, "card_" + c.background().key() + "_" + c.rarity().key() + (holo ? "_holo" : ""));
        m.displayName(holo ? t("✦ ", TextColor.color(0xB8F2FF)).append(t(c.name(), c.rarity().color())).decorate(TextDecoration.BOLD)
                : t(c.name(), c.rarity().color()).decorate(TextDecoration.BOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(t(c.rarity().label, c.rarity().color()));
        if (holo) lore.add(t("✦ Holographic", TextColor.color(0xB8F2FF)));
        if (!c.title().isBlank()) lore.add(Component.text(c.title(), NamedTextColor.GRAY).decorate(TextDecoration.ITALIC));
        lore.add(t(c.background().label, NamedTextColor.DARK_GRAY));
        lore.add(t("Type: " + Rules.type(c).label + "   Team cost: " + Rules.cost(c.rarity()), NamedTextColor.AQUA));
        lore.add(Component.empty());
        for (Stat s : Stat.values()) lore.add(t(s.label + ": " + c.stat(s), NamedTextColor.WHITE));
        lore.add(Component.empty());
        for (Move mv : c.moves()) lore.add(t(mv.name() + " (" + mv.stat().label + " " + c.power(mv) + ", " + Rules.moveCost(c, mv) + " energy)", NamedTextColor.YELLOW));
        lore.add(Component.empty());
        lore.add(t("Right-click a mat slot to play", NamedTextColor.DARK_GRAY));
        m.lore(lore);
        m.getPersistentDataContainer().set(cardKey, PersistentDataType.STRING, c.id());
        if (holo) m.getPersistentDataContainer().set(holoKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        // the resource pack draws this profile's head in the card's picture window
        it.setData(DataComponentTypes.PROFILE, profile(c));
        return it;
    }

    public ItemStack of(HandCard h) { return h.isChar() ? card(h.card, h.holo) : itemCard(h.item); }

    /** An item or trap card. */
    public ItemStack itemCard(ItemType type) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        model(m, "item_" + type.key());
        m.displayName(t(type.label, type.rarity.color()).decorate(TextDecoration.BOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(t(type.rarity.label + (type.kind == ItemType.Kind.TRAP ? " Trap" : " Item"), type.rarity.color()));
        lore.add(t(type.description, NamedTextColor.WHITE));
        lore.add(t("Energy cost: " + type.cost, NamedTextColor.AQUA));
        lore.add(Component.empty());
        lore.add(t("Before a battle: right-click your side", NamedTextColor.DARK_GRAY));
        lore.add(t("of the mat to bring it (max 2).", NamedTextColor.DARK_GRAY));
        m.lore(lore);
        m.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, type.name());
        it.setItemMeta(m);
        return it;
    }

    public ItemType itemType(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return ItemType.parse(it.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING));
    }

    /** A player's deck lying on the mat: a stack of card backs (the plugin scales its height to the cards left). */
    public ItemStack deckStack() {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        model(m, "deck_stack");
        m.displayName(t("Deck", NamedTextColor.GRAY));
        it.setItemMeta(m);
        return it;
    }

    /** Shown on the mat before a battle starts, so opponents can't see the card. */
    public ItemStack cardBack() {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        model(m, "card_facedown");
        m.displayName(t("Face-down card", NamedTextColor.GRAY));
        it.setItemMeta(m);
        return it;
    }

    /** Looks exactly like a normal pack, so the surprise lands when it's opened. */
    public ItemStack godPack(int amount) {
        ItemStack it = pack(amount);
        it.editMeta(m -> m.getPersistentDataContainer().set(godKey, PersistentDataType.BYTE, (byte) 1));
        return it;
    }

    public boolean isGodPack(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(godKey, PersistentDataType.BYTE);
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

    public boolean isHolo(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(holoKey, PersistentDataType.BYTE);
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
