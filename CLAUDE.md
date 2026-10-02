# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

A Java/Swing remake of FTL's *Dungeon Master* (1988), built sprint by sprint.
- Sprint 1: walk Level 1 with DM's six-button arrow panel, which is the only input.
- Sprint 2: the Hall of Champions. Mirror portraits, the character sheet, Resurrect (not Reincarnate), and champion bars along the top.
- Sprint 3: wall bumps. The original thud, 1 damage to the front-row champions with DM's damage burst on their boxes, plus the red flash.
- Sprint 4: the original dungeon textures. Walls, floor and ceiling are pixel-exact; doors, stairs and pits are fitted. Wall/floor decorations are deferred.
- Sprint 5: floor sensors (pressure plates) that move doors, animated doors with sound, the party formation box, and bump damage by side.

Door, pit, stairs and teleporter behaviour, picking items up, combat and spells are not implemented yet.

## Commands

```
mvn package                                   # compile, run tests, build the jar
mvn test                                      # all tests
mvn test -Dtest=PartyTest                     # one test class
mvn test -Dtest=PartyTest#wallsBlock          # one test method
java -jar target/dungeon-master-0.1.0-SNAPSHOT.jar
java "-Ddm.debug=true" -jar target/dungeon-master-0.1.0-SNAPSHOT.jar      # print Level 1 as ASCII, log each move, show coordinates on screen
java "-Ddm.soundtest=all" -jar target/dungeon-master-0.1.0-SNAPSHOT.jar   # play every GRAPHICS.DAT sound with its index (or =<index>)
mvn compile exec:java                         # run without packaging
```

The shell is PowerShell, which splits an unquoted `-Ddm.x=y` at the dot, so always quote `-D` options. The pom targets Java 17 (`maven.compiler.release`) because only JDK 17 is installed. Java 21 was requested, so switch to 21 once it's available. No linter is configured.

## Game data

- `data/DUNGEON.DAT` and `data/GRAPHICS.DAT` come from the user's own copy of the game. They are copyrighted and git-ignored, so never commit them.
- The local copies are the PC version. DUNGEON.DAT is little-endian and uncompressed.
- The DUNGEON.DAT path is resolved in this order: first CLI argument, then `-Ddm.dungeon`, then `data/DUNGEON.DAT`. GRAPHICS.DAT is read from the same folder unless `-Ddm.graphics` is set. Without it, `Art.none()` gives placeholder art and the game still runs.

## Architecture

Code lives under `src/main/java/dm/`, in three layers.

**`data/`: parses DUNGEON.DAT and GRAPHICS.DAT**
- `DungeonFile.parse` doesn't know the platform in advance. It tries every decoding (compressed variants from `Decompressor` plus the raw bytes) in both byte orders, and keeps the first one that passes its consistency checks:
  - the map count is plausible;
  - each map fits inside the raw map data;
  - the sections fit inside the file;
  - the party starts on a walkable square.
- Add new format knowledge as further checks rather than special cases for one platform.
- The javadoc on `DungeonFile` documents the file layout.
- Squares are stored column-major (`[x][y]`), one byte each. Bits 5-7 hold the element type and bits 0-4 hold attributes. Bit 4 means the square has a thing list.
- **Things** (`Thing`, `Thing.Store`):
  - A thing id packs the cell (bits 14-15), type (10-13) and index (0-9). Each record's first word links to the next thing, and 0xFFFE ends the list.
  - Squares with things take entries from the square-first-things table in column-major order, starting at that column's cumulative count.
- **Text** (`TextDecoder`): 5-bit codes, 3 per word. Code 28 is a line break. Code 30 followed by another code is an escape for a common string (e.g. 30+2 = "THE ").
- **Champions** (`ChampionFinder`):
  - A mirror is a sensor of type 127 on a wall square. The sensor's data is the portrait number and its cell is the wall side.
  - The starting items are the objects on that same wall side.
  - The champion's text is on the floor square the mirror faces: `NAME\nTITLE\n\nGENDER\nvitals\nstats\nskills`, with numbers written as hex letters A-P.
- **GRAPHICS.DAT** (`GraphicsFile`, `ImageDecoder`), as found by reverse-checking the user's PC file:
  - The header is followed by tables of sizes and width/height, then the entries.
  - Each image is a nibble stream: a 6-colour local palette, then single-pixel or run commands, including "copy from the row above". The javadoc on `ImageDecoder` has the details.
  - Entries 671 and up are sounds and data, not images. Entry 694 is the object name list, indexed by icon number.
  - Sounds (671–693 and 701–712) are a big-endian sample count followed by unsigned 8-bit mono PCM, played at `SOUND_SAMPLE_RATE` (5500 Hz).
  - `GraphicsFile.sound()` reads them. Which index is which effect has to be checked by ear with `-Ddm.soundtest`. The user confirmed 687 as the wall bump.
  - The useful entry indexes are constants on `GraphicsFile` (inventory 17, portraits 26, icon sheets 42-48, mirror 346).
- **Ornament lists:** each map's creature/wall/floor/door ornament lists come straight after its squares. Their counts are in map words B (wall bits 0-3, floor bits 8-11) and C (creatures bits 4-7, doors bits 0-3).
- **Floor sensors** (`FloorSensorFinder` → `dm.model.FloorSensor`): sensor things on non-wall squares.
  - Word 1: type in bits 0-6.
  - Word 2: once-only bit 0, effect bits 1-2 (set/clear/toggle/hold), revert bit 3, audible bit 4, floor-ornament ordinal bits 12-15.
  - Word 3: target X in bits 6-10, Y in bits 11-15.
  - The layout was verified on the Level 1 Hall plate (6,9) → door (5,9).
  - Only door targets do anything yet.
- **Items** (`ItemCatalog`): maps the type numbers stored in DUNGEON.DAT to names and wear slots.
  - This is reference data from the game itself, not from either file.
  - Icons are found by matching names against GRAPHICS.DAT's name list. `Item.nameVariant` picks between icons that share a name (e.g. ROBE for the body vs. the legs).

**`model/`: map and party, no UI**
- `Square` keeps the raw byte and decodes attribute bits through accessors (door state, pit open, stairs up). `isPassable` holds the movement rules.
- `DungeonMap` returns `Square.SOLID` for out-of-bounds squares. `DungeonMap.fromAscii` / `toAscii` build test maps and produce the debug dump. Its character legend is used by the tests.
- `Direction` follows DM's encoding: 0 = north, numbered clockwise. North is -Y.
- `Party.Move` is relative to the facing (forward, right, back, left).
- `Party` holds up to 4 `Champion`s. `recruit(mirror)` adds the champion and marks the `ChampionMirror` as taken, so it renders empty. `facingMirror()` is the untaken mirror on the adjacent wall straight ahead.
- **Formation:** `members()` is the recruit order, which is also the colour and status-box order. `at(position)` is the formation, using DM's cells: `FRONT_LEFT` 0, `FRONT_RIGHT` 1, `BACK_RIGHT` 2, `BACK_LEFT` 3. `bump(move)` damages the two positions on the side that hit the wall.
- **Doors and sensors in `DungeonMap`:**
  - Doors have live state (0 open … 4 closed, 5 broken), seeded from the square byte. `moveDoor`/`toggleDoor` set a target, and `tickDoors()` steps toward it once per game tick. As in DM, the door sound plays on every step except the last (`DoorTick.rattled`), so a full open or close rattles 3 times.
  - `isPassable` uses the live state, so use it (not `Square.isPassable`) for doors.
  - `Party.step(move)` moves and then runs `partyMoved`: sensors on the entered square fire; HOLD or revert sensors on the left square undo.
  - Type-3 (party) sensors need ≥1 champion. In DM an empty party is the ghost Theron.
- `Champion.addStartingItem` chooses slots the way DM does: worn items on the body, weapons in the action hand then the quiver or ready hand, potions in the pouches, everything else in the backpack.

**`ui/`: draws everything at the original 320×200 resolution**
- `GameScreen` holds all screen state and click routing, with no Swing. `GameWindow` is a thin wrapper that scales the 320×200 buffer with nearest-neighbour filtering and maps mouse positions back. Tests and scratch renders drive `GameScreen.press`/`render` directly.
- `GameWindow` runs a game tick every `GameScreen.TICK_MS` (170 ms) through `GameScreen.tick()`. That animates doors and repaints only on change. Tests call `tick()` directly.
- `FormationBox` (top-right, x 276-319) draws champion colours with graphic 28's icons. Click a champion, then a cell, to swap positions.
- Click order: an open `CharacterSheet` first, then `ChampionBars`, then `FormationBox`, then the portrait rectangle the renderer recorded during the last draw (`portraitHit`, only for the adjacent wall straight ahead), then the arrows. The arrows are ignored while a sheet is open.
- Screen regions match the original layout:
  - the dungeon view is the `ViewRenderer.VIEWPORT` rectangle (the character sheet replaces it while open);
  - the arrows are `MovementPanel.AREA`;
  - the champion boxes run across the top;
  - the spell and action areas are drawn as empty outlines for now.
- `CharacterSheet` slot positions come from DM's inventory background (graphic 17). Text uses `PixelFont`, a hand-made 5×5 font, because the PC GRAPHICS.DAT has no UI font image.
- Blocked moves go through `GameScreen.bump()`:
  - it plays the thud through the injected `SoundPlayer` (`javaSound()` in the game, `silent()` or a lambda in tests);
  - `Party.bump()` damages the `FRONT_ROW` members;
  - `ChampionBars.showDamage` shows the burst (graphic 16) until it expires on `GameScreen`'s injectable clock.
- `Art` converts images using DM's 16-colour palette, where colour 10 is transparent in sprites. Every caller must handle `null` from `Art` by drawing a placeholder (`Placeholders`).
- **Two `ViewRenderer`s**, chosen by `ViewRenderer.forArt`:
  - `TexturedViewRenderer` when GRAPHICS.DAT is loaded;
  - `FlatViewRenderer` (the original Sprint 1 renderer) as the fallback.
  Both draw back to front, outermost squares first, and record `portraitHit`.
- **`TexturedViewRenderer`** works like DM: no 3D maths, just pre-drawn pieces pasted at fixed viewport positions.
  - **Walls:** a table indexed by [depth][lateral+2] gives the graphic, x and y (D1 front 160×111 at (32,8), D2 106×74 at (59,18), D3 70×49 at (77,25)). These were measured from the art: each side piece contains its square's visible front face plus its side face, and each front face ends exactly where the next nearer centre piece begins.
  - **Flipping:** when (x + y + facing) is odd, the floor, ceiling and centre walls are mirrored, and each side uses the opposite side's piece mirrored.
  - **Doors, stairs and pits:** their positions are fitted from mid-square perspective planes, because DM's coordinate tables live in the program file, not in GRAPHICS.DAT. Treat those constants as tunable.
  - **Pits** are graphics 50-55. **Floor ornaments** (pressure plates etc.) are 6 pieces each from graphic 385, centred on each depth's mid-square floor line. Graphics 415-420 are the black-flame-pit *ornament*, not pits.
  - **Door design:** `DungeonMap.doorStyle` (bits 8-15 of the map's graphics-set word, chosen by bit 0 of the door thing) picks one of 4 designs (graphics 246 + style×3).
  - **Orientation:** bit 3 of a door or stairs square (`Square.runsNorthSouth`) decides whether it's seen head-on.
- **`FlatViewRenderer`:**
  - View space: the square at (depth d, lateral l) spans z ∈ [d-0.5, d+0.5] and x ∈ [l-0.5, l+0.5].
  - Projection divides by `z + EYE_BACK`. The eye offset gives DM's gentle shrink of about 1.6× per square, and keeps z positive, so there is no near-plane clipping. Tune `EYE_BACK` and `FOCAL_*` together.
  - Every shape goes through `sx`/`sy`, and colours go through `shade()` (darkening with distance).

Tests (`src/test/java/dm/`) include helpers to reuse when extending the loaders:
- a synthetic DUNGEON.DAT builder in `DungeonFileTest`, including a champion mirror, with matching code for building compressed files;
- a text encoder in `TextDecoderTest`;
- small hand-checked images from the real GRAPHICS.DAT in `GraphicsFileTest`.
