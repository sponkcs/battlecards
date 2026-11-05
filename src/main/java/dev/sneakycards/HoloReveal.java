package dev.sneakycards;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The holo pull moment, seen only by the player who opened the pack: the card rises in front of them spinning,
 * a rainbow ring of sparkles circles it, chimes climb in pitch, then it bursts, shows its name and flies into
 * their inventory. Several holos in one pack play one after another.
 */
final class HoloReveal {
    private static final TextColor HOLO = TextColor.color(0xB8F2FF);
    private static final Map<UUID, Deque<Card>> queue = new HashMap<>();
    private static final Map<UUID, Boolean> playing = new HashMap<>();

    private HoloReveal() { }

    static void play(SneakyCardsPlugin plugin, Player p, Card card) {
        queue.computeIfAbsent(p.getUniqueId(), k -> new ArrayDeque<>()).add(card);
        if (!playing.getOrDefault(p.getUniqueId(), false)) next(plugin, p);
    }

    private static void next(SneakyCardsPlugin plugin, Player p) {
        Deque<Card> q = queue.get(p.getUniqueId());
        Card card = q == null ? null : q.poll();
        if (card == null || !p.isOnline()) { playing.remove(p.getUniqueId()); queue.remove(p.getUniqueId()); return; }
        playing.put(p.getUniqueId(), true);

        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().setY(0);
        if (dir.lengthSquared() < 1e-6) dir = new Vector(0, 0, 1);
        dir.normalize();
        final Vector toward = dir.clone();
        Location spot = eye.clone().add(dir.clone().multiply(1.6)).add(0, -0.15, 0);
        spot.setYaw(eye.getYaw()); spot.setPitch(0);
        ItemDisplay d = p.getWorld().spawn(spot, ItemDisplay.class, e -> {
            e.setItemStack(plugin.items().card(card, true));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            e.setBillboard(Display.Billboard.FIXED);
            e.setVisibleByDefault(false);
            e.setPersistent(false);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setTransformation(tf(0f, 0.01f, 0f));
        });
        p.showEntity(plugin, d);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 1.8f);

        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!p.isOnline() || !d.isValid()) { d.remove(); cancel(); next(plugin, p); return; }
                Location c = d.getLocation().add(0, 0.05, 0);
                if (t <= 40) {                                         // rise and spin, slowing down
                    double f = t / 40.0, ease = 1 - Math.pow(1 - f, 3);
                    float angle = (float) Math.toRadians(720 * ease);
                    float scale = (float) Math.min(1.0, 0.15 + f * 1.6);
                    d.setInterpolationDelay(0); d.setInterpolationDuration(2);
                    d.setTransformation(tf(angle, scale, (float) (0.25 * ease)));
                    for (int i = 0; i < 3; i++) {                      // rainbow ring of sparkles
                        double a = t * 0.35 + i * (Math.PI * 2 / 3);
                        java.awt.Color rgb = java.awt.Color.getHSBColor((float) ((t * 0.02 + i / 3.0) % 1.0), 0.6f, 1f);
                        p.spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * 0.65, 0.25 * ease + Math.sin(t * 0.3 + i) * 0.25, Math.sin(a) * 0.65),
                                1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(rgb.getRed(), rgb.getGreen(), rgb.getBlue()), 0.9f));
                    }
                    if (t % 8 == 0) p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.7f + t / 40f);
                }
                if (t == 40) {                                         // the burst
                    p.spawnParticle(Particle.FIREWORK, c.clone().add(0, 0.25, 0), 40, 0.1, 0.1, 0.1, 0.12);
                    p.spawnParticle(Particle.END_ROD, c.clone().add(0, 0.25, 0), 20, 0.3, 0.3, 0.3, 0.04);
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.4f);
                    p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1f, 1.2f);
                    p.showTitle(Title.title(Component.text("✦ HOLOGRAPHIC ✦", HOLO).decorate(TextDecoration.BOLD),
                            Component.text(card.name(), card.rarity().color()).append(Component.text("  " + card.rarity().label, HOLO)),
                            Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1600), Duration.ofMillis(400))));
                }
                if (t > 40 && t <= 70) {                               // hold: gentle bob and a few drifting sparkles
                    float bob = (float) (0.25 + Math.sin((t - 40) * 0.2) * 0.04);
                    d.setInterpolationDelay(0); d.setInterpolationDuration(2);
                    d.setTransformation(tf((float) Math.toRadians(Math.sin((t - 40) * 0.12) * 10), 1f, bob));
                    if (t % 3 == 0) p.spawnParticle(Particle.WAX_ON, c.clone().add(0, 0.25, 0), 1, 0.35, 0.4, 0.35, 0);
                }
                if (t > 70 && t <= 80) {                               // shrink and fly to the player
                    double f = (t - 70) / 10.0;
                    d.setInterpolationDelay(0); d.setInterpolationDuration(2);
                    d.setTransformation(tf(0f, (float) Math.max(0.05, 1 - f), (float) (0.25 - f * 0.6)));
                    d.teleport(d.getLocation().add(toward.clone().multiply(-0.12)));
                }
                if (t == 80) p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
                if (t++ > 82) { d.remove(); cancel(); next(plugin, p); }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static Transformation tf(float yRot, float scale, float lift) {
        return new Transformation(new Vector3f(0, lift, 0), new AxisAngle4f(yRot, 0, 1, 0), new Vector3f(scale, scale, scale), new AxisAngle4f());
    }
}
