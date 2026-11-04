# SneakyCards (Paper 26.2, Java 25)

Collectible mannequin cards with a binder, packs, game mats and battles.

## Build
No JDK 25 handy? Push this folder to a GitHub repo: `.github/workflows/build.yml` builds the jar and attaches it to the run (Actions tab, artifact `SneakyCards-jar`). Compiler errors show in the run log.
    gradle build            # needs JDK 25; jar ends up in build/libs/
Drop the jar in `plugins/`. First start creates `plugins/SneakyCards/cards.yml` with the demo card "Vince Sunseeker".

## Resource pack
    python3 tools/make_pack.py resourcepack      # needs Pillow; writes resourcepack.zip and prints its sha1
Host the zip at a public URL, then set `resource-pack.url` and `resource-pack.sha1` in `plugins/SneakyCards/config.yml`
and run `/cardadmin reload`. Players are asked to accept it on join (`/cardadmin pack` resends it).
Re-run it whenever you update the plugin: item models are named `card_<background>_<rarity>` and `mat_<variant>`.

## Backgrounds
Skyline, Blossom Orchard, Hellfire, Grove Forest, Goo Trees, Brown Slums, Dwarven Mines, Wizard Spire.
In `/cardadmin` click the Background slot (top row, fourth slot). Art lives in `tools/backgrounds.py`.
Cards saved with the old names (sakura, arcane) load as Blossom Orchard and Wizard Spire.

## Game mats
Three looks: Classic Felt, Royal Purple, Neon Gradient (cyan to pink). Art lives in `tools/mats.py`.
    /cardadmin mat <classic|purple|neon> [player]    give a mat item
    /cardadmin mat remove                            remove the nearest mat
Right-click the top of a block with the mat item to place it (it covers 3 x 3 blocks). The player who places it
stands at the near end. Sneak + right-click the middle of an empty mat to pick it up.
Mats are not saved: they disappear on restart or when the chunk unloads, and cards on them go back to their owners.

## How a battle works
Timing is set in `config.yml`: `turn-menu-delay-ticks` (default 2 s before the turn menu opens) and `pause-after-attack-ticks`.
Upgrading from 0.1.x? Delete the old `hover-height` and `mannequin-scale` lines from config.yml; the new keys are `mannequin-hover` and `mannequin-size`.
1. Hold a card and right-click a slot on the mat. The first card claims that side. Fill all 3 slots on your side.
2. The battle starts when both sides are full. The side with the higher total Dexterity goes first; a tie is a coin flip.
3. On your turn a menu opens: click one of your cards, one of its 2 moves, then an enemy card. The move deals damage equal to its stat.
4. The attacking card's Luck is its percent chance to deal double damage.
5. A card at 0 Health is knocked out. Knock out all 3 enemy cards to win. Cards go back to their owners afterwards.

Players: `/sneakycards turn` reopens the menu (or right-click one of your cards), `/sneakycards forfeit`, `/sneakycards help`.
Sneak + right-click one of your cards before the battle starts to take it back.

## Skins
Set **Arms** in `/cardadmin` (right-hand column) to Slim for skins with 3-pixel arms, or Classic for 4-pixel arms. It only matters for texture links; username skins already carry their arm type. In `cards.yml` it is `slim-arms: true|false`.
A card with skin `placeholder` uses `placeholder-skin` from `config.yml`. The placeholder art is `tools/placeholder_skin.png`:
upload it to mineskin.org and paste the resulting `textures.minecraft.net` link into the config.
Skin links must be `https://textures.minecraft.net/texture/<hash>` (upload the PNG to mineskin.org to get one) or a username.
Edit `skin:` in `cards.yml` for the demo card, then `/cardadmin reload`.

## Admin commands (permission sneakycards.admin)
    /cardadmin                         open the card creator
    /cardadmin give <player> pack [n]  |  give <player> card <id> [n]
    /cardadmin binder [player]         give a binder (players also have /binder)
    /cardadmin mat ...                 see above
    /cardadmin place <id>              test: show a card without a mat
    /cardadmin move <1|2> | hurt       test the animations on your nearest card
    /cardadmin clear | list | reload | pack

## Not tested yet
Written against the Paper 26.2 API but not compiled or run on a server. Things most likely to need small tweaks:
Mannequin profile from a texture link, the flat card angle, particle names, and the ItemDisplay scale used to size the mat.
