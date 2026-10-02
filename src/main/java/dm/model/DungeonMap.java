package dm.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single dungeon level. Squares are addressed as [x][y]; anything out of bounds is solid.
 *
 * The square bytes are fixed, but doors and floor sensors have live state:
 * a door's state (0 open .. 4 closed, 5 broken) starts from its square byte
 * and moves one step per {@link #tickDoors()} toward a target set by sensors.
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
        this.doorState = new int[width][height];
        this.doorTarget = new int[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (squares[x][y].type() == SquareType.DOOR) {
                    doorState[x][y] = doorTarget[x][y] = squares[x][y].doorState();
                }
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

    /** The floor ornament drawn on (x, y) (e.g. 1 = square pressure plate), or -1. */
    public int floorOrnament(int x, int y) {
        for (FloorSensor s : sensors) {
            if (s.x() == x && s.y() == y && s.ornament() >= 0) {
                return s.ornament();
            }
        }
        return -1;
    }

    /**
     * Runs the floor sensors for a party that has just moved from (fromX, fromY)
     * to its current square. Sensors on the new square fire their effect;
     * sensors on the old square undo theirs if they are HOLD or revert sensors.
     */
    public StepResult partyMoved(Party party, int fromX, int fromY) {
        boolean doorStarted = false;
        boolean click = false;
        for (FloorSensor s : sensors) {
            boolean left = s.x() == fromX && s.y() == fromY;
            boolean entered = s.x() == party.x() && s.y() == party.y();
            if (left == entered || !s.triggeredBy(party)) {
                continue;
            }
            Boolean open;
            if (entered) {
                open = switch (s.effect()) {
                    case SET, HOLD -> Boolean.TRUE;
                    case CLEAR -> Boolean.FALSE;
                    case TOGGLE -> null;
                };
            } else if (s.effect() == FloorSensor.Effect.HOLD || s.revert()) {
                open = switch (s.effect()) {
                    case SET, HOLD -> Boolean.FALSE;
                    case CLEAR -> Boolean.TRUE;
                    case TOGGLE -> null;
                };
            } else {
                continue;
            }
            boolean started = open == null ? toggleDoor(s.targetX(), s.targetY())
                    : moveDoor(s.targetX(), s.targetY(), open);
            doorStarted |= started;
            click |= s.audible();
            if (entered) {
                s.used();
            }
        }
        return doorStarted || click ? new StepResult(doorStarted, click) : StepResult.NOTHING;
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
