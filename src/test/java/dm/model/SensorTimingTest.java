package dm.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sprint 25: sensor delays (F272), countdowns (F248) and launchers (F247), and floor sensors 4 and 8 (F276). */
class SensorTimingTest {

    private static final FloorSensor.Effect SET = FloorSensor.Effect.SET;
    private static final FloorSensor.Effect CLEAR = FloorSensor.Effect.CLEAR;
    private static final Item ARROW = ItemCatalog.item(Item.Category.WEAPON, 27);
    private static final Item SWORD = ItemCatalog.item(Item.Category.WEAPON, 10);

    private DungeonMap map;
    private Party party;

    @BeforeEach
    void setUp() {
        map = DungeonMap.fromAscii(0,
                "#####",
                "#...#",
                "#.O.#",
                "#...#",
                "#####");
        party = new Party(map, 1, 3, Direction.NORTH);
        party.setRandom(new Random(1));
        party.dungeon().setIconOf(item -> item.name().equals("ARROW") ? 51 : item.name().equals("SWORD") ? 37 : -1);
    }

    /** A click sensor on wall (0,1)'s east side, sending {@code effect} to (tx, ty) cell {@code cell}. */
    private WallSensor button(FloorSensor.Effect effect, int tx, int ty, int cell, int delay) {
        WallSensor s = new WallSensor(0, 1, Direction.EAST, WallSensor.TYPE_CLICK, 0, effect, false, false, false,
                false, 0, tx, ty, cell, -1);
        s.setTiming(delay, 0);
        map.addWallSensor(s);
        return s;
    }

    private void click() {
        map.clickWall(0, 1, Direction.EAST, party, i -> 0);
    }

    private void tick(int ticks) {
        for (int i = 0; i < ticks; i++) {
            party.tick();
        }
    }

    @Test
    void anEffectArrivesAfterItsSensorsDelay() {
        button(CLEAR, 2, 2, 0, 3);
        click();
        assertTrue(map.isPitOpen(2, 2), "not yet");
        tick(2);
        assertTrue(map.isPitOpen(2, 2));
        tick(1);
        assertFalse(map.isPitOpen(2, 2), "3 ticks on");
    }

    @Test
    void anEffectWithNoDelayArrivesAtOnce() {
        button(CLEAR, 2, 2, 0, 0);
        click();
        assertFalse(map.isPitOpen(2, 2));
    }

    @Test
    void aCountdownFiresWhenItReachesZero() {
        button(CLEAR, 4, 2, 0, 0); // counts the countdown on the east wall down
        WallSensor countdown = new WallSensor(4, 2, Direction.WEST, WallSensor.TYPE_COUNTDOWN, 2, CLEAR, false, false,
                false, false, 0, 2, 2, 0, -1);
        map.addWallSensor(countdown);
        click();
        assertTrue(map.isPitOpen(2, 2), "2 to 1");
        click();
        assertFalse(map.isPitOpen(2, 2), "1 to 0: it fires");
        assertEquals(0, countdown.count());
    }

    @Test
    void aSetCountsACountdownUpAndZeroStaysZero() {
        WallSensor countdown = new WallSensor(4, 2, Direction.WEST, WallSensor.TYPE_COUNTDOWN, 1, CLEAR, false, false,
                false, false, 0, 2, 2, 0, -1);
        assertTrue(countdown.countDown(SET));
        assertEquals(2, countdown.count());
        countdown.countDown(CLEAR);
        countdown.countDown(FloorSensor.Effect.TOGGLE);
        assertEquals(0, countdown.count());
        assertFalse(countdown.countDown(SET), "spent");
    }

    /** A launcher on wall (4,1)'s west side, shooting west into (3,1). */
    private WallSensor launcher(int type, int data) {
        WallSensor s = new WallSensor(4, 1, Direction.WEST, type, data, SET, false, false, false, true, 0, 0, 0, 0, -1);
        s.setTiming(0, 50);
        map.addWallSensor(s);
        button(SET, 4, 1, Direction.WEST.ordinal(), 0);
        return s;
    }

    @Test
    void anObjectLauncherShootsANewObjectOutOfTheWall() {
        launcher(WallSensor.TYPE_LAUNCHER_OBJECT, 51); // an arrow
        click();
        List<Projectile> flying = map.projectiles();
        assertEquals(1, flying.size());
        Projectile p = flying.get(0);
        assertEquals(ARROW.name(), p.item().name());
        assertEquals(3, p.x);
        assertEquals(1, p.y);
        assertSame(Direction.WEST, p.direction);
        assertTrue(p.cell == 1 || p.cell == 2, "a cell on the wall's side: NE or SE");
        assertEquals(50, p.kineticEnergy);
        assertEquals(0, p.stepEnergy, "DM 1.x's launched things never tire");
        assertFalse(p.ignoreImpacts, "they can hit at once");
    }

    @Test
    void aDoubleSpellLauncherShootsTwoSideBySide() {
        launcher(WallSensor.TYPE_LAUNCHER_SPELL_DOUBLE, Explosion.FIREBALL);
        click();
        List<Projectile> flying = map.projectiles();
        assertEquals(2, flying.size());
        assertTrue(flying.get(0).isSpell() && flying.get(1).isSpell());
        assertEquals(Explosion.FIREBALL, flying.get(0).spell());
        assertEquals(Set2.of(1, 2), Set2.of(flying.get(0).cell, flying.get(1).cell));
    }

    @Test
    void aWallObjectLauncherShootsWhatLiesOnItsSide() {
        launcher(WallSensor.TYPE_LAUNCHER_WALL_OBJECT, 0);
        map.addItem(4, 1, Direction.WEST.ordinal(), SWORD);
        click();
        assertEquals(SWORD, map.projectiles().get(0).item());
        assertTrue(map.itemsAt(4, 1, Direction.WEST.ordinal()).isEmpty());
        click();
        assertEquals(1, map.projectiles().size(), "nothing left to shoot");
    }

    @Test
    void aLauncherOnlyAnswersEffectsNamingItsSide() {
        WallSensor s = new WallSensor(4, 1, Direction.WEST, WallSensor.TYPE_LAUNCHER_OBJECT, 51, SET, true, false,
                false, true, 0, 0, 0, 0, -1);
        s.setTiming(0, 50);
        map.addWallSensor(s);
        button(SET, 4, 1, Direction.NORTH.ordinal(), 0);
        click();
        assertTrue(map.projectiles().isEmpty());
    }

    @Test
    void aOnceOnlyLauncherShootsOnce() {
        WallSensor s = new WallSensor(4, 1, Direction.WEST, WallSensor.TYPE_LAUNCHER_OBJECT, 51, SET, true, false,
                false, true, 0, 0, 0, 0, -1);
        s.setTiming(0, 50);
        map.addWallSensor(s);
        button(SET, 4, 1, Direction.WEST.ordinal(), 0);
        click();
        click();
        assertEquals(1, map.projectiles().size());
    }

    @Test
    void launchersMakeOnlyDmsObjects() {
        assertEquals("ROCK", DungeonMap.launcherObject(54).name());
        assertEquals("TORCH", DungeonMap.launcherObject(6).name());
        assertEquals(0, DungeonMap.launcherObject(6).charges(), "BUG0_65: no charges");
        assertNull(DungeonMap.launcherObject(184), "a gold key isn't one");
    }

    @Test
    void anObjectPlateIsPressedOnlyByItsObject() {
        FloorSensor plate = new FloorSensor(1, 1, FloorSensor.TYPE_OBJECT, CLEAR, false, false, false, 2, 2, -1,
                51, 0, 0);
        map.addSensor(plate);
        map.dropItem(1, 1, 0, SWORD);
        assertTrue(map.isPitOpen(2, 2), "not a sword");
        map.dropItem(1, 1, 1, ARROW);
        assertFalse(map.isPitOpen(2, 2), "the arrow presses it");
    }

    @Test
    void aPossessionPlateGoesOffComingAndGoingWithTheObject() {
        FloorSensor plate = new FloorSensor(1, 2, FloorSensor.TYPE_PARTY_POSSESSION, FloorSensor.Effect.TOGGLE, false,
                false, false, 2, 2, -1, 51, 0, 0);
        map.addSensor(plate);
        party.step(Party.Move.FORWARD); // onto (1,2) carrying nothing
        assertTrue(map.isPitOpen(2, 2));
        party.step(Party.Move.BACKWARD);
        party.setHeld(ARROW);
        party.step(Party.Move.FORWARD); // arriving with the arrow
        assertFalse(map.isPitOpen(2, 2));
        party.step(Party.Move.BACKWARD); // and leaving with it
        assertTrue(map.isPitOpen(2, 2));
    }

    /** Two cells as an unordered pair. */
    private record Set2(int low, int high) {
        static Set2 of(int a, int b) {
            return new Set2(Math.min(a, b), Math.max(a, b));
        }
    }
}
