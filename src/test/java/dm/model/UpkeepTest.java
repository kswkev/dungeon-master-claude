package dm.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DM's upkeep rules (ReDMCSB F331, F349, F366), checked against values worked
 * out by hand from that code for Elija: stamina 580 (shown as 58), mana 22,
 * wisdom 42, vitality 36, strength 42, priest level 4, wizard level 1.
 */
class UpkeepTest {

    private Champion elija;

    @BeforeEach
    void setUp() {
        elija = Champion.parse(ChampionTest.ELIJA, 0);
        elija.setFood(1600);
        elija.setWater(1600);
    }

    @Test
    void aRestedFedChampionGetsALittleHungrierAndThirstier() {
        assertEquals(0, Upkeep.applyTimeEffects(elija, 64, 0, false));
        assertEquals(1598, elija.food());
        assertEquals(1599, elija.water());
        assertEquals(580, elija.rawStamina(), "already full");
    }

    @Test
    void lowStaminaComesBackFasterAndCostsMoreFood() {
        elija.decrementStamina(480); // down to 100: below an eighth, so 8 cycles
        Upkeep.applyTimeEffects(elija, 64, 0, false);
        assertEquals(116, elija.rawStamina());
        assertEquals(1580, elija.food());
        assertEquals(1591, elija.water());
    }

    @Test
    void manaComesBackWhenTheTimePatternIsBelowWisdomPlusMagicLevels() {
        elija.setMana(10);
        Upkeep.applyTimeEffects(elija, 64, 0, false); // pattern 64: not below 42 + 5
        assertEquals(10, elija.mana());
        Upkeep.applyTimeEffects(elija, 128, 0, false); // pattern 32: 1 mana for 11 stamina, then rest refills it
        assertEquals(11, elija.mana());
        assertEquals(580, elija.rawStamina());
        assertEquals(1598 - 6, elija.food(), "resting longer than 80 ticks: three cycles");
        assertEquals(1599 - 3, elija.water());
    }

    @Test
    void starvingWithNoStaminaHurts() {
        elija.setFood(-600);
        elija.setWater(-600);
        elija.decrementStamina(579); // 1 left
        assertEquals(3, Upkeep.applyTimeEffects(elija, 64, 0, false), "8 stamina owed, half of the 7 missing as damage");
        assertEquals(0, elija.rawStamina());
        assertEquals(-608, elija.food());
        assertEquals(-604, elija.water());
    }

    @Test
    void healthComesBackWhileStaminaIsAboveAQuarter() {
        elija.takeDamage(10);
        Upkeep.applyTimeEffects(elija, 128, 0, false); // pattern 32 < vitality 36 + 12
        assertEquals(51, elija.health(), "60 / 128 + 1");
    }

    @Test
    void sleepersRecoverTwiceAsFast() {
        elija.decrementStamina(480);
        elija.takeDamage(10);
        elija.setStat(Champion.Stat.STRENGTH, 40);
        Upkeep.applyTimeEffects(elija, 64, 0, true);
        assertEquals(132, elija.rawStamina(), "twice the 16 an awake champion gets");
        assertEquals(1580, elija.food(), "eating as much as awake");
        assertEquals(41, elija.stat(Champion.Stat.STRENGTH), "statistics every 64 ticks, not 256");
        Upkeep.applyTimeEffects(elija, 128, 0, true);
        assertEquals(52, elija.health(), "(60 / 128 + 1) doubled");
    }

    @Test
    void foodAndWaterBottomOutAt1024Below() {
        elija.setFood(-5000);
        assertEquals(Champion.MIN_FOOD, elija.food());
        elija.setWater(5000);
        assertEquals(Champion.MAX_FOOD, elija.water());
    }

    @Test
    void aStepCostsOneStaminaUnlessHeavilyLaden() {
        assertEquals(440, elija.maxLoad(), "42 * 8 + 100, rounded up to a whole kilogram");
        assertEquals(1, Upkeep.stepCost(0, 440));
        assertEquals(2, Upkeep.stepCost(150, 440));
    }

    @Test
    void thePartyClockRunsUpkeepEvery64Ticks() {
        DungeonMap m = DungeonMap.fromAscii(0, "#####", "#...#", "#####");
        Party p = new Party(List.of(m), 0, 1, 1, Direction.EAST);
        p.setRandom(new Random(1));
        ChampionMirror mirror = new ChampionMirror(1, 0, Direction.SOUTH, elija);
        p.recruit(mirror);
        int food = elija.food();
        assertTrue(food >= 1500 && food < 1756, "DM's 1500 + random(256)");
        for (int i = 0; i < 63; i++) {
            assertFalse(p.tick().changed());
        }
        assertTrue(p.tick().changed());
        assertEquals(food - 2, elija.food());
    }

    @Test
    void everyMoveAttemptTiresTheParty() {
        DungeonMap m = DungeonMap.fromAscii(0, "#####", "#...#", "#####");
        Party p = new Party(List.of(m), 0, 1, 1, Direction.EAST);
        p.recruit(new ChampionMirror(1, 0, Direction.SOUTH, elija));
        p.step(Party.Move.FORWARD);
        assertEquals(579, elija.rawStamina());
        assertNull(p.step(Party.Move.LEFT), "into the wall");
        assertEquals(578, elija.rawStamina(), "a bump costs stamina too");
    }

    // ---- eating and drinking ------------------------------------------------

    private static final Item APPLE = ItemCatalog.item(Item.Category.JUNK, 29);
    private static final Item SWORD = ItemCatalog.item(Item.Category.WEAPON, 10);

    @Test
    void eatingAnAppleUsesItUp() {
        assertNull(Upkeep.consume(elija, APPLE));
        assertEquals(2048, elija.food(), "1600 + 500, but no more than 2048");
    }

    @Test
    void aWaterskinLosesADraughtAndIsCalledWaterWhileItHoldsSome() {
        Item full = ItemCatalog.item(Item.Category.JUNK, ItemCatalog.WATERSKIN, 3);
        assertEquals("WATER", full.name());
        elija.setWater(0);
        Item left = Upkeep.consume(elija, full);
        assertEquals(800, elija.water());
        assertEquals(2, left.charges());
        Item empty = Upkeep.consume(elija, Upkeep.consume(elija, left));
        assertEquals("WATERSKIN", empty.name());
        assertEquals(0, empty.charges());
        assertEquals(empty, Upkeep.consume(elija, empty), "nothing left to drink");
    }

    @Test
    void potionsLeaveAnEmptyFlask() {
        elija.setWater(0);
        Item left = Upkeep.consume(elija, ItemCatalog.item(Item.Category.POTION, ItemCatalog.WATER_FLASK));
        assertEquals("EMPTY FLASK", left.name());
        assertEquals(1600, elija.water());
        Upkeep.consume(elija, ItemCatalog.item(Item.Category.POTION, 7, 70)); // KU, power 70
        assertEquals(42 + 7, elija.stat(Champion.Stat.STRENGTH));
    }

    @Test
    void aSwordCantBeEaten() {
        assertEquals(SWORD, Upkeep.consume(elija, SWORD));
        assertEquals(1600, elija.food());
    }

    @Test
    void thePartyFeedsTheHeldItem() {
        DungeonMap m = DungeonMap.fromAscii(0, "#####", "#...#", "#####");
        Party p = new Party(List.of(m), 0, 1, 1, Direction.EAST);
        p.recruit(new ChampionMirror(1, 0, Direction.SOUTH, elija));
        p.setHeld(SWORD);
        assertFalse(p.feed(elija));
        p.setHeld(APPLE);
        assertTrue(p.feed(elija));
        assertNull(p.held());
    }

    @Test
    void weightsFollowDm() {
        assertEquals(32, SWORD.weight());
        assertEquals(4, APPLE.weight());
        assertEquals(3 + 6, ItemCatalog.item(Item.Category.JUNK, ItemCatalog.WATERSKIN, 3).weight());
        assertEquals(1, ItemCatalog.item(Item.Category.POTION, ItemCatalog.EMPTY_FLASK).weight());
    }
}
