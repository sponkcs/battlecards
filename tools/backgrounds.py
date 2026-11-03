"""Pixel-art card backgrounds. Each scene is 60x54 and is shared by the resource pack and the HTML mockup."""
from PIL import Image, ImageDraw
import random, math

W, H = 60, 54

def vgrad(d, stops, y0=0, y1=H):
    n = len(stops) - 1
    for y in range(y0, y1):
        t = (y - y0) / max(1, y1 - y0 - 1) * n
        i = min(int(t), n - 1); f = t - i
        a, b = stops[i], stops[i + 1]
        d.line([(0, y), (W - 1, y)], fill=tuple(int(a[k] + (b[k] - a[k]) * f) for k in range(3)) + (255,))

def glow(im, cx, cy, r, color, a=90):
    ov = Image.new("RGBA", im.size, (0, 0, 0, 0)); od = ImageDraw.Draw(ov)
    for i in range(r, 0, -1):
        od.ellipse((cx - i, cy - i, cx + i, cy + i), fill=color + (int(a * (1 - i / r)),))
    im.alpha_composite(ov)

def px(d, x, y, c):
    if 0 <= x < W and 0 <= y < H: d.point((x, y), fill=c)

def blob(d, rng, cx, cy, r, palette, n=None):
    for _ in range(n or int(r * r * 2.2)):
        a = rng.random() * 6.283; rr = r * math.sqrt(rng.random())
        px(d, int(cx + math.cos(a) * rr), int(cy + math.sin(a) * rr * 0.85), rng.choice(palette) + (255,))

def line(d, p, q, c, w=1):
    d.line([p, q], fill=c + (255,), width=w)

# ---------------------------------------------------------------- skyline
def skyline(im, d, rng):
    vgrad(d, [(59, 42, 107), (232, 104, 63), (255, 196, 107)], 0, 40)
    for _ in range(12): px(d, rng.randrange(W), rng.randrange(0, 14), (255, 240, 220, 255))
    glow(im, 44, 23, 13, (255, 220, 140), 150)
    d.ellipse((38, 17, 50, 29), fill=(255, 227, 161, 255))
    def row(col, hmin, hmax, wmin, wmax, win):
        x = -2
        while x < W:
            w = rng.randint(wmin, wmax); h = rng.randint(hmin, hmax)
            d.rectangle((x, H - h, x + w - 1, H), fill=col + (255,))
            if win:
                for wy in range(H - h + 3, H - 4, 4):
                    for wx in range(x + 2, x + w - 2, 3):
                        if rng.random() < 0.55: d.rectangle((wx, wy, wx, wy + 1), fill=(255, 217, 138, 255))
            x += w + rng.randint(0, 1)
    row((84, 60, 112), 20, 34, 6, 10, False)
    row((35, 26, 56), 10, 26, 7, 12, True)
    d.rectangle((0, H - 3, W, H), fill=(18, 14, 32, 255))

# ---------------------------------------------------------------- blossom orchard
def blossom(im, d, rng):
    vgrad(d, [(168, 208, 240), (214, 230, 248), (255, 240, 236)], 0, 38)
    for cx, cy, w in ((10, 8, 9), (38, 5, 11), (52, 14, 7)):
        d.ellipse((cx - w, cy - 2, cx + w, cy + 3), fill=(255, 255, 255, 255)); d.ellipse((cx - w // 2, cy - 4, cx + w // 2 + 2, cy + 1), fill=(255, 255, 255, 255))
    d.ellipse((-22, 30, 38, 66), fill=(150, 190, 130, 255)); d.ellipse((22, 33, 92, 68), fill=(128, 176, 116, 255))
    d.rectangle((0, 40, W, H), fill=(102, 156, 82, 255))
    for x, tp in ((17, 28), (47, 27)):                                   # distant orchard trees
        d.rectangle((x, tp + 4, x + 1, 40), fill=(96, 70, 56, 255)); blob(d, rng, x, tp, 4, [(255, 196, 218), (255, 226, 236), (246, 162, 196)])
    d.rectangle((33, 30, 47, 41), fill=(236, 224, 200, 255))              # cottage
    d.polygon([(30, 31), (40, 21), (50, 31)], fill=(178, 142, 72, 255))
    for k in range(5): line(d, (31 + k * 2, 31 - k), (49 - k * 2, 31 - k), (150, 116, 56))
    d.rectangle((44, 22, 46, 27), fill=(150, 140, 130, 255))
    d.rectangle((38, 35, 41, 41), fill=(104, 70, 44, 255)); d.rectangle((35, 33, 36, 35), fill=(130, 170, 206, 255)); d.rectangle((44, 33, 45, 35), fill=(130, 170, 206, 255))
    d.polygon([(26, H), (40, H), (41, 42), (38, 42)], fill=(198, 166, 122, 255))   # dirt path
    for x in range(0, W, 8):                                              # picket fence
        d.rectangle((x, 38, x + 1, 44), fill=(244, 238, 226, 255)); px(d, x, 37, (244, 238, 226, 255))
    d.line([(0, 40), (W, 40)], fill=(214, 206, 190, 255)); d.line([(0, 43), (W, 43)], fill=(214, 206, 190, 255))
    bark = (92, 62, 52)
    for tx, top in ((7, 24), (53, 20)):
        d.polygon([(tx - 3, H), (tx + 3, H), (tx + 1, top + 6), (tx - 1, top + 6)], fill=bark + (255,))
    line(d, (7, 34), (14, 25), bark, 2); line(d, (7, 30), (1, 22), bark, 2); line(d, (53, 32), (46, 23), bark, 2); line(d, (53, 27), (58, 18), bark, 2)
    pk = [(255, 183, 208), (255, 160, 196), (255, 232, 240), (240, 132, 176)]
    for cx, cy, r in ((14, 22, 8), (5, 18, 8), (9, 30, 6), (46, 21, 8), (55, 14, 7), (51, 28, 6)): blob(d, rng, cx, cy, r, pk)
    for _ in range(16): px(d, rng.randrange(W), rng.randrange(6, 44), (255, 214, 228, 255))
    for _ in range(34): px(d, rng.randrange(W), rng.randrange(45, H), rng.choice([(255, 196, 218), (255, 255, 255), (255, 230, 120)]) + (255,))

# ---------------------------------------------------------------- hellfire
def hellfire(im, d, rng):
    vgrad(d, [(16, 3, 10), (96, 12, 16), (224, 74, 18), (255, 150, 30)], 0, 44)
    vgrad(d, [(255, 80, 12), (255, 150, 30), (255, 214, 70)], 42, H)
    glow(im, 30, 30, 24, (255, 110, 20), 150)
    d.ellipse((23, 8, 37, 22), fill=(140, 18, 20, 255)); d.ellipse((25, 10, 35, 20), fill=(200, 38, 26, 255))
    for _ in range(16):
        x = rng.randrange(W); y = rng.randrange(44, H); d.rectangle((x, y, x + rng.randint(3, 8), y + 1), fill=(46, 14, 12, 255))
    for _ in range(10):
        x = rng.randrange(W); y = rng.randrange(46, H); d.rectangle((x, y, x + rng.randint(2, 5), y), fill=(255, 238, 120, 255))
    rock = (14, 8, 12, 255)
    for x, h, w in ((5, 26, 7), (16, 15, 6), (30, 10, 5), (44, 28, 8), (55, 16, 6)):
        d.polygon([(x - w, 46), (x - w + 2, 46 - h // 2), (x - 1, 46 - h + 3), (x + 1, 46 - h), (x + 3, 46 - h // 2), (x + w, 46)], fill=rock)
        line(d, (x - w + 2, 46 - h // 2), (x + 1, 46 - h), (190, 54, 20))
    for x in range(1, W, 3):
        hh = rng.randint(2, 6)
        d.polygon([(x, 46), (x + 1, 46 - hh), (x + 2, 46)], fill=(255, rng.randint(90, 170), 20, 255))
    for _ in range(26): px(d, rng.randrange(W), rng.randrange(6, 44), (255, rng.randint(150, 230), 60, 255))

# ---------------------------------------------------------------- grove forest
def grove(im, d, rng):
    vgrad(d, [(16, 48, 38), (46, 110, 60), (138, 190, 100)], 0, H)
    for x in range(1, W, 9): d.rectangle((x, 0, x + 2, H), fill=(26, 70, 46, 255))
    ov = Image.new("RGBA", im.size, (0, 0, 0, 0)); od = ImageDraw.Draw(ov)
    od.polygon([(6, 0), (16, 0), (36, H), (22, H)], fill=(255, 255, 190, 46))
    od.polygon([(26, 0), (31, 0), (48, H), (40, H)], fill=(255, 255, 190, 40))
    im.alpha_composite(ov)
    bark, bk2 = (76, 48, 32, 255), (52, 32, 22, 255)
    for x, w in ((10, 6), (35, 7), (53, 5)):
        d.polygon([(x - 2, H), (x + w + 2, H), (x + w, 40), (x + w, 0), (x, 0), (x, 40)], fill=bark)
        for y in range(2, 44, 5): d.line([(x + 1, y), (x + 1, y + 2)], fill=bk2)
        d.line([(x + w - 1, 0), (x + w - 1, 44)], fill=(104, 70, 44, 255))
    for cx in range(-2, W + 6, 9): blob(d, rng, cx, 1, 8, [(14, 62, 36), (22, 82, 44), (30, 98, 52)])
    d.rectangle((0, 45, W, H), fill=(34, 90, 44, 255))
    for x in range(0, W, 2):
        h = rng.randint(2, 5); c = rng.choice([(54, 130, 60), (74, 156, 70), (28, 76, 40)])
        d.line([(x, 45), (x + rng.randint(-1, 1), 45 - h)], fill=c + (255,))
    for fx in (20, 46):
        for k in range(-4, 5): line(d, (fx, 46), (fx + k * 2, 40 + abs(k)), (92, 170, 80))
    for mx in (27, 5):
        d.rectangle((mx, 47, mx + 3, 48), fill=(214, 60, 52, 255)); d.rectangle((mx + 1, 49, mx + 2, 51), fill=(240, 232, 210, 255)); px(d, mx + 1, 47, (255, 255, 255, 255))
    for _ in range(9): glow(im, rng.randrange(W), rng.randrange(14, 46), 3, (255, 245, 140), 160)
    ov = Image.new("RGBA", im.size, (0, 0, 0, 0)); od = ImageDraw.Draw(ov)
    od.rectangle((0, 42, W, H), fill=(210, 240, 220, 36)); im.alpha_composite(ov)

# ---------------------------------------------------------------- sticky yellow goo trees (blocky, with grass)
def goo(im, d, rng):
    vgrad(d, [(66, 74, 14), (150, 152, 24), (222, 212, 64)], 0, 46)
    Y, HI, SH = (255, 226, 38, 255), (255, 250, 160, 255), (206, 160, 8, 255)
    TR, TR2, TR3 = (58, 42, 20, 255), (40, 28, 14, 255), (84, 62, 32, 255)
    def block(x0, y0, x1, y1):
        d.rectangle((x0, y0, x1, y1), fill=Y); d.line([(x0, y0), (x1, y0)], fill=HI); d.line([(x0, y0), (x0, y1)], fill=HI)
        d.line([(x0, y1), (x1, y1)], fill=SH); d.line([(x1, y0), (x1, y1)], fill=SH)
    for x in (-1, 17, 36, 54):                                            # distant blocky trees
        d.rectangle((x, 22, x + 3, 46), fill=(108, 110, 26, 255)); d.rectangle((x - 4, 14, x + 7, 21), fill=(120, 122, 32, 255))
    d.rectangle((0, 44, W, 47), fill=(84, 162, 52, 255))                  # grass strip
    d.rectangle((0, 48, W, H), fill=(112, 78, 46, 255))                   # dirt
    for _ in range(46): px(d, rng.randrange(W), rng.randrange(48, H), rng.choice([(96, 66, 38), (132, 94, 58), (84, 58, 34)]) + (255,))
    for x in range(0, W, 3): d.rectangle((x, 44, x + 1, 44), fill=(118, 196, 74, 255)); px(d, x + 1, 43, (84, 162, 52, 255))
    def tree(cx, top):
        d.rectangle((cx - 2, top + 12, cx + 1, 45), fill=TR); d.line([(cx - 1, top + 12), (cx - 1, 45)], fill=TR2); d.line([(cx + 1, top + 12), (cx + 1, 45)], fill=TR3)
        for yy in range(top + 24, 44, 8): block(cx - 2, yy, cx + 1, yy + 3)       # goo clinging to the trunk
        block(cx - 5, top, cx + 4, top + 4); block(cx - 9, top + 5, cx + 8, top + 10); block(cx - 6, top + 11, cx + 5, top + 14)
        for dx in (-8, -4, 0, 4, 7):
            ln = rng.randint(4, 12); d.rectangle((cx + dx, top + 11, cx + dx + 1, top + 11 + ln), fill=Y)
            d.rectangle((cx + dx, top + 10 + ln, cx + dx + 1, top + 12 + ln), fill=SH); px(d, cx + dx, top + 11, HI)
    for cx, tp in ((12, 12), (31, 5), (50, 14)): tree(cx, tp)
    for x0, x1 in ((2, 12), (20, 28), (36, 46), (50, 58)):                  # goo spilled on the grass
        d.rectangle((x0, 42, x1, 45), fill=Y); d.line([(x0, 42), (x1, 42)], fill=HI); d.line([(x0, 46), (x1, 46)], fill=SH)
    for _ in range(7): bx, by = rng.randrange(2, W - 3), rng.randrange(30, 41); d.rectangle((bx, by, bx + 1, by + 1), fill=Y); px(d, bx, by, HI)
    for x in range(1, W, 5): d.line([(x, 43), (x, 41 - rng.randint(0, 1))], fill=(84, 162, 52, 255))

# ---------------------------------------------------------------- brown slums (drab, no coloured lights)
def slums(im, d, rng):
    vgrad(d, [(104, 82, 62), (146, 116, 82), (178, 148, 106)], 0, H)
    x = -3
    while x < W:
        w = rng.randint(8, 13); h = rng.randint(16, 26)
        d.rectangle((x, 44 - h, x + w, 46), fill=(98, 76, 56, 255)); x += w + 1
    for sx in (14, 41):
        for k in range(5): glow(im, sx + rng.randint(-3, 3), 12 - k * 2, 5, (150, 140, 130), 90)
    pal = [(110, 76, 48), (86, 62, 42), (126, 90, 58), (74, 56, 40)]
    x = -2; i = 0
    while x < W:
        w = rng.randint(10, 15); h = rng.randint(14, 22); top = 46 - h; c = pal[i % 4]; i += 1
        d.rectangle((x, top, x + w, 46), fill=c + (255,))
        for px_ in range(x + 2, x + w, 3): d.line([(px_, top + 1), (px_, 45)], fill=tuple(int(v * .8) for v in c) + (255,))
        d.polygon([(x - 1, top), (x + w + 1, top), (x + w + 1, top - 3), (x - 1, top - 1)], fill=(92, 94, 98, 255))
        for rx in range(x, x + w + 1, 2): px(d, rx, top - 2, (60, 62, 66, 255))
        d.rectangle((x + 2, top + 4, x + 3, top + 5), fill=(30, 22, 18, 255)); d.rectangle((x + 2, top + 4, x + 3, top + 4), fill=(62, 48, 38, 255))
        d.rectangle((x + w - 4, 40, x + w - 2, 46), fill=(40, 28, 20, 255))
        x += w + rng.randint(0, 2)
    line(d, (0, 20), (59, 25), (50, 40, 30))
    for cx, cc in ((8, (150, 140, 126)), (16, (176, 168, 152)), (25, (118, 110, 98)), (34, (150, 140, 126)), (45, (176, 168, 152)), (52, (110, 102, 92))):
        d.rectangle((cx, 21 + (cx // 12), cx + 3, 25 + (cx // 12)), fill=cc + (255,))
    d.rectangle((0, 46, W, H), fill=(72, 52, 36, 255))
    for _ in range(10): px(d, rng.randrange(W), rng.randrange(47, H), (54, 38, 26, 255))
    for px_, py_, w in ((6, 49, 10), (30, 51, 12), (46, 48, 9)):
        d.ellipse((px_, py_, px_ + w, py_ + 2), fill=(118, 108, 96, 255)); d.line([(px_ + 2, py_), (px_ + 4, py_)], fill=(150, 142, 130, 255))
    d.rectangle((55, 40, 58, 47), fill=(96, 62, 36, 255)); d.line([(55, 42), (58, 42)], fill=(60, 60, 64, 255))
    d.line([(1, 30), (1, 47)], fill=(50, 40, 30, 255)); d.rectangle((0, 29, 2, 30), fill=(60, 50, 40, 255))

# ---------------------------------------------------------------- mountain dwarven mines
def mines(im, d, rng):
    d.rectangle((0, 0, W, H), fill=(50, 42, 46, 255))
    for _ in range(260): px(d, rng.randrange(W), rng.randrange(H), rng.choice([(62, 52, 56), (38, 32, 36), (70, 60, 62)]) + (255,))
    d.rectangle((18, 18, 41, 46), fill=(20, 15, 18, 255)); d.ellipse((18, 10, 41, 26), fill=(20, 15, 18, 255))
    glow(im, 30, 34, 11, (255, 190, 100), 130); d.rectangle((29, 33, 31, 35), fill=(255, 220, 140, 255))
    for x in range(0, W, 4):
        h = rng.randint(2, 7); d.polygon([(x, 0), (x + 4, 0), (x + 2, h)], fill=(70, 60, 62, 255))
    d.rectangle((0, 45, W, H), fill=(66, 52, 44, 255))
    line(d, (27, 45), (16, H - 1), (150, 150, 160)); line(d, (33, 45), (44, H - 1), (150, 150, 160))
    for y, a, b in ((47, 25, 35), (49, 22, 38), (52, 17, 43)): d.line([(a, y), (b, y)], fill=(104, 70, 40, 255), width=1)
    wood, dk, lt = (122, 82, 44, 255), (84, 54, 28, 255), (150, 104, 60, 255)
    for x in (10, 47):
        d.rectangle((x, 12, x + 3, 46), fill=wood); d.line([(x + 3, 12), (x + 3, 46)], fill=dk); d.line([(x, 12), (x, 46)], fill=lt)
    d.rectangle((8, 9, 52, 12), fill=wood); d.line([(8, 12), (52, 12)], fill=dk); d.line([(8, 9), (52, 9)], fill=lt)
    line(d, (13, 12), (19, 18), (122, 82, 44), 2); line(d, (47, 12), (41, 18), (122, 82, 44), 2)
    for lx in (16, 42):
        d.line([(lx, 13), (lx, 19)], fill=(40, 40, 44, 255)); glow(im, lx, 22, 9, (255, 170, 50), 150)
        d.rectangle((lx - 1, 19, lx + 1, 23), fill=(255, 200, 70, 255)); d.rectangle((lx - 1, 18, lx + 1, 18), fill=(40, 40, 44, 255))
    for _ in range(7):
        x, y = rng.randint(1, 6), rng.randint(20, 40); d.rectangle((x, y, x + 1, y + 1), fill=(255, 210, 60, 255)); px(d, x, y, (255, 245, 170, 255))
    for x, y in ((53, 26), (55, 34), (57, 22)):
        d.polygon([(x, y + 5), (x + 1, y), (x + 3, y + 5)], fill=(80, 170, 255, 255)); px(d, x + 1, y + 1, (200, 235, 255, 255))
    glow(im, 55, 31, 6, (80, 170, 255), 90)

# ---------------------------------------------------------------- wizard spire
def spire(im, d, rng):
    vgrad(d, [(8, 6, 30), (36, 20, 86), (96, 56, 140), (170, 110, 170)], 0, H)
    for _ in range(28): px(d, rng.randrange(W), rng.randrange(0, 34), (240, 236, 255, 255))
    glow(im, 11, 10, 9, (210, 200, 255), 110); d.ellipse((7, 6, 15, 14), fill=(232, 228, 255, 255)); d.ellipse((10, 5, 18, 13), fill=(30, 16, 74, 255))
    for cx, cy, w in ((4, 49, 12), (22, 51, 14), (44, 50, 13), (58, 52, 10)):       # cloud sea
        d.ellipse((cx - w, cy - 4, cx + w, cy + 6), fill=(190, 156, 214, 255))
    for cx, cy, w in ((12, 52, 12), (34, 53, 12), (52, 51, 11)):
        d.ellipse((cx - w, cy - 3, cx + w, cy + 7), fill=(222, 196, 236, 255))
    for ix, iy, w in ((8, 32, 6), (53, 24, 5)):                                      # floating rocks with crystals
        d.polygon([(ix - w, iy), (ix + w, iy), (ix + w - 2, iy + 3), (ix, iy + 7), (ix - w + 2, iy + 3)], fill=(70, 58, 104, 255)); d.line([(ix - w, iy), (ix + w, iy)], fill=(110, 140, 90, 255))
        d.polygon([(ix - 1, iy), (ix, iy - 5), (ix + 1, iy)], fill=(120, 230, 255, 255)); glow(im, ix, iy - 3, 5, (120, 230, 255), 110)
    cx = 30
    d.ellipse((cx - 15, 22, cx + 15, 28), outline=(150, 110, 255, 255)); d.ellipse((cx - 13, 33, cx + 13, 39), outline=(110, 190, 255, 255))   # rings, back half
    stone, shade, lite = (52, 40, 92, 255), (32, 24, 64, 255), (96, 78, 148, 255)
    d.polygon([(cx - 9, 52), (cx + 9, 52), (cx + 4, 14), (cx - 4, 14)], fill=stone)
    d.polygon([(cx - 9, 52), (cx - 5, 52), (cx - 1, 14), (cx - 4, 14)], fill=shade); d.polygon([(cx + 5, 52), (cx + 9, 52), (cx + 4, 14), (cx + 2, 14)], fill=lite)
    for y in range(18, 52, 4): d.line([(cx - 8, y), (cx + 8, y)], fill=shade)
    for ly in (30, 42):
        d.rectangle((cx - 9, ly, cx + 9, ly + 1), fill=(84, 66, 130, 255)); d.rectangle((cx - 9, ly - 1, cx + 9, ly - 1), fill=lite)
    for y, c in ((18, (120, 220, 255)), (24, (255, 214, 120)), (34, (120, 220, 255)), (37, (255, 214, 120)), (46, (120, 220, 255))):
        d.rectangle((cx - 1, y, cx, y + 2), fill=c + (255,)); px(d, cx - 1, y - 1, c + (255,))
    d.rectangle((cx - 2, 47, cx + 1, 52), fill=(20, 14, 40, 255)); px(d, cx - 1, 46, (20, 14, 40, 255))
    d.polygon([(cx - 6, 14), (cx, 3), (cx + 6, 14)], fill=(112, 62, 172, 255)); d.polygon([(cx, 3), (cx + 6, 14), (cx + 2, 14)], fill=(150, 96, 210, 255))
    d.rectangle((cx - 7, 14, cx + 7, 15), fill=(84, 66, 130, 255))
    glow(im, cx, 2, 8, (120, 230, 255), 160); d.rectangle((cx - 1, 0, cx, 2), fill=(200, 250, 255, 255))
    d.arc((cx - 15, 22, cx + 15, 28), 0, 180, fill=(150, 110, 255, 255)); d.arc((cx - 13, 33, cx + 13, 39), 0, 180, fill=(110, 190, 255, 255))   # rings, front half
    for _ in range(14): px(d, rng.randrange(W), rng.randrange(8, 46), (190, 170, 255, 255))

BACKGROUNDS = {
    "skyline": ("Skyline", skyline),
    "blossom": ("Blossom Orchard", blossom),
    "hellfire": ("Hellfire", hellfire),
    "grove": ("Grove Forest", grove),
    "goo": ("Goo Trees", goo),
    "slums": ("Brown Slums", slums),
    "mines": ("Dwarven Mines", mines),
    "spire": ("Wizard Spire", spire),
}

def scene(key):
    im = Image.new("RGBA", (W, H), (0, 0, 0, 255)); d = ImageDraw.Draw(im)
    BACKGROUNDS[key][1](im, d, random.Random(key))
    return im

if __name__ == "__main__":
    S = 4; cols = 4
    sheet = Image.new("RGBA", (cols * (W * S + 10) + 10, 2 * (H * S + 10) + 10), (30, 30, 34, 255))
    for i, k in enumerate(BACKGROUNDS):
        sheet.paste(scene(k).resize((W * S, H * S), Image.NEAREST), (10 + (i % cols) * (W * S + 10), 10 + (i // cols) * (H * S + 10)))
    sheet.save("backgrounds-preview.png")
