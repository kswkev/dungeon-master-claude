package dm.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pits on a corridor along y = 1. Each level below is offset by (+1, 0), so
 * square (x, y) on one level lies over (x - 1, y) on the next.
 */
class PitTest {

    private static final Item SWORD = ItemCatalog.item(Item.Category.WEAPON, 10);
    private static final int OPEN_PIT = (2 << 5) | 8;
    private static final int CLOSED_PIT = 2 << 5;
    private static final int IMAGINARY_PIT = (2 << 5) | 8 | 4;
    private static final int INVISIBLE_PIT = (2 << 5) | 8 | 1;

    /** A level with a corridor from x = 1 to 5 and the given square bytes at (x, 1). */
    private static DungeonMap level(int level, int... squaresAt) {
        DungeonMap ascii = DungeonMap.fromAscii(level, "#######", "#.....#", "#######");
        Square[][] squares = new Square[ascii.width()][ascii.height()];
        for (int x = 0; x < ascii.width(); x++) {
            for (int y = 0; y < ascii.height(); y++) {
                squares[x][y] = ascii.get(x, y);
            }
        }
        for (int i = 0; i + 1 < squaresAt.length; i += 2) {
            squares[squaresAt[i]][1] = new Square(squaresAt[i + 1]);
        }
        DungeonMap map = new DungeonMap(level, squares);
        map.setOffset(level, 0);
        return map;
    }

    private static Party party(List<DungeonMap> maps, int x, Direction facing) {
        Party p = new Party(maps, 0, x, 1, facing);
        p.setRandom(new Random(1));
        p.recruit(new ChampionMirror(0, 0, Direction.SOUTH, Champion.parse(ChampionTest.ELIJA, 0)));
        p.recruit(new ChampionMirror(0, 0, Direction.SOUTH, Champion.parse(ChampionTest.ELIJA, 1)));
        return p;
    }

    @Test
    void walkingIntoAnOpenPitDropsThePartyToTheSameSpotBelow() {
        DungeonMap upper = level(0, 3, OPEN_PIT);
        DungeonMap lower = level(1);
        Party p = party(List.of(upper, lower), 2, Direction.EAST);
        int health = p.members().get(0).health();

        DungeonMap.StepResult r = p.step(Party.Move.FORWARD);
        assertTrue(r.fell());
        assertTrue(r.levelChanged());
        assertSame(lower, p.map());
        assertEquals(2, p.x(), "upper (3,1) lies over lower (2,1)");
        assertEquals(1, p.y());
        assertEquals(Direction.EAST, p.facing(), "the party keeps its facing");

        assertNotNull(r.damage());
        for (int i = 0; i < 2; i++) {
            int d = r.damage()[i];
            assertTrue(d >= 10 && d <= 19, "DM's fall damage is 10-19, got " + d);
        }
        assertEquals(health - r.damage()[0], p.members().get(0).health());
    }

    @Test
    void closedAndImaginaryPitsHoldThePartyUp() {
        DungeonMap upper = level(0, 3, CLOSED_PIT, 4, IMAGINARY_PIT);
        Party p = party(List.of(upper, level(1)), 2, Direction.EAST);
        assertFalse(p.step(Party.Move.FORWARD).fell());
        assertFalse(p.step(Party.Move.FORWARD).fell());
        assertSame(upper, p.map());
        assertEquals(4, p.x());
    }

    @Test
    void invisiblePitsStillDrop() {
        DungeonMap upper = level(0, 3, INVISIBLE_PIT);
        Party p = party(List.of(upper, level(1)), 2, Direction.EAST);
        assertTrue(p.step(Party.Move.FORWARD).fell());
    }

    @Test
    void thePartyKeepsFallingThroughPitsBelow() {
        DungeonMap top = level(0, 3, OPEN_PIT);
        DungeonMap middle = level(1, 2, OPEN_PIT);
        DungeonMap bottom = level(2);
        Party p = party(List.of(top, middle, bottom), 2, Direction.EAST);
        DungeonMap.StepResult r = p.step(Party.Move.FORWARD);
        assertSame(bottom, p.map());
        assertEquals(1, p.x());
        assertTrue(r.damage()[0] >= 20, "hurt by both falls");
    }

    @Test
    void withNoLevelBelowNothingHappens() {
        DungeonMap only = level(0, 3, OPEN_PIT);
        Party p = party(List.of(only), 2, Direction.EAST);
        DungeonMap.StepResult r = p.step(Party.Move.FORWARD);
        assertFalse(r.fell());
        assertSame(only, p.map());
        assertEquals(3, p.x());
    }

    @Test
    void aPitOpenedUnderThePartyDropsIt() {
        DungeonMap upper = level(0, 3, CLOSED_PIT);
        DungeonMap lower = level(1);
        Party p = party(List.of(upper, lower), 3, Direction.NORTH);
        upper.addWallSensor(new WallSensor(3, 0, Direction.SOUTH, WallSensor.TYPE_CLICK, 0, FloorSensor.Effect.SET,
                false, false, false, false, 0, 3, 1, 0, -1));
        assertFalse(p.settle().fell(), "closed");
        upper.clickWall(3, 0, Direction.SOUTH, p, i -> 0);
        DungeonMap.StepResult r = p.settle();
        assertTrue(r.fell());
        assertSame(lower, p.map());
        assertEquals(2, p.x());
    }

    @Test
    void droppedAndThrownItemsFallToo() {
        DungeonMap upper = level(0, 3, OPEN_PIT, 5, OPEN_PIT);
        DungeonMap lower = level(1);
        Party p = party(List.of(upper, lower), 1, Direction.EAST);

        upper.dropItem(3, 1, 2, SWORD);
        assertTrue(upper.itemsAt(3, 1, 2).isEmpty());
        assertEquals(List.of(SWORD), lower.itemsAt(2, 1, 2), "same cell, one level down");

        Flight.launch(p, SWORD, upper, 1, 1, 2, Direction.EAST, 200, 100, 5);
        for (int i = 0; i < 12; i++) {
            p.tick();
        }
        assertTrue(upper.projectiles().isEmpty());
        assertFalse(upper.hasItems(3, 1), "it flew over the first pit");
        assertFalse(upper.hasItems(5, 1));
        assertTrue(lower.hasItems(4, 1), "and fell through the second, where it hit the wall");
    }

    @Test
    void aPlateBelowFeelsTheFallingItem() {
        DungeonMap upper = level(0, 3, OPEN_PIT);
        DungeonMap lower = level(1);
        lower.addSensor(new FloorSensor(2, 1, FloorSensor.TYPE_ANY, FloorSensor.Effect.SET, false, false, true,
                0, 0, -1));
        party(List.of(upper, lower), 1, Direction.EAST);
        assertTrue(upper.dropItem(3, 1, 0, SWORD).click());
    }
}
