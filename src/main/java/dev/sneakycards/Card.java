package dev.sneakycards;

import org.bukkit.configuration.ConfigurationSection;
import java.util.*;
import java.util.regex.Pattern;

public record Card(String id, String name, String skin, String collection, Rarity rarity, Background background,
                   Map<Stat, Integer> stats, List<Move> moves, boolean inPacks, boolean slim) {

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

    public int stat(Stat s) { return stats.getOrDefault(s, 0); }
    public int power(Move m) { return stat(m.stat()); }

    public void save(ConfigurationSection s) {
        s.set("name", name);
        s.set("skin", skin);
        s.set("collection", collection);
        s.set("rarity", rarity.name());
        s.set("background", background.name());
        s.set("in-packs", inPacks);
        s.set("slim-arms", slim);
        for (Stat st : Stat.values()) s.set("stats." + st.name().toLowerCase(Locale.ROOT), stat(st));
        for (int i = 0; i < moves.size(); i++) {
            s.set("moves." + i + ".name", moves.get(i).name());
            s.set("moves." + i + ".stat", moves.get(i).stat().name());
        }
    }

    public static Card load(String id, ConfigurationSection s) {
        Map<Stat, Integer> stats = new EnumMap<>(Stat.class);
        for (Stat st : Stat.values())
            stats.put(st, s.getInt("stats." + st.name().toLowerCase(Locale.ROOT), st.defaultValue()));
        List<Move> moves = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Stat ms;
            try { ms = Stat.valueOf(s.getString("moves." + i + ".stat", "STRENGTH")); }
            catch (IllegalArgumentException e) { ms = Stat.STRENGTH; }
            moves.add(new Move(s.getString("moves." + i + ".name", "Move " + (i + 1)), ms));
        }
        Rarity r;
        try { r = Rarity.valueOf(s.getString("rarity", "COMMON")); }
        catch (IllegalArgumentException e) { r = Rarity.COMMON; }
        Background bg = Background.parse(s.getString("background", "SKYLINE"));
        return new Card(id, s.getString("name", id), s.getString("skin", "Steve"),
                s.getString("collection", "none"), r, bg, stats, List.copyOf(moves), s.getBoolean("in-packs", true),
                s.getBoolean("slim-arms", id.equals("vince_sunseeker"))); // demo skin is slim
    }
}
