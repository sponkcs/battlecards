package dev.sneakycards;

import java.util.Locale;

/** Booster pack varieties. Every pack holds 5 cards; what they are depends on the variety. */
public enum PackType {
    STANDARD("Card Pack", "pack", null, "3 character cards", "2 item or trap cards"),
    PREMIUM("Premium Pack", "pack_premium", null, "2 character cards", "1 character card, Rare or better", "2 item or trap cards", "Triple holo chance"),
    PRISM("Prism Pack", "pack_prism", null, "1 holographic character card", "2 character cards", "2 item or trap cards"),
    THEME_SKYLINE(null, "pack_theme_skyline", Background.SKYLINE),
    THEME_BLOSSOM(null, "pack_theme_blossom", Background.BLOSSOM),
    THEME_HELLFIRE(null, "pack_theme_hellfire", Background.HELLFIRE),
    THEME_GROVE(null, "pack_theme_grove", Background.GROVE),
    THEME_GOO(null, "pack_theme_goo", Background.GOO),
    THEME_SLUMS(null, "pack_theme_slums", Background.SLUMS),
    THEME_MINES(null, "pack_theme_mines", Background.MINES),
    THEME_SPIRE(null, "pack_theme_spire", Background.SPIRE),
    SUPPLY_CRATE("Supply Crate", "pack_supply", null, "4 quick items", "1 trap"),
    TRAP_KIT("Trap Kit", "pack_trap_kit", null, "4 traps", "1 quick item");

    private final String label;
    public final String model;
    public final Background theme;   // themed packs: 2 of the 3 characters come from this background
    private final String[] contents;

    PackType(String label, String model, Background theme, String... contents) {
        this.label = label; this.model = model; this.theme = theme; this.contents = contents;
    }

    public String label() { return theme == null ? label : theme.label + " Pack"; }

    public String[] contents() {
        if (theme == null) return contents;
        return new String[]{"2 " + theme.label + " characters", "1 character from any background", "2 item or trap cards"};
    }

    public String key() { return name().toLowerCase(Locale.ROOT); }

    public static PackType parse(String s) {
        if (s == null) return null;
        String k = s.toUpperCase(Locale.ROOT).replace('-', '_');
        try { return valueOf(k); } catch (IllegalArgumentException e) { }
        try { return valueOf("THEME_" + k); } catch (IllegalArgumentException e) { return null; }
    }
}
