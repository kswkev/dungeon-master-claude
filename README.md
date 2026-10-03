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
- Reincarnate, spells and actions come in later sprints.

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
- **Throwing:** with an item in hand, click higher up in the view to throw it from that side. It flies one square per tick for up to 4 squares and lands in front of a wall or closed door. Thrown items don't do damage yet.

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

**Not yet:** creatures, combat, spells, food and water, and saving.

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
- recruiting, and the click flow of portrait → sheet → Resurrect/Cancel;
- wall bumps, the formation box, pressure plates and door animation;
- decoration placement (including DM's random formula), inscriptions and the screen-layout table;
- moving items between inventory slots, floor items, picking up, dropping and throwing;
- wall sensors, alcoves, door buttons and AND/OR gates;
- stairs between levels;
- pits, teleporters, levers, alcove clicks and plates pressed by items;
- the Sprint 11 bug reports replayed on your own Level 2. These run only when `data/` holds the game files, so CI skips them.

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
