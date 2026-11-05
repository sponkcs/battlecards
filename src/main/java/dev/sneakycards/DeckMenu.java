package dev.sneakycards;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import java.util.*;

/**
 * Deck builder, opened from the binder.
 *   rows 0-1  your deck (15 slots); click a card to take it out. Slot 16 = deck status, 17 = back to binder.
 *   rows 2-4  your binder; click a card to add it.
 *   row 5     previous page, filter, rules, clear, next page.
 */
public final class DeckMenu implements InventoryHolder {
    private static final int STATUS = 16, BACK = 17, COLL = 18, PER_PAGE = 27, PREV = 45, FILTER = 47, RULES = 49, CLEAR = 51, NEXT = 53;
    private static final String[] FILTERS = {"All cards", "Characters", "Items and traps"};

    private final SneakyCardsPlugin plugin;
    private final Player player;
    private final Inventory inv;
    private final List<String> deck;
    private final List<String> shown = new ArrayList<>();
    private int page = 0, filter = 0;

    DeckMenu(SneakyCardsPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.deck = new ArrayList<>(plugin.decks().get(player.getUniqueId()));
        this.inv = Bukkit.createInventory(this, 54, Component.text("Deck builder"));
        render();
    }

    public void open() { player.openInventory(inv); }
    @Override public Inventory getInventory() { return inv; }

    private void save() { plugin.decks().set(player.getUniqueId(), deck); }

    private int copies(String baseKey) {
        int n = 0; for (String k : deck) { HandCard h = HandCard.parse(k, plugin.registry()); if (h != null && h.baseKey().equals(baseKey)) n++; } return n;
    }

    private void render() {
        inv.clear();
        Items it = plugin.items();
        CardRegistry reg = plugin.registry();
        // the deck, characters first then items, each by rarity
        List<String> sorted = new ArrayList<>(deck);
        sorted.sort(Comparator.comparing((String k) -> { HandCard h = HandCard.parse(k, reg); return h == null || !h.isChar(); })
                .thenComparing(k -> { HandCard h = HandCard.parse(k, reg); return h == null ? 0 : -h.rarity().ordinal(); }));
        deck.clear(); deck.addAll(sorted);
        for (int i = 0; i < DeckStore.SIZE; i++) {
            if (i >= deck.size()) { inv.setItem(i, it.menu("blank", "Empty deck slot", NamedTextColor.DARK_GRAY, 1, "Click a card below to add it")); continue; }
            HandCard h = HandCard.parse(deck.get(i), reg);
            if (h == null) { inv.setItem(i, it.menu("close", "Missing card", NamedTextColor.RED, 1, "Click to remove")); continue; }
            ItemStack s = it.of(h);
            s.editMeta(m -> { var lore = m.lore() == null ? new ArrayList<Component>() : new ArrayList<>(m.lore()); lore.add(Component.empty()); lore.add(Items.t("Click to take it out of the deck", NamedTextColor.YELLOW)); m.lore(lore); });
            inv.setItem(i, s);
        }
        // status
        List<String> problems = plugin.decks().problems(player.getUniqueId(), deck);
        int chars = 0; for (String k : deck) { HandCard h = HandCard.parse(k, reg); if (h != null && h.isChar()) chars++; }
        int cost = DeckStore.cost(deck, reg), cap = plugin.decks().costCap();
        List<String> lore = new ArrayList<>(List.of("Cards: " + deck.size() + "/" + DeckStore.SIZE, "Characters: " + chars + " (" + DeckStore.MIN_CHARS + " or more)",
                "Deck cost: " + cost + "/" + cap));
        if (problems.isEmpty()) lore.add("Ready: right-click your side of a mat, or /sc solo");
        else lore.addAll(problems);
        inv.setItem(STATUS, it.menu(problems.isEmpty() ? "create" : "close", problems.isEmpty() ? "Deck ready" : "Deck not ready",
                problems.isEmpty() ? NamedTextColor.GREEN : NamedTextColor.RED, Math.max(1, deck.size()), lore.toArray(new String[0])));
        inv.setItem(BACK, it.menu("prev", "Back to binder", NamedTextColor.WHITE, 1));

        // the binder
        shown.clear();
        Map<String, Integer> own = plugin.binders().counts(player.getUniqueId());
        List<String> keys = new ArrayList<>();
        for (var e : own.entrySet()) {
            if (e.getValue() <= 0) continue;
            HandCard h = HandCard.parse(e.getKey(), reg);
            if (h == null) continue;
            if (filter == 1 && !h.isChar()) continue;
            if (filter == 2 && h.isChar()) continue;
            keys.add(e.getKey());
        }
        keys.sort(Comparator.comparing((String k) -> !HandCard.parse(k, reg).isChar())
                .thenComparing(k -> -HandCard.parse(k, reg).rarity().ordinal()).thenComparing(k -> HandCard.parse(k, reg).name()));
        int pages = Math.max(1, (int) Math.ceil(keys.size() / (double) PER_PAGE));
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= keys.size()) break;
            String k = keys.get(idx);
            HandCard h = HandCard.parse(k, reg);
            int inDeck = 0; for (String d : deck) if (d.equals(k)) inDeck++;
            int ownN = own.getOrDefault(k, 0), copies = copies(h.baseKey());
            boolean can = deck.size() < DeckStore.SIZE && copies < DeckStore.MAX_COPIES && inDeck < ownN;
            ItemStack s = it.of(h);
            s.setAmount(Math.max(1, Math.min(64, ownN)));
            final boolean canF = can; final int inDeckF = inDeck, copiesF = copies;
            s.editMeta(m -> {
                var l = m.lore() == null ? new ArrayList<Component>() : new ArrayList<>(m.lore());
                l.add(Component.empty());
                l.add(Items.t("In binder: " + ownN + "   In deck: " + inDeckF + "   (max " + DeckStore.MAX_COPIES + " of a card)", NamedTextColor.GRAY));
                l.add(Items.t(canF ? "Click to add to your deck" : deck.size() >= DeckStore.SIZE ? "Deck is full" : copiesF >= DeckStore.MAX_COPIES ? "Already 2 in the deck" : "All your copies are in the deck",
                        canF ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY));
                m.lore(l);
            });
            inv.setItem(COLL + i, s);
            shown.add(k);
        }
        if (page > 0) inv.setItem(PREV, it.menu("prev", "Previous page", NamedTextColor.WHITE, 1));
        if (page < pages - 1) inv.setItem(NEXT, it.menu("next", "Next page", NamedTextColor.WHITE, 1));
        inv.setItem(FILTER, it.menu("random", "Showing: " + FILTERS[filter], NamedTextColor.AQUA, 1, "Click to switch"));
        inv.setItem(RULES, it.menu("info", "Deck rules", NamedTextColor.AQUA, 1,
                DeckStore.SIZE + " cards, at least " + DeckStore.MIN_CHARS + " characters",
                "At most " + DeckStore.MAX_COPIES + " copies of a card (holo counts too)",
                "Deck cost " + cap + " or less: Common 1 ... Mythic 6",
                "Cards stay in your binder"));
        inv.setItem(CLEAR, it.menu("reset", "Clear deck", NamedTextColor.RED, 1, "Takes every card out"));
    }

    public void click(int slot) {
        if (slot >= 0 && slot < DeckStore.SIZE) {
            if (slot < deck.size()) { deck.remove(slot); save(); render(); }
            return;
        }
        if (slot == BACK) { Bukkit.getScheduler().runTask(plugin, () -> new BinderMenu(plugin, player).open()); return; }
        if (slot == PREV) { page--; render(); return; }
        if (slot == NEXT) { page++; render(); return; }
        if (slot == FILTER) { filter = (filter + 1) % FILTERS.length; page = 0; render(); return; }
        if (slot == CLEAR) { deck.clear(); save(); render(); return; }
        int idx = slot - COLL;
        if (idx < 0 || idx >= shown.size()) return;
        String k = shown.get(idx);
        HandCard h = HandCard.parse(k, plugin.registry());
        int inDeck = 0; for (String d : deck) if (d.equals(k)) inDeck++;
        if (deck.size() >= DeckStore.SIZE) { BattleManager.say(player, "Your deck is full (15). Click a deck card to take it out.", NamedTextColor.YELLOW); return; }
        if (copies(h.baseKey()) >= DeckStore.MAX_COPIES) { BattleManager.say(player, "You already have 2 of " + h.name() + " in the deck.", NamedTextColor.YELLOW); return; }
        if (inDeck >= plugin.binders().counts(player.getUniqueId()).getOrDefault(k, 0)) return;
        deck.add(k); save(); render();
    }
}
