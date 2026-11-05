"""Game mat textures. A mat is 3 x 3 blocks. Each side has three card slots in a row; the deck pile sits off to the
owner's right, nudged towards them, and the discard pile mirrors it on the left. Traps lie in the open middle.
Texture is 288 x 288 (96 px per block); the same layout numbers are used by the plugin (Mat.java)."""
from PIL import Image, ImageDraw, ImageFilter
import math, random

PPB = 96                       # pixels per block
MW, MH = 3, 3                  # mat size in blocks
TW, TH = MW * PPB, MH * PPB
SLOT_W, SLOT_H = 0.54, 0.76     # slot size in blocks
ROW_Z = 0.7                   # distance of each card row from the centre, in blocks
COLS = (-0.62, 0.0, 0.62)    # character slots, from the owner's left
PILE_X, PILE_Z = 1.12, 0.9   # deck pile (owner's right) and discard pile (owner's left), nudged towards the owner
PILE_W, PILE_H = 0.42, 0.58

def px(bx, bz):                # block offsets from the mat centre -> pixel position
    return TW / 2 + bx * PPB, TH / 2 + bz * PPB

def box(cx, cz, w, h):
    x, z = px(cx, cz)
    return (x - w * PPB / 2, z - h * PPB / 2, x + w * PPB / 2, z + h * PPB / 2)

def slot_boxes():
    """Character slots for both sides. Side at +z is the near side (texture bottom), seen from its owner."""
    out = []
    for side in (-1, 1):
        for cx in COLS:
            out.append(box(cx * side, side * ROW_Z, SLOT_W, SLOT_H))
    return out

def pile_boxes():
    """Deck and discard spots for both sides."""
    return [box(x * side, side * PILE_Z, PILE_W, PILE_H) for side in (-1, 1) for x in (PILE_X, -PILE_X)]

def lerp(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))

def gradient(size, c0, c1, diagonal=True):
    w, h = size; im = Image.new("RGB", size); p = im.load()
    for y in range(h):
        for x in range(w):
            t = (x / w * 0.45 + y / h * 0.55) if diagonal else y / h
            p[x, y] = lerp(c0, c1, t)
    return im

def glow_layer(draw_fn, radius, color, strength=1.0):
    layer = Image.new("RGBA", (TW, TH), (0, 0, 0, 0)); draw_fn(ImageDraw.Draw(layer))
    g = layer.filter(ImageFilter.GaussianBlur(radius))
    r, gch, b, a = g.split(); a = a.point(lambda v: min(255, int(v * strength)))
    return Image.merge("RGBA", (r, gch, b, a))

def star(d, cx, cy, r, fill):
    d.polygon([(cx, cy - r), (cx + r * .28, cy - r * .28), (cx + r, cy), (cx + r * .28, cy + r * .28),
               (cx, cy + r), (cx - r * .28, cy + r * .28), (cx - r, cy), (cx - r * .28, cy - r * .28)], fill=fill)

def _unused_dashed_rect(d, b, color, width=2, dash=6):
    x0, y0, x1, y1 = [int(v) for v in b]
    for x in range(x0, x1, dash * 2):
        d.line([(x, y0), (min(x + dash, x1), y0)], fill=color, width=width); d.line([(x, y1), (min(x + dash, x1), y1)], fill=color, width=width)
    for y in range(y0, y1, dash * 2):
        d.line([(x0, y), (x0, min(y + dash, y1))], fill=color, width=width); d.line([(x1, y), (x1, min(y + dash, y1))], fill=color, width=width)

def common_layout(im, line, slot_fill, corner, emblem):
    d = ImageDraw.Draw(im, "RGBA")
    for b in slot_boxes():
        d.rounded_rectangle(b, radius=7, fill=slot_fill, outline=line, width=3)
    faint = line[:3] + (110,) if len(line) == 4 else line + (110,)
    for b in pile_boxes():
        d.rounded_rectangle(b, radius=5, outline=faint, width=2)
    cx, cz = px(0, 0)
    emblem(d, cx, cz)

# ---------------------------------------------------------------- classic
def classic():
    im = Image.new("RGB", (TW, TH)); p = im.load()
    for y in range(TH):
        for x in range(TW):
            stripe = ((x + y) // 12) % 2
            p[x, y] = (31, 90, 74) if stripe else (27, 82, 68)
    d = ImageDraw.Draw(im, "RGBA")
    wood, trim = (107, 74, 43, 255), (160, 116, 66, 255)
    d.rectangle((0, 0, TW - 1, TH - 1), outline=wood, width=12)
    d.rectangle((12, 12, TW - 13, TH - 13), outline=trim, width=2)
    d.rectangle((0, 0, TW - 1, TH - 1), outline=(70, 46, 26, 255), width=2)
    def emblem(d, cx, cz):
        d.ellipse((cx - 20, cz - 20, cx + 20, cz + 20), outline=(255, 255, 255, 120), width=3)
        d.polygon([(cx, cz - 13), (cx + 9, cz), (cx, cz + 13), (cx - 9, cz)], fill=(214, 176, 90, 255), outline=(120, 90, 40, 255))
    common_layout(im, (255, 255, 255, 120), (0, 0, 0, 56), (255, 255, 255, 150), emblem)
    return im.convert("RGBA")

# ---------------------------------------------------------------- royal purple
def purple():
    im = gradient((TW, TH), (74, 34, 128), (36, 16, 76), diagonal=False)
    rng = random.Random(7); d = ImageDraw.Draw(im, "RGBA")
    for _ in range(260):
        x, y = rng.randrange(TW), rng.randrange(TH); r = rng.choice([1, 1, 2])
        d.ellipse((x - r, y - r, x + r, y + r), fill=(220, 200, 255, rng.randint(50, 140)))
    gold, dgold = (222, 176, 72, 255), (128, 90, 30, 255)
    d.rectangle((0, 0, TW - 1, TH - 1), outline=(26, 12, 52, 255), width=12)
    d.rectangle((12, 12, TW - 13, TH - 13), outline=gold, width=3)
    d.rectangle((18, 18, TW - 19, TH - 19), outline=dgold, width=1)
    for x, y in ((12, 12), (TW - 13, 12), (12, TH - 13), (TW - 13, TH - 13)): star(d, x, y, 11, gold)
    def emblem(d, cx, cz):
        d.ellipse((cx - 20, cz - 20, cx + 20, cz + 20), outline=gold, width=3)
        star(d, cx, cz, 15, gold); d.ellipse((cx - 4, cz - 4, cx + 4, cz + 4), fill=(120, 60, 200, 255))
    common_layout(im, gold, (20, 8, 44, 110), (255, 230, 150, 255), emblem)
    return im.convert("RGBA")

# ---------------------------------------------------------------- cyan to pink gradient
def neon():
    im = gradient((TW, TH), (0, 214, 255), (255, 58, 160))
    d = ImageDraw.Draw(im, "RGBA")
    d.rectangle((0, 0, TW - 1, TH - 1), outline=(14, 12, 38, 255), width=12)
    d.rectangle((12, 12, TW - 13, TH - 13), outline=(255, 255, 255, 235), width=2)
    def emblem(d, cx, cz):
        d.ellipse((cx - 20, cz - 20, cx + 20, cz + 20), outline=(255, 255, 255, 255), width=4)
        star(d, cx, cz, 15, (255, 255, 255, 255)); d.ellipse((cx - 4, cz - 4, cx + 4, cz + 4), fill=(14, 12, 38, 255))
    glow_src = lambda gd: [gd.rounded_rectangle(b, radius=8, outline=(255, 255, 255, 255), width=5) for b in slot_boxes()]
    im = im.convert("RGBA"); im.alpha_composite(glow_layer(glow_src, 6, (255, 255, 255), 1.6)); im = im.convert("RGB")
    common_layout(im, (255, 255, 255), (14, 12, 38, 175), (255, 255, 255), emblem)
    return im.convert("RGBA")

MATS = {"classic": ("Classic Felt", classic), "purple": ("Royal Purple", purple), "neon": ("Neon Gradient", neon)}

if __name__ == "__main__":
    sheet = Image.new("RGBA", (3 * 230 + 20, 240), (30, 30, 34, 255))
    for i, (k, (label, fn)) in enumerate(MATS.items()):
        sheet.paste(fn().resize((220, 220), Image.LANCZOS), (10 + i * 230, 10))
    sheet.save("mats-preview.png")
