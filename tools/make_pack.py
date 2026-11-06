#!/usr/bin/env python3
"""Generates the card game resource pack (textures, models, item definitions), a zip, and preview.png.
Usage: python3 tools/make_pack.py [output_dir]   (needs Pillow)"""
import json, os, sys, shutil, zipfile, hashlib
from PIL import Image, ImageDraw, ImageFont

OUT = sys.argv[1] if len(sys.argv) > 1 else "resourcepack"
NS = "sneakycards"
shutil.rmtree(OUT, ignore_errors=True)
def path(*p):
    f = os.path.join(OUT, *p); os.makedirs(os.path.dirname(f), exist_ok=True); return f
def wjson(p, obj): json.dump(obj, open(p, "w"), indent=1)

RARITIES = {"common": (157,157,157), "uncommon": (85,196,85), "rare": (74,140,255),
            "epic": (179,107,255), "legendary": (255,176,32), "mythic": (255,61,127)}
OUTLINE = (43,43,43, 255)
sheet = {}

def icon(name, fn):
    im = Image.new("RGBA", (16,16), (0,0,0,0)); d = ImageDraw.Draw(im); fn(d, im)
    im.save(path("assets", NS, "textures", "item", "menu", name + ".png")); sheet[name] = im
    wjson(path("assets", NS, "models", "item", "menu", name + ".json"),
          {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/menu/{name}"}})
    wjson(path("assets", NS, "items", "menu", name + ".json"),
          {"model": {"type": "minecraft:model", "model": f"{NS}:item/menu/{name}"}})

def name_tag(d, im):
    d.polygon([(3,5),(10,5),(14,8),(10,11),(3,11)], fill=(232,205,140), outline=(110,80,40)); d.point((5,8), fill=(110,80,40))
    d.line([(1,2),(4,5)], fill=(150,150,150))
def skin(d, im):
    d.rectangle((6,1,9,4), fill=(200,149,107)); d.rectangle((6,1,9,2), fill=(91,58,30))
    d.rectangle((5,5,10,10), fill=(47,159,168)); d.rectangle((3,5,4,10), fill=(200,149,107)); d.rectangle((11,5,12,10), fill=(200,149,107))
    d.rectangle((5,11,7,14), fill=(59,74,156)); d.rectangle((8,11,10,14), fill=(59,74,156))
def collection(d, im):
    for (y, c) in ((2,(190,60,60)), (6,(60,90,190)), (10,(60,160,90))):
        d.rectangle((2,y,13,y+3), fill=c, outline=OUTLINE); d.line([(12,y+1),(12,y+2)], fill=(240,230,200))
def luck(d, im):
    g = (60,180,80)
    for b in ((3,2,8,7),(8,2,13,7),(3,7,8,12),(8,7,13,12)): d.ellipse(b, fill=g, outline=(30,100,50))
    d.line([(8,10),(10,15)], fill=(40,120,60), width=1)
def strength(d, im):
    d.rectangle((4,7,11,8), fill=(170,170,170)); d.rectangle((1,4,3,11), fill=(90,90,100), outline=OUTLINE)
    d.rectangle((12,4,14,11), fill=(90,90,100), outline=OUTLINE)
def dexterity(d, im):
    d.polygon([(10,1),(4,9),(7,9),(6,15),(12,6),(8,6)], fill=(255,214,60), outline=(150,110,10))
def magicing(d, im):
    d.polygon([(8,1),(10,6),(15,8),(10,10),(8,15),(6,10),(1,8),(6,6)], fill=(170,100,255), outline=(80,40,150)); d.point((8,8), fill=(255,255,255))
def health(d, im):
    r = (220,50,60); d.ellipse((2,3,7,8), fill=r); d.ellipse((8,3,13,8), fill=r); d.polygon([(2,6),(13,6),(8,13)], fill=r); d.point((4,5), fill=(255,170,170))
def button(kind, big):
    def f(d, im):
        minus = kind == "minus"
        d.rectangle((1,1,14,14), fill=(170,50,50) if minus else (50,150,70), outline=(40,20,20) if minus else (15,50,25))
        w = (255,255,255)
        d.rectangle((4,7,11,8), fill=w)
        if minus and big: d.rectangle((4,4,11,5), fill=w); d.rectangle((4,10,11,11), fill=w)
        if not minus:
            d.rectangle((7,4,8,11), fill=w)
            if big: d.rectangle((3,3,4,4), fill=w); d.rectangle((11,11,12,12), fill=w)
    return f
def gem(c):
    def f(d, im):
        dark = tuple(int(x*.5) for x in c) + (255,); lt = tuple(min(255,int(x*1.3)) for x in c) + (255,)
        d.polygon([(8,1),(14,6),(8,15),(2,6)], fill=c+(255,), outline=dark)
        d.line([(2,6),(14,6)], fill=lt); d.line([(8,1),(6,6),(8,15)], fill=lt)
    return f
def packbox(on):
    def f(d, im):
        d.rectangle((2,5,13,13), fill=(150,105,60) if on else (100,80,60), outline=(70,45,20)); d.line([(2,8),(13,8)], fill=(70,45,20))
        if on: d.line([(5,11),(7,13)], fill=(80,230,120)); d.line([(7,13),(12,9)], fill=(80,230,120))
        else:  d.line([(5,10),(10,13)], fill=(230,80,80)); d.line([(10,10),(5,13)], fill=(230,80,80))
    return f
def dice(d, im):
    d.rectangle((2,2,13,13), fill=(240,240,240), outline=OUTLINE)
    for p in ((5,5),(10,5),(8,8),(5,10),(10,10)): d.point(p, fill=(30,30,30))
def reset(d, im):
    d.arc((3,3,12,12), 40, 330, fill=(120,190,255), width=2); d.polygon([(11,1),(14,6),(8,5)], fill=(120,190,255))
def create(d, im):
    d.ellipse((1,1,14,14), fill=(40,150,80)); d.line([(4,8),(7,11),(12,4)], fill=(255,255,255), width=2)
def close(d, im):
    d.line([(3,3),(12,12)], fill=(220,60,60), width=3); d.line([(12,3),(3,12)], fill=(220,60,60), width=3)
def gift(d, im):
    d.rectangle((2,6,13,13), fill=(200,70,170), outline=(90,20,70)); d.rectangle((7,6,8,13), fill=(255,214,60))
    d.polygon([(8,6),(4,2),(6,6)], fill=(255,214,60)); d.polygon([(8,6),(12,2),(10,6)], fill=(255,214,60))
def move(d, im):
    d.line([(3,13),(12,4)], fill=(190,190,200), width=2); d.line([(4,4),(13,13)], fill=(190,190,200), width=2)
    d.rectangle((2,12,4,14), fill=(150,100,50)); d.rectangle((11,12,13,14), fill=(150,100,50))
def prev(d, im): d.polygon([(11,2),(4,8),(11,14)], fill=(240,240,240), outline=OUTLINE)
def nxt(d, im): d.polygon([(5,2),(12,8),(5,14)], fill=(240,240,240), outline=OUTLINE)
def info(d, im):
    d.ellipse((1,1,14,14), fill=(60,130,220), outline=(20,50,110)); d.rectangle((7,7,8,11), fill=(255,255,255)); d.rectangle((7,4,8,5), fill=(255,255,255))
def blank(d, im):
    d.rectangle((0,0,15,15), fill=(70,70,78,255)); d.rectangle((0,0,15,0), fill=(90,90,98,255)); d.rectangle((0,15,15,15), fill=(50,50,56,255))

for n, f in [("name_tag",name_tag),("skin",skin),("collection",collection),("luck",luck),("strength",strength),
             ("dexterity",dexterity),("magicing",magicing),("health",health),
             ("minus",button("minus",False)),("minus_big",button("minus",True)),("plus",button("plus",False)),("plus_big",button("plus",True)),
             ("pack_on",packbox(True)),("pack_off",packbox(False)),("random",dice),("reset",reset),("create",create),
             ("close",close),("gift",gift),("move",move),("prev",prev),("next",nxt),("info",info),("blank",blank)]:
    icon(n, f)
for k, c in RARITIES.items(): icon("gem_" + k, gem(c))

# ---------- card frames: 128x128 texture, art 80x112, scene 60x54 per background ----------
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from backgrounds import BACKGROUNDS, scene
def card_front(rc, mythic, scn):
    base = Image.new("RGBA", (40,56)); d = ImageDraw.Draw(base)
    dark = tuple(int(x*.55) for x in rc); light = tuple(min(255,int(x*1.25)) for x in rc)
    d.rectangle((0,0,39,55), fill=rc); d.rectangle((1,1,38,54), outline=dark); d.rectangle((3,3,36,52), fill=(28,30,36))
    if mythic:
        for i in range(0,40,2): d.point((i,0), fill=light); d.point((i,55), fill=light)
        for i in range(0,56,2): d.point((0,i), fill=light); d.point((39,i), fill=light)
    d.rectangle((5,5,34,10), fill=dark); d.rectangle((6,6,23,9), fill=light)
    for x, c in ((6,(70,190,90)),(14,(220,70,70)),(22,(240,210,60)),(30,(170,100,255))): d.rectangle((x,41,x+4,45), fill=c)
    d.rectangle((6,48,33,50), fill=(60,20,25)); d.rectangle((6,48,26,50), fill=(220,50,60))
    im = base.resize((80,112), Image.NEAREST)
    im.paste(scn, (10,24))
    # the character's head is drawn on top by the item model (player_head layer using the item's profile)
    return im
def card_back():
    im = Image.new("RGBA", (40,56)); d = ImageDraw.Draw(im)
    d.rectangle((0,0,39,55), fill=(90,70,140)); d.rectangle((3,3,36,52), fill=(35,30,60))
    inner = Image.new("RGBA", (34,50), (35,30,60,255)); di = ImageDraw.Draw(inner)
    for i in range(-60, 60, 8):
        di.line([(i,0),(i+50,50)], fill=(60,50,100)); di.line([(i+50,0),(i,50)], fill=(60,50,100))
    di.polygon([(17,17),(23,25),(17,33),(11,25)], fill=(255,214,100)); im.paste(inner, (3,3))
    return im.resize((80,112), Image.NEAREST)
def save_card(name, art):
    full = Image.new("RGBA", (128,128), (0,0,0,0)); full.paste(art, (0,0))
    full.save(path("assets", NS, "textures", "item", name + ".png"))
save_card("card_back", card_back())
sheet["card_back"] = card_back()
for bk in BACKGROUNDS:
    scn = scene(bk)
    # submenu icon: scene squeezed to 14x14 with a dark border
    ic = Image.new("RGBA", (16,16), (20,20,24,255)); ic.paste(scn.resize((14,14), Image.BOX), (1,1))
    ic.save(path("assets", NS, "textures", "item", "menu", "bg_" + bk + ".png")); sheet["bg_" + bk] = ic
    wjson(path("assets", NS, "models", "item", "menu", "bg_" + bk + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/menu/bg_{bk}"}})
    wjson(path("assets", NS, "items", "menu", "bg_" + bk + ".json"), {"model": {"type": "minecraft:model", "model": f"{NS}:item/menu/bg_{bk}"}})
    for k, c in RARITIES.items():
        art = card_front(c, k == "mythic", scn); save_card(f"card_{bk}_{k}", art)
        if k == "mythic": sheet[f"card_{bk}"] = art

# The character's head: a flattened player head placed over the card's picture window.
# Each display context = the card's own transform followed by the head offset (HEAD_T, HEAD_S),
# so the head moves with the card in the GUI, hands, on the ground and on the mat.
import math
CARD_DISPLAY = {
    "gui": ([0,0,0], [0,0,0], 1.1),
    "firstperson_righthand": ([0,-10,0], [1.5,3,1], 0.38),
    "firstperson_lefthand": ([0,10,0], [1.5,3,1], 0.38),
    "thirdperson_righthand": ([0,0,0], [0,3,1], 0.4),
    "thirdperson_lefthand": ([0,0,0], [0,3,1], 0.4),
    "fixed": ([0,0,0], [0,0,0], 1.0),
    "ground": ([0,0,0], [0,0,0], 0.5),
}
# The character's head is the player_head special model, which draws the head centred on x = z = 0 with
# y from -8 to 0 (model units). Which way it faces has been hard to pin down from screenshots, so the pack has
# four head orientation modes; the plugin picks one per card item (config card-face-mode a/b/c/d,
# /cardadmin facetest gives one card of each). Each is moved to the picture window centre (8, 8.6) and squashed flat
# just in front of the card face (z 8.25).
HEAD_P0, HEAD_S, HEAD_TARGET = (0.0, -4.0, 0.0), (0.625, 0.625, 0.01), (8.0, 8.6, 8.32)
def rot_y(v, deg):
    r = math.radians(deg); x, y, z = v
    return (x*math.cos(r) + z*math.sin(r), y, -x*math.sin(r) + z*math.cos(r))
TURNS = {  # head turn: (applied to the head, euler XYZ for a card turned theta about Y)
    "y": (lambda v: (-v[0], v[1], -v[2]), lambda th: [0, th + 180, 0]),     # 180 about Y
    "x": (lambda v: (v[0], -v[1], -v[2]), lambda th: [180, -th, 0]),        # 180 about X
    "z": (lambda v: (-v[0], -v[1], v[2]), lambda th: [180, 180 - th, 0]),   # 180 about Z
}
# Screenshots showed the 180-about-Y head upright in hand/ground but upside down on item displays (the "fixed"
# view used on the mat and in the holo reveal), so mode "a" (the default) uses X for "fixed" and Y elsewhere.
FACE_MODES = {"a": lambda ctx: "x" if ctx == "fixed" else "y", "b": lambda ctx: "y", "c": lambda ctx: "x", "d": lambda ctx: "z"}
for mode, pick in FACE_MODES.items():
    disp = {}
    for ctx, (rot, tr, c) in CARD_DISPLAY.items():
        turn, euler = TURNS[pick(ctx)]
        local = turn(tuple(HEAD_S[i] * (HEAD_P0[i] - 8) for i in range(3)))
        th_vec = tuple(HEAD_TARGET[i] - 8 - local[i] for i in range(3))
        off = rot_y(th_vec, rot[1])
        disp[ctx] = {"rotation": euler(rot[1]), "translation": [round(tr[i] + c*off[i], 4) for i in range(3)],
                     "scale": [round(c*HEAD_S[i], 4) for i in range(3)]}
    wjson(path("assets", NS, "models", "item", "card_head" + ("" if mode == "a" else "_" + mode) + ".json"),
          {"textures": {"particle": f"{NS}:item/card_back"}, "gui_light": "front", "display": disp})

def head_layer():
    """Picks the head orientation from the card item's custom_model_data string (a/b/c/d)."""
    spec = lambda m: {"type": "minecraft:special", "base": f"{NS}:item/card_head" + ("" if m == "a" else "_" + m), "model": {"type": "minecraft:player_head"}}
    return {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
            "cases": [{"when": m, "model": spec(m)} for m in ("b", "c", "d")], "fallback": spec("a")}

wjson(path("assets", NS, "models", "item", "card_base.json"), {
    "textures": {"particle": f"{NS}:item/card_back"},
    "elements": [{"from": [3,1,7.75], "to": [13,15,8.25], "faces": {
        # the GUI, hand and ground views look at the south face, and the plugin lays cards on the mat with south facing up
        "south": {"uv": [0,0,10,14], "texture": "#front"},
        "north": {"uv": [0,0,10,14], "texture": "#back"}}}],
    "display": {
        "gui": {"rotation": [0,0,0], "scale": [1.1,1.1,1.1]},
        **{ctx: {"rotation": rot, "translation": tr, "scale": [c, c, c]} for ctx, (rot, tr, c) in CARD_DISPLAY.items() if ctx != "gui"}},
    "gui_light": "front"})
# item and trap cards: same flat card model, no head layer
from item_cards import ITEMS, item_front
for ik, (irar, ikind) in ITEMS.items():
    n = f"item_{ik}"
    art = item_front(ik, RARITIES[irar], irar == "mythic"); save_card(n, art); sheet[n] = art
    wjson(path("assets", NS, "models", "item", n + ".json"),
          {"parent": f"{NS}:item/card_base", "textures": {"front": f"{NS}:item/{n}", "back": f"{NS}:item/card_back", "particle": f"{NS}:item/{n}"}})
    wjson(path("assets", NS, "items", n + ".json"), {"model": {"type": "minecraft:model", "model": f"{NS}:item/{n}"}})

# holographic cards: a translucent, slowly shifting rainbow foil laid just in front of the card face (no glint)
import colorsys
FOIL_FRAMES, FOIL = 12, 32
foil = Image.new("RGBA", (FOIL, FOIL * FOIL_FRAMES))
fp = foil.load()
for f in range(FOIL_FRAMES):
    for y in range(FOIL):
        for x in range(FOIL):
            t = ((x + y) / (2 * FOIL) + f / FOIL_FRAMES) % 1.0
            r, g, b = colorsys.hsv_to_rgb(t, 0.55, 1.0)
            band = 0.5 + 0.5 * math.sin((x - y) * 0.45 + f * (2 * math.pi / FOIL_FRAMES))
            fp[x, f * FOIL + y] = (int(r * 255), int(g * 255), int(b * 255), int(28 + 44 * band))
foil.save(path("assets", NS, "textures", "item", "holo_foil.png"))
wjson(path("assets", NS, "textures", "item", "holo_foil.png.mcmeta"), {"animation": {"frametime": 3, "interpolate": True}})
wjson(path("assets", NS, "models", "item", "card_holo_overlay.json"), {
    "parent": f"{NS}:item/card_base", "textures": {"foil": f"{NS}:item/holo_foil", "particle": f"{NS}:item/holo_foil"},
    "elements": [{"from": [3, 1, 8.27], "to": [13, 15, 8.27], "faces": {"south": {"uv": [0, 0, 16, 16], "texture": "#foil"}}}]})
for bk in BACKGROUNDS:
    for k in RARITIES:
        n = f"card_{bk}_{k}"
        wjson(path("assets", NS, "items", n + "_holo.json"), {"model": {"type": "minecraft:composite", "models": [
            {"type": "minecraft:model", "model": f"{NS}:item/{n}"},
            {"type": "minecraft:model", "model": f"{NS}:item/card_holo_overlay"},
            head_layer()]}})

# a deck on the mat: a box of card backs; the plugin scales its thickness to the cards left
edge = Image.new("RGBA", (16, 16), (236, 228, 208, 255)); ed = ImageDraw.Draw(edge)
for y in range(0, 16, 2): ed.line([(0, y), (15, y)], fill=(190, 178, 156, 255))
edge.save(path("assets", NS, "textures", "item", "deck_edge.png"))
E = {"uv": [0, 0, 16, 16], "texture": "#edge"}
wjson(path("assets", NS, "models", "item", "deck_stack.json"), {
    "parent": f"{NS}:item/card_base",
    "textures": {"back": f"{NS}:item/card_back", "edge": f"{NS}:item/deck_edge", "particle": f"{NS}:item/card_back"},
    "elements": [{"from": [3, 1, 6], "to": [13, 15, 10], "faces": {
        "south": {"uv": [0, 0, 10, 14], "texture": "#back"}, "north": {"uv": [0, 0, 10, 14], "texture": "#back"},
        "east": E, "west": E, "up": E, "down": E}}]})
wjson(path("assets", NS, "items", "deck_stack.json"), {"model": {"type": "minecraft:model", "model": f"{NS}:item/deck_stack"}})

# face-down card shown on the mat until the battle starts (card back on both sides, no head layer)
wjson(path("assets", NS, "models", "item", "card_facedown.json"),
      {"parent": f"{NS}:item/card_base", "textures": {"front": f"{NS}:item/card_back", "back": f"{NS}:item/card_back", "particle": f"{NS}:item/card_back"}})
wjson(path("assets", NS, "items", "card_facedown.json"), {"model": {"type": "minecraft:model", "model": f"{NS}:item/card_facedown"}})
for bk in BACKGROUNDS:
    for k in RARITIES:
        n = f"card_{bk}_{k}"
        wjson(path("assets", NS, "models", "item", n + ".json"),
              {"parent": f"{NS}:item/card_base", "textures": {"front": f"{NS}:item/{n}", "back": f"{NS}:item/card_back", "particle": f"{NS}:item/{n}"}})
        wjson(path("assets", NS, "items", n + ".json"), {"model": {"type": "minecraft:composite", "models": [
            {"type": "minecraft:model", "model": f"{NS}:item/{n}"},
            head_layer()]}})

# ---------- game mats: a flat square plate 16 x 16 model units; the plugin scales it (3, 1, 3) to 3 x 3 blocks ----------
from mats import MATS
wjson(path("assets", NS, "models", "item", "mat_base.json"), {
    "textures": {"particle": f"{NS}:item/mat_classic"},
    "elements": [{"from": [0, 7.5, 0], "to": [16, 8.5, 16], "faces": {
        "up": {"uv": [0,0,16,16], "texture": "#0"}, "down": {"uv": [0,0,16,16], "texture": "#0"},
        "north": {"uv": [0,0,16,1], "texture": "#0"}, "south": {"uv": [0,0,16,1], "texture": "#0"},
        "east": {"uv": [0,0,1,16], "texture": "#0"}, "west": {"uv": [0,0,1,16], "texture": "#0"}}}],
    "display": {
        "gui": {"rotation": [55,0,0], "translation": [0,-1,0], "scale": [1.05,1.05,1.05]},
        "firstperson_righthand": {"rotation": [35,0,0], "translation": [0,2,2], "scale": [0.45,0.45,0.45]},
        "thirdperson_righthand": {"rotation": [0,0,0], "translation": [0,2,1], "scale": [0.4,0.4,0.4]},
        "ground": {"rotation": [0,0,0], "scale": [0.4,0.4,0.4]},
        "fixed": {"rotation": [0,0,0], "scale": [1,1,1]}}})
for mk, (mlabel, fn) in MATS.items():
    art = fn(); art.save(path("assets", NS, "textures", "item", f"mat_{mk}.png")); sheet[f"mat_{mk}"] = art.resize((64, 64), Image.LANCZOS)
    wjson(path("assets", NS, "models", "item", f"mat_{mk}.json"), {"parent": f"{NS}:item/mat_base", "textures": {"0": f"{NS}:item/mat_{mk}", "particle": f"{NS}:item/mat_{mk}"}})
    wjson(path("assets", NS, "items", f"mat_{mk}.json"), {"model": {"type": "minecraft:model", "model": f"{NS}:item/mat_{mk}"}})

# ---------- booster packs: the standard pack ("pack") plus the other varieties ("pack_<key>") ----------
from packs import packs as _packs, sprite as _sprite
for pk, art in _packs().items():
    name = "pack" if pk == "standard" else "pack_" + pk
    sp = _sprite(art); sp.save(path("assets", NS, "textures", "item", name + ".png")); sheet[name] = sp
    wjson(path("assets", NS, "models", "item", name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
    wjson(path("assets", NS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})

# ---------- 3D binder (colours match the binder menu in tools/gui.py) ----------
# 64 x 64 atlas, 4 px per model unit. No two faces share a plane, so nothing z-fights (flickers).
import random as _r
LEA, LEA_D, LEA_L, GOLDC, PAGE, PAGE_L = (104,44,40), (64,24,22), (138,64,56), (214,176,112), (243,235,214), (226,214,188)
bt = Image.new("RGBA", (64,64), (0,0,0,0)); d = ImageDraw.Draw(bt); rg = _r.Random(5)
def leather(box, base):
    d.rectangle(box, fill=base)
    for _ in range((box[2]-box[0]+1)*(box[3]-box[1]+1)//7):
        d.point((rg.randint(box[0],box[2]), rg.randint(box[1],box[3])), fill=rg.choice([LEA_D, LEA_L, (92,38,34)]))
# front/back cover 40 x 56 at (0,0)
leather((0,0,39,55), LEA)
d.rectangle((0,0,39,55), outline=LEA_D)
for x in range(4, 36, 3): d.point((x,3), fill=GOLDC); d.point((x,52), fill=GOLDC)          # stitching
for y in range(4, 52, 3): d.point((3,y), fill=GOLDC); d.point((36,y), fill=GOLDC)
for (cx, cy, sx, sy) in ((0,0,1,1),(39,0,-1,1),(0,55,1,-1),(39,55,-1,-1)):                 # gold corners
    d.polygon([(cx,cy),(cx+6*sx,cy),(cx,cy+6*sy)], fill=GOLDC)
d.rectangle((12,15,27,38), fill=GOLDC); d.rectangle((14,17,25,36), fill=PAGE)               # card emblem
d.rectangle((16,19,23,28), fill=(196,96,90)); d.rectangle((16,30,23,31), fill=PAGE_L); d.rectangle((16,33,21,34), fill=PAGE_L)
# spine 16 x 56 at (40,0) with metal ring bands like the menu
leather((40,0,55,55), (84,34,30))
for y in (8, 25, 42):
    d.rectangle((40,y,55,y+5), fill=(196,200,208)); d.line([(40,y+1),(55,y+1)], fill=(236,238,242)); d.line([(40,y+5),(55,y+5)], fill=(110,114,122))
# page edges 8 x 56 at (56,0)
d.rectangle((56,0,63,55), fill=PAGE)
for y in range(1,56,2): d.line([(56,y),(63,y)], fill=PAGE_L)
# plain leather for thin cover edges at (40,56)
d.rectangle((40,56,47,63), fill=LEA_D)
bt.save(path("assets", NS, "textures", "item", "binder.png")); sheet["binder"] = bt.resize((16,16), Image.LANCZOS)
COVER, SPINE_UV, PAGES_UV, EDGE = [0,0,10,14], [10,0,14,14], [14,0,16,14], [10,14,12,16]
def fx(**kw): return {k: {"uv": v, "texture": "#0"} for k, v in kw.items()}
wjson(path("assets", NS, "models", "item", "binder.json"), {
    "textures": {"0": f"{NS}:item/binder", "particle": f"{NS}:item/binder"},
    "elements": [
        # spine (west); its east face is hidden against the covers, so it is left out
        {"from": [3,1,6], "to": [4,15,10], "faces": fx(west=SPINE_UV, north=EDGE, south=EDGE, up=EDGE, down=EDGE)},
        # back cover (north) and front cover (south); no west face (that is where the spine is)
        {"from": [4,1,6], "to": [13,15,7], "faces": fx(north=COVER, south=EDGE, east=EDGE, up=EDGE, down=EDGE)},
        {"from": [4,1,9], "to": [13,15,10], "faces": fx(south=COVER, north=EDGE, east=EDGE, up=EDGE, down=EDGE)},
        # pages: inset from the cover edges; only the visible edges get faces
        {"from": [4,1.5,7], "to": [12.5,14.5,9], "faces": fx(east=PAGES_UV, up=[14,0,16,2], down=[14,0,16,2])}],
    "display": {
        "gui": {"rotation": [20,-30,0], "scale": [0.85,0.85,0.85]},
        "firstperson_righthand": {"rotation": [0,-30,0], "translation": [0,3,2], "scale": [0.6,0.6,0.6]},
        "thirdperson_righthand": {"rotation": [0,0,0], "translation": [0,3,1], "scale": [0.5,0.5,0.5]},
        "ground": {"rotation": [0,0,0], "translation": [0,2,0], "scale": [0.45,0.45,0.45]},
        "fixed": {"rotation": [0,180,0], "scale": [1,1,1]}}})
wjson(path("assets", NS, "items", "binder.json"), {"model": {"type": "minecraft:model", "model": f"{NS}:item/binder"}})

# ---------- binder menu background, drawn as a font glyph in the menu title ----------
# \ue000 = the 176 x 222 GUI image; \uf001 moves 8 px left (to the GUI edge), \uf002 moves back 169 px so the
# title text starts where it normally would. BinderMenu.java builds the title from these characters.
from gui import binder as binder_gui
binder_gui().save(path("assets", NS, "textures", "font", "binder_gui.png"))
wjson(path("assets", NS, "font", "gui.json"), {"providers": [
    {"type": "bitmap", "file": f"{NS}:font/binder_gui.png", "height": 222, "ascent": 13, "chars": ["\ue000"]},
    {"type": "space", "advances": {"\uf001": -8, "\uf002": -169}}]})

# pack.mcmeta: resource pack format 88 = Minecraft 26.2
wjson(path("pack.mcmeta"), {"pack": {"description": "Card game textures and models", "min_format": [88,0], "max_format": [99,0]}})

zpath = OUT.rstrip("/") + ".zip"
with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
    for root, _, files in os.walk(OUT):
        for f in files:
            full = os.path.join(root, f); z.write(full, os.path.relpath(full, OUT))
print("zip:", zpath, "sha1:", hashlib.sha1(open(zpath, "rb").read()).hexdigest())

S, cols = 6, 8
names = list(sheet); cw, ch = 16*S + 12, 160
rows = (len(names) + cols - 1) // cols
pv = Image.new("RGBA", (cols*cw + 12, rows*ch + 12), (40,42,46,255)); dd = ImageDraw.Draw(pv); font = ImageFont.load_default()
for i, n in enumerate(names):
    im = sheet[n]; sc = im.resize((im.width*S, im.height*S) if im.width == 16 else (int(im.width*1.2), int(im.height*1.2)), Image.NEAREST)
    x, y = 12 + (i % cols)*cw, 12 + (i // cols)*ch
    pv.alpha_composite(sc, (x, y)); dd.text((x, y + max(sc.height, 16*S) + 3), n, fill=(220,220,220,255), font=font)
pv.save("preview.png")
