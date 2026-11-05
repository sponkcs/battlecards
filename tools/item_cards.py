"""Pixel art for item and trap cards. Keys and rarities match ItemType.java."""
from PIL import Image, ImageDraw

ITEMS = {  # key: (rarity, kind)
    "bandage": ("common", "quick"), "spike_trap": ("common", "trap"),
    "herbal_tonic": ("uncommon", "quick"), "decoy": ("uncommon", "trap"), "energy_drink": ("uncommon", "quick"),
    "mending_potion": ("rare", "quick"), "snare": ("rare", "trap"),
    "mirror_ward": ("epic", "trap"), "last_stand": ("legendary", "trap"), "guardian_angel": ("mythic", "trap"),
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
    return im

def item_front(key, rc, mythic):
    """80 x 112 card: rarity frame, name bar, icon window, ITEM (green) or TRAP (purple) band."""
    kind = ITEMS[key][1]
    base = Image.new("RGBA", (40, 56)); d = ImageDraw.Draw(base)
    dark = tuple(int(x * .55) for x in rc); light = tuple(min(255, int(x * 1.25)) for x in rc)
    d.rectangle((0, 0, 39, 55), fill=rc); d.rectangle((1, 1, 38, 54), outline=dark); d.rectangle((3, 3, 36, 52), fill=(28, 30, 36))
    if mythic:
        for i in range(0, 40, 2): d.point((i, 0), fill=light); d.point((i, 55), fill=light)
        for i in range(0, 56, 2): d.point((0, i), fill=light); d.point((39, i), fill=light)
    d.rectangle((5, 5, 34, 10), fill=dark); d.rectangle((6, 6, 23, 9), fill=light)
    band = (60, 160, 90) if kind == "quick" else (130, 70, 180)
    d.rectangle((5, 41, 34, 50), fill=band)
    for x in range(7, 33, 4): d.point((x, 45), fill=tuple(min(255, c + 60) for c in band))
    im = base.resize((80, 112), Image.NEAREST)
    win = Image.new("RGBA", (60, 54), (40, 70, 50, 255) if kind == "quick" else (52, 36, 70, 255))
    wd = ImageDraw.Draw(win)
    for y in range(0, 54, 6): wd.line([(0, y), (59, y)], fill=(255, 255, 255, 18))
    win.alpha_composite(icon(key).resize((60, 54), Image.NEAREST))
    im.paste(win, (10, 24), win)
    return im
