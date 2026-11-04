package dev.sneakycards;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
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
                if (p.isSneaking()) battles.pickUpCard(p, placed);
                else if (placed.mat.state == Mat.State.FIGHTING) battles.openTurnMenu(p);
            } else if (p.isSneaking()) {
                plugin.displays().remove(placed);
                p.getInventory().addItem(plugin.items().card(placed.card));
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

    private void openPack(Player p, ItemStack pack) {
        if (plugin.registry().draw(rng) == null) {
            p.sendMessage(Component.text("There are no cards in the pack pool yet.", NamedTextColor.RED));
            return;
        }
        pack.setAmount(pack.getAmount() - 1);
        int n = plugin.getConfig().getInt("cards-per-pack", 3);
        p.sendMessage(Component.text("Pack opened! You got:", NamedTextColor.GOLD));
        for (int i = 0; i < n; i++) {
            Card c = plugin.registry().draw(rng);
            var left = p.getInventory().addItem(plugin.items().card(c));
            left.values().forEach(x -> p.getWorld().dropItemNaturally(p.getLocation(), x));
            p.sendMessage(Component.text(" • ", NamedTextColor.DARK_GRAY)
                    .append(Items.t(c.name(), c.rarity().color()))
                    .append(Component.text("  " + c.rarity().label, NamedTextColor.GRAY)));
        }
        p.sendMessage(Component.text("The cards are in your inventory. Open your binder and click a card to store it.", NamedTextColor.GRAY));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
    }
}
