package dm.model;

/** Square element types, indexed by the top 3 bits of a raw DUNGEON.DAT square byte. */
public enum SquareType {
    WALL,
    CORRIDOR,
    PIT,
    STAIRS,
    DOOR,
    TELEPORTER,
    FAKEWALL;

    public static SquareType fromRaw(int raw) {
        int element = (raw & 0xFF) >>> 5;
        // Element 7 is unused in the original data; treat it as solid rock.
        return element < values().length ? values()[element] : WALL;
    }
}
