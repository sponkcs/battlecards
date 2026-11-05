package dev.sneakycards;

import org.bukkit.Location;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Vector;
import java.util.*;

/**
 * One placed game mat: 3 x 3 blocks. Side 0 is the near end (where the placer stood), side 1 the far end.
 * Each side has three character slots on its owner's left and a deck slot on their right; the centre strip
 * holds each side's discard pile (in front of its deck) and up to two face-down traps. Numbers match tools/mats.py.
 */
final class Mat {
    static final double ROW_Z = 0.74;                       // distance of each card row from the centre
    static final double[] COL_X = {-1.08, -0.36, 0.36};     // character slots, from the owner's left
    static final double DECK_X = 1.08;                      // deck slot, on the owner's right
    static final double STRIP_Z = 0.16;                     // discard pile and traps, towards the owner
    static final double[] TRAP_X = {-0.95, -0.5};
    static final UUID AI = new UUID(0L, 0x5ca4dL);          // the computer opponent in a solo battle

    enum State { WAITING, SETUP, FIGHTING, ENDED }

    final Location center;                // centre of the mat's top surface
    final float yaw;
    final MatVariant variant;
    ItemDisplay display;
    Interaction core;
    final Interaction[][] slotHits = new Interaction[2][4];   // 0-2 character slots, 3 = deck slot
    final UUID[] players = new UUID[2];
    final DisplayManager.Placed[][] cards = new DisplayManager.Placed[2][3];
    State state = State.WAITING;
    int turn = -1;                        // side whose turn it is
    boolean busy = false;                 // true while an animation plays
    boolean countdown = false;
    int countdownId = 0;

    // deck battle state, per side
    @SuppressWarnings("unchecked") final Deque<HandCard>[] deck = new Deque[]{new ArrayDeque<>(), new ArrayDeque<>()};
    @SuppressWarnings("unchecked") final List<HandCard>[] hand = new List[]{new ArrayList<>(), new ArrayList<>()};
    @SuppressWarnings("unchecked") final List<HandCard>[] discard = new List[]{new ArrayList<>(), new ArrayList<>()};
    @SuppressWarnings("unchecked") final List<Trap>[] traps = new List[]{new ArrayList<>(), new ArrayList<>()};
    final ItemDisplay[] deckDisplay = new ItemDisplay[2], discardDisplay = new ItemDisplay[2];
    final boolean[] deckPlaced = new boolean[2], ready = new boolean[2], playedChar = new boolean[2], itemUsed = new boolean[2];
    final int[] energy = new int[2], points = new int[2], whet = new int[2];
    final boolean[] warDrum = new boolean[2];

    static final class Trap {
        final ItemType type; ItemDisplay display; HandCard source;
        Trap(ItemType type) { this.type = type; }
    }

    Mat(Location center, float yaw, MatVariant variant) {
        this.center = center;
        this.yaw = yaw;
        this.variant = variant;
    }

    Vector forward() { double r = Math.toRadians(yaw); return new Vector(-Math.sin(r), 0, Math.cos(r)); }
    Vector right() { double r = Math.toRadians(yaw); return new Vector(-Math.cos(r), 0, -Math.sin(r)); }

    /** A point on the mat: x along the owner's right, z towards the owner (both in blocks from the centre). */
    private Location spot(int side, double x, double towardOwner) {
        double s = side == 0 ? 1 : -1;
        return center.clone().add(forward().multiply(-towardOwner * s)).add(right().multiply(x * s));
    }

    Location slotTop(int side, int col) { return spot(side, COL_X[col], ROW_Z); }
    Location deckTop(int side) { return spot(side, DECK_X, ROW_Z); }
    Location discardTop(int side) { return spot(side, DECK_X, STRIP_Z); }
    Location trapSpot(int side, int index) { return spot(side, TRAP_X[index], STRIP_Z); }

    /** Cards face the opposing side. */
    float sideYaw(int side) { return side == 0 ? yaw : yaw + 180f; }

    int sideOf(UUID id) {
        for (int s = 0; s < 2; s++) if (id.equals(players[s])) return s;
        return -1;
    }

    boolean solo() { return Mat.AI.equals(players[0]) || Mat.AI.equals(players[1]); }
    boolean isAi(int side) { return Mat.AI.equals(players[side]); }

    boolean hasCards(int side) { for (var c : cards[side]) if (c != null) return true; return false; }
    int emptySlot(int side) { for (int i = 0; i < 3; i++) if (cards[side][i] == null) return i; return -1; }

    /** Has a character anywhere: on the mat, in hand or in the deck. */
    boolean hasCharacters(int side) {
        if (hasCards(side)) return true;
        for (HandCard h : hand[side]) if (h.isChar()) return true;
        for (HandCard h : deck[side]) if (h.isChar()) return true;
        return false;
    }

    void resetBattleState() {
        for (int s = 0; s < 2; s++) {
            deck[s].clear(); hand[s].clear(); discard[s].clear(); traps[s].clear();
            deckPlaced[s] = ready[s] = playedChar[s] = itemUsed[s] = warDrum[s] = false;
            energy[s] = points[s] = whet[s] = 0;
        }
    }
}
