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
                        "3. Draw 5, put up to 3 characters face down, click Ready. They flip; higher total Dexterity goes first.",
                        "4. Each turn: draw 1, +2 energy (max 6). Play 1 character and 1 item or trap, then attack once (ends your turn).",
                        "5. Moves cost 1-3 energy; Luck moves refund 1. Each card acts once per round.",
                        "6. Types: Strength > Magicing > Dexterity > Strength (x1.5 / x0.75). Luck is the crit chance (x1.5).",
                        "7. Each knockout scores 1 point. First to 3 wins. Hand limit 7; with an empty deck you just skip the draw.",
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
