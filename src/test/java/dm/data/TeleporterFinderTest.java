package dm.data;

import dm.model.Direction;
import dm.model.Teleporter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeleporterFinderTest {

    /**
     * The layout was checked against the real file: all 175 teleporters in
     * the PC DUNGEON.DAT then lead to an open square on an existing map.
     * This record is Level 2's (31,1): to (29,4) on map 1, facing east
     * (absolute rotation 1), moving everything, audible.
     */
    @Test
    void decodesTargetRotationScopeAndMap() {
        int word1 = 29 | (4 << 5) | (1 << 10) | 0x1000 | (3 << 13) | 0x8000;
        Teleporter t = TeleporterFinder.decode(31, 1, new int[] {0xFFFE, word1, 1 << 8});
        assertEquals(1, t.targetMap());
        assertEquals(29, t.targetX());
        assertEquals(4, t.targetY());
        assertEquals(Direction.EAST, t.turn(Direction.SOUTH), "absolute");
        assertTrue(t.moves(Teleporter.SCOPE_OBJECTS));
        assertTrue(t.moves(Teleporter.SCOPE_CREATURES));
        assertTrue(t.audible());
    }

    @Test
    void relativeRotationAndObjectsOnly() {
        int word1 = 5 | (6 << 5) | (3 << 10) | (1 << 13);
        Teleporter t = TeleporterFinder.decode(0, 0, new int[] {0xFFFE, word1, 4 << 8});
        assertEquals(4, t.targetMap());
        assertEquals(Direction.EAST, t.turn(Direction.SOUTH), "three quarter-turns clockwise from south");
        assertFalse(t.moves(Teleporter.SCOPE_CREATURES));
        assertFalse(t.audible());
    }
}
