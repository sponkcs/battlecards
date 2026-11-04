"""Binder menu background (176 x 222, the size of a 6-row chest GUI). Drawn as a font glyph in the
menu title so it covers the vanilla chest texture. Slot positions must match the vanilla layout:
container slots start at (7, 17) and are 18 px apart; player inventory at y 139, hotbar at y 197."""
from PIL import Image, ImageDraw
import random

W, H = 176, 222

def slot_vanilla(d, x, y):
    d.rectangle((x, y, x + 17, y + 17), fill=(139, 139, 139))
    d.line([(x, y), (x + 16, y)], fill=(55, 55, 55)); d.line([(x, y), (x, y + 16)], fill=(55, 55, 55))
    d.line([(x + 1, y + 17), (x + 17, y + 17)], fill=(255, 255, 255)); d.line([(x + 17, y + 1), (x + 17, y + 17)], fill=(255, 255, 255))

def pocket(d, x, y):
    # clear plastic card sleeve: thin seam, pale inside, open top lip and a small shine
    d.rectangle((x, y, x + 17, y + 17), fill=(226, 232, 236))
    d.rectangle((x, y, x + 17, y + 17), outline=(150, 160, 170))
    d.line([(x + 1, y + 2), (x + 16, y + 2)], fill=(186, 196, 204))          # pocket opening
    for i in range(3): d.point((x + 13 + i, y + 12 - i), fill=(255, 255, 255))

def binder():
    rng = random.Random(3)
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    leather, dark, light = (104, 44, 40), (64, 24, 22), (138, 64, 56)
    # upper half: an open binder, two pages with the ring spine down the middle column
    d.rounded_rectangle((0, 0, W - 1, 127), radius=5, fill=leather, outline=dark)
    for _ in range(420):                                                     # leather grain
        x, y = rng.randrange(2, W - 2), rng.randrange(2, 126)
        d.point((x, y), fill=rng.choice([dark + (255,), light + (255,), (92, 38, 34, 255)]))
    for x in range(4, W - 4, 4):                                             # stitching
        d.point((x, 2), fill=(214, 176, 112)); d.point((x, 125), fill=(214, 176, 112))
    page, line, edge = (243, 235, 214), (234, 223, 198), (190, 172, 140)
    left, right = (4, 14, 82, 107), (94, 14, W - 5, 107)                     # spine column is x 79..96
    for box in (left, right):
        d.rectangle(box, fill=page, outline=edge)
        for y in range(18, 107, 6): d.line([(box[0] + 1, y), (box[2] - 1, y)], fill=line)
    d.rectangle((83, 14, 93, 107), fill=dark)                                # spine
    d.line([(82, 14), (82, 107)], fill=(160, 140, 110)); d.line([(94, 14), (94, 107)], fill=(160, 140, 110))
    for r in range(5):
        for c in range(9):
            if c != 4: pocket(d, 7 + 18 * c, 17 + 18 * r)
    for r in range(5):                                                       # metal rings across the spine
        y = 17 + 18 * r + 9
        d.rounded_rectangle((77, y - 3, 99, y + 3), radius=3, fill=(196, 200, 208), outline=(96, 100, 110))
        d.line([(80, y - 1), (96, y - 1)], fill=(236, 238, 242))
        d.rectangle((81, y, 95, y + 1), fill=(150, 154, 162))
    # bottom strip: navigation row (prev / page / next) with gold corners
    d.rectangle((4, 108, W - 5, 124), fill=(84, 34, 30), outline=dark)
    for x in (4, W - 7):
        d.rectangle((x, 108, x + 2, 110), fill=(214, 176, 112)); d.rectangle((x, 122, x + 2, 124), fill=(214, 176, 112))
    # lower half: normal player inventory so it still feels like Minecraft
    d.rounded_rectangle((0, 128, W - 1, H - 1), radius=4, fill=(198, 198, 198), outline=(85, 85, 85))
    d.line([(1, 129), (W - 3, 129)], fill=(255, 255, 255)); d.line([(1, 129), (1, H - 3)], fill=(255, 255, 255))
    for r in range(3):
        for c in range(9): slot_vanilla(d, 7 + 18 * c, 139 + 18 * r)
    for c in range(9): slot_vanilla(d, 7 + 18 * c, 197)
    return im

if __name__ == "__main__":
    binder().resize((W * 3, H * 3), Image.NEAREST).save("binder-gui-preview.png")
