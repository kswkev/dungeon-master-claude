package dm.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyTest {

    private static final DungeonMap MAP = DungeonMap.fromAscii(0,
            "#######",
            "#..D..#",
            "#.#d#O#",
            "#..S.T#",
            "#######");

    @Test
    void walksForwardAndBack() {
        Party p = new Party(MAP, 1, 3, Direction.NORTH);
        assertTrue(p.move(Party.Move.FORWARD));
        assertEquals(1, p.x());
        assertEquals(2, p.y());
        assertTrue(p.move(Party.Move.BACKWARD));
        assertEquals(3, p.y());
        assertEquals(Direction.NORTH, p.facing());
    }

    @Test
    void strafesRelativeToFacing() {
        Party p = new Party(MAP, 1, 3, Direction.EAST);
        // Facing east, strafing left goes north.
        assertTrue(p.move(Party.Move.LEFT));
        assertEquals(1, p.x());
        assertEquals(2, p.y());
        assertTrue(p.move(Party.Move.RIGHT));
        assertEquals(3, p.y());
        assertEquals(Direction.EAST, p.facing());
    }

    @Test
    void wallsBlock() {
        Party p = new Party(MAP, 1, 1, Direction.NORTH);
        assertFalse(p.move(Party.Move.FORWARD));
        assertFalse(p.move(Party.Move.LEFT));
        assertEquals(1, p.x());
        assertEquals(1, p.y());
    }

    @Test
    void closedDoorsBlockOpenDoorsDoNot() {
        Party p = new Party(MAP, 2, 1, Direction.EAST);
        assertFalse(p.move(Party.Move.FORWARD), "closed door at (3,1)");
        Party q = new Party(MAP, 3, 3, Direction.NORTH);
        assertTrue(q.move(Party.Move.FORWARD), "open door at (3,2)");
    }

    @Test
    void pitsStairsAndTeleportersAreWalkable() {
        Party p = new Party(MAP, 2, 3, Direction.EAST);
        assertTrue(p.move(Party.Move.FORWARD)); // stairs
        assertTrue(p.move(Party.Move.FORWARD));
        assertTrue(p.move(Party.Move.FORWARD)); // teleporter
        assertTrue(p.move(Party.Move.LEFT));    // pit
        assertEquals(5, p.x());
        assertEquals(2, p.y());
    }

    @Test
    void edgeOfMapBlocks() {
        DungeonMap open = DungeonMap.fromAscii(0, "..", "..");
        Party p = new Party(open, 0, 0, Direction.NORTH);
        assertFalse(p.move(Party.Move.FORWARD));
        assertFalse(p.move(Party.Move.LEFT));
    }

    private static DungeonMap hallWithMirrors(int count) {
        // A corridor along y=1 with mirrors on the walls above it (y=0), facing south.
        List<ChampionMirror> mirrors = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            mirrors.add(new ChampionMirror(i + 1, 0, Direction.SOUTH, Champion.parse(ChampionTest.ELIJA, i)));
        }
        DungeonMap ascii = DungeonMap.fromAscii(0, "########", "#......#", "########");
        Square[][] squares = new Square[ascii.width()][ascii.height()];
        for (int x = 0; x < ascii.width(); x++) {
            for (int y = 0; y < ascii.height(); y++) {
                squares[x][y] = ascii.get(x, y);
            }
        }
        return new DungeonMap(0, squares, mirrors);
    }

    @Test
    void findsTheMirrorStraightAhead() {
        DungeonMap hall = hallWithMirrors(2);
        Party p = new Party(hall, 1, 1, Direction.NORTH);
        assertEquals(hall.mirrors().get(0), p.facingMirror());
        p.turnRight();
        assertEquals(null, p.facingMirror(), "facing along the corridor");
        Party q = new Party(hall, 1, 1, Direction.SOUTH);
        assertEquals(null, q.facingMirror(), "mirror is behind");
    }

    @Test
    void recruitsUpToFourAndEmptiesMirrors() {
        DungeonMap hall = hallWithMirrors(5);
        Party p = new Party(hall, 1, 1, Direction.NORTH);
        for (int i = 0; i < 4; i++) {
            assertTrue(p.recruit(hall.mirrors().get(i)));
            assertTrue(hall.mirrors().get(i).taken());
        }
        assertTrue(p.isFull());
        assertFalse(p.recruit(hall.mirrors().get(4)), "party of 4 is full");
        assertFalse(hall.mirrors().get(4).taken());
        assertEquals(4, p.members().size());
        assertEquals(null, p.facingMirror(), "taken mirror shows empty");
    }

    @Test
    void cannotRecruitTheSameChampionTwice() {
        DungeonMap hall = hallWithMirrors(1);
        Party p = new Party(hall, 1, 1, Direction.NORTH);
        assertTrue(p.recruit(hall.mirrors().get(0)));
        assertFalse(p.recruit(hall.mirrors().get(0)));
        assertEquals(1, p.members().size());
    }

    @Test
    void turningChangesOnlyFacing() {
        Party p = new Party(MAP, 1, 1, Direction.NORTH);
        p.turnRight();
        assertEquals(Direction.EAST, p.facing());
        p.turnLeft();
        p.turnLeft();
        assertEquals(Direction.WEST, p.facing());
        assertEquals(1, p.x());
        assertEquals(1, p.y());
    }
}
