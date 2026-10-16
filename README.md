# Dungeon Master (Java remake)

A Java remake of FTL's *Dungeon Master* (1988), built one sprint at a time.

## Status

Sprints 1-28 are done. The whole game can be played from the entrance to the end, with the original's levels, art, sounds, creatures, combat and magic, ported from the reverse-engineered original (ReDMCSB). A few things aren't in the original, such as a map, spell lists, difficulty options and some extra descriptions.

Move with DM's six-button arrow panel, the numeric keypad, the arrow keys, or W A S D with Q and E to turn. Esc opens the game menu (save, load, options, quit).

See [CHANGELOG.md](CHANGELOG.md) for what each sprint added.

## Requirements

- JDK 17 or newer
- Maven 3.9+
- `DUNGEON.DAT` and `GRAPHICS.DAT` from your own copy of Dungeon Master

## Game data

The original game data is copyrighted and is **not** included. Dungeon Master is now abandonware and can be downloaded from [My Abandonware](https://www.myabandonware.com/game/dungeon-master-n0). Copy your own files to:

```
data/DUNGEON.DAT
data/GRAPHICS.DAT
data/SONG.DAT      (optional: the entrance music)
```

- For DUNGEON.DAT, the Atari ST, Amiga and PC versions should all work. The loader works out compression and byte order by itself. So far only a PC (uncompressed) file has been tested.
- GRAPHICS.DAT must be the PC version. Without it the game still runs, using simple placeholder art.
- SONG.DAT is the PC version's music file. Without it the entrance is silent.
- All of these files are git-ignored.

## Build and run

```
mvn package
java -jar target/dungeon-master.jar
```

Options:

| Option | Effect |
|---|---|
| `java -jar <jar> path/to/DUNGEON.DAT` | Load the data file from another location |
| `-Ddm.dungeon=path/to/DUNGEON.DAT` | Same, set as a system property |
| `-Ddm.graphics=path/to/GRAPHICS.DAT` | Load the graphics from another location (default: next to DUNGEON.DAT) |
| `-Ddm.debug=true` | Print Level 1 as an ASCII map and the list of champions, log each move, and show the party's position on screen |
| `-Ddm.soundtest=all` | Play every sound effect from GRAPHICS.DAT with its index, then exit (or `=<index>` for one) |
| `-Ddm.start=13,15,2,N` | For testing (not in the original): start on that level (1-based), square and facing |
| `-Ddm.recruit=all` | For testing: recruit the Hall of Champions' first four champions (or a number) |
| `-Ddm.give=weapon:45,junk:51` | For testing: give the first champion items, as `category:type[:charges]` (weapon 45 is the Firestaff with the Power Gem, junk 51 a Zokathra) |

To try the ending: `java "-Ddm.start=13,15,2,N" "-Ddm.recruit=all" "-Ddm.give=weapon:45" -jar target/dungeon-master.jar` puts the party two squares south of Lord Chaos with the complete Firestaff (FLUXCAGE and FUSE in its action menu).

In PowerShell, put quotes around `-D` options, e.g. `java "-Ddm.debug=true" -jar target/dungeon-master.jar`. Otherwise PowerShell splits the option at the dot and Java reports `Could not find or load main class`.

If DUNGEON.DAT is missing or can't be read, an error dialog explains why.

### Windows download

```
mvn package -Pdist
```

This builds `target/DungeonMaster-<version>-windows.zip` (about 22 MB). It holds `DungeonMaster.exe` with its own Java runtime, so players don't need Java installed. It also has empty `data` and `saves` folders and a `readme.txt` that explains where to put the DAT files. The exe always uses the `data` and `saves` folders next to it, wherever it is started from. The build needs a Windows JDK with `jpackage` (JDK 14 or newer).

### Releases

When a pull request is merged into `main` and `pom.xml` has a new release version (one that isn't a `-SNAPSHOT` and hasn't been released yet), GitHub Actions builds the jar and the Windows zip. It publishes them as a GitHub release tagged `v<version>`. Merges that keep the same version, or a SNAPSHOT, don't release anything.

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
- wall bumps, the formation box, the active champion, pressure plates and door animation;
- the map (seen squares, wall buttons, illusionary walls) and the spell lists;
- decoration placement (including DM's random formula), inscriptions and the screen-layout table;
- moving items between inventory slots, floor items, picking up, dropping and throwing;
- chest contents and scroll text, the open chest's cells, item descriptions, and how far sounds carry;
- FUSE, the fuse sequence and the end screen, the VI altar's rebirth, the object exchanger, creatures walking into projectiles, and which potions can be drunk;
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
