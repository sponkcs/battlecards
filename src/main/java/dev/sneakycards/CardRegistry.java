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
        if (!file.exists()) { seedDemo(); for (Card c : starterSet()) cards.put(c.id(), c); save(); return; }
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

    /** 15 test cards (placeholder skin) built for the balance rules: within their stat budgets, mixed types. */
    public static List<Card> starterSet() {
        List<Card> out = new ArrayList<>();
        starter(out, "pip_the_paperboy", "Pip the Paperboy", "street kid", Rarity.COMMON, Background.SLUMS, 8, 10, 16, 4, 34,
                "Paper Toss", Stat.DEXTERITY, "Lucky Penny", Stat.LUCK);
        starter(out, "greta_grovekeeper", "Greta Grovekeeper", "forest warden", Rarity.COMMON, Background.GROVE, 6, 20, 8, 8, 30,
                "Branch Swing", Stat.STRENGTH, "Sap Spell", Stat.MAGICING);
        starter(out, "mossy_mike", "Mossy Mike", "goo gardener", Rarity.COMMON, Background.GOO, 10, 4, 6, 22, 30,
                "Goo Glob", Stat.MAGICING, "Slip Up", Stat.LUCK);
        starter(out, "tunnel_tess", "Tunnel Tess", "night miner", Rarity.COMMON, Background.MINES, 6, 20, 10, 4, 32,
                "Pickaxe Poke", Stat.STRENGTH, "Rockslide Dodge", Stat.DEXTERITY);
        starter(out, "ember_jinx", "Ember Jinx", "fire juggler", Rarity.UNCOMMON, Background.HELLFIRE, 10, 6, 12, 22, 28,
                "Fireball", Stat.MAGICING, "Quick Toss", Stat.DEXTERITY);
        starter(out, "rusty_rourke", "Rusty Rourke", "scrap boxer", Rarity.UNCOMMON, Background.SLUMS, 8, 22, 12, 2, 34,
                "Haymaker", Stat.STRENGTH, "Jab", Stat.DEXTERITY);
        starter(out, "wren_swiftfoot", "Wren Swiftfoot", "orchard runner", Rarity.UNCOMMON, Background.BLOSSOM, 10, 10, 18, 4, 36,
                "Blossom Dash", Stat.DEXTERITY, "Petal Luck", Stat.LUCK);
        starter(out, "sister_lumen", "Sister Lumen", "spire acolyte", Rarity.RARE, Background.SPIRE, 8, 2, 8, 32, 34,
                "Starlight", Stat.MAGICING, "Lucky Prayer", Stat.LUCK);
        starter(out, "brick_bronson", "Brick Bronson", "club bouncer", Rarity.RARE, Background.SKYLINE, 6, 28, 8, 2, 40,
                "Body Slam", Stat.STRENGTH, "Shove", Stat.DEXTERITY);
        starter(out, "nyx_nightwire", "Nyx Nightwire", "rooftop thief", Rarity.RARE, Background.SKYLINE, 12, 8, 20, 8, 36,
                "Shadow Step", Stat.DEXTERITY, "Pickpocket", Stat.LUCK);
        starter(out, "gorrim_deepdelve", "Gorrim Deepdelve", "dwarf foreman", Rarity.EPIC, Background.MINES, 8, 30, 6, 6, 40,
                "Mountain Breaker", Stat.STRENGTH, "Ale Toss", Stat.LUCK);
        starter(out, "vela_thornwhisper", "Vela Thornwhisper", "grove druid", Rarity.EPIC, Background.GROVE, 10, 4, 12, 30, 34,
                "Thorn Wall", Stat.MAGICING, "Vine Lash", Stat.DEXTERITY);
        starter(out, "infernus_rex", "Infernus Rex", "pit champion", Rarity.LEGENDARY, Background.HELLFIRE, 10, 32, 10, 8, 36,
                "Hellfire Fist", Stat.STRENGTH, "Brimstone", Stat.MAGICING);
        starter(out, "archmagus_orrin", "Arch-Magus Orrin", "spire master", Rarity.LEGENDARY, Background.SPIRE, 12, 4, 10, 36, 34,
                "Arcane Storm", Stat.MAGICING, "Blink", Stat.DEXTERITY);
        starter(out, "silk_phantom", "Silk Phantom", "masked duelist", Rarity.MYTHIC, Background.BLOSSOM, 12, 14, 28, 10, 40,
                "Thousand Cuts", Stat.DEXTERITY, "Fortune's Edge", Stat.LUCK);
        return out;
    }

    private static void starter(List<Card> out, String id, String name, String title, Rarity r, Background bg,
                                int luck, int str, int dex, int mag, int hp, String m1, Stat s1, String m2, Stat s2) {
        Map<Stat, Integer> st = new EnumMap<>(Stat.class);
        st.put(Stat.LUCK, luck); st.put(Stat.STRENGTH, str); st.put(Stat.DEXTERITY, dex); st.put(Stat.MAGICING, mag); st.put(Stat.HEALTH, hp);
        out.add(new Card(id, name, "placeholder", title, r, bg, st, List.of(new Move(m1, s1), new Move(m2, s2)), true, false));
    }

    /** Adds any starter cards that are missing. Returns how many were added. */
    public int addStarterSet() {
        int n = 0;
        for (Card c : starterSet()) if (!cards.containsKey(c.id())) { cards.put(c.id(), c); n++; }
        if (n > 0) { dirty(); save(); }
        return n;
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

    /**
     * A card drawn with custom rarity weights (index = rarity), optionally only from one background.
     * Falls back to the whole pack pool if nothing matches.
     */
    public Card draw(Random r, double[] weights, Background only) {
        Map<Rarity, List<Card>> by = new EnumMap<>(Rarity.class);
        pool().forEach((k, v) -> {
            List<Card> l = only == null ? v : v.stream().filter(c -> c.background() == only).toList();
            if (!l.isEmpty() && weights[k.ordinal()] > 0) by.put(k, l);
        });
        if (by.isEmpty()) return only != null ? draw(r, weights, null) : draw(r);
        double total = by.keySet().stream().mapToDouble(x -> weights[x.ordinal()]).sum(), roll = r.nextDouble() * total;
        Rarity pick = null;
        for (Rarity x : by.keySet()) { pick = x; roll -= weights[x.ordinal()]; if (roll < 0) break; }
        List<Card> list = by.get(pick);
        return list.get(r.nextInt(list.size()));
    }

    /** The normal rarity weights. */
    public static double[] baseWeights() { double[] w = new double[Rarity.values().length]; for (Rarity x : Rarity.values()) w[x.ordinal()] = x.weight; return w; }

    /** A card of at least the given rarity (falls back to any card if none qualify). */
    public Card draw(Random r, Rarity min) {
        Map<Rarity, List<Card>> by = new EnumMap<>(Rarity.class);
        pool().forEach((k, v) -> { if (k.ordinal() >= min.ordinal()) by.put(k, v); });
        if (by.isEmpty()) return draw(r);
        double total = by.keySet().stream().mapToDouble(x -> x.weight).sum(), roll = r.nextDouble() * total;
        Rarity pick = null;
        for (Rarity x : by.keySet()) { pick = x; roll -= x.weight; if (roll < 0) break; }
        List<Card> list = by.get(pick);
        return list.get(r.nextInt(list.size()));
    }

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
