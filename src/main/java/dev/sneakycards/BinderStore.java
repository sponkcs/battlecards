package dev.sneakycards;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.*;

/** Each player's collection: card id -> how many copies they own. */
public final class BinderStore {
    private final SneakyCardsPlugin plugin;
    private final File file;
    private final Map<UUID, Map<String, Integer>> data = new HashMap<>();

    BinderStore(SneakyCardsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "binders.yml");
    }

    void load() {
        data.clear();
        ConfigurationSection sec = YamlConfiguration.loadConfiguration(file).getConfigurationSection("players");
        if (sec == null) return;
        for (String u : sec.getKeys(false)) {
            ConfigurationSection p = sec.getConfigurationSection(u);
            if (p == null) continue;
            Map<String, Integer> m = new HashMap<>();
            for (String id : p.getKeys(false)) m.put(id, p.getInt(id));
            data.put(UUID.fromString(u), m);
        }
    }

    void save() {
        YamlConfiguration y = new YamlConfiguration();
        data.forEach((u, m) -> m.forEach((id, n) -> { if (n > 0) y.set("players." + u + "." + id, n); }));
        try { plugin.getDataFolder().mkdirs(); y.save(file); }
        catch (IOException e) { plugin.getLogger().warning("Could not save binders.yml: " + e.getMessage()); }
    }

    public Map<String, Integer> counts(UUID id) { return data.computeIfAbsent(id, k -> new HashMap<>()); }
    public void add(UUID id, String card, int n) { counts(id).merge(card, n, Integer::sum); save(); }

    public boolean take(UUID id, String card) {
        Map<String, Integer> m = counts(id);
        int have = m.getOrDefault(card, 0);
        if (have <= 0) return false;
        if (have == 1) m.remove(card); else m.put(card, have - 1);
        save();
        return true;
    }
}
