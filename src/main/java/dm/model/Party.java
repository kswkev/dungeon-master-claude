package dm.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    public static final int MAX_MEMBERS = 4;

    private final DungeonMap map;
    private final List<Champion> members = new ArrayList<>();
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

    public List<Champion> members() {
        return Collections.unmodifiableList(members);
    }

    public boolean isFull() {
        return members.size() >= MAX_MEMBERS;
    }

    /** Resurrects the mirror's champion into the party; false if the party is full or the mirror is empty. */
    public boolean recruit(ChampionMirror mirror) {
        if (isFull() || mirror.taken()) {
            return false;
        }
        members.add(mirror.champion());
        mirror.markTaken();
        return true;
    }

    /** The mirror straight ahead on the adjacent wall, if there is one with a champion still in it. */
    public ChampionMirror facingMirror() {
        ChampionMirror m = map.mirrorAt(x + facing.dx, y + facing.dy, facing.opposite());
        return m == null || m.taken() ? null : m;
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
