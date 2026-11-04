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
                        "1. Play 3 different cards on your side of a mat. Team cost max 10 (Common 1 ... Mythic 6).",
                        "2. Higher total Dexterity goes first (tie: coin flip). The other side starts with +1 energy.",
                        "3. Each turn you gain 2 energy (max 6). Moves cost 1-3 energy; Luck moves refund 1. Or Rest for +1.",
                        "4. Each card acts once per round: use all your cards before any can act again.",
                        "5. Types: Strength > Magicing > Dexterity > Strength. Advantage x1.5, disadvantage x0.75.",
                        "6. Luck is the crit chance (x1.5 damage). Knock out all 3 enemy cards to win.",
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
