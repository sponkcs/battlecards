package dev.sneakycards;

import java.util.EnumMap;
import java.util.Map;

/** The card an admin is currently building in the menu. */
public final class Draft {
    String name = "", skin = "", collection = "";
    Rarity rarity = Rarity.COMMON;
    Background background = Background.SKYLINE;
    boolean inPacks = true;
    final Map<Stat, Integer> stats = new EnumMap<>(Stat.class);
    final String[] moveNames = {"", ""};
    final Stat[] moveStats = {Stat.STRENGTH, Stat.LUCK};

    Draft() { reset(); }

    void reset() {
        name = ""; skin = ""; collection = ""; rarity = Rarity.COMMON; background = Background.SKYLINE; inPacks = true;
        for (Stat s : Stat.values()) stats.put(s, s.defaultValue());
        moveNames[0] = ""; moveNames[1] = "";
        moveStats[0] = Stat.STRENGTH; moveStats[1] = Stat.LUCK;
    }
}
