package dev.sneakycards;

import java.util.EnumMap;
import java.util.Map;

/** The card an admin is currently building in the menu. */
public final class Draft {
    String name = "", skin = "", collection = "";
    Rarity rarity = Rarity.COMMON;
    Background background = Background.SKYLINE;
    boolean inPacks = true;
    boolean slim = false;
    String editing = null;   // id of the card being edited, or null when creating a new one
    long deleteArmedAt = 0;  // delete needs a second click within 5 s
    final Map<Stat, Integer> stats = new EnumMap<>(Stat.class);
    final String[] moveNames = {"", ""};
    final Stat[] moveStats = {Stat.STRENGTH, Stat.LUCK};

    Draft() { reset(); }

    /** Fills the form from an existing card so it can be edited. */
    void load(Card c) {
        reset();
        editing = c.id();
        name = c.name(); skin = c.skin(); collection = c.collection();
        rarity = c.rarity(); background = c.background(); inPacks = c.inPacks(); slim = c.slim();
        for (Stat s : Stat.values()) stats.put(s, c.stat(s));
        for (int i = 0; i < 2 && i < c.moves().size(); i++) { moveNames[i] = c.moves().get(i).name(); moveStats[i] = c.moves().get(i).stat(); }
    }

    void reset() {
        name = ""; skin = ""; collection = ""; rarity = Rarity.COMMON; background = Background.SKYLINE; inPacks = true; slim = false; editing = null; deleteArmedAt = 0;
        for (Stat s : Stat.values()) stats.put(s, s.defaultValue());
        moveNames[0] = ""; moveNames[1] = "";
        moveStats[0] = Stat.STRENGTH; moveStats[1] = Stat.LUCK;
    }
}
