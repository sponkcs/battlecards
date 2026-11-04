package dev.sneakycards;

import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SneakyCardsPlugin extends JavaPlugin {
    private CardRegistry registry;
    private BinderStore binders;
    private Items items;
    private DisplayManager displays;
    private BattleManager battles;
    private PromptManager prompts;
    private final Map<UUID, Draft> drafts = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        items = new Items(this);
        registry = new CardRegistry(this);
        registry.load();
        binders = new BinderStore(this);
        binders.load();
        binders.startAutosave();
        prompts = new PromptManager();
        displays = new DisplayManager(this);
        battles = new BattleManager(this);
        getServer().getPluginManager().registerEvents(new Listeners(this), this);
        AdminCommand cmd = new AdminCommand(this);
        getCommand("cardadmin").setExecutor(cmd);
        getCommand("cardadmin").setTabCompleter(cmd);
        PlayCommand play = new PlayCommand(this);
        getCommand("sneakycards").setExecutor(play);
        getCommand("sneakycards").setTabCompleter(play);
        getCommand("binder").setExecutor((sender, c, l, a) -> {
            if (sender instanceof Player p) new BinderMenu(this, p).open();
            return true;
        });
        displays.start();
    }

    @Override
    public void onDisable() {
        if (battles != null) battles.clearAll();
        if (displays != null) displays.clearAll();
        if (registry != null) registry.save();
        if (binders != null) binders.save();
    }

    public void sendPack(Player p) {
        String url = getConfig().getString("resource-pack.url", "");
        String sha1 = getConfig().getString("resource-pack.sha1", "");
        if (url.isBlank() || sha1.length() != 40) return;
        try {
            ResourcePackInfo info = ResourcePackInfo.resourcePackInfo()
                    .id(UUID.nameUUIDFromBytes("sneakycards-pack".getBytes(StandardCharsets.UTF_8)))
                    .uri(URI.create(url)).hash(sha1).build();
            p.sendResourcePacks(ResourcePackRequest.resourcePackRequest().packs(info)
                    .prompt(Component.text("Card game textures and models"))
                    .required(getConfig().getBoolean("resource-pack.required", false)).build());
        } catch (IllegalArgumentException e) {
            getLogger().warning("Bad resource-pack settings in config.yml: " + e.getMessage());
        }
    }

    public Draft draft(Player p) { return drafts.computeIfAbsent(p.getUniqueId(), k -> new Draft()); }
    public CardRegistry registry() { return registry; }
    public BinderStore binders() { return binders; }
    public Items items() { return items; }
    public DisplayManager displays() { return displays; }
    public BattleManager battles() { return battles; }
    public PromptManager prompts() { return prompts; }
}
