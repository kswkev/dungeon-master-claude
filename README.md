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
- recruiting, and the click flow of portrait → sheet → Resurrect/Cancel.

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
