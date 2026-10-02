package dm.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DirectionTest {

    @Test
    void turnsClockwiseAndBack() {
        assertEquals(Direction.EAST, Direction.NORTH.turnRight());
        assertEquals(Direction.WEST, Direction.NORTH.turnLeft());
        assertEquals(Direction.NORTH, Direction.WEST.turnRight());
        assertEquals(Direction.SOUTH, Direction.NORTH.opposite());
        for (Direction d : Direction.values()) {
            assertEquals(d, d.turnLeft().turnRight());
            assertEquals(d, d.turnRight().turnRight().turnRight().turnRight());
        }
    }

    @Test
    void decodesDmIndices() {
        assertEquals(Direction.NORTH, Direction.fromIndex(0));
        assertEquals(Direction.EAST, Direction.fromIndex(1));
        assertEquals(Direction.SOUTH, Direction.fromIndex(2));
        assertEquals(Direction.WEST, Direction.fromIndex(3));
        assertEquals(Direction.NORTH, Direction.fromIndex(4));
    }

    @Test
    void northIsNegativeY() {
        assertEquals(-1, Direction.NORTH.dy);
        assertEquals(1, Direction.EAST.dx);
    }
}
