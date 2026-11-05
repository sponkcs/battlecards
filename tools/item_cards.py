"""Pixel art for item and trap cards. Keys and rarities match ItemType.java."""
from PIL import Image, ImageDraw

ITEMS = {  # key: (rarity, kind)
    "bandage": ("common", "quick"), "spike_trap": ("common", "trap"),
    "herbal_tonic": ("uncommon", "quick"), "decoy": ("uncommon", "trap"), "energy_drink": ("uncommon", "quick"),
    "mending_potion": ("rare", "quick"), "snare": ("rare", "trap"),
    "mirror_ward": ("epic", "trap"), "last_stand": ("legendary", "trap"), "guardian_angel": ("mythic", "trap"),
    # expanded roster for the deck builder
    "scouts_map": ("common", "quick"), "whetstone": ("common", "quick"),
    "counter_stance": ("uncommon", "trap"),
    "sprint_shoes": ("rare", "quick"), "rally_horn": ("rare", "quick"), "pitfall": ("rare", "trap"),
    "recall_scroll": ("epic", "quick"), "thunder_scroll": ("epic", "quick"), "smoke_veil": ("epic", "trap"),
    "war_drum": ("legendary", "quick"), "banish_rune": ("legendary", "trap"),
    "dragons_hoard": ("mythic", "quick"),
}
OUT = (30, 24, 28)

def bottle(d, glass, liquid, cork=(150, 105, 60)):
    d.rectangle((8, 2, 11, 4), fill=cork)
    d.rectangle((8, 5, 11, 6), fill=glass)
    d.ellipse((4, 6, 15, 17), fill=glass, outline=OUT)
    d.ellipse((5, 10, 14, 16), fill=liquid)
    d.point((7, 8), fill=(255, 255, 255)); d.point((6, 9), fill=(255, 255, 255))

def icon(key):
    im = Image.new("RGBA", (20, 18), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    if key == "bandage":
        d.polygon([(3, 12), (12, 3), (16, 7), (7, 16)], fill=(236, 214, 176), outline=OUT)
        d.polygon([(7, 8), (9, 6), (12, 9), (10, 11)], fill=(250, 240, 220))
        for x, y in ((8, 8), (10, 8), (9, 10), (11, 10)): d.point((x, y), fill=(190, 160, 120))
    elif key == "spike_trap":
        d.rectangle((1, 14, 18, 16), fill=(90, 90, 96), outline=OUT)
        for x in (2, 7, 12):
            d.polygon([(x, 14), (x + 2, 4), (x + 4, 14)], fill=(190, 194, 204), outline=OUT)
        d.point((9, 5), fill=(255, 255, 255))
    elif key == "herbal_tonic":
        bottle(d, (200, 230, 210), (70, 170, 80))
        d.polygon([(14, 2), (18, 1), (17, 5)], fill=(80, 170, 70))
    elif key == "decoy":
        d.rectangle((9, 6, 10, 17), fill=(120, 84, 50))
        d.rectangle((3, 7, 16, 8), fill=(120, 84, 50))
        d.ellipse((6, 0, 13, 6), fill=(214, 186, 110), outline=OUT)
        d.rectangle((6, 8, 13, 13), fill=(176, 70, 60), outline=OUT)
        d.point((8, 3), fill=OUT); d.point((11, 3), fill=OUT)
    elif key == "energy_drink":
        d.rectangle((6, 2, 13, 17), fill=(60, 120, 220), outline=OUT)
        d.rectangle((6, 2, 13, 3), fill=(200, 200, 210))
        d.polygon([(11, 5), (8, 10), (10, 10), (8, 15), (12, 9), (10, 9)], fill=(255, 220, 60))
    elif key == "mending_potion":
        bottle(d, (230, 210, 220), (210, 40, 60))
        d.rectangle((9, 11, 10, 15), fill=(255, 220, 230)); d.rectangle((7, 12, 12, 13), fill=(255, 220, 230))
    elif key == "snare":
        d.ellipse((3, 3, 16, 14), outline=(170, 130, 80), width=2)
        d.line([(15, 12), (19, 17)], fill=(170, 130, 80), width=2)
        d.line([(2, 16), (18, 16)], fill=(90, 70, 50))
    elif key == "mirror_ward":
        d.ellipse((4, 1, 15, 15), fill=(180, 120, 240), outline=OUT)
        d.ellipse((6, 3, 13, 13), fill=(214, 232, 250))
        d.line([(8, 5), (11, 8)], fill=(255, 255, 255)); d.line([(8, 7), (10, 9)], fill=(255, 255, 255))
        d.rectangle((9, 15, 10, 17), fill=(180, 120, 240))
    elif key == "last_stand":
        d.polygon([(10, 1), (17, 3), (16, 11), (10, 17), (4, 11), (3, 3)], fill=(255, 176, 32), outline=OUT)
        d.polygon([(10, 4), (14, 5), (13, 10), (10, 13), (7, 10), (6, 5)], fill=(200, 60, 50))
        d.rectangle((9, 6, 10, 11), fill=(255, 230, 150))
    elif key == "guardian_angel":
        d.ellipse((6, 0, 13, 3), outline=(255, 230, 120))
        d.polygon([(10, 7), (1, 3), (2, 10), (6, 14)], fill=(250, 250, 255), outline=OUT)
        d.polygon([(10, 7), (19, 3), (18, 10), (14, 14)], fill=(250, 250, 255), outline=OUT)
        d.ellipse((8, 5, 12, 9), fill=(255, 210, 180), outline=OUT)
        d.rectangle((8, 9, 12, 16), fill=(255, 140, 190), outline=OUT)
    elif key == "scouts_map":
        d.rectangle((2, 3, 17, 15), fill=(226, 204, 150), outline=OUT)
        d.line([(7, 3), (7, 15)], fill=(190, 166, 110)); d.line([(12, 3), (12, 15)], fill=(190, 166, 110))
        d.line([(4, 12), (8, 8), (11, 10), (15, 5)], fill=(200, 60, 50))
        d.line([(14, 5), (16, 7)], fill=(200, 60, 50)); d.line([(16, 5), (14, 7)], fill=(200, 60, 50))
    elif key == "whetstone":
        d.polygon([(2, 12), (12, 4), (18, 7), (8, 15)], fill=(120, 124, 134), outline=OUT)
        d.line([(5, 11), (13, 6)], fill=(170, 174, 184))
        d.line([(10, 1), (12, 3)], fill=(255, 240, 160)); d.line([(15, 1), (14, 3)], fill=(255, 240, 160))
    elif key == "counter_stance":
        d.line([(3, 15), (15, 3)], fill=(200, 204, 214), width=2); d.line([(3, 3), (15, 15)], fill=(200, 204, 214), width=2)
        d.rectangle((2, 14, 5, 17), fill=(140, 90, 50)); d.rectangle((13, 14, 16, 17), fill=(140, 90, 50))
        d.polygon([(9, 6), (12, 9), (9, 12), (6, 9)], fill=(255, 210, 80), outline=OUT)
    elif key == "sprint_shoes":
        d.polygon([(4, 6), (9, 6), (10, 11), (17, 12), (17, 15), (3, 15)], fill=(220, 80, 60), outline=OUT)
        d.rectangle((3, 14, 17, 15), fill=(240, 240, 240))
        for y in (8, 10, 12): d.line([(0, y), (2, y)], fill=(255, 255, 255))
    elif key == "rally_horn":
        d.polygon([(2, 9), (14, 3), (16, 4), (16, 14), (14, 15), (2, 11)], fill=(214, 176, 112), outline=OUT)
        d.ellipse((13, 3, 18, 15), fill=(160, 120, 60), outline=OUT)
        d.line([(4, 10), (13, 10)], fill=(250, 220, 160))
    elif key == "pitfall":
        d.ellipse((2, 8, 17, 16), fill=(30, 20, 18), outline=(110, 80, 50))
        d.line([(4, 9), (8, 12)], fill=(120, 90, 60)); d.line([(15, 9), (11, 12)], fill=(120, 90, 60))
        d.polygon([(9, 1), (12, 1), (12, 5), (14, 5), (10, 8), (7, 5), (9, 5)], fill=(255, 230, 80), outline=OUT)
    elif key == "recall_scroll":
        d.rectangle((4, 3, 15, 15), fill=(236, 222, 190), outline=OUT)
        d.rectangle((3, 2, 16, 4), fill=(170, 120, 70), outline=OUT); d.rectangle((3, 14, 16, 16), fill=(170, 120, 70), outline=OUT)
        d.arc((6, 6, 13, 13), 40, 320, fill=(150, 90, 220), width=2); d.polygon([(12, 5), (14, 8), (11, 8)], fill=(150, 90, 220))
    elif key == "thunder_scroll":
        d.rectangle((4, 3, 15, 15), fill=(236, 222, 190), outline=OUT)
        d.rectangle((3, 2, 16, 4), fill=(170, 120, 70), outline=OUT); d.rectangle((3, 14, 16, 16), fill=(170, 120, 70), outline=OUT)
        d.polygon([(11, 5), (7, 10), (10, 10), (8, 14), (13, 8), (10, 8)], fill=(255, 220, 60), outline=OUT)
    elif key == "smoke_veil":
        for (x, y, r) in ((6, 10, 5), (11, 8, 6), (14, 12, 4), (9, 13, 4)):
            d.ellipse((x - r, y - r, x + r, y + r), fill=(170, 170, 186))
        for (x, y) in ((7, 8), (12, 6)): d.point((x, y), fill=(230, 230, 240))
    elif key == "war_drum":
        d.ellipse((3, 3, 16, 7), fill=(240, 220, 180), outline=OUT)
        d.rectangle((3, 5, 16, 14), fill=(190, 50, 40), outline=OUT)
        d.ellipse((3, 12, 16, 16), fill=(150, 36, 30), outline=OUT)
        for x in (5, 9, 13): d.line([(x, 7), (x + 2, 13)], fill=(240, 200, 90))
        d.line([(14, 0), (18, 4)], fill=(150, 105, 60), width=2)
    elif key == "banish_rune":
        d.ellipse((2, 1, 17, 16), fill=(60, 40, 90), outline=(255, 176, 32))
        d.polygon([(9, 3), (13, 9), (9, 14), (5, 9)], outline=(255, 210, 90))
        d.line([(9, 3), (9, 14)], fill=(255, 210, 90)); d.line([(5, 9), (13, 9)], fill=(255, 210, 90))
    elif key == "dragons_hoard":
        d.polygon([(2, 16), (5, 9), (9, 11), (12, 7), (17, 16)], fill=(255, 196, 40), outline=OUT)
        for (x, y) in ((6, 12), (10, 10), (13, 13), (8, 14)): d.ellipse((x - 1, y - 1, x + 1, y + 1), fill=(255, 240, 150))
        d.polygon([(11, 2), (13, 5), (11, 8), (9, 5)], fill=(255, 61, 127), outline=OUT)
    return im

def item_front(key, rc, mythic):
    """80 x 112 card: rarity frame around one big icon. Quick items have a green tint, traps a purple one."""
    kind = ITEMS[key][1]
    base = Image.new("RGBA", (40, 56)); d = ImageDraw.Draw(base)
    dark = tuple(int(x * .55) for x in rc); light = tuple(min(255, int(x * 1.25)) for x in rc)
    d.rectangle((0, 0, 39, 55), fill=rc); d.rectangle((1, 1, 38, 54), outline=dark)
    if mythic:
        for i in range(0, 40, 2): d.point((i, 0), fill=light); d.point((i, 55), fill=light)
        for i in range(0, 56, 2): d.point((0, i), fill=light); d.point((39, i), fill=light)
    d.rectangle((3, 3, 36, 52), fill=(40, 70, 50) if kind == "quick" else (52, 36, 70))
    im = base.resize((80, 112), Image.NEAREST)
    win = Image.new("RGBA", (60, 54), (0, 0, 0, 0))
    win.alpha_composite(icon(key).resize((60, 54), Image.NEAREST))
    im.alpha_composite(win, (10, 29))
    return im
