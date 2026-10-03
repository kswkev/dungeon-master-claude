package dm.data;

import dm.model.Direction;
import dm.model.DungeonMap;
import dm.model.FloorSensor;
import dm.model.Square;
import dm.model.WallSensor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Wall sensor words as they are in the original DUNGEON.DAT (Levels 1 and 2). */
class WallSensorFinderTest {

    /** A wall list where ordinal n maps to global ornament 100 + n. */
    private static final int[] WALL_LIST = {101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115};

    private static WallSensor decode(int word1, int word2, int word3) {
        return WallSensorFinder.decode(5, 6, Direction.NORTH, new int[] {0xFFFE, word1, word2, word3}, WALL_LIST);
    }

    @Test
    void goldKeyhole() {
        // Level 2 (0,3) north: keyhole wanting icon 184 (GOLD KEY), once only, opens the door at (1,3).
        WallSensor s = decode((184 << 7) | 4, 0xC004, 0x1840);
        assertEquals(WallSensor.TYPE_CLICK_WITH_ITEM_USED_UP, s.type());
        assertEquals(184, s.data());
        assertEquals(FloorSensor.Effect.SET, s.effect());
        assertTrue(s.onceOnly());
        assertFalse(s.local());
        assertEquals(1, s.targetX());
        assertEquals(3, s.targetY());
        assertEquals(0, s.targetCell());
        assertEquals(112, s.ornament(), "ordinal 12");
    }

    @Test
    void switchPair() {
        // Level 2 (13,19) east: a local sensor that flips the lever, and a TOGGLE one feeding the gate at (17,20).
        WallSensor lever = decode(1, 0xA800, 0x0010);
        assertTrue(lever.local());
        assertEquals(1, lever.localAction());
        assertEquals(110, lever.ornament());
        WallSensor remote = decode(1, 0xB010, 0xA440);
        assertFalse(remote.local());
        assertEquals(FloorSensor.Effect.TOGGLE, remote.effect(), "a lever pull reverses its input (#16)");
        assertEquals(17, remote.targetX());
        assertEquals(20, remote.targetY());
        assertEquals(0, remote.targetCell());
        assertEquals(111, remote.ornament());
    }

    @Test
    void gateAndTorchHolder() {
        // Level 2 (17,20) south: AND gate needing inputs 0 and 1 (data 0x30), holding (18,20) open while satisfied.
        WallSensor gate = decode((48 << 7) | 5, 0x0018, 0xA480);
        assertEquals(WallSensor.TYPE_AND_OR_GATE, gate.type());
        assertEquals(48, gate.data());
        assertEquals(FloorSensor.Effect.HOLD, gate.effect());
        assertFalse(gate.revert());
        assertEquals(18, gate.targetX());
        assertEquals(20, gate.targetY());
        assertEquals(-1, gate.ornament(), "ordinal 0: no decoration");
        // Level 1 (3,14) east: torch holder storing icon 4 (TORCH), rotating its side.
        WallSensor torch = decode((4 << 7) | 13, 0x8800, 0x0010);
        assertEquals(WallSensor.TYPE_STORAGE_ROTATE, torch.type());
        assertEquals(4, torch.data());
        assertTrue(torch.local());
        assertEquals(108, torch.ornament());
    }

    /** A 3x3 map whose centre (1,1) is a wall holding {@code things}; everything else is floor. */
    @SuppressWarnings("unchecked")
    private static List<List<Thing>>[] things(Square[][] squares, Thing... onWall) {
        List<List<Thing>>[] lists = new List[3];
        for (int x = 0; x < 3; x++) {
            lists[x] = new ArrayList<>();
            for (int y = 0; y < 3; y++) {
                squares[x][y] = new Square(x == 1 && y == 1 ? 0x10 : 0x20);
                lists[x].add(x == 1 && y == 1 ? List.of(onWall) : List.of());
            }
        }
        return lists;
    }

    @Test
    void wallSidesHoldItemsExceptAChampionMirrorsSide() {
        Square[][] squares = new Square[3][3];
        List<List<Thing>>[] lists = things(squares,
                new Thing(Thing.SENSOR, 0, Direction.NORTH.ordinal(), new int[] {0xFFFE, (5 << 7) | 127, 0, 0}),
                new Thing(Thing.WEAPON, 0, Direction.NORTH.ordinal(), new int[] {0xFFFE, 23}),  // the champion's club
                new Thing(Thing.SCROLL, 0, Direction.SOUTH.ordinal(), new int[] {0xFFFE, 0}),   // in an alcove
                new Thing(Thing.SENSOR, 1, Direction.SOUTH.ordinal(), new int[] {0xFFFE, 0, 2 << 12, 0}));
        DungeonMap map = new DungeonMap(0, squares);
        FloorItemFinder.place(map, squares, lists);
        WallSensorFinder.find(squares, lists, WALL_LIST).forEach(map::addWallSensor);

        assertTrue(map.itemsAt(1, 1, Direction.NORTH.ordinal()).isEmpty(), "the club stays with the champion");
        assertEquals("SCROLL", map.itemsAt(1, 1, Direction.SOUTH.ordinal()).get(0).name());
        assertTrue(map.wallSensors(1, 1, Direction.NORTH).isEmpty(), "mirrors aren't wall sensors");
        assertEquals(1, map.wallSensors(1, 1, Direction.SOUTH).size());
        assertEquals(102, map.wallOrnament(1, 1, Direction.SOUTH));
    }
}
