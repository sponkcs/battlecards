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

## Packs and the binder
Right-click a pack to open it. The cards go into your inventory (extras drop at your feet).
Right-click a binder to open it, then click a card in your inventory to store it (shift-click stores the whole stack).
The binder's Deck builder button builds your battle deck from what's stored there.
Click a card in the binder to take one out. The binder background needs the resource pack.

## Game mats
Three looks: Classic Felt, Royal Purple, Neon Gradient (cyan to pink). Art lives in `tools/mats.py`.
    /cardadmin mat <classic|purple|neon> [player]    give a mat item
    /cardadmin mat remove                            remove the nearest mat
Right-click the top of a block with the mat item to place it (it covers 3 x 3 blocks). The player who places it
stands at the near end. Each side has three character slots on the owner's left and a deck slot on their right;
the middle strip holds each side's discard pile (in front of its deck) and up to two face-down traps.
Sneak + right-click the middle of an empty mat to pick it up. Mats are not saved across restarts.
## Decks
Open your binder and click **Deck builder**. A deck is 15 cards from your binder: at least 6 characters, at most
2 copies of a card (holo and normal count together), and a deck cost of 35 or less (Common 1 ... Mythic 6).
Cards stay in your binder; the deck only lists which ones to bring. `/sc deck` opens the builder too.

## How a battle works
1. Right-click your side of a mat to place your deck there (a card stack appears in your deck slot).
   When both decks are down, both are shuffled. `/sc solo` plays the computer instead.
2. Each player draws 5 (always at least 2 characters), puts up to 3 characters face down, and clicks Ready.
   The cards flip (Epic and higher with a rarity flair). Higher total Dexterity goes first; the other side starts with +1 energy.
3. Each turn: draw 1 (an empty deck gives your rival a point instead), +2 energy (max 6). Then play up to
   1 character into an empty slot and 1 item or trap, and attack once. Attacking ends your turn; /sc end passes.
4. Moves cost energy by power: 1 (up to 12), 2 (up to 24), 3 (above). Luck moves refund 1. Each card acts once per round.
5. Types: a card's type is its highest of Strength / Dexterity / Magicing. Strength beats Magicing, Magicing beats
   Dexterity, Dexterity beats Strength (x1.5 / x0.75). Damage = power x 0.6 x type (x1.5 on a crit; Luck is the crit chance).
6. A knocked-out card goes to its owner's discard pile and scores 1 point. First to 3 points wins; a player with no
   characters left anywhere loses. Hand limit 7.
7. Items (22): quick items heal, give energy, draw cards or boost an attack; traps are set face down and the first one
   that applies springs on the rival's next attack. One item per turn; using one doesn't end your turn.
All numbers are in the `rules:` section of config.yml. Timing: `turn-menu-delay-ticks`, `pause-after-attack-ticks`.

### Strongest deck right now: "Brick Wall"
Found by simulating thousands of games between legal decks (the computer plays it in `/sc solo`; `/cardadmin testdeck`
gives it to you). It won about 96% against random decks and 69% against the other top decks.
Brick Bronson x2, Rusty Rourke x2, Tunnel Tess, Nyx Nightwire, Energy Drink x2, Herbal Tonic x2, Bandage, Decoy,
Spike Trap, Guardian Angel, Whetstone (deck cost 33).
## Holographic cards
Each character card from a pack has a `holo-chance` (default 3%, about one holo every 11 packs) to be holographic. Pulling one plays a reveal only you see: the card rises in front of you spinning inside a rainbow ring of sparkles, bursts with a "✦ HOLOGRAPHIC ✦" title, then flies into your inventory. Holo cards have a shifting rainbow foil on the card and, in battle, a mannequin that floats a little higher, sways slowly and has two colour-shifting sparkles drifting around it. Holo cards are stored separately in the binder and come back as holo after battles. `/cardadmin give <player> card <id> 1 holo` gives one.

## Titles
Each card can have an optional title (for example *off-duty*), shown in italics under the name. It is only for looks and has no effect on battles. Older cards saved with a `collection:` keep it as their title.

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
    /cardadmin testdeck [player]       give the Brick Wall deck (adds the cards to the binder and sets it as the deck)
    /cardadmin cards                   list every card: click to edit, shift-click twice to delete
    /cardadmin edit <id>               open a card in the creator; the green button becomes Save changes
    /cardadmin delete <id> confirm     delete a card (it disappears from packs and binders)
    /cardadmin place <id>              test: show a card without a mat
    /cardadmin move <1|2> | hurt       test the animations on your nearest card
    /cardadmin clear | list | reload | pack

## Not tested yet
Written against the Paper 26.2 API but not compiled or run on a server. Things most likely to need small tweaks:
Mannequin profile from a texture link, the flat card angle, particle names, and the ItemDisplay scale used to size the mat.
