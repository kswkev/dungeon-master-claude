package dm.model;

/**
 * The teleporter on square (x, y), decoded from a DUNGEON.DAT teleporter thing.
 * What steps onto it while it is open is moved to (targetX, targetY) on map
 * {@code targetMap} (an index into the dungeon's maps, not a level number).
 *
 * @param rotation quarter-turns clockwise; with {@code absolute} the new facing itself
 * @param scope    what it moves: {@link #SCOPE_OBJECTS} and/or {@link #SCOPE_CREATURES}
 *                 (the party counts as a creature, as in DM)
 */
public record Teleporter(int x, int y, int targetMap, int targetX, int targetY, int rotation, boolean absolute,
                         int scope, boolean audible) {

    public static final int SCOPE_OBJECTS = 1;
    public static final int SCOPE_CREATURES = 2;

    public boolean moves(int what) {
        return (scope & what) != 0;
    }

    /** The facing (or cell) {@code from} becomes on arrival. */
    public Direction turn(Direction from) {
        return absolute ? Direction.fromIndex(rotation) : Direction.fromIndex(from.ordinal() + rotation);
    }
}
