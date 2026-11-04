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
        String id = plugin.items().cardId(hand);
        Card card = id == null ? null : plugin.registry().get(id);
        if (card == null) { say(p, "Hold a card and right-click a slot to play it.", NamedTextColor.YELLOW); return; }
        UUID me = p.getUniqueId();
        Mat other = byPlayer.get(me);
        if (other != null && other != m) { say(p, "You are already playing on another mat.", NamedTextColor.RED); return; }
        if (m.players[side] != null && !m.players[side].equals(me)) { say(p, nameOf(m.players[side]) + " is playing on this side.", NamedTextColor.RED); return; }
        if (m.players[1 - side] != null && m.players[1 - side].equals(me)) { say(p, "You are already playing on the other side.", NamedTextColor.RED); return; }
        if (m.cards[side][col] != null) { say(p, "That slot is taken. Sneak + right-click a card to pick it up.", NamedTextColor.RED); return; }

        boolean claimed = m.players[side] == null;
        DisplayManager.Placed pl = displays().place(p, card, m.slotTop(side, col), m.sideYaw(side));
        if (pl == null) return;
        if (claimed) { m.players[side] = me; byPlayer.put(me, m); }
        pl.mat = m; pl.side = side; pl.col = col;
        m.cards[side][col] = pl;
        if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);

        if (m.full(0) && m.full(1)) { start(m); return; }
        if (m.full(side)) {
            say(p, "Your team is ready. Waiting for an opponent to fill the other side.", NamedTextColor.GREEN);
            if (m.players[1 - side] != null) say(Bukkit.getPlayer(m.players[1 - side]), nameOf(me) + "'s team is ready.", NamedTextColor.GRAY);
        } else {
            int have = 0; for (var c : m.cards[side]) if (c != null) have++;
            say(p, have + " of 3 cards placed.", NamedTextColor.GRAY);
        }
    }

    public void pickUpCard(Player p, DisplayManager.Placed pl) {
        Mat m = pl.mat;
        if (m == null || m.state != Mat.State.WAITING || !pl.owner.equals(p.getUniqueId())) return;
        displays().remove(pl);
        m.cards[pl.side][pl.col] = null;
        giveBack(pl.owner, pl.card);
        if (!m.hasCards(pl.side)) releaseSide(m, pl.side);
    }

    private void releaseSide(Mat m, int side) {
        UUID id = m.players[side];
        if (id != null) byPlayer.remove(id);
        m.players[side] = null;
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
        m.players[0] = null; m.players[1] = null;
        m.state = Mat.State.WAITING;
        m.turn = -1;
        m.busy = false;
    }

    // ---------------------------------------------------------------- battle flow

    private int dexterity(Mat m, int side) {
        int sum = 0;
        for (var c : m.cards[side]) if (c != null) sum += c.card.stat(Stat.DEXTERITY);
        return sum;
    }

    private void start(Mat m) {
        m.state = Mat.State.FIGHTING;
        int d0 = dexterity(m, 0), d1 = dexterity(m, 1);
        int first;
        boolean coin = false;
        if (d0 > d1) first = 0;
        else if (d1 > d0) first = 1;
        else { first = rng.nextBoolean() ? 0 : 1; coin = true; }
        m.turn = first;
        String who = nameOf(m.players[first]);
        Component line = coin
                ? Component.text("Dexterity is tied at " + d0 + ". Coin flip: " + who + " goes first.", NamedTextColor.GOLD)
                : Component.text(who + " goes first (Dexterity " + (first == 0 ? d0 + " vs " + d1 : d1 + " vs " + d0) + ").", NamedTextColor.GOLD);
        tell(m, line);
        Title t = Title.title(Component.text("Battle!", NamedTextColor.RED).decorate(TextDecoration.BOLD), Component.text(who + " goes first", NamedTextColor.GOLD),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(400)));
        for (UUID id : m.players) { Player p = id == null ? null : Bukkit.getPlayer(id); if (p != null) p.showTitle(t); }
        for (int s = 0; s < 2; s++) for (var c : m.cards[s]) if (c != null) m.center.getWorld().spawnParticle(Particle.CRIT, c.base.clone().add(0, 0.5, 0), 10, 0.2, 0.2, 0.2, 0.1);
        m.busy = true;
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (m.state == Mat.State.FIGHTING && mats.contains(m)) beginTurn(m); }, 50L);
    }

    private void beginTurn(Mat m) {
        m.busy = false;
        final int side = m.turn;
        Player cur = Bukkit.getPlayer(m.players[side]);
        Player other = m.players[1 - side] == null ? null : Bukkit.getPlayer(m.players[1 - side]);
        if (cur != null) {
            say(cur, "Your turn! Choose a card, a move and a target. (Right-click your card or use /sneakycards turn to open the menu.)", NamedTextColor.GREEN);
            cur.showTitle(Title.title(Component.empty(), Component.text("Your turn", NamedTextColor.GREEN),
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
        m.busy = true;
        boolean crit = rng.nextInt(100) < Math.min(100, att.card.stat(Stat.LUCK));
        int dmg = att.card.power(mv) * (crit ? 2 : 1);
        displays().attack(att, tgt, mv, () -> impact(m, att, tgt, mv, dmg, crit), () -> afterAttack(m));
    }

    private void impact(Mat m, DisplayManager.Placed att, DisplayManager.Placed tgt, Move mv, int dmg, boolean crit) {
        if (m.state != Mat.State.FIGHTING) return;
        tgt.hp = Math.max(0, tgt.hp - dmg);
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
        tell(m, msg);
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
        Bukkit.getScheduler().runTaskLater(plugin, () -> nextTurn(m), Math.max(22L, pause));
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
