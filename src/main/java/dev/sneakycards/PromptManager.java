package dev.sneakycards;

import org.bukkit.entity.Player;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Lets the menu ask for text in chat. The callback gets null if the player typed "cancel". */
public final class PromptManager {
    private final Map<UUID, Consumer<String>> pending = new ConcurrentHashMap<>();
    void ask(Player p, Consumer<String> cb) { pending.put(p.getUniqueId(), cb); }
    Consumer<String> take(UUID id) { return pending.remove(id); }
}
