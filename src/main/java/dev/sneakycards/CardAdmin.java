package dev.sneakycards;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

/** Shared edit/delete actions used by the admin command and menus. */
final class CardAdmin {
    private CardAdmin() { }

    /** Deletes a card from the registry. Binders keep the entry but hide it; copies in inventories stop working. */
    static boolean delete(SneakyCardsPlugin plugin, CommandSender who, String id) {
        Card c = plugin.registry().remove(id);
        if (c == null) { who.sendMessage(Component.text("No card with id " + id + ".", NamedTextColor.RED)); return false; }
        who.sendMessage(Component.text("Deleted ", NamedTextColor.GOLD).append(Items.t(c.name(), c.rarity().color()))
                .append(Component.text(". It is no longer in packs or binders.", NamedTextColor.GOLD)));
        return true;
    }
}
