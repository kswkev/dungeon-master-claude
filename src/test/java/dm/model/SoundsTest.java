package dm.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** #37: DM's F064 plays a sound loud, soft or not at all by its distance from the party. */
class SoundsTest {

    /** DM's C24 footstep (mummies, trolins...): loud under 0 squares, so never; soft under 4. */
    private static final int FOOTSTEP = 24;
    /** DM's C03 attack (rats, hellhounds): loud under 3, soft under 5. */
    private static final int ATTACK = 3;

    @Test
    void distanceIsCountedAlongBothAxes() {
        assertFalse(Sounds.hear(ATTACK, 1, 1).soft(), "2 squares: loud");
        assertTrue(Sounds.hear(ATTACK, 2, 1).soft(), "3 squares: soft");
        assertTrue(Sounds.hear(ATTACK, -4, 0).soft(), "4 squares: soft");
        assertNull(Sounds.hear(ATTACK, 3, -2), "5 squares: not heard");
    }

    @Test
    void footstepsAreOnlyEverSoftAndShortRange() {
        assertTrue(Sounds.hear(FOOTSTEP, 0, 1).soft());
        assertTrue(Sounds.hear(FOOTSTEP, 3, 0).soft());
        assertNull(Sounds.hear(FOOTSTEP, 4, 0));
        assertNull(Sounds.hear(FOOTSTEP, 12, 0), "the old 12-square box let this through");
    }

    private static Party party() {
        DungeonMap map = DungeonMap.fromAscii(0, "##########", "#........#", "##########");
        return new Party(List.of(map), 0, 1, 1, Direction.EAST);
    }

    private static List<Sounds.Heard> heard(Party p, int dmSound, DungeonMap m, int x, int y) {
        p.dungeon().creatures().soundAt(p, dmSound, m, x, y);
        return p.tick().sounds();
    }

    @Test
    void creaturesAreHeardByTheirDistance() {
        Party p = party();
        assertEquals(List.of(new Sounds.Heard(ATTACK, false)), heard(p, ATTACK, p.map(), 2, 1));
        assertEquals(List.of(new Sounds.Heard(ATTACK, true)), heard(p, ATTACK, p.map(), 5, 1));
        assertEquals(List.of(), heard(p, ATTACK, p.map(), 7, 1));
    }

    @Test
    void anotherMapIsSilent() {
        Party p = party();
        DungeonMap elsewhere = DungeonMap.fromAscii(1, "###", "#.#", "###");
        assertEquals(List.of(), heard(p, ATTACK, elsewhere, 1, 1));
    }

    /** The rattle (C02) is loud under 3 squares and soft under 6. */
    private static DungeonMap.DoorTick openDoorFrom(int partyX) {
        DungeonMap map = DungeonMap.fromAscii(0, "##########", "#.......D.", "##########");
        new Party(List.of(map), 0, partyX, 1, Direction.EAST);
        map.moveDoor(8, 1, true);
        return map.tickDoors();
    }

    @Test
    void aDoorFarAwayRattlesSoftlyOrNotAtAll() {
        assertFalse(openDoorFrom(7).rattle().soft(), "next to it");
        assertTrue(openDoorFrom(4).rattle().soft(), "4 squares away");
        DungeonMap.DoorTick far = openDoorFrom(1);
        assertTrue(far.moved());
        assertFalse(far.rattled(), "7 squares away");
    }
}
