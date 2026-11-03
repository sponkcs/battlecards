package dev.sneakycards;

import net.kyori.adventure.text.format.TextColor;
import java.util.Locale;

public enum Rarity {
    COMMON("Common", 0x9D9D9D, 60),
    UNCOMMON("Uncommon", 0x55C455, 25),
    RARE("Rare", 0x4A8CFF, 10),
    EPIC("Epic", 0xB36BFF, 4),
    LEGENDARY("Legendary", 0xFFB020, 1),
    MYTHIC("Mythic", 0xFF3D7F, 0.5);

    public final String label;
    public final TextColor color;
    public final double weight;

    Rarity(String label, int rgb, double weight) {
        this.label = label;
        this.color = TextColor.color(rgb);
        this.weight = weight;
    }

    public TextColor color() { return color; }

    public String key() { return name().toLowerCase(Locale.ROOT); }
}
