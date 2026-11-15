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

    /** cards.yml format: 2 = mana rework (type, Health, retreat, moves with cost/damage/effect). */
    static final int FORMAT = 2;

    void load() {
        cards.clear();
        dirty();
        if (!file.exists()) { for (Card c : templates()) cards.put(c.id(), c); save(); return; }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("cards");
        if (sec == null) return;
        boolean old = y.getInt("format", 1) < FORMAT;
        Map<String, Card> tpl = new HashMap<>();
        for (Card c : templates()) tpl.put(c.id(), c);
        int updated = 0, converted = 0;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection c = sec.getConfigurationSection(id);
            if (c == null) continue;
            Card card = Card.load(id, c);
            if (old && tpl.containsKey(id)) { card = tpl.get(id).withLook(card.skin(), card.slim(), card.title(), card.inPacks()); updated++; }
            else if (Card.isLegacy(c)) converted++;
            cards.put(id, card);
        }
        if (old) {
            save();
            plugin.getLogger().info("Cards updated for the mana rules: " + updated + " template cards replaced, " + converted
                    + " custom cards converted (check them in /cardadmin cards).");
        }
    }

    void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("format", FORMAT);
        for (Card c : cards.values()) c.save(y.createSection("cards." + c.id()));
        try { plugin.getDataFolder().mkdirs(); y.save(file); }
        catch (IOException e) { plugin.getLogger().warning("Could not save cards.yml: " + e.getMessage()); }
    }

    /** The demo card plus the 15 test cards. */
    public static List<Card> templates() {
        List<Card> out = new ArrayList<>();
        out.add(new Card("vince_sunseeker", "Vince Sunseeker", "Steve", "off-duty", Rarity.MYTHIC, Background.SKYLINE, CardType.DEXTERITY, 120, 1,
                List.of(new Move("Sunrise Strike", 2, 70, MoveEffect.NONE), new Move("Golden Hour", 3, 90, MoveEffect.HEAL)), true, true));
        out.addAll(starterSet());
        return out;
    }

    /**
     * 15 test cards (placeholder skin), built to the card creator's caps. Cheap cards hit early; rare cards need more mana
     * but hit harder and take more hits. Each type has a fast attacker, a tank and a trick card.
     */
    public static List<Card> starterSet() {
        List<Card> out = new ArrayList<>();
        S(out, "pip_the_paperboy", "Pip the Paperboy", "street kid", Rarity.COMMON, Background.SLUMS, CardType.DEXTERITY, 60, 1,
                "Paper Toss", 1, 20, MoveEffect.NONE, "Lucky Penny", 1, 10, MoveEffect.DRAW);
        S(out, "greta_grovekeeper", "Greta Grovekeeper", "forest warden", Rarity.COMMON, Background.GROVE, CardType.STRENGTH, 70, 2,
                "Branch Swing", 1, 20, MoveEffect.NONE, "Sap Spell", 2, 40, MoveEffect.HEAL);
        S(out, "mossy_mike", "Mossy Mike", "goo gardener", Rarity.COMMON, Background.GOO, CardType.MAGICING, 60, 1,
                "Goo Glob", 1, 20, MoveEffect.NONE, "Slip Up", 2, 30, MoveEffect.DAZE);
        S(out, "tunnel_tess", "Tunnel Tess", "night miner", Rarity.COMMON, Background.MINES, CardType.STRENGTH, 70, 1,
                "Pickaxe Poke", 1, 20, MoveEffect.NONE, "Rockslide", 2, 50, MoveEffect.NONE);
        S(out, "ember_jinx", "Ember Jinx", "fire juggler", Rarity.UNCOMMON, Background.HELLFIRE, CardType.MAGICING, 70, 1,
                "Quick Toss", 1, 20, MoveEffect.NONE, "Fireball", 2, 50, MoveEffect.NONE);
        S(out, "rusty_rourke", "Rusty Rourke", "scrap boxer", Rarity.UNCOMMON, Background.SLUMS, CardType.STRENGTH, 80, 2,
                "Jab", 1, 20, MoveEffect.NONE, "Haymaker", 3, 80, MoveEffect.NONE);
        S(out, "wren_swiftfoot", "Wren Swiftfoot", "orchard runner", Rarity.UNCOMMON, Background.BLOSSOM, CardType.DEXTERITY, 70, 0,
                "Blossom Dash", 1, 30, MoveEffect.NONE, "Petal Luck", 2, 30, MoveEffect.CHARGE);
        S(out, "sister_lumen", "Sister Lumen", "spire acolyte", Rarity.RARE, Background.SPIRE, CardType.MAGICING, 80, 1,
                "Lucky Prayer", 1, 20, MoveEffect.HEAL, "Starlight", 2, 60, MoveEffect.NONE);
        S(out, "brick_bronson", "Brick Bronson", "club bouncer", Rarity.RARE, Background.SKYLINE, CardType.STRENGTH, 100, 3,
                "Shove", 1, 20, MoveEffect.NONE, "Body Slam", 3, 80, MoveEffect.DAZE);
        S(out, "nyx_nightwire", "Nyx Nightwire", "rooftop thief", Rarity.RARE, Background.SKYLINE, CardType.DEXTERITY, 80, 0,
                "Pickpocket", 1, 20, MoveEffect.DRAIN, "Shadow Step", 2, 60, MoveEffect.NONE);
        S(out, "gorrim_deepdelve", "Gorrim Deepdelve", "dwarf foreman", Rarity.EPIC, Background.MINES, CardType.STRENGTH, 110, 2,
                "Ale Toss", 1, 30, MoveEffect.NONE, "Mountain Breaker", 3, 100, MoveEffect.NONE);
        S(out, "vela_thornwhisper", "Vela Thornwhisper", "grove druid", Rarity.EPIC, Background.GROVE, CardType.MAGICING, 100, 1,
                "Vine Lash", 1, 20, MoveEffect.DAZE, "Thorn Wall", 2, 50, MoveEffect.HEAL);
        S(out, "infernus_rex", "Infernus Rex", "pit champion", Rarity.LEGENDARY, Background.HELLFIRE, CardType.STRENGTH, 130, 3,
                "Brimstone", 2, 50, MoveEffect.SPLASH, "Hellfire Fist", 4, 150, MoveEffect.OVERLOAD);
        S(out, "archmagus_orrin", "Arch-Magus Orrin", "spire master", Rarity.LEGENDARY, Background.SPIRE, CardType.MAGICING, 110, 1,
                "Blink", 1, 30, MoveEffect.DRAW, "Arcane Storm", 3, 90, MoveEffect.SPLASH);
        S(out, "silk_phantom", "Silk Phantom", "masked duelist", Rarity.MYTHIC, Background.BLOSSOM, CardType.DEXTERITY, 120, 1,
                "Thousand Cuts", 2, 60, MoveEffect.DRAIN, "Fortune's Edge", 3, 110, MoveEffect.NONE);
        return out;
    }

    private static void S(List<Card> out, String id, String name, String title, Rarity r, Background bg, CardType type, int hp, int retreat,
                          String m1, int c1, int d1, MoveEffect e1, String m2, int c2, int d2, MoveEffect e2) {
        out.add(new Card(id, name, "placeholder", title, r, bg, type, hp, retreat,
                List.of(new Move(m1, c1, d1, e1), new Move(m2, c2, d2, e2)), true, false));
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
