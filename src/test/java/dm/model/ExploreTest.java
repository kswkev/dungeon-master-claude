package dm.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the party has seen, for the map (not in DM). */
class ExploreTest {

    @Test
    void theViewIsSeenUpToItsFirstWallAndNotBeyond() {
        DungeonMap map = DungeonMap.fromAscii(0,
                "#######",
                "#.....#",
                "#.###.#",
                "#.....#",
                "#######");
        Party p = new Party(List.of(map), 0, 1, 3, Direction.NORTH);
        assertFalse(map.explored(), "nothing seen before the party looks");
        p.explore();
        assertTrue(map.seen(1, 3) && map.seen(1, 2) && map.seen(1, 1), "the party's column");
        assertTrue(map.seen(1, 0), "and the wall ending it");
        assertTrue(map.seen(2, 1), "beside an open square of the column");
        assertTrue(map.seen(2, 3) && map.seen(2, 2), "the column beside, to its first wall");
        assertFalse(map.seen(3, 1), "round the corner");
        assertFalse(map.seen(5, 3), "behind the party's back to the side");
        p.turnRight(); // turning looks down the bottom corridor
        assertTrue(map.seen(4, 3));
        assertFalse(map.seen(5, 3), "4 squares away");
    }

    @Test
    void aClosedWoodenDoorHidesWhatIsBehindItButAPortcullisDoesNot() {
        DungeonMap map = DungeonMap.fromAscii(0,
                "###",
                "#.#",
                "#D#",
                "#.#",
                "###");
        map.setDoorStyle(1, 2, 1); // wood
        Party p = new Party(List.of(map), 0, 1, 3, Direction.NORTH);
        p.explore();
        assertTrue(map.seen(1, 2), "the door itself");
        assertFalse(map.seen(1, 1), "behind it");
        map.setDoorStyle(1, 2, 0); // portcullis
        p.explore();
        assertTrue(map.seen(1, 1));
    }

    @Test
    void stepsAndTicksExploreAndOnlyTheMapWalkedOn() {
        DungeonMap top = DungeonMap.fromAscii(0, "###", "#.#", "#.#", "###");
        DungeonMap bottom = DungeonMap.fromAscii(1, "###", "#.#", "#.#", "###");
        Party p = new Party(List.of(top, bottom), 0, 1, 2, Direction.NORTH);
        p.tick();
        assertTrue(top.seen(1, 1));
        assertFalse(bottom.explored());
    }
}
