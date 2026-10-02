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
    /**
     * Formation positions, numbered like DM's cells, clockwise from the front
     * left (relative to the way the party faces). New recruits fill them in
     * this order.
     */
    public static final int FRONT_LEFT = 0;
    public static final int FRONT_RIGHT = 1;
    public static final int BACK_RIGHT = 2;
    public static final int BACK_LEFT = 3;
    /**
     * Damage for walking into a wall. In DM this is 1 point reduced by torso
     * and leg armour; armour values aren't modelled yet, so it is a flat 1.
     */
    public static final int BUMP_DAMAGE = 1;
    /**
     * How many squares a thrown item flies. In DM it depends on the
     * thrower's strength and the item's weight, which aren't modelled yet.
     */
    public static final int THROW_RANGE = 4;

    private final DungeonMap map;
    private final List<Champion> members = new ArrayList<>();
    private final Champion[] positions = new Champion[MAX_MEMBERS];
    private int x;
    private int y;
    private Direction facing;
    /** The item on the mouse pointer (DM's leader hand), shared by the whole party. */
    private Item held;

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

    /** The item being carried on the mouse pointer, or null. */
    public Item held() {
        return held;
    }

    public void setHeld(Item item) {
        held = item;
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
        for (int p = 0; p < MAX_MEMBERS; p++) {
            if (positions[p] == null) {
                positions[p] = mirror.champion();
                break;
            }
        }
        mirror.markTaken();
        return true;
    }

    /** The champion standing in formation position {@code position}, or null. */
    public Champion at(int position) {
        return positions[position];
    }

    /** The formation position of {@code champion}, or -1 if not in the party. */
    public int positionOf(Champion champion) {
        for (int p = 0; p < MAX_MEMBERS; p++) {
            if (positions[p] == champion) {
                return p;
            }
        }
        return -1;
    }

    /** Swaps whoever stands in two formation positions; either may be empty, which moves a champion. */
    public void swap(int a, int b) {
        Champion tmp = positions[a];
        positions[a] = positions[b];
        positions[b] = tmp;
    }

    /** The mirror straight ahead on the adjacent wall, if there is one with a champion still in it. */
    public ChampionMirror facingMirror() {
        ChampionMirror m = map.mirrorAt(x + facing.dx, y + facing.dy, facing.opposite());
        return m == null || m.taken() ? null : m;
    }

    /**
     * Walking into a wall hurts the two champions on the side that hit it:
     * the front row going forward, the back row going backward, and the left
     * or right pair when sidestepping. Returns the damage each member took,
     * indexed like {@link #members()}.
     */
    public int[] bump(Move move) {
        int[] hit = switch (move) {
            case FORWARD -> new int[] {FRONT_LEFT, FRONT_RIGHT};
            case BACKWARD -> new int[] {BACK_LEFT, BACK_RIGHT};
            case LEFT -> new int[] {FRONT_LEFT, BACK_LEFT};
            case RIGHT -> new int[] {FRONT_RIGHT, BACK_RIGHT};
        };
        int[] damage = new int[members.size()];
        for (int p : hit) {
            if (positions[p] != null) {
                damage[members.indexOf(positions[p])] = positions[p].takeDamage(BUMP_DAMAGE);
            }
        }
        return damage;
    }

    public void turnLeft() {
        facing = facing.turnLeft();
    }

    public void turnRight() {
        facing = facing.turnRight();
    }

    /** Attempts a step; returns false (and stays put) if the target square blocks. */
    public boolean move(Move move) {
        return step(move) != null;
    }

    /**
     * Attempts a step and runs the floor sensors on the squares left and
     * entered. Returns null if the move was blocked.
     */
    public DungeonMap.StepResult step(Move move) {
        Direction d = Direction.fromIndex(facing.ordinal() + move.turns);
        int nx = x + d.dx;
        int ny = y + d.dy;
        if (!map.isPassable(nx, ny)) {
            return null;
        }
        int fromX = x;
        int fromY = y;
        x = nx;
        y = ny;
        return map.partyMoved(this, fromX, fromY);
    }
}
