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
    void viewCellsRotateWithFacing() {
        // Facing north, view cells are DM's absolute cells: 0 NW, 1 NE, 2 SE, 3 SW.
        assertEquals(0, Direction.NORTH.cellOf(0));
        // Facing east, the far-left cell is north-east and the near-left is north-west.
        assertEquals(1, Direction.EAST.cellOf(0));
        assertEquals(0, Direction.EAST.cellOf(3));
        // Facing west, the far-right cell is north-west.
        assertEquals(0, Direction.WEST.cellOf(1));
        for (Direction d : Direction.values()) {
            for (int c = 0; c < 4; c++) {
                assertEquals(c, d.viewCellOf(d.cellOf(c)));
            }
        }
    }

    @Test
    void northIsNegativeY() {
        assertEquals(-1, Direction.NORTH.dy);
        assertEquals(1, Direction.EAST.dx);
    }
}
