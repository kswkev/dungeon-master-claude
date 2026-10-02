package dm.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two levels joined by stairs: the upper map's east-west down stairs at
 * (1,1) and the lower map's north-south up stairs at (2,0) are the same
 * dungeon-wide square once each map's offset is added, as in DUNGEON.DAT.
 */
class StairsTest {

    /** Square bytes: stairs (element 3) running east-west, going down; and north-south going up. */
    private static Square stairs(boolean up, boolean northSouth) {
        return new Square((3 << 5) | (up ? 4 : 0) | (northSouth ? 8 : 0));
    }

    private static DungeonMap map(int level, String[] rows, int sx, int sy, Square stairs) {
        DungeonMap ascii = DungeonMap.fromAscii(level, rows);
        Square[][] squares = new Square[ascii.width()][ascii.height()];
        for (int x = 0; x < ascii.width(); x++) {
            for (int y = 0; y < ascii.height(); y++) {
                squares[x][y] = x == sx && y == sy ? stairs : ascii.get(x, y);
            }
        }
        return new DungeonMap(level, squares);
    }

    /** Level 0: a corridor along y = 1 ending in east-west down stairs at (1,1); offset (0,0). */
    private static final String[] UPPER = {"#####", "#S..#", "#####"};
    /** Level 1: north-south up stairs at (2,0) with a corridor below them; offset (-1, +1) relative to level 0. */
    private static final String[] LOWER = {"##S##", "##.##", "##.##", "#####"};

    private DungeonMap upper;
    private DungeonMap lower;

    private Party party() {
        upper = map(0, UPPER, 1, 1, stairs(false, false));
        lower = map(1, LOWER, 2, 0, stairs(true, true));
        // Dungeon-wide, upper (1,1) is at (1+5, 1+3) = (6,4); lower (2,0) is at (2+4, 0+4) = (6,4).
        upper.setOffset(5, 3);
        lower.setOffset(4, 4);
        return new Party(List.of(upper, lower), 0, 3, 1, Direction.WEST);
    }

    @Test
    void stairsExitIsTheOpenNeighbourAlongTheStairsAxis() {
        party();
        DungeonMap.StairsExit down = upper.stairsExit(1, 1);
        assertNotNull(down);
        assertEquals(2, down.x());
        assertEquals(1, down.y());
        assertEquals(Direction.EAST, down.facing());
        DungeonMap.StairsExit up = lower.stairsExit(2, 0);
        assertEquals(2, up.x());
        assertEquals(1, up.y());
        assertEquals(Direction.SOUTH, up.facing());
        assertNull(upper.stairsExit(2, 1), "not stairs");
    }

    @Test
    void goingDownLandsBesideTheMatchingStairsFacingAway() {
        Party p = party();
        assertTrue(p.move(Party.Move.FORWARD)); // to (2,1)
        assertSame(upper, p.map());
        DungeonMap.StepResult r = p.step(Party.Move.FORWARD); // onto the stairs at (1,1)
        assertNotNull(r);
        assertTrue(r.levelChanged());
        assertSame(lower, p.map());
        assertEquals(1, p.level());
        assertEquals(2, p.x());
        assertEquals(1, p.y());
        assertEquals(Direction.SOUTH, p.facing());
    }

    @Test
    void comingBackUpReturnsBesideTheOriginalStairs() {
        Party p = party();
        p.move(Party.Move.FORWARD);
        p.move(Party.Move.FORWARD); // down
        p.turnRight();
        p.turnRight(); // face north, at the stairs
        DungeonMap.StepResult r = p.step(Party.Move.FORWARD);
        assertTrue(r.levelChanged());
        assertSame(upper, p.map());
        assertEquals(2, p.x());
        assertEquals(1, p.y());
        assertEquals(Direction.EAST, p.facing());
    }

    @Test
    void stairsWithoutAPartnerBlock() {
        Party p = party();
        upper.setOffset(0, 0); // the levels no longer line up
        p.move(Party.Move.FORWARD);
        assertNull(p.step(Party.Move.FORWARD));
        assertSame(upper, p.map());
        assertEquals(2, p.x());
    }

    @Test
    void aPlateOnTheArrivalSquareFires() {
        Party p = party();
        lower.addSensor(new FloorSensor(2, 1, FloorSensor.TYPE_ANY, FloorSensor.Effect.SET, false, false, true,
                2, 2, -1));
        p.move(Party.Move.FORWARD);
        DungeonMap.StepResult r = p.step(Party.Move.FORWARD);
        assertTrue(r.click(), "the audible plate under the arrival square clicked");
        assertFalse(r.doorStarted());
    }
}
