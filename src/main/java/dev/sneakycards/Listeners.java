package dev.sneakycards;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import java.util.Random;
import java.util.function.Consumer;

public final class Listeners implements Listener {
    private final SneakyCardsPlugin plugin;
    private final Random rng = new Random();

    Listeners(SneakyCardsPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.sendPack(e.getPlayer()), 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.battles().leave(e.getPlayer().getUniqueId());
        plugin.displays().clearOwner(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        Consumer<String> cb = plugin.prompts().take(e.getPlayer().getUniqueId());
        if (cb == null) return;
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message());
        Bukkit.getScheduler().runTask(plugin, () -> cb.accept(text.equalsIgnoreCase("cancel") ? null : text));
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        InventoryHolder h = top.getHolder();
        if (h instanceof AdminMenu m) {
            e.setCancelled(true);
            if (e.getClickedInventory() == top) m.click(e.getRawSlot(), e.getClick());
        } else if (h instanceof CardListMenu m) {
            e.setCancelled(true);
            if (e.getClickedInventory() == top) m.click(e.getRawSlot(), e.getClick());
        } else if (h instanceof BackgroundMenu m) {
            e.setCancelled(true);
            if (e.getClickedInventory() == top) m.click(e.getRawSlot());
        } else if (h instanceof BattleMenu m) {
            e.setCancelled(true);
            if (e.getClickedInventory() == top) m.click(e.getRawSlot());
        } else if (h instanceof BinderMenu m) {
            e.setCancelled(true);
            if (e.getClickedInventory() == top) { m.click(e.getRawSlot()); return; }
            ItemStack it = e.getCurrentItem();
            String id = plugin.items().cardId(it);
            if (id != null && plugin.items().isHolo(it)) id = id + "#holo";
            if (id == null) { ItemType t = plugin.items().itemType(it); if (t != null) id = "item:" + t.name(); }
            if (id == null) return;
            int n = e.getClick().isShiftClick() ? it.getAmount() : 1;
            plugin.binders().add(e.getWhoClicked().getUniqueId(), id, n);
            it.setAmount(it.getAmount() - n);
            m.render();
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder h = e.getView().getTopInventory().getHolder();
        if (h instanceof AdminMenu || h instanceof CardListMenu || h instanceof BinderMenu || h instanceof BackgroundMenu || h instanceof BattleMenu) e.setCancelled(true);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !e.getAction().isRightClick()) return;
        ItemStack it = e.getItem();
        if (it == null) return;
        Player p = e.getPlayer();
        Items items = plugin.items();
        if (items.isPack(it)) { e.setCancelled(true); openPack(p, it); return; }
        if (items.isBinder(it)) { e.setCancelled(true); new BinderMenu(plugin, p).open(); return; }
        MatVariant mv = items.matVariant(it);
        if (mv != null) {
            e.setCancelled(true);
            if (e.getClickedBlock() == null || e.getBlockFace() != BlockFace.UP) return;
            Location top = e.getClickedBlock().getLocation().add(0.5, 1, 0.5);
            float yaw = Math.round(p.getLocation().getYaw() / 90f) * 90f;
            if (plugin.battles().placeMat(p, top, yaw, mv) != null && p.getGameMode() != GameMode.CREATIVE) it.setAmount(it.getAmount() - 1);
            return;
        }
        if (items.cardId(it) != null && e.getClickedBlock() != null) {
            e.setCancelled(true);
            p.sendMessage(Component.text("Cards are played on a game mat. Place a mat, then right-click one of its slots.", NamedTextColor.YELLOW));
        }
    }

    /** Right-click a card or a mat slot. Sneak + right-click picks things back up. */
    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Entity ent = e.getRightClicked();
        Player p = e.getPlayer();
        BattleManager battles = plugin.battles();
        var placed = plugin.displays().byEntity(ent.getUniqueId());
        if (placed != null) {
            e.setCancelled(true);
            if (!placed.owner.equals(p.getUniqueId())) return;
            if (placed.mat != null) {
                ItemStack held = p.getInventory().getItemInMainHand();
                if (placed.mat.state == Mat.State.WAITING && plugin.items().itemType(held) != null) {
                    battles.slotClick(p, placed.mat, placed.side, placed.col);   // brings the item card
                    return;
                }
                if (p.isSneaking()) battles.pickUpCard(p, placed);
                else if (placed.mat.state == Mat.State.FIGHTING) battles.openTurnMenu(p);
            } else if (p.isSneaking()) {
                plugin.displays().remove(placed);
                Items.give(p, plugin.items().card(placed.card, placed.holo));
            }
            return;
        }
        Mat mat = battles.matOf(ent);
        if (mat == null) return;
        e.setCancelled(true);
        int[] slot = battles.slotOf(ent);
        if (slot == null) return;
        if (slot[0] < 0) {
            if (p.isSneaking()) battles.pickUpMat(p, mat);
            else p.sendMessage(Component.text("Hold a card and right-click a slot on your side. Sneak + right-click here to pick the mat up.", NamedTextColor.GRAY));
        } else {
            battles.slotClick(p, mat, slot[0], slot[1]);
        }
    }

    private final Map<UUID, Long> lastPack = new HashMap<>();

    /** Opens one pack. Rate-limited per player; cards that don't fit drop at the player's feet. */
    private void openPack(Player p, ItemStack pack) {
        long now = System.currentTimeMillis();
        long cooldown = plugin.getConfig().getLong("pack-cooldown-ms", 400);
        Long last = lastPack.get(p.getUniqueId());
        if (last != null && now - last < cooldown) return;               // ignore spam clicks quietly
        lastPack.put(p.getUniqueId(), now);

        int n = plugin.getConfig().getInt("cards-per-pack", 3);
        if (!plugin.registry().hasPool()) {
            p.sendMessage(Component.text("There are no cards in the pack pool yet.", NamedTextColor.RED));
            return;
        }
        pack.setAmount(pack.getAmount() - 1);
        var line = Component.text().append(Component.text("Pack: ", NamedTextColor.GOLD));
        boolean dropped = false;
        for (int i = 0; i < n; i++) {
            Card c = plugin.registry().draw(rng);
            boolean holo = rng.nextDouble() < plugin.getConfig().getDouble("holo-chance", 0.03);
            if (Items.give(p, plugin.items().card(c, holo))) dropped = true;
            if (i > 0) line.append(Component.text(", ", NamedTextColor.DARK_GRAY));
            if (holo) {
                line.append(Items.t("✦ ", net.kyori.adventure.text.format.TextColor.color(0xB8F2FF)));
                HoloReveal.play(plugin, p, c);
            }
            line.append(Items.t(c.name(), c.rarity().color()));
        }
        int items = plugin.getConfig().getInt("items-per-pack", 2);
        if (items > 0) line.append(Component.text("  +  ", NamedTextColor.DARK_GRAY));
        for (int i = 0; i < items; i++) {
            ItemType t = ItemType.draw(rng);
            if (Items.give(p, plugin.items().itemCard(t))) dropped = true;
            if (i > 0) line.append(Component.text(", ", NamedTextColor.DARK_GRAY));
            line.append(Items.t(t.label, t.rarity.color()));
        }
        p.sendMessage(line.build());
        if (dropped) p.sendMessage(Component.text("Your inventory was full, so some cards dropped at your feet.", NamedTextColor.YELLOW));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.4f);
    }

    @EventHandler
    public void onQuitClearCooldown(org.bukkit.event.player.PlayerQuitEvent e) { lastPack.remove(e.getPlayer().getUniqueId()); }
}
