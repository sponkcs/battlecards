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
 *  2. Each player draws 5 (always at least 1 character) and puts one character face down in the middle (the Active)
 *     and up to 2 on the Bench. They flip over and a coin decides who goes first.
 *  3. Each turn: draw 1, get 1 mana (none on the very first turn of the game) and attach it to any of your cards.
 *     Mana stays on a card. Bench as many characters as fit, play 1 item or trap, retreat once (the Active discards
 *     mana equal to its retreat cost), then attack with your Active: a move needs that much mana attached and hits
 *     the rival's Active. Attacking ends your turn. A type that beats the defender's type deals +10.
 *  4. Each knockout scores 1 point; the knocked-out card goes to the discard pile with its mana and a benched card
 *     steps up. First to 3 points wins; a player with no characters left in play loses.
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
    private int handLimit() { return rules().handLimit(); }

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
            m.points[s] = 0; m.mana[s] = 0; m.turns[s] = 0; m.ready[s] = false; m.promoting[s] = false;
            List<HandCard> list = new ArrayList<>(m.deck[s]);
            // opening hand of 5 with at least 1 character (reshuffle and redraw if not)
            for (int tries = 0; tries < 20; tries++) {
                Collections.shuffle(list, rng);
                boolean any = false; for (int i = 0; i < 5 && i < list.size(); i++) if (list.get(i).isChar()) any = true;
                if (any) break;
            }
            m.deck[s].clear(); m.deck[s].addAll(list);
            w.playSound(m.deckTop(s), Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.7f);
            draw(m, s, 5, false);
            refreshDiscard(m, s);
        }
        tell(m, Component.text("Decks shuffled. You drew 5 cards: put a character face down in the middle (your Active), "
                + "and up to 2 more on your Bench, then click Ready.", NamedTextColor.GOLD));
        for (int s = 0; s < 2; s++) {
            if (m.isAi(s)) { Ai.setup(this, m, s); continue; }
            Player p = player(m, s);
            if (p != null) Bukkit.getScheduler().runTaskLater(plugin, () -> { if (m.state == Mat.State.SETUP && p.isOnline()) new BattleMenu(plugin, p, m).open(); }, 15L);
        }
    }

    /** Setup: puts a character from your hand face down in an empty slot (1 = Active, 0 and 2 = Bench). */
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
        if (m.cards[side][Mat.ACTIVE] == null) {
            // a bench card but no Active: slide it into the middle
            for (int b : Mat.BENCH) if (m.cards[side][b] != null) { relocate(m, m.cards[side][b], Mat.ACTIVE, true); break; }
        }
        if (m.cards[side][Mat.ACTIVE] == null) { say(p, "Put a character face down in the middle slot (your Active) first.", NamedTextColor.YELLOW); return; }
        markReady(m, side);
    }

    void markReady(Mat m, int side) {
        m.ready[side] = true;
        tell(m, Component.text(nameOf(m.players[side]) + " is ready.", NamedTextColor.GRAY));
        if (m.ready[0] && m.ready[1]) startFight(m);
    }

    private void startFight(Mat m) {
        m.state = Mat.State.FIGHTING;
        for (int s = 0; s < 2; s++) { Player p = player(m, s); if (p != null && p.getOpenInventory().getTopInventory().getHolder() instanceof BattleMenu) p.closeInventory(); }
        for (var row : m.cards) for (var c : row) if (c != null) { c.mana = 0; c.dazed = false; c.dazedTurn = -1; }
        int first = rng.nextInt(2);
        m.turn = first; m.first = first;
        String who = nameOf(m.players[first]);
        tell(m, Component.text("Coin flip: " + who + " goes first" + (rules().firstTurnMana() ? "." : " (no mana on the very first turn)."), NamedTextColor.GOLD));
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

    /** True while this card can't attack or retreat (dazed during its owner's current turn). */
    static boolean dazed(Mat m, DisplayManager.Placed c) { return c != null && c.dazed && c.dazedTurn == m.turns[c.side] && m.turn == c.side; }

    private void daze(Mat m, DisplayManager.Placed c) {
        c.dazed = true;
        c.dazedTurn = m.turns[c.side] + 1;   // its owner's next turn (or this one's successor, if it's their turn now)
        displays().refreshHp(c);
        c.base.getWorld().spawnParticle(Particle.ITEM_SLIME, c.base.clone().add(0, 0.5, 0), 15, 0.15, 0.2, 0.15, 0);
    }

    private void beginTurn(Mat m) {
        m.busy = false;
        final int side = m.turn;
        m.turns[side]++;
        m.itemUsed[side] = false; m.retreated[side] = false; m.bonus[side] = 0;
        boolean veryFirst = m.turns[0] + m.turns[1] == 1;
        m.mana[side] = veryFirst && !rules().firstTurnMana() ? 0 : rules().manaPerTurn();
        if (m.deck[side].isEmpty())
            tell(m, Component.text(nameOf(m.players[side]) + "'s deck is empty, so no card is drawn this turn.", NamedTextColor.GRAY));
        List<HandCard> drew = draw(m, side, 1, true);
        for (var c : m.cards[side]) if (c != null) displays().refreshHp(c);
        if (m.isAi(side)) { Ai.turn(this, m, side); return; }
        Player cur = player(m, side);
        Player other = player(m, 1 - side);
        if (cur != null) {
            String d = drew.isEmpty() ? "nothing" : drew.get(0).name();
            say(cur, "Your turn: you drew " + d + "." + (m.mana[side] > 0 ? " Attach your mana ◆ to a card, then attack with your Active." : " No mana on the first turn."), NamedTextColor.GREEN);
            cur.showTitle(Title.title(Component.empty(), Component.text("Your turn" + (m.mana[side] > 0 ? "  +◆ mana" : ""), NamedTextColor.GREEN),
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

    boolean myTurn(Mat m, int side) { return m.state == Mat.State.FIGHTING && !m.busy && m.turn == side && !m.promoting[side]; }

    private void refreshMenu(Mat m, int side) {
        Player p = player(m, side);
        if (p != null && p.getOpenInventory().getTopInventory().getHolder() instanceof BattleMenu bm) bm.refresh();
    }

    /** Plays a character from hand onto an empty Bench slot (as many per turn as you have room for). */
    public boolean playCharacter(Mat m, int side, int handIdx) {
        if (!myTurn(m, side) || handIdx < 0 || handIdx >= m.hand[side].size()) return false;
        int col = m.emptyBench(side);
        if (col < 0) { say(player(m, side), "Your Bench is full.", NamedTextColor.YELLOW); return false; }
        HandCard h = m.hand[side].get(handIdx);
        if (!h.isChar()) return false;
        DisplayManager.Placed pl = displays().placeFor(m.players[side], h.card, m.slotTop(side, col), m.sideYaw(side), true, h.holo);
        pl.mat = m; pl.side = side; pl.col = col; pl.source = h;
        m.cards[side][col] = pl;
        m.hand[side].remove(handIdx);
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (live(m)) displays().reveal(pl); }, 3L);
        tell(m, Component.text(nameOf(m.players[side]) + " benched ", NamedTextColor.WHITE).append(Items.t((h.holo ? "✦ " : "") + h.card.name(), h.card.rarity().color())).append(Component.text(".", NamedTextColor.WHITE)));
        return true;
    }

    /** Attaches this turn's mana to one of your cards. It stays there until the card is knocked out, retreats or loses it. */
    public boolean attachMana(Mat m, int side, DisplayManager.Placed c) {
        Player p = player(m, side);
        if (!myTurn(m, side) || c == null || c.ko || c.side != side) return false;   // a card benched this instant may still be flipping over
        if (m.mana[side] <= 0) { say(p, m.turns[0] + m.turns[1] == 1 ? "No mana on the very first turn." : "You already attached your mana this turn.", NamedTextColor.YELLOW); return false; }
        m.mana[side]--;
        c.mana++;
        displays().refreshHp(c);
        World w = c.base.getWorld();
        w.spawnParticle(Particle.DUST, c.base.clone().add(0, 0.35, 0), 18, 0.15, 0.25, 0.15, 0, new Particle.DustOptions(org.bukkit.Color.fromRGB(0x55E0FF), 1.1f));
        w.spawnParticle(Particle.ENCHANT, c.base.clone().add(0, 0.6, 0), 20, 0.2, 0.3, 0.2, 0.4);
        w.playSound(c.base, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.3f);
        tell(m, Component.text(nameOf(m.players[side]) + " attached mana to " + c.card.name() + " (◆ " + c.mana + ").", NamedTextColor.AQUA));
        return true;
    }

    /** Moves a card to another slot of its side (respawns its displays there). Returns the card in its new place. */
    private DisplayManager.Placed relocate(Mat m, DisplayManager.Placed pl, int col, boolean faceDown) {
        int side = pl.side;
        DisplayManager.Placed np = displays().placeFor(m.players[side], pl.card, m.slotTop(side, col), m.sideYaw(side), faceDown, pl.holo);
        np.hp = pl.hp; np.maxHp = pl.maxHp; np.mana = pl.mana; np.dazed = pl.dazed; np.dazedTurn = pl.dazedTurn;
        np.mat = m; np.side = side; np.col = col; np.source = pl.source;
        if (m.cards[side][pl.col] == pl) m.cards[side][pl.col] = null;
        displays().remove(pl);
        m.cards[side][col] = np;
        displays().refreshHp(np);
        if (!faceDown) np.base.getWorld().spawnParticle(Particle.CLOUD, np.base.clone().add(0, 0.3, 0), 8, 0.15, 0.1, 0.15, 0.01);
        return np;
    }

    /** Swaps the Active card with a benched one. */
    private void swap(Mat m, int side, DisplayManager.Placed bench) {
        DisplayManager.Placed act = m.cards[side][Mat.ACTIVE];
        int benchCol = bench.col;
        if (act != null) { m.cards[side][Mat.ACTIVE] = null; act = relocate(m, act, benchCol, false); }
        else m.cards[side][benchCol] = null;
        relocate(m, bench, Mat.ACTIVE, false);
        if (act != null) m.cards[side][benchCol] = act;
        m.center.getWorld().playSound(m.slotTop(side, Mat.ACTIVE), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1f, 1.2f);
    }

    /** Retreat: once per turn, the Active card discards mana equal to its retreat cost and swaps with a benched card. */
    public boolean retreat(Mat m, int side, DisplayManager.Placed bench) {
        Player p = player(m, side);
        var act = m.active(side);
        if (!myTurn(m, side) || act == null || bench == null || bench.side != side || bench.col == Mat.ACTIVE || bench.ko) return false;
        if (m.retreated[side]) { say(p, "You already retreated this turn.", NamedTextColor.YELLOW); return false; }
        if (dazed(m, act)) { say(p, act.card.name() + " is dazed and can't retreat.", NamedTextColor.YELLOW); return false; }
        int cost = act.card.retreat();
        if (act.mana < cost) { say(p, "Retreating " + act.card.name() + " costs " + cost + " mana; it has " + act.mana + ".", NamedTextColor.YELLOW); return false; }
        act.mana -= cost;
        m.retreated[side] = true;
        String out = act.card.name();
        swap(m, side, bench);
        tell(m, Component.text(nameOf(m.players[side]) + " retreated " + out + (cost > 0 ? " (discarded " + cost + " mana)" : "") + "; " + bench.card.name() + " is now Active.", NamedTextColor.WHITE));
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

    // ---------------------------------------------------------------- knockouts and the new Active

    /** The side's Active is gone: it must move a benched card up. Waits for a player's choice (with a time limit). */
    private void needActive(Mat m, int side) {
        if (m.state != Mat.State.FIGHTING || m.active(side) != null) return;
        List<DisplayManager.Placed> bench = m.bench(side);
        if (bench.isEmpty()) return;   // checkWin ends the battle: no characters in play
        if (bench.size() == 1 || m.isAi(side) || player(m, side) == null) { promote(m, side, m.isAi(side) ? Ai.pickActive(m, side) : bench.get(0)); return; }
        m.promoting[side] = true;
        Player p = player(m, side);
        say(p, "Your Active was knocked out! Pick a benched card to become your new Active.", NamedTextColor.GOLD);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.2f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (m.promoting[side] && p.isOnline()) new BattleMenu(plugin, p, m).open(); }, 5L);
        final int turnsNow = m.turns[0] + m.turns[1];
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!m.promoting[side] || m.state != Mat.State.FIGHTING || m.turns[0] + m.turns[1] != turnsNow) return;
            var b = m.bench(side);
            if (!b.isEmpty()) { say(p, "Time's up: picked " + b.get(0).card.name() + ".", NamedTextColor.GRAY); promote(m, side, b.get(0)); }
        }, 400L);
    }

    /** Moves a benched card into the empty Active slot. */
    public boolean promote(Mat m, int side, DisplayManager.Placed bench) {
        if (m.state != Mat.State.FIGHTING || bench == null || bench.side != side || bench.ko || bench.col == Mat.ACTIVE || m.active(side) != null) return false;
        m.cards[side][Mat.ACTIVE] = null;
        relocate(m, bench, Mat.ACTIVE, false);
        m.promoting[side] = false;
        tell(m, Component.text(bench.card.name() + " steps up as " + nameOf(m.players[side]) + "'s Active.", NamedTextColor.WHITE));
        Player p = player(m, side);
        if (p != null && p.getOpenInventory().getTopInventory().getHolder() instanceof BattleMenu) p.closeInventory();
        refreshMenu(m, 1 - side);
        return true;
    }

    /** Knocks a card out: it scores a point for the other side and goes to its owner's discard pile with its mana. */
    private void knockOut(Mat m, DisplayManager.Placed pl, int scorer) {
        if (pl.ko) return;
        displays().knockOut(pl);
        m.points[scorer]++;
        m.pendingKo++;
        tell(m, Component.text(pl.card.name() + " is knocked out! " + nameOf(m.players[scorer]) + " scores (" + m.points[scorer] + "/" + rules().pointsToWin() + ").", NamedTextColor.RED));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            m.pendingKo = Math.max(0, m.pendingKo - 1);
            if (!mats.contains(m) || m.state == Mat.State.WAITING) return;
            boolean wasActive = pl.col == Mat.ACTIVE && m.cards[pl.side][pl.col] == pl;
            if (m.cards[pl.side][pl.col] == pl) m.cards[pl.side][pl.col] = null;
            displays().remove(pl);
            discard(m, pl.side, pl.source);
            if (checkWin(m)) return;
            if (wasActive) needActive(m, pl.side);
        }, 24L);
    }

    // ---------------------------------------------------------------- items

    /** Uses an item or sets a trap from hand. target: one of your cards (heals), a benched card (Energy Drink, Sprint Shoes) or a rival card (Thunder Scroll). */
    public boolean useItem(Mat m, int side, int handIdx, DisplayManager.Placed target) {
        Player p = player(m, side);
        if (!myTurn(m, side) || handIdx < 0 || handIdx >= m.hand[side].size()) return false;
        HandCard h = m.hand[side].get(handIdx);
        if (h.isChar()) return false;
        ItemType t = h.item;
        if (m.itemUsed[side]) { say(p, "You already played an item this turn.", NamedTextColor.YELLOW); return false; }
        if (t.kind == ItemType.Kind.TRAP && m.traps[side].size() >= 2) { say(p, "You already have 2 traps set.", NamedTextColor.YELLOW); return false; }
        switch (t.target()) {
            case OWN -> { if (target == null || target.ko || target.side != side) { say(p, "Choose one of your cards.", NamedTextColor.YELLOW); return false; } }
            case BENCH -> { if (target == null || target.ko || target.side != side || target.col == Mat.ACTIVE) { say(p, "Choose one of your benched cards.", NamedTextColor.YELLOW); return false; } }
            case ENEMY -> { if (target == null || target.ko || target.side == side) { say(p, "Choose a rival card.", NamedTextColor.YELLOW); return false; } }
            default -> { }
        }
        if (t == ItemType.RECALL_SCROLL && m.discard[side].stream().noneMatch(HandCard::isChar)) { say(p, "There's no character in your discard pile.", NamedTextColor.YELLOW); return false; }

        m.itemUsed[side] = true;
        m.hand[side].remove(handIdx);
        World w = m.center.getWorld();
        String who = nameOf(m.players[side]);
        Component used = Component.text(who + " used ", NamedTextColor.WHITE).append(Items.t(t.label, t.rarity.color()));
        if (t.kind == ItemType.Kind.TRAP) {
            Mat.Trap trap = new Mat.Trap(t);
            int free = 0;   // the trap spot that isn't taken (a sprung trap frees its spot)
            for (Mat.Trap other : m.traps[side]) if (other.spot == free) free = 1 - free;
            trap.spot = free;
            Location spot = m.trapSpot(side, free);
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
            case ENERGY_DRINK -> {
                target.mana += t.amount; displays().refreshHp(target);
                w.playSound(m.center, Sound.ENTITY_GENERIC_DRINK, 0.8f, 1.5f);
                w.spawnParticle(Particle.ENCHANT, target.base.clone().add(0, 0.6, 0), 20, 0.2, 0.3, 0.2, 0.4);
                tell(m, used.append(Component.text(": " + target.card.name() + " gets +1 mana (◆ " + target.mana + ").", NamedTextColor.WHITE)));
            }
            case SPRINT_SHOES -> {
                String out = m.active(side) == null ? "" : m.active(side).card.name();
                swap(m, side, target);
                tell(m, used.append(Component.text(": " + out + " swaps with " + target.card.name() + ".", NamedTextColor.WHITE)));
            }
            case SCOUTS_MAP -> { int n = draw(m, side, t.amount, true).size(); tell(m, used.append(Component.text(" and drew " + n + ".", NamedTextColor.WHITE))); }
            case DRAGONS_HOARD -> {
                int n = draw(m, side, 2, true).size();
                for (var c : m.cards[side]) if (c != null && !c.ko) { c.mana += t.amount; displays().refreshHp(c);
                    w.spawnParticle(Particle.ENCHANT, c.base.clone().add(0, 0.6, 0), 15, 0.2, 0.3, 0.2, 0.4); }
                w.playSound(m.center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);
                tell(m, used.append(Component.text(": drew " + n + " and every card gets +1 mana.", NamedTextColor.WHITE)));
            }
            case WHETSTONE, WAR_DRUM -> {
                m.bonus[side] += t.amount;
                w.playSound(m.center, t == ItemType.WHETSTONE ? Sound.ITEM_AXE_SCRAPE : Sound.EVENT_RAID_HORN, t == ItemType.WHETSTONE ? 1f : 0.6f, t == ItemType.WHETSTONE ? 1.2f : 0.8f);
                tell(m, used.append(Component.text(": +" + t.amount + " damage this turn.", NamedTextColor.WHITE)));
            }
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
                hit(m, target, t.amount, NamedTextColor.YELLOW);
                w.strikeLightningEffect(target.base);
                tell(m, used.append(Component.text(" on " + target.card.name() + " (" + t.amount + " damage).", NamedTextColor.WHITE)));
                if (target.hp <= 0) Bukkit.getScheduler().runTaskLater(plugin, () -> { if (live(m)) knockOut(m, target, side); }, 14L);
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

    /** Takes Health off a card with the hurt animation and a damage number (no knockout check). */
    private void hit(Mat m, DisplayManager.Placed c, int dmg, NamedTextColor color) {
        c.hp = Math.max(0, c.hp - dmg);
        displays().refreshHp(c);
        displays().hurt(c, null, false);
        displays().popup(c.base.clone().add(0, 1.05, 0), Component.text("-" + dmg, color).decorate(TextDecoration.BOLD), 0.9f);
    }

    /** The first of the defender's traps that applies to this hit (removed from the mat), or null. */
    private Mat.Trap springTrap(Mat m, int side, int dmg, DisplayManager.Placed tgt) {
        for (Mat.Trap t : m.traps[side]) {
            if (t.type.savesFromKo() && dmg < tgt.hp) continue;
            if (t.type == ItemType.BANISH_RUNE && m.bench(1 - side).isEmpty()) continue;
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

    /** Damage a move would deal to a card right now (weakness and this turn's item bonus included). */
    int damage(Mat m, int side, DisplayManager.Placed att, Move mv, DisplayManager.Placed tgt) {
        return rules().damage(att.card, mv, tgt.card, m.bonus[side]);
    }

    /** Why the Active can't use this move right now, or null if it can. */
    String cantAttack(Mat m, int side, Move mv) {
        var att = m.active(side);
        if (m.state != Mat.State.FIGHTING || m.turn != side) return "Not your turn";
        if (m.busy) return "Wait for the move to finish";
        if (att == null) return "You have no Active card";
        if (m.promoting[1 - side] || m.active(1 - side) == null || m.pendingKo > 0 || m.active(1 - side).hp <= 0) return "Waiting for the rival to pick a new Active";
        if (dazed(m, att)) return att.card.name() + " is dazed this turn";
        if (att.mana < mv.cost()) return "Needs " + mv.cost() + " mana on " + att.card.name() + " (has " + att.mana + ")";
        return null;
    }

    /** Your Active uses one of its moves on the rival's Active. Attacking ends your turn. */
    public void attack(Player p, Mat m, int moveIndex) { attack(m, m.sideOf(p.getUniqueId()), moveIndex); }

    void attack(Mat m, int side, int moveIndex) {
        Player p = player(m, side);
        if (!myTurn(m, side)) return;
        var att = m.active(side);
        if (att == null) return;
        Move mv = att.card.moves().get(moveIndex);
        String why = cantAttack(m, side, mv);
        if (why != null) { say(p, why + ".", NamedTextColor.YELLOW); return; }
        var tgt = m.active(1 - side);
        m.busy = true;
        int raw = damage(m, side, att, mv, tgt);
        Mat.Trap trap = springTrap(m, tgt.side, raw, tgt);
        int dmg = trap == null ? raw : trap.type == ItemType.DECOY ? Math.max(0, raw - trap.type.amount) : trap.type == ItemType.SMOKE_VEIL ? 0 : raw;
        displays().attack(att, tgt, mv, () -> impact(m, att, tgt, mv, dmg, trap), () -> afterAttack(m));
    }

    private void impact(Mat m, DisplayManager.Placed att, DisplayManager.Placed tgt, Move mv, int dmg, Mat.Trap trap) {
        if (m.state != Mat.State.FIGHTING) return;
        int side = att.side, foe = tgt.side;
        World w = m.center.getWorld();
        tgt.hp = Math.max(0, tgt.hp - dmg);
        List<Component> notes = new ArrayList<>();
        boolean banish = false, missed = trap != null && trap.type == ItemType.SMOKE_VEIL;
        int recoil = 0;
        if (trap != null) {
            revealTrap(m, foe, trap);
            ItemType t = trap.type;
            Component head = Component.text("Trap! ", NamedTextColor.LIGHT_PURPLE).append(Items.t(t.label, t.rarity.color())).append(Component.text(": ", NamedTextColor.LIGHT_PURPLE));
            switch (t) {
                case DECOY -> notes.add(head.append(Component.text("the hit was " + t.amount + " weaker.", NamedTextColor.WHITE)));
                case SMOKE_VEIL -> { notes.add(head.append(Component.text("the attack missed!", NamedTextColor.WHITE)));
                    w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, tgt.base.clone().add(0, 0.4, 0), 12, 0.2, 0.2, 0.2, 0.01); }
                case LAST_STAND, GUARDIAN_ANGEL -> {
                    tgt.hp = t == ItemType.LAST_STAND ? t.amount : Math.max(10, Rules.round10(tgt.maxHp / 2.0));
                    w.spawnParticle(Particle.TOTEM_OF_UNDYING, tgt.base.clone().add(0, 0.6, 0), 30, 0.2, 0.3, 0.2, 0.3);
                    w.playSound(tgt.base, Sound.ITEM_TOTEM_USE, 0.7f, 1.3f);
                    notes.add(head.append(Component.text(tgt.card.name() + " survives with " + tgt.hp + " Health!", NamedTextColor.WHITE)));
                }
                case SPIKE_TRAP, MIRROR_WARD -> {
                    recoil = t == ItemType.MIRROR_WARD ? Math.max(10, Rules.round10(dmg / 2.0)) : t.amount;
                    notes.add(head.append(Component.text(att.card.name() + " takes " + recoil + " damage.", NamedTextColor.WHITE)));
                }
                case COUNTER_STANCE -> {
                    if (tgt.hp > 0) { recoil = t.amount; notes.add(head.append(Component.text(tgt.card.name() + " strikes back for " + recoil + "!", NamedTextColor.WHITE))); }
                    else notes.add(head.append(Component.text(tgt.card.name() + " fell before it could strike back.", NamedTextColor.WHITE)));
                }
                case SNARE -> { daze(m, att); notes.add(head.append(Component.text(att.card.name() + " is dazed: it can't attack or retreat next turn.", NamedTextColor.WHITE))); }
                case PITFALL -> { att.mana = Math.max(0, att.mana - t.amount); displays().refreshHp(att);
                    notes.add(head.append(Component.text(att.card.name() + " loses " + t.amount + " mana.", NamedTextColor.WHITE))); }
                case BANISH_RUNE -> { banish = true; notes.add(head.append(Component.text(att.card.name() + " is sent to the bottom of its owner's deck.", NamedTextColor.WHITE))); }
                default -> { }
            }
        }
        // the move's own effect (a missed attack does nothing)
        if (!missed) switch (mv.effect()) {
            case HEAL -> { int h = heal(att, 20); if (h > 0) notes.add(Component.text(att.card.name() + " heals " + h + ".", NamedTextColor.GREEN)); }
            case DRAIN -> { if (tgt.mana > 0) { tgt.mana--; displays().refreshHp(tgt); notes.add(Component.text(tgt.card.name() + " loses 1 mana.", NamedTextColor.AQUA)); } }
            case CHARGE -> { att.mana++; displays().refreshHp(att); notes.add(Component.text(att.card.name() + " charges up (◆ " + att.mana + ").", NamedTextColor.AQUA)); }
            case OVERLOAD -> { if (att.mana > 0) { att.mana--; displays().refreshHp(att); notes.add(Component.text(att.card.name() + " burns 1 mana.", NamedTextColor.GRAY)); } }
            case DRAW -> { int n = draw(m, side, 1, true).size(); if (n > 0) notes.add(Component.text(nameOf(m.players[side]) + " draws a card.", NamedTextColor.WHITE)); }
            case DAZE -> {
                if (tgt.hp > 0 && rng.nextBoolean()) { daze(m, tgt); notes.add(Component.text("Heads! " + tgt.card.name() + " is dazed next turn.", NamedTextColor.YELLOW)); }
                else notes.add(Component.text("Tails: no daze.", NamedTextColor.GRAY));
            }
            case SPLASH -> {
                for (var b : m.bench(foe)) {
                    hit(m, b, 10, NamedTextColor.RED);
                    if (b.hp <= 0) Bukkit.getScheduler().runTaskLater(plugin, () -> { if (live(m)) knockOut(m, b, side); }, 16L);
                }
            }
            default -> { }
        }
        displays().refreshHp(tgt);
        if (dmg > 0) {
            displays().hurt(tgt, att, dmg >= 80);
            displays().popup(tgt.base.clone().add(0, 1.05, 0), Component.text("-" + dmg, NamedTextColor.RED).decorate(TextDecoration.BOLD), dmg >= 80 ? 1.3f : 0.9f);
        } else if (mv.damage() > 0) displays().popup(tgt.base.clone().add(0, 1.05, 0), Component.text(missed ? "MISS" : "0", NamedTextColor.GRAY).decorate(TextDecoration.BOLD), 0.9f);
        boolean weak = dmg > 0 && att.card.type().beats(tgt.card.type());
        Component msg = Component.text(att.card.name(), att.card.rarity().color())
                .append(Component.text(" used " + mv.name() + (dmg > 0 || mv.damage() > 0 ? " on " : ""), NamedTextColor.WHITE))
                .append(dmg > 0 || mv.damage() > 0 ? Component.text(tgt.card.name(), tgt.card.rarity().color()) : Component.empty())
                .append(Component.text(dmg > 0 ? " for " + dmg + " damage." : ".", NamedTextColor.WHITE));
        if (weak) msg = msg.append(Component.text(" Weakness +" + rules().weaknessBonus() + "!", NamedTextColor.GREEN));
        tell(m, msg);
        for (Component n : notes) tell(m, n);
        if (recoil > 0) {
            final int back = recoil;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!live(m) || att.ko) return;
                hit(m, att, back, NamedTextColor.LIGHT_PURPLE);
                if (att.hp <= 0) knockOut(m, att, foe);
            }, 14L);
        }
        if (tgt.hp <= 0) Bukkit.getScheduler().runTaskLater(plugin, () -> { if (live(m)) knockOut(m, tgt, side); }, dmg >= 80 ? 20L : 14L);
        if (banish) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!live(m) || att.ko || m.cards[side][att.col] != att || att.hp <= 0) return;
                w.spawnParticle(Particle.REVERSE_PORTAL, att.base.clone().add(0, 0.4, 0), 30, 0.2, 0.3, 0.2, 0.05);
                w.playSound(att.base, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
                displays().remove(att);
                m.cards[side][att.col] = null;
                m.deck[side].addLast(att.source);
                refreshDeck(m, side);
                needActive(m, side);
            }, 22L);
        }
    }

    private void afterAttack(Mat m) {
        long pause = plugin.getConfig().getLong("pause-after-attack-ticks", 30);
        Bukkit.getScheduler().runTaskLater(plugin, () -> nextTurn(m), Math.max(36L, pause));
    }

    private void nextTurn(Mat m) {
        if (m.state != Mat.State.FIGHTING || !mats.contains(m)) return;
        if (checkWin(m)) return;
        // wait for knockouts to finish and for anyone choosing a new Active
        if (m.pendingKo > 0 || m.promoting[0] || m.promoting[1] || m.active(0) == null || m.active(1) == null) {
            for (int s = 0; s < 2; s++) if (!m.promoting[s] && m.pendingKo == 0 && m.active(s) == null) needActive(m, s);
            Bukkit.getScheduler().runTaskLater(plugin, () -> nextTurn(m), 10L);
            return;
        }
        int ended = m.turn;
        for (var c : m.cards[ended]) if (c != null && c.dazed && c.dazedTurn <= m.turns[ended]) { c.dazed = false; displays().refreshHp(c); }
        m.mana[ended] = 0;   // mana that wasn't attached is lost
        m.turn = 1 - m.turn;
        beginTurn(m);
    }

    /** Ends the battle if someone has enough points or has no characters left in play. */
    boolean checkWin(Mat m) {
        if (m.state != Mat.State.FIGHTING) return true;
        for (int s = 0; s < 2; s++) if (m.points[s] >= rules().pointsToWin()) { end(m, s); return true; }
        if (m.pendingKo > 0) return false;
        for (int s = 0; s < 2; s++) if (!m.hasCards(s)) {
            tell(m, Component.text(nameOf(m.players[s]) + " has no characters left in play.", NamedTextColor.RED));
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
