package dm.data;

import dm.model.CreatureType;
import dm.model.Direction;
import dm.model.Group;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupFinderTest {

    /** A 16-byte group record as words: next, possessions, type | cells << 8, 4 hit points, attributes. */
    private static int[] record(int type, int cells, int count, Direction facing) {
        int attributes = (facing.ordinal() << 8) | ((count - 1) << 5);
        return new int[] {0xFFFE, 0xFFFE, type | (cells << 8), 21, 14, 9, 0, attributes};
    }

    @Test
    void decodesTypeCellsHealthDirectionAndCount() {
        // Two mummies on cells 3 (south-west) and 2 (south-east), facing east.
        Group g = GroupFinder.decode(1, 19, record(10, 3 | (2 << 2), 2, Direction.EAST), List.of());
        assertEquals(CreatureType.MUMMY, g.type());
        assertEquals(2, g.count());
        assertEquals(Direction.EAST, g.facing());
        assertFalse(g.centred());
        assertEquals(3, g.cellOf(0));
        assertEquals(2, g.cellOf(1));
        assertEquals(1, g.creatureOn(2));
        assertEquals(21, g.health(0));
        assertEquals(14, g.health(1));
        assertEquals(1, g.x());
        assertEquals(19, g.y());
    }

    @Test
    void aLoneCreatureCanStandInTheCentre() {
        Group g = GroupFinder.decode(0, 0, record(6, 0xFF, 1, Direction.NORTH), List.of());
        assertTrue(g.centred());
        assertEquals(CreatureType.SCREAMER, g.type());
        assertEquals(-1, g.creatureOn(0));
    }

    @Test
    void possessionsComeFromTheirOwnThingList() {
        Thing torch = new Thing(Thing.WEAPON, 0, 0, new int[] {0xFFFE, 2 | (15 << 10)});
        Group g = GroupFinder.decode(0, 0, record(6, 0xFF, 1, Direction.NORTH), List.of(torch));
        assertEquals(1, g.possessions().size());
        assertEquals("TORCH", g.possessions().get(0).name());
        assertEquals(15, g.possessions().get(0).charges());
    }

    @Test
    void unknownTypesAreSkipped() {
        assertNull(GroupFinder.decode(0, 0, record(40, 0xFF, 1, Direction.NORTH), List.of()));
    }

    @Test
    void creatureTypesKnowTheirPictures() {
        // The mummy has front, side, back and attack pictures; the screamer front and attack only.
        assertEquals(0, CreatureType.MUMMY.graphicOffset(CreatureType.View.FRONT));
        assertEquals(1, CreatureType.MUMMY.graphicOffset(CreatureType.View.SIDE));
        assertEquals(2, CreatureType.MUMMY.graphicOffset(CreatureType.View.BACK));
        assertEquals(3, CreatureType.MUMMY.graphicOffset(CreatureType.View.ATTACK));
        assertEquals(-1, CreatureType.SCREAMER.graphicOffset(CreatureType.View.SIDE));
        assertEquals(1, CreatureType.SCREAMER.graphicOffset(CreatureType.View.ATTACK));
        assertEquals(CreatureType.Size.FULL, CreatureType.RED_DRAGON.size());
        assertEquals(CreatureType.Size.QUARTER, CreatureType.MUMMY.size());
        assertEquals(2, CreatureType.WIZARD_EYE.coordinateSet(), "flying");
    }
}
