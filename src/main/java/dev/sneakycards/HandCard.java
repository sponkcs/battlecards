package dev.sneakycards;

/** One card in a deck, hand or discard pile: a character (maybe holographic) or an item. */
public final class HandCard {
    public final Card card;       // null for items
    public final boolean holo;
    public final ItemType item;   // null for characters

    private HandCard(Card card, boolean holo, ItemType item) { this.card = card; this.holo = holo; this.item = item; }
    public static HandCard of(Card c, boolean holo) { return new HandCard(c, holo, null); }
    public static HandCard of(ItemType t) { return new HandCard(null, false, t); }

    public boolean isChar() { return card != null; }
    public String name() { return isChar() ? card.name() : item.label; }
    public Rarity rarity() { return isChar() ? card.rarity() : item.rarity; }

    /** Binder/deck key: "id", "id#holo" or "item:NAME". */
    public String key() { return isChar() ? card.id() + (holo ? "#holo" : "") : "item:" + item.name(); }
    /** Key without the holo mark: copies of a card count together. */
    public String baseKey() { return isChar() ? card.id() : "item:" + item.name(); }

    public static HandCard parse(String key, CardRegistry reg) {
        if (key == null) return null;
        if (key.startsWith("item:")) { ItemType t = ItemType.parse(key.substring(5)); return t == null ? null : of(t); }
        boolean holo = key.endsWith("#holo");
        Card c = reg.get(holo ? key.substring(0, key.length() - 5) : key);
        return c == null ? null : of(c, holo);
    }
}
