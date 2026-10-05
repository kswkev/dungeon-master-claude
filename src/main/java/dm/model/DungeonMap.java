package dm.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * A single dungeon level. Squares are addressed as [x][y]; anything out of bounds is solid.
 *
 * The square bytes are fixed, but doors and floor sensors have live state:
 * a door's state (0 open .. 4 closed, 5 broken) starts from its square byte
 * and moves one step per {@link #tickDoors()} toward a target set by sensors.
 * Items lie in piles on each square's 4 cells (on a wall square, a cell is a
 * side: alcove and torch-holder contents), and thrown items fly through the
 * air ({@link Flight}). Pits open and close, and wall sensors
 * change state when clicked ({@link #clickWall}).
 */
public final class DungeonMap implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int DOOR_OPEN = 0;
    public static final int DOOR_CLOSED = 4;
    public static final int DOOR_BROKEN = 5;

    /**
     * What a party step (or an item put down or picked up) set off: a door
     * started moving, an audible sensor clicked, the party changed level,
     * fell through a pit or was teleported.
     *
     * @param damage fall damage per champion, indexed like {@link Party#members()}, or null
     */
    public record StepResult(boolean doorStarted, boolean click, boolean levelChanged, boolean fell,
                             boolean teleported, int[] damage) {
        public static final StepResult NOTHING = new StepResult(false, false, false);

        public StepResult(boolean doorStarted, boolean click, boolean levelChanged) {
            this(doorStarted, click, levelChanged, false, false, null);
        }

        /** Both results together; damage adds up. */
        public StepResult and(StepResult o) {
            int[] sum = damage;
            if (o.damage != null) {
                sum = damage == null ? o.damage.clone() : damage.clone();
                for (int i = 0; damage != null && i < o.damage.length; i++) {
                    sum[i] += o.damage[i];
                }
            }
            return new StepResult(doorStarted || o.doorStarted, click || o.click, levelChanged || o.levelChanged,
                    fell || o.fell, teleported || o.teleported, sum);
        }
    }

    private final int level;
    private final Square[][] squares;
    private final int width;
    private final int height;
    private final List<ChampionMirror> mirrors;
    private int[][] doorStyles;
    private final int[][] doorState;
    private final int[][] doorTarget;
    private final List<FloorSensor> sensors = new ArrayList<>();
    private final Map<Integer, List<Item>> floorItems = new HashMap<>();
    private final Map<Integer, List<WallSensor>> wallSensors = new HashMap<>();
    private final boolean[][] pitOpen;
    private final boolean[][] teleporterOpen;
    private final Map<Integer, Teleporter> teleporters = new HashMap<>();
    /** The dungeon this map belongs to, for things falling or teleporting to other maps; null on its own. */
    private Dungeon dungeon;
    /** The party, while it is on this map (floor sensors need to know where it stands). */
    private Party party;
    private int offsetX;
    private int offsetY;
    private final List<Projectile> projectiles = new ArrayList<>();
    private Decorations decorations;

    public DungeonMap(int level, Square[][] squares) {
        this(level, squares, List.of());
    }

    public DungeonMap(int level, Square[][] squares, List<ChampionMirror> mirrors) {
        this(level, squares, mirrors, null);
    }

    /**
     * @param doorStyles door graphic style (0-3) per square [x][y], or null for all style 0
     */
    public DungeonMap(int level, Square[][] squares, List<ChampionMirror> mirrors, int[][] doorStyles) {
        this.level = level;
        this.squares = squares;
        this.width = squares.length;
        this.height = width == 0 ? 0 : squares[0].length;
        this.mirrors = List.copyOf(mirrors);
        this.doorStyles = doorStyles;
        this.decorations = Decorations.none(width, height);
        this.doorState = new int[width][height];
        this.doorTarget = new int[width][height];
        this.pitOpen = new boolean[width][height];
        this.teleporterOpen = new boolean[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (squares[x][y].type() == SquareType.DOOR) {
                    doorState[x][y] = doorTarget[x][y] = squares[x][y].doorState();
                }
                pitOpen[x][y] = squares[x][y].type() == SquareType.PIT && squares[x][y].pitOpen();
                teleporterOpen[x][y] = squares[x][y].type() == SquareType.TELEPORTER
                        && squares[x][y].teleporterOpen();
            }
        }
    }

    void setDungeon(Dungeon dungeon) {
        this.dungeon = dungeon;
    }

    /** Builds a map from rows of characters; see {@link #charFor} for the legend. */
    public static DungeonMap fromAscii(int level, String... rows) {
        int h = rows.length;
        int w = rows[0].length();
        Square[][] sq = new Square[w][h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                sq[x][y] = squareFor(rows[y].charAt(x));
            }
        }
        return new DungeonMap(level, sq);
    }

    public int level() {
        return level;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    public Square get(int x, int y) {
        return inBounds(x, y) ? squares[x][y] : Square.SOLID;
    }

    /** Walls block; doors block unless fully open or broken; everything else is walkable. */
    public boolean isPassable(int x, int y) {
        if (!inBounds(x, y)) {
            return false;
        }
        Square s = squares[x][y];
        if (s.type() == SquareType.DOOR) {
            int state = doorState[x][y];
            return state == DOOR_OPEN || state == DOOR_BROKEN;
        }
        return s.isPassable();
    }

    // ---- doors -------------------------------------------------------------

    /** Which of DM's 4 door designs the door at (x, y) uses: 0 grate, 1 wood, 2 iron, 3 ra. */
    public int doorStyle(int x, int y) {
        return doorStyles != null && inBounds(x, y) ? doorStyles[x][y] & 3 : 0;
    }

    /** Bit 4 of a door's style entry: the door opens upward (DM's door bit 5), so a short creature fits under it part-open. */
    public static final int DOOR_VERTICAL = 0x10;

    /** Whether the door at (x, y) slides up rather than sideways. */
    public boolean doorOpensVertically(int x, int y) {
        return doorStyles != null && inBounds(x, y) && (doorStyles[x][y] & DOOR_VERTICAL) != 0;
    }

    /** Door bit 7: spells (fireballs and the like) can break the door. */
    public static final int DOOR_MAGIC_DESTRUCTIBLE = 0x20;
    /** Door bit 8: blows and thrown things can break the door. */
    public static final int DOOR_MELEE_DESTRUCTIBLE = 0x40;

    /** Sets the door at (x, y)'s design (0-3) and flags ({@link #DOOR_VERTICAL} and the destructible bits). */
    public void setDoorStyle(int x, int y, int style) {
        if (doorStyles == null) {
            doorStyles = new int[width][height];
        }
        if (inBounds(x, y)) {
            doorStyles[x][y] = style;
        }
    }

    /** DM's G254 door defense by design (portcullis, wood, iron, ra): the attack that breaks it. */
    private static final int[] DOOR_DEFENSE = {110, 42, 230, 255};

    /** Whether the door's design lets small thrown things through (G254 attribute bit 1: only the portcullis). */
    boolean doorLetsProjectilesThrough(int x, int y) {
        return doorStyle(x, y) == 0;
    }

    /** Doors a blow has broken, breaking a few ticks later (DM's event 2), by square key, with the ticks left. */
    private final Map<Integer, Integer> doorsBreaking = new HashMap<>();

    /**
     * DM's F232: an attack of {@code attack} on the closed door at (x, y).
     * If the door can be broken that way (blows or magic) and the attack
     * matches its design's defense, it breaks: at once, or {@code ticks}
     * later. Returns whether it will break.
     */
    public boolean breakDoor(int x, int y, int attack, boolean magic, int ticks) {
        if (!isDoor(x, y) || doorStyles == null) {
            return false;
        }
        int flags = doorStyles[x][y];
        if ((flags & (magic ? DOOR_MAGIC_DESTRUCTIBLE : DOOR_MELEE_DESTRUCTIBLE)) == 0
                || attack < DOOR_DEFENSE[doorStyle(x, y)] || doorState[x][y] != DOOR_CLOSED) {
            return false;
        }
        if (ticks > 0) {
            doorsBreaking.put(x * height + y, ticks);
        } else {
            doorState[x][y] = doorTarget[x][y] = DOOR_BROKEN;
        }
        return true;
    }

    /** Live state of the door at (x, y): 0 open, 1-3 part open, 4 closed, 5 broken. */
    public int doorState(int x, int y) {
        return inBounds(x, y) ? doorState[x][y] : DOOR_CLOSED;
    }

    private boolean isDoor(int x, int y) {
        return inBounds(x, y) && squares[x][y].type() == SquareType.DOOR;
    }

    /**
     * Sends the door at (x, y) toward open or closed. Returns true if a door
     * at rest starts moving (the cue for the door sound); redirecting a door
     * that is already moving returns false.
     */
    public boolean moveDoor(int x, int y, boolean open) {
        if (!isDoor(x, y) || doorState[x][y] == DOOR_BROKEN) {
            return false;
        }
        int target = open ? DOOR_OPEN : DOOR_CLOSED;
        boolean wasMoving = doorState[x][y] != doorTarget[x][y];
        doorTarget[x][y] = target;
        return !wasMoving && doorState[x][y] != target;
    }

    /** Toggling reverses where the door is heading, or where it rests. */
    public boolean toggleDoor(int x, int y) {
        return isDoor(x, y) && moveDoor(x, y, doorTarget[x][y] != DOOR_OPEN);
    }

    /**
     * What a door tick did: whether any door moved, and the rattle and thud
     * the party heard, loud or soft by distance (DM's F064, #37), or null.
     * As in DM, a door rattles on every step except the last one, where it
     * settles fully open or shut, so a full 4-step move rattles 3 times.
     */
    public record DoorTick(boolean moved, Sounds.Heard rattle, Sounds.Heard thudSound) {
        public static final DoorTick NOTHING = new DoorTick(false, null, null);

        public boolean rattled() {
            return rattle != null;
        }

        public boolean thud() {
            return thudSound != null;
        }
    }

    /** DM's C02 door rattle and C04 wooden thud. */
    private static final int SOUND_DOOR_RATTLE = 2;
    private static final int SOUND_WOODEN_THUD = 4;

    /** How the party hears {@code dmSound} made at (x, y): the party is on this map, or it's a test map without one. */
    private Sounds.Heard heard(Sounds.Heard sofar, int dmSound, int x, int y) {
        Sounds.Heard heard = party == null ? Sounds.hear(dmSound, 0, 0)
                : Sounds.hear(dmSound, x - party.x(), y - party.y());
        return sofar == null || heard != null && sofar.soft() && !heard.soft() ? heard : sofar;
    }

    /**
     * Moves every door that isn't at its target one step. A door closing on
     * creatures (not ghostly ones) hurts them and bounces back a step once
     * it is down to their height, with a wooden thud, as DM's door event
     * does; it keeps trying until they die or move away.
     */
    public DoorTick tickDoors() {
        boolean moved = false;
        Sounds.Heard rattled = null;
        Sounds.Heard thud = null;
        for (var it = doorsBreaking.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            e.setValue(e.getValue() - 1);
            if (e.getValue() <= 0) {
                int x = e.getKey() / height;
                int y = e.getKey() % height;
                doorState[x][y] = doorTarget[x][y] = DOOR_BROKEN;
                moved = true;
                it.remove();
            }
        }
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int state = doorState[x][y];
                int target = doorTarget[x][y];
                Group crushed = target > state && state != DOOR_BROKEN ? groupAt(x, y) : null;
                if (crushed != null && !crushed.type().nonMaterial()
                        && state >= (doorOpensVertically(x, y) ? crushed.type().height() : 1)) {
                    if (party != null && dungeon != null) {
                        dungeon.creatures().crushedByDoor(party, this, x, y);
                    }
                    doorState[x][y] = Math.max(DOOR_OPEN, state - 1);
                    moved = true;
                    thud = heard(thud, SOUND_WOODEN_THUD, x, y);
                    continue;
                }
                if (state != target && state != DOOR_BROKEN) {
                    int next = state + Integer.signum(target - state);
                    doorState[x][y] = next;
                    moved = true;
                    if (next != target) {
                        rattled = heard(rattled, SOUND_DOOR_RATTLE, x, y);
                    }
                }
            }
        }
        return moved ? new DoorTick(true, rattled, thud) : DoorTick.NOTHING;
    }

    // ---- floor sensors -----------------------------------------------------

    public void addSensor(FloorSensor sensor) {
        sensors.add(sensor);
    }

    public List<FloorSensor> sensors() {
        return Collections.unmodifiableList(sensors);
    }

    /**
     * The floor ornament drawn on (x, y) (e.g. 1 = square pressure plate), or -1.
     * A sensor's own ornament wins over a random one, as in DM.
     */
    public int floorOrnament(int x, int y) {
        for (FloorSensor s : sensors) {
            if (s.x() == x && s.y() == y && s.ornament() >= 0) {
                return s.ornament();
            }
        }
        return decorations.floor(x, y);
    }

    public Decorations decorations() {
        return decorations;
    }

    public void setDecorations(Decorations decorations) {
        this.decorations = decorations;
    }

    /**
     * Runs the floor sensors for a party that has just moved on this map from
     * (fromX, fromY) to its current square (from out of bounds when it has
     * just arrived from another map). See {@link #updateSensors}.
     */
    public StepResult partyMoved(Party party, int fromX, int fromY) {
        this.party = party;
        Outcome out = new Outcome();
        updateSensors(fromX, fromY, out);
        updateSensors(party.x(), party.y(), out);
        return out.result();
    }

    /** The party has left this map from (x, y): sensors there are released. */
    public StepResult partyLeft(int x, int y) {
        party = null;
        Outcome out = new Outcome();
        updateSensors(x, y, out);
        return out.result();
    }

    /**
     * Puts the party on this map without setting anything off, as at the
     * start of the game: sensors under it (and under items) start pressed.
     */
    public void placeParty(Party party) {
        this.party = party;
        initSensors();
    }

    /** Marks every sensor pressed or not from what lies on it now, without firing. */
    public void initSensors() {
        for (FloorSensor s : sensors) {
            s.setPressed(pressedNow(s));
        }
    }

    private boolean pressedNow(FloorSensor s) {
        boolean partyOn = party != null && party.map() == this && party.x() == s.x() && party.y() == s.y()
                && s.triggeredBy(party);
        Group g = groupAt(s.x(), s.y());
        boolean creatureOn = g != null && !g.type().levitates() && s.acceptsCreatures();
        return partyOn || creatureOn || s.acceptsItems() && hasItems(s.x(), s.y());
    }

    /**
     * Re-checks the floor sensors on (x, y) after the party or an item came
     * or went (#17). A sensor that becomes pressed applies its effect, and a
     * HOLD sensor that is released undoes it. The revert flag swaps pressing
     * and releasing, as "revert effect when stepping in and out" in DM: a
     * SET + revert plate fires when it's left, and a HOLD + revert plate
     * clears while pressed (Level 2's (25,3) holds the pit at (24,5) shut).
     */
    private void updateSensors(int x, int y, Outcome out) {
        for (FloorSensor s : sensors) {
            if (s.x() != x || s.y() != y || !s.enabled()) {
                continue;
            }
            boolean now = pressedNow(s);
            if (now == s.pressed()) {
                continue;
            }
            s.setPressed(now);
            boolean trigger = now != s.revert();
            FloorSensor.Effect effect;
            if (s.effect() == FloorSensor.Effect.HOLD) {
                effect = trigger ? FloorSensor.Effect.SET : FloorSensor.Effect.CLEAR;
            } else if (trigger) {
                effect = s.effect();
            } else {
                continue;
            }
            applyEffect(s.targetX(), s.targetY(), 0, effect, out, 0);
            out.sound |= s.audible();
            if (trigger) {
                s.used();
            }
        }
    }


    /** What a chain of sensor effects did, collected as it runs. */
    private static final class Outcome {
        boolean doorStarted;
        boolean sound;
        boolean fired;
        boolean handChanged;

        StepResult result() {
            return doorStarted || sound ? new StepResult(doorStarted, sound, false) : StepResult.NOTHING;
        }
    }

    /** Gates can feed gates; deeper chains than this are taken to be loops. */
    private static final int MAX_CHAIN = 8;

    /**
     * Sends a sensor effect to square (x, y): a door opens (SET), closes
     * (CLEAR) or toggles; a pit or teleporter opens, closes or toggles (and
     * a group on it falls or is sent on at once); on a wall square every
     * AND/OR gate gets the effect as input {@code cell}; a corridor's
     * creature generators make their creatures. A pit opening under the
     * party drops it on its next {@link Party#settle()}.
     */
    private void applyEffect(int x, int y, int cell, FloorSensor.Effect effect, Outcome out, int depth) {
        if (!inBounds(x, y) || depth > MAX_CHAIN) {
            return;
        }
        switch (squares[x][y].type()) {
            case DOOR -> out.doorStarted |= switch (effect) {
                case SET, HOLD -> moveDoor(x, y, true);
                case CLEAR -> moveDoor(x, y, false);
                case TOGGLE -> toggleDoor(x, y);
            };
            case PIT -> {
                pitOpen[x][y] = switch (effect) {
                    case SET, HOLD -> true;
                    case CLEAR -> false;
                    case TOGGLE -> !pitOpen[x][y];
                };
                settleCreatures(x, y);
            }
            case TELEPORTER -> {
                teleporterOpen[x][y] = switch (effect) {
                    case SET, HOLD -> true;
                    case CLEAR -> false;
                    case TOGGLE -> !teleporterOpen[x][y];
                };
                settleCreatures(x, y);
            }
            case CORRIDOR -> {
                for (FloorSensor s : new ArrayList<>(sensors)) {
                    if (s.x() == x && s.y() == y && s.type() == FloorSensor.TYPE_GENERATOR && s.enabled()
                            && party != null && dungeon != null) {
                        dungeon.creatures().generate(party, this, s);
                    }
                }
            }
            case WALL -> {
                for (Direction side : Direction.values()) {
                    for (WallSensor gate : wallSensors(x, y, side)) {
                        if (gate.type() != WallSensor.TYPE_AND_OR_GATE || !gate.enabled()) {
                            continue;
                        }
                        // Like a plate: satisfied is pressed, revert swaps the two, HOLD clears on leaving.
                        boolean was = gate.gateSatisfied();
                        boolean now = gate.gateInput(cell, effect);
                        boolean trigger = now != gate.revert();
                        if (gate.effect() == FloorSensor.Effect.HOLD) {
                            if (now || was) {
                                fire(gate, trigger ? FloorSensor.Effect.SET : FloorSensor.Effect.CLEAR,
                                        out, depth + 1, null);
                            }
                        } else if (trigger) {
                            fire(gate, gate.effect(), out, depth + 1, null);
                        }
                    }
                }
            }
            default -> { }
        }
    }

    /**
     * A wall sensor going off: a local sensor marks its side for rotation
     * (collected in {@code rotate}, applied after the click as in DM); a
     * remote one sends {@code effect} to its target.
     */
    private void fire(WallSensor s, FloorSensor.Effect effect, Outcome out, int depth, List<WallSensor> rotate) {
        out.fired = true;
        out.sound |= s.audible();
        if (s.local()) {
            if (s.localAction() != WallSensor.ACTION_ADD_EXPERIENCE && rotate != null) {
                rotate.add(s);
            }
        } else {
            applyEffect(s.targetX(), s.targetY(), s.targetCell(), effect, out, depth);
        }
        s.used();
    }

    // ---- stairs and levels -------------------------------------------------

    /** Where the map sits in dungeon-wide coordinates: square (x, y) is at (x + offsetX, y + offsetY). */
    public int offsetX() {
        return offsetX;
    }

    public int offsetY() {
        return offsetY;
    }

    public void setOffset(int x, int y) {
        offsetX = x;
        offsetY = y;
    }

    private int difficulty;

    /**
     * The map's difficulty (bits 12-15 of its definition's third word). DM
     * keeps a difficulty-0 map fully lit whatever the party carries (F337),
     * which is why the first level needs no torch.
     */
    public int difficulty() {
        return difficulty;
    }

    public void setDifficulty(int difficulty) {
        this.difficulty = difficulty;
    }

    /** Where a party coming off stairs stands, and which way it faces (away from the stairs). */
    public record StairsExit(int x, int y, Direction facing) {
    }

    /**
     * The square beside the stairs at (x, y) that the party steps off onto:
     * of the two neighbours along the stairs' axis ({@link Square#runsNorthSouth}),
     * the open one. Null if (x, y) isn't stairs or both sides are blocked.
     */
    public StairsExit stairsExit(int x, int y) {
        Square sq = get(x, y);
        if (sq.type() != SquareType.STAIRS) {
            return null;
        }
        Direction[] sides = sq.runsNorthSouth()
                ? new Direction[] {Direction.NORTH, Direction.SOUTH}
                : new Direction[] {Direction.EAST, Direction.WEST};
        for (Direction d : sides) {
            if (isPassable(x + d.dx, y + d.dy) && get(x + d.dx, y + d.dy).type() != SquareType.STAIRS) {
                return new StairsExit(x + d.dx, y + d.dy, d);
            }
        }
        return null;
    }

    // ---- pits --------------------------------------------------------------

    /** Live pit state: sensors can open and close pits. */
    public boolean isPitOpen(int x, int y) {
        return inBounds(x, y) && squares[x][y].type() == SquareType.PIT && pitOpen[x][y];
    }

    /** Whether the party and items fall through (x, y): an open pit that isn't imaginary. */
    public boolean dropsThrough(int x, int y) {
        return isPitOpen(x, y) && !squares[x][y].pitImaginary();
    }

    /** Where something falling through (x, y) lands, or null (no level below, or no dungeon). */
    public Dungeon.Location below(int x, int y) {
        return dungeon == null ? null : dungeon.below(this, x, y);
    }

    /** Whether the square above (x, y) is an open pit: DM shows a hole in the ceiling there. */
    public boolean ceilingPit(int x, int y) {
        Dungeon.Location up = dungeon == null ? null : dungeon.above(this, x, y);
        return up != null && up.map().get(up.x(), up.y()).type() == SquareType.PIT && up.map().isPitOpen(up.x(), up.y());
    }

    // ---- creatures ----------------------------------------------------------

    private final List<Group> groups = new ArrayList<>();
    /** The creature types this map allows (its creature list), which decide DM's palette colours 9 and 10. */
    private List<CreatureType> creatureTypes;

    public void addGroup(Group g) {
        if (inBounds(g.x(), g.y())) {
            groups.add(g);
        }
    }

    public List<Group> groups() {
        return Collections.unmodifiableList(groups);
    }

    /** The creature group on (x, y), or null. */
    public Group groupAt(int x, int y) {
        for (Group g : groups) {
            if (g.x() == x && g.y() == y) {
                return g;
            }
        }
        return null;
    }

    /** Whether creatures stand on (x, y): the party can't step there and thrown items stop short. */
    public boolean hasCreatures(int x, int y) {
        return groupAt(x, y) != null;
    }

    // ---- explosions -----------------------------------------------------------

    /** Kept only so a game saved before Sprint 19 with a puff of smoke in the air still loads. */
    @Deprecated
    public static final class Smoke implements Serializable {
        private static final long serialVersionUID = 1L;
        private int x;
        private int y;
        private int cell;
        private int attack;
    }

    /** Pre-Sprint 19 smoke, read from old saves and dropped. */
    @SuppressWarnings("unused")
    private List<Smoke> smoke;

    /** DM's explosions on this map (fire, lightning, poison clouds, smoke...), oldest first. */
    private List<Explosion> explosions = new ArrayList<>();

    List<Explosion> explosionList() {
        if (explosions == null) {
            explosions = new ArrayList<>(); // a game saved before Sprint 19
        }
        return explosions;
    }

    /** The explosions on (x, y), oldest first. */
    public List<Explosion> explosionsAt(int x, int y) {
        List<Explosion> here = new ArrayList<>();
        for (Explosion e : explosionList()) {
            if (e.x() == x && e.y() == y) {
                here.add(e);
            }
        }
        return here;
    }

    /** A pit or teleporter has just changed under (x, y): a group standing there falls or is teleported. */
    private void settleCreatures(int x, int y) {
        if (party != null && dungeon != null && groupAt(x, y) != null) {
            dungeon.creatures().settle(party, this, x, y);
        }
    }

    /** Brings back generators whose rest is over (DM's event 65). */
    void reenableGenerators(long now) {
        for (FloorSensor s : sensors) {
            s.reenable(now);
        }
    }

    /** Takes {@code g} off this map (it died, or went to another map). */
    void removeGroup(Group g) {
        groups.remove(g);
    }

    /**
     * A group has left (x, y) or arrived there: the floor sensors on the
     * square are checked again, since creatures press DM's "anything",
     * "party or creature" and "creature" plates (types 1, 2 and 7).
     */
    StepResult groupLeft(int x, int y) {
        Outcome out = new Outcome();
        updateSensors(x, y, out);
        return out.result();
    }

    StepResult groupArrived(int x, int y) {
        return groupLeft(x, y);
    }

    /** Whether DM lets {@code type} live on this map (its creature list); a map built without one allows any. */
    public boolean allowsCreature(CreatureType type) {
        return creatureTypes == null || creatureTypes.contains(type);
    }
    public List<CreatureType> creatureTypes() {
        return creatureTypes == null ? List.of() : creatureTypes;
    }

    public void setCreatureTypes(List<CreatureType> types) {
        creatureTypes = List.copyOf(types);
    }

    // ---- teleporters -------------------------------------------------------

    public void addTeleporter(Teleporter t) {
        if (inBounds(t.x(), t.y())) {
            teleporters.put(t.x() * height + t.y(), t);
        }
    }

    /** The teleporter on (x, y), open or not, or null. */
    public Teleporter teleporterAt(int x, int y) {
        return inBounds(x, y) && squares[x][y].type() == SquareType.TELEPORTER
                ? teleporters.get(x * height + y) : null;
    }

    /** Live teleporter state: sensors can switch teleporters on and off. */
    public boolean isTeleporterOpen(int x, int y) {
        return inBounds(x, y) && squares[x][y].type() == SquareType.TELEPORTER && teleporterOpen[x][y];
    }

    /** The open teleporter on (x, y) that moves {@code what}, or null. */
    public Teleporter activeTeleporter(int x, int y, Teleporter.Kind what) {
        Teleporter t = teleporterAt(x, y);
        return t != null && isTeleporterOpen(x, y) && t.moves(what) ? t : null;
    }

    /** Where the teleporter {@code t} on this map sends things, or null if its target doesn't exist. */
    public Dungeon.Location destination(Teleporter t) {
        DungeonMap m = dungeon == null ? null : dungeon.map(t.targetMap());
        return m == null || !m.inBounds(t.targetX(), t.targetY()) ? null
                : new Dungeon.Location(m, t.targetX(), t.targetY());
    }

    // ---- wall sensors ------------------------------------------------------

    private List<WallSensor> sideSensors(int x, int y, Direction side, boolean create) {
        if (!inBounds(x, y)) {
            return null;
        }
        int key = ((x * height) + y) * 4 + side.ordinal();
        List<WallSensor> list = wallSensors.get(key);
        if (list == null && create) {
            list = new ArrayList<>();
            wallSensors.put(key, list);
        }
        return list;
    }

    public void addWallSensor(WallSensor sensor) {
        List<WallSensor> list = sideSensors(sensor.x(), sensor.y(), sensor.side(), true);
        if (list != null) {
            list.add(sensor);
        }
    }

    /** The sensors on one side of wall square (x, y), in DM's order. */
    public List<WallSensor> wallSensors(int x, int y, Direction side) {
        List<WallSensor> list = sideSensors(x, y, side, false);
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }

    /**
     * The decoration shown on a wall side: as in DM, the last sensor there
     * that carries one; without sensors, the map's (random or explicit) one.
     * Rotating a side's sensors changes it, e.g. a lever moving or a torch
     * holder emptying.
     */
    public int wallOrnament(int x, int y, Direction side) {
        int shown = -1;
        for (WallSensor s : wallSensors(x, y, side)) {
            if (s.ornament() >= 0) {
                shown = s.ornament();
            }
        }
        return shown >= 0 ? shown : decorations.wall(x, y, side);
    }

    /** Global wall ornaments that are alcoves (the third is the VI altar): items can be put in and taken out. */
    public static boolean isAlcove(int ornament) {
        return ornament >= 1 && ornament <= 3;
    }

    /** Global wall ornament 35, a fountain (DM Encyclopaedia's decoration list). */
    public static final int FOUNTAIN = 35;

    /** What clicking a wall did; {@code drank} means the party drank from a fountain. */
    public record WallClick(boolean fired, boolean sound, boolean doorStarted, boolean handChanged, boolean drank) {
        public static final WallClick NOTHING = new WallClick(false, false, false, false, false);
    }

    /**
     * The party clicks the decoration on {@code side} of wall square (x, y),
     * holding {@code party.held()} or nothing. The side's sensors are checked
     * in order, like DM:
     * <ul>
     *   <li>click: always fires;</li>
     *   <li>click with any item: fires when an item is held;</li>
     *   <li>click with item: fires when the held item's icon is the sensor's
     *       data; the "used up" kind (keyholes, coin slots) also takes it;</li>
     *   <li>storage (torch holders): an empty hand takes the stored item, a
     *       hand with the right item puts it back; either way it fires.</li>
     * </ul>
     * Sensors that fired locally then rotate their side once each.
     *
     * <p>Levers toggle (#16). DM's levers are a pair of click sensors: a
     * local one that rotates the side (flipping the picture) and a remote
     * TOGGLE one that reverses its target: a door or pit, or a gate input,
     * which then goes on and off with the lever.
     *
     * <p>As in DM, clicking a side that shows an alcove is an alcove click
     * (#14): the alcove takes the held item or hands over its top one, and
     * only sensors that react to items are checked; plain click and storage
     * sensors don't fire, so a button that revealed the alcove doesn't hide
     * it again.
     *
     * <p>A fountain refills a held waterskin (to 3 draughts) or turns an
     * empty flask into a water flask, as in DM (F377), before the sensors
     * run. Clicking it with an empty hand lets every living champion drink
     * their fill, which DM itself doesn't do: the user asked for it.
     *
     * <p>With the options' lock master ({@link Party#lockMaster()}), a sensor
     * that wants a key or a coin fires whatever the hand holds; only the
     * right key or coin is used up, as it would be anyway.
     *
     * @param iconOf an item's inventory icon number, which item sensors compare with their data
     */
    public WallClick clickWall(int x, int y, Direction side, Party party, ToIntFunction<Item> iconOf) {
        Outcome out = new Outcome();
        List<WallSensor> rotate = new ArrayList<>();
        int cell = side.ordinal();
        boolean alcove = isAlcove(wallOrnament(x, y, side));
        boolean drank = false;
        if (wallOrnament(x, y, side) == FOUNTAIN) {
            Item held = party.held();
            if (held == null) {
                drank = party.drinkFromFountain();
            } else {
                Item filled = Upkeep.fill(held);
                if (filled != held) {
                    party.setHeld(filled);
                    out.handChanged = true;
                }
            }
        }
        for (WallSensor s : new ArrayList<>(wallSensors(x, y, side))) {
            if (!s.enabled()) {
                continue;
            }
            Item held = party.held();
            // Revert turns the item tests round (DM Encyclopaedia): an empty hand
            // instead of any item, any other item instead of the one named.
            boolean match = held != null && iconOf.applyAsInt(held) == s.data();
            boolean wanted = s.revert() ? held != null && !match : match || picked(s, party, iconOf);
            boolean fires = switch (s.type()) {
                case WallSensor.TYPE_CLICK -> !alcove;
                case WallSensor.TYPE_CLICK_WITH_ANY_ITEM -> (held != null) != s.revert();
                case WallSensor.TYPE_CLICK_WITH_ITEM -> wanted;
                case WallSensor.TYPE_CLICK_WITH_ITEM_USED_UP -> {
                    if (wanted && (match || s.revert())) { // lock master uses up only the right key or coin
                        party.setHeld(null);
                        out.handChanged = true;
                    }
                    yield wanted;
                }
                case WallSensor.TYPE_STORAGE_ROTATE -> !alcove && storage(x, y, cell, s.data(), party, iconOf, out);
                default -> false; // disabled sensors, gates (fed by events) and types not handled yet
            };
            if (fires) {
                fire(s, s.effect(), out, 0, rotate);
            }
        }
        // Each local firing rotates the side once: the first sensor moves to the end.
        List<WallSensor> list = sideSensors(x, y, side, false);
        for (int i = 0; i < rotate.size() && list != null && !list.isEmpty(); i++) {
            list.add(list.remove(0));
        }
        if (alcove) {
            Item held = party.held();
            if (held != null) {
                addItem(x, y, cell, held);
                party.setHeld(null);
                out.handChanged = true;
            } else {
                Item taken = takeItem(x, y, cell);
                if (taken != null) {
                    party.setHeld(taken);
                    out.handChanged = true;
                }
            }
        }
        if (!out.fired && !out.handChanged && !drank) {
            return WallClick.NOTHING;
        }
        return new WallClick(out.fired, out.sound, out.doorStarted, out.handChanged, drank);
    }

    /** Lock master: an item sensor that wants a key or a coin (not a revert one) opens without it. */
    private static boolean picked(WallSensor s, Party party, ToIntFunction<Item> iconOf) {
        if (!party.lockMaster() || s.type() != WallSensor.TYPE_CLICK_WITH_ITEM
                && s.type() != WallSensor.TYPE_CLICK_WITH_ITEM_USED_UP) {
            return false;
        }
        for (Item key : ItemCatalog.keysAndCoins()) {
            if (iconOf.applyAsInt(key) == s.data()) {
                return true;
            }
        }
        return false;
    }

    /** A storage sensor: swap the stored item (icon {@code icon}) with an empty hand, or take it back. */
    private boolean storage(int x, int y, int cell, int icon, Party party, ToIntFunction<Item> iconOf, Outcome out) {
        Item held = party.held();
        List<Item> pile = pile(x, y, cell, false);
        Item stored = null;
        if (pile != null) {
            for (Item i : pile) {
                if (iconOf.applyAsInt(i) == icon) {
                    stored = i;
                }
            }
        }
        if (held == null && stored != null) {
            pile.remove(stored);
            party.setHeld(stored);
            out.handChanged = true;
            return true;
        }
        if (held != null && stored == null && iconOf.applyAsInt(held) == icon) {
            addItem(x, y, cell, held);
            party.setHeld(null);
            out.handChanged = true;
            return true;
        }
        return false;
    }

    /** A door's own button toggles it. Returns true if the door starts moving. */
    public boolean pressDoorButton(int x, int y) {
        return isDoor(x, y) && toggleDoor(x, y);
    }

    // ---- items on the floor ------------------------------------------------
    //
    // Each square has 4 cells, numbered like DM: 0 north-west, 1 north-east,
    // 2 south-east, 3 south-west. Each cell holds a pile, bottom first; the
    // last item put down is on top and is the one picked up.

    private List<Item> pile(int x, int y, int cell, boolean create) {
        if (!inBounds(x, y)) {
            return null;
        }
        int key = ((x * height) + y) * 4 + (cell & 3);
        List<Item> pile = floorItems.get(key);
        if (pile == null && create) {
            pile = new ArrayList<>();
            floorItems.put(key, pile);
        }
        return pile;
    }

    /** The pile on cell {@code cell} of (x, y), bottom first. */
    public List<Item> itemsAt(int x, int y, int cell) {
        List<Item> pile = pile(x, y, cell, false);
        return pile == null ? List.of() : Collections.unmodifiableList(pile);
    }

    /** Puts {@code item} on top of the pile on cell {@code cell} of (x, y). */
    public void addItem(int x, int y, int cell, Item item) {
        List<Item> pile = pile(x, y, cell, true);
        if (pile != null) {
            pile.add(item);
        }
    }

    /** Removes and returns the top item on cell {@code cell} of (x, y), or null. */
    public Item takeItem(int x, int y, int cell) {
        List<Item> pile = pile(x, y, cell, false);
        return pile == null || pile.isEmpty() ? null : pile.remove(pile.size() - 1);
    }

    /** Whether any cell of (x, y) holds an item. */
    public boolean hasItems(int x, int y) {
        for (int cell = 0; cell < 4; cell++) {
            List<Item> pile = pile(x, y, cell, false);
            if (pile != null && !pile.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Puts {@code item} down on cell {@code cell} of floor square (x, y) in
     * play, unlike {@link #addItem}: it falls through an open pit to the
     * level below and is moved by an open teleporter that takes objects,
     * and the floor sensors where it ends up are checked (#17).
     */
    public StepResult dropItem(int x, int y, int cell, Item item) {
        Outcome out = new Outcome();
        drop(x, y, cell, item, out, 0);
        return out.result();
    }

    private void drop(int x, int y, int cell, Item item, Outcome out, int depth) {
        if (depth < MAX_CHAIN) {
            Dungeon.Location below = dropsThrough(x, y) ? below(x, y) : null;
            if (below != null) {
                below.map().drop(below.x(), below.y(), cell, item, out, depth + 1);
                return;
            }
            Teleporter t = activeTeleporter(x, y, Teleporter.Kind.ITEM);
            Dungeon.Location to = t == null ? null : destination(t);
            if (to != null) {
                out.sound |= t.audible();
                int turned = t.turn(Direction.fromIndex(cell)).ordinal();
                if (to.map() != this || to.x() != x || to.y() != y) {
                    to.map().drop(to.x(), to.y(), turned, item, out, depth + 1);
                    return;
                }
                cell = turned; // a spinner only turns it
            }
        }
        addItem(x, y, cell, item);
        updateSensors(x, y, out);
    }

    /** What picking an item up took, and what that set off (a plate released). */
    public record Pickup(Item item, StepResult result) {
    }

    /** Picks the top item off cell {@code cell} of floor square (x, y) and checks the floor sensors there (#17). */
    public Pickup pickUpItem(int x, int y, int cell) {
        Item item = takeItem(x, y, cell);
        if (item == null) {
            return new Pickup(null, StepResult.NOTHING);
        }
        Outcome out = new Outcome();
        updateSensors(x, y, out);
        return new Pickup(item, out.result());
    }

    // ---- things in flight ----------------------------------------------------

    /** Items in flight on this map, oldest first ({@link Flight} moves them). */
    public List<Projectile> projectiles() {
        return Collections.unmodifiableList(projectiles);
    }

    List<Projectile> projectileList() {
        return projectiles;
    }

    // ---- champions ---------------------------------------------------------

    public List<ChampionMirror> mirrors() {
        return mirrors;
    }

    /** The mirror hanging on the given side of wall square (x, y), or null. */
    public ChampionMirror mirrorAt(int x, int y, Direction side) {
        for (ChampionMirror m : mirrors) {
            if (m.x() == x && m.y() == y && m.side() == side) {
                return m;
            }
        }
        return null;
    }

    // ---- ASCII -------------------------------------------------------------

    public String toAscii(Party party) {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (party != null && party.x() == x && party.y() == y) {
                    sb.append("^>v<".charAt(party.facing().ordinal()));
                } else if (hasCreatures(x, y)) {
                    sb.append('M');
                } else if (isDoor(x, y)) {
                    sb.append(isPassable(x, y) ? 'd' : 'D');
                } else if (floorOrnament(x, y) >= 0) {
                    sb.append('_');
                } else {
                    sb.append(charFor(squares[x][y]));
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    static char charFor(Square s) {
        return switch (s.type()) {
            case WALL -> '#';
            case CORRIDOR -> '.';
            case PIT -> 'O';
            case STAIRS -> 'S';
            case DOOR -> s.isDoorOpen() ? 'd' : 'D';
            case TELEPORTER -> 'T';
            case FAKEWALL -> 'F';
        };
    }

    static Square squareFor(char c) {
        int element = switch (c) {
            case '.', ' ' -> 1;
            case 'O' -> 2;
            case 'S' -> 3;
            case 'D', 'd' -> 4;
            case 'T' -> 5;
            case 'F' -> 6;
            default -> 0;
        };
        int attrs = switch (c) {
            case 'D' -> 4; // closed door state
            case 'O' -> 8; // open pit
            default -> 0;
        };
        return new Square((element << 5) | attrs);
    }
}
