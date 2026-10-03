# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

A Java/Swing remake of FTL's *Dungeon Master* (1988), built sprint by sprint.
- Sprint 1: walk Level 1 with DM's six-button arrow panel, which is the only input.
- Sprint 2: the Hall of Champions. Mirror portraits, the character sheet, Resurrect (not Reincarnate), and champion bars along the top.
- Sprint 3: wall bumps. The original thud, 1 damage to the front-row champions with DM's damage burst on their boxes, plus the red flash.
- Sprint 4: the original dungeon textures. Walls, floor and ceiling are pixel-exact; doors, stairs and pits are fitted. Wall/floor decorations are deferred.
- Sprint 5: floor sensors (pressure plates) that move doors, animated doors with sound, the party formation box, and bump damage by side.
- Sprint 6: wall, floor and door decorations (issue #4). Explicit and DM-random placement, inscriptions with text, door decorations and buttons. Visual only.
- Sprint 7: up/down stair graphics fixed (#8), champion mirrors drawn on side walls (#9), and moving items between inventory cells with the item icon as the mouse pointer.
- Sprint 8: items on the floor. They are drawn at DM's own positions (decoded from GRAPHICS.DAT's zone table), can be picked up from and dropped on the party's square, and can be thrown.
- Sprint 9: wall interaction (switches, buttons, keyholes, coin slots, torch holders, alcoves, door buttons, AND/OR gates, pits as targets), front wall decorations at DM's positions, and hand clicks in the status boxes (#12).
- Sprint 10: stairs between levels.
- Sprint 11: bugs #14-#17 (alcove clicks, eye-level keyholes and levers, levers that toggle, plates pressed by items), pits that drop the party and items a level, and teleporters.
- Sprint 12 (in progress): sensor bits and teleporter scopes fixed (#20), champion upkeep (food, water, stamina, mana, health over time; eating and drinking), fountains. Still to come: torches and darkness, death, keyboard movement.

Creatures, combat and spells are not implemented yet.

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
- Each map definition carries the map's X/Y offset (two bytes after the first 4 skipped bytes), its position in dungeon-wide coordinates (`DungeonMap.offsetX/offsetY`). Every staircase's partner is at the same dungeon-wide position one level up or down.
- Squares are stored column-major (`[x][y]`), one byte each. Bits 5-7 hold the element type and bits 0-4 hold attributes. Bit 4 means the square has a thing list.
  - Pits: bit 3 open, bit 2 imaginary (drawn, nothing falls), bit 0 invisible (not drawn, things still fall).
  - Teleporters: bit 3 open (active), bit 2 visible.
- **Teleporters** (`TeleporterFinder` → `dm.model.Teleporter`): thing type 1 on a teleporter square, layout from ReDMCSB.
  - Word 1: target X bits 0-4, Y bits 5-9, rotation bits 10-11, absolute rotation bit 12, scope bits 13-14 (0 items, 1 creatures, 2 items and the party, 3 everything; checked in the original on Level 2 (13,16), #20), audible bit 15.
  - Word 2: target **map index** (not level) in bits 8-15.
  - Verified on the PC file: all 175 teleporters lead to an open square on an existing map.
  - A teleporter that targets its own square is a "spinner": it only turns the party.
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
  - `GraphicsFile.sound()` reads them. Which index is which effect has to be checked by ear with `-Ddm.soundtest`. The user confirmed 687 as the wall bump and 677 as the scream (`SOUND_SCREAM`), which plays as a pit fall starts and, in DM, when a champion dies.
  - The useful entry indexes are constants on `GraphicsFile` (inventory 17, portraits 26, icon sheets 42-48, mirror 346, floor objects 498-583).
  - Icon sheets use colour 12 as their background; `Art.iconSprite` makes it transparent for the pointer.
- **Screen layout** (`Zones`, entry 696): DM's "zones", so some screen coordinates *are* in the PC data after all.
  - Layout: magic 0xFC0D, a range count, (first, last) id pairs, then a 4-word record (type, parent, a, b) per id.
  - Type 9 is a size, types 1-4 anchor a rectangle by a corner, and type 7 is a point relative to its parent (viewport coordinates for the ids used here). Zone 7 places the 224×136 viewport at (0,33).
  - 2500-2547: objects lying on the floor (bottom centre). 2900-2947: objects in flight (centre). The id is base + viewSquare×4 + viewCell. View squares run D3 C/L/R/far-L/far-R, D2 C/L/R, D1 C/L/R, D0. View cells run back-left, back-right, front-right, front-left. (0,0) means the cell isn't shown.
  - 2548-2554: objects in alcoves (D3 C/L/R, D2 C/L/R, D1 C). Two more such sets follow (2555, 2562), and which one DM uses when is unknown.
  - 3000-3006: front wall decoration *centres* (type 0 points), in the same order. A second set at 3007 sits a few px lower; all decorations use the first. With these, alcove objects sit on the shelf.
  - DM's per-decoration coordinate sets aren't in the zone table, so some decorations are placed by hand. `TexturedViewRenderer.FLOOR_LEVEL_ORNAMENTS` (the moss tuft 33 and the drain grate 34, as the user reported) and full-height pictures stand at the foot of the wall, on side faces too. Add more as they're spotted against the original.
  - `EYE_LEVEL_ORNAMENTS` (#15) are centred higher: row 48 of the viewport at D1 (40 of the face's 111 rows), and the same fraction of every other front or side face. They are 4-6, 15-32, 44-45 and 51-53 (keyholes, locks, slots, gems, the skull, the hook and ring, and the lever positions), all confirmed by the user against the original.
  - 3200-3394 look like creature positions (5 per view square). 1500-1510 look like floor-decoration points.
  - Doors, stairs and pits are still fitted. Their ranges haven't been found.
- **Ornament lists** (`OrnamentLists`): each map's creature/wall/floor/door ornament lists come straight after its squares.
  - Counts are in map words B (wall bits 0-3, random wall 4-7, floor 8-11, random floor 12-15) and C (creatures bits 4-7, doors bits 0-3).
  - Things refer to decorations by a 1-based ordinal into these lists.
- **Decorations** (`DecorationFinder` → `dm.model.Decorations`, via `DungeonMap.decorations()`). The rules are reconstructed from ReDMCSB F0169-F0172:
  - **Random hash:** `((((v1*31417)>>1) + v2*11 + seed) >> 2) % 30` in 16-bit unsigned maths, with v1 = 2000+(x<<5)+y, v2 = 3000+(map<<6)+w+h, and seed = header word 0.
  - **Random walls:** a wall side is allowed one if square bit (8 >> side) is set, and it hashes row (y+1)×(side+1). Corridors use bit 3 and the real (x,y).
  - **Explicit decorations override random ones:** wall sensors' attribute bits 12-15, and visible texts (inscriptions, with their text). Champion-mirror sides carry the mirror frame (decoration 43), so side views show it. The renderer skips it on the centre front face, where `drawMirror` draws the frame, the portrait and the click target.
  - **Doors:** record bits 1-4 are the decoration ordinal, and bit 6 means the door has a button.
- **Decoration art:**
  - wall decoration k: side view 259+2k, front view 260+2k (0 inscription stone, 1-3 alcoves/altar, 43 mirror, 59 outdoor picture);
  - floor decoration k: 385+6k;
  - door decoration k: 441+k, where orange (colour 9) is see-through too (`Art.doorSprite`);
  - door button: 453 (8×9 with a bevelled edge, drawn at full size at D1; checked against the original);
  - inscription font: 258 (8-pixel cells, A-Z then space and '.').
- **Wall sensors** (`WallSensorFinder` → `dm.model.WallSensor`): the sensor things on wall squares, except type-127 mirrors. Their order on each side is kept.
  - Word 1: type in bits 0-6, data in bits 7-15. For item sensors the data is the required item's **inventory icon number** (184 gold key, 176 iron key, 125 copper coin, 4 torch). For gates it's the start value (bits 0-3) and the target value (bits 4-7).
  - Word 2: as for floor sensors, plus bit 11 = local.
  - Word 3: remote targets keep X in bits 6-10, Y in bits 11-15 and cell in bits 4-5. Local sensors keep an action in bits 4-15 (10 = add experience, anything else = rotate this side).
  - Handled types: 0 disabled, 1 click, 2 any item, 3 specific item (kept), 4 specific item (used up: keyholes, coin slots), 5 AND/OR gate, 13 single-object storage (torch holders). Others are ignored.
- **Wall-side objects:** things on a wall square whose cell is the side, such as alcove and torch-holder contents. `FloorItemFinder` loads them into the same piles, except on champion-mirror sides.
  - Word 1: type in bits 0-6.
  - Word 2 (`SensorBits`, from the DM Encyclopaedia): once-only bit 2, effect bits 3-4 (set/clear/toggle/hold), revert bit 5, audible bit 6, delay bits 7-10 (not modelled), local bit 11, ornament ordinal bits 12-15. Bits 0-1 are clear on all 660 sensors. Until #20 everything was read two bits too low.
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
- **`Dungeon`:** every map plus the ways between them. `Party(maps, index, …)` wraps the maps in one (the single-map constructor is for tests), and each map gets a back reference (`setDungeon`), so items can fall or teleport to other maps. A map on its own (no `Dungeon`) has no level below and no teleporter targets.
- **Stairs:**
  - Stepping onto stairs finds the partner stairs one level up or down at the same dungeon-wide position (`Dungeon.stairsPartner`).
  - The party lands on that staircase's `stairsExit`: the open neighbour along its axis (`Square.runsNorthSouth`), facing away from the stairs.
  - Floor sensors run on the square left and on the square arrived at. `StepResult.levelChanged` is set.
  - Stairs without a partner block like a wall.
  - Only the current map ticks.
- **Pits and teleporters** (`Party.settle`, run after every step, after wall clicks, and on every tick):
  - An open, non-imaginary pit (`dropsThrough`) drops the party to the same dungeon-wide square one level down (`Dungeon.below`), keeping its facing. Each champion takes DM's fall damage (attack 20: 10 + random(10), no armour yet; `Party.setRandom` for tests). It keeps falling through further pits.
  - An open teleporter whose scope includes the party (2 or 3) moves it to its target and turns it (relative, or absolute with `Teleporter.turn`). Teleporters chain, up to 8 hops; a spinner only turns.
  - `StepResult` carries `fell`, `teleported` and per-member `damage`; `and()` merges results.
  - `applyEffect` opens, closes and toggles pits and teleporters (live `pitOpen`/`teleporterOpen`).
- `Party` holds up to 4 `Champion`s. `recruit(mirror)` adds the champion and marks the `ChampionMirror` as taken, so it renders empty. `facingMirror()` is the untaken mirror on the adjacent wall straight ahead.
- **Formation:** `members()` is the recruit order, which is also the colour and status-box order. `at(position)` is the formation, using DM's cells: `FRONT_LEFT` 0, `FRONT_RIGHT` 1, `BACK_RIGHT` 2, `BACK_LEFT` 3. `bump(move)` damages the two positions on the side that hit the wall.
- **Doors and sensors in `DungeonMap`:**
  - Doors have live state (0 open … 4 closed, 5 broken), seeded from the square byte. `moveDoor`/`toggleDoor` set a target, and `tickDoors()` steps toward it once per game tick. As in DM, the door sound plays on every step except the last (`DoorTick.rattled`), so a full open or close rattles 3 times.
  - `isPassable` uses the live state, so use it (not `Square.isPassable`) for doors.
  - Floor sensors have a live `pressed` state: pressed while the party (if `triggeredBy` it) or, for type 1 only (`acceptsItems`), any item is on the square. Becoming pressed fires the effect; a released HOLD sensor clears. Revert swaps pressing and releasing: SET+revert fires on leaving, HOLD+revert clears while pressed (Level 2 (25,3) holds the pit at (24,5) shut). DM's type 2 is party/creature and type 3 party only, so neither counts items.
  - The map tracks the party while it's on it (`partyMoved`, `partyLeft`, `placeParty`). `updateSensors(x, y)` re-checks a square after the party moves, or after `dropItem`/`pickUpItem`. `initSensors` sets the starting state silently after loading.
  - Type-3 (party) sensors need ≥1 champion. In DM an empty party is the ghost Theron.
- **Floor items:** `FloorItemFinder` puts every object thing on a non-wall square into `DungeonMap`'s piles (`itemsAt`/`addItem`/`takeItem`), one pile per cell (0 NW, 1 NE, 2 SE, 3 SW), with the top item last.
  - `addItem`/`takeItem` are raw (loading, alcoves, wall sides). In play, use `dropItem` (falls through open pits, moves through object teleporters, presses plates) and `pickUpItem` (releases plates).
  - `Direction.cellOf(viewCell)` / `viewCellOf(cell)` convert between absolute cells and view cells (0 back-left, 1 back-right, 2 front-right, 3 front-left).
  - `ItemCatalog.floorGraphic` maps each item to its floor picture. This is hand-built reference data, matched by eye against the icons.
- **Wall interaction** (`DungeonMap.clickWall(x, y, side, party, iconOf)`): walks the side's sensors in order.
  - A fired local sensor rotates the side: once per firing, after the walk, the first sensor moves to the end. A remote one sends its effect through `applyEffect`.
  - The decoration shown is the *last* sensor's decoration that has one (`wallOrnament`), so rotation flips pictures: a switch's lever, or a torch holder going empty.
  - After the sensors, an alcove (global decoration 1-3) swaps items with the hand.
  - **Alcove clicks (#14):** if the side shows an alcove *before* the click, only item sensors (types 2-4) run; plain click and storage sensors don't, so a button that revealed an alcove doesn't hide it again.
  - **Levers (#16):** a lever is a local rotating click sensor plus a remote TOGGLE click sensor, so every pull reverses its door, pit or gate input. Item sensors (types 2-4) with revert want an empty hand / any other item instead. Checked on Level 2: (6,8) → pit (7,8), (4,10) → door (5,9).
  - `iconOf` comes from the UI (`Art.iconIndex`), because icon numbers live in GRAPHICS.DAT.
- **`applyEffect`:**
  - doors: set = open, clear = close, toggle;
  - pits: live `isPitOpen` state, visual only;
  - wall squares: each AND/OR gate there gets the effect as input bit = cell. A gate is "pressed" while its value equals the target, with the same HOLD/revert rules as plates.
  - Floor plates use it too. HOLD counts as SET.
- `pressDoorButton` toggles a door.
- **Throwing:** `DungeonMap.throwItem` adds a `Projectile`, and `tickProjectiles()` (run from `GameScreen.tick()`) moves it one square per tick.
  - It stops before a wall or closed door, or after `Party.THROW_RANGE` squares, and lands on the far cell of its side, through `dropItem`.
  - It flies over open pits. An object teleporter moves it to the target, turned, and it flies on from there (on that map's list, which only ticks while the party is on that map).
  - `tickProjectiles` returns a `ProjectileTick` (moved, click).
  - There's no damage or strength-based range yet.
- `Champion.addStartingItem` chooses slots the way DM does: worn items on the body, weapons in the action hand then the quiver or ready hand, potions in the pouches, everything else in the backpack.
- **Moving items:**
  - `Item.fits(slot)` holds DM's slot rules: hands and backpack take anything; body slots only take what `wornOn` names; pouches take potions, scrolls and `ItemCatalog.POUCH_JUNK`; quiver 1 takes any weapon; quivers 2-4 take only missiles.
  - `Champion.take`/`place` move items, and `place` returns the item it displaced.
  - The item on the pointer is `Party.held()` (DM's leader hand), so it survives switching champions and closing the sheet.
- **Items are values** with `charges` (weapon bits 10-13: a torch's light power; junk bits 14-15: a waterskin's draughts; a potion's power, bits 0-7). A changed item is a new one (`withCharges`). A waterskin holding water is named WATER, as DM's icon list names it. `ItemCatalog` also holds DM's weights (tenths of a kg) and food values, taken from ScummVM's DM engine because the PC keeps item 559's tables in the program.
- **Upkeep** (`Upkeep`, ported from ReDMCSB, DM 1.2+ rules):
  - `Party.tick()` advances DM's game clock (one per `GameScreen.TICK_MS`); every 64 ticks F331 runs for each living champion: food and water drain, stamina comes back (faster when rested 80/250 ticks, lost when starving), mana comes back for stamina when a time pattern is below wisdom + priest + wizard levels, health when stamina is at least a quarter, and statistics drift to their maximum every 256 ticks.
  - Every move attempt (blocked too) costs each living champion `load*3/maxLoad + 1` stamina (F366). `Party.load` counts the held item for the first member (DM's leader).
  - Stamina spent below 0 hurts by half the shortfall.
  - Food and water start at 1500 + random(256), cap at 2048 and bottom out at -1024.
  - `Party.feed` / `Upkeep.consume` (F349): food is eaten, a waterskin gives 800 water per draught, a water flask 1600, other potions their DM effect (YA and antivenin do nothing yet), leaving an empty flask. The swallow sound isn't identified yet.
  - Sleeping, wounds and poison aren't modelled.
- **Fountains** (wall decoration 35, `DungeonMap.FOUNTAIN`): clicking one with a waterskin refills it to 3 and turns an empty flask into a water flask (DM's F377, `Upkeep.fill`), before the side's sensors run. Clicking with an empty hand lets every living champion drink to the 2048 maximum (`Party.drinkFromFountain`, `WallClick.drank`). That is the user's addition: DM itself has no direct drinking.

**`ui/`: draws everything at the original 320×200 resolution**
- `GameScreen` holds all screen state and click routing, with no Swing. `GameWindow` is a thin wrapper that scales the 320×200 buffer with nearest-neighbour filtering and maps mouse positions back. Tests and scratch renders drive `GameScreen.press`/`render` directly.
- `GameWindow` runs a game tick every `GameScreen.TICK_MS` (170 ms) through `GameScreen.tick()`. That animates doors and repaints only on change. Tests call `tick()` directly.
- `FormationBox` (top-right, x 276-319) draws champion colours with graphic 28's icons. Click a champion, then a cell, to swap positions.
- Click order:
  1. a hand box in `ChampionBars` (`handAt`), except on the box of the champion whose sheet is open;
  2. an open `CharacterSheet`;
  3. the `ChampionBars` boxes;
  4. `FormationBox`;
  5. in the view, the rectangles the renderer recorded during the last draw, all only for the square straight ahead: `portraitHit`, `doorButtonHit`, `wallHit`;
  6. the rest of the view (floor or throw);
  7. the arrows.
  The arrows and the dungeon are ignored while a sheet is open.
- Screen regions match the original layout:
  - the dungeon view is the `ViewRenderer.VIEWPORT` rectangle (the character sheet replaces it while open);
  - the arrows are `MovementPanel.AREA`;
  - the champion boxes run across the top;
  - the spell and action areas are drawn as empty outlines for now.
- `CharacterSheet` slot positions come from DM's inventory background (graphic 17).
  - On a party member's sheet, clicking a cell (`Action.SLOT`, `slotAt`) picks up, places or swaps through `GameScreen.clickSlot`. A candidate's items can't be touched.
  - The mouth (`Action.MOUTH`, viewport (56,13)) feeds the held item. A member's panel shows DM's food/water panel (graphic 20 keyed on red, labels 30/31 keyed on dark grey, F344 bars); holding the eye (`Action.EYE`, (12,13)) shows skills and statistics instead. Candidates always show their statistics.
  - An item that doesn't fit stays in hand.
  - With no sheet open, a click in the bottom of the view (`GameScreen.FLOOR_CLICK_Y` and below) picks up from or drops onto the party square's left or right cell ahead. A click higher up with an item in hand throws it from that side.
  - While an item is held, `GameWindow` hides the OS cursor and `GameScreen` draws `Art.iconSprite` (the icon with background colour 12 transparent) centred on the pointer, on top of everything. Text uses `PixelFont`, a hand-made 5×5 font, because the PC GRAPHICS.DAT has no UI font image.
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
  - **Doors, stairs and pits:** their positions are fitted from mid-square perspective planes. Treat those constants as tunable. Sprint 8 found that GRAPHICS.DAT's zone table (entry 696) holds some of DM's coordinates; the ranges for these haven't been identified yet, so check there before fitting anything new.
  - **Stairs:** 108-113 are *up* stairs (steps climbing into darkness) and 115-120 are *down* stairs (a stairwell opening in the floor), in pairs of left then centre piece for D3, D2 and D1. The side-on pieces (114, 121, 123, 124) aren't drawn yet.
  - **Pits** are graphics 50-55; invisible pits aren't drawn.
  - **Teleporters** are drawn (as a translucent overlay) only when visible and open. Graphics 70-75 look like DM's field masks and 76/77 like 32×32 field patterns; using them is still to do.
  - **Objects** use the zone points, scaled by DM's object scales: 32/32 at D0, then 27/21 (D1 near/far), 18/14 (D2), 12 (D3). Far cells are drawn before a door and near cells after it; items in flight come last. **Floor ornaments** (pressure plates etc.) are 6 pieces each from graphic 385, centred on each depth's mid-square floor line. Graphics 415-420 are the black-flame-pit *ornament*, not pits.
  - **Door design:** `DungeonMap.doorStyle` (bits 8-15 of the map's graphics-set word, chosen by bit 0 of the door thing) picks one of 4 designs (graphics 246 + style×3).
  - **Orientation:** bit 3 of a door or stairs square (`Square.runsNorthSouth`) decides whether it's seen head-on.
- **`FlatViewRenderer`:**
  - View space: the square at (depth d, lateral l) spans z ∈ [d-0.5, d+0.5] and x ∈ [l-0.5, l+0.5].
  - Projection divides by `z + EYE_BACK`. The eye offset gives DM's gentle shrink of about 1.6× per square, and keeps z positive, so there is no near-plane clipping. Tune `EYE_BACK` and `FOCAL_*` together.
  - Every shape goes through `sx`/`sy`, and colours go through `shade()` (darkening with distance).

Tests (`src/test/java/dm/`) include helpers to reuse when extending the loaders:
- a synthetic DUNGEON.DAT builder in `DungeonFileTest`, including a champion mirror, with matching code for building compressed files;
- a text encoder in `TextDecoderTest`;
- small hand-checked images from the real GRAPHICS.DAT in `GraphicsFileTest`;
- `RealDungeonTest`, which replays reported bugs on the user's own `data/` files and is skipped when they're missing (as on CI).
