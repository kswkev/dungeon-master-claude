package dm.model;

import org.junit.jupiter.api.Test;

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
