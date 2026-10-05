package dm.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sprint 22: the Firestaff's FUSE on Lord Chaos (DM's F225), and fluxcages around him (F224). */
class FuseTest {

    private DungeonMap map;
    private Party party;
    private Group chaos;

    /** A corridor (1,1)-(3,1) between walls: the party on (1,1) faces Lord Chaos on (2,1). */
    @BeforeEach
    void setUp() {
        map = DungeonMap.fromAscii(0, "#####", "#...#", "#####");
        party = new Party(map, 1, 1, Direction.EAST);
        party.setRandom(new Random(11));
        Champion elija = Champion.parse(ChampionTest.ELIJA, 0);
        party.recruit(new ChampionMirror(0, 0, Direction.SOUTH, elija));
        elija.replace(Slot.ACTION_HAND, ItemCatalog.item(Item.Category.WEAPON, 45)); // the complete Firestaff
        chaos = new Group(CreatureType.LORD_CHAOS, 2, 1, Group.CENTRED, new int[] {1000, 0, 0, 0}, 1,
                Direction.WEST, List.of());
        map.addGroup(chaos);
    }

    private void fuse() {
        party.act(0, Actions.FUSE);
    }

    @Test
    void caughtBetweenFluxcagesAndWallsHeIsFused() {
        Flight.fluxcage(party, map, 1, 1);
        Flight.fluxcage(party, map, 3, 1);
        fuse();
        assertNotNull(party.endgame());
        assertEquals(2, party.endgame().x());
        assertSame(chaos, map.groupAt(2, 1));
    }

    @Test
    void withAnOpenSideHeEscapes() {
        Flight.fluxcage(party, map, 1, 1);
        fuse();
        assertNull(party.endgame());
        assertSame(chaos, map.groupAt(3, 1), "he steps out the open side");
        assertNull(map.groupAt(2, 1));
    }

    @Test
    void fuseElsewhereIsJustABlast() {
        map.removeGroup(chaos);
        fuse();
        assertNull(party.endgame());
        assertTrue(map.explosionsAt(2, 1).stream().anyMatch(e -> e.type() == Explosion.HARM_NON_MATERIAL
                && e.attack() == 255));
    }
}
