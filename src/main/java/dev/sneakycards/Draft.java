package dev.sneakycards;

/** The card an admin is currently building in the card creator. */
public final class Draft {
    String name = "", skin = "", title = "";
    Rarity rarity = Rarity.COMMON;
    Background background = Background.SKYLINE;
    CardType type = CardType.STRENGTH;
    int hp = 60, retreat = 1;
    boolean inPacks = true;
    boolean slim = false;
    String editing = null;   // id of the card being edited, or null when creating a new one
    long deleteArmedAt = 0;  // delete needs a second click within 5 s
    final String[] moveNames = {"", ""};
    final int[] moveCosts = {1, 2};
    final int[] moveDamage = {20, 50};
    final MoveEffect[] moveEffects = {MoveEffect.NONE, MoveEffect.NONE};

    Draft() { reset(); }

    /** Fills the form from an existing card so it can be edited. */
    void load(Card c) {
        reset();
        editing = c.id();
        name = c.name(); skin = c.skin(); title = c.title();
        rarity = c.rarity(); background = c.background(); inPacks = c.inPacks(); slim = c.slim();
        type = c.type(); hp = c.hp(); retreat = c.retreat();
        for (int i = 0; i < 2 && i < c.moves().size(); i++) {
            Move m = c.moves().get(i);
            moveNames[i] = m.name(); moveCosts[i] = m.cost(); moveDamage[i] = m.damage(); moveEffects[i] = m.effect();
        }
    }

    void reset() {
        name = ""; skin = ""; title = ""; rarity = Rarity.COMMON; background = Background.SKYLINE; inPacks = true; slim = false; editing = null; deleteArmedAt = 0;
        type = CardType.STRENGTH; hp = 60; retreat = 1;
        moveNames[0] = ""; moveNames[1] = "";
        moveCosts[0] = 1; moveCosts[1] = 2; moveDamage[0] = 20; moveDamage[1] = 50;
        moveEffects[0] = MoveEffect.NONE; moveEffects[1] = MoveEffect.NONE;
    }

    /** Pulls Health and damage back inside the caps after the rarity, a cost or an effect changes. */
    void clamp() {
        hp = Math.max(Rules.MIN_HP, Math.min(Rules.maxHp(rarity), hp));
        retreat = Math.max(0, Math.min(Rules.MAX_RETREAT, retreat));
        for (int i = 0; i < 2; i++) {
            moveCosts[i] = Math.max(0, Math.min(Rules.MAX_MOVE_COST, moveCosts[i]));
            moveDamage[i] = Math.max(0, Math.min(Rules.maxDamage(moveCosts[i], rarity, moveEffects[i]), moveDamage[i]));
        }
    }

    Move move(int i) { return new Move(moveNames[i], moveCosts[i], moveDamage[i], moveEffects[i]); }
}
