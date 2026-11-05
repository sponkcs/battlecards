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
Click a card in the binder to take one out. The binder background needs the resource pack.

## Game mats
Three looks: Classic Felt, Royal Purple, Neon Gradient (cyan to pink). Art lives in `tools/mats.py`.
    /cardadmin mat <classic|purple|neon> [player]    give a mat item
    /cardadmin mat remove                            remove the nearest mat
Right-click the top of a block with the mat item to place it (it covers 3 x 3 blocks). The player who places it
stands at the near end. Sneak + right-click the middle of an empty mat to pick it up.
Mats are not saved: they disappear on restart or when the chunk unloads, and cards on them go back to their owners.

## How a battle works
1. Each side plays 3 **different** cards. Team cost is capped at 10: Common 1, Uncommon 2, Rare 3, Epic 4, Legendary 5, Mythic 6.
2. Cards are played face down. When both sides are full they flip over one by one (Epic and higher with a rarity flair) and the battle starts. Higher total Dexterity goes first (tie: coin flip); the other side starts with +1 energy.
3. At the start of your turn you gain 2 energy (max 6). Moves cost energy by power: 1 (up to 12), 2 (up to 24), 3 (above). Luck moves refund 1. Rest skips the turn for +1 extra energy.
4. Each card acts once per round: all your living cards must act before any can act again.
5. Types: a card's type is its highest of Strength / Dexterity / Magicing. Strength beats Magicing, Magicing beats Dexterity, Dexterity beats Strength (x1.5 / x0.75).
6. Damage = move power x 0.6 x type multiplier. Luck is the crit chance (x1.5). Knock out all 3 enemy cards to win.
7. Stat budget: in /cardadmin the five stats add up to at most Common 72, Uncommon 78, Rare 84, Epic 90, Legendary 96, Mythic 104.
8. **Item cards** (2 in every pack): before the battle, right-click your side of the mat (or one of your cards) with up to 2 item cards. Once both teams are full there is a 5-second countdown to add them.
   On your turn use one item from the turn menu (costs energy, doesn't end your turn). Quick items heal or give energy; traps are set face down and spring on the opponent's next matching attack.
   Bandage (heal 6), Herbal Tonic (heal 10), Mending Potion (heal 16), Energy Drink (+2 energy); traps: Spike Trap (attacker takes 6), Decoy (halves a hit), Snare (attacker skips its next action), Mirror Ward (attacker takes half the damage), Last Stand (survive a knockout at 1 Health), Guardian Angel (survive a knockout at half Health).
   Items you bring are returned after the battle. `/cardadmin give <player> item <id>` gives one.
All numbers are in the `rules:` section of config.yml. `/cardadmin seed` adds 15 balanced test cards (placeholder skin).
Timing is set in `config.yml`: `turn-menu-delay-ticks` and `pause-after-attack-ticks`.

Players: `/sneakycards turn` reopens the menu (or right-click one of your cards), `/sneakycards forfeit`, `/sneakycards help`.
Sneak + right-click one of your cards before the battle starts to take it back.

## Holographic cards
Each character card from a pack has a `holo-chance` (default 0.05%, about 1 in 2000) to be holographic: a shifting rainbow foil on the card and, in battle, a mannequin that floats a little higher, sways slowly and has two colour-shifting sparkles drifting around it. Holo cards are stored separately in the binder and come back as holo after battles. `/cardadmin give <player> card <id> 1 holo` gives one.

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
    /cardadmin cards                   list every card: click to edit, shift-click twice to delete
    /cardadmin edit <id>               open a card in the creator; the green button becomes Save changes
    /cardadmin delete <id> confirm     delete a card (it disappears from packs and binders)
    /cardadmin place <id>              test: show a card without a mat
    /cardadmin move <1|2> | hurt       test the animations on your nearest card
    /cardadmin clear | list | reload | pack

## Not tested yet
Written against the Paper 26.2 API but not compiled or run on a server. Things most likely to need small tweaks:
Mannequin profile from a texture link, the flat card angle, particle names, and the ItemDisplay scale used to size the mat.
