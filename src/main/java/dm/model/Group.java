package dm.model;

import java.io.Serializable;
import java.util.List;

/**
 * A group of one to four creatures of one type on a square, from a
 * DUNGEON.DAT group thing (type 4), as the DM Encyclopaedia documents it:
 * <pre>
 *   word 1   possessions (a thing list dropped when the group dies)
 *   byte     creature type (0-26)
 *   byte     cells: 2 bits per creature, creature 1 in bits 0-1;
 *            0xFF for a single creature in the centre of the square
 *   word x4  hit points of creatures 1-4
 *   word     bits 8-9 direction, bits 5-6 creature count - 1
 * </pre>
 */
public final class Group implements Serializable {

    private static final long serialVersionUID = 1L;

    /** The cells value for one creature standing in the middle of the square. */
    public static final int CENTRED = 0xFF;

    private final CreatureType type;
    private int x;
    private int y;
    private final int cells;
    private final int[] health;
    private final int count;
    private Direction facing;
    private final List<Item> possessions;

    public Group(CreatureType type, int x, int y, int cells, int[] health, int count, Direction facing,
                 List<Item> possessions) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.cells = cells;
        this.health = health.clone();
        this.count = count;
        this.facing = facing;
        this.possessions = List.copyOf(possessions);
    }

    public CreatureType type() {
        return type;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int count() {
        return count;
    }

    public Direction facing() {
        return facing;
    }

    public void face(Direction d) {
        facing = d;
    }

    /** Whether the group is a single creature in the centre of the square. */
    public boolean centred() {
        return cells == CENTRED;
    }

    /** The cell (0 NW, 1 NE, 2 SE, 3 SW) creature {@code i} stands on; meaningless when {@link #centred()}. */
    public int cellOf(int i) {
        return (cells >> (i * 2)) & 3;
    }

    /** The creature standing on {@code cell}, or -1. */
    public int creatureOn(int cell) {
        if (centred()) {
            return -1;
        }
        for (int i = 0; i < count; i++) {
            if (cellOf(i) == cell) {
                return i;
            }
        }
        return -1;
    }

    public int health(int i) {
        return health[i];
    }

    /** What the group carries, dropped when it dies (combat comes later). */
    public List<Item> possessions() {
        return possessions;
    }

    @Override
    public String toString() {
        return count + " " + type.displayName() + " at (" + x + "," + y + ") facing " + facing;
    }
}
