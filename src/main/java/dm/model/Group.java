package dm.model;

import java.io.Serializable;
import java.util.ArrayList;
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
 *   word     bits 0-3 behaviour, 5-6 creature count - 1, 8-9 direction
 * </pre>
 *
 * In play a group also carries what DM keeps in its ACTIVE_GROUP record
 * (see {@link CreatureAI}): a direction and a look per creature, the
 * square it is heading for, the square it came from, and so on.
 */
public final class Group implements Serializable {

    private static final long serialVersionUID = 2L;

    /** The cells value for one creature standing in the middle of the square. */
    public static final int CENTRED = 0xFF;

    /** DM's behaviours (C0, C5-C7). */
    public static final int WANDER = 0;
    public static final int FLEE = 5;
    public static final int ATTACK = 6;
    public static final int APPROACH = 7;

    /** Aspect bits, as DM's: the picture is mirrored, the creature is attacking. */
    static final int ASPECT_FLIP = 0x40;
    static final int ASPECT_ATTACKING = 0x80;

    private final CreatureType type;
    private int x;
    private int y;
    private int cells;
    private final int[] health;
    private int count;
    /** Each creature's direction, 2 bits per creature like the cells. */
    private int directions;
    private int behaviour;
    private final List<Item> possessions;

    // ---- DM's ACTIVE_GROUP: set while the party is on the group's map ----
    private final int[] aspect = new int[4];
    int targetX;
    int targetY;
    int priorX;
    int priorY;
    int homeX;
    int homeY;
    long lastMoveTime;
    int delayFleeing;

    public Group(CreatureType type, int x, int y, int cells, int[] health, int count, Direction facing,
                 List<Item> possessions) {
        this(type, x, y, cells, health, count, facing, WANDER, possessions);
    }

    public Group(CreatureType type, int x, int y, int cells, int[] health, int count, Direction facing,
                 int behaviour, List<Item> possessions) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.cells = cells;
        this.health = health.clone();
        this.count = count;
        this.behaviour = behaviour;
        this.possessions = new ArrayList<>(possessions);
        face(facing);
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

    void moveTo(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int count() {
        return count;
    }

    /** The way the group as a whole faces: its first creature's direction, as DM saves it. */
    public Direction facing() {
        return facing(0);
    }

    /** The way creature {@code i} faces. */
    public Direction facing(int i) {
        return Direction.fromIndex(directions >> (i * 2));
    }

    /** Turns every creature in the group to {@code d}. */
    public void face(Direction d) {
        for (int i = 0; i < 4; i++) {
            setFacing(i, d.ordinal());
        }
    }

    void setFacing(int i, int dir) {
        directions = (directions & ~(3 << (i * 2))) | ((dir & 3) << (i * 2));
    }

    /** Whether the group is a single creature in the centre of the square. */
    public boolean centred() {
        return cells == CENTRED;
    }

    /** The cell (0 NW, 1 NE, 2 SE, 3 SW) creature {@code i} stands on; meaningless when {@link #centred()}. */
    public int cellOf(int i) {
        return (cells >> (i * 2)) & 3;
    }

    int cells() {
        return cells;
    }

    void setCells(int cells) {
        this.cells = cells;
    }

    void setCell(int i, int cell) {
        cells = (cells & ~(3 << (i * 2))) | ((cell & 3) << (i * 2));
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

    void setHealth(int i, int value) {
        health[i] = value;
    }

    /** DM's behaviour: {@link #WANDER}, {@link #FLEE}, {@link #ATTACK} or {@link #APPROACH}. */
    public int behaviour() {
        return behaviour;
    }

    void setBehaviour(int behaviour) {
        this.behaviour = behaviour;
    }

    /** Whether creature {@code i} is showing its attack picture. */
    public boolean attacking(int i) {
        return (aspect[i] & ASPECT_ATTACKING) != 0;
    }

    /** Whether creature {@code i}'s picture is mirrored (DM flips some creatures as they move or strike). */
    public boolean flipped(int i) {
        return (aspect[i] & ASPECT_FLIP) != 0;
    }

    /** Creature {@code i}'s picture offset, -3..3 pixels each way. */
    public int jitterX(int i) {
        int v = aspect[i] & 7;
        return v > 3 ? v - 8 : v;
    }

    public int jitterY(int i) {
        int v = (aspect[i] >> 3) & 7;
        return v > 3 ? v - 8 : v;
    }

    int aspect(int i) {
        return aspect[i];
    }

    void setAspect(int i, int value) {
        aspect[i] = value;
    }

    /**
     * Removes creature {@code i}, as DM does when one dies: the creatures
     * after it move down a place. Returns false if it was the last one.
     */
    boolean remove(int i) {
        if (count == 1) {
            health[0] = 0;
            return false;
        }
        for (int j = i; j < count - 1; j++) {
            health[j] = health[j + 1];
            setFacing(j, directions >> ((j + 1) * 2));
            if (!centred()) {
                setCell(j, cellOf(j + 1));
            }
            aspect[j] = aspect[j + 1];
        }
        count--;
        health[count] = 0;
        if (!centred()) {
            cells &= (1 << (count * 2)) - 1;
        }
        return true;
    }

    /** What the group carries, dropped when it dies. */
    public List<Item> possessions() {
        return possessions;
    }

    @Override
    public String toString() {
        return count + " " + type.displayName() + " at (" + x + "," + y + ") facing " + facing();
    }
}
