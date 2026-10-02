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
- The champion, spell and action areas are empty placeholders.

## Requirements

- JDK 17 or newer
- Maven 3.9+
- `DUNGEON.DAT` from your own copy of Dungeon Master

## Game data

The original game data is copyrighted and is **not** included. Copy your own file to:

```
data/DUNGEON.DAT
```

- Atari ST, Amiga and PC versions should all work. The loader works out compression and byte order by itself.
- So far only a PC (uncompressed) file has been tested.
- The file is git-ignored.

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
| `-Ddm.debug=true` | Print Level 1 as an ASCII map, log each move, and show the party's position on screen |

If the data file is missing or can't be read, an error dialog explains why.

## Tests

```
mvn test
```

The tests cover movement and collisions on small hand-made maps. They also check the data-file loader on generated test files: both byte orders, a compressed file, and broken or missing files.

## Project layout

```
src/main/java/dm/
  data/   DUNGEON.DAT loader (format detection, decompression)
  model/  map, squares, directions, party movement
  ui/     window, 3D dungeon view, movement arrow panel
```

See [CLAUDE.md](CLAUDE.md) for architecture notes.

## Disclaimer

This is a non-commercial fan project. *Dungeon Master* is © FTL Games / Software Heaven. You need an original copy of the game to play.
