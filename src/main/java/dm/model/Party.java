package dm.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** The party: its champions, and its position and facing on the current map. */
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
    /**
     * DM's attack strength for falling into a pit (F0324 with 20): each
     * champion takes half of it plus a random amount below that half, so
     * 10-19 points. In DM leg and foot armour soften it; armour isn't
     * modelled yet.
     */
    public static final int FALL_ATTACK = 20;
    /** Falls and teleports chained in one move before the party is taken to be stuck in a loop. */
    private static final int MAX_HOPS = 8;

    /** Every level of the dungeon; stairs, pits and teleporters move the party between them. */
    private final Dungeon dungeon;
    private DungeonMap map;
    private Random random = new Random();
    private final List<Champion> members = new ArrayList<>();
    private final Champion[] positions = new Champion[MAX_MEMBERS];
    private int x;
    private int y;
    private Direction facing;
    /** The item on the mouse pointer (DM's leader hand), shared by the whole party. */
    private Item held;

    /** A party on a single map; its stairs lead nowhere and block like walls. */
    public Party(DungeonMap map, int x, int y, Direction facing) {
        this(List.of(map), 0, x, y, facing);
    }

    /** A party on {@code maps.get(mapIndex)}, able to take stairs to the other maps. */
    public Party(List<DungeonMap> maps, int mapIndex, int x, int y, Direction facing) {
        this.dungeon = new Dungeon(maps);
        this.map = maps.get(mapIndex);
        this.x = x;
        this.y = y;
        this.facing = facing;
        map.placeParty(this);
    }

    /** Replaces the random numbers behind fall damage, for tests. */
    public void setRandom(Random random) {
        this.random = random;
    }

    /** The map the party is on. */
    public DungeonMap map() {
        return map;
    }

    /** The current dungeon level, 0 for the first. */
    public int level() {
        return map.level();
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
     *
     * Stepping onto stairs takes the party to the level above or below, as
     * in DM: onto the square beside the matching stairs there, facing away
     * from them. Stairs with no matching stairs on the next level block.
     * Wherever the party ends up, open pits and teleporters then act on it
     * ({@link #settle()}).
     */
    public DungeonMap.StepResult step(Move move) {
        Direction d = Direction.fromIndex(facing.ordinal() + move.turns);
        int nx = x + d.dx;
        int ny = y + d.dy;
        if (!map.isPassable(nx, ny)) {
            return null;
        }
        Square target = map.get(nx, ny);
        Dungeon.Location stairs = null;
        if (target.type() == SquareType.STAIRS) {
            stairs = dungeon.stairsPartner(map, nx, ny, target.stairsUp());
            if (stairs == null) {
                return null;
            }
        }
        DungeonMap.StepResult result = moveTo(map, nx, ny);
        if (stairs != null) {
            DungeonMap.StairsExit exit = stairs.map().stairsExit(stairs.x(), stairs.y());
            facing = exit.facing();
            result = result.and(moveTo(stairs.map(), exit.x(), exit.y()));
        }
        return result.and(settle());
    }

    /**
     * Puts the party on (nx, ny) of {@code to}, running the floor sensors on
     * the square it left and the one it arrived on.
     */
    private DungeonMap.StepResult moveTo(DungeonMap to, int nx, int ny) {
        DungeonMap from = map;
        int fromX = x;
        int fromY = y;
        x = nx;
        y = ny;
        if (to == from) {
            return from.partyMoved(this, fromX, fromY);
        }
        map = to;
        DungeonMap.StepResult left = from.partyLeft(fromX, fromY);
        return left.and(to.partyMoved(this, -1, -1))
                .and(new DungeonMap.StepResult(false, false, true));
    }

    /**
     * Lets the square under the party act on it, as many times as it takes:
     * an open pit (not an imaginary one) drops it to the same spot one level
     * down, hurting every champion; an open teleporter that moves the party
     * sends it to its target, turned as the teleporter says (one that
     * targets its own square is a spinner: it only turns). Called after
     * every step, and by the game whenever a sensor may have opened a pit or
     * teleporter under the party.
     */
    public DungeonMap.StepResult settle() {
        DungeonMap.StepResult result = DungeonMap.StepResult.NOTHING;
        for (int hop = 0; hop < MAX_HOPS; hop++) {
            Dungeon.Location below = map.dropsThrough(x, y) ? map.below(x, y) : null;
            if (below != null) {
                result = result.and(moveTo(below.map(), below.x(), below.y()))
                        .and(new DungeonMap.StepResult(false, false, false, true, false, fall()));
                continue;
            }
            Teleporter t = map.activeTeleporter(x, y, Teleporter.Kind.PARTY);
            Dungeon.Location to = t == null ? null : map.destination(t);
            if (to == null) {
                break;
            }
            facing = t.turn(facing);
            boolean spinner = to.map() == map && to.x() == x && to.y() == y;
            result = result.and(spinner ? DungeonMap.StepResult.NOTHING : moveTo(to.map(), to.x(), to.y()))
                    .and(new DungeonMap.StepResult(false, t.audible(), false, false, true, null));
            if (spinner) {
                break; // a teleporter onto itself only turns the party
            }
        }
        return result;
    }

    /** Fall damage for each champion, indexed like {@link #members()}. */
    private int[] fall() {
        int[] damage = new int[members.size()];
        int half = FALL_ATTACK / 2;
        for (int i = 0; i < damage.length; i++) {
            damage[i] = members.get(i).takeDamage(half + random.nextInt(half));
        }
        return damage;
    }
}
