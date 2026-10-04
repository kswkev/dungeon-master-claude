# Dungeon Master (Java remake)

A Java remake of FTL's *Dungeon Master* (1988), built one sprint at a time.

## Status

**Sprint 1: done.** The first level loads from the original game data. You can explore it in the first-person 3D view by clicking DM's six-button arrow panel:

| | | |
|---|---|---|
| ↶ Turn left | ↑ Forward | ↷ Turn right |
| ← Strafe left | ↓ Backward | → Strafe right |

- Walls and closed doors block movement. The view gets a red border for a moment when a move is blocked.
- Pits, stairs and teleporters are drawn but don't do anything yet.

**Sprint 2: done.** The Hall of Champions has its 24 champions:

- Their portraits hang in the wall mirrors, drawn with the original artwork.
- Face a mirror from the square in front of it and click the portrait to open the champion's character sheet. The sheet shows their equipment, health, stamina and mana, stats and skill levels. Hover over an item to see its name.
- **Resurrect** adds the champion to your party (up to 4) and leaves the mirror empty. **Cancel** leaves them where they are.
- Party members appear in the boxes across the top with their hands and health/stamina/mana bars. Click a box to reopen that champion's sheet.
- Reincarnate came in Sprint 17; spells come later.

**Sprint 3: done.** Walking into a wall or closed door now gives the original feedback:
- the original thud sound from GRAPHICS.DAT;
- the two front-row champions (your first two recruits) each take 1 damage, shown by the original red damage burst with the number over their status boxes;
- the red border flash, as before.

With no party yet, you only get the sound and the flash. Health can't drop below 0; champion death comes later. Sound is always on, and if no audio device is available the game simply stays silent.

**Sprint 4: done.** The dungeon view uses the original artwork from GRAPHICS.DAT:
- **Walls, floor and ceiling:** placed pixel-exact, as in the original. As in DM, the art is mirrored on alternate squares so walking looks like movement.
- **Doors, stairs and pits:** the original graphics, including the right door design for each door (grate, wood, ...). DM's exact coordinates for these aren't in the data files, so their positions are fitted to the wall geometry and may be a pixel or two off the original.
- **Not yet:** wall and floor decorations (torch holders, switches, moss...).

Without GRAPHICS.DAT the game still uses the flat-shaded view.

**Sprint 5: done.**
- **Pressure plates:** the original pressure plates work, read from DUNGEON.DAT.
  - The plate just inside the Hall of Champions exit opens the exit door once you step on it with at least one champion. As in DM, an empty party can't press a party plate.
  - Plates can open, close or toggle doors. Some only work while you stand on them, some only work once, and some click.
- **Doors:** they slide open or shut over about half a second, with the original door sound.
- **Party formation:** use the box in the top-right corner. Click a champion's icon, then any of the four positions, to move them there (swapping with whoever stands there). New recruits fill front-left, front-right, back-right, back-left.
- **Wall bumps:** now hurt the two champions on the side that hits the wall: the back row when backing into a wall, the left or right pair when sidestepping.
- **Pits:** now use the original pit graphics.

**Sprint 6: done.** Walls, floors and doors carry their original decorations:
- **Placed explicitly by the dungeon:** alcoves, the VI altar, keyholes, switches and pictures, drawn where DUNGEON.DAT puts them.
- **DM's "random" decorations:** iron rings, grates, moss, puddles, cracks and the like, chosen by the same seeded formula as the original.
- **Inscriptions** such as "HALL OF CHAMPIONS" are carved in DM's inscription font on the wall straight ahead.
- **Door decorations** (grilles, locks, the black entrance door) and door buttons.
- **Not yet:** decorations are visual only; clicking switches, buttons, keyholes and alcoves comes later.
- **Accuracy:** screen positions were fitted by hand at first (Sprint 9 moved front-wall decorations onto DM's own coordinates), and the random-placement rules were reconstructed from the ReDMCSB source.

Run with `"-Ddm.debug=true"` to list every decoration on Level 1.

**Sprint 7: done.**
- **Stairs (#8):** up and down stairs now use the right graphics. The two sets had been swapped.
- **Mirrors (#9):** champion mirrors now show their frame on side walls too, not only on the wall straight ahead.
- **Inventory:** on a party member's character sheet, click an item to pick it up. Its icon becomes the mouse pointer. Click a cell to put the item down, or to swap it with the item already there.
  - DM's slot rules apply: hands and backpack take anything, armour only goes where it's worn, pouches take potions, scrolls and small items, and the quiver takes weapons (only missiles past the first slot). An item that doesn't fit stays in your hand.
  - The held item stays in hand when you switch champions or close the sheet. A candidate's items (before Resurrect) can't be touched.

**Sprint 8: done.** Items lie on the floor:
- **Drawing:** items on the floor are drawn where DM draws them, on each square's four cells and scaled with distance. These positions come from DM's own screen-layout table, which turned out to be in GRAPHICS.DAT (entry 696).
- **Picking up and dropping:** click the bottom of the view to pick up the top item from the left or right cell just ahead of you on the party's square, or to drop the item in your hand there.
- **Throwing:** with an item in hand, click higher up in the view to throw it from that side. (Since Sprint 16 it flies and hits as in the original.)

**Sprint 9: done.** Walls can be used (#12):
- **Switches and buttons:** click them to work doors and other mechanisms. Levers flip as you click them.
- **Keyholes and coin slots:** click with the right key or coin in hand. It is used up and the mechanism fires.
- **Torch holders:** take a torch out or put one back. The holder looks empty when it is.
- **Alcoves:** click to take an item out of an alcove or put the held item in. Items sit on the alcove's shelf.
- **Door buttons:** click to open or close the door.
- **Logic:** DM's hidden AND/OR gates work, and so does the rest of the wiring that sends a switch or plate to its target. Pits can be opened and closed, but this is visual only so far.
- **Decoration positions:** front-wall decorations are now placed at DM's own coordinates from the screen-layout table. Moss, the drain grate and full-height pictures stand at the foot of the wall.
- **Hands in the status boxes:** clicking a champion's hand in the top boxes picks up, puts down or swaps an item, just like the hand cell on the character sheet.

**Sprint 10: done.** Stairs take you between levels:
- Step onto a staircase to go up or down a level. You arrive on the square next to the matching staircase on that level, facing away from it.
- Every level loads from DUNGEON.DAT. The window title shows which level you're on.
- Pressure plates fire as you leave one level and arrive on the next.
- Stairs that don't lead anywhere block you like a wall.

**Sprint 11: done.** Pits, teleporters and four Level 2 fixes:
- **Pits:** walking into an open pit drops the party to the level below with the original scream, and every champion takes the original's fall damage (10-19, shown on their boxes). A pit opened under you by a lever drops you too. Dropped and thrown items fall through as well. Imaginary pits look real but hold you up; invisible ones don't show.
- **Teleporters:** an active teleporter moves the party (and, depending on the teleporter, items and thrown objects) to its target, on the same level or another one, turning you as the original does. Some only spin you round on the spot. Sensors can switch teleporters on and off.
- **Alcoves (#14):** an alcove revealed by a button stays open, and clicking it takes or gives items.
- **Keyholes and levers (#15):** they now hang at the original's eye level, and a lever no longer jumps when pulled.
- **Levers (#16):** every pull reverses what the lever controls: doors open and close, pits close and reopen, and gate puzzles work.
- **Pressure plates (#17):** an item on a plate holds it down, so the door stays open after you walk off. It closes again only once the plate is empty.

**Sprint 12: done.** The champions live in the dungeon. The rules are ported from the reverse-engineered original (ReDMCSB):
- **Food and water:** champions slowly get hungry and thirsty. Stamina, mana and health come back over time, faster after a rest, and every step tires them a little. Starving champions lose stamina and then health. Their sheet shows the original FOOD and WATER bars; hold the eye to see skills and statistics instead.
- **Eating and drinking:** click a champion's mouth with food, a waterskin or a potion in hand. Fountains refill waterskins and empty flasks, and clicking one with an empty hand lets the whole party drink its fill (an addition: the original only refills).
- **Light:** Level 1 is always lit. Deeper levels are dark except for torches held in hands (and worn Illumulets). Torches burn down, their flames shrink, and the view darkens through the original's six light levels.
- **Death:** a champion whose health runs out drops everything, with their bones on top, and their box shows a skull. When the whole party dies, the game ends.
- **Keyboard:** move with the numeric keypad as on the PC (7 8 9 turn left, forward, turn right; 4 5 6 left, back, right), the arrow keys, or W A S D with Q and E to turn.
- **Fixes:** the sensor data was being read two bits off, and teleporters that move only items were treated as moving nothing (#20). Plates, levers, keyholes and gates now follow the original's own data.

**Sprint 13: done.** Save, load and quit:
- Press Esc, or click the disk icon at the top of a champion's sheet, to open the game menu, built from the original's dialog art: SAVE, LOAD, QUIT, OPTIONS (since Sprint 18) and CANCEL.
- There are four save slots, each showing its level and when it was saved. Saves go in a `saves/` folder next to the game (`-Ddm.saves` to change it).
- QUIT asks whether to save first: SAVE AND QUIT, QUIT or CANCEL.
- The game is paused while the menu is open; CANCEL or Esc goes back. Esc works even after the party has died, to load a saved game.

**Sprint 14: done.** Creatures, and the view on DM's own layout:
- The dungeon's creatures appear where DUNGEON.DAT puts them, drawn as the original draws them: front, side or back depending on which way they face, smaller and darker further away, in each level's own creature colours.
- They stand in the way (no bump), stop thrown items, and turn to face you when they can see you. They don't move or attack yet.
- Doors, stairs (now also seen side-on), pits (including the faint invisible ones and holes in the ceiling) and the champion mirrors are placed by the original's own screen layout data rather than by hand.

**Sprint 15: done.** Creatures come alive, with the original's own behaviour (ported from ReDMCSB):
- **They hunt you:** creatures wander, notice the party when they see it (less far in the dark) or smell it, follow its scent trail, run at it, and turn and shuffle into the front of their square to strike.
- **They hurt:** each blow uses the original's hit roll against the champion's dexterity and luck, is softened by the armour and shields on the body part it lands on, and can wound that part. Wounded hands, head, torso, legs and feet show as red boxes, as in the original. Scorpions, wasps, worms and others poison: poison keeps hurting until it wears off or a BRO potion (antivenin) cures it, and a VI potion heals wounds. Gigglers steal from your hands instead.
- **They're seen and heard:** each creature shows its attack picture as it strikes, its own way of mirroring and jittering, and the original's attack, footstep and "ouch" sounds play.
- **The dungeon acts on them:** they can't pass closed doors (ghosts can), stay out of open pits (unless they fly), fall through pits that open under them, are carried by creature teleporters, and press pressure plates. A door shut on a creature hurts it and bounces back. The original's 50 creature generators make new creatures.
- Creatures that cast spells (Vexirks, Wizard Eyes, Demons and others) only fight hand to hand for now.

**Sprint 16: done.** The party fights back, with the original's rules (ported from ReDMCSB):
- **The action area:** below the spell area, each champion's action-hand item shows as a black icon on cyan (a fist for an empty hand). Click one to open that champion's menu of actions, drawn on the original's panel, and click an action (or PASS). The icon is shaded while the champion recovers. After a blow, the original's starburst shows the damage for a moment, or CAN'T REACH / NEED AMMO.
- **Melee:** swing, chop, stab, thrust, punch, kick and the rest hit the creature in front of the champion, with the original's hit roll, damage from strength, weapon and skill, critical hits, and the creature's defense. Champions in the back row can't reach past the one in front. Hit creatures turn on the party.
- **Throwing and shooting:** THROW (and clicking the view with an item in hand) throws with the champion's strength and skill, so the item flies further and hits harder; bows and slings SHOOT the arrows or rocks in the other hand, and the next one comes from the quiver. Things in flight hit creatures, champions, doors and walls as in the original, and land where they stop.
- **Frightening:** WAR CRY, CALM, BRANDISH and BLOW HORN can send creatures fleeing.
- **Deaths:** a dying creature leaves smoke and drops what the original's creatures always carry (a skeleton's falchion and shield, a rat's drumsticks...) plus anything it picked up; the rest of its group may lose heart and flee.
- **Doors:** a strong enough blow breaks a breakable door (wooden ones most easily), and so can thrown things.
- **Experience:** every action trains its skill, faster in a fight and on deeper levels, and a new level raises the champion's statistics, health, stamina and mana, announced in the message area at the bottom of the screen ("... JUST GAINED A FIGHTER LEVEL!"). Champions turn to face whoever hits them.

**Not yet:** spells, including the magic in items' action menus (a staff's fireball and the like); exploding bombs.

**Sprint 17: done.**
- **Reincarnate:** a champion in a mirror now shows the original's panel with RESURRECT, REINCARNATE and CANCEL. REINCARNATE opens the original's keyboard: type (or click) a new name and title, then OK. The champion forgets every skill but gains 12 statistic points. Hold the eye to see a candidate's skills and statistics first. As in the original, you can only look at a candidate with room in the party and nothing in your hand.
- **Sleeping:** click the ZZZ on a champion's sheet. The view goes dark with WAKE UP, time passes faster, and the champions recover mana, stamina and health twice as fast. Click the view or press Return to wake up; any creature attack wakes the party too, and sleepers defend poorly.
- **Teleporters:** a visible teleporter now shimmers with the original's sparkling field, shaped like the square it fills, at every distance and to the sides.
- **Distance colours:** wall decorations and objects two or three squares away take the original's darker, duller colours, as creatures and doors already did.

**Sprint 18: done.** Options (OPTIONS in the game menu), none of them in the original:
- **Difficulty:** EASY, NORMAL or HARD, picked with green gems. On EASY creatures do 20% less damage, the champions' blows, throws and shots do 20% more, every skill earns 20% more experience, and food and water last 20% longer. HARD is the reverse; NORMAL is the original.
- **God mode:** the champions' health, stamina, mana, food and water never go down, and they can't be wounded. Eating, drinking and resting still raise them.
- **Deep sleep:** lying down to sleep (the ZZZ on a sheet) restores every living champion's health, stamina and mana at once.
- **Lock master:** keyholes, locks and coin slots open without their key or coin. Holding the right one still uses it up, as usual; anything else stays in hand.
- They take effect at once and are saved with the game.

## Requirements

- JDK 17 or newer
- Maven 3.9+
- `DUNGEON.DAT` and `GRAPHICS.DAT` from your own copy of Dungeon Master

## Game data

The original game data is copyrighted and is **not** included. Copy your own files to:

```
data/DUNGEON.DAT
data/GRAPHICS.DAT
```

- For DUNGEON.DAT, the Atari ST, Amiga and PC versions should all work. The loader works out compression and byte order by itself. So far only a PC (uncompressed) file has been tested.
- GRAPHICS.DAT must be the PC version. Without it the game still runs, using simple placeholder art.
- Both files are git-ignored.

## Build and run

```
mvn package
java -jar target/dungeon-master-0.1.0-SNAPSHOT.jar
```

Options:

| Option | Effect |
|---|---|
| `java -jar <jar> path/to/DUNGEON.DAT` | Load the data file from another location |
| `-Ddm.dungeon=path/to/DUNGEON.DAT` | Same, set as a system property |
| `-Ddm.graphics=path/to/GRAPHICS.DAT` | Load the graphics from another location (default: next to DUNGEON.DAT) |
| `-Ddm.debug=true` | Print Level 1 as an ASCII map and the list of champions, log each move, and show the party's position on screen |
| `-Ddm.soundtest=all` | Play every sound effect from GRAPHICS.DAT with its index, then exit (or `=<index>` for one) |

In PowerShell, put quotes around `-D` options, e.g. `java "-Ddm.debug=true" -jar target/dungeon-master-0.1.0-SNAPSHOT.jar`. Otherwise PowerShell splits the option at the dot and Java reports `Could not find or load main class`.

If DUNGEON.DAT is missing or can't be read, an error dialog explains why.

## Tests

```
mvn test
```

The tests cover:
- movement and collisions on small hand-made maps;
- the DUNGEON.DAT loader on generated test files: both byte orders, a compressed file, a champion mirror, and broken or missing files;
- the GRAPHICS.DAT image decoder and text decoder;
- champion parsing, skill levels and where starting items go;
- recruiting, the click flow of portrait → sheet → Resurrect/Reincarnate/Cancel, and the rename keyboard;
- sleeping and waking;
- wall bumps, the formation box, pressure plates and door animation;
- decoration placement (including DM's random formula), inscriptions and the screen-layout table;
- moving items between inventory slots, floor items, picking up, dropping and throwing;
- wall sensors, alcoves, door buttons and AND/OR gates;
- stairs between levels;
- pits, teleporters, levers, alcove clicks and plates pressed by items;
- creature behaviour, attacks, wounds, poison, and creatures with doors, pits, teleporters, plates and generators;
- combat: action menus, blows, throwing and shooting, projectiles, creature deaths and drops, fear, breaking doors, experience and levels, the action and message areas;
- the Sprint 11 bug reports replayed on your own Level 2, and the creatures there. These run only when `data/` holds the game files, so CI skips them.

GitHub Actions builds the project and runs the tests on every push and pull request to `develop` and `main`.

## Project layout

```
src/main/java/dm/
  data/   DUNGEON.DAT and GRAPHICS.DAT loaders (format detection, decompression, objects, text)
  model/  map, squares, party, champions, items
  ui/     window, 3D dungeon view, character sheet, champion bars, arrow panel
```

See [CLAUDE.md](CLAUDE.md) for architecture notes.

## Disclaimer

This is a non-commercial fan project. *Dungeon Master* is © FTL Games / Software Heaven. You need an original copy of the game to play.
