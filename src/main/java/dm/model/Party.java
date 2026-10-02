package dm.model;

/** Party position and facing on the current map. */
public final class Party {

    /** Relative moves offered by the movement arrow panel. */
    public enum Move {
        FORWARD(0), RIGHT(1), BACKWARD(2), LEFT(3);

        /** Clockwise quarter-turns from the facing direction. */
        final int turns;

        Move(int turns) {
            this.turns = turns;
        }
    }

    private DungeonMap map;
    private int x;
    private int y;
    private Direction facing;

    public Party(DungeonMap map, int x, int y, Direction facing) {
        this.map = map;
        this.x = x;
        this.y = y;
        this.facing = facing;
    }

    public DungeonMap map() {
        return map;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public Direction facing() {
        return facing;
    }

    public void turnLeft() {
        facing = facing.turnLeft();
    }

    public void turnRight() {
        facing = facing.turnRight();
    }

    /** Attempts a step; returns false (and stays put) if the target square blocks. */
    public boolean move(Move move) {
        Direction d = Direction.fromIndex(facing.ordinal() + move.turns);
        int nx = x + d.dx;
        int ny = y + d.dy;
        if (!map.isPassable(nx, ny)) {
            return false;
        }
        x = nx;
        y = ny;
        return true;
    }
}
