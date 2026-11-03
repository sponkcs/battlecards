package dev.sneakycards;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.List;

/** /sneakycards for players: reopen the turn menu, forfeit, or read the rules. */
public final class PlayCommand implements CommandExecutor, TabCompleter {
    private final SneakyCardsPlugin plugin;

    PlayCommand(SneakyCardsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
        String sub = a.length == 0 ? "help" : a[0].toLowerCase();
        switch (sub) {
            case "turn", "menu" -> plugin.battles().openTurnMenu(p);
            case "forfeit", "leave" -> plugin.battles().forfeit(p);
            default -> {
                String[] rules = {
                        "SneakyCards rules",
                        "1. Hold a card and right-click a slot on a game mat to play it. Fill all 3 slots on your side.",
                        "2. The battle starts when both sides are full. Higher total Dexterity goes first. A tie is a coin flip.",
                        "3. On your turn pick a card, one of its 2 moves, and an enemy card. The move deals damage equal to its stat.",
                        "4. Luck is the chance for double damage. Luck 18 means an 18% chance.",
                        "5. A card at 0 Health is knocked out. Knock out all 3 enemy cards to win.",
                        "Commands: /sneakycards turn (reopen the turn menu), /sneakycards forfeit, /binder"};
                for (int i = 0; i < rules.length; i++) p.sendMessage(Component.text(rules[i], i == 0 ? NamedTextColor.GOLD : NamedTextColor.GRAY));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        return a.length == 1 ? List.of("help", "turn", "forfeit") : List.of();
    }
}
