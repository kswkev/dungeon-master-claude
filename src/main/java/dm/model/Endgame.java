package dm.model;

/**
 * DM's fuse sequence (ReDMCSB STARTND2.C F446), started when FUSE catches
 * Lord Chaos: a script the screen runs one step per game tick.
 */
public final class Endgame {

    private final Party party;
    private final DungeonMap map;
    private final int x;
    private final int y;

    Endgame(Party party, DungeonMap map, int x, int y) {
        this.party = party;
        this.map = map;
        this.x = x;
        this.y = y;
    }

    /** Where Lord Chaos stands. */
    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public DungeonMap map() {
        return map;
    }

    Party party() {
        return party;
    }
}
