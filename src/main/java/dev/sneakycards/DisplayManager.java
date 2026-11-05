package dev.sneakycards;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Cards in play. Each placed card is an ItemDisplay lying flat, a Mannequin hovering above it
 * (skin + outer layers), an Interaction hitbox, a health label, and a TextDisplay preview that only
 * the player looking at the card can see. Also plays the move animations and damage popups.
 */
public final class DisplayManager {
    enum Anim { NONE, LUNGE, DASH, SPIN, BOUNCE, HURT }

    static final class Placed {
        final UUID owner; final Card card; final Location base; final float yaw;
        ItemDisplay face; Interaction hit; Mannequin man; TextDisplay preview, hpText;
        Anim anim = Anim.NONE; int animTick = 0; long age = 0;
        // battle state
        int hp, maxHp; boolean ko = false; boolean acted = false; boolean faceDown = false; boolean stunned = false;   // acted: already used its action this round
        Mat mat; int side = -1, col = -1;
        // attack animation state
        Vector dir; double reach = 1.0; Placed target; Runnable onImpact, onDone;
        boolean bigHit = false;   // the hurt animation is for a critical hit
        Placed(UUID owner, Card card, Location base, float yaw) { this.owner = owner; this.card = card; this.base = base; this.yaw = yaw; }
    }

    private final SneakyCardsPlugin plugin;
    private final List<Placed> placed = new ArrayList<>();
    private final Map<UUID, Placed> byEntity = new HashMap<>();
    private final Map<UUID, Placed> viewing = new HashMap<>();
    private int ticks = 0;

    DisplayManager(SneakyCardsPlugin plugin) { this.plugin = plugin; }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tick();
            if (++ticks % 4 == 0) updatePreviews();
        }, 1L, 1L);
    }

    // ---- placing and removing ----

    public Placed place(Player owner, Card card, Location top, float yaw) { return place(owner, card, top, yaw, false); }

    /** faceDown: shows the card back and no mannequin until {@link #reveal} is called (used on game mats). */
    public Placed place(Player owner, Card card, Location top, float yaw, boolean faceDown) {
        int max = plugin.getConfig().getInt("max-cards-per-player", 3);
        long mine = placed.stream().filter(p -> p.owner.equals(owner.getUniqueId())).count();
        if (mine >= max) {
            owner.sendMessage(Component.text("You can only have " + max + " cards in play.", NamedTextColor.RED));
            return null;
        }
        World w = top.getWorld();
        Placed p = new Placed(owner.getUniqueId(), card, top.clone(), yaw);
        p.maxHp = card.stat(Stat.HEALTH);
        p.hp = p.maxHp;
        p.faceDown = faceDown;
        ItemStack item = faceDown ? plugin.items().cardBack() : plugin.items().card(card);
        double hover = hover(), scale = scale(), headTop = hover + 1.85 * scale;

        // the mat surface is about 0.063 above the block top, so the card sits just above it
        p.face = w.spawn(top.clone().add(0, 0.075, 0), ItemDisplay.class, d -> {
            d.setItemStack(item);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setRotation(yaw, 0);
            // Lays the card flat. Tune angle/scale on your server if it looks mirrored.
            d.setTransformation(flat(0f, 1f));
            d.setPersistent(false);
        });
        p.hit = w.spawn(top.clone(), Interaction.class, i -> {
            i.setInteractionWidth(0.7f);
            i.setInteractionHeight((float) (headTop + 0.1));
            i.setResponsive(true);
            i.setPersistent(false);
        });
        if (!faceDown) spawnMannequin(p);
        p.hpText = w.spawn(top.clone().add(0, headTop + 0.18, 0), TextDisplay.class, t -> {
            t.text(hpLine(p));
            t.setBillboard(Display.Billboard.CENTER);
            t.setBackgroundColor(Color.fromARGB(120, 10, 10, 16));
            t.setPersistent(false);
            t.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.35f, 0.35f, 0.35f), new AxisAngle4f()));
        });
        p.preview = w.spawn(top.clone().add(0, headTop + 0.55, 0), TextDisplay.class, t -> {
            t.text(previewText(card));
            t.setBillboard(Display.Billboard.CENTER);
            t.setBackgroundColor(Color.fromARGB(170, 15, 15, 20));
            t.setLineWidth(240);
            t.setVisibleByDefault(false);
            t.setPersistent(false);
            t.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.5f, 0.5f, 0.5f), new AxisAngle4f()));
        });
        placed.add(p);
        byEntity.put(p.hit.getUniqueId(), p);
        return p;
    }

    /** The card lying flat on the mat. lift raises it (blocks); width 1 = normal, near 0 = edge-on (mid-flip). */
    private static Transformation flat(float lift, float width) {
        // ItemDisplay turns its item 180 degrees around Y, so +90 about X puts the card's front face up
        // with its top pointing away from the owner (readable from their seat).
        return new Transformation(new Vector3f(0, lift, 0), new AxisAngle4f((float) Math.toRadians(90), 1, 0, 0),
                new Vector3f(1.08f * width, 1.08f, 1.08f), new AxisAngle4f());
    }

    private void spawnMannequin(Placed p) {
        World w = p.base.getWorld();
        ResolvableProfile profile = plugin.items().profile(p.card);
        double scale = scale();
        Location manLoc = p.base.clone().add(0, hover(), 0);
        manLoc.setYaw(p.yaw);
        p.man = w.spawn(manLoc, Mannequin.class, m -> {
            m.setProfile(profile);
            m.setImmovable(true);
            m.setGravity(false);
            m.setInvulnerable(true);
            m.setSilent(true);
            m.setPersistent(false);
            m.setDescription(null);
            m.setCustomNameVisible(false);
            AttributeInstance sc = m.getAttribute(Attribute.SCALE);
            if (sc != null) sc.setBaseValue(scale);
        });
        byEntity.put(p.man.getUniqueId(), p);
    }

    /**
     * Flips a face-down card: it lifts and turns edge-on, swaps to the real card, settles back down, then the
     * mannequin appears. Epic and higher get a rarity flair.
     */
    public void reveal(Placed p) {
        if (!p.faceDown || p.face == null || !p.face.isValid()) return;
        p.faceDown = false;
        World w = p.base.getWorld();
        p.face.setInterpolationDelay(0);
        p.face.setInterpolationDuration(4);
        p.face.setTransformation(flat(0.25f, 0.02f));
        w.playSound(p.base, Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.8f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.face == null || !p.face.isValid()) return;
            p.face.setItemStack(plugin.items().card(p.card));
            p.face.setInterpolationDelay(0);
            p.face.setInterpolationDuration(5);
            p.face.setTransformation(flat(0f, 1f));
            refreshHp(p);
        }, 4L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!placed.contains(p) || p.ko) return;
            spawnMannequin(p);
            w.spawnParticle(Particle.POOF, p.base.clone().add(0, hover() + 0.3, 0), 8, 0.1, 0.15, 0.1, 0.02);
            flair(p);
        }, 9L);
    }

    /** Particle flair for Epic (purple swirl), Legendary (golden column) and Mythic (pink helix and burst). */
    private void flair(Placed p) {
        Rarity r = p.card.rarity();
        if (r.ordinal() < Rarity.EPIC.ordinal()) return;
        World w = p.base.getWorld();
        Location c = p.base.clone().add(0, 0.1, 0);
        int rgb = r.color.value();
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(rgb), r == Rarity.MYTHIC ? 1.1f : 0.8f);
        switch (r) {
            case EPIC -> w.playSound(c, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.2f);
            case LEGENDARY -> { w.playSound(c, Sound.BLOCK_BELL_USE, 0.8f, 1.4f); w.playSound(c, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f); }
            default -> { w.playSound(c, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.6f); w.playSound(c, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1f, 1.2f); }
        }
        final int len = r == Rarity.EPIC ? 20 : r == Rarity.LEGENDARY ? 26 : 34;
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t++ >= len || !placed.contains(p)) { cancel(); return; }
                double f = t / (double) len;
                switch (r) {
                    case EPIC -> {                                   // a purple ring that spirals up around the card
                        for (int i = 0; i < 2; i++) {
                            double a = t * 0.6 + i * Math.PI;
                            w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * 0.35, f * 0.9, Math.sin(a) * 0.35), 1, 0, 0, 0, 0, dust);
                        }
                        if (t % 4 == 0) w.spawnParticle(Particle.WITCH, c.clone().add(0, f * 0.9, 0), 2, 0.15, 0.1, 0.15, 0);
                    }
                    case LEGENDARY -> {                              // a golden column with falling sparks
                        for (int i = 0; i < 3; i++) {
                            double a = t * 0.5 + i * (Math.PI * 2 / 3);
                            w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * 0.25, f * 1.2, Math.sin(a) * 0.25), 1, 0, 0, 0, 0, dust);
                        }
                        if (t % 3 == 0) w.spawnParticle(Particle.WAX_ON, c.clone().add(0, 1.0, 0), 3, 0.25, 0.1, 0.25, 0);
                        if (t == len - 1) w.spawnParticle(Particle.FIREWORK, c.clone().add(0, 1.1, 0), 18, 0.1, 0.1, 0.1, 0.08);
                    }
                    default -> {                                     // Mythic: double pink helix, end-rod sparkles, then a burst
                        for (int i = 0; i < 2; i++) {
                            double a = t * 0.45 + i * Math.PI, rad = 0.45 - f * 0.2;
                            w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * rad, f * 1.4, Math.sin(a) * rad), 2, 0.01, 0.01, 0.01, 0, dust);
                        }
                        if (t % 2 == 0) w.spawnParticle(Particle.END_ROD, c.clone().add(0, f * 1.4, 0), 1, 0.2, 0.05, 0.2, 0.01);
                        if (t == len - 1) {
                            w.spawnParticle(Particle.FIREWORK, c.clone().add(0, 1.3, 0), 30, 0.1, 0.1, 0.1, 0.12);
                            w.spawnParticle(Particle.TOTEM_OF_UNDYING, c.clone().add(0, 1.0, 0), 25, 0.2, 0.3, 0.2, 0.25);
                            w.playSound(c, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 1.3f);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private double hover() { return plugin.getConfig().getDouble("mannequin-hover", 0.3); }
    private double scale() { return plugin.getConfig().getDouble("mannequin-size", 0.28); }
    /** Animation distances were tuned for a 0.45 mannequin; shrink them with the mannequin. */
    private double k() { return scale() / 0.45; }

    private Component previewText(Card c) {
        var b = Component.text();
        b.append(Component.text(c.name(), c.rarity().color()).decorate(TextDecoration.BOLD)).appendNewline();
        b.append(Component.text(c.rarity().label + "  ", c.rarity().color()))
                .append(Component.text(c.title(), NamedTextColor.GRAY).decorate(TextDecoration.ITALIC)).appendNewline();
        b.append(Component.text(c.background().label, NamedTextColor.DARK_GRAY)).appendNewline();
        for (Stat s : Stat.values()) b.append(Component.text(s.label + " " + c.stat(s), NamedTextColor.WHITE)).appendNewline();
        for (Move m : c.moves())
            b.append(Component.text(m.name() + "  " + m.stat().label + " " + c.power(m), NamedTextColor.YELLOW)).appendNewline();
        return b.build();
    }

    /** Small label above the mannequin: just the health number (the name is in the look-at preview). */
    private Component hpLine(Placed p) {
        if (p.faceDown) return Component.text("?", NamedTextColor.GRAY);
        if (p.ko) return Component.text("KO", NamedTextColor.RED);
        double r = p.hp / (double) Math.max(1, p.maxHp);
        NamedTextColor c = r > 0.5 ? NamedTextColor.GREEN : r > 0.25 ? NamedTextColor.YELLOW : NamedTextColor.RED;
        return Component.text("❤ " + p.hp, c);
    }

    public void refreshHp(Placed p) { if (p.hpText != null && p.hpText.isValid()) p.hpText.text(hpLine(p)); }

    public Placed byEntity(UUID id) { return byEntity.get(id); }

    public void remove(Placed p) {
        p.onImpact = null; p.onDone = null;
        for (Entity e : new Entity[]{p.face, p.hit, p.man, p.preview, p.hpText}) if (e != null) { byEntity.remove(e.getUniqueId()); e.remove(); }
        viewing.values().removeIf(v -> v == p);
        placed.remove(p);
    }

    public void clearOwner(UUID owner) { new ArrayList<>(placed).stream().filter(p -> p.owner.equals(owner)).forEach(this::remove); }
    public void clearAll() { new ArrayList<>(placed).forEach(this::remove); }

    public Placed nearestOwned(Player pl) {
        Placed best = null; double bd = 100;
        for (Placed p : placed) {
            if (!p.owner.equals(pl.getUniqueId()) || !p.base.getWorld().equals(pl.getWorld())) continue;
            double d = p.base.distanceSquared(pl.getLocation());
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    // ---- battle effects ----

    /** Makes the attacker play its move toward the target. onImpact runs at the hit frame, onDone when the animation ends. */
    public void attack(Placed att, Placed tgt, Move move, Runnable onImpact, Runnable onDone) {
        Vector to = tgt.base.toVector().subtract(att.base.toVector());
        to.setY(0);
        double dist = Math.max(0.01, to.length());
        att.dir = to.normalize();
        att.reach = Math.max(0.5, dist * 0.45 / (1.1 * k()));
        att.target = tgt;
        att.onImpact = onImpact;
        att.onDone = onDone;
        att.anim = animFor(move);
        att.animTick = 0;
    }

    private static Anim animFor(Move move) {
        return switch (move.stat()) {
            case STRENGTH -> Anim.LUNGE;
            case DEXTERITY -> Anim.DASH;
            case MAGICING -> Anim.SPIN;
            default -> Anim.BOUNCE;
        };
    }

    /** Plain animation without a target (used by /cardadmin move). */
    public void play(Placed p, Move move) {
        p.anim = animFor(move);
        p.animTick = 0;
        Player owner = Bukkit.getPlayer(p.owner);
        if (owner != null) owner.sendActionBar(Component.text(p.card.name() + " uses " + move.name() + "!", p.card.rarity().color()));
    }

    public void hurt(Placed p) { hurt(p, null, false); }

    /** Knocks the target back (away from the attacker), flashes it red and shakes it. A crit hits harder. */
    public void hurt(Placed p, Placed from, boolean crit) {
        Vector push = from == null ? forward(p.yaw).multiply(-1) : p.base.toVector().subtract(from.base.toVector()).setY(0);
        if (push.lengthSquared() < 1e-6) push = forward(p.yaw).multiply(-1);
        p.dir = push.normalize();
        p.bigHit = crit;
        p.anim = Anim.HURT; p.animTick = 0;
        p.onImpact = null; p.onDone = null; p.target = null;
    }

    public void knockOut(Placed p) {
        p.ko = true;
        World w = p.base.getWorld();
        Location l = p.base.clone().add(0, hover() + 0.5 * scale(), 0);
        w.spawnParticle(Particle.LARGE_SMOKE, l, 18, 0.2, 0.3, 0.2, 0.02);
        w.spawnParticle(Particle.POOF, l, 20, 0.2, 0.3, 0.2, 0.05);
        w.playSound(l, Sound.ENTITY_PLAYER_DEATH, 0.7f, 1.2f);
        if (p.man != null) { byEntity.remove(p.man.getUniqueId()); p.man.remove(); p.man = null; }
        if (p.face != null) p.face.setBrightness(new Display.Brightness(1, 1));
        p.anim = Anim.NONE;
        refreshHp(p);
    }

    /** Floating number that rises and fades away. */
    public void popup(Location at, Component text) { popup(at, text, 1f); }

    public void popup(Location at, Component text, float size) {
        TextDisplay t = at.getWorld().spawn(at, TextDisplay.class, d -> {
            d.text(text);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(size, size, size), new AxisAngle4f()));
            d.setBillboard(Display.Billboard.CENTER);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setSeeThrough(true);
            d.setPersistent(false);
        });
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (++n > 30 || !t.isValid()) { t.remove(); cancel(); return; }
                t.teleport(t.getLocation().add(0, n < 6 ? 0.06 : 0.02, 0));
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ---- animation loop ----

    private static Vector forward(float yaw) {
        double r = Math.toRadians(yaw);
        return new Vector(-Math.sin(r), 0, Math.cos(r));
    }

    private static int hitFrame(Anim a) {
        return switch (a) { case LUNGE -> 9; case DASH -> 7; case SPIN -> 12; case BOUNCE -> 10; default -> -1; };
    }

    private void tick() {
        double hover = hover(), k = k();
        for (Placed p : new ArrayList<>(placed)) {
            if (p.man == null || !p.man.isValid()) continue;
            p.age++;
            double up = hover + Math.sin(p.age * 0.12) * 0.04 * k, fwd = 0, side = 0;
            float yawAdd = 0;
            int a = p.animTick;
            if (p.anim != Anim.NONE) {
                boolean done = false;
                switch (p.anim) {
                    case LUNGE -> { fwd = (a < 5 ? -0.25 * a / 5.0 : a < 9 ? -0.25 + 1.35 * (a - 5) / 4.0 : 1.1 * (1 - (a - 9) / 7.0)) * p.reach * k; done = a >= 16; }
                    case DASH -> { fwd = Math.sin(a / 14.0 * Math.PI) * 0.7 * Math.min(p.reach, 1.6) * k; side = Math.sin(a / 14.0 * Math.PI * 2) * 0.6 * k; done = a >= 14; }
                    case SPIN -> { up += Math.sin(a / 24.0 * Math.PI) * 0.7 * k; yawAdd = a * 30f; done = a >= 24; }
                    case BOUNCE -> { up += Math.abs(Math.sin(a / 20.0 * Math.PI * 2)) * 0.5 * k; done = a >= 20; }
                    case HURT -> {
                        // snap back, hop, wobble, then settle (crits hit harder and last longer)
                        int len = p.bigHit ? 24 : 18;
                        double power = p.bigHit ? 1.7 : 1.0, fade = Math.max(0, 1 - a / (double) len);
                        fwd = (a < 3 ? 0.32 * a / 3.0 : 0.32 * fade * fade) * power * k;
                        if (a < 7) up += Math.sin(a / 7.0 * Math.PI) * 0.16 * power * k;
                        side = Math.sin(a * 2.6) * 0.07 * power * k * fade;
                        yawAdd = (float) (Math.sin(a * 2.1) * 28 * power * fade);
                        done = a >= len;
                    }
                    default -> { }
                }
                p.animTick++;
                Vector f = p.dir != null ? p.dir : forward(p.yaw), r = new Vector(-f.getZ(), 0, f.getX());
                Location l = p.base.clone().add(f.clone().multiply(fwd)).add(r.multiply(side)).add(0, up, 0);
                l.setYaw(p.yaw + yawAdd); l.setPitch(0);
                p.man.teleport(l);
                effects(p, l, f, a);
                if (p.onImpact != null && a == hitFrame(p.anim)) { Runnable run = p.onImpact; p.onImpact = null; run.run(); }
                if (done) {
                    p.anim = Anim.NONE; p.dir = null; p.reach = 1.0; p.target = null;
                    Runnable run = p.onDone; p.onDone = null;
                    if (run != null) run.run();
                }
            } else if (p.age % 2 == 0) {
                Location l = p.base.clone().add(0, up, 0);
                l.setYaw(p.yaw); l.setPitch(0);
                p.man.teleport(l);
            }
        }
    }

    private void beam(Location from, Location to, Particle particle) {
        World w = from.getWorld();
        for (int i = 0; i <= 10; i++) {
            double t = i / 10.0;
            w.spawnParticle(particle, from.clone().add(to.clone().subtract(from).toVector().multiply(t)), 1, 0, 0, 0, 0);
        }
    }

    private void effects(Placed p, Location l, Vector f, int a) {
        World w = l.getWorld();
        double mid = hover() + 0.9 * scale();
        Location at = p.target != null ? p.target.base.clone().add(0, mid, 0) : l.clone().add(f.clone().multiply(0.4)).add(0, 0.9 * scale(), 0);
        switch (p.anim) {
            case LUNGE -> {
                if (a == 9) {
                    w.spawnParticle(Particle.SWEEP_ATTACK, at, 1);
                    w.spawnParticle(Particle.CRIT, at, 12, 0.2, 0.2, 0.2, 0.2);
                    w.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1f);
                }
            }
            case DASH -> {
                if (a % 2 == 0) w.spawnParticle(Particle.CLOUD, l.clone().add(0, 0.1, 0), 3, 0.1, 0.05, 0.1, 0.01);
                if (a == 7) {
                    w.spawnParticle(Particle.CRIT, at, 14, 0.2, 0.2, 0.2, 0.25);
                    w.playSound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.3f);
                }
            }
            case SPIN -> {
                w.spawnParticle(Particle.ENCHANT, l.clone().add(0, 0.9 * scale(), 0), 6, 0.3, 0.35, 0.3, 0.3);
                if (a == 12) {
                    beam(l.clone().add(0, 0.9 * scale(), 0), at, Particle.END_ROD);
                    w.spawnParticle(Particle.END_ROD, at, 15, 0.2, 0.3, 0.2, 0.05);
                    w.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
                }
            }
            case BOUNCE -> {
                if (a == 5) w.spawnParticle(Particle.HAPPY_VILLAGER, l.clone().add(0, 0.9 * scale(), 0), 8, 0.25, 0.25, 0.25, 0);
                if (a == 10) {
                    beam(l.clone().add(0, 0.9 * scale(), 0), at, Particle.HAPPY_VILLAGER);
                    w.spawnParticle(Particle.HAPPY_VILLAGER, at, 12, 0.25, 0.25, 0.25, 0);
                    w.playSound(at, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.8f);
                }
            }
            case HURT -> {
                double body = 0.9 * scale();
                Location c = l.clone().add(0, body, 0);
                if (a == 0) {
                    if (p.man != null) p.man.playHurtAnimation(0f);           // red flash on the mannequin
                    if (p.face != null) { p.face.setGlowColorOverride(Color.RED); p.face.setGlowing(true); }
                    if (p.hpText != null) p.hpText.setBackgroundColor(Color.fromARGB(190, 170, 20, 20));
                    w.spawnParticle(Particle.DAMAGE_INDICATOR, c, p.bigHit ? 14 : 7, 0.15, 0.2, 0.15, 0.15);
                    w.spawnParticle(Particle.CRIT, c, p.bigHit ? 24 : 10, 0.15, 0.2, 0.15, 0.35);
                    w.playSound(l, Sound.ENTITY_PLAYER_HURT, 1f, p.bigHit ? 0.8f : 1.1f);
                    if (p.bigHit) {
                        w.spawnParticle(Particle.EXPLOSION, c, 1);
                        w.playSound(l, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.7f);
                        w.playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 0.35f, 1.6f);
                    }
                }
                if (a == 3) w.playSound(l, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.8f, 1.2f);
                if (a > 0 && a < 8 && a % 2 == 0) w.spawnParticle(Particle.SMOKE, l.clone().add(0, 0.05, 0), 2, 0.08, 0, 0.08, 0.01);
                if (a == 4 && p.bigHit && p.man != null) p.man.playHurtAnimation(0f);
                if (a == 8) {
                    if (p.face != null) p.face.setGlowing(false);
                    if (p.hpText != null) p.hpText.setBackgroundColor(Color.fromARGB(120, 10, 10, 16));
                }
            }
            default -> { }
        }
    }

    // ---- look-at preview ----

    private void updatePreviews() {
        if (placed.isEmpty() && viewing.isEmpty()) return;               // nothing to look at: skip the ray casts
        for (Player pl : Bukkit.getOnlinePlayers()) {
            Entity e = pl.getTargetEntity(6);
            Placed want = e == null ? null : byEntity.get(e.getUniqueId());
            if (want != null && want.faceDown && !want.owner.equals(pl.getUniqueId())) want = null;   // only the owner may peek
            Placed cur = viewing.get(pl.getUniqueId());
            if (cur == want) continue;
            if (cur != null && cur.preview != null) pl.hideEntity(plugin, cur.preview);
            if (want != null && want.preview != null) { pl.showEntity(plugin, want.preview); viewing.put(pl.getUniqueId(), want); }
            else viewing.remove(pl.getUniqueId());
        }
    }
}
