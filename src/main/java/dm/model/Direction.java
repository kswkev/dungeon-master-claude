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

    /**
     * Square cells as seen facing this way: view cell 0 back-left, 1
     * back-right, 2 front-right, 3 front-left ("back" = further away).
     * Facing north they equal DM's absolute cells (0 NW, 1 NE, 2 SE, 3 SW).
     */
    public int cellOf(int viewCell) {
        return (viewCell + ordinal()) & 3;
    }

    /** The inverse of {@link #cellOf}: where absolute cell {@code cell} appears in the view. */
    public int viewCellOf(int cell) {
        return (cell - ordinal()) & 3;
    }
}
