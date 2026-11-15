package dev.sneakycards;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Chest GUI for creating cards (9 columns x 6 rows):
 *  row 0: name, skin, title, background (opens a submenu), randomize, reset, test pack, close, create
 *  row 1: type, Health (-10 / +10), retreat cost (-1 / +1)
 *  row 2: rarity gems, add-to-packs toggle, slim/classic arms
 *  rows 3-4: one move per row: name, mana cost (-1 / +1), damage (-10 / +10), effect
 *  row 5: card summary; when editing: back to the card list, delete
 */
public final class AdminMenu implements InventoryHolder {
    private static final Random RNG = new Random();
    private final SneakyCardsPlugin plugin;
    private final Player player;
    private final Draft d;
    private final Inventory inv;
    private final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();

    AdminMenu(SneakyCardsPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.d = plugin.draft(player);
        this.inv = Bukkit.createInventory(this, 54, Component.text(d.editing == null ? "Card Creator" : "Editing: " + d.name));
        render();
    }

    public void open() { player.openInventory(inv); }
    @Override public Inventory getInventory() { return inv; }

    public void click(int slot, ClickType type) {
        Consumer<ClickType> a = actions.get(slot);
        if (a == null) return;
        a.accept(type);
        if (player.getOpenInventory().getTopInventory() == inv) render();
    }

    private void set(int slot, ItemStack item, Consumer<ClickType> action) {
        inv.setItem(slot, item);
        if (action != null) actions.put(slot, action);
    }

    private static String or(String s, String fallback) { return s.isBlank() ? fallback : s; }

    private void ask(String question, Consumer<String> setter) {
        player.sendMessage(Component.text(question + " Type 'cancel' to abort.", NamedTextColor.YELLOW));
        plugin.prompts().ask(player, v -> {
            if (v != null) setter.accept(v.trim());
            new AdminMenu(plugin, player).open();
        });
        Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
    }

    private void render() {
        inv.clear();
        actions.clear();
        Items it = plugin.items();
        ItemStack blank = it.menu("blank", " ", NamedTextColor.GRAY, 1);
        for (int i = 0; i < 54; i++) inv.setItem(i, blank);

        set(0, it.menu("name_tag", "Card name", NamedTextColor.WHITE, 1, or(d.name, "(not set)"), "Click, then type in chat"),
                t -> ask("Type the card name.", v -> d.name = v));
        set(1, it.menu("skin", "Skin", NamedTextColor.WHITE, 1, or(d.skin, "(not set)"),
                        "A textures.minecraft.net link or a username", "Click, then type in chat"),
                t -> ask("Paste the skin link or type a username.", v -> {
                    String c = Card.cleanSkin(v);
                    if (Card.validSkin(c)) { d.skin = c; player.sendMessage(Component.text("Skin set: " + c, NamedTextColor.GREEN)); }
                    else err("That isn't a skin link. Use the http://textures.minecraft.net/texture/... link from mineskin.org, or a username.");
                }));
        set(2, it.menu("collection", "Title", NamedTextColor.WHITE, 1, or(d.title, "(none)"),
                        "Optional flavour text under the name", "Click, then type in chat ('none' to clear)"),
                t -> ask("Type the card's title, for example off-duty. Type 'none' to clear it.", v -> d.title = v.equalsIgnoreCase("none") ? "" : v));
        set(3, it.menu("bg_" + d.background.key(), "Background", NamedTextColor.WHITE, 1, d.background.label, "Click to choose"),
                t -> Bukkit.getScheduler().runTask(plugin, () -> new BackgroundMenu(plugin, player).open()));
        set(4, it.menu("random", "Randomize", NamedTextColor.AQUA, 1, "Random type, Health and moves", "inside this rarity's caps"), t -> randomize());
        set(5, it.menu("reset", d.editing == null ? "Reset" : "Stop editing", NamedTextColor.GRAY, 1,
                d.editing == null ? "Clears the form" : "Clears the form and goes back to making a new card"), t -> d.reset());
        set(6, it.menu("gift", "Give test pack", NamedTextColor.LIGHT_PURPLE, 1, "Gives you one pack"),
                t -> Items.give(player, plugin.items().pack(1)));
        set(7, it.menu("close", "Close menu", NamedTextColor.RED, 1), t -> Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory()));
        if (d.editing == null)
            set(8, it.menu("create", "Create card", NamedTextColor.GREEN, 1, "Saves the card", "Adds it to packs if ON"), t -> create());
        else {
            set(8, it.menu("create", "Save changes", NamedTextColor.GREEN, 1, "Updates " + d.editing, "Cards players already own update too"), t -> create());
            boolean armed = System.currentTimeMillis() - d.deleteArmedAt < 5000;
            set(53, it.menu("close", armed ? "Click again to DELETE" : "Delete card", NamedTextColor.RED, 1,
                    armed ? "This cannot be undone" : "Removes it from packs and binders", "Click twice to confirm"), t -> {
                if (System.currentTimeMillis() - d.deleteArmedAt < 5000) {
                    CardAdmin.delete(plugin, player, d.editing);
                    d.reset();
                } else d.deleteArmedAt = System.currentTimeMillis();
            });
            set(52, it.menu("info", "Back to card list", NamedTextColor.AQUA, 1, "Open /cardadmin cards"),
                    t -> Bukkit.getScheduler().runTask(plugin, () -> new CardListMenu(plugin, player).open()));
        }

        // row 1: type, Health, retreat
        set(9, it.menu(d.type.icon, "Type: " + d.type.label, NamedTextColor.WHITE, 1, "Beats " + beatsLabel(d.type) + " (+" + plugin.rules().weaknessBonus() + " damage)",
                "Weak to " + d.type.weakness().label, "Click to change"), t -> d.type = d.type.next());
        set(11, it.menu("health", "Health: " + d.hp, NamedTextColor.RED, Math.max(1, Math.min(64, d.hp / 10)),
                "Allowed: " + Rules.MIN_HP + " to " + Rules.maxHp(d.rarity) + " for " + d.rarity.label), null);
        set(12, it.menu("minus", "Health -10", NamedTextColor.RED, 1, "Click to change"), t -> { d.hp -= 10; d.clamp(); });
        set(13, it.menu("plus", "Health +10", NamedTextColor.GREEN, 1, "Click to change"), t -> { d.hp += 10; d.clamp(); });
        set(15, it.menu("reset", "Retreat cost: " + (d.retreat == 0 ? "free" : "◆".repeat(d.retreat)), NamedTextColor.WHITE, Math.max(1, d.retreat),
                "Mana the Active discards to swap with a benched card", "0 to " + Rules.MAX_RETREAT), null);
        set(16, it.menu("minus", "Retreat -1", NamedTextColor.RED, 1, "Click to change"), t -> { d.retreat--; d.clamp(); });
        set(17, it.menu("plus", "Retreat +1", NamedTextColor.GREEN, 1, "Click to change"), t -> { d.retreat++; d.clamp(); });

        // row 2: rarity, packs, arms
        Rarity[] rs = Rarity.values();
        for (int k = 0; k < rs.length; k++) {
            Rarity r = rs[k];
            set(18 + k, it.menu("gem_" + r.key(), r.label, r.color, 1,
                    d.rarity == r ? "Selected" : "Click to select", "Health up to " + Rules.maxHp(r), "Deck cost " + Rules.cost(r), "Pack weight: " + r.weight),
                    t -> { d.rarity = r; d.clamp(); });
        }
        set(25, it.menu(d.inPacks ? "pack_on" : "pack_off", "Add to packs", d.inPacks ? NamedTextColor.GREEN : NamedTextColor.RED, 1,
                d.inPacks ? "ON: enters the pack pool" : "OFF: not in packs", "Click to toggle"), t -> d.inPacks = !d.inPacks);
        set(26, it.menu("skin", d.slim ? "Arms: Slim" : "Arms: Classic", NamedTextColor.WHITE, 1,
                d.slim ? "3-pixel arms (Alex style)" : "4-pixel arms (Steve style)",
                "Match the skin's arm width", "Click to toggle"), t -> d.slim = !d.slim);

        // rows 3-4: moves
        for (int k = 0; k < 2; k++) {
            final int kk = k, row = 27 + k * 9;
            int max = Rules.maxDamage(d.moveCosts[k], d.rarity, d.moveEffects[k]);
            set(row, it.menu("move", "Move " + (k + 1) + ": " + or(d.moveNames[k], "(not set)"), NamedTextColor.YELLOW, 1, "Click, then type the name in chat"),
                    t -> ask("Type the name of move " + (kk + 1) + ".", v -> d.moveNames[kk] = v));
            set(row + 1, it.menu("mana", "Mana cost: " + (d.moveCosts[k] == 0 ? "free" : "◆".repeat(d.moveCosts[k])), NamedTextColor.AQUA, Math.max(1, d.moveCosts[k]),
                    "Mana that must be attached to use it", "Mana stays on the card: it isn't spent", "0 to " + Rules.MAX_MOVE_COST), null);
            set(row + 2, it.menu("minus", "Cost -1", NamedTextColor.RED, 1, "Click to change"), t -> { d.moveCosts[kk]--; d.clamp(); });
            set(row + 3, it.menu("plus", "Cost +1", NamedTextColor.GREEN, 1, "Click to change"), t -> { d.moveCosts[kk]++; d.clamp(); });
            set(row + 4, it.menu(d.type.icon, "Damage: " + d.moveDamage[k], NamedTextColor.WHITE, Math.max(1, Math.min(64, d.moveDamage[k] / 10)),
                    "Up to " + max + " for " + d.moveCosts[k] + " mana on a " + d.rarity.label + (d.moveEffects[k] == MoveEffect.NONE ? "" : " with " + d.moveEffects[k].label),
                    "Weakness adds +" + plugin.rules().weaknessBonus()), null);
            set(row + 5, it.menu("minus", "Damage -10", NamedTextColor.RED, 1, "Click to change"), t -> { d.moveDamage[kk] -= 10; d.clamp(); });
            set(row + 6, it.menu("plus", "Damage +10", NamedTextColor.GREEN, 1, "Up to " + max, "Click to change"), t -> { d.moveDamage[kk] += 10; d.clamp(); });
            MoveEffect e = d.moveEffects[k];
            set(row + 8, it.menu(e == MoveEffect.NONE ? "blank" : "random", "Effect: " + e.label, NamedTextColor.LIGHT_PURPLE, 1,
                    e == MoveEffect.NONE ? "Just damage" : e.description,
                    e.budget > 0 ? "Uses " + e.budget + " of the damage budget" : e.budget < 0 ? "Adds " + (-e.budget) + " to the damage budget" : "",
                    "Click to change"), t -> { d.moveEffects[kk] = d.moveEffects[kk].next(); d.clamp(); });
        }

        // row 5: summary
        List<String> sum = new ArrayList<>();
        sum.add(d.rarity.label + " " + d.type.label + "  ❤ " + d.hp + "  Retreat " + d.retreat);
        for (int k = 0; k < 2; k++) {
            Move m = d.move(k);
            sum.add(m.costText() + " " + or(m.name(), "Move " + (k + 1)) + (m.damage() > 0 ? "  " + m.damage() : "") + (m.effect() == MoveEffect.NONE ? "" : "  (" + m.effect().label + ")"));
        }
        sum.add("Deck cost " + Rules.cost(d.rarity));
        set(45, it.menu("info", "Card summary", NamedTextColor.AQUA, 1, sum.toArray(new String[0])), null);
    }

    private static String beatsLabel(CardType t) { for (CardType o : CardType.values()) if (t.beats(o)) return o.label; return "-"; }

    /** Random type, Health and moves inside the rarity's caps: a cheap move and a 2-3 mana move. */
    private void randomize() {
        d.type = CardType.values()[RNG.nextInt(3)];
        int maxHp = Rules.maxHp(d.rarity);
        d.hp = Rules.round10(maxHp * (0.7 + RNG.nextDouble() * 0.3));
        d.retreat = RNG.nextInt(Rules.MAX_RETREAT + 1);
        MoveEffect[] fx = MoveEffect.values();
        d.moveCosts[0] = 1; d.moveCosts[1] = 2 + RNG.nextInt(2);
        for (int k = 0; k < 2; k++) {
            d.moveEffects[k] = RNG.nextInt(3) == 0 ? fx[1 + RNG.nextInt(fx.length - 1)] : MoveEffect.NONE;
            int max = Rules.maxDamage(d.moveCosts[k], d.rarity, d.moveEffects[k]);
            d.moveDamage[k] = Math.max(0, max - 10 * RNG.nextInt(2));
        }
        d.clamp();
    }

    private void err(String m) { player.sendMessage(Component.text(m, NamedTextColor.RED)); }

    private void create() {
        if (d.name.isBlank()) { err("Set a card name first."); return; }
        if (!Card.validSkin(d.skin)) { err("Set a skin: a textures.minecraft.net link or a username."); return; }
        if (d.moveNames[0].isBlank() || d.moveNames[1].isBlank()) { err("Each card needs 2 moves. Name both."); return; }
        d.clamp();
        if (d.editing != null) {
            Card old = plugin.registry().get(d.editing);
            if (old == null) { err("That card was deleted. Reset the form to make a new one."); return; }
            Card c = new Card(old.id(), d.name, d.skin, d.title, d.rarity, d.background, d.type, d.hp, d.retreat,
                    List.of(d.move(0), d.move(1)), d.inPacks, d.slim);
            plugin.registry().update(c);
            player.sendMessage(Component.text("Saved changes to ", NamedTextColor.GREEN).append(Items.t(c.name(), c.rarity().color()))
                    .append(Component.text(c.inPacks() ? " (in the pack pool)." : " (not in packs).", NamedTextColor.GREEN)));
            return;
        }
        String id = Card.slug(d.name);
        if (id.isEmpty() || plugin.registry().get(id) != null) { err("A card with that name already exists."); return; }
        Card c = new Card(id, d.name, d.skin, d.title, d.rarity, d.background, d.type, d.hp, d.retreat,
                List.of(d.move(0), d.move(1)), d.inPacks, d.slim);
        plugin.registry().add(c);
        Items.give(player, plugin.items().card(c));
        player.sendMessage(Component.text("Created ", NamedTextColor.GREEN).append(Items.t(c.name(), c.rarity().color()))
                .append(Component.text(c.inPacks() ? " and added it to the pack pool." : ".", NamedTextColor.GREEN)));
        d.name = ""; d.skin = "";
    }
}
