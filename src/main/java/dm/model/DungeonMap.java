package dm.model;

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
 * side: alcove and torch-holder contents), and thrown items fly one square
 * per {@link #tickProjectiles()}. Pits open and close, and wall sensors
 * change state when clicked ({@link #clickWall}).
 */
public final class DungeonMap {

    public static final int DOOR_OPEN = 0;
    public static final int DOOR_CLOSED = 4;
    public static final int DOOR_BROKEN = 5;

    /** What a party step set off: a door started moving, and/or an audible sensor clicked. */
    public record StepResult(boolean doorStarted, boolean click) {
        public static final StepResult NOTHING = new StepResult(false, false);
    }

    private final int level;
    private final Square[][] squares;
    private final int width;
    private final int height;
    private final List<ChampionMirror> mirrors;
    private final int[][] doorStyles;
    private final int[][] doorState;
    private final int[][] doorTarget;
    private final List<FloorSensor> sensors = new ArrayList<>();
    private final Map<Integer, List<Item>> floorItems = new HashMap<>();
    private final Map<Integer, List<WallSensor>> wallSensors = new HashMap<>();
    private final boolean[][] pitOpen;
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
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (squares[x][y].type() == SquareType.DOOR) {
                    doorState[x][y] = doorTarget[x][y] = squares[x][y].doorState();
                }
                pitOpen[x][y] = squares[x][y].type() == SquareType.PIT && squares[x][y].pitOpen();
            }
        }
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
        return doorStyles != null && inBounds(x, y) ? doorStyles[x][y] : 0;
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
     * What a door tick did: whether any door moved, and whether any door
     * rattled. As in DM, a door rattles on every step except the last one,
     * where it settles fully open or shut, so a full 4-step move rattles 3 times.
     */
    public record DoorTick(boolean moved, boolean rattled) {
        public static final DoorTick NOTHING = new DoorTick(false, false);
    }

    /** Moves every door that isn't at its target one step. */
    public DoorTick tickDoors() {
        boolean moved = false;
        boolean rattled = false;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int state = doorState[x][y];
                int target = doorTarget[x][y];
                if (state != target && state != DOOR_BROKEN) {
                    int next = state + Integer.signum(target - state);
                    doorState[x][y] = next;
                    moved = true;
                    rattled |= next != target;
                }
            }
        }
        return moved ? new DoorTick(true, rattled) : DoorTick.NOTHING;
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
     * Runs the floor sensors for a party that has just moved from (fromX, fromY)
     * to its current square. Sensors on the new square fire their effect;
     * sensors on the old square undo theirs if they are HOLD or revert sensors.
     */
    public StepResult partyMoved(Party party, int fromX, int fromY) {
        Outcome out = new Outcome();
        for (FloorSensor s : sensors) {
            boolean left = s.x() == fromX && s.y() == fromY;
            boolean entered = s.x() == party.x() && s.y() == party.y();
            if (left == entered || !s.triggeredBy(party)) {
                continue;
            }
            FloorSensor.Effect effect;
            if (entered) {
                effect = s.effect();
            } else if (s.effect() == FloorSensor.Effect.HOLD || s.revert()) {
                effect = opposite(s.effect());
            } else {
                continue;
            }
            applyEffect(s.targetX(), s.targetY(), 0, effect, out, 0);
            out.sound |= s.audible();
            if (entered) {
                s.used();
            }
        }
        return out.doorStarted || out.sound ? new StepResult(out.doorStarted, out.sound) : StepResult.NOTHING;
    }

    /** The effect that undoes {@code effect}: set and clear swap, a toggle toggles back. */
    private static FloorSensor.Effect opposite(FloorSensor.Effect effect) {
        return switch (effect) {
            case SET, HOLD -> FloorSensor.Effect.CLEAR;
            case CLEAR -> FloorSensor.Effect.SET;
            case TOGGLE -> FloorSensor.Effect.TOGGLE;
        };
    }

    /** What a chain of sensor effects did, collected as it runs. */
    private static final class Outcome {
        boolean doorStarted;
        boolean sound;
        boolean fired;
        boolean handChanged;
    }

    /** Gates can feed gates; deeper chains than this are taken to be loops. */
    private static final int MAX_CHAIN = 8;

    /**
     * Sends a sensor effect to square (x, y): a door opens (SET), closes
     * (CLEAR) or toggles; a pit opens, closes or toggles; on a wall square
     * every AND/OR gate gets the effect as input {@code cell}. Other targets
     * (teleporters, creatures...) don't respond yet.
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
            case PIT -> pitOpen[x][y] = switch (effect) {
                case SET, HOLD -> true;
                case CLEAR -> false;
                case TOGGLE -> !pitOpen[x][y];
            };
            case WALL -> {
                for (Direction side : Direction.values()) {
                    for (WallSensor gate : wallSensors(x, y, side)) {
                        if (gate.type() != WallSensor.TYPE_AND_OR_GATE || !gate.enabled()) {
                            continue;
                        }
                        boolean was = gate.gateSatisfied();
                        if (gate.gateInput(cell, effect)) {
                            fire(gate, gate.effect(), out, depth + 1, null);
                        } else if (was && gate.revert()) {
                            fire(gate, opposite(gate.effect()), out, depth + 1, null);
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

    // ---- pits --------------------------------------------------------------

    /** Live pit state: sensors can open and close pits. */
    public boolean isPitOpen(int x, int y) {
        return inBounds(x, y) && squares[x][y].type() == SquareType.PIT && pitOpen[x][y];
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

    /** What clicking a wall did. */
    public record WallClick(boolean fired, boolean sound, boolean doorStarted, boolean handChanged) {
        public static final WallClick NOTHING = new WallClick(false, false, false, false);
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
     * Sensors that fired locally then rotate their side once each. Finally,
     * an alcove takes the held item or hands over its top one.
     *
     * @param iconOf an item's inventory icon number, which item sensors compare with their data
     */
    public WallClick clickWall(int x, int y, Direction side, Party party, ToIntFunction<Item> iconOf) {
        Outcome out = new Outcome();
        List<WallSensor> rotate = new ArrayList<>();
        int cell = side.ordinal();
        for (WallSensor s : new ArrayList<>(wallSensors(x, y, side))) {
            if (!s.enabled()) {
                continue;
            }
            Item held = party.held();
            boolean fires = switch (s.type()) {
                case WallSensor.TYPE_CLICK -> true;
                case WallSensor.TYPE_CLICK_WITH_ANY_ITEM -> held != null;
                case WallSensor.TYPE_CLICK_WITH_ITEM -> held != null && iconOf.applyAsInt(held) == s.data();
                case WallSensor.TYPE_CLICK_WITH_ITEM_USED_UP -> {
                    if (held != null && iconOf.applyAsInt(held) == s.data()) {
                        party.setHeld(null);
                        out.handChanged = true;
                        yield true;
                    }
                    yield false;
                }
                case WallSensor.TYPE_STORAGE_ROTATE -> storage(x, y, cell, s.data(), party, iconOf, out);
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
        if (isAlcove(wallOrnament(x, y, side))) {
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
        if (!out.fired && !out.handChanged) {
            return WallClick.NOTHING;
        }
        return new WallClick(out.fired, out.sound, out.doorStarted, out.handChanged);
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

    // ---- thrown items ------------------------------------------------------

    /** Items in flight, oldest first. */
    public List<Projectile> projectiles() {
        return Collections.unmodifiableList(projectiles);
    }

    /**
     * Throws {@code item} from (x, y) toward {@code direction}, on the left or
     * right side. It starts in the thrower's square and lands, on the far cell
     * of its side, in the last open square it reaches.
     */
    public void throwItem(Item item, int x, int y, Direction direction, boolean rightSide, int range) {
        int cell = direction.cellOf(rightSide ? 1 : 0);
        projectiles.add(new Projectile(item, x, y, direction, cell, range));
    }

    /**
     * Moves every item in flight one square. One that can't go further (a
     * wall or closed door ahead, or out of range) drops onto its square.
     * Returns true if anything was in flight.
     */
    public boolean tickProjectiles() {
        if (projectiles.isEmpty()) {
            return false;
        }
        List<Projectile> flying = new ArrayList<>();
        for (Projectile p : projectiles) {
            Projectile next = p.advance();
            if (p.range() > 0 && isPassable(next.x(), next.y())) {
                flying.add(next);
            } else {
                addItem(p.x(), p.y(), p.cell(), p.item());
            }
        }
        projectiles.clear();
        projectiles.addAll(flying);
        return true;
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
