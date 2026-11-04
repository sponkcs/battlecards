package dev.sneakycards;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.*;

public final class CardRegistry {
    private final SneakyCardsPlugin plugin;
    private final File file;
    private final Map<String, Card> cards = new LinkedHashMap<>();
    private Map<Rarity, List<Card>> pool = null;   // rebuilt only when cards change
    private double poolWeight = 0;

    private void dirty() { pool = null; }

    CardRegistry(SneakyCardsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "cards.yml");
    }

    void load() {
        cards.clear();
        dirty();
        if (!file.exists()) { seedDemo(); save(); return; }
        ConfigurationSection sec = YamlConfiguration.loadConfiguration(file).getConfigurationSection("cards");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection c = sec.getConfigurationSection(id);
            if (c != null) cards.put(id, Card.load(id, c));
        }
    }

    void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Card c : cards.values()) c.save(y.createSection("cards." + c.id()));
        try { plugin.getDataFolder().mkdirs(); y.save(file); }
        catch (IOException e) { plugin.getLogger().warning("Could not save cards.yml: " + e.getMessage()); }
    }

    /** Demo card. Replace the skin with a textures.minecraft.net link (see README). */
    private void seedDemo() {
        Map<Stat, Integer> st = new EnumMap<>(Stat.class);
        st.put(Stat.LUCK, 18); st.put(Stat.STRENGTH, 14); st.put(Stat.DEXTERITY, 22);
        st.put(Stat.MAGICING, 9); st.put(Stat.HEALTH, 40);
        cards.put("vince_sunseeker", new Card("vince_sunseeker", "Vince Sunseeker", "Steve", "off-duty",
                Rarity.MYTHIC, Background.SKYLINE, st, List.of(new Move("Sunrise Strike", Stat.STRENGTH), new Move("Golden Hour", Stat.LUCK)), true, true));
    }

    public boolean add(Card c) {
        if (cards.containsKey(c.id())) return false;
        cards.put(c.id(), c);
        dirty();
        save();
        return true;
    }

    /** Replaces an existing card (same id) with edited values. */
    public boolean update(Card c) {
        if (!cards.containsKey(c.id())) return false;
        cards.put(c.id(), c);
        dirty();
        save();
        return true;
    }

    public Card remove(String id) {
        Card c = cards.remove(id);
        if (c != null) { dirty(); save(); }
        return c;
    }

    public Card get(String id) { return cards.get(id); }
    public Collection<Card> all() { return cards.values(); }

    private Map<Rarity, List<Card>> pool() {
        if (pool == null) {
            Map<Rarity, List<Card>> by = new EnumMap<>(Rarity.class);
            for (Card c : cards.values()) if (c.inPacks()) by.computeIfAbsent(c.rarity(), k -> new ArrayList<>()).add(c);
            pool = by;
            poolWeight = by.keySet().stream().mapToDouble(x -> x.weight).sum();
        }
        return pool;
    }

    public boolean hasPool() { return !pool().isEmpty(); }

    public Card draw(Random r) {
        Map<Rarity, List<Card>> by = pool();
        if (by.isEmpty()) return null;
        double roll = r.nextDouble() * poolWeight;
        Rarity pick = null;
        for (Rarity x : by.keySet()) { pick = x; roll -= x.weight; if (roll < 0) break; }
        List<Card> list = by.get(pick);
        return list.get(r.nextInt(list.size()));
    }
}
