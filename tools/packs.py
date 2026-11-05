"""Pack art (40 x 56 pixel art, padded to 64 x 64 for the item sprite). Keys match PackType.java."""
from PIL import Image, ImageDraw
import colorsys
from backgrounds import BACKGROUNDS, scene
from item_cards import icon

def lerp(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

def pack_art(c1, c2, accent, emblem=None, foil=False):
    W, H = 40, 56; im = Image.new("RGBA", (W, H), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    for y in range(4, H - 4): d.line([(3, y), (W - 4, y)], fill=lerp(c1, c2, (y - 4) / (H - 8)) + (255,))
    for x in range(3, W - 3):                                    # crimped ends
        h = 2 if x % 2 else 3
        d.line([(x, 4 - h), (x, 4)], fill=lerp(c1, (255, 255, 255), .25) + (255,))
        d.line([(x, H - 5), (x, H - 5 + h)], fill=lerp(c2, (0, 0, 0), .2) + (255,))
    d.rectangle((3, 4, W - 4, H - 5), outline=lerp(c2, (0, 0, 0), .45) + (255,))
    d.line([(3, 8), (W - 4, 8)], fill=accent + (255,)); d.line([(3, H - 9), (W - 4, H - 9)], fill=accent + (255,))
    if emblem is not None:
        e = emblem.convert("RGBA").resize((24, 22), Image.NEAREST); d.rectangle((7, 14, 32, 37), fill=accent + (255,)); im.paste(e, (8, 15), e)
    else:
        d.polygon([(20, 13), (23, 22), (32, 25), (23, 28), (20, 37), (17, 28), (8, 25), (17, 22)], fill=accent + (255,), outline=lerp(accent, (0, 0, 0), .5) + (255,))
    d.rectangle((7, 41, 32, 45), fill=lerp(c2, (0, 0, 0), .35) + (255,))
    for x in range(9, 31, 3): d.point((x, 43), fill=accent + (255,))
    sh = Image.new("RGBA", (W, H), (0, 0, 0, 0)); sd = ImageDraw.Draw(sh)
    sd.polygon([(6, 4), (11, 4), (3, 18), (3, 12)], fill=(255, 255, 255, 70)); sd.polygon([(30, H - 5), (36, H - 5), (36, H - 12)], fill=(255, 255, 255, 40))
    if foil:
        for y in range(4, H - 4):
            for x in range(3, W - 3):
                r, g, b = colorsys.hsv_to_rgb(((x + y) / 50) % 1, .6, 1); sd.point((x, y), fill=(int(r * 255), int(g * 255), int(b * 255), 55))
    return Image.alpha_composite(im, sh)

THEMES = {"skyline": ((240, 120, 80), (60, 40, 110)), "blossom": ((255, 180, 210), (200, 90, 150)), "hellfire": ((230, 80, 40), (90, 10, 10)),
          "grove": ((90, 160, 80), (30, 70, 40)), "goo": ((240, 210, 60), (150, 120, 20)), "slums": ((150, 110, 80), (70, 50, 40)),
          "mines": ((120, 120, 130), (50, 50, 60)), "spire": ((110, 90, 200), (30, 20, 70))}

def packs():
    P = {"standard": pack_art((214, 60, 90), (150, 30, 60), (255, 214, 60)),
         "premium": pack_art((40, 40, 52), (14, 14, 20), (255, 190, 60)),
         "prism": pack_art((200, 220, 255), (150, 120, 230), (255, 255, 255), foil=True),
         "supply": pack_art((60, 150, 90), (30, 90, 60), (240, 230, 180), emblem=icon("mending_potion")),
         "trap_kit": pack_art((120, 70, 180), (60, 30, 100), (220, 190, 255), emblem=icon("spike_trap"))}
    for k, (a, b) in THEMES.items(): P["theme_" + k] = pack_art(a, b, (255, 236, 170), emblem=scene(k))
    return P

def sprite(art):
    out = Image.new("RGBA", (64, 64), (0, 0, 0, 0)); out.alpha_composite(art, (12, 4)); return out
