package dm.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquareTest {

    /** Raw bytes from the original Level 1: a door in a north-south corridor, one in an east-west one. */
    private static final Square DOOR_NS = new Square(0x9C);
    private static final Square DOOR_EW = new Square(0x94);

    @Test
    void doorOrientationDecidesWhenThePanelIsSeen() {
        assertTrue(DOOR_NS.runsNorthSouth());
        assertTrue(DOOR_NS.facesAlong(Direction.NORTH));
        assertTrue(DOOR_NS.facesAlong(Direction.SOUTH));
        assertFalse(DOOR_NS.facesAlong(Direction.EAST));
        assertFalse(DOOR_EW.runsNorthSouth());
        assertTrue(DOOR_EW.facesAlong(Direction.WEST));
        assertFalse(DOOR_EW.facesAlong(Direction.NORTH));
    }

    @Test
    void decodesDoorStairsAndPitBits() {
        assertEquals(SquareType.DOOR, DOOR_NS.type());
        assertEquals(4, DOOR_NS.doorState());
        assertFalse(DOOR_NS.isDoorOpen());
        Square stairsUpNs = new Square(0x6C);
        assertEquals(SquareType.STAIRS, stairsUpNs.type());
        assertTrue(stairsUpNs.stairsUp());
        assertTrue(stairsUpNs.runsNorthSouth());
        assertFalse(new Square(0x60).stairsUp());
        assertTrue(new Square(0x48).pitOpen());
        assertFalse(new Square(0x40).pitOpen());
    }
}
