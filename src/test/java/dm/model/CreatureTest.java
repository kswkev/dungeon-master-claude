package dm.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Creatures this sprint: they stand in the way, stop thrown items, and face a party they can see. */
class CreatureTest {

    private static final Item SWORD = ItemCatalog.item(Item.Category.WEAPON, 10);

    private DungeonMap map;
    private Party party;
    private Group mummies;

    /** A corridor along y = 1 from x = 1 to 6, with two mummies at (5,1) facing north. */
    @BeforeEach
    void setUp() {
        map = DungeonMap.fromAscii(0, "########", "#......#", "########");
        mummies = new Group(CreatureType.MUMMY, 5, 1, 3 | (2 << 2), new int[] {20, 20, 0, 0}, 2, Direction.NORTH,
                List.of());
        map.addGroup(mummies);
        party = new Party(List.of(map), 0, 3, 1, Direction.EAST);
    }

    @Test
    void creaturesBlockThePartyWithoutABump() {
        assertTrue(party.move(Party.Move.FORWARD));
        assertNull(party.step(Party.Move.FORWARD), "the mummies stand in the way");
        assertTrue(party.blockedByCreatures());
        assertEquals(4, party.x());
        assertNull(party.step(Party.Move.LEFT), "a wall");
        assertFalse(party.blockedByCreatures());
    }

    @Test
    void thrownItemsStopInFrontOfCreatures() {
        map.throwItem(SWORD, 3, 1, Direction.EAST, false, 4);
        for (int i = 0; i < 5; i++) {
            map.tickProjectiles();
        }
        assertTrue(map.hasItems(4, 1), "it lands short of the mummies");
        assertFalse(map.hasItems(5, 1));
    }

    @Test
    void groupsTurnToFaceAPartyTheyCanSee() {
        assertTrue(map.faceParty(party.x(), party.y()));
        assertEquals(Direction.WEST, mummies.facing());
        assertFalse(map.faceParty(party.x(), party.y()), "already facing it");
    }

    @Test
    void groupsDontSeeThroughWallsOrFarAway() {
        DungeonMap walled = DungeonMap.fromAscii(0, "#########", "#...#...#", "#########");
        Group g = new Group(CreatureType.SCREAMER, 6, 1, Group.CENTRED, new int[] {50, 0, 0, 0}, 1, Direction.NORTH,
                List.of());
        walled.addGroup(g);
        assertFalse(walled.faceParty(2, 1), "a wall between");
        DungeonMap open = DungeonMap.fromAscii(0, "########", "#......#", "########");
        open.addGroup(g);
        assertFalse(open.faceParty(1, 1), "five squares is too far");
        assertTrue(open.faceParty(3, 1));
    }

    @Test
    void theTickTurnsThem() {
        party.tick();
        assertEquals(Direction.WEST, mummies.facing());
    }

    @Test
    void theMapListsCreatures() {
        assertEquals(mummies, map.groupAt(5, 1));
        assertNull(map.groupAt(4, 1));
        assertTrue(map.toAscii(party).contains("M"));
    }
}
