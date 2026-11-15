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
            case "solo", "practice" -> plugin.battles().solo(p);
            case "deck" -> new DeckMenu(plugin, p).open();
            case "end", "pass" -> plugin.battles().endTurn(p);
            case "forfeit", "leave" -> plugin.battles().forfeit(p);
            default -> {
                String[] rules = {
                        "SneakyCards rules",
                        "1. Build a 15-card deck in your binder (/binder, Deck builder): 6+ characters, max 2 of a card, deck cost 35 or less.",
                        "2. Right-click your side of a mat to place your deck (or /sc solo to play the computer). Both decks are shuffled.",
                        "3. Draw 5. Put a character face down in the middle (your Active) and up to 2 on your Bench, click Ready. A coin decides who goes first.",
                        "4. Each turn: draw 1 and get 1 mana (none on the very first turn). Attach it to any of your cards: mana stays on that card.",
                        "5. Bench characters, play 1 item or trap, retreat once (the Active discards mana equal to its retreat cost).",
                        "6. Attack with your Active: a move needs that much mana on it and hits the rival's Active. Attacking ends your turn.",
                        "7. Types: Strength > Magicing > Dexterity > Strength: +10 damage. Each knockout scores 1 point; a benched card steps up.",
                        "8. First to 3 points wins, or when the rival has no characters left in play. Hand limit 7.",
                        "Commands: /sc solo, /sc turn (open the battle menu), /sc end (end turn), /sc deck, /sc forfeit, /binder"};
                for (int i = 0; i < rules.length; i++) p.sendMessage(Component.text(rules[i], i == 0 ? NamedTextColor.GOLD : NamedTextColor.GRAY));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        return a.length == 1 ? List.of("help", "solo", "turn", "end", "deck", "forfeit") : List.of();
    }
}
