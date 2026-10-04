package dm.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pressure plates opening doors, on a corridor like the Hall exit: door at (1,1), plate at (2,1). */
class FloorSensorTest {

    private static final String ELIJA = "ELIJA\nLION OF YAITOPYA\n\nM\nAADMACEEAABG\nDCCKCICKCEDFCI\nBBCAAAAACBECAAAA";

    private DungeonMap map;
    private Party party;
    private ChampionMirror mirror;

    @BeforeEach
    void setUp() {
        DungeonMap ascii = DungeonMap.fromAscii(0, "######", "#D...#", "######");
        Square[][] squares = new Square[6][3];
        for (int x = 0; x < 6; x++) {
            for (int y = 0; y < 3; y++) {
                squares[x][y] = ascii.get(x, y);
            }
        }
        mirror = new ChampionMirror(3, 0, Direction.SOUTH, Champion.parse(ELIJA, 0));
        map = new DungeonMap(0, squares, List.of(mirror));
        party = new Party(map, 3, 1, Direction.WEST);
    }

    private FloorSensor plate(int type, FloorSensor.Effect effect, boolean once, boolean revert) {
        FloorSensor s = new FloorSensor(2, 1, type, effect, once, revert, true, 1, 1, 1);
        map.addSensor(s);
        return s;
    }

    private void ticks(int n) {
        for (int i = 0; i < n; i++) {
            map.tickDoors();
        }
    }

    @Test
    void partyPlateNeedsAChampion() {
        plate(FloorSensor.TYPE_PARTY, FloorSensor.Effect.SET, false, false);
        assertTrue(party.move(Party.Move.FORWARD));
        ticks(4);
        assertEquals(DungeonMap.DOOR_CLOSED, map.doorState(1, 1), "an empty party can't press a party plate");

        party.move(Party.Move.BACKWARD);
        party.recruit(mirror);
        DungeonMap.StepResult r = party.step(Party.Move.FORWARD);
        assertTrue(r.doorStarted());
        assertTrue(r.click());
        assertFalse(map.isPassable(1, 1), "still closed until it has moved");
    }

    @Test
    void doorSlidesOpenOneStepPerTick() {
        plate(FloorSensor.TYPE_PARTY, FloorSensor.Effect.SET, false, false);
        party.recruit(mirror);
        party.move(Party.Move.FORWARD);
        for (int expected = 3; expected >= 0; expected--) {
            DungeonMap.DoorTick tick = map.tickDoors();
            assertTrue(tick.moved());
            assertEquals(expected, map.doorState(1, 1));
            assertEquals(expected != 0, tick.rattled(), "rattles on every step but the last");
        }
        assertFalse(map.tickDoors().moved(), "nothing left to move");
        assertTrue(map.isPassable(1, 1));
        assertTrue(party.move(Party.Move.FORWARD), "party can walk through");
    }

    @Test
    void closingRattlesThreeTimesToo() {
        map.moveDoor(1, 1, true);
        ticks(4);
        map.moveDoor(1, 1, false);
        int rattles = 0;
        DungeonMap.DoorTick tick;
        while ((tick = map.tickDoors()).moved()) {
            rattles += tick.rattled() ? 1 : 0;
        }
        assertEquals(3, rattles);
        assertEquals(DungeonMap.DOOR_CLOSED, map.doorState(1, 1));
    }

    @Test
    void anyTypePlateWorksWithoutChampions() {
        plate(FloorSensor.TYPE_ANY, FloorSensor.Effect.SET, false, false);
        party.move(Party.Move.FORWARD);
        ticks(4);
        assertTrue(map.isPassable(1, 1));
    }

    @Test
    void aRevertPlateFiresWhenThePartyStepsOff() {
        plate(FloorSensor.TYPE_ANY, FloorSensor.Effect.SET, false, true);
        party.move(Party.Move.FORWARD);
        ticks(4);
        assertFalse(map.isPassable(1, 1), "stepping on does nothing");
        party.move(Party.Move.BACKWARD);
        ticks(4);
        assertTrue(map.isPassable(1, 1), "stepping off opens it");
    }

    /** As on Level 2 (25,3) -> pit (24,5): HOLD + revert keeps the pit shut while something is on the plate. */
    @Test
    void aHoldRevertPlateClosesAPitWhilePressed() {
        DungeonMap m = DungeonMap.fromAscii(0, "######", "#..O.#", "######");
        m.addSensor(new FloorSensor(1, 1, FloorSensor.TYPE_ANY, FloorSensor.Effect.HOLD, false, true, false,
                3, 1, -1));
        assertTrue(m.isPitOpen(3, 1));
        m.dropItem(1, 1, 0, SWORD);
        assertFalse(m.isPitOpen(3, 1), "the item holds it shut");
        m.pickUpItem(1, 1, 0);
        assertTrue(m.isPitOpen(3, 1));
    }

    @Test
    void holdKeepsTheDoorOpenOnlyWhileStanding() {
        plate(FloorSensor.TYPE_ANY, FloorSensor.Effect.HOLD, false, false);
        party.move(Party.Move.FORWARD);
        ticks(2);
        party.move(Party.Move.BACKWARD); // leaves while the door is still opening
        ticks(4);
        assertEquals(DungeonMap.DOOR_CLOSED, map.doorState(1, 1));
    }

    @Test
    void toggleAndOnceOnly() {
        FloorSensor s = plate(FloorSensor.TYPE_ANY, FloorSensor.Effect.TOGGLE, true, false);
        party.move(Party.Move.FORWARD);
        ticks(4);
        assertTrue(map.isPassable(1, 1));
        assertFalse(s.enabled(), "once-only plate switches off");
        party.move(Party.Move.BACKWARD);
        party.move(Party.Move.FORWARD);
        ticks(4);
        assertTrue(map.isPassable(1, 1), "a used once-only plate does nothing");
    }

    @Test
    void clearPlateClosesAnOpenDoor() {
        map.moveDoor(1, 1, true);
        ticks(4);
        plate(FloorSensor.TYPE_ANY, FloorSensor.Effect.CLEAR, false, false);
        party.move(Party.Move.FORWARD);
        ticks(4);
        assertFalse(map.isPassable(1, 1));
    }

    private static final Item SWORD = ItemCatalog.item(Item.Category.WEAPON, 10);

    /** Issue #17, as on Level 2 (25,1) -> door (27,0): an "anything" HOLD plate. */
    @Test
    void anItemOnThePlateHoldsTheDoorOpen() {
        plate(FloorSensor.TYPE_ANY, FloorSensor.Effect.HOLD, false, false);
        DungeonMap.StepResult r = map.dropItem(2, 1, 0, SWORD);
        assertTrue(r.doorStarted());
        assertTrue(r.click());
        ticks(4);
        assertTrue(map.isPassable(1, 1), "dropping an item opens the door");

        DungeonMap.Pickup p = map.pickUpItem(2, 1, 0);
        assertSame(SWORD, p.item());
        assertTrue(p.result().doorStarted());
        ticks(4);
        assertEquals(DungeonMap.DOOR_CLOSED, map.doorState(1, 1), "picking it up closes it");
    }

    @Test
    void theDoorStaysOpenWhileAnItemIsLeftBehind() {
        plate(FloorSensor.TYPE_ANY, FloorSensor.Effect.HOLD, false, false);
        party.move(Party.Move.FORWARD); // onto the plate
        map.dropItem(2, 1, 0, SWORD);
        party.move(Party.Move.BACKWARD);
        ticks(4);
        assertTrue(map.isPassable(1, 1), "the sword still presses the plate");

        party.move(Party.Move.FORWARD);
        map.pickUpItem(2, 1, 0);
        ticks(4);
        assertTrue(map.isPassable(1, 1), "the party still stands on it");
        party.move(Party.Move.BACKWARD);
        ticks(4);
        assertEquals(DungeonMap.DOOR_CLOSED, map.doorState(1, 1), "empty at last");
    }

    @Test
    void aThrownItemLandingOnThePlateOpensTheDoor() {
        plate(FloorSensor.TYPE_ANY, FloorSensor.Effect.HOLD, false, false);
        Flight.launch(party, SWORD, map, 3, 1, Direction.WEST.ordinal(), Direction.WEST, 7, 50, 5);
        party.tick(); // onto the plate's square
        party.tick();
        assertTrue(party.tick().click(), "spent, it drops on the plate");
        assertFalse(map.itemsAt(2, 1, Direction.WEST.cellOf(0)).isEmpty());
        ticks(4);
        assertTrue(map.isPassable(1, 1));
    }

    @Test
    void partyPlatesIgnoreItems() {
        plate(FloorSensor.TYPE_PARTY, FloorSensor.Effect.SET, false, true);
        assertFalse(map.dropItem(2, 1, 0, SWORD).doorStarted());
        ticks(4);
        assertEquals(DungeonMap.DOOR_CLOSED, map.doorState(1, 1));
    }

    @Test
    void itemsAlreadyOnAPlateAtTheStartDontFireIt() {
        map.addItem(2, 1, 0, SWORD);
        plate(FloorSensor.TYPE_ANY, FloorSensor.Effect.TOGGLE, false, false);
        map.initSensors();
        assertFalse(map.dropItem(2, 1, 1, SWORD).doorStarted(), "already pressed");
        party.move(Party.Move.FORWARD);
        ticks(4);
        assertEquals(DungeonMap.DOOR_CLOSED, map.doorState(1, 1), "still pressed when the party arrives");
    }

    @Test
    void plateShowsItsOrnament() {
        plate(FloorSensor.TYPE_PARTY, FloorSensor.Effect.SET, false, false);
        assertEquals(1, map.floorOrnament(2, 1));
        assertEquals(-1, map.floorOrnament(3, 1));
    }
}
