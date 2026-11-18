package dev.sneakycards;

import org.bukkit.Location;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Vector;
import java.util.*;

/**
 * One placed game mat: 3 x 3 blocks. Side 0 is the near end (where the placer stood), side 1 the far end.
 * Each side has three character slots in a row: the middle one is the Active card, the outer two are the Bench. The deck pile sits off to the owner's right, nudged towards them,
 * and the discard pile mirrors it on the left. Face-down traps lie in the open middle. Numbers match tools/mats.py.
 */
final class Mat {
    static final double ROW_Z = 0.7;                        // distance of each card row from the centre
    static final double[] COL_X = {-0.62, 0.0, 0.62};       // character slots, from the owner's left
    static final double PILE_X = 1.12, PILE_Z = 0.9;        // deck pile on the owner's right, discard on their left
    static final double STRIP_Z = 0.15;                     // traps, in the middle towards the owner
    static final double[] TRAP_X = {-0.55, 0.55};
    static final int ACTIVE = 1;                            // the middle slot; 0 and 2 are the bench
    static final int[] BENCH = {0, 2};
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
    final boolean[] deckPlaced = new boolean[2], ready = new boolean[2], itemUsed = new boolean[2], retreated = new boolean[2];
    final boolean[] promoting = new boolean[2];   // this side's Active was knocked out and it must pick a new one
    final int[] points = new int[2], bonus = new int[2], turns = new int[2];   // bonus: damage from Whetstone / War Drum this turn
    final int[] mana = new int[2];                // mana still to attach this turn
    int pendingKo = 0;                            // knockout animations still playing (the next turn waits for them)
    int first = 0;                                // the side that took the first turn

    static final class Trap {
        final ItemType type; ItemDisplay display; HandCard source; int spot;
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
    Location deckTop(int side) { return spot(side, PILE_X, PILE_Z); }
    Location discardTop(int side) { return spot(side, -PILE_X, PILE_Z); }
    Location trapSpot(int side, int index) { return spot(side, TRAP_X[index], STRIP_Z); }

    /** Cards face the opposing side. */
    float sideYaw(int side) { return side == 0 ? yaw : yaw + 180f; }

    int sideOf(UUID id) {
        for (int s = 0; s < 2; s++) if (id.equals(players[s])) return s;
        return -1;
    }

    boolean solo() { return Mat.AI.equals(players[0]) || Mat.AI.equals(players[1]); }
    boolean isAi(int side) { return Mat.AI.equals(players[side]); }

    boolean hasCards(int side) { for (var c : cards[side]) if (c != null && !c.ko) return true; return false; }
    int emptySlot(int side) { for (int i = 0; i < 3; i++) if (cards[side][i] == null) return i; return -1; }
    int emptyBench(int side) { for (int b : BENCH) if (cards[side][b] == null) return b; return -1; }
    DisplayManager.Placed active(int side) { var a = cards[side][ACTIVE]; return a == null || a.ko ? null : a; }
    List<DisplayManager.Placed> bench(int side) {
        List<DisplayManager.Placed> out = new ArrayList<>();
        for (int b : BENCH) if (cards[side][b] != null && !cards[side][b].ko) out.add(cards[side][b]);
        return out;
    }

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
            deckPlaced[s] = ready[s] = itemUsed[s] = retreated[s] = promoting[s] = false;
            points[s] = bonus[s] = turns[s] = mana[s] = 0;
        }
        pendingKo = 0; first = 0;
    }
}
