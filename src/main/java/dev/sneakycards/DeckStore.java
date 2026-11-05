package dev.sneakycards;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Each player's deck: a list of binder keys ("id", "id#holo", "item:NAME"). Cards stay in the binder; the deck
 * only says which ones to bring. Rules: 15 cards, at least 6 characters, at most 2 copies of a card
 * (holo and normal count together), deck cost at most 35 (Common 1 ... Mythic 6).
 */
public final class DeckStore {
    public static final int SIZE = 15, MIN_CHARS = 6, MAX_COPIES = 2;

    private final SneakyCardsPlugin plugin;
    private final File file;
    private final Map<UUID, List<String>> decks = new HashMap<>();

    DeckStore(SneakyCardsPlugin plugin) { this.plugin = plugin; this.file = new File(plugin.getDataFolder(), "decks.yml"); }

    public int costCap() { return plugin.getConfig().getInt("rules.deck-cost-cap", 35); }

    void load() {
        decks.clear();
        ConfigurationSection sec = YamlConfiguration.loadConfiguration(file).getConfigurationSection("players");
        if (sec == null) return;
        for (String u : sec.getKeys(false)) decks.put(UUID.fromString(u), new ArrayList<>(sec.getStringList(u)));
    }

    void save() {
        YamlConfiguration y = new YamlConfiguration();
        decks.forEach((u, l) -> y.set("players." + u, l));
        try { plugin.getDataFolder().mkdirs(); y.save(file); }
        catch (IOException e) { plugin.getLogger().warning("Could not save decks.yml: " + e.getMessage()); }
    }

    public List<String> get(UUID id) { return decks.computeIfAbsent(id, k -> new ArrayList<>()); }
    public void set(UUID id, List<String> keys) { decks.put(id, new ArrayList<>(keys)); save(); }

    /** What's wrong with a deck, or an empty list if it's ready to play. Checks ownership against the binder. */
    public List<String> problems(UUID owner, List<String> keys) {
        List<String> out = new ArrayList<>();
        CardRegistry reg = plugin.registry();
        int chars = 0, cost = 0;
        Map<String, Integer> copies = new HashMap<>(), used = new HashMap<>();
        for (String k : keys) {
            HandCard h = HandCard.parse(k, reg);
            if (h == null) { out.add("A card in the deck no longer exists"); continue; }
            if (h.isChar()) chars++;
            cost += Rules.cost(h.rarity());
            copies.merge(h.baseKey(), 1, Integer::sum);
            used.merge(k, 1, Integer::sum);
        }
        if (keys.size() != SIZE) out.add("Needs exactly " + SIZE + " cards (has " + keys.size() + ")");
        if (chars < MIN_CHARS) out.add("Needs at least " + MIN_CHARS + " characters (has " + chars + ")");
        if (cost > costCap()) out.add("Deck cost " + cost + " is over " + costCap());
        copies.forEach((k, n) -> { if (n > MAX_COPIES) out.add("More than " + MAX_COPIES + " copies of " + label(k)); });
        Map<String, Integer> own = plugin.binders().counts(owner);
        used.forEach((k, n) -> { if (own.getOrDefault(k, 0) < n) out.add("Your binder doesn't have " + n + "x " + label(k)); });
        return out;
    }

    private String label(String key) {
        HandCard h = HandCard.parse(key, plugin.registry());
        return h == null ? key : h.name() + (h.holo ? " (holo)" : "");
    }

    public static int cost(List<String> keys, CardRegistry reg) {
        int c = 0; for (String k : keys) { HandCard h = HandCard.parse(k, reg); if (h != null) c += Rules.cost(h.rarity()); } return c;
    }
}
