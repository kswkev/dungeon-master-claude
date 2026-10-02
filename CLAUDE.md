# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

A Java/Swing remake of FTL's *Dungeon Master* (1988), built sprint by sprint. Sprint 1 (done) loads Level 1 from the original `DUNGEON.DAT` and lets you walk around it with DM's six-button arrow panel, which is the only input. Door, pit, stairs and teleporter behaviour, items and champions are not implemented yet.

## Commands

```
mvn package                                   # compile, run tests, build the jar
mvn test                                      # all tests
mvn test -Dtest=PartyTest                     # one test class
mvn test -Dtest=PartyTest#wallsBlock          # one test method
java -jar target/dungeon-master-0.1.0-SNAPSHOT.jar
java -Ddm.debug=true -jar target/dungeon-master-0.1.0-SNAPSHOT.jar   # print Level 1 as ASCII, log each move, show coordinates on screen
mvn compile exec:java                         # run without packaging
```

The pom targets Java 17 (`maven.compiler.release`) because only JDK 17 is installed. Java 21 was requested, so switch to 21 once it's available. No linter is configured.

## Game data

- `data/DUNGEON.DAT` comes from the user's own copy of the game. It is copyrighted and git-ignored, so never commit it.
- The local copy is the PC version: little-endian and uncompressed.
- The path is resolved in this order: first CLI argument, then `-Ddm.dungeon`, then `data/DUNGEON.DAT`.

## Architecture

Code lives under `src/main/java/dm/`, in three layers.

**`data/`: parses DUNGEON.DAT**
- `DungeonFile.parse` doesn't know the platform in advance. It tries every decoding (compressed variants from `Decompressor` plus the raw bytes) in both byte orders, and keeps the first one that passes its consistency checks:
  - the map count is plausible;
  - each map fits inside the raw map data;
  - the sections fit inside the file;
  - the party starts on a walkable square.
- Add new format knowledge as further checks rather than special cases for one platform.
- The javadoc on `DungeonFile` documents the file layout.
- Squares are stored column-major (`[x][y]`), one byte each. Bits 5-7 hold the element type and bits 0-4 hold attributes.
- Things (items, creatures, sensors), text and ornament tables are currently skipped by size only, using `THING_SIZES` and the header counts.

**`model/`: map and party, no UI**
- `Square` keeps the raw byte and decodes attribute bits through accessors (door state, pit open, stairs up). `isPassable` holds the movement rules.
- `DungeonMap` returns `Square.SOLID` for out-of-bounds squares. `DungeonMap.fromAscii` / `toAscii` build test maps and produce the debug dump. Its character legend is used by the tests.
- `Direction` follows DM's encoding: 0 = north, numbered clockwise. North is -Y.
- `Party.Move` is relative to the facing (forward, right, back, left).

**`ui/`: draws everything at the original 320×200 resolution**
- `GameWindow` draws into a 320×200 `BufferedImage` and scales it to the window with nearest-neighbour filtering. Mouse clicks are mapped back to 320×200 coordinates before hit-testing.
- Screen regions match the original layout:
  - the dungeon view is the `DungeonViewRenderer.VIEWPORT` rectangle;
  - the arrows are `MovementPanel.AREA`;
  - the champion, spell and action areas are drawn as empty outlines for now.
- `DungeonViewRenderer` draws the first-person view.
  - It draws back to front: depth 3 down to 0, and the outermost squares first at each depth.
  - View space: the square at (depth d, lateral l) spans z ∈ [d-0.5, d+0.5] and x ∈ [l-0.5, l+0.5].
  - Projection divides by `z + EYE_BACK`. The eye offset gives DM's gentle shrink of about 1.6× per square, and keeps z positive, so there is no near-plane clipping. Tune `EYE_BACK` and `FOCAL_*` together.
  - Every shape goes through `sx`/`sy`, and colours go through `shade()` (darkening with distance).

Tests (`src/test/java/dm/`) include a synthetic DUNGEON.DAT builder in `DungeonFileTest`, with matching code for building compressed files. Use it when extending the parser.
