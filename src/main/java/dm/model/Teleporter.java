package dm.model;

import java.io.Serializable;

/**
 * The teleporter on square (x, y), decoded from a DUNGEON.DAT teleporter thing.
 * What steps onto it while it is open is moved to (targetX, targetY) on map
 * {@code targetMap} (an index into the dungeon's maps, not a level number).
 *
 * @param rotation quarter-turns clockwise; with {@code absolute} the new facing itself
 * @param scope    DM's scope value: {@link #SCOPE_ITEMS}, {@link #SCOPE_CREATURES},
 *                 {@link #SCOPE_ITEMS_AND_PARTY} or {@link #SCOPE_EVERYTHING}
 */
public record Teleporter(int x, int y, int targetMap, int targetX, int targetY, int rotation, boolean absolute,
                         int scope, boolean audible) implements Serializable {

    /**
     * Items only. Checked in the original (#20): an item thrown into Level 2's
     * (13,16) arrives at (14,14), but the party walks over it untouched.
     */
    public static final int SCOPE_ITEMS = 0;
    /** Creatures only: all 30 in the PC file are invisible one-square nudges that keep monsters in their rooms. */
    public static final int SCOPE_CREATURES = 1;
    public static final int SCOPE_ITEMS_AND_PARTY = 2;
    public static final int SCOPE_EVERYTHING = 3;

    /** What may be moved, for {@link #moves}. */
    public enum Kind { ITEM, CREATURE, PARTY }

    public boolean moves(Kind what) {
        return switch (what) {
            case ITEM -> scope != SCOPE_CREATURES;
            case CREATURE -> scope == SCOPE_CREATURES || scope == SCOPE_EVERYTHING;
            case PARTY -> scope == SCOPE_ITEMS_AND_PARTY || scope == SCOPE_EVERYTHING;
        };
    }

    /** The facing (or cell) {@code from} becomes on arrival. */
    public Direction turn(Direction from) {
        return absolute ? Direction.fromIndex(rotation) : Direction.fromIndex(from.ordinal() + rotation);
    }
}
