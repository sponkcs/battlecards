package dev.sneakycards;

import org.bukkit.configuration.ConfigurationSection;
import java.util.*;
import java.util.regex.Pattern;

/**
 * A character card: a type, Health, a retreat cost and two moves (each with a mana cost, damage and an optional effect).
 */
public record Card(String id, String name, String skin, String title, Rarity rarity, Background background,
                   CardType type, int hp, int retreat, List<Move> moves, boolean inPacks, boolean slim) {

    private static final Pattern TEXTURE_URL = Pattern.compile("^https?://textures\\.minecraft\\.net/texture/[0-9a-fA-F]+$");
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    /** Tidies pasted skin input: trims spaces/quotes/trailing slash; a bare 64-char texture hash becomes a full link. */
    public static String cleanSkin(String s) {
        if (s == null) return "";
        s = s.trim().replaceAll("^[\"'<]+|[\"'>]+$", "").trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (s.matches("^[0-9a-fA-F]{40,}$")) s = "http://textures.minecraft.net/texture/" + s;
        return s;
    }

    public static boolean validSkin(String s) {
        return s != null && (TEXTURE_URL.matcher(s).matches() || USERNAME.matcher(s).matches());
    }

    public static String slug(String name) {
        return name.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    /** Same card with a different skin, arms, title and pack setting (keeps an admin's edits when templates update). */
    public Card withLook(String skin, boolean slim, String title, boolean inPacks) {
        return new Card(id, name, skin, title, rarity, background, type, hp, retreat, moves, inPacks, slim);
    }

    public void save(ConfigurationSection s) {
        s.set("name", name);
        s.set("skin", skin);
        s.set("title", title);
        s.set("rarity", rarity.name());
        s.set("background", background.name());
        s.set("in-packs", inPacks);
        s.set("slim-arms", slim);
        s.set("type", type.name());
        s.set("hp", hp);
        s.set("retreat", retreat);
        for (int i = 0; i < moves.size(); i++) {
            Move m = moves.get(i);
            s.set("moves." + i + ".name", m.name());
            s.set("moves." + i + ".cost", m.cost());
            s.set("moves." + i + ".damage", m.damage());
            s.set("moves." + i + ".effect", m.effect().name());
        }
    }

    /** True if this section was written before the mana rework (stats instead of type/hp). */
    public static boolean isLegacy(ConfigurationSection s) { return !s.contains("hp"); }

    public static Card load(String id, ConfigurationSection s) {
        Rarity r;
        try { r = Rarity.valueOf(s.getString("rarity", "COMMON")); }
        catch (IllegalArgumentException e) { r = Rarity.COMMON; }
        Background bg = Background.parse(s.getString("background", "SKYLINE"));
        String name = s.getString("name", id), skin = s.getString("skin", "Steve"), title = s.getString("title", s.getString("collection", ""));
        boolean inPacks = s.getBoolean("in-packs", true), slim = s.getBoolean("slim-arms", id.equals("vince_sunseeker"));
        if (isLegacy(s)) return fromLegacy(id, name, skin, title, r, bg, inPacks, slim, s);
        CardType type = CardType.parse(s.getString("type"), CardType.STRENGTH);
        int hp = clamp(s.getInt("hp", 60), Rules.MIN_HP, Rules.maxHp(r));
        int retreat = clamp(s.getInt("retreat", 1), 0, Rules.MAX_RETREAT);
        List<Move> moves = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            MoveEffect e = MoveEffect.parse(s.getString("moves." + i + ".effect", "NONE"));
            int cost = clamp(s.getInt("moves." + i + ".cost", i + 1), 0, Rules.MAX_MOVE_COST);
            int dmg = clamp(s.getInt("moves." + i + ".damage", 20), 0, Rules.maxDamage(cost, r, e));
            moves.add(new Move(s.getString("moves." + i + ".name", "Move " + (i + 1)), cost, dmg, e));
        }
        return new Card(id, name, skin, title, r, bg, type, hp, retreat, List.copyOf(moves), inPacks, slim);
    }

    /** Converts a card from the old stat system: type = highest of Str/Dex/Mag, Health x2, moves by their old power. */
    private static Card fromLegacy(String id, String name, String skin, String title, Rarity r, Background bg, boolean inPacks, boolean slim, ConfigurationSection s) {
        int str = s.getInt("stats.strength", 10), dex = s.getInt("stats.dexterity", 10), mag = s.getInt("stats.magicing", 10);
        CardType type = mag > str && mag > dex ? CardType.MAGICING : dex > str ? CardType.DEXTERITY : CardType.STRENGTH;
        int hp = clamp(Rules.round10(s.getInt("stats.health", 30) * 2.2), Rules.MIN_HP, Rules.maxHp(r));
        List<Move> moves = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            String stat = s.getString("moves." + i + ".stat", "STRENGTH").toLowerCase(Locale.ROOT);
            int power = s.getInt("stats." + stat, 10);
            int cost = power <= 12 ? 1 : power <= 24 ? 2 : 3;
            moves.add(new Move(s.getString("moves." + i + ".name", "Move " + (i + 1)), cost, Rules.maxDamage(cost, r, MoveEffect.NONE) - 10, MoveEffect.NONE));
        }
        moves.sort(Comparator.comparingInt(Move::cost));
        return new Card(id, name, skin, title, r, bg, type, hp, 1, List.copyOf(moves), inPacks, slim);
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
