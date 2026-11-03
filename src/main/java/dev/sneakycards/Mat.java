package dev.sneakycards;

import org.bukkit.Location;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Vector;
import java.util.UUID;

/**
 * One placed game mat: 5 blocks wide, 7 blocks long. Side 0 is the near end (where the placer stood),
 * side 1 the far end. Each side has three card slots. Numbers match tools/mats.py.
 */
final class Mat {
    static final double ROW_Z = 2.2;      // distance of each card row from the centre
    static final double COL_SPACING = 1.5;

    enum State { WAITING, FIGHTING, ENDED }

    final Location center;                // centre of the mat's top surface
    final float yaw;
    final MatVariant variant;
    ItemDisplay display;
    Interaction core;
    final Interaction[][] slotHits = new Interaction[2][3];
    final UUID[] players = new UUID[2];
    final DisplayManager.Placed[][] cards = new DisplayManager.Placed[2][3];
    State state = State.WAITING;
    int turn = -1;                        // side whose turn it is
    boolean busy = false;                 // true while an attack animation plays

    Mat(Location center, float yaw, MatVariant variant) {
        this.center = center;
        this.yaw = yaw;
        this.variant = variant;
    }

    Vector forward() { double r = Math.toRadians(yaw); return new Vector(-Math.sin(r), 0, Math.cos(r)); }
    Vector right() { double r = Math.toRadians(yaw); return new Vector(-Math.cos(r), 0, -Math.sin(r)); }

    /** The point on the mat surface where a card for this side and column sits. */
    Location slotTop(int side, int col) {
        double z = side == 0 ? -ROW_Z : ROW_Z;
        double x = (col - 1) * COL_SPACING * (side == 0 ? 1 : -1);
        return center.clone().add(forward().multiply(z)).add(right().multiply(x));
    }

    /** Cards face the opposing side. */
    float sideYaw(int side) { return side == 0 ? yaw : yaw + 180f; }

    int sideOf(UUID id) {
        for (int s = 0; s < 2; s++) if (id.equals(players[s])) return s;
        return -1;
    }

    boolean full(int side) { for (var c : cards[side]) if (c == null) return false; return true; }
    boolean hasCards(int side) { for (var c : cards[side]) if (c != null) return true; return false; }
    boolean allKnockedOut(int side) { for (var c : cards[side]) if (c != null && !c.ko) return false; return true; }
}
