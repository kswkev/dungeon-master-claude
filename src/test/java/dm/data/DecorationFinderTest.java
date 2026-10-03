package dm.data;

import dm.model.Decorations;
import dm.model.Direction;
import dm.model.Square;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecorationFinderTest {

    private static final int SEED = 99; // the original DUNGEON.DAT's ornament seed

    @Test
    void randomIndexMatchesHandComputation() {
        // v1 = 2033, v2 = 3037: 2033*31417 = 63870761 -> & 0xFFFF = 38697 -> >>1 = 19348;
        // + 3037*11 + 99 = 52854 -> >>2 = 13213 -> % 30 = 13
        assertEquals(13, DecorationFinder.randomIndex(2033, 3037, SEED, 30));
    }

    @Test
    void randomIndexWrapsAt16Bits() {
        // 6000*11 = 66000 wraps to 464 -> >>2 = 116 -> % 30 = 26
        assertEquals(26, DecorationFinder.randomIndex(0, 6000, 0, 30));
    }

    @Test
    void randomOrdinalNeedsPermissionAndAnIndexBelowTheCount() {
        assertEquals(0, DecorationFinder.randomOrdinal(false, 15, 1, 1, SEED, 0, 18, 19));
        // (1,1) on an 18x19 map 0 hashes to 13 (see above): ordinal 14 when 15 are random, none when 4 are.
        assertEquals(14, DecorationFinder.randomOrdinal(true, 15, 1, 1, SEED, 0, 18, 19));
        assertEquals(0, DecorationFinder.randomOrdinal(true, 4, 1, 1, SEED, 0, 18, 19));
    }

    /** One wall square at (1,1) of a 3x3 map, with the given square byte and things. */
    private static Decorations decorate(int squareByte, OrnamentLists lists, Thing... things) {
        Square[][] squares = new Square[3][3];
        @SuppressWarnings("unchecked")
        List<List<Thing>>[] thingLists = new List[3];
        for (int x = 0; x < 3; x++) {
            thingLists[x] = new ArrayList<>();
            for (int y = 0; y < 3; y++) {
                squares[x][y] = new Square(x == 1 && y == 1 ? squareByte : 0x20);
                thingLists[x].add(x == 1 && y == 1 ? List.of(things) : List.of());
            }
        }
        return new DecorationFinder(lists, SEED, 0, TextDecoderTest.encode("HELLO\nWORLD"))
                .find(squares, thingLists);
    }

    private static OrnamentLists allRandom(int... wall) {
        return new OrnamentLists(new int[0], wall, wall.length, new int[0], 0, new int[0]);
    }

    @Test
    void randomWallDecorationsOnlyOnPermittedSides() {
        // 15 random wall decorations, so most hashes land on one; allow only the east side (bit 2).
        int[] wall = {10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24};
        Decorations d = decorate(0x04, allRandom(wall));
        assertEquals(-1, d.wall(1, 1, Direction.NORTH));
        assertEquals(-1, d.wall(1, 1, Direction.SOUTH));
        assertEquals(-1, d.wall(1, 1, Direction.WEST));
        int expected = DecorationFinder.randomOrdinal(true, 15, 1, (1 + 1) * (Direction.EAST.ordinal() + 1),
                SEED, 0, 3, 3);
        assertEquals(expected == 0 ? -1 : wall[expected - 1], d.wall(1, 1, Direction.EAST));
    }

    @Test
    void wallSensorOrnamentBeatsRandomAndTextBecomesAnInscription() {
        int[] wall = {0, 7, 9}; // ordinal 1 is the inscription stone
        OrnamentLists lists = new OrnamentLists(new int[0], wall, 0, new int[0], 0, new int[0]);
        Thing sensor = new Thing(Thing.SENSOR, 0, Direction.SOUTH.ordinal(), new int[] {0xFFFE, 0, 3 << 12, 0});
        Thing text = new Thing(Thing.TEXT, 0, Direction.WEST.ordinal(), new int[] {0xFFFE, 1});
        Decorations d = decorate(0x0F, lists, sensor, text);
        assertEquals(9, d.wall(1, 1, Direction.SOUTH), "sensor ordinal 3 -> global 9");
        assertEquals(0, d.wall(1, 1, Direction.WEST), "the inscription stone");
        assertEquals("HELLO\nWORLD", d.inscription(1, 1, Direction.WEST));
        assertNull(d.inscription(1, 1, Direction.SOUTH));
    }

    @Test
    void mirrorSideCarriesTheMirrorDecoration() {
        int[] wall = {10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 43, 21, 22, 23, 24};
        Thing mirror = new Thing(Thing.SENSOR, 0, Direction.NORTH.ordinal(), new int[] {0xFFFE, 127, 0xB080, 0});
        Decorations d = decorate(0x0F, allRandom(wall), mirror);
        assertEquals(43, d.wall(1, 1, Direction.NORTH), "sensor ordinal 11 -> global 43");
    }

    @Test
    void mirrorWithoutAnOrdinalFallsBackToTheMirrorFrame() {
        Thing mirror = new Thing(Thing.SENSOR, 0, Direction.EAST.ordinal(), new int[] {0xFFFE, 127, 0x0080, 0});
        Decorations d = decorate(0x00, allRandom(), mirror);
        assertEquals(DecorationFinder.MIRROR_ORNAMENT, d.wall(1, 1, Direction.EAST));
        assertEquals(43, DecorationFinder.MIRROR_ORNAMENT);
    }

    @Test
    void doorRecordGivesDecorationAndButton() {
        OrnamentLists lists = new OrnamentLists(new int[0], new int[0], 0, new int[0], 0, new int[] {7, 3});
        Square[][] squares = {{new Square(0x94)}};
        @SuppressWarnings("unchecked")
        List<List<Thing>>[] things = new List[] {List.of(List.of(
                new Thing(Thing.DOOR, 0, 0, new int[] {0xFFFE, (2 << 1) | 0x40})))};
        Decorations d = new DecorationFinder(lists, SEED, 0, new int[0]).find(squares, things);
        assertEquals(3, d.door(0, 0), "ordinal 2 -> global 3");
        assertTrue(d.doorButton(0, 0));

        Square[][] plain = {{new Square(0x94)}};
        @SuppressWarnings("unchecked")
        List<List<Thing>>[] plainThings = new List[] {List.of(List.of(
                new Thing(Thing.DOOR, 0, 0, new int[] {0xFFFE, 0x0020})))};
        Decorations p = new DecorationFinder(lists, SEED, 0, new int[0]).find(plain, plainThings);
        assertEquals(-1, p.door(0, 0));
        assertFalse(p.doorButton(0, 0));
    }

    @Test
    void readsOrnamentListsAfterTheSquares() throws Exception {
        // 1 creature type, 2 wall (1 random), 3 floor (2 random), 1 door ornament
        byte[] data = {9, 4, 33, 2, 8, 6, 7};
        OrnamentLists lists = OrnamentLists.read(data, 0, data.length, 0x2312, 0x0011);
        assertEquals(List.of(4, 33), List.of(lists.wall()[0], lists.wall()[1]));
        assertEquals(1, lists.randomWall());
        assertEquals(3, lists.floor().length);
        assertEquals(6, lists.floor()[2]);
        assertEquals(2, lists.randomFloor());
        assertEquals(7, lists.door()[0]);
        assertEquals(0, lists.inscriptionOrdinal(), "no inscription stone in this list");
    }
}
