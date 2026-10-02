package dm.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
    void pitsAndTeleportersAreWalkable() {
        Party p = new Party(MAP, 4, 3, Direction.EAST);
        assertTrue(p.move(Party.Move.FORWARD)); // teleporter
        assertTrue(p.move(Party.Move.LEFT));    // pit
        assertEquals(5, p.x());
        assertEquals(2, p.y());
    }

    @Test
    void stairsWithNowhereToGoBlock() {
        Party p = new Party(MAP, 2, 3, Direction.EAST); // a single map: no level below
        assertFalse(p.move(Party.Move.FORWARD));
        assertEquals(2, p.x());
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

    private static Party fullParty() {
        DungeonMap hall = hallWithMirrors(4);
        Party p = new Party(hall, 1, 1, Direction.NORTH);
        for (ChampionMirror m : hall.mirrors()) {
            p.recruit(m);
        }
        return p;
    }

    @Test
    void recruitsFillTheFormationClockwiseFromFrontLeft() {
        Party p = fullParty();
        assertSame(p.members().get(0), p.at(Party.FRONT_LEFT));
        assertSame(p.members().get(1), p.at(Party.FRONT_RIGHT));
        assertSame(p.members().get(2), p.at(Party.BACK_RIGHT));
        assertSame(p.members().get(3), p.at(Party.BACK_LEFT));
        assertEquals(Party.BACK_LEFT, p.positionOf(p.members().get(3)));
    }

    @Test
    void swappingAndMovingInTheFormation() {
        DungeonMap hall = hallWithMirrors(2);
        Party p = new Party(hall, 1, 1, Direction.NORTH);
        p.recruit(hall.mirrors().get(0));
        p.recruit(hall.mirrors().get(1));
        Champion first = p.members().get(0);
        Champion second = p.members().get(1);
        p.swap(Party.FRONT_LEFT, Party.FRONT_RIGHT);
        assertSame(second, p.at(Party.FRONT_LEFT));
        assertSame(first, p.at(Party.FRONT_RIGHT));
        p.swap(Party.FRONT_RIGHT, Party.BACK_LEFT); // into an empty position
        assertSame(first, p.at(Party.BACK_LEFT));
        assertNull(p.at(Party.FRONT_RIGHT));
        assertSame(first, p.members().get(0), "status boxes keep recruit order");
    }

    @Test
    void bumpHurtsTheSideThatHitsTheWall() {
        // members: 0 front-left, 1 front-right, 2 back-right, 3 back-left
        assertArrayEquals(new int[] {1, 1, 0, 0}, fullParty().bump(Party.Move.FORWARD));
        assertArrayEquals(new int[] {0, 0, 1, 1}, fullParty().bump(Party.Move.BACKWARD));
        assertArrayEquals(new int[] {1, 0, 0, 1}, fullParty().bump(Party.Move.LEFT));
        assertArrayEquals(new int[] {0, 1, 1, 0}, fullParty().bump(Party.Move.RIGHT));
    }

    @Test
    void bumpFollowsTheFormation() {
        Party p = fullParty();
        p.swap(Party.FRONT_LEFT, Party.BACK_LEFT); // member 0 moves to the back
        int[] damage = p.bump(Party.Move.BACKWARD);
        assertEquals(1, damage[0]);
        assertEquals(59, p.members().get(0).health());
        assertEquals(60, p.members().get(3).health(), "member 3 is now in front");
    }

    @Test
    void bumpWithNoPartyDoesNothing() {
        Party p = new Party(MAP, 1, 1, Direction.NORTH);
        assertEquals(0, p.bump(Party.Move.FORWARD).length);
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
