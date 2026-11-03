package dev.sneakycards;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.List;

public final class AdminCommand implements CommandExecutor, TabCompleter {
    private final SneakyCardsPlugin plugin;

    AdminCommand(SneakyCardsPlugin plugin) { this.plugin = plugin; }

    private static void say(CommandSender s, String m) { s.sendMessage(Component.text(m, NamedTextColor.YELLOW)); }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 0) {
            if (s instanceof Player p) new AdminMenu(plugin, p).open(); else say(s, "Players only.");
            return true;
        }
        switch (a[0].toLowerCase()) {
            case "give" -> give(s, a);
            case "binder" -> {
                Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : (s instanceof Player p ? p : null);
                if (t == null) { say(s, "Player not found."); return true; }
                t.getInventory().addItem(plugin.items().binder());
                say(s, "Gave a binder to " + t.getName() + ".");
            }
            case "list" -> {
                say(s, plugin.registry().all().size() + " cards:");
                plugin.registry().all().forEach(card -> s.sendMessage(Component.text(" • " + card.id() + " (" + card.rarity().label + ", " + card.collection() + ")", NamedTextColor.GRAY)));
            }
            case "mat" -> mat(s, a);
            case "reload" -> { plugin.reloadConfig(); plugin.registry().load(); plugin.binders().load(); say(s, "Reloaded."); }
            case "pack" -> { if (s instanceof Player p) plugin.sendPack(p); }
            case "place", "move", "hurt" -> { if (s instanceof Player p) playerTools(p, a); else say(s, "Players only."); }
            case "clear" -> { plugin.battles().clearAll(); plugin.displays().clearAll(); say(s, "Cleared every mat and card in play."); }
            default -> say(s, "Unknown subcommand.");
        }
        return true;
    }

    private void mat(CommandSender s, String[] a) {
        if (a.length > 1 && a[1].equalsIgnoreCase("remove")) {
            if (!(s instanceof Player p)) { say(s, "Players only."); return; }
            Mat m = plugin.battles().nearestMat(p.getLocation());
            if (m == null) { say(s, "No mat nearby."); return; }
            plugin.battles().removeMat(m, null);
            say(s, "Removed the nearest mat.");
            return;
        }
        MatVariant v = a.length > 1 ? MatVariant.parse(a[1]) : null;
        if (v == null) { say(s, "Usage: /cardadmin mat <classic|purple|neon> [player]  or  /cardadmin mat remove"); return; }
        Player t = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : (s instanceof Player p ? p : null);
        if (t == null) { say(s, "Player not found."); return; }
        t.getInventory().addItem(plugin.items().mat(v));
        say(s, "Gave a " + v.label + " mat to " + t.getName() + ".");
    }

    private void give(CommandSender s, String[] a) {
        if (a.length < 3) { say(s, "Usage: /cardadmin give <player> pack [amount] | card <id> [amount]"); return; }
        Player t = Bukkit.getPlayerExact(a[1]);
        if (t == null) { say(s, "Player not found."); return; }
        if (a[2].equalsIgnoreCase("pack")) {
            int n = a.length > 3 ? parse(a[3]) : 1;
            t.getInventory().addItem(plugin.items().pack(n));
            say(s, "Gave " + n + " pack(s) to " + t.getName() + ".");
        } else if (a[2].equalsIgnoreCase("card") && a.length > 3) {
            Card card = plugin.registry().get(a[3]);
            if (card == null) { say(s, "No such card."); return; }
            var item = plugin.items().card(card);
            item.setAmount(a.length > 4 ? parse(a[4]) : 1);
            t.getInventory().addItem(item);
            say(s, "Gave " + card.name() + " to " + t.getName() + ".");
        }
    }

    private static int parse(String s) { try { return Math.max(1, Math.min(64, Integer.parseInt(s))); } catch (NumberFormatException e) { return 1; } }

    private void playerTools(Player p, String[] a) {
        var d = plugin.displays();
        switch (a[0].toLowerCase()) {
            case "place" -> {
                Card card = a.length > 1 ? plugin.registry().get(a[1]) : null;
                Block b = p.getTargetBlockExact(6);
                if (card == null || b == null) { say(p, "Usage: /cardadmin place <cardId> while looking at a block."); return; }
                Location top = b.getLocation().add(0.5, 1, 0.5);
                d.place(p, card, top, Math.round(p.getLocation().getYaw() / 90f) * 90f);
            }
            case "move" -> {
                var placed = d.nearestOwned(p);
                int i = a.length > 1 ? parse(a[1]) : 1;
                if (placed == null) { say(p, "Place a card first."); return; }
                d.play(placed, placed.card.moves().get(Math.min(1, i - 1)));
            }
            case "hurt" -> { var placed = d.nearestOwned(p); if (placed != null) d.hurt(placed); }
            case "clear" -> { d.clearOwner(p.getUniqueId()); say(p, "Cleared your cards from the mat."); }
            default -> { }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 1) return List.of("give", "binder", "mat", "place", "move", "hurt", "clear", "list", "pack", "reload");
        if (a.length == 2 && a[0].equalsIgnoreCase("place")) return plugin.registry().all().stream().map(Card::id).toList();
        if (a.length == 2 && a[0].equalsIgnoreCase("mat")) return List.of("classic", "purple", "neon", "remove");
        if (a.length == 2 && a[0].equalsIgnoreCase("move")) return List.of("1", "2");
        if (a.length == 3 && a[0].equalsIgnoreCase("give")) return List.of("pack", "card");
        if (a.length == 4 && a[0].equalsIgnoreCase("give") && a[2].equalsIgnoreCase("card")) return plugin.registry().all().stream().map(Card::id).toList();
        return List.of();
    }
}
