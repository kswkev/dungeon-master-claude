# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Time and Token is limited

For each sprint ask 3-5 clarifying questions to better understand requirements

## Project

A Java/Swing remake of FTL's *Dungeon Master* (1988), built sprint by sprint.
- Sprint 1: walk Level 1 with DM's six-button arrow panel.
- Sprint 2: the Hall of Champions. Mirror portraits, the character sheet, Resurrect, and champion bars along the top.
- Sprint 3: wall bumps. The original thud, 1 damage to the front-row champions with DM's damage burst on their boxes, plus the red flash.
- Sprint 4: the original dungeon textures. Walls, floor and ceiling are pixel-exact; doors, stairs and pits are fitted. Wall/floor decorations are deferred.
- Sprint 5: floor sensors (pressure plates) that move doors, animated doors with sound, the party formation box, and bump damage by side.
- Sprint 6: wall, floor and door decorations (issue #4). Explicit and DM-random placement, inscriptions with text, door decorations and buttons. Visual only.
- Sprint 7: up/down stair graphics fixed (#8), champion mirrors drawn on side walls (#9), and moving items between inventory cells with the item icon as the mouse pointer.
- Sprint 8: items on the floor. They are drawn at DM's own positions (decoded from GRAPHICS.DAT's zone table), can be picked up from and dropped on the party's square, and can be thrown.
- Sprint 9: wall interaction (switches, buttons, keyholes, coin slots, torch holders, alcoves, door buttons, AND/OR gates, pits as targets), front wall decorations at DM's positions, and hand clicks in the status boxes (#12).
- Sprint 10: stairs between levels.
- Sprint 11: bugs #14-#17 (alcove clicks, eye-level keyholes and levers, levers that toggle, plates pressed by items), pits that drop the party and items a level, and teleporters.
- Sprint 12: sensor bits and teleporter scopes fixed (#20), champion upkeep (food, water, stamina, mana, health over time; eating and drinking), fountains, torches and darkness, death, and keyboard movement.
- Sprint 13: save, load and quit from the disk icon on the character sheet (a game menu built from DM's dialog art, 4 save slots).
- Sprint 14: creatures you can see (groups from DUNGEON.DAT drawn with DM's art, blocking the party, facing it, saved), and the whole view on DM's own layout zones (doors, stairs, pits, ceiling pits, mirrors).

- Sprint 15: creature AI and attacks, ported from ReDMCSB: DM's group timeline, sight, smell and scent trails, attacks with hit rolls, armour, wounds and poison, attack pictures and sounds, and creatures with doors, pits, teleporters, plates and generators.
- Sprint 16: combat, ported from ReDMCSB: the action area and menus, melee, throwing and shooting with DM's projectiles, experience and levelling with DM's message area, creature deaths with their fixed drops and fear, and doors broken by blows.
- Sprint 17: Reincarnate (DM's resurrect panel and rename keyboard), sleeping, DM's teleporter field, and DM's distance colours for wall decorations and objects at D2/D3.
- Sprint 18: the game menu's OPTIONS: difficulty (easy, normal, hard), god mode, deep sleep and lock master. None of them is in DM.
- Sprint 19: spells, ported from ReDMCSB: DM's spell area (caster tabs, rune symbols in DM's own font, cast and backspace), casting with DM's spell table, skill checks and messages, light, darkness, the party and fire shields, and spells in flight with DM's explosions (fireball, lightning, poison bolt and cloud, harm non-material, open door).

- Sprint 20: the rest of the magic, ported from ReDMCSB: brewing and drinking every potion, invisibility, thieves' eye, magic footprints, ZO KATH RA, item magic in the action menus (with charges), freeze life, fluxcages, creature spells, the party walking into projectiles (F266), and bursting VEN potions and FUL bombs.

Not implemented yet: FUSE (the Fluxcage of Chaos endgame, and Lord Chaos's capture by fluxcages), and creatures walking into projectiles (F266 for groups).

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
  - Pits: bit 3 open, bit 2 imaginary (drawn, nothing falls), bit 0 invisible (not drawn, things still fall). ReDMCSB's DEFS.H has these two the other way round (MASK0x0001_PIT_IMAGINARY, MASK0x0004_PIT_INVISIBLE); still to check in the original. Only 4 pits in the PC file have bit 2 set: the closed row on Level 12 at (10,26)-(10,29).
  - Teleporters: bit 3 open (active), bit 2 visible.
- **Teleporters** (`TeleporterFinder` → `dm.model.Teleporter`): thing type 1 on a teleporter square, layout from ReDMCSB.
  - Word 1: target X bits 0-4, Y bits 5-9, rotation bits 10-11, absolute rotation bit 12, scope bits 13-14 (0 items, 1 creatures, 2 items and the party, 3 everything; checked in the original on Level 2 (13,16), #20), audible bit 15.
  - Word 2: target **map index** (not level) in bits 8-15.
  - Verified on the PC file: all 175 teleporters lead to an open square on an existing map.
  - A teleporter that targets its own square is a "spinner": it only turns the party.
- **Creature groups** (`GroupFinder` → `dm.model.Group`): thing type 4, 16 bytes: next, possessions (their own thing list), type byte (0-26, `CreatureType`), cells byte (2 bits per creature; 0xFF one creature centred), 4 hit-point words, then direction bits 8-9 and count-1 bits 5-6. A map's allowed creature types are the first of its ornament lists (`OrnamentLists.creatures`, `DungeonMap.creatureTypes`).
  - `CreatureType` holds DM's G0243/G0219 data (from ScummVM): size (quarter/half/full), which pictures exist (front, side, back, attack, in that order from 584 + firstGraphic), coordinate set (0 ground, 1 large, 2 flying), transparent colour, and the replacement colour sets (1-based) for colours 9 and 10.
  - Since Sprint 15 it also has the rest of G0243 (`INFO`): movement and attack ticks, defense, base health, attack, poison attack, dexterity, sight/smell/attack ranges, properties (fear, wariness), resistances, animation ticks, wound probabilities (feet, torso, legs, head nibbles from the bottom) and attack type; and the attribute flags (side attack, prefer back row, attack any champion, levitation, non-material, height bits 7-8, night vision, archenemy). `attackSound()` maps the attack sound ordinal through DM's G0244; `movementSound()` is DM's F0514.
  - The group's attribute word also holds the behaviour in bits 0-3.
- **Doors:** the door thing's bit 0 picks the map's door set (style 0-3), bit 5 means it opens upward, bit 7 that magic can break it and bit 8 that blows can; `doorStyles` keeps them all (`DungeonMap.DOOR_VERTICAL`, `DOOR_MAGIC_DESTRUCTIBLE`, `DOOR_MELEE_DESTRUCTIBLE`; tests set them with `setDoorStyle`).
- **Creature generators** (floor sensor type 6, `FloorSensor.TYPE_GENERATOR`): word 1's data is the creature type, word 2 bits 7-10 the count (bit 3 set: random up to bits 0-2; otherwise the value is the count), word 3 bits 4-7 the health multiplier (0 = the map's difficulty) and bits 8-15 the ticks it rests (over 127: (n - 126) × 64). All 50 in the PC file are on corridors. ReDMCSB DEFS.H (M45/M46) confirms the layout.
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
  - Entry 695 is DM's font (`GraphicsFile.FONT`, FNT1, ScummVM's loadFNT1intoBitmap): 6 rows of 128 bytes, one byte per character, its low 5 bits the pixels (bit 4 leftmost). Characters 96-119 are the spell symbols. `GraphicsFile.raw` reads it; `dm.ui.DmFont` draws it.
  - Sounds (671–693 and 701–712) are a big-endian sample count followed by unsigned 8-bit mono PCM, played at `SOUND_SAMPLE_RATE` (5500 Hz).
  - `GraphicsFile.sound()` reads them. Which index is which effect has to be checked by ear with `-Ddm.soundtest`. The user confirmed 687 as the wall bump, 678 as the swallow and 677 as the scream (`SOUND_SCREAM`), which plays as a pit fall starts and, in DM, when a champion dies.
  - `GraphicsFile.soundEntry(dmSound)` maps DM's 34 sound indexes to PC entries (ScummVM's soundsDOS table). It agrees with every sound confirmed by ear (672 switch, 673 door, 677 scream, 678 swallow, 687 "party damaged" = the bump). The creature sounds come from it: attacks 688-693, 684, 708-710 and 674 (the wooden thud), footsteps 701-703, 705, 706, 711 and 712, a champion being hit 679-682, and the buzz 685. They still need checking by ear.
  - The useful entry indexes are constants on `GraphicsFile` (inventory 17, portraits 26, icon sheets 42-48, mirror 346, floor objects 498-583).
  - Icon sheets use colour 12 as their background; `Art.iconSprite` makes it transparent for the pointer.
- **Screen layout** (`Zones`, entry 696): DM's "zones", so some screen coordinates *are* in the PC data after all.
  - Layout: magic 0xFC0D, a range count, (first, last) id pairs, then a 4-word record (type, parent, a, b) per id.
  - Type 9 is a size, types 1-4 anchor a rectangle by a corner, and type 7 is a point relative to its parent (viewport coordinates for the ids used here). Zone 7 places the 224×136 viewport at (0,33).
  - 2500-2547: objects lying on the floor (bottom centre). 2900-2947: objects in flight (centre). The id is base + viewSquare×4 + viewCell. View squares run D3 C/L/R/far-L/far-R, D2 C/L/R, D1 C/L/R, D0. View cells run back-left, back-right, front-right, front-left. (0,0) means the cell isn't shown.
  - 2548-2554: objects in alcoves (D3 C/L/R, D2 C/L/R, D1 C). Two more such sets follow (2555, 2562), and which one DM uses when is unknown.
  - 3000-3006: front wall decoration *centres* (type 0 points), in the same order. A second set at 3007 sits a few px lower; all decorations use the first. With these, alcove objects sit on the shelf.
  - DM's per-decoration coordinate sets aren't in the zone table, so some decorations are placed by hand. `TexturedViewRenderer.FLOOR_LEVEL_ORNAMENTS` (the moss tuft 33 and the drain grate 34, as the user reported) and full-height pictures stand at the foot of the wall, on side faces too. Add more as they're spotted against the original.
  - `EYE_LEVEL_ORNAMENTS` (#15) are centred higher: row 48 of the viewport at D1 (40 of the face's 111 rows), and the same fraction of every other front or side face. They are 4-6, 15-32, 44-45 and 51-53 (keyholes, locks, slots, gems, the skull, the hook and ring, and the lever positions), all confirmed by the user against the original. The champion mirror's frame (decoration 43, `MIRROR_ORNAMENT`) is centred at `MIRROR_LEVEL` (41.5/111) on side faces and on front faces other than D1 straight ahead. That is the height of the straight-on D1 mirror, which the user confirmed (#30); `level(ornament)` picks the fraction.
  - 1500-1510 look like floor-decoration points.
  - **Layout engine** (`Zones.coord`, `Art.coord`): a port of DM's F0635 GET_COORD (ScummVM's DisplayMan::getCoord). It anchors a picture in a zone by the zone's type (0-8 anchors, 9 sizes, 10-18 relative to a grandparent's size), walks up the parents and clips; it returns {x, y, w, h, srcX, srcY} in viewport coordinates. Zone numbers are ScummVM's PC ones: walls 702-717, door frames 718-734, wall portrait 737, stairs front 802-825 and side 826-833, floor pits 852-863, ceiling pits 864-872, door button 1950 (+0 D3R, +1 D3C, +2 D2C, +3 D1C), door panels 3720-3800 (+state for part-open). `RealDungeonTest.wallZonesMatchTheTable` pins the walls.
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
  - Combat data (from ScummVM's tables): G238 weapon info (`weaponClass`, `weaponStrength`, `weaponKineticEnergy`, `shootAttack`; classes 0 swing, 2 daggers and axes, 10 bow ammo, 11 sling ammo, 12 poison dart, 16-31 bows, 32-47 slings, 112+ magic), G237's action set per object info index (`objectInfoIndex`: scroll 0, container 1, potions 2+, weapons 23+, armour 69+, junk 127+; `actionSet`), and G237's "passes through doors" flag (`passesThroughDoors`, never for keys).
  - `Actions` holds DM's 44 actions: names (G490), skill (G496), disabled ticks, stamina, experience (G497), defense (G495), hit probability and damage factor, and the 44 action sets (three actions each; the second and third with a minimum skill level, bit 7 = needs a charge). `isMagic` marks the spells and item magic.

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
- **Creatures:** `DungeonMap.groups`/`groupAt`/`hasCreatures`/`removeGroup`/`allowsCreature` (a map built without a creature list allows any).
  - The party can't step onto a creature square. That's a plain block without a bump (`Party.blockedByCreatures`), and the group reacts (DM's "party adjacent": it attacks).
  - Thrown and shot things hit them (`Flight`).
  - A party landing on a group (by teleporter) deletes it, as DM does.
  - Groups are saved with the whole AI state (`SaveGames.VERSION` 4). The ASCII map shows them as M.
  - `Group` keeps what DM keeps in ACTIVE_GROUP: a direction and an aspect per creature (attacking, flipped, jitter), the behaviour (WANDER 0, FLEE 5, ATTACK 6, APPROACH 7), the target, prior and home squares, the last move time and the fleeing delay. `remove(i)` drops a dead creature and closes the gap.
- **Creature AI** (`CreatureAI`, owned by `Dungeon`): a port of ReDMCSB GROUP1.C F175-F209 and GROUP2.C F230. ReDMCSB wins where ScummVM differs (e.g. F182 clears all four attack flags).
  - **Timeline:** it runs on DM's timeline of `Event`s (map, square, type, time, ticks, priority), sorted as F234 does: by time, then higher type, then priority.
    - Types: 29-31 reactions, 32-36 aspect updates, 37 group behaviour, 38-41 creature behaviour.
    - `Party.tick()` runs the due ones each tick.
    - `addGroupEvent` is F208: an aspect event goes first when it is due sooner, carrying the delay to the behaviour event in `ticks`.
  - **`processEvent`** is F209. Its goto labels are the states of a loop (`SET_ATTACK` = T209_044 and so on), so it can be compared line by line with the source.
  - **Seeing** (F200/F199/F197):
    - only within the creature's facing quarter (F227);
    - only up to the sight range minus half the palette index, unless the creature has night vision;
    - only along an unblocked line. Walls block it, as do closed fake walls and doors 3/4 or fully closed, unless the door design is see-through (portcullis and ra doors).
  - **Smelling** (F201/F198): the party within (smell + 1)/2 squares, or a fresh enough scent on the square, leading to the next scent.
  - **Moving** (F202) is never into:
    - walls, stairs or closed fake walls;
    - open pits, unless the creature levitates;
    - the party or other groups;
    - a door closed beyond the creature's height (non-material creatures pass).
    Wary creatures (wariness 10+) won't take a teleporter to a map that doesn't allow them.
  - **`moveGroup`** is F267 for groups:
    - it follows creature teleporters, turning the group as F262 does, and drops through pits (F191 damage 20);
    - it checks the plates left and entered, plays the footstep, and moves the group between maps;
    - leaving or reaching the party's map deactivates or activates the group (F184/F183), and it starts wandering again (F180);
    - if the party or a group stands where it would land, it waits 5 ticks.
  - **Attacks** (F207 picks the target with F286/F229; F230 does the damage through F321):
    - The hit roll: the champion's dexterity (F310) against the creature's dexterity + 2×difficulty, or 1 in 4, then F311 luck at 60.
    - The wound is chosen from the creature's probabilities.
    - For attack types other than normal, the attack is scaled by the body part's defense (F313): worn armour, shields in hands with F312 strength, vitality, and minus 8-11 if already wounded.
    - Wounds are rolled against vitality, and the parry skill (`Champion.PARRY`) lowers the attack. Every attack teaches parry (the creature's experience value, F304), hit or not. The body part's defense includes the champion's action defense.
    - A hit plays the champion's "ouch" (C09 + index) and may poison (F322).
    - The champion turns to face the hardest blow since the last tick (`Champion.receivedBlow`; `Party.faceAttackers` is F390, skipping the leader), and turns back to the party's facing once no creature has attacked for 60 ticks (F331).
    - Gigglers steal from the ready hand instead (DM's slot table is all zeros) and may flee.
  - **Deaths** (F186-F190): `damageCreature` returns `KILLED_NONE`/`KILLED_SOME`/`KILLED_ALL`.
    - One of several dying drops its type's fixed possessions (`CreatureType.fixedPossessions`, for types with attribute 0x200) on its cell, or a random one a quarter of the time; "maybe" items half the time; a metallic thud for a weapon, a wooden one otherwise. If the group was attacking, the rest may flee (fear resistance + count - 2 against random 16).
    - The last one dying deletes the group (F189) and drops every creature's fixed possessions plus the group's own things on random cells (F188).
    - A group falling down a pit is "moving": the drops of those that die wait for where it lands (F187).
  - **The champions' side** (Sprint 16): `hitCreature` (F190 for a blow or a projectile), `frighten` (F401's fear test; a frightened group stops attacking and flees for ((16 - fear) << 2) / movement ticks), `meleeTarget` (F177 with F229's attack order), `hurtChampion` (F321 for projectiles), `soundAt` and `changed` (to report between ticks).
  - **Not done yet:**
    - Lord Chaos's capture by fluxcages (F224's danger reaction, F225 FUSE), and groups walking into projectiles (F266).
  - **Creature spells** (Sprint 20, F207): the real attack range (`CreatureType.attackRange`). A creature with a range over 1 casts from afar, or half the time up close (`creatureSpell`: vexirk and Lord Chaos fireball half the time, else harm non-material, lightning, poison cloud or open door; swamp slime slime; wizard eye lightning, 1 in 8 open door; materializer poison cloud or fireball; demon and red dragon fireball; Lord Order and the Grey Lord none, DM's BUG0_13). Kinetic energy (attack/4 + 1) plus two randoms, 20-255; attack = dexterity; step energy 8; from the creature's target cell toward the party.
  - **Invisibility** (event 71, `Party.invisible`): F200 sees nothing unless the creature sees the invisible. **Freeze life** (`Party.freezeLife`, at most 200 ticks, one off each tick): F209 drops reactions and puts other events off 4 ticks, except for Lord Chaos (archenemy). A **fluxcage** (`Explosion.FLUXCAGE`, F224, 100 ticks) blocks only the archenemy (F202).
  - Groups on other maps (after the party has been there) take a random step now and then, as in DM.
  - **Smoke:** a creature that dies (falls, doors, blows, projectiles) leaves a puff of smoke, DM's smoke explosion (`Explosion.SMOKE`, C040, F0190), on its cell or the square's centre. It starts at 110, 190 or 255 by creature size and shrinks by 40 a tick while above 55 (F0220, in `Flight.tickExplosions`), so it lasts 3 to 6 ticks. See **Explosions** for how it is drawn.
  - Between ticks, `react`, `crushedByDoor`, `settle` and `generate` add to an `Outcome`. The next tick hands it over: damage per member, DM sounds, clicks and whether anything changed.
- **Scents** (`Party`, DM F267/F315/F316): the last 24 squares walked on, each with a strength.
  - The square left gains the ticks spent on it, up to 80. A new square starts at 24.
  - Every 64 ticks all but the party's own square fade by 1, and the oldest goes at 0.
- **Poison** (DM F322): `Party.poison(member, attack)` does attack/64 damage (at least 1) and queues `Champion.Poison(attack - 1)` 36 ticks later, until it reaches 0. `Party.tick` runs them, and BRO (antivenin) clears them.
- **Wounds:** `Champion.wounds()` holds DM's 6 bits in slot order (ready hand, action hand, head, torso, legs, feet; `WOUND_SLOTS`). A VI potion heals at least one (F349 and-ing with random bits).
- **Doors and sensors in `DungeonMap`:**
  - Doors have live state (0 open … 4 closed, 5 broken), seeded from the square byte. `moveDoor`/`toggleDoor` set a target, and `tickDoors()` steps toward it once per game tick. As in DM, the door sound plays on every step except the last (`DoorTick.rattled`), so a full open or close rattles 3 times.
  - A door closing on a material creature hurts the whole group by about 5 (F191) once the door is down to the creature's height (upward doors) or 1. It then bounces back a step with DM's wooden thud (`DoorTick.thud`, sound 4) and tries again next tick, and the group reacts by moving away (danger on its square).
  - `isPassable` uses the live state, so use it (not `Square.isPassable`) for doors.
  - Floor sensors have a live `pressed` state: pressed while the party (if `triggeredBy` it), a walking creature (types 1, 2 and 7, `acceptsCreatures`; `groupLeft`/`groupArrived` re-check the square) or, for type 1 only (`acceptsItems`), any item is on the square. Becoming pressed fires the effect; a released HOLD sensor clears. Revert swaps pressing and releasing: SET+revert fires on leaving, HOLD+revert clears while pressed (Level 2 (25,3) holds the pit at (24,5) shut). DM's type 2 is party/creature and type 3 party only, so neither counts items.
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
  - pits and teleporters: live state; a group standing there falls or is teleported at once (`CreatureAI.settle`);
  - corridors: their creature generators make a group (`CreatureAI.generate`, F185), then rest (`reenableGenerators`, run every tick on every map) or stop for good if once-only;
  - wall squares: each AND/OR gate there gets the effect as input bit = cell. A gate is "pressed" while its value equals the target, with the same HOLD/revert rules as plates.
  - Floor plates use it too. HOLD counts as SET.
- `pressDoorButton` toggles a door.
- **Actions** (`Combat`, ReDMCSB MENUS.C F383/F391/F401-F407, GROUP2.C F231/F232, CHAMPION.C F326/F328/F330, TIMELINE.C F253/F259; the UI calls `Party.actions(member)` and `Party.act(member, action)`):
  - **Menu** (F383): the action hand's set (an empty hand is set 2: punch, kick, war cry); the first action always, the others if the champion's skill level reaches their minimum and, when bit 7 says so, the item has a charge. Only FUSE is left out. Empty while the champion is dead or recovering, or for an item with no actions.
  - **Performing** (F391, F407): the action's defense is added (`Champion.actionDefense`, read by F313), the action runs against the square ahead **in the champion's own direction** (`Champion.facing`), then F330 disables the champion for the action's ticks, it costs its stamina + random(2) (`Party.spendStamina`, F325: half the shortfall below 0 hurts), and it earns its experience in its skill. `act` returns what the action area shows: damage, `Party.CANT_REACH` or `Party.NEED_AMMO`.
  - **Melee** (F402, F231): punch, kick, swing, chop, stab, thrust, jab, parry, hack, berzerk, bash, stun, disrupt, melee, slash, cleave.
    - The target creature is F177's; a back-row champion with someone in front can't reach.
    - The hit roll is dexterity (F310) against random(32) + the creature's dexterity + 2×difficulty - 16, or 1 in 4, or luck against 75 - the action's hit probability. Non-material creatures only take disrupt and the Vorpal Blade.
    - Damage starts from F312 strength (`Champion.strength`: strength, the item's weight against what the champion can carry, the weapon's strength and twice its skill; stamina and a wounded hand lessen it), × the action's factor / 32, less random(32) + defense + 2×difficulty (the Diamond Edge ignores a quarter, Hardcleave an eighth); a weak blow may still do a little. A skill roll (random(64) < level) doubles it + 10.
    - A hit earns (damage × the creature's experience / 16) + 3 and costs 4-7 stamina; a miss 2-3. A surviving group turns on the party.
    - A blow that lands nowhere (no creature) halves its experience and disabled ticks. An action that ends up not disabling the champion takes its defense back at once (DM's BUG0_54 kept it).
  - **Doors:** bash, hack, berzerk, kick, swing and chop at a closed door (6 ticks) break it if it is melee-destructible and F312 strength reaches its design's defense (portcullis 110, wood 42, iron 230, ra 255); it breaks 2 ticks later (`DungeonMap.breakDoor`, F232; `tickDoors` counts down).
  - **Fright** (F401): war cry (3, 12 experience, sound 28), calm (7, 35), brandish (6, 30), blow horn (6, 20, sound 25), plus the influence level, against the group ahead. A failed fright halves the experience, in influence.
  - **Shoot** (F326): a bow with bow ammunition, or a sling with sling ammunition, in the ready hand (otherwise NEED AMMO): kinetic energy = both weapons' energy, attack = (shoot attack + shoot level) × 2, step energy = the class's position in its range. It leaves from the front cell on the champion's side.
  - **Throw** (F328; also F329 for the item on the pointer, `Party.throwHeld`, which the view's upper half uses): energy from F312 strength + the weapon's kinetic energy + random(16) + half of that + the throw level; attack = 8×level + random(32), 40-200; step energy = 11 - level, at least 5; F305 stamina for the weight; 4 ticks; 8 experience, +4 for a weapon, + a quarter of its energy for throwing weapons. It leaves from the party's front left or right, the way the party faces.
  - **Item magic** (Sprint 20, F407): fireball (energy 150), lightning (180), dispell (harm non-material, 150), spit (fireball, 250) and invoke (random(128) + 100: poison bolt, poison cloud, harm non-material, or fireball half the time) fly through `Magic.projectileSpell` (F0327), costing 7 - min(6, skill) mana for fire/air/earth/water/wizard actions (with less, the energy shrinks in proportion); spell and fire shields through `Magic.shieldWithMana` (F0403: 280 ticks, 4 mana); heal (DM 1.1: cycles of min(10, heal level) health for 2 mana, 2 experience each); light (light power 2 for 2500 ticks); window (thieves' eye for random(level + 8) + 5 ticks); freeze life (`Party.freezeLife`: the blue magical box 30 ticks and green 125, used up; otherwise 70 and a charge); fluxcage (`Flight.fluxcage` on the square ahead). Each charged use takes a charge (F405, `decrementCharges`). FUSE is left out of the menus.
  - **Flip** prints heads or tails. **Climb down** (the rope) steps the party into the pit ahead and down unhurt, for some stamina (`Party.climbDown`).
  - **Recovering** (F330): a champion already recovering waits half of what is left longer (or, for a shorter action, half its own ticks). When the time is up (`Party.enableActions`, F253) the defense goes; after a shot an empty ready hand takes the next compatible ammunition from the quiver, and after a throw an empty action hand the next weapon (F259; DM's quiver slots 12, 7, 8, 9 = our `QUIVER_1`, `QUIVER_3`, `QUIVER_2`, `QUIVER_4`).
- **Spells** (Sprint 19; `Spells`, `Magic`, ReDMCSB MENUS.C F0399/F0400/F0403/F0408-F0412 and TIMELINE.C events 70-78, cross-checked against ScummVM's `MenuMan`; the UI calls `Party.magicCaster`/`setMagicCaster`, `addSymbol(column)`, `deleteSymbol()` and `cast()`):
  - **Symbols** are DM's characters 96-119: 4 rows of 6 (power LO UM ON EE PAL MON; element YA VI OH FUL DES ZO; form VEN EW KATH IR BRO GOR; class KU ROS DAIN NETA RA SAR). Each champion keeps `symbols()` and `symbolStep()` (the row for the next one; after the fourth it wraps to 0 and the next symbol starts a new spell). F0399 charges G0485's cost (× G0486's power multiplier / 8 after the power) as each is entered, and refuses it without the mana. F0400 takes one back; the mana stays spent.
  - **The caster** (DM's G514) is the chosen member, or the first living one; a dead champion's symbols are cleared (`bury`).
  - **Casting** (F0412): no spell → "NAME MUMBLES A MEANINGLESS SPELL." (cyan, -1). `Spells.find` packs the symbols and compares those after the power with G0487 (25 entries: symbols, base skill level, skill, attributes = duration 15-10, type 9-4, kind 3-0). Required level = base + power ordinal (1-6); experience = random(8) + 16×required + 8×(power-1)×base + required². Each missing level fails if random(128) > min(wisdom + 15, 115): "NAME NEEDS MORE PRACTICE WITH THIS WIZARD/PRIEST SPELL." with experience >> missing. Success: the spell, then the experience (F304) and F330 disabling for the duration. The symbols are cleared either way.
  - **Potions** (Sprint 20, F0411/F0412): the first empty flask in the action hand, then the ready hand, becomes the spell's potion type with power random(16) + 40 × power ordinal; without one, "NAME NEEDS AN EMPTY FLASK IN HAND FOR POTION." (after the skill check).
  - **The other spells** (Sprint 20): thieves' eye (event 73, (spellPower/2)² ticks; `Party.thievesEye`), invisibility (event 71, spellPower ticks: DM's tick count shares the power's register), magic footprints (event 79, spellPower² ticks: the scents left meanwhile, `Party.footprintsAt`, F172/F316) and ZO KATH RA (junk 51 into an empty ready, then action hand, else on the floor). They run out through `Magic.PartySpell` (kinds 71/73/79, counts on the party; `PartySpell.member` for 72).
  - **Thieves' eye drawing** (F0124/F0111, `TexturedViewRenderer`): the view is drawn off-screen; the D1 front wall straight ahead shows the view behind it through graphic 41 at viewport (64,19) (gold = wall, flesh = behind, the rim itself; DM's BUG0_74 doesn't arise), and a door there gets mask 440 (door ornament 16, coordinate set 1). Footprints are DM's floor ornament 15, pieces 379-384 (just before 385), flipped with the walls straight ahead.
  - **Projectile spells** (F0327): the caster turns to the party's facing; energy = clamp((power+2) × (4 + 2×skill), 21, 255) (skill doubled for open door), attack 90, step energy 10 - min(8, max mana / 8), +3 energy and -1 step when the energy is under 4 steps; from the front cell on the caster's side (as shooting).
  - **Other spells** (spell power = (power+1) × 4): light adds G039[power/2 - 1] magical light for 10000 + (power-8)×512 ticks; magic torch G039[power/4 + 1] for 2000 + (power-3)×128; darkness takes G039[power/4] away for 98 ticks. When it runs out (event 70) the light fades (or darkness lifts) one light power every 4 ticks. Party shield adds power (a quarter over 50) to `Party.shieldDefense` for power² ticks; fire shield (F0403 without mana) adds (power² + 100) >> 5 (a quarter over 50) to `fireShieldDefense` for power² + 100 ticks. `spellShieldDefense` exists for the item actions (`Magic.shield`).
  - **The shields at work:** F313 (`CreatureAI.woundDefense`) adds the party shield to every body part; F321 takes the fire shield off fire attacks and the spell shield off magic attacks.
  - **God mode** skips the skill check and the mana checks; mana never goes down anyway.
  - All this is saved; old saves load with no symbols, light or shields (`VERSION` stays 4, checked by loading a save made before the sprint).
- **Things in flight** (`Flight` and `Projectile`, ReDMCSB PROJEXPL.C F212-F219):
  - A projectile has kinetic energy, attack and step energy, an absolute cell and a direction. It moves half a square per tick on the party's map (every 3 ticks elsewhere, as DM 1.x): from a back cell to the front cell of its side, then into the next square. `Party.tick` moves them before the creatures.
  - Its first move ignores impacts. After that, each move may first hit a champion on its cell of the party's square (F321, head and torso, blunt) or a creature on its cell; if its energy is no more than its step energy it drops; otherwise both energy and attack lose a step.
  - Leaving a square, a wall, a closed fake wall or stairs met from stairs stops it: it drops in front. Crossing into the middle of a door square, a closed door (state 2 or more, not broken) stops it, unless a portcullis lets it through (attack > random(128) and `passesThroughDoors`); the impact may break the door (F232, attack + random(attack)).
  - F216's impact: the weapon's kinetic energy (random(4) for other things) + half the weight, with the projectile's energy, worn down as its attack fades. A creature takes (impact × 64) / its defense (F190); non-material creatures let it through. A surviving group reacts (CM2), and creatures with attribute 0x400 keep a dagger, arrow, slayer, poison dart or throwing star that didn't kill.
  - It ends on the floor where it stopped (through `dropItem`, so pits, teleporters and plates act), with a metallic thud for a weapon or a wooden one otherwise. It flies over pits; teleporters that take things send it on, turned (F263).
  - **Spells in flight** (Sprint 19): `Projectile.isSpell()`/`spell()` is an `Explosion` type instead of an item (`item()` is null; a pre-Sprint 19 save's projectiles are all items). Things pass through non-material creatures except harm non-material, and a fireball hitting a black flame heals it (up to 1000) and is gone. F216 for spells: a fireball 10-40 + random of fire (C1), a lightning bolt five times that (C7); harm non-material, open door and poison cloud 0 (magic, C5); a poison bolt 1 plus its energy as poison (F192 against a creature's poison resistance; a champion is poisoned half the time through `CreatureAI.poisonChampion`). A portcullis lets harm non-material, open door and the poisons through. Open door toggles a door that has a button (`Decorations.doorButton`). A spell that runs out of energy just vanishes.
  - **Explosions** (`Explosion`, `DungeonMap.explosionsAt`; F213 `Flight.explode`, F220 `Flight.tickExplosions`, every map, each tick from `Party.tick` before the projectiles move): most spells burst where they hit, on the projectile's square and cell (a poison cloud centred), with the kinetic energy as attack (a lightning bolt half). Types: 0 fireball, 1 slime, 2 lightning, 3 harm non-material, 4 open door, 6 poison bolt, 7 poison cloud, 40 smoke. Sounds: under type 3 strong (5) over attack 80, else weak (20); others the spell sound (13); smoke none.
    - A fireball or lightning burns at once: (attack/2 + 1) + random, halved for lightning. On the party's square every champion gets about that (F324, ± an eighth) as fire on any body part, scaled by the creature-damage %; otherwise the group there, unless fire-immune (`CreatureType.fireResistance` 15), takes it minus random(2×resistance+1), a quarter for non-material ones (F191 `CreatureAI.blastGroup`, the party-damage %).
    - Next tick (F220): fireball and lightning may break a door on that square (`breakDoor`, magic); harm non-material hurts non-material groups (a materializer only while attacking); a poison cloud hurts the party (normal attack, no wounds) or the group there (F192; a survivor of more than 2 moves away), thinning by 3 a tick down to 6, so attack/3 ticks; smoke thins. The rest go after one tick.
    - Drawing (`TexturedViewRenderer.drawExplosions`, after everything else on the square): DM's explosion points (G225 centred, G226 left/right column); pictures 486 fire (fireball, lightning), 487 spell (the rest), 488 poison (poison bolt and cloud, and smoke in G212's colours); scaled by max(48, attack+1) × G216 (16/23/32/32 for D3/D2/D1/D0) / 256 and flipped at random each frame. On the party's own square the whole view is filled with the 48×31 pattern 489 + 3×aspect + size (attack under 32 small, under 128 medium, else large), copied 16 pixels at a time from unit random(64), wrapping after unit 87-90 (F0133, like the teleporter field).
    - Spells in flight (`drawSpell`): G210's projectile pictures from 454: fireball 482, slime 484, poison 485, others 483, lightning 463 head-on and 464 side-on (mirrored by direction). Scaled by G215 (13/16 D3 back/front, 19/22 D2, 25/28 D1, 32 D0) × max(96, energy+1) / 256, with the objects' distance colours; drawn as is at full energy on the party's square.
  - **Bursting potions** (Sprint 20, F217): a thrown VEN potion (type 3) bursts into a centred poison cloud and a FUL bomb (19) into a fireball, its power as attack, on any impact (not when it runs out of energy); it is used up. DM does nothing special for the other bombs, and its F216 gives poison darts no poison.
  - **F266** (Sprint 20, `Flight.partyMoves`, from `Party.moveTo`): projectiles on the square the party leaves hit the champions in their cells; stepping to the adjacent square, the back row crosses the front cells and the front row meets the projectiles in the back cells ahead. Not yet for groups.
- **Experience** (`Party.addSkillExperience`, F304): fighting skills (swing to shoot) learn half as fast with no creature attack in the last 150 ticks (`Party.creatureAttacked`) and twice as fast within 25; a deeper map multiplies by its difficulty. Hidden skills also feed their base skill. An eighth (1-100) goes to temporary experience, which counts in `skillLevel` (F303) and fades by 1 every 64 ticks. A new base level raises statistics, health, stamina and mana as DM does and prints "NAME JUST GAINED A ... LEVEL!" (`Party.message`, `takeMessages`, in the member's colour). `Champion.skillLevel` adds DM's item bonuses (Firestaff, Pendant Feral, Ekkhard Cross, Gem of Ages, Sceptre of Lyf, Moonstone).
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
  - `Party.feed` / `Upkeep.consume` (F349): food is eaten, a waterskin gives 800 water per draught, a water flask 1600, other potions their DM effect, leaving an empty flask. Effects follow DM's type numbers (type 11 is DM's MON stamina potion, which the icon list calls MA). BRO (antivenin) cures poison, VI also heals wounds, and YA raises the drinker's own shield (`Champion.shieldDefense`, added in F313 and drawn as the party shield's border on their box) for its square in ticks (event 72, scheduled by `Party.feed`). Each plays the swallow (`SOUND_SWALLOW` 678, confirmed by ear), as does drinking at a fountain.
  - **Sleeping** (ScummVM's C145/F314, ReDMCSB F331): `Party.sleep()` from the sheet's ZZZ icon (`CharacterSheet.Action.SLEEP`, viewport (190,2,19,11), measured on graphic 17; not for a candidate). While `Party.sleeping()`:
    - F331 runs every `Upkeep.SLEEPING_PERIOD` (16) ticks, with mana, stamina and health gains doubled and statistics recovering every 64 ticks; champions don't turn back;
    - every skill level is 1 (F303), dexterity is halved (F310) and so is each body part's defense (F313). Each champion carries the flag (`Champion.setAsleep`) for these;
    - creatures walk silently (F514 returns sound 35);
    - the view is black with WAKE UP in cyan at viewport (93,69), and the arrows and action area are shaded. Only a click in the view (x < 224, y 33-168) or Return (`GameScreen.pressReturn`) wakes the party; so does any creature attack (`Party.creatureAttacked`, F230) and any blow that gets through armour (F321, non-normal attacks);
    - `GameScreen.tick()` runs `SLEEP_TICKS` (5) game ticks per window tick, an estimate of DM not waiting for input while asleep.
  - `Party.Tick` carries the creatures' damage and DM sounds as well as upkeep's. `GameScreen.showDamage` stays the one damage funnel.
- **Options** (Sprint 18, not in DM; `Party.difficulty()`/`setDifficulty`, `godMode()`/`setGodMode`, `deepSleep()`/`setDeepSleep`, `lockMaster()`/`setLockMaster`, saved with the party; a save without them loads as NORMAL, so `VERSION` stayed 4):
  - `Difficulty` holds percentages: creature damage (EASY 80, HARD 120: the attack in F230 before armour, and its poison; since Sprint 19 also spells and blasts that hit the party), party damage (120/80: melee in `Combat` before `hitCreature`, projectiles in `Flight.hitCreature`, blasts in `Flight.explode`/`tickExplosions`; melee experience is still worked out from the unscaled damage), experience (120/80, at the end of `addSkillExperience`, so every source) and hunger (80/120: the food and water F331 uses up, in `Upkeep.applyTimeEffects`).
  - `Difficulty.scale` rounds the fraction up as often as it is worth, so small numbers come out right on average. 100% draws no random number, so NORMAL games (and the tests) replay exactly as before.
  - God mode is a flag on each `Champion` (set for recruits too): `takeDamage` does nothing, `decrementStamina` only gains, `setFood`/`setWater`/`setMana` ignore decreases, and `addWounds` does nothing (healing wounds still works). So bursts, poison, starvation and wounds do no harm, and gains still count. Since Sprint 19 every spell cast succeeds and symbols cost nothing (`Magic`).
  - Deep sleep: `Party.sleep()` first fills every living champion's health, stamina and mana (`Champion.refresh`; mana a potion raised above its maximum is kept). The party then sleeps as usual until woken.
  - Lock master: in `clickWall`, a type 3 or 4 sensor (not reverted) whose data is the icon of a coin or key (junk 6-24, `ItemCatalog.keysAndCoins`) fires whatever the hand holds. Type 4 still uses up the held item only when it is the right one. Sensors wanting anything else (a torch, a gem) are unchanged.
- **Death** (ReDMCSB F318/F319/F444):
  - Health 0 is dead. `GameScreen.showDamage` (the one funnel for bumps, falls and starvation) calls `Party.bury()`. Everything the champion carried falls onto their cell of the party's square in DM's drop order, hands last. Their BONES go on top, with charges = member index, for a later altar resurrection. They leave the formation, and the scream plays.
  - A killing blow shows no damage burst. The dead box is graphic 8 with the name. A dead champion's box opens no sheet, and their hands can't be clicked. Upkeep, step costs, falls, bumps and feeding skip the dead. `Party.leader()` is the first living member.
  - When everyone is dead (`Party.allDead`, never for an empty party) the game is over. The screen is dark blue with graphic 6 (THE END) at (120,95) in white, as DM's palette does, and input is ignored. DM's RESTART option isn't offered.
- **Light** (`Light`, ReDMCSB F337/F338/F301; tables from ScummVM):
  - A map of difficulty 0 (bits 12-15 of the map definition's third word; only Level 1) is always fully lit.
  - Elsewhere the light is the torches in the champions' hands (each worth its charges through DM's power-to-light table, the four brightest first, each half the one before, plus one more) and 12 for each Illumulet worn on a neck, plus the light spells' `Party.magicalLight` (negative under darkness). It picks one of DM's six dungeon palettes (thresholds 99/75/50/25/1).
  - Torches in hands lose a charge every 512 ticks (`Party.tick`). In a hand a torch is drawn lit, its flame shrinking with its charges (icons 4-7, `ItemCatalog.shownIn`); elsewhere it's unlit.
  - `GameScreen.drawView` draws the view off-screen and `Darkness` remaps the viewport's palette colours to the chosen palette (ScummVM's G021 values; colours 9 and 10 from the ST rows). Colour 4 (cyan) stays bright, as in DM. Other colours are dimmed by that palette's white.
  - Far things are also recoloured as DM shrinks them (F0129's palette changes, `TexturedViewRenderer.distant`): creatures (G0221/G0222), door decorations (G0200/G0201), door buttons and wall decorations, front and side (G0198/G0199), and objects and things in flight (G0213 for the D3 size, G0214 for D2; `objectChanges` maps our scales: 27+ none, 16-26 D2, below D3). Floor decorations have their own pre-drawn pieces per depth.
- **Fountains** (wall decoration 35, `DungeonMap.FOUNTAIN`): clicking one with a waterskin refills it to 3 and turns an empty flask into a water flask (DM's F377, `Upkeep.fill`), before the side's sensors run. Clicking with an empty hand lets every living champion drink to the 2048 maximum (`Party.drinkFromFountain`, `WallClick.drank`). That is the user's addition: DM itself has no direct drinking.

**`ui/`: draws everything at the original 320×200 resolution**
- `GameScreen` holds all screen state and click routing, with no Swing. `GameWindow` is a thin wrapper that scales the 320×200 buffer with nearest-neighbour filtering and maps mouse positions back. Tests and scratch renders drive `GameScreen.press`/`render` directly.
- `GameWindow` runs a game tick every `GameScreen.TICK_MS` (170 ms) through `GameScreen.tick()`. That animates doors and repaints only on change. Tests call `tick()` directly.
- `FormationBox` (top-right, x 276-319) draws champion colours with graphic 28's icons. Click a champion, then a cell, to swap positions. Empty cells, like empty status boxes, stay black as in DM (#27); only the placeholder art (`!art.available()`) outlines them.
- Click order:
  1. a hand box in `ChampionBars` (`handAt`), except on the box of the champion whose sheet is open;
  2. the `ActionArea` (even with a sheet open, as in DM);
  3. the `SpellArea` (likewise);
  4. an open `CharacterSheet`;
  5. the `ChampionBars` boxes;
  6. `FormationBox`;
  7. in the view, the rectangles the renderer recorded during the last draw, all only for the square straight ahead: `portraitHit`, `doorButtonHit`, `wallHit`;
  8. the rest of the view (floor or throw);
  9. the arrows.
  The arrows and the dungeon are ignored while a sheet is open.
- **Spell area** (`SpellArea`, MENUS.C F0392-F0394, clicks EVENTS.C F0370/G0454), at (233,42)-(319,73):
  - Tabs (y 42-49, F0393's ST layout): the caster's wide tab (45×8 at x 233 + 14×caster) with the name black on cyan at text point (tab + 2, 48); each other living member's small cyan tab (12×7) at 233 + 14i before the caster's, 280 + 14(i - 1) after. Clicking a small tab makes that member the caster.
  - The panel is graphic 9 (87×25) at (233,50). The caster's current row of symbols is drawn at text points (239 + 14i, 58), the symbols entered at (241 + 9i, 70), cyan on black, in DM's font (`Art.font()`, `DmFont`: F040 puts the text's top-left at (x - 1, y - 4), 6-pixel cells).
  - Clicks: symbols 235 + 14i … +12 × 51-61, cast 234-303 × 63-73 (only with symbols entered), backspace 305-318 × 63-73.
  - Ignored while asleep (shaded, F0136), with a mirror candidate shown, or with nobody alive. Keys (not in DM): top-row 1-6 (`KeyMap.spellSymbol`, never the keypad), Enter casts (`GameScreen.pressReturn`, which still wakes a sleeping party first), Backspace (`GameScreen.backspace`).
  - The party's shields draw DM's borders over every living status box (F292: graphics 37 party shield, 39 spell shield, 38 fire shield on top, keyed on colour 10; `ChampionBars.draw` with the shield bits).
- **Action area** (`ActionArea`, MENUS.C F385-F391), at (233,77)-(319,121):
  - Icons: one box per member, x = 233 + 22×index, 20×35 from y 86, cyan, with the action hand's icon at (x+2, 95) in DM's G498 colours (the icon background cyan, everything else black). An empty hand is icon 201; an item with no actions leaves the box blank; a dead champion's box is black. Shaded (every other pixel black, F136) while the champion recovers or a mirror candidate is shown. Clicking one opens that champion's menu (DM's boxes 233-252, 255-274, 277-296, 299-318, y 86-120).
  - Menu: graphic 10 (with PASS printed on it) cut to 97/109/121 for 1-3 actions; the name at (235,83) black on cyan, the actions at (241, 93 + 12i) cyan. Clicks: PASS at 285-318 × 77-83, actions at 234-318 × 86-96 / 98-108 / 110-120.
  - Result: after an action the next tick shows what it did, for that tick only (F390): damage over 40 on graphic 14 at full size, 16-40 shrunk to 64×37 at (242,81), up to 15 to 42×37 at (251,81), the number at x = 274 - 3×digits, y 100; or CAN'T REACH at x 242 / NEED AMMO at 248. A miss shows nothing.
  - Without GRAPHICS.DAT the panel and burst are drawn by hand.
- **Message area** (`MessageArea`, TEXT.C F047-F052): 4 rows of 7 px from y 172, 53 columns of 6 px. Each message starts a new row, scrolling up at the bottom; words that don't fit wrap, indented by 2. A row clears 200 ticks after its last text. `GameScreen` moves `Party.takeMessages()` in (member colour from `ChampionBars.COLORS`, otherwise cyan).
- **Game menu** (`GameMenu`, opened by the sheet's disk icon, `CharacterSheet.Action.DISK` at viewport (180,3,9,9), measured on graphic 17; ScummVM's 174-182 box is another version's layout):
  - Drawn over the viewport from DM's dialog box, graphic 0 (224×136). Its pieces: message panel (10,10)-(213,51), wide button (10,62)-(213,88), half buttons (10..107 / 117..213, y 99..125).
  - The menu's own screens rearrange those pieces into a title strip and three rows: MAIN is SAVE | LOAD, QUIT | OPTIONS, CANCEL; the slot screens show 4 slot buttons and CANCEL. QUIT (SAVE AND QUIT / QUIT / CANCEL) and the OK messages use DM's own 3- and 1-choice layouts.
  - Text is gold on brown (DM's F425), titles and messages yellow.
  - While the menu is open, clicks go only to it, `tick()` does nothing (the game is paused, as in DM), and keys are ignored. CANCEL returns to where the menu was opened (the sheet, or the dungeon view). Esc (`GameScreen.escape`) toggles the menu from anywhere, even THE END, so a saved game can be loaded after the party dies. Quitting runs `GameScreen.setOnQuit`; the window exits.
  - **OPTIONS** (Sprint 18, not in DM): the title strip, a difficulty panel (the message panel cut to 36 rows) with EASY / NORMAL / HARD as a radio group (each third of the panel takes clicks), a panel (42 rows) with the toggles in two columns of gems (GOD MODE, DEEP SLEEP; LOCK MASTER), each switched only by its gem, and BACK (to MAIN; the wide button cut to 19 rows). The chosen setting shows the green gem's icon (junk 28, `ItemCatalog.GREEN_GEM`, through `Art.iconSprite`); an unchosen one shows the gem's shape in black. Clicks come back as `Choice.DIFFICULTY` (slot = the `Difficulty` ordinal), `Choice.GOD_MODE`, `Choice.DEEP_SLEEP` and `Choice.LOCK_MASTER`, and take effect at once. `draw` takes a `GameMenu.Settings`.
  - Loading swaps the party (`GameScreen.restore`, so `party` isn't final, and `GameScreen.party()` is the current one) and clears the sheet, the end, bursts and the formation pick.
- **Saved games** (`dm.data.SaveGames`): 4 slots, `saves/slotN.dmsave` (`-Ddm.saves` changes the folder; git-ignored).
  - A file is the text DMREMAKE-SAVE, a version int, a `Header` (level, game time, saved-at, champion names) for the slot buttons, then the whole `Party` by Java serialization. Every model class it reaches is `Serializable`, so new model state is saved automatically; keep new fields serializable, and bump `VERSION` when old saves can't be read.
  - Saves go to a temp file that is then moved into place. Loading accepts only `dm.*` and `java.*` classes (an `ObjectInputFilter`).
  - About 1 MB, as a save holds the whole parsed dungeon.
- **Keyboard** (`KeyMap`, `GameScreen.key`): keys go through the same path as the arrow buttons, lighting the arrow while held. The PC numpad works (7/8/9 turn left, forward, turn right; 4/5/6 left, back, right), with Num Lock off too: keypad keys are told apart by `KEY_LOCATION_NUMPAD`, so the keypad's Left sidesteps while the arrow key's Left turns. The arrow keys work (up/down move, left/right turn), and so do W/A/S/D with Q/E to turn. Movement keys are ignored while a sheet is open or after the end; the spell keys (1-6, Enter, Backspace) work with a sheet open, like the spell area (see **Spell area**).
- Screen regions match the original layout:
  - the dungeon view is the `ViewRenderer.VIEWPORT` rectangle (the character sheet replaces it while open);
  - the arrows are `MovementPanel.AREA`, drawn from GRAPHICS.DAT entry 13 (DM's cyan arrows, #28). The click boxes are DM's own (turns from F0365, moves from G0463), and a pressed arrow is highlighted as DM's F0006 does: colour index 4 is XOR-ed over its box, so cyan and black swap;
  - the champion boxes run across the top;
  - the spell area is `SpellArea.AREA` (black, outlined with placeholder art, until there is a champion); the action area is `ActionArea.AREA`.
- `CharacterSheet` slot positions come from DM's inventory background (graphic 17).
  - On a party member's sheet, clicking a cell (`Action.SLOT`, `slotAt`) picks up, places or swaps through `GameScreen.clickSlot`. A candidate's items can't be touched.
  - The mouth (`Action.MOUTH`, viewport (56,13)) feeds the held item. A member's panel shows DM's food/water panel (graphic 20 keyed on red, labels 30/31 keyed on dark grey, F344 bars); holding the eye (`Action.EYE`, (12,13)) shows skills and statistics instead, and draws the eye looking down to the right (icon 203 via `Art.icon(int)`; 202, the background's own, is the eye not looking). A candidate shows DM's resurrect panel (graphic 40 keyed on dark green at the panel box (80,52); clicks from G0457: RESURRECT (108,57)-(158,105), REINCARNATE (161,57)-(211,105), CANCEL (108,108)-(211,120)) and their statistics only while the eye is held.
  - **Candidates** open only while the party has room and the hand is empty (F280). Resurrect and Reincarnate print "NAME RESURRECTED." / "NAME REINCARNATED." in the member's colour.
  - **Reincarnate** shows the `RenamePanel` (F281): graphic 27 keyed on cyan, the name (7 letters) typed at viewport (177,58) and the title (19) at (105,76), by keys (`GameScreen.type`, while `typing()`) or by clicking DM's keyboard (screen x 107-215, y 116-144, 10 px keys, RETURN two keys tall at the right, BACKSPACE 107-175 × 147-155, OK 197-215 × 147-155). RETURN moves to the title; BACKSPACE at the start of the title returns to the name. OK needs a name, and one no member has (`Party.nameFree`). `Party.reincarnate` then zeroes every skill (`Champion.reincarnate`, as ScummVM's `resetSkillsToZero`) and adds 12 statistic points, each to a random statistic (luck included), current and maximum.
  - An item that doesn't fit stays in hand.
  - Wounds and poison, as DM's F292 draws them:
    - a wounded body part's cell gets the red slot box (graphic 34, keyed on colour 12) and, if empty, the wounded outline (icon 212 + 2×slot + 1);
    - the mouth box is red while the champion is hungry, thirsty or poisoned, and the eye box while any statistic is below its maximum;
    - a poisoned champion's food/water panel shows the POISONED label (graphic 32 at (112,105));
    - in the status boxes an empty hand shows DM's hand outline (icon 212 ready, 214 action); a wounded hand gets box 34, and the wounded outline (213/215) when it is empty.
  - With no sheet open, a click in the bottom of the view (`GameScreen.FLOOR_CLICK_Y` and below) picks up from or drops onto the party square's left or right cell ahead. A click higher up with an item in hand throws it from that side (`Party.throwHeld`, F329: the leader throws, so an empty party can't).
  - While an item is held, `GameWindow` hides the OS cursor and `GameScreen` draws `Art.iconSprite` (the icon with background colour 12 transparent) centred on the pointer, on top of everything. Most text uses `PixelFont`, a hand-made 5×5 font, from before DM's own font (entry 695) was found in Sprint 19; the spell area uses `DmFont`.
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
  - **Walls:** drawn in DM's wall zones (`WALL_ZONE`); the table indexed by [depth][lateral+2] gives the graphic, and a fallback x and y equal to the zones' (D1 front 160×111 at (32,9), D2 106×74 at (59,19), D3 70×49 at (77,25)). The zones moved D1/D2 down a pixel from Sprint 4's measured table.
  - **Flipping:** when (x + y + facing) is odd, the floor, ceiling and centre walls are mirrored, and each side uses the opposite side's piece mirrored.
  - **Doors, stairs and pits** follow ScummVM's PC drawSquare functions (F0116-F0127): per view square, the graphic, the zone, and whether the left piece is mirrored for the right. Order: pit or stairs, floor decoration, ceiling pit, far things, creatures, door, near things.
  - **Stairs:** the PC stairs graphics start at 108: up front D3L/C 108-109, D2 110-111, D1 112-113, D0 114; down 115-121 likewise; side pieces D2 122, D1 up 123, D1 down 124, D0 125. Stairs seen side-on (`!facesAlong`) use the side pieces at D2/D1/D0 left and right.
  - **Pits:** floor pits 50-57 (D3L/C, D2L/C, D1L/C, D0L/C; DM's numbers +1 on the PC), invisible-pit variants 58-63 (D3 uses the plain one), ceiling pits 64-69 where the square above is an open pit (`DungeonMap.ceilingPit`, `Dungeon.above`).
  - **Doors** (F0111): frames from the wall set (front 86, left pillars D1C 87, D2C 88, D3C 89, D3L/R 90, lintels 91/92); the panel 246 + style×3 + (D3 0, D2 1, D1 2), with its decoration painted on by DM's G0207 boxes (G0196 picks the box set; D2/D3 shrunk with DM's palette changes; colour 9 see-through, colour 10 cuts holes) and a broken door cut by mask 439. Part-open doors use zone + state. The button (453) is shrunk for D2/D3 by height.
  - **Mirrors:** at D1 the portrait goes in zone 737 (96,35) and the frame around it; D2/D3 frames are raised to match.
  - **Teleporter fields** (F0113, `drawTeleporter`), when visible and open, over everything else on the square, D0 included:
    - Graphic 76 is the 32×32 teleporter pattern (77 the fluxcage's), transparent colour 10.
    - The field fills the square's wall outline: the wall zone's box. Side squares are masked by graphics 70-75, which are exactly the PC's left wall pieces' silhouettes (75 D0L = 94, 74 D1L = 96, 73 D2L = 101, 72 D2L2 = 99, 71 D3L = 106, 70 D3L2, 36 of 104's 44 columns); right squares mirror them, right-aligned. D0C fills the viewport.
    - The pattern isn't tiled: it is copied 16 pixels at a time, unit after unit (2 per pattern row) along the box's rows (units aligned to viewport x multiples of 16), from unit random(32), wrapping at G0188's count + random(2) (D0C 59, D1C 61, D2C 60, others 63). ScummVM's port of this is marked FIXME; this follows the ST unit layout.
    - It changes every frame, so `ViewRenderer.animated()` makes `GameScreen.tick` repaint every tick.
  - **Creatures** (`drawCreatures`, `CreatureArt`, after ScummVM's F0115 creature block): view squares D3 C/L/R, D2, D1, D0 L/R; positions from DM's coordinate sets G0224 (x centre, bottom y) by cell: quarter-square creatures on their view cell (0-3), half-square ones on a column (0/1) facing you or a row (2 back, 4 front) side-on, full-square and centred ones at 4. Back cells first. Each creature's own facing difference picks its picture (`creaturePicture`): back (0), side (odd; mirrored at 1), otherwise the attack picture while its aspect says it is attacking, or the front, mirrored when its aspect says so. Its aspect's jitter moves it by DM's G223 shift sets (by depth). D2/D3 shrink to 20/32 and 16/32 with DM's creature palette changes, the transparent colour changed with them.
  - **Colours 9 and 10:** a map's creature types replace them with DM's replacement colour sets (G0220, six light levels each), across the whole viewport, as DM's palette does. `GameScreen.drawView` passes them to `Darkness.apply`, which remaps even at full light. In D2/D3 creature pictures, 9/10 become the set's D2/D3 colour.
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
