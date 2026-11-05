package dev.sneakycards;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import java.time.Duration;
import java.util.*;

/**
 * Game mats and the battle rules.
 *
 * Rules (kept simple on purpose):
 *  1. Right-click a slot on a mat while holding a card to put it there. The first card claims that side of the mat.
 *  2. When both sides have 3 cards the battle starts. The side with the higher total Dexterity goes first;
 *     on a tie a coin is flipped.
 *  3. On your turn pick one of your cards, one of its 2 moves, and an enemy card. The move deals damage equal
 *     to the card's value in the move's stat.
 *  4. The attacking card's Luck is its percent chance to deal double damage.
 *  5. A card at 0 Health is knocked out. Knock out all 3 enemy cards to win.
 */
public final class BattleManager {
    private final SneakyCardsPlugin plugin;
    private final List<Mat> mats = new ArrayList<>();
    private final Map<UUID, Mat> byEntity = new HashMap<>();
    private final Map<UUID, int[]> slotOf = new HashMap<>();
    private final Map<UUID, Mat> byPlayer = new HashMap<>();
    private final Random rng = new Random();

    BattleManager(SneakyCardsPlugin plugin) { this.plugin = plugin; }

    private DisplayManager displays() { return plugin.displays(); }

    static void say(Player p, String text, NamedTextColor c) { if (p != null) p.sendMessage(Component.text(text, c)); }

    private static String nameOf(UUID id) {
        if (id == null) return "nobody";
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? "a player" : n;
    }

    private void tell(Mat m, Component msg) {
        for (UUID id : m.players) { Player p = id == null ? null : Bukkit.getPlayer(id); if (p != null) p.sendMessage(msg); }
    }

    // ---------------------------------------------------------------- mats

    public Mat matOf(Entity e) { return byEntity.get(e.getUniqueId()); }

    /** {side, column} for a slot hitbox, {-1, -1} for the centre of the mat, null if not a mat entity. */
    public int[] slotOf(Entity e) { return slotOf.get(e.getUniqueId()); }

    public Mat matOfPlayer(UUID id) { return byPlayer.get(id); }

    public Mat placeMat(Player p, Location top, float yaw, MatVariant v) {
        for (Mat o : mats) {
            if (o.center.getWorld().equals(top.getWorld()) && o.center.distanceSquared(top) < 16) {
                say(p, "That is too close to another mat.", NamedTextColor.RED);
                return null;
            }
        }
        World w = top.getWorld();
        Mat m = new Mat(top.clone(), yaw, v);
        ItemStack item = plugin.items().mat(v);
        m.display = w.spawn(top.clone().add(0, 0.0313, 0), ItemDisplay.class, d -> {
            d.setItemStack(item);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setRotation(yaw, 0);
            // the model is a 1 x 1 block plate; scale it up to 3 x 3 blocks
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(3f, 1f, 3f), new AxisAngle4f()));
            d.setPersistent(false);
        });
        m.core = w.spawn(top.clone(), Interaction.class, i -> {
            i.setInteractionWidth(0.45f);
            i.setInteractionHeight(0.12f);
            i.setResponsive(true);
            i.setPersistent(false);
        });
        byEntity.put(m.display.getUniqueId(), m);
        byEntity.put(m.core.getUniqueId(), m);
        slotOf.put(m.core.getUniqueId(), new int[]{-1, -1});
        for (int side = 0; side < 2; side++) {
            for (int col = 0; col < 3; col++) {
                Interaction hit = w.spawn(m.slotTop(side, col), Interaction.class, i -> {
                    i.setInteractionWidth(0.72f);
                    i.setInteractionHeight(0.12f);
                    i.setResponsive(true);
                    i.setPersistent(false);
                });
                m.slotHits[side][col] = hit;
                byEntity.put(hit.getUniqueId(), m);
                slotOf.put(hit.getUniqueId(), new int[]{side, col});
            }
        }
        mats.add(m);
        say(p, "Mat placed. Hold a card and right-click a slot on your side to join. Sneak + right-click the middle to pick the mat up.", NamedTextColor.GREEN);
        return m;
    }

    /** Removes a mat. Cards on it go back to their owners. */
    public void removeMat(Mat m, Player giveItemTo) {
        resetMat(m);
        for (Entity e : new Entity[]{m.display, m.core}) if (e != null) { byEntity.remove(e.getUniqueId()); slotOf.remove(e.getUniqueId()); e.remove(); }
        for (var row : m.slotHits) for (var h : row) if (h != null) { byEntity.remove(h.getUniqueId()); slotOf.remove(h.getUniqueId()); h.remove(); }
        mats.remove(m);
        if (giveItemTo != null) {
            var left = giveItemTo.getInventory().addItem(plugin.items().mat(m.variant));
            left.values().forEach(i -> giveItemTo.getWorld().dropItemNaturally(giveItemTo.getLocation(), i));
        }
    }

    public Mat nearestMat(Location l) {
        Mat best = null; double bd = Double.MAX_VALUE;
        for (Mat m : mats) {
            if (!m.center.getWorld().equals(l.getWorld())) continue;
            double d = m.center.distanceSquared(l);
            if (d < bd) { bd = d; best = m; }
        }
        return best;
    }

    public void pickUpMat(Player p, Mat m) {
        if (m.state != Mat.State.WAITING || m.hasCards(0) || m.hasCards(1)) {
            say(p, "You can only pick the mat up when it is empty.", NamedTextColor.RED);
            return;
        }
        removeMat(m, p);
    }

    public void clearAll() { new ArrayList<>(mats).forEach(m -> removeMat(m, null)); }

    // ---------------------------------------------------------------- joining

    public void slotClick(Player p, Mat m, int side, int col) {
        if (m.state != Mat.State.WAITING) { say(p, "A battle is in progress on this mat.", NamedTextColor.RED); return; }
        ItemStack hand = p.getInventory().getItemInMainHand();
        ItemType itemType = plugin.items().itemType(hand);
        if (itemType != null) { bringItem(p, m, side, hand, itemType); return; }
        String id = plugin.items().cardId(hand);
        Card card = id == null ? null : plugin.registry().get(id);
        if (card == null) { say(p, "Hold a card and right-click a slot to play it.", NamedTextColor.YELLOW); return; }
        UUID me = p.getUniqueId();
        Mat other = byPlayer.get(me);
        if (other != null && other != m) { say(p, "You are already playing on another mat.", NamedTextColor.RED); return; }
        if (m.players[side] != null && !m.players[side].equals(me)) { say(p, nameOf(m.players[side]) + " is playing on this side.", NamedTextColor.RED); return; }
        if (m.players[1 - side] != null && m.players[1 - side].equals(me)) { say(p, "You are already playing on the other side.", NamedTextColor.RED); return; }
        if (m.cards[side][col] != null) { say(p, "That slot is taken. Sneak + right-click a card to pick it up.", NamedTextColor.RED); return; }
        int cost = Rules.cost(card.rarity()), cap = plugin.rules().teamCostCap();
        for (var c : m.cards[side]) if (c != null) {
            if (c.card.id().equals(card.id())) { say(p, "Your team already has " + card.name() + ". No duplicate cards.", NamedTextColor.RED); return; }
            cost += Rules.cost(c.card.rarity());
        }
        if (cost > cap) {
            say(p, "Team cost would be " + cost + "/" + cap + ". " + card.rarity().label + " cards cost " + Rules.cost(card.rarity())
                    + " (Common 1 ... Mythic 6). Swap in cheaper cards.", NamedTextColor.RED);
            return;
        }

        boolean claimed = m.players[side] == null;
        DisplayManager.Placed pl = displays().place(p, card, m.slotTop(side, col), m.sideYaw(side), true);
        if (pl == null) return;
        if (claimed) { m.players[side] = me; byPlayer.put(me, m); }
        pl.mat = m; pl.side = side; pl.col = col;
        m.cards[side][col] = pl;
        if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);

        if (m.full(0) && m.full(1)) { countdown(m); return; }
        if (m.full(side)) {
            say(p, "Your team is ready. Waiting for an opponent to fill the other side.", NamedTextColor.GREEN);
            if (m.players[1 - side] != null) say(Bukkit.getPlayer(m.players[1 - side]), nameOf(me) + "'s team is ready.", NamedTextColor.GRAY);
        } else {
            int have = 0, spent = 0; for (var c : m.cards[side]) if (c != null) { have++; spent += Rules.cost(c.card.rarity()); }
            say(p, have + " of 3 cards placed. Team cost " + spent + "/" + cap + ".", NamedTextColor.GRAY);
        }
    }

    public void pickUpCard(Player p, DisplayManager.Placed pl) {
        Mat m = pl.mat;
        if (m == null || m.state != Mat.State.WAITING || !pl.owner.equals(p.getUniqueId())) return;
        displays().remove(pl);
        m.cards[pl.side][pl.col] = null;
        giveBack(pl.owner, pl.card);
        if (m.countdown) { m.countdown = false; tell(m, Component.text("A card was picked up, so the countdown stopped.", NamedTextColor.GRAY)); }
        if (!m.hasCards(pl.side)) releaseSide(m, pl.side);
    }

    private void releaseSide(Mat m, int side) {
        UUID id = m.players[side];
        if (id != null) byPlayer.remove(id);
        returnItems(m, side);
        m.players[side] = null;
    }

    // ---------------------------------------------------------------- item cards

    /** Before a battle: right-click your side with an item card to bring it (max 2). */
    private void bringItem(Player p, Mat m, int side, ItemStack hand, ItemType type) {
        UUID me = p.getUniqueId();
        if (m.players[side] == null || !m.players[side].equals(me)) { say(p, "Place a card on this side first, then add item cards.", NamedTextColor.YELLOW); return; }
        int max = plugin.getConfig().getInt("rules.items-per-battle", 2);
        if (m.brought[side].size() >= max) { say(p, "You can bring at most " + max + " item cards to a battle.", NamedTextColor.RED); return; }
        m.hand[side].add(type);
        m.brought[side].add(type);
        if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
        p.playSound(p.getLocation(), Sound.ITEM_BUNDLE_INSERT, 1f, 1.2f);
        say(p, "Added " + type.label + " to your items (" + m.brought[side].size() + "/" + max + "). You get it back after the battle.", NamedTextColor.GREEN);
    }

    private void returnItems(Mat m, int side) {
        UUID owner = m.players[side];
        for (Mat.Trap t : m.traps[side]) if (t.display != null) t.display.remove();
        m.traps[side].clear();
        if (owner != null) for (ItemType t : m.brought[side]) giveBack(owner, t);
        m.brought[side].clear();
        m.hand[side].clear();
    }

    private void giveBack(UUID owner, ItemType t) {
        Player p = Bukkit.getPlayer(owner);
        if (p != null) Items.give(p, plugin.items().itemCard(t));
        else plugin.binders().add(owner, "item:" + t.name(), 1);
    }

    /** Uses an item from the turn menu. target is your own card for heals, otherwise null. */
    public void useItem(Player p, Mat m, int index, DisplayManager.Placed target) {
        int side = m.sideOf(p.getUniqueId());
        if (m.state != Mat.State.FIGHTING || m.busy || side != m.turn) return;
        if (index < 0 || index >= m.hand[side].size()) return;
        if (m.itemUsedThisTurn) { say(p, "You already used an item this turn.", NamedTextColor.YELLOW); return; }
        ItemType t = m.hand[side].get(index);
        if (m.energy[side] < t.cost) { say(p, t.label + " costs " + t.cost + " energy; you have " + m.energy[side] + ".", NamedTextColor.YELLOW); return; }
        if (t.kind == ItemType.Kind.TRAP && m.traps[side].size() >= 2) { say(p, "You already have 2 traps set.", NamedTextColor.YELLOW); return; }
        World w = m.center.getWorld();
        if (t.heals()) {
            if (target == null || target.ko || target.side != side) { say(p, "Choose one of your cards to heal.", NamedTextColor.YELLOW); return; }
            int before = target.hp;
            target.hp = Math.min(target.maxHp, target.hp + t.amount);
            displays().refreshHp(target);
            displays().popup(target.base.clone().add(0, 1.05, 0), Component.text("+" + (target.hp - before), NamedTextColor.GREEN).decorate(TextDecoration.BOLD), 0.9f);
            w.spawnParticle(Particle.HEART, target.base.clone().add(0, 0.7, 0), 4, 0.15, 0.15, 0.15, 0);
            w.spawnParticle(Particle.HAPPY_VILLAGER, target.base.clone().add(0, 0.5, 0), 10, 0.2, 0.25, 0.2, 0);
            w.playSound(target.base, Sound.ENTITY_GENERIC_DRINK, 0.8f, 1.2f);
            tell(m, Component.text(p.getName() + " used ", NamedTextColor.WHITE).append(Items.t(t.label, t.rarity.color()))
                    .append(Component.text(" on " + target.card.name() + " (+" + (target.hp - before) + " Health).", NamedTextColor.WHITE)));
        } else if (t == ItemType.ENERGY_DRINK) {
            w.playSound(m.center, Sound.ENTITY_GENERIC_DRINK, 0.8f, 1.5f);
            tell(m, Component.text(p.getName() + " used ", NamedTextColor.WHITE).append(Items.t(t.label, t.rarity.color()))
                    .append(Component.text(" (+" + t.amount + " energy).", NamedTextColor.WHITE)));
        } else {
            Mat.Trap trap = new Mat.Trap(t);
            Location spot = m.trapSpot(side, m.traps[side].size());
            trap.display = w.spawn(spot.clone().add(0, 0.075, 0), org.bukkit.entity.ItemDisplay.class, d -> {
                d.setItemStack(plugin.items().cardBack());
                d.setItemDisplayTransform(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.FIXED);
                d.setRotation(m.sideYaw(side) + 90f, 0);
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f((float) Math.toRadians(90), 1, 0, 0),
                        new Vector3f(0.55f, 0.55f, 0.55f), new AxisAngle4f()));
                d.setPersistent(false);
            });
            m.traps[side].add(trap);
            w.playSound(spot, Sound.BLOCK_TRIPWIRE_ATTACH, 1f, 0.8f);
            tell(m, Component.text(p.getName() + " set a trap card face down.", NamedTextColor.LIGHT_PURPLE));
        }
        m.energy[side] = Math.min(plugin.rules().energyMax(), m.energy[side] - t.cost + (t == ItemType.ENERGY_DRINK ? t.amount : 0));
        m.hand[side].remove(index);
        m.itemUsedThisTurn = true;
    }

    /** Picks the first of the defender's traps that applies to this hit, removes it and returns it (or null). */
    private Mat.Trap springTrap(Mat m, int side, int dmg, DisplayManager.Placed tgt) {
        for (Mat.Trap t : m.traps[side]) {
            if (t.type.savesFromKo() && dmg < tgt.hp) continue;   // waits for a knockout blow
            m.traps[side].remove(t);
            return t;
        }
        return null;
    }

    private void revealTrap(Mat m, Mat.Trap t) {
        if (t.display == null || !t.display.isValid()) return;
        var d = t.display;
        d.setItemStack(plugin.items().itemCard(t.type));
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(6);
        d.setTransformation(new Transformation(new Vector3f(0, 0.35f, 0), new AxisAngle4f((float) Math.toRadians(90), 1, 0, 0),
                new Vector3f(0.7f, 0.7f, 0.7f), new AxisAngle4f()));
        World w = d.getWorld();
        Location l = d.getLocation().add(0, 0.4, 0);
        w.spawnParticle(Particle.DUST, l, 20, 0.2, 0.15, 0.2, 0, new Particle.DustOptions(org.bukkit.Color.fromRGB(t.type.rarity.color.value()), 1f));
        w.spawnParticle(Particle.ENCHANTED_HIT, l, 15, 0.2, 0.2, 0.2, 0.2);
        w.playSound(l, Sound.BLOCK_PISTON_EXTEND, 0.8f, 1.6f);
        w.playSound(l, Sound.ENTITY_EVOKER_CAST_SPELL, 0.6f, 1.4f);
        Bukkit.getScheduler().runTaskLater(plugin, d::remove, 40L);
    }

    private void giveBack(UUID owner, Card c) {
        Player p = Bukkit.getPlayer(owner);
        if (p != null) {
            var left = p.getInventory().addItem(plugin.items().card(c));
            left.values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
        } else {
            plugin.binders().add(owner, c.id(), 1);
        }
    }

    private void clearCards(Mat m) {
        for (int s = 0; s < 2; s++) for (int c = 0; c < 3; c++) {
            DisplayManager.Placed pl = m.cards[s][c];
            if (pl == null) continue;
            displays().remove(pl);
            giveBack(pl.owner, pl.card);
            m.cards[s][c] = null;
        }
    }

    /** Puts the mat back to its empty state. Cards go back to their owners. */
    private void resetMat(Mat m) {
        for (int s = 0; s < 2; s++) {
            UUID id = m.players[s];
            if (id == null) continue;
            byPlayer.remove(id);
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.getOpenInventory().getTopInventory().getHolder() instanceof BattleMenu) p.closeInventory();
        }
        clearCards(m);
        returnItems(m, 0); returnItems(m, 1);
        m.players[0] = null; m.players[1] = null;
        m.state = Mat.State.WAITING;
        m.countdown = false;
        m.turn = -1;
        m.busy = false;
    }

    // ---------------------------------------------------------------- battle flow

    private int dexterity(Mat m, int side) {
        int sum = 0;
        for (var c : m.cards[side]) if (c != null) sum += c.card.stat(Stat.DEXTERITY);
        return sum;
    }

    /** Both teams are full: a short countdown so players can still add item cards, then the battle starts. */
    private void countdown(Mat m) {
        if (m.countdown) return;
        m.countdown = true;
        final int id = ++m.countdownId;
        long ticks = plugin.getConfig().getLong("start-countdown-ticks", 100);
        tell(m, Component.text("Both teams are ready! The battle starts in " + (ticks / 20) + " seconds. Right-click one of your cards with an item card to bring it.", NamedTextColor.GOLD));
        for (long t = ticks - 60; t < ticks; t += 20) {
            final long left = (ticks - t) / 20;
            if (t < 0) continue;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!m.countdown || m.countdownId != id || !mats.contains(m)) return;
                for (UUID u : m.players) { Player p = u == null ? null : Bukkit.getPlayer(u); if (p != null) {
                    p.sendActionBar(Component.text("Battle starts in " + left + "...", NamedTextColor.GOLD));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.2f); } }
            }, t);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!m.countdown || m.countdownId != id || !mats.contains(m)) return;
            m.countdown = false;
            if (m.state == Mat.State.WAITING && m.full(0) && m.full(1)) start(m);
        }, ticks);
    }

    private void start(Mat m) {
        m.state = Mat.State.FIGHTING;
        m.energy[0] = 0; m.energy[1] = 0;
        for (var row : m.cards) for (var c : row) if (c != null) { c.acted = false; c.stunned = false; }
        for (int s = 0; s < 2; s++) m.traps[s].clear();
        int d0 = dexterity(m, 0), d1 = dexterity(m, 1);
        int first;
        boolean coin = false;
        if (d0 > d1) first = 0;
        else if (d1 > d0) first = 1;
        else { first = rng.nextBoolean() ? 0 : 1; coin = true; }
        m.turn = first;
        m.energy[1 - first] = plugin.rules().secondPlayerBonus();   // going second starts with a little extra energy
        String who = nameOf(m.players[first]);
        Component line = coin
                ? Component.text("Dexterity is tied at " + d0 + ". Coin flip: " + who + " goes first.", NamedTextColor.GOLD)
                : Component.text(who + " goes first (Dexterity " + (first == 0 ? d0 + " vs " + d1 : d1 + " vs " + d0) + ").", NamedTextColor.GOLD);
        tell(m, line);
        Title t = Title.title(Component.text("Battle!", NamedTextColor.RED).decorate(TextDecoration.BOLD), Component.text(who + " goes first", NamedTextColor.GOLD),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(400)));
        for (UUID id : m.players) { Player p = id == null ? null : Bukkit.getPlayer(id); if (p != null) p.showTitle(t); }
        // flip the cards one at a time, alternating sides, then the first turn begins
        m.busy = true;
        long delay = 25L, extra = 0;
        for (int col = 0; col < 3; col++) for (int s = 0; s < 2; s++) {
            var c = m.cards[s][col];
            if (c == null) continue;
            final var card = c;
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (m.state == Mat.State.FIGHTING && mats.contains(m)) displays().reveal(card); }, delay);
            if (c.card.rarity().ordinal() >= Rarity.EPIC.ordinal()) extra = Math.max(extra, 30L);
            delay += 10L;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (m.state == Mat.State.FIGHTING && mats.contains(m)) beginTurn(m); }, delay + 20L + extra);
    }

    private void beginTurn(Mat m) {
        m.busy = false;
        final int side = m.turn;
        Rules rules = plugin.rules();
        m.energy[side] = Math.min(rules.energyMax(), m.energy[side] + rules.energyPerTurn());
        m.itemUsedThisTurn = false;
        boolean anyReady = false;
        for (var c : m.cards[side]) if (c != null && !c.ko && !c.acted) anyReady = true;
        if (!anyReady) {
            for (var c : m.cards[side]) if (c != null) { c.acted = c.stunned; c.stunned = false; }   // stunned cards sit this round out
            Player owner = Bukkit.getPlayer(m.players[side]);
            if (owner != null) say(owner, "New round: all your cards can act again.", NamedTextColor.AQUA);
        }
        Player cur = Bukkit.getPlayer(m.players[side]);
        Player other = m.players[1 - side] == null ? null : Bukkit.getPlayer(m.players[1 - side]);
        if (cur != null) {
            say(cur, "Your turn! Choose a card, a move and a target. (Right-click your card or use /sneakycards turn to open the menu.)", NamedTextColor.GREEN);
            cur.showTitle(Title.title(Component.empty(), Component.text("Your turn  ⚡ " + m.energy[side] + "/" + rules.energyMax(), NamedTextColor.GREEN),
                    Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1200), Duration.ofMillis(300))));
            cur.playSound(cur.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.4f);
            long delay = plugin.getConfig().getLong("turn-menu-delay-ticks", 40);
            if (delay >= 0) Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (m.state != Mat.State.FIGHTING || m.turn != side || m.busy || !cur.isOnline()) return;
                if (cur.getOpenInventory().getTopInventory().getHolder() instanceof BattleMenu) return; // already opened it
                new BattleMenu(plugin, cur, m).open();
            }, delay);
        }
        if (other != null) other.sendActionBar(Component.text(nameOf(m.players[side]) + "'s turn", NamedTextColor.GRAY));
    }

    public void openTurnMenu(Player p) {
        Mat m = byPlayer.get(p.getUniqueId());
        if (m == null || m.state != Mat.State.FIGHTING) { say(p, "You are not in a battle.", NamedTextColor.RED); return; }
        if (m.sideOf(p.getUniqueId()) != m.turn) { say(p, "It is not your turn.", NamedTextColor.YELLOW); return; }
        if (m.busy) { say(p, "Wait for the move to finish.", NamedTextColor.YELLOW); return; }
        new BattleMenu(plugin, p, m).open();
    }

    /** Called by the turn menu once a card, a move and a target are chosen. */
    public void attack(Player p, Mat m, DisplayManager.Placed att, Move mv, DisplayManager.Placed tgt) {
        if (m.state != Mat.State.FIGHTING || m.busy || m.sideOf(p.getUniqueId()) != m.turn) return;
        if (att.ko || tgt.ko || att.side != m.turn || tgt.side == m.turn) return;
        if (att.acted) { say(p, att.card.name() + " already acted this round. Use your other cards first.", NamedTextColor.YELLOW); return; }
        int cost = Rules.moveCost(att.card, mv);
        if (m.energy[m.turn] < cost) { say(p, "Not enough energy: " + mv.name() + " costs " + cost + ", you have " + m.energy[m.turn] + ".", NamedTextColor.YELLOW); return; }
        m.busy = true;
        m.energy[m.turn] = Math.min(plugin.rules().energyMax(), m.energy[m.turn] - cost + Rules.refund(mv));
        att.acted = true;
        boolean crit = rng.nextInt(100) < Math.min(100, att.card.stat(Stat.LUCK));
        int raw = plugin.rules().damage(att.card, mv, tgt.card, crit);
        Mat.Trap trap = springTrap(m, tgt.side, raw, tgt);
        int dmg = trap != null && trap.type == ItemType.DECOY ? Math.max(1, raw / 2) : raw;
        displays().attack(att, tgt, mv, () -> impact(m, att, tgt, mv, dmg, crit, trap), () -> afterAttack(m));
    }

    private void impact(Mat m, DisplayManager.Placed att, DisplayManager.Placed tgt, Move mv, int dmg, boolean crit, Mat.Trap trap) {
        if (m.state != Mat.State.FIGHTING) return;
        tgt.hp = Math.max(0, tgt.hp - dmg);
        Component trapMsg = null;
        if (trap != null) {
            revealTrap(m, trap);
            ItemType t = trap.type;
            Component head = Component.text("Trap! ", NamedTextColor.LIGHT_PURPLE).append(Items.t(t.label, t.rarity.color())).append(Component.text(": ", NamedTextColor.LIGHT_PURPLE));
            switch (t) {
                case DECOY -> trapMsg = head.append(Component.text("the hit was halved.", NamedTextColor.WHITE));
                case LAST_STAND, GUARDIAN_ANGEL -> {
                    tgt.hp = t == ItemType.LAST_STAND ? 1 : Math.max(1, tgt.maxHp / 2);
                    m.center.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, tgt.base.clone().add(0, 0.6, 0), 30, 0.2, 0.3, 0.2, 0.3);
                    m.center.getWorld().playSound(tgt.base, Sound.ITEM_TOTEM_USE, 0.7f, 1.3f);
                    trapMsg = head.append(Component.text(tgt.card.name() + " survives with " + tgt.hp + " Health!", NamedTextColor.WHITE));
                }
                case SPIKE_TRAP, MIRROR_WARD -> {
                    int back = t == ItemType.SPIKE_TRAP ? t.amount : Math.max(1, dmg / 2);
                    att.hp = Math.max(0, att.hp - back);
                    displays().refreshHp(att);
                    // the attacker is still mid-move: let its animation finish before it reacts
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (m.state != Mat.State.FIGHTING || !mats.contains(m) || att.ko) return;
                        displays().hurt(att, tgt, false);
                        displays().popup(att.base.clone().add(0, 1.05, 0), Component.text("-" + back, NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD), 0.9f);
                    }, 14L);
                    trapMsg = head.append(Component.text(att.card.name() + " takes " + back + " damage.", NamedTextColor.WHITE));
                    if (att.hp <= 0) Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (m.state != Mat.State.FIGHTING || !mats.contains(m)) return;
                        displays().knockOut(att);
                        tell(m, Component.text(att.card.name() + " is knocked out!", NamedTextColor.RED));
                    }, 30L);
                }
                case SNARE -> {
                    att.stunned = true;
                    m.center.getWorld().spawnParticle(Particle.ITEM_SLIME, att.base.clone().add(0, 0.5, 0), 15, 0.15, 0.2, 0.15, 0);
                    trapMsg = head.append(Component.text(att.card.name() + " is stunned and will skip its next action.", NamedTextColor.WHITE));
                }
                default -> { }
            }
        }
        displays().refreshHp(tgt);
        displays().hurt(tgt, att, crit);
        Component num = crit
                ? Component.text("CRIT -" + dmg, NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
                : Component.text("-" + dmg, NamedTextColor.RED).decorate(TextDecoration.BOLD);
        displays().popup(tgt.base.clone().add(0, 1.05, 0), num, crit ? 1.3f : 0.9f);
        Component msg = Component.text(att.card.name(), att.card.rarity().color())
                .append(Component.text(" used " + mv.name() + " on ", NamedTextColor.WHITE))
                .append(Component.text(tgt.card.name(), tgt.card.rarity().color()))
                .append(Component.text(" for " + dmg + " damage" + (crit ? " (critical hit!)" : "") + ".", crit ? NamedTextColor.GOLD : NamedTextColor.WHITE));
        double mult = plugin.rules().typeMultiplier(mv, tgt.card);
        if (mult > 1) msg = msg.append(Component.text(" Super effective!", NamedTextColor.GREEN));
        else if (mult < 1) msg = msg.append(Component.text(" Not very effective.", NamedTextColor.GRAY));
        tell(m, msg);
        if (trapMsg != null) tell(m, trapMsg);
        if (tgt.hp <= 0) {
            // let the hit animation play before the card goes down
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (m.state != Mat.State.FIGHTING || !mats.contains(m)) return;
                displays().knockOut(tgt);
                tell(m, Component.text(tgt.card.name() + " is knocked out!", NamedTextColor.RED));
            }, crit ? 20L : 14L);
        }
    }

    /** Waits a moment after the move so players can see what happened, then checks for a winner. */
    private void afterAttack(Mat m) {
        long pause = plugin.getConfig().getLong("pause-after-attack-ticks", 30);
        Bukkit.getScheduler().runTaskLater(plugin, () -> nextTurn(m), Math.max(30L, pause));
    }

    private void nextTurn(Mat m) {
        if (m.state != Mat.State.FIGHTING || !mats.contains(m)) return;
        if (m.allKnockedOut(0)) { end(m, 1); return; }
        if (m.allKnockedOut(1)) { end(m, 0); return; }
        m.turn = 1 - m.turn;
        beginTurn(m);
    }

    private void end(Mat m, int winner) {
        m.state = Mat.State.ENDED;
        m.busy = true;
        UUID win = m.players[winner], lose = m.players[1 - winner];
        Player wp = win == null ? null : Bukkit.getPlayer(win), lp = lose == null ? null : Bukkit.getPlayer(lose);
        Duration d = Duration.ofSeconds(3);
        if (wp != null) {
            wp.showTitle(Title.title(Component.text("Victory!", NamedTextColor.GOLD).decorate(TextDecoration.BOLD), Component.text("You win the battle", NamedTextColor.YELLOW),
                    Title.Times.times(Duration.ofMillis(300), d, Duration.ofMillis(600))));
            wp.playSound(wp.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        if (lp != null) {
            lp.showTitle(Title.title(Component.text("Defeat", NamedTextColor.RED).decorate(TextDecoration.BOLD), Component.text(nameOf(win) + " wins", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(300), d, Duration.ofMillis(600))));
        }
        tell(m, Component.text(nameOf(win) + " wins the battle! Cards return to their owners shortly.", NamedTextColor.GOLD));
        for (var c : m.cards[winner]) if (c != null && !c.ko) {
            m.center.getWorld().spawnParticle(Particle.FIREWORK, c.base.clone().add(0, 0.6, 0), 30, 0.2, 0.3, 0.2, 0.1);
            m.center.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, c.base.clone().add(0, 0.6, 0), 20, 0.2, 0.3, 0.2, 0.3);
        }
        for (UUID id : m.players) {
            Player p = id == null ? null : Bukkit.getPlayer(id);
            if (p != null && p.getOpenInventory().getTopInventory().getHolder() instanceof BattleMenu) p.closeInventory();
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (mats.contains(m)) resetMat(m); }, 120L);
    }

    /** Skip the turn to bank extra energy. */
    public void rest(Player p) {
        Mat m = byPlayer.get(p.getUniqueId());
        if (m == null || m.state != Mat.State.FIGHTING || m.busy || m.sideOf(p.getUniqueId()) != m.turn) return;
        m.busy = true;
        m.energy[m.turn] = Math.min(plugin.rules().energyMax(), m.energy[m.turn] + plugin.rules().restBonus());
        tell(m, Component.text(p.getName() + " rests and gains " + plugin.rules().restBonus() + " extra energy.", NamedTextColor.GRAY));
        afterAttack(m);
    }

    public void forfeit(Player p) {
        Mat m = byPlayer.get(p.getUniqueId());
        if (m == null) { say(p, "You are not on a mat.", NamedTextColor.RED); return; }
        leave(p.getUniqueId());
    }

    /** Called when a player forfeits, leaves a mat or logs out. */
    public void leave(UUID id) {
        Mat m = byPlayer.get(id);
        if (m == null) return;
        int side = m.sideOf(id);
        if (side < 0) { byPlayer.remove(id); return; }
        switch (m.state) {
            case FIGHTING -> {
                tell(m, Component.text(nameOf(id) + " forfeits.", NamedTextColor.GRAY));
                end(m, 1 - side);
            }
            case WAITING -> {
                for (int c = 0; c < 3; c++) {
                    DisplayManager.Placed pl = m.cards[side][c];
                    if (pl == null) continue;
                    displays().remove(pl);
                    giveBack(pl.owner, pl.card);
                    m.cards[side][c] = null;
                }
                releaseSide(m, side);
            }
            default -> { }
        }
    }
}
