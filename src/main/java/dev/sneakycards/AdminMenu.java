package dev.sneakycards;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Chest GUI for creating cards (9 columns x 6 rows):
 *  row 0: name, skin, title, background (opens a submenu), randomize, reset, test pack, close, create
 *  rows 1-5: one stat per row (label, -5, -1, +1, +5)
 *  columns 6-7: rarity gems, slot 17: add-to-packs toggle, slot 26: slim/classic arms
 *  slots 42-43: move names, 51-52: move stat selectors
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
        set(4, it.menu("random", "Randomize stats", NamedTextColor.AQUA, 1, "Spends the rarity's full stat budget at random"), t -> randomize());
        set(5, it.menu("reset", d.editing == null ? "Reset" : "Stop editing", NamedTextColor.GRAY, 1,
                d.editing == null ? "Clears the form" : "Clears the form and goes back to making a new card"), t -> d.reset());
        set(6, it.menu("gift", "Give test pack", NamedTextColor.LIGHT_PURPLE, 1, "Gives you one pack"),
                t -> player.getInventory().addItem(plugin.items().pack(1)));
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
            set(44, it.menu("info", "Back to card list", NamedTextColor.AQUA, 1, "Open /cardadmin cards"),
                    t -> Bukkit.getScheduler().runTask(plugin, () -> new CardListMenu(plugin, player).open()));
        }

        int[] deltas = {-5, -1, 1, 5};
        String[] icons = {"minus_big", "minus", "plus", "plus_big"};
        Stat[] stats = Stat.values();
        for (int r = 0; r < stats.length; r++) {
            Stat s = stats[r];
            int base = (r + 1) * 9;
            int v = d.stats.get(s);
            set(base, it.menu(s.icon, s.label, NamedTextColor.WHITE, Math.max(1, Math.min(99, v)), "Current: " + v,
                    "Allowed: " + Rules.min(s) + " to " + Rules.max(s)), null);
            for (int k = 0; k < 4; k++) {
                int delta = deltas[k];
                set(base + 1 + k, it.menu(icons[k], s.label + " " + (delta > 0 ? "+" : "") + delta,
                        delta < 0 ? NamedTextColor.RED : NamedTextColor.GREEN, Math.abs(delta), "Click to change"),
                        t -> {
                            int cur = d.stats.get(s), left = Rules.budget(d.rarity) - Rules.total(d.stats);
                            int want = delta > 0 ? Math.min(delta, Math.max(0, left)) : delta;      // can't spend past the budget
                            d.stats.put(s, Math.max(Rules.min(s), Math.min(Rules.max(s), cur + want)));
                        });
            }
        }
        int used = Rules.total(d.stats), budget = Rules.budget(d.rarity);
        boolean over = used > budget;
        set(14, it.menu(over ? "close" : "info", "Stat points: " + used + "/" + budget, over ? NamedTextColor.RED : NamedTextColor.AQUA,
                Math.max(1, Math.min(64, budget - used)),
                over ? "Over budget! Lower some stats to save." : (budget - used) + " points left to spend",
                d.rarity.label + " budget: " + budget + "   Team cost: " + Rules.cost(d.rarity),
                "Type (highest of Str/Dex/Mag) decides match-ups"), null);

        Rarity[] rs = Rarity.values();
        for (int k = 0; k < rs.length; k++) {
            Rarity r = rs[k];
            int slot = k < 3 ? (k + 1) * 9 + 6 : (k - 2) * 9 + 7;
            set(slot, it.menu("gem_" + r.key(), r.label, r.color, 1,
                    d.rarity == r ? "Selected" : "Click to select", "Pack weight: " + r.weight), t -> d.rarity = r);
        }
        set(26, it.menu("skin", d.slim ? "Arms: Slim" : "Arms: Classic", NamedTextColor.WHITE, 1,
                d.slim ? "3-pixel arms (Alex style)" : "4-pixel arms (Steve style)",
                "Match the skin's arm width", "Click to toggle"), t -> d.slim = !d.slim);
        set(17, it.menu(d.inPacks ? "pack_on" : "pack_off", "Add to packs", d.inPacks ? NamedTextColor.GREEN : NamedTextColor.RED, 1,
                d.inPacks ? "ON: enters the pack pool" : "OFF: not in packs", "Click to toggle"), t -> d.inPacks = !d.inPacks);

        for (int k = 0; k < 2; k++) {
            int kk = k;
            set(42 + k, it.menu("move", "Move " + (k + 1), NamedTextColor.YELLOW, 1, or(d.moveNames[k], "(not set)"), "Click, then type in chat"),
                    t -> ask("Type the name of move " + (kk + 1) + ".", v -> d.moveNames[kk] = v));
            Stat ms = d.moveStats[k];
            set(51 + k, it.menu(ms.icon, "Move " + (k + 1) + " power stat", NamedTextColor.WHITE, 1, "Uses " + ms.label, "Click to change"), t -> {
                List<Stat> opts = Stat.MOVE_STATS;
                d.moveStats[kk] = opts.get((opts.indexOf(d.moveStats[kk]) + 1) % opts.size());
            });
        }
    }

    /** Spends the full budget: Health gets 30-40%, the rest is spread over the other stats with one favoured. */
    private void randomize() {
        int budget = Rules.budget(d.rarity);
        int hp = Math.max(Rules.min(Stat.HEALTH), Math.min(Rules.max(Stat.HEALTH), (int) Math.round(budget * (0.30 + RNG.nextDouble() * 0.10))));
        d.stats.put(Stat.HEALTH, hp);
        Stat[] others = {Stat.LUCK, Stat.STRENGTH, Stat.DEXTERITY, Stat.MAGICING};
        for (Stat s : others) d.stats.put(s, Rules.min(s));
        Stat fav = others[1 + RNG.nextInt(3)];
        int left = budget - Rules.total(d.stats), guard = 0;
        while (left > 0 && guard++ < 1000) {
            Stat s = RNG.nextInt(3) == 0 ? fav : others[RNG.nextInt(4)];
            if (d.stats.get(s) < Rules.max(s)) { d.stats.put(s, d.stats.get(s) + 1); left--; }
        }
    }

    private void err(String m) { player.sendMessage(Component.text(m, NamedTextColor.RED)); }

    private void create() {
        if (d.name.isBlank()) { err("Set a card name first."); return; }
        if (!Card.validSkin(d.skin)) { err("Set a skin: a textures.minecraft.net link or a username."); return; }
        if (d.moveNames[0].isBlank() || d.moveNames[1].isBlank()) { err("Each card needs 2 moves. Name both."); return; }
        if (Rules.total(d.stats) > Rules.budget(d.rarity)) {
            err("Stats use " + Rules.total(d.stats) + " points but a " + d.rarity.label + " card may use " + Rules.budget(d.rarity) + ". Lower some stats.");
            return;
        }
        if (d.editing != null) {
            Card old = plugin.registry().get(d.editing);
            if (old == null) { err("That card was deleted. Reset the form to make a new one."); return; }
            Card c = new Card(old.id(), d.name, d.skin, d.title, d.rarity, d.background, new EnumMap<>(d.stats),
                    List.of(new Move(d.moveNames[0], d.moveStats[0]), new Move(d.moveNames[1], d.moveStats[1])), d.inPacks, d.slim);
            plugin.registry().update(c);
            player.sendMessage(Component.text("Saved changes to ", NamedTextColor.GREEN).append(Items.t(c.name(), c.rarity().color()))
                    .append(Component.text(c.inPacks() ? " (in the pack pool)." : " (not in packs).", NamedTextColor.GREEN)));
            return;
        }
        String id = Card.slug(d.name);
        if (id.isEmpty() || plugin.registry().get(id) != null) { err("A card with that name already exists."); return; }
        Card c = new Card(id, d.name, d.skin, d.title, d.rarity, d.background, new EnumMap<>(d.stats),
                List.of(new Move(d.moveNames[0], d.moveStats[0]), new Move(d.moveNames[1], d.moveStats[1])), d.inPacks, d.slim);
        plugin.registry().add(c);
        player.getInventory().addItem(plugin.items().card(c));
        player.sendMessage(Component.text("Created ", NamedTextColor.GREEN).append(Items.t(c.name(), c.rarity().color()))
                .append(Component.text(c.inPacks() ? " and added it to the pack pool." : ".", NamedTextColor.GREEN)));
        d.name = ""; d.skin = "";
    }
}
