package dm.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sprint 22: DM's VI altar (F374, F255, F283) brings a dead champion back from their bones. */
class RebirthTest {

    private static final Direction S = Direction.SOUTH;

    private DungeonMap map;
    private Party party;
    private Champion elija;
    private Champion halk;

    /** Wall (1,0)'s south side shows the VI altar (global decoration 2); the party stands below it, facing north. */
    @BeforeEach
    void setUp() {
        map = DungeonMap.fromAscii(0, "#####", "#...#", "#####");
        altar(1, DungeonMap.VI_ALTAR);
        altar(3, 1); // a plain alcove
        party = new Party(map, 1, 1, Direction.NORTH);
        party.setRandom(new Random(5));
        elija = Champion.parse(ChampionTest.ELIJA, 0);
        halk = Champion.parse(ChampionTest.ELIJA, 1);
        party.recruit(new ChampionMirror(0, 0, S, elija));
        party.recruit(new ChampionMirror(0, 0, S, halk));
    }

    private void altar(int x, int ornament) {
        map.addWallSensor(new WallSensor(x, 0, S, WallSensor.TYPE_DISABLED, 0, FloorSensor.Effect.SET, false, false,
                false, false, 0, 0, 0, 0, ornament));
    }

    /** Halk dies, and the party picks up his bones. */
    private Item killHalk() {
        halk.takeDamage(10_000);
        party.bury();
        assertEquals(-1, party.positionOf(halk));
        Item bones = ItemCatalog.item(Item.Category.JUNK, Party.BONES, 1);
        assertTrue(map.removeItem(1, 1, Direction.NORTH.turnRight().ordinal(), bones)
                || removeAnywhere(bones));
        return bones;
    }

    private boolean removeAnywhere(Item bones) {
        for (int c = 0; c < 4; c++) {
            if (map.removeItem(1, 1, c, bones)) {
                return true;
            }
        }
        return false;
    }

    private void ticks(int n) {
        for (int i = 0; i < n; i++) {
            party.tick();
        }
    }

    @Test
    void bonesOnTheAltarBringTheChampionBack() {
        int max = halk.maxHealth();
        party.setHeld(killHalk());
        map.clickWall(1, 0, S, party, i -> 0);
        assertEquals(1, map.itemsAt(1, 0, S.ordinal()).size(), "the bones lie on the altar");
        party.tick();
        assertTrue(map.explosionsAt(1, 0).stream().anyMatch(e -> e.type() == Explosion.REBIRTH_1), "the sparkle");
        ticks(5);
        assertTrue(map.itemsAt(1, 0, S.ordinal()).isEmpty(), "the bones are taken");
        assertEquals(0, halk.health(), "not yet");
        party.tick();
        int newMax = Math.max(25, max - (max >> 6) - 1);
        assertEquals(newMax, halk.maxHealth());
        assertEquals(newMax >> 1, halk.health());
        assertTrue(party.positionOf(halk) >= 0, "back in the formation");
        assertEquals(party.facing(), halk.facing());
    }

    @Test
    void bonesTakenBackBeforeTheyAreTakenStopIt() {
        party.setHeld(killHalk());
        map.clickWall(1, 0, S, party, i -> 0);
        ticks(2);
        map.clickWall(1, 0, S, party, i -> 0);
        assertNotNull(party.held(), "the bones are back in hand");
        ticks(10);
        assertEquals(0, halk.health());
    }

    @Test
    void aPlainAlcoveDoesNothing() {
        party.setHeld(killHalk());
        map.clickWall(3, 0, S, party, i -> 0);
        ticks(10);
        assertEquals(0, halk.health());
        assertFalse(map.itemsAt(3, 0, S.ordinal()).isEmpty());
    }

    @Test
    void otherBonesDoNothing() {
        killHalk();
        party.setHeld(ItemCatalog.item(Item.Category.JUNK, 52));
        map.clickWall(1, 0, S, party, i -> 0);
        ticks(10);
        assertEquals(0, halk.health());
    }

    @Test
    void aRebornChampionTakesAFreeCellIfTheirsIsTaken() {
        party.setHeld(killHalk());
        Champion third = Champion.parse(ChampionTest.ELIJA, 2);
        party.recruit(new ChampionMirror(0, 0, S, third));
        map.clickWall(1, 0, S, party, i -> 0);
        ticks(8);
        assertTrue(halk.health() > 0);
        assertTrue(party.positionOf(halk) >= 0);
        assertTrue(party.positionOf(halk) != party.positionOf(third));
    }
}
