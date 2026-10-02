package dm.model;

/**
 * Facing directions, in the same order DM encodes them (0 = north, clockwise).
 * Map X grows east, map Y grows south.
 */
public enum Direction {
    NORTH(0, -1),
    EAST(1, 0),
    SOUTH(0, 1),
    WEST(-1, 0);

    public final int dx;
    public final int dy;

    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public static Direction fromIndex(int index) {
        return values()[index & 3];
    }

    public Direction turnRight() {
        return values()[(ordinal() + 1) & 3];
    }

    public Direction turnLeft() {
        return values()[(ordinal() + 3) & 3];
    }

    public Direction opposite() {
        return values()[(ordinal() + 2) & 3];
    }
}
