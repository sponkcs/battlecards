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
 * Game mats and deck battles.
 *
 *  1. Build a deck in the binder (15 cards). Hold your binder and right-click your side of a mat to place the deck
 *     there. When both decks are down (or you start a solo battle) both decks are shuffled.
 *  2. Each player draws 5 (always at least 2 characters) and puts up to 3 characters face down. They flip over.
 *     Higher total Dexterity goes first; the other side starts with +1 energy.
 *  3. Each turn: draw 1 (nothing if your deck is empty), gain 2 energy, then play up to
 *     1 character and 1 item or trap, and attack once (attacking ends your turn). Each card acts once per round.
 *  4. A knocked-out card goes to its owner's discard pile and scores 1 point. First to 3 points wins; a player
 *     with no characters left anywhere loses. Cards never leave anyone's binder.
 */
public final class BattleManager {
    private final SneakyCardsPlugin plugin;
    private final List<Mat> mats = new ArrayList<>();
    private final Map<UUID, Mat> byEntity = new HashMap<>();
    private final Map<UUID, int[]> slotOf = new HashMap<>();
    private final Map<UUID, Mat> byPlayer = new HashMap<>();
    private final Random rng = new Random();

    BattleManager(SneakyCardsPlugin plugin) { this.plugin = plugin; }

    SneakyCardsPlugin plugin() { return plugin; }
    private DisplayManager displays() { return plugin.displays(); }
    private Rules rules() { return plugin.rules(); }
    private int pointsToWin() { return plugin.getConfig().getInt("rules.points-to-win", 3); }
    private int handLimit() { return plugin.getConfig().getInt("rules.hand-limit", 7); }

    static void say(Player p, String text, NamedTextColor c) { if (p != null) p.sendMessage(Component.text(text, c)); }

    static String nameOf(UUID id) {
        if (id == null) return "nobody";
        if (Mat.AI.equals(id)) return "Rival";
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? "a player" : n;
    }

    private Player player(Mat m, int side) { UUID id = m.players[side]; return id == null || Mat.AI.equals(id) ? null : Bukkit.getPlayer(id); }

    void tell(Mat m, Component msg) { for (int s = 0; s < 2; s++) { Player p = player(m, s); if (p != null) p.sendMessage(msg); } }

    private boolean live(Mat m) { return mats.contains(m) && (m.state == Mat.State.FIGHTING || m.state == Mat.State.SETUP); }

    // ---------------------------------------------------------------- mats

    public Mat matOf(Entity e) { return byEntity.get(e.getUniqueId()); }

    /** {side, slot} for a slot hitbox (slot 3 = deck slot), {-1, -1} for the centre of the mat, null otherwise. */
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
            i.setInteractionWidth(0.4f);
            i.setInteractionHeight(0.12f);
            i.setResponsive(true);
            i.setPersistent(false);
        });
        byEntity.put(m.display.getUniqueId(), m);
        byEntity.put(m.core.getUniqueId(), m);
        slotOf.put(m.core.getUniqueId(), new int[]{-1, -1});
        for (int side = 0; side < 2; side++) {
            for (int col = 0; col < 4; col++) {
                Location at = col < 3 ? m.slotTop(side, col) : m.deckTop(side);
                final float width = col < 3 ? 0.54f : 0.42f;
                Interaction hit = w.spawn(at, Interaction.class, i -> {
                    i.setInteractionWidth(width);
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
        say(p, "Mat placed. Right-click your side of the mat to place your deck (or /sc solo to play the computer). Sneak + right-click the middle to pick the mat up.", NamedTextColor.GREEN);
        return m;
    }

    /** Removes a mat and everything on it. */
    public void removeMat(Mat m, Player giveItemTo) {
        resetMat(m);
        for (Entity e : new Entity[]{m.display, m.core}) if (e != null) { byEntity.remove(e.getUniqueId()); slotOf.remove(e.getUniqueId()); e.remove(); }
        for (var row : m.slotHits) for (var h : row) if (h != null) { byEntity.remove(h.getUniqueId()); slotOf.remove(h.getUniqueId()); h.remove(); }
        mats.remove(m);
        if (giveItemTo != null) Items.give(giveItemTo, plugin.items().mat(m.variant));
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
        if (m.state != Mat.State.WAITING || m.players[0] != null || m.players[1] != null) {
            say(p, "You can only pick the mat up when nobody is using it.", NamedTextColor.RED);
            return;
        }
        removeMat(m, p);
    }

    public void clearAll() { new ArrayList<>(mats).forEach(m -> removeMat(m, null)); }

    // ---------------------------------------------------------------- placing decks

    /** Right-click on a slot or the deck slot of a mat. */
    public void slotClick(Player p, Mat m, int side, int slot) {
        UUID me = p.getUniqueId();
        if (m.state == Mat.State.WAITING) {
            if (p.isSneaking() && me.equals(m.players[side])) { takeDeckBack(m, side); return; }
            placeDeck(p, m, side);
            return;
        }
        if (m.sideOf(me) >= 0) openMenu(p);
        else say(p, "A battle is in progress on this mat.", NamedTextColor.GRAY);
    }

    /** Right-click on a card that lies on a mat. */
    public void cardClick(Player p, DisplayManager.Placed pl) {
        if (pl.mat == null) return;
        if (pl.mat.sideOf(p.getUniqueId()) >= 0 && pl.mat.state != Mat.State.WAITING) openMenu(p);
    }

    private boolean placeDeck(Player p, Mat m, int side) {
        UUID me = p.getUniqueId();
        Mat other = byPlayer.get(me);
        if (other != null && other != m) { say(p, "You are already playing on another mat.", NamedTextColor.RED); return false; }
        if (m.players[side] != null && !m.players[side].equals(me)) { say(p, nameOf(m.players[side]) + " has placed a deck on this side.", NamedTextColor.RED); return false; }
        if (me.equals(m.players[1 - side])) { say(p, "Your deck is already on the other side.", NamedTextColor.RED); return false; }
        if (m.deckPlaced[side]) { say(p, "Your deck is on the mat. Sneak + right-click to take it back.", NamedTextColor.GRAY); return false; }
        List<String> keys = plugin.decks().get(me);
        List<String> problems = plugin.decks().problems(me, keys);
        if (!problems.isEmpty()) {
            say(p, "Your deck isn't ready: " + problems.get(0) + ". Open your binder (/binder) and click Deck builder.", NamedTextColor.RED);
            return false;
        }
        m.players[side] = me;
        byPlayer.put(me, m);
        loadDeck(m, side, keys);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PUT, 1f, 0.9f);
        if (m.deckPlaced[1 - side]) countdown(m);
        else say(p, "Deck placed. Waiting for an opponent to place theirs on the other side (or use /sc solo).", NamedTextColor.GREEN);
        return true;
    }

    private void loadDeck(Mat m, int side, List<String> keys) {
        m.deck[side].clear();
        for (String k : keys) { HandCard h = HandCard.parse(k, plugin.registry()); if (h != null) m.deck[side].add(h); }
        m.deckPlaced[side] = true;
        refreshDeck(m, side);
    }

    private void takeDeckBack(Mat m, int side) {
        Player p = player(m, side);
        releaseSide(m, side);
        if (m.countdown) { m.countdown = false; tell(m, Component.text("A deck was taken back, so the countdown stopped.", NamedTextColor.GRAY)); }
        say(p, "You took your deck back.", NamedTextColor.GRAY);
    }

    private void releaseSide(Mat m, int side) {
        UUID id = m.players[side];
        if (id != null) byPlayer.remove(id);
        m.players[side] = null;
        m.deckPlaced[side] = false;
        m.deck[side].clear();
        refreshDeck(m, side);
    }

    /** Practice against the computer on the nearest free mat. */
    public void solo(Player p) {
        Mat m = byPlayer.get(p.getUniqueId());
        if (m == null) {
            m = nearestMat(p.getLocation());
            if (m == null || m.center.distanceSquared(p.getLocation()) > 64) { say(p, "Stand next to a game mat to start a solo battle.", NamedTextColor.RED); return; }
        }
        if (m.state != Mat.State.WAITING) { say(p, "That mat is busy.", NamedTextColor.RED); return; }
        int side = m.sideOf(p.getUniqueId());
        if (side < 0) {
            side = m.players[0] == null ? 0 : m.players[1] == null ? 1 : -1;
            if (side < 0) { say(p, "Both sides of that mat are taken.", NamedTextColor.RED); return; }
            if (!placeDeck(p, m, side)) return;
        }
        int ai = 1 - side;
        if (m.players[ai] != null) { say(p, "Someone else is on the other side.", NamedTextColor.RED); return; }
        m.players[ai] = Mat.AI;
        loadDeck(m, ai, Ai.deck(plugin));
        say(p, "Your rival shuffles up a deck.", NamedTextColor.LIGHT_PURPLE);
        countdown(m);
    }

    // ---------------------------------------------------------------- deck and discard displays

    private void refreshDeck(Mat m, int side) {
        int n = m.deck[side].size();
        if (!m.deckPlaced[side] || n == 0) { if (m.deckDisplay[side] != null) { m.deckDisplay[side].remove(); m.deckDisplay[side] = null; } return; }
        float f = Math.max(0.12f, n / 15f), s = 0.66f;
        Transformation t = new Transformation(new Vector3f(0, 0.125f * f * s, 0), new AxisAngle4f((float) Math.toRadians(90), 1, 0, 0),
                new Vector3f(s, s, s * f), new AxisAngle4f());
        if (m.deckDisplay[side] == null || !m.deckDisplay[side].isValid()) {
            m.deckDisplay[side] = m.center.getWorld().spawn(m.deckTop(side).add(0, 0.065, 0), ItemDisplay.class, d -> {
                d.setItemStack(plugin.items().deckStack());
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setRotation(m.sideYaw(side), 0);
                d.setPersistent(false);
            });
        }
        m.deckDisplay[side].setInterpolationDelay(0);
        m.deckDisplay[side].setInterpolationDuration(4);
        m.deckDisplay[side].setTransformation(t);
    }

    private void refreshDiscard(Mat m, int side) {
        List<HandCard> d = m.discard[side];
        if (d.isEmpty()) { if (m.discardDisplay[side] != null) { m.discardDisplay[side].remove(); m.discardDisplay[side] = null; } return; }
        ItemStack top = plugin.items().of(d.get(d.size() - 1));
        if (m.discardDisplay[side] == null || !m.discardDisplay[side].isValid()) {
            m.discardDisplay[side] = m.center.getWorld().spawn(m.discardTop(side).add(0, 0.07, 0), ItemDisplay.class, e -> {
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                e.setRotation(m.sideYaw(side), 0);
                e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f((float) Math.toRadians(90), 1, 0, 0), new Vector3f(0.66f, 0.66f, 0.66f), new AxisAngle4f()));
                e.setPersistent(false);
            });
        }
        m.discardDisplay[side].setItemStack(top);
    }

    private void discard(Mat m, int side, HandCard h) { m.discard[side].add(h); refreshDiscard(m, side); }

    /** Draws up to n cards; cards past the hand limit go straight to the discard pile. */
    private List<HandCard> draw(Mat m, int side, int n, boolean limit) {
        List<HandCard> got = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            HandCard h = m.deck[side].pollFirst();
            if (h == null) break;
            if (limit && m.hand[side].size() >= handLimit()) {
                discard(m, side, h);
                say(player(m, side), "Your hand is full (" + handLimit() + "), so " + h.name() + " was discarded.", NamedTextColor.GRAY);
                continue;
            }
            m.hand[side].add(h);
            got.add(h);
        }
        refreshDeck(m, side);
        return got;
    }

    // ---------------------------------------------------------------- setup

    private void countdown(Mat m) {
        if (m.countdown) return;
        m.countdown = true;
        final int id = ++m.countdownId;
        long ticks = m.solo() ? 40 : plugin.getConfig().getLong("start-countdown-ticks", 100);
        tell(m, Component.text("Both decks are down! Shuffling in " + Math.max(1, ticks / 20) + " seconds.", NamedTextColor.GOLD));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!m.countdown || m.countdownId != id || !mats.contains(m)) return;
            m.countdown = false;
            if (m.state == Mat.State.WAITING && m.deckPlaced[0] && m.deckPlaced[1]) startSetup(m);
        }, ticks);
    }

    private void startSetup(Mat m) {
        m.state = Mat.State.SETUP;
        World w = m.center.getWorld();
        for (int s = 0; s < 2; s++) {
            m.hand[s].clear(); m.discard[s].clear(); m.traps[s].clear();
            m.points[s] = 0; m.energy[s] = 0; m.ready[s] = false;
            List<HandCard> list = new ArrayList<>(m.deck[s]);
            // opening hand of 5 with at least 2 characters (reshuffle and redraw if not)
            for (int tries = 0; tries < 12; tries++) {
                Collections.shuffle(list, rng);
                int chars = 0; for (int i = 0; i < 5 && i < list.size(); i++) if (list.get(i).isChar()) chars++;
                if (chars >= 2) break;
            }
            m.deck[s].clear(); m.deck[s].addAll(list);
            w.playSound(m.deckTop(s), Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.7f);
            draw(m, s, 5, false);
            refreshDiscard(m, s);
        }
        tell(m, Component.text("Decks shuffled. You drew 5 cards: put up to 3 characters face down, then click Ready.", NamedTextColor.GOLD));
        for (int s = 0; s < 2; s++) {
            if (m.isAi(s)) { Ai.setup(this, m, s); continue; }
            Player p = player(m, s);
            if (p != null) Bukkit.getScheduler().runTaskLater(plugin, () -> { if (m.state == Mat.State.SETUP && p.isOnline()) new BattleMenu(plugin, p, m).open(); }, 15L);
        }
    }

    /** Setup: puts a character from your hand face down in an empty slot. */
    public boolean setupPlace(Mat m, int side, int handIdx, int col) {
        if (m.state != Mat.State.SETUP || m.ready[side]) return false;
        if (handIdx < 0 || handIdx >= m.hand[side].size() || col < 0 || col > 2 || m.cards[side][col] != null) return false;
        HandCard h = m.hand[side].get(handIdx);
        if (!h.isChar()) return false;
        DisplayManager.Placed pl = displays().placeFor(m.players[side], h.card, m.slotTop(side, col), m.sideYaw(side), true, h.holo);
        pl.mat = m; pl.side = side; pl.col = col; pl.source = h;
        m.cards[side][col] = pl;
        m.hand[side].remove(handIdx);
        m.center.getWorld().playSound(pl.base, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.1f);
        return true;
    }

    /** Setup: takes a face-down card back into your hand. */
    public void setupTakeBack(Mat m, int side, int col) {
        if (m.state != Mat.State.SETUP || m.ready[side]) return;
        var pl = m.cards[side][col];
        if (pl == null) return;
        displays().remove(pl);
        m.cards[side][col] = null;
        m.hand[side].add(pl.source);
    }

    public void setupReady(Player p, Mat m) {
        int side = m.sideOf(p.getUniqueId());
        if (side < 0 || m.state != Mat.State.SETUP) return;
        if (!m.hasCards(side)) { say(p, "Put at least one character face down first.", NamedTextColor.YELLOW); return; }
        markReady(m, side);
    }

    void markReady(Mat m, int side) {
        m.ready[side] = true;
        tell(m, Component.text(nameOf(m.players[side]) + " is ready.", NamedTextColor.GRAY));
        if (m.ready[0] && m.ready[1]) startFight(m);
    }

    private int dexterity(Mat m, int side) {
        int sum = 0;
        for (var c : m.cards[side]) if (c != null) sum += c.card.stat(Stat.DEXTERITY);
        return sum;
    }

    private void startFight(Mat m) {
        m.state = Mat.State.FIGHTING;
        for (int s = 0; s < 2; s++) { Player p = player(m, s); if (p != null && p.getOpenInventory().getTopInventory().getHolder() instanceof BattleMenu) p.closeInventory(); }
        for (var row : m.cards) for (var c : row) if (c != null) { c.acted = false; c.stunned = false; }
        int d0 = dexterity(m, 0), d1 = dexterity(m, 1);
        boolean coin = d0 == d1;
        int first = d0 > d1 ? 0 : d1 > d0 ? 1 : rng.nextInt(2);
        m.turn = first;
        m.energy[first] = 0;
        m.energy[1 - first] = rules().secondPlayerBonus();
        String who = nameOf(m.players[first]);
        tell(m, coin ? Component.text("Dexterity is tied at " + d0 + ". Coin flip: " + who + " goes first.", NamedTextColor.GOLD)
                : Component.text(who + " goes first (Dexterity " + Math.max(d0, d1) + " vs " + Math.min(d0, d1) + ").", NamedTextColor.GOLD));
        Title t = Title.title(Component.text("Reveal!", NamedTextColor.RED).decorate(TextDecoration.BOLD), Component.text(who + " goes first", NamedTextColor.GOLD),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(400)));
        for (int s = 0; s < 2; s++) { Player p = player(m, s); if (p != null) p.showTitle(t); }
        m.busy = true;
        long delay = 25L, extra = 0;
        for (int col = 0; col < 3; col++) for (int s = 0; s < 2; s++) {
            var c = m.cards[s][col];
            if (c == null) continue;
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (live(m)) displays().reveal(c); }, delay);
            if (c.card.rarity().ordinal() >= Rarity.EPIC.ordinal()) extra = Math.max(extra, 30L);
            delay += 10L;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (live(m)) beginTurn(m); }, delay + 20L + extra);
    }

    // ---------------------------------------------------------------- turns

    private void beginTurn(Mat m) {
        m.busy = false;
        final int side = m.turn;
        m.playedChar[side] = false; m.itemUsed[side] = false; m.whet[side] = 0; m.warDrum[side] = false;
        if (m.deck[side].isEmpty())
            tell(m, Component.text(nameOf(m.players[side]) + "'s deck is empty, so no card is drawn this turn.", NamedTextColor.GRAY));
        List<HandCard> drew = draw(m, side, 1, true);
        m.energy[side] = Math.min(rules().energyMax(), m.energy[side] + rules().energyPerTurn());
        boolean anyReady = false;
        for (var c : m.cards[side]) if (c != null && !c.ko && !c.acted) anyReady = true;
        if (!anyReady) for (var c : m.cards[side]) if (c != null) { c.acted = c.stunned; c.stunned = false; }
        if (m.isAi(side)) { Ai.turn(this, m, side); return; }
        Player cur = player(m, side);
        Player other = player(m, 1 - side);
        if (cur != null) {
            String d = drew.isEmpty() ? "nothing" : drew.get(0).name();
            say(cur, "Your turn: you drew " + d + ". Right-click your card or use /sc turn to open the menu.", NamedTextColor.GREEN);
            cur.showTitle(Title.title(Component.empty(), Component.text("Your turn  ⚡ " + m.energy[side] + "/" + rules().energyMax(), NamedTextColor.GREEN),
                    Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1200), Duration.ofMillis(300))));
            cur.playSound(cur.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.4f);
            long delay = plugin.getConfig().getLong("turn-menu-delay-ticks", 40);
            if (delay >= 0) Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (m.state != Mat.State.FIGHTING || m.turn != side || m.busy || !cur.isOnline()) return;
                if (cur.getOpenInventory().getTopInventory().getHolder() instanceof BattleMenu) return;
                new BattleMenu(plugin, cur, m).open();
            }, delay);
        }
        if (other != null) other.sendActionBar(Component.text(nameOf(m.players[side]) + "'s turn", NamedTextColor.GRAY));
    }

    public void openMenu(Player p) {
        Mat m = byPlayer.get(p.getUniqueId());
        if (m == null || (m.state != Mat.State.FIGHTING && m.state != Mat.State.SETUP)) { say(p, "You are not in a battle.", NamedTextColor.RED); return; }
        new BattleMenu(plugin, p, m).open();
    }

    public void openTurnMenu(Player p) { openMenu(p); }

    private boolean myTurn(Mat m, int side) { return m.state == Mat.State.FIGHTING && !m.busy && m.turn == side; }

    /** Plays a character from hand into an empty slot (1 per turn). */
    public boolean playCharacter(Mat m, int side, int handIdx, int col) {
        if (!myTurn(m, side) || m.playedChar[side]) return false;
        if (handIdx < 0 || handIdx >= m.hand[side].size() || col < 0 || col > 2 || m.cards[side][col] != null) return false;
        HandCard h = m.hand[side].get(handIdx);
        if (!h.isChar()) return false;
        DisplayManager.Placed pl = displays().placeFor(m.players[side], h.card, m.slotTop(side, col), m.sideYaw(side), true, h.holo);
        pl.mat = m; pl.side = side; pl.col = col; pl.source = h;
        m.cards[side][col] = pl;
        m.hand[side].remove(handIdx);
        m.playedChar[side] = true;
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (live(m)) displays().reveal(pl); }, 3L);
        tell(m, Component.text(nameOf(m.players[side]) + " played ", NamedTextColor.WHITE).append(Items.t((h.holo ? "✦ " : "") + h.card.name(), h.card.rarity().color())).append(Component.text(".", NamedTextColor.WHITE)));
        return true;
    }

    /** Ends the turn without attacking. */
    public void endTurn(Player p) {
        Mat m = byPlayer.get(p.getUniqueId());
        if (m == null) return;
        endTurn(m, m.sideOf(p.getUniqueId()));
    }

    void endTurn(Mat m, int side) {
        if (!myTurn(m, side)) return;
        m.busy = true;
        tell(m, Component.text(nameOf(m.players[side]) + " ends their turn.", NamedTextColor.GRAY));
        Bukkit.getScheduler().runTaskLater(plugin, () -> nextTurn(m), 10L);
    }

    // ---------------------------------------------------------------- items

    /** Uses an item or sets a trap from hand. target: your own card for heals and Sprint Shoes, an enemy card for Thunder Scroll. */
    public boolean useItem(Mat m, int side, int handIdx, DisplayManager.Placed target) {
        Player p = player(m, side);
        if (!myTurn(m, side) || handIdx < 0 || handIdx >= m.hand[side].size()) return false;
        HandCard h = m.hand[side].get(handIdx);
        if (h.isChar()) return false;
        ItemType t = h.item;
        if (m.itemUsed[side]) { say(p, "You already used an item this turn.", NamedTextColor.YELLOW); return false; }
        if (m.energy[side] < t.cost) { say(p, t.label + " costs " + t.cost + " energy; you have " + m.energy[side] + ".", NamedTextColor.YELLOW); return false; }
        if (t.kind == ItemType.Kind.TRAP && m.traps[side].size() >= 2) { say(p, "You already have 2 traps set.", NamedTextColor.YELLOW); return false; }
        switch (t.target()) {
            case OWN -> { if (target == null || target.ko || target.side != side) { say(p, "Choose one of your cards.", NamedTextColor.YELLOW); return false; }
                          if (t == ItemType.SPRINT_SHOES && !target.acted) { say(p, "Choose a card that already acted.", NamedTextColor.YELLOW); return false; } }
            case ENEMY -> { if (target == null || target.ko || target.side == side) { say(p, "Choose an enemy card.", NamedTextColor.YELLOW); return false; } }
            default -> { }
        }
        if (t == ItemType.RECALL_SCROLL && m.discard[side].stream().noneMatch(HandCard::isChar)) { say(p, "There's no character in your discard pile.", NamedTextColor.YELLOW); return false; }

        m.energy[side] -= t.cost;
        m.itemUsed[side] = true;
        m.hand[side].remove(handIdx);
        World w = m.center.getWorld();
        String who = nameOf(m.players[side]);
        Component used = Component.text(who + " used ", NamedTextColor.WHITE).append(Items.t(t.label, t.rarity.color()));
        if (t.kind == ItemType.Kind.TRAP) {
            Mat.Trap trap = new Mat.Trap(t);
            Location spot = m.trapSpot(side, m.traps[side].size());
            trap.display = w.spawn(spot.clone().add(0, 0.07, 0), ItemDisplay.class, d -> {
                d.setItemStack(plugin.items().cardBack());
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setRotation(m.sideYaw(side) + 90f, 0);
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f((float) Math.toRadians(90), 1, 0, 0), new Vector3f(0.38f, 0.38f, 0.38f), new AxisAngle4f()));
                d.setPersistent(false);
            });
            trap.source = h;
            m.traps[side].add(trap);
            w.playSound(spot, Sound.BLOCK_TRIPWIRE_ATTACH, 1f, 0.8f);
            tell(m, Component.text(who + " set a trap card face down.", NamedTextColor.LIGHT_PURPLE));
            return true;
        }
        discard(m, side, h);
        switch (t) {
            case BANDAGE, HERBAL_TONIC, MENDING_POTION -> {
                int healed = heal(target, t.amount);
                tell(m, used.append(Component.text(" on " + target.card.name() + " (+" + healed + " Health).", NamedTextColor.WHITE)));
            }
            case RALLY_HORN -> {
                int total = 0; for (var c : m.cards[side]) if (c != null && !c.ko) total += heal(c, t.amount);
                w.playSound(m.center, Sound.EVENT_RAID_HORN, 0.5f, 1.4f);
                tell(m, used.append(Component.text(": all cards heal (+" + total + " total).", NamedTextColor.WHITE)));
            }
            case ENERGY_DRINK -> { m.energy[side] = Math.min(rules().energyMax(), m.energy[side] + t.amount); w.playSound(m.center, Sound.ENTITY_GENERIC_DRINK, 0.8f, 1.5f);
                tell(m, used.append(Component.text(" (+" + t.amount + " energy).", NamedTextColor.WHITE))); }
            case SCOUTS_MAP -> { int n = draw(m, side, t.amount, true).size(); tell(m, used.append(Component.text(" and drew " + n + ".", NamedTextColor.WHITE))); }
            case DRAGONS_HOARD -> { m.energy[side] = Math.min(rules().energyMax(), m.energy[side] + t.amount); int n = draw(m, side, 2, true).size();
                w.playSound(m.center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);
                tell(m, used.append(Component.text(": +" + t.amount + " energy and drew " + n + ".", NamedTextColor.WHITE))); }
            case WHETSTONE -> { m.whet[side] = t.amount; w.playSound(m.center, Sound.ITEM_AXE_SCRAPE, 1f, 1.2f); tell(m, used.append(Component.text(": +" + t.amount + " damage this turn.", NamedTextColor.WHITE))); }
            case WAR_DRUM -> { m.warDrum[side] = true; w.playSound(m.center, Sound.EVENT_RAID_HORN, 0.6f, 0.8f); tell(m, used.append(Component.text(": +50% damage this turn.", NamedTextColor.WHITE))); }
            case SPRINT_SHOES -> { target.acted = false; target.stunned = false; tell(m, used.append(Component.text(": " + target.card.name() + " can act again.", NamedTextColor.WHITE))); }
            case RECALL_SCROLL -> {
                for (int i = m.discard[side].size() - 1; i >= 0; i--) {
                    HandCard d = m.discard[side].get(i);
                    if (!d.isChar()) continue;
                    m.discard[side].remove(i); m.hand[side].add(d); refreshDiscard(m, side);
                    tell(m, used.append(Component.text(": " + d.name() + " returns to their hand.", NamedTextColor.WHITE)));
                    break;
                }
            }
            case THUNDER_SCROLL -> {
                target.hp = Math.max(0, target.hp - t.amount);
                displays().refreshHp(target);
                displays().hurt(target, null, false);
                w.strikeLightningEffect(target.base);
                displays().popup(target.base.clone().add(0, 1.05, 0), Component.text("-" + t.amount, NamedTextColor.YELLOW).decorate(TextDecoration.BOLD), 0.9f);
                tell(m, used.append(Component.text(" on " + target.card.name() + " (" + t.amount + " damage).", NamedTextColor.WHITE)));
                if (target.hp <= 0) { m.busy = true; Bukkit.getScheduler().runTaskLater(plugin, () -> { knockOut(m, target, side); m.busy = false; checkWin(m); }, 14L); }
            }
            default -> { }
        }
        if (t.heals() || t == ItemType.RALLY_HORN) {
            for (var c : t == ItemType.RALLY_HORN ? Arrays.asList(m.cards[side]) : List.of(target)) if (c != null && !c.ko) {
                w.spawnParticle(Particle.HEART, c.base.clone().add(0, 0.7, 0), 3, 0.15, 0.15, 0.15, 0);
                w.spawnParticle(Particle.HAPPY_VILLAGER, c.base.clone().add(0, 0.5, 0), 8, 0.2, 0.25, 0.2, 0);
            }
            w.playSound(m.center, Sound.ENTITY_GENERIC_DRINK, 0.8f, 1.2f);
        }
        return true;
    }

    private int heal(DisplayManager.Placed c, int n) {
        int before = c.hp;
        c.hp = Math.min(c.maxHp, c.hp + n);
        displays().refreshHp(c);
        if (c.hp > before) displays().popup(c.base.clone().add(0, 1.05, 0), Component.text("+" + (c.hp - before), NamedTextColor.GREEN).decorate(TextDecoration.BOLD), 0.9f);
        return c.hp - before;
    }

    /** The first of the defender's traps that applies to this hit (removed from the mat), or null. */
    private Mat.Trap springTrap(Mat m, int side, int dmg, DisplayManager.Placed tgt) {
        for (Mat.Trap t : m.traps[side]) {
            if (t.type.savesFromKo() && dmg < tgt.hp) continue;
            m.traps[side].remove(t);
            return t;
        }
        return null;
    }

    private void revealTrap(Mat m, int side, Mat.Trap t) {
        discard(m, side, t.source);
        if (t.display == null || !t.display.isValid()) return;
        var d = t.display;
        d.setItemStack(plugin.items().itemCard(t.type));
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(6);
        d.setTransformation(new Transformation(new Vector3f(0, 0.35f, 0), new AxisAngle4f((float) Math.toRadians(90), 1, 0, 0), new Vector3f(0.6f, 0.6f, 0.6f), new AxisAngle4f()));
        World w = d.getWorld();
        Location l = d.getLocation().add(0, 0.4, 0);
        w.spawnParticle(Particle.DUST, l, 20, 0.2, 0.15, 0.2, 0, new Particle.DustOptions(org.bukkit.Color.fromRGB(t.type.rarity.color.value()), 1f));
        w.spawnParticle(Particle.ENCHANTED_HIT, l, 15, 0.2, 0.2, 0.2, 0.2);
        w.playSound(l, Sound.BLOCK_PISTON_EXTEND, 0.8f, 1.6f);
        w.playSound(l, Sound.ENTITY_EVOKER_CAST_SPELL, 0.6f, 1.4f);
        Bukkit.getScheduler().runTaskLater(plugin, d::remove, 40L);
    }

    // ---------------------------------------------------------------- attacking

    public void attack(Player p, Mat m, DisplayManager.Placed att, Move mv, DisplayManager.Placed tgt) { attack(m, m.sideOf(p.getUniqueId()), att, mv, tgt); }

    void attack(Mat m, int side, DisplayManager.Placed att, Move mv, DisplayManager.Placed tgt) {
        Player p = player(m, side);
        if (!myTurn(m, side) || att.ko || tgt.ko || att.side != side || tgt.side == side) return;
        if (att.acted) { say(p, att.card.name() + " already acted this round. Use your other cards first.", NamedTextColor.YELLOW); return; }
        int cost = Rules.moveCost(att.card, mv);
        if (m.energy[side] < cost) { say(p, "Not enough energy: " + mv.name() + " costs " + cost + ", you have " + m.energy[side] + ".", NamedTextColor.YELLOW); return; }
        m.busy = true;
        m.energy[side] = Math.min(rules().energyMax(), m.energy[side] - cost + Rules.refund(mv));
        att.acted = true;
        boolean crit = rng.nextInt(100) < Math.min(100, att.card.stat(Stat.LUCK));
        int raw = damage(m, side, att, mv, tgt, crit);
        Mat.Trap trap = springTrap(m, tgt.side, raw, tgt);
        int dmg = trap == null ? raw : trap.type == ItemType.DECOY ? Math.max(1, raw / 2) : trap.type == ItemType.SMOKE_VEIL ? 0 : raw;
        displays().attack(att, tgt, mv, () -> impact(m, att, tgt, mv, dmg, crit, trap), () -> afterAttack(m));
    }

    /** Damage including this turn's Whetstone and War Drum. */
    int damage(Mat m, int side, DisplayManager.Placed att, Move mv, DisplayManager.Placed tgt, boolean crit) {
        double d = rules().damage(att.card, mv, tgt.card, crit) * (m.warDrum[side] ? 1.5 : 1.0) + m.whet[side];
        return Math.max(1, (int) Math.round(d));
    }

    private void impact(Mat m, DisplayManager.Placed att, DisplayManager.Placed tgt, Move mv, int dmg, boolean crit, Mat.Trap trap) {
        if (m.state != Mat.State.FIGHTING) return;
        int side = att.side, foe = tgt.side;
        World w = m.center.getWorld();
        tgt.hp = Math.max(0, tgt.hp - dmg);
        Component trapMsg = null;
        boolean banish = false;
        if (trap != null) {
            revealTrap(m, foe, trap);
            ItemType t = trap.type;
            Component head = Component.text("Trap! ", NamedTextColor.LIGHT_PURPLE).append(Items.t(t.label, t.rarity.color())).append(Component.text(": ", NamedTextColor.LIGHT_PURPLE));
            switch (t) {
                case DECOY -> trapMsg = head.append(Component.text("the hit was halved.", NamedTextColor.WHITE));
                case SMOKE_VEIL -> { trapMsg = head.append(Component.text("the attack missed!", NamedTextColor.WHITE));
                    w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, tgt.base.clone().add(0, 0.4, 0), 12, 0.2, 0.2, 0.2, 0.01); }
                case LAST_STAND, GUARDIAN_ANGEL -> {
                    tgt.hp = t == ItemType.LAST_STAND ? 1 : Math.max(1, tgt.maxHp / 2);
                    w.spawnParticle(Particle.TOTEM_OF_UNDYING, tgt.base.clone().add(0, 0.6, 0), 30, 0.2, 0.3, 0.2, 0.3);
                    w.playSound(tgt.base, Sound.ITEM_TOTEM_USE, 0.7f, 1.3f);
                    trapMsg = head.append(Component.text(tgt.card.name() + " survives with " + tgt.hp + " Health!", NamedTextColor.WHITE));
                }
                case SPIKE_TRAP, MIRROR_WARD, COUNTER_STANCE -> {
                    int back = t == ItemType.MIRROR_WARD ? Math.max(1, dmg / 2) : t.amount;
                    att.hp = Math.max(0, att.hp - back);
                    displays().refreshHp(att);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (m.state != Mat.State.FIGHTING || !mats.contains(m) || att.ko) return;
                        displays().hurt(att, tgt, false);
                        displays().popup(att.base.clone().add(0, 1.05, 0), Component.text("-" + back, NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD), 0.9f);
                    }, 14L);
                    trapMsg = head.append(Component.text(att.card.name() + " takes " + back + " damage.", NamedTextColor.WHITE));
                    if (att.hp <= 0) Bukkit.getScheduler().runTaskLater(plugin, () -> { if (m.state == Mat.State.FIGHTING && mats.contains(m)) knockOut(m, att, foe); }, 30L);
                }
                case SNARE -> {
                    att.stunned = true;
                    w.spawnParticle(Particle.ITEM_SLIME, att.base.clone().add(0, 0.5, 0), 15, 0.15, 0.2, 0.15, 0);
                    trapMsg = head.append(Component.text(att.card.name() + " is stunned and will skip its next action.", NamedTextColor.WHITE));
                }
                case PITFALL -> {
                    m.energy[side] = Math.max(0, m.energy[side] - t.amount);
                    trapMsg = head.append(Component.text(nameOf(m.players[side]) + " loses " + t.amount + " energy.", NamedTextColor.WHITE));
                }
                case BANISH_RUNE -> {
                    banish = true;
                    trapMsg = head.append(Component.text(att.card.name() + " is sent to the bottom of its owner's deck.", NamedTextColor.WHITE));
                }
                default -> { }
            }
        }
        displays().refreshHp(tgt);
        if (dmg > 0) {
            displays().hurt(tgt, att, crit);
            Component num = crit ? Component.text("CRIT -" + dmg, NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
                    : Component.text("-" + dmg, NamedTextColor.RED).decorate(TextDecoration.BOLD);
            displays().popup(tgt.base.clone().add(0, 1.05, 0), num, crit ? 1.3f : 0.9f);
        } else displays().popup(tgt.base.clone().add(0, 1.05, 0), Component.text("MISS", NamedTextColor.GRAY).decorate(TextDecoration.BOLD), 0.9f);
        Component msg = Component.text(att.card.name(), att.card.rarity().color())
                .append(Component.text(" used " + mv.name() + " on ", NamedTextColor.WHITE))
                .append(Component.text(tgt.card.name(), tgt.card.rarity().color()))
                .append(Component.text(dmg > 0 ? " for " + dmg + " damage" + (crit ? " (critical hit!)" : "") + "." : ".", crit ? NamedTextColor.GOLD : NamedTextColor.WHITE));
        double mult = rules().typeMultiplier(mv, tgt.card);
        if (dmg > 0 && mult > 1) msg = msg.append(Component.text(" Super effective!", NamedTextColor.GREEN));
        else if (dmg > 0 && mult < 1) msg = msg.append(Component.text(" Not very effective.", NamedTextColor.GRAY));
        tell(m, msg);
        if (trapMsg != null) tell(m, trapMsg);
        if (tgt.hp <= 0) Bukkit.getScheduler().runTaskLater(plugin, () -> { if (m.state == Mat.State.FIGHTING && mats.contains(m)) knockOut(m, tgt, side); }, crit ? 20L : 14L);
        if (banish && att.hp > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (m.state != Mat.State.FIGHTING || !mats.contains(m) || att.ko || m.cards[side][att.col] != att) return;
                w.spawnParticle(Particle.REVERSE_PORTAL, att.base.clone().add(0, 0.4, 0), 30, 0.2, 0.3, 0.2, 0.05);
                w.playSound(att.base, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
                displays().remove(att);
                m.cards[side][att.col] = null;
                m.deck[side].addLast(att.source);
                refreshDeck(m, side);
            }, 18L);
        }
    }

    /** Knocks a card out: it scores a point for the other side and goes to its owner's discard pile. */
    private void knockOut(Mat m, DisplayManager.Placed pl, int scorer) {
        if (pl.ko) return;
        displays().knockOut(pl);
        m.points[scorer]++;
        tell(m, Component.text(pl.card.name() + " is knocked out! " + nameOf(m.players[scorer]) + " scores (" + m.points[scorer] + "/" + pointsToWin() + ").", NamedTextColor.RED));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!mats.contains(m) || m.state == Mat.State.WAITING) return;
            if (m.cards[pl.side][pl.col] == pl) m.cards[pl.side][pl.col] = null;
            displays().remove(pl);
            discard(m, pl.side, pl.source);
        }, 24L);
    }

    private void afterAttack(Mat m) {
        long pause = plugin.getConfig().getLong("pause-after-attack-ticks", 30);
        Bukkit.getScheduler().runTaskLater(plugin, () -> nextTurn(m), Math.max(36L, pause));
    }

    private void nextTurn(Mat m) {
        if (m.state != Mat.State.FIGHTING || !mats.contains(m)) return;
        if (checkWin(m)) return;
        m.turn = 1 - m.turn;
        beginTurn(m);
    }

    /** Ends the battle if someone has enough points or has run out of characters. */
    boolean checkWin(Mat m) {
        if (m.state != Mat.State.FIGHTING) return true;
        for (int s = 0; s < 2; s++) if (m.points[s] >= pointsToWin()) { end(m, s); return true; }
        for (int s = 0; s < 2; s++) if (!m.hasCharacters(s)) {
            tell(m, Component.text(nameOf(m.players[s]) + " has no characters left.", NamedTextColor.RED));
            end(m, 1 - s); return true;
        }
        return false;
    }

    private void end(Mat m, int winner) {
        m.state = Mat.State.ENDED;
        m.busy = true;
        UUID win = m.players[winner];
        Player wp = player(m, winner), lp = player(m, 1 - winner);
        Duration d = Duration.ofSeconds(3);
        if (wp != null) {
            wp.showTitle(Title.title(Component.text("Victory!", NamedTextColor.GOLD).decorate(TextDecoration.BOLD), Component.text("You win the battle", NamedTextColor.YELLOW),
                    Title.Times.times(Duration.ofMillis(300), d, Duration.ofMillis(600))));
            wp.playSound(wp.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        if (lp != null) lp.showTitle(Title.title(Component.text("Defeat", NamedTextColor.RED).decorate(TextDecoration.BOLD), Component.text(nameOf(win) + " wins", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(300), d, Duration.ofMillis(600))));
        tell(m, Component.text(nameOf(win) + " wins " + m.points[winner] + " to " + m.points[1 - winner] + "!", NamedTextColor.GOLD));
        for (var c : m.cards[winner]) if (c != null && !c.ko) {
            m.center.getWorld().spawnParticle(Particle.FIREWORK, c.base.clone().add(0, 0.6, 0), 30, 0.2, 0.3, 0.2, 0.1);
            m.center.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, c.base.clone().add(0, 0.6, 0), 20, 0.2, 0.3, 0.2, 0.3);
        }
        for (int s = 0; s < 2; s++) { Player p = player(m, s); if (p != null && p.getOpenInventory().getTopInventory().getHolder() instanceof BattleMenu) p.closeInventory(); }
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (mats.contains(m)) resetMat(m); }, 120L);
    }

    /** Puts the mat back to its empty state. Nothing is returned: cards never left anyone's binder. */
    private void resetMat(Mat m) {
        for (int s = 0; s < 2; s++) {
            UUID id = m.players[s];
            if (id != null) byPlayer.remove(id);
            Player p = player(m, s);
            if (p != null && p.getOpenInventory().getTopInventory().getHolder() instanceof BattleMenu) p.closeInventory();
            for (int c = 0; c < 3; c++) { if (m.cards[s][c] != null) displays().remove(m.cards[s][c]); m.cards[s][c] = null; }
            for (Mat.Trap t : m.traps[s]) if (t.display != null) t.display.remove();
            if (m.deckDisplay[s] != null) m.deckDisplay[s].remove();
            if (m.discardDisplay[s] != null) m.discardDisplay[s].remove();
            m.deckDisplay[s] = null; m.discardDisplay[s] = null;
            m.players[s] = null;
        }
        m.resetBattleState();
        m.state = Mat.State.WAITING;
        m.countdown = false;
        m.turn = -1;
        m.busy = false;
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
            case FIGHTING, SETUP -> {
                tell(m, Component.text(nameOf(id) + " forfeits.", NamedTextColor.GRAY));
                if (m.state == Mat.State.SETUP) { m.state = Mat.State.FIGHTING; }
                end(m, 1 - side);
            }
            case WAITING -> {
                releaseSide(m, side);
                if (m.isAi(1 - side)) releaseSide(m, 1 - side);
                m.countdown = false;
            }
            default -> { }
        }
    }
}
