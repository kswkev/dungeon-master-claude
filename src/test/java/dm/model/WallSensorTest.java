package dm.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wall sensors on a small map: walls along y = 0 face south onto a corridor
 * at y = 1 with a closed door at (3,1) and an open pit at (5,1).
 */
class WallSensorTest {

    private static final Item GOLD_KEY = ItemCatalog.item(Item.Category.JUNK, 17);
    private static final Item IRON_KEY = ItemCatalog.item(Item.Category.JUNK, 9);
    private static final Item TORCH = ItemCatalog.item(Item.Category.WEAPON, 2);
    private static final Item SCROLL = ItemCatalog.item(Item.Category.SCROLL, 0);
    /** Inventory icon numbers as in GRAPHICS.DAT's name list. */
    private static final ToIntFunction<Item> ICONS = i -> switch (i.name()) {
        case "GOLD KEY" -> 184;
        case "IRON KEY" -> 176;
        case "TORCH" -> 4;
        case "COPPER COIN" -> 125;
        default -> 0;
    };
    private static final Direction S = Direction.SOUTH;
    private static final FloorSensor.Effect SET = FloorSensor.Effect.SET;
    private static final FloorSensor.Effect CLEAR = FloorSensor.Effect.CLEAR;
    private static final FloorSensor.Effect TOGGLE = FloorSensor.Effect.TOGGLE;
    private static final FloorSensor.Effect HOLD = FloorSensor.Effect.HOLD;

    private DungeonMap map;
    private Party party;

    @BeforeEach
    void setUp() {
        map = DungeonMap.fromAscii(0, "#######", "#..D.O#", "#######");
        party = new Party(map, 1, 1, Direction.NORTH);
    }

    /** A remote sensor on wall (x, 0)'s south side sending {@code effect} to (tx, ty) cell {@code cell}. */
    private WallSensor remote(int x, int type, int data, FloorSensor.Effect effect, boolean audible, boolean once,
                              int tx, int ty, int cell, int ornament) {
        WallSensor s = new WallSensor(x, 0, S, type, data, effect, once, false, audible, false, 0, tx, ty, cell,
                ornament);
        map.addWallSensor(s);
        return s;
    }

    /** A local sensor that rotates its side when it fires. */
    private WallSensor local(int x, int type, int data, int ornament) {
        WallSensor s = new WallSensor(x, 0, S, type, data, SET, false, false, false, true, 1, 0, 0, 0, ornament);
        map.addWallSensor(s);
        return s;
    }

    private DungeonMap.WallClick click(int x) {
        return map.clickWall(x, 0, S, party, ICONS);
    }

    private boolean doorHeadingOpen() {
        while (map.tickDoors().moved()) {
            // let the door finish moving
        }
        return map.isPassable(3, 1);
    }

    @Test
    void aSwitchTogglesTheDoorAndFlipsItsLever() {
        local(1, WallSensor.TYPE_CLICK, 0, 10);
        remote(1, WallSensor.TYPE_CLICK, 0, TOGGLE, true, false, 3, 1, 0, 11);
        assertEquals(11, map.wallOrnament(1, 0, S), "the last sensor's decoration shows");

        DungeonMap.WallClick c = click(1);
        assertTrue(c.fired());
        assertTrue(c.sound());
        assertTrue(c.doorStarted());
        assertEquals(10, map.wallOrnament(1, 0, S), "lever flipped");
        assertTrue(doorHeadingOpen());

        click(1);
        assertEquals(11, map.wallOrnament(1, 0, S), "and back");
        assertFalse(doorHeadingOpen());
    }

    @Test
    void aKeyholeWantsTheRightKeyAndKeepsIt() {
        remote(1, WallSensor.TYPE_CLICK_WITH_ITEM_USED_UP, 184, TOGGLE, false, false, 3, 1, 0, 5);
        assertFalse(click(1).fired(), "empty hand");
        party.setHeld(IRON_KEY);
        assertFalse(click(1).fired());
        assertSame(IRON_KEY, party.held(), "the wrong key stays in hand");

        party.setHeld(GOLD_KEY);
        DungeonMap.WallClick c = click(1);
        assertTrue(c.fired());
        assertTrue(c.handChanged());
        assertNull(party.held(), "the key is used up");
        assertTrue(doorHeadingOpen());
    }

    @Test
    void lockMasterOpensLocksWithoutTheirKey() {
        party.setLockMaster(true);
        remote(1, WallSensor.TYPE_CLICK_WITH_ITEM_USED_UP, 184, TOGGLE, false, false, 3, 1, 0, 5);
        assertTrue(click(1).fired(), "an empty hand opens the gold lock");
        assertTrue(doorHeadingOpen());
        party.setHeld(SCROLL);
        DungeonMap.WallClick c = click(1);
        assertTrue(c.fired(), "so does anything else");
        assertFalse(c.handChanged());
        assertSame(SCROLL, party.held(), "which isn't used up");
        party.setHeld(GOLD_KEY);
        click(1);
        assertNull(party.held(), "the right key still goes in, as it would anyway");
    }

    @Test
    void lockMasterFeedsCoinSlotsButNothingElse() {
        party.setLockMaster(true);
        remote(1, WallSensor.TYPE_CLICK_WITH_ITEM_USED_UP, 125, TOGGLE, false, false, 3, 1, 0, 6);
        assertTrue(click(1).fired(), "a coin slot without a coin");
        remote(2, WallSensor.TYPE_CLICK_WITH_ITEM, 4, SET, false, false, 3, 1, 0, -1);
        assertFalse(click(2).fired(), "a sensor that wants a torch still wants it");
        party.setLockMaster(false);
        assertFalse(click(1).fired(), "and with lock master off the slot wants its coin again");
    }

    @Test
    void itemSensorsKeepTheItemAndAnyItemSensorsTakeAnything() {
        remote(1, WallSensor.TYPE_CLICK_WITH_ITEM, 176, SET, false, false, 3, 1, 0, -1);
        party.setHeld(IRON_KEY);
        assertTrue(click(1).fired());
        assertSame(IRON_KEY, party.held(), "kept");

        remote(2, WallSensor.TYPE_CLICK_WITH_ANY_ITEM, 0, CLEAR, false, false, 3, 1, 0, -1);
        party.setHeld(null);
        assertFalse(click(2).fired());
        party.setHeld(SCROLL);
        assertTrue(click(2).fired());
    }

    @Test
    void aTorchHolderGivesAndTakesItsTorch() {
        local(1, WallSensor.TYPE_DISABLED, 0, 7);   // the empty holder
        local(1, WallSensor.TYPE_STORAGE_ROTATE, 4, 8); // with its torch
        map.addItem(1, 0, S.ordinal(), TORCH);
        assertEquals(8, map.wallOrnament(1, 0, S));

        assertTrue(click(1).handChanged());
        assertSame(TORCH, party.held());
        assertTrue(map.itemsAt(1, 0, S.ordinal()).isEmpty());
        assertEquals(7, map.wallOrnament(1, 0, S), "now empty");

        party.setHeld(SCROLL);
        assertFalse(click(1).fired(), "only a torch fits");
        party.setHeld(TORCH);
        click(1);
        assertNull(party.held());
        assertEquals(List.of(TORCH), map.itemsAt(1, 0, S.ordinal()));
        assertEquals(8, map.wallOrnament(1, 0, S));
    }

    // ---- fountains ----

    /** A fountain (decoration 35) on wall (4,0), with a recruited Elija who has drunk nothing for a while. */
    private Champion fountainAndThirstyChampion() {
        local(4, WallSensor.TYPE_DISABLED, 0, DungeonMap.FOUNTAIN);
        Champion c = Champion.parse(ChampionTest.ELIJA, 0);
        party.recruit(new ChampionMirror(1, 0, S, c));
        c.setWater(-300);
        return c;
    }

    @Test
    void anEmptyHandDrinksItsFillFromAFountain() {
        Champion c = fountainAndThirstyChampion();
        DungeonMap.WallClick result = click(4);
        assertTrue(result.drank());
        assertEquals(Champion.MAX_FOOD, c.water());
    }

    @Test
    void aFountainRefillsWaterskinsAndFlasks() {
        Champion c = fountainAndThirstyChampion();
        party.setHeld(ItemCatalog.item(Item.Category.JUNK, ItemCatalog.WATERSKIN, 1));
        DungeonMap.WallClick result = click(4);
        assertTrue(result.handChanged());
        assertFalse(result.drank(), "filling isn't drinking");
        assertEquals(3, party.held().charges());
        assertEquals("WATER", party.held().name());
        assertEquals(-300, c.water());

        party.setHeld(ItemCatalog.item(Item.Category.POTION, ItemCatalog.EMPTY_FLASK));
        click(4);
        assertEquals("WATER FLASK", party.held().name());
    }

    @Test
    void aFountainLeavesOtherItemsAlone() {
        fountainAndThirstyChampion();
        party.setHeld(GOLD_KEY);
        assertEquals(DungeonMap.WallClick.NOTHING, click(4));
        assertSame(GOLD_KEY, party.held());
    }

    @Test
    void anAlcoveTakesAndGivesItems() {
        local(2, WallSensor.TYPE_DISABLED, 0, 2); // alcove decoration
        map.addItem(2, 0, S.ordinal(), SCROLL);
        DungeonMap.WallClick c = click(2);
        assertTrue(c.handChanged());
        assertFalse(c.fired());
        assertSame(SCROLL, party.held());

        party.setHeld(GOLD_KEY);
        click(2);
        assertNull(party.held());
        assertEquals(List.of(GOLD_KEY), map.itemsAt(2, 0, S.ordinal()));

        assertEquals(DungeonMap.WallClick.NOTHING, click(4), "a plain wall does nothing");
    }

    @Test
    void anAndGateOpensItsDoorOnlyWithBothInputsAndRevertsWhenOneGoesOff() {
        // Gate on wall (6,0): inputs 0 and 1 must both be on (data 0x30); HOLD: the door at (3,1) is open only while they are.
        WallSensor gate = new WallSensor(6, 0, S, WallSensor.TYPE_AND_OR_GATE, 0x30, HOLD, false, false, true, false,
                0, 3, 1, 0, -1);
        map.addWallSensor(gate);
        remote(1, WallSensor.TYPE_CLICK, 0, TOGGLE, true, false, 6, 0, 0, -1);
        remote(2, WallSensor.TYPE_CLICK, 0, TOGGLE, true, false, 6, 0, 1, -1);

        click(1);
        assertFalse(doorHeadingOpen(), "one input isn't enough");
        click(2);
        assertTrue(doorHeadingOpen(), "both on");
        click(1);
        assertFalse(doorHeadingOpen(), "HOLD closes it again");
    }

    @Test
    void onceOnlySensorsFireOnce() {
        remote(1, WallSensor.TYPE_CLICK, 0, TOGGLE, false, true, 3, 1, 0, -1);
        assertTrue(click(1).fired());
        assertFalse(click(1).fired());
        assertTrue(doorHeadingOpen(), "toggled just once");
    }

    @Test
    void sensorsOpenAndClosePits() {
        assertTrue(map.isPitOpen(5, 1));
        remote(1, WallSensor.TYPE_CLICK, 0, CLEAR, false, false, 5, 1, 0, -1);
        click(1);
        assertFalse(map.isPitOpen(5, 1));
        assertFalse(map.isPitOpen(4, 1), "not a pit");
    }

    @Test
    void aFloorPlateCanOpenAPit() {
        DungeonMap m = DungeonMap.fromAscii(0, "#####", "#..O#", "#####");
        m.addWallSensor(new WallSensor(1, 0, S, WallSensor.TYPE_CLICK, 0, CLEAR, false, false, false, false, 0,
                3, 1, 0, -1));
        m.clickWall(1, 0, S, new Party(m, 1, 1, Direction.NORTH), ICONS);
        assertFalse(m.isPitOpen(3, 1));
        m.addSensor(new FloorSensor(2, 1, FloorSensor.TYPE_ANY, SET, false, false, false, 3, 1, -1));
        Party p = new Party(m, 1, 1, Direction.EAST);
        p.step(Party.Move.FORWARD);
        assertTrue(m.isPitOpen(3, 1));
    }

    /** Issue #14, as on Level 2 (6,4) north: [disabled alcove 1, local toggle button 49] with a falchion. */
    @Test
    void aRevealedAlcoveStaysAndHandsOverItsItem() {
        local(2, WallSensor.TYPE_DISABLED, 0, 1);
        WallSensor button = new WallSensor(2, 0, S, WallSensor.TYPE_CLICK, 0, TOGGLE, false, false, true, true, 1,
                0, 0, 0, 49);
        map.addWallSensor(button);
        Item falchion = ItemCatalog.item(Item.Category.WEAPON, 9);
        map.addItem(2, 0, S.ordinal(), falchion);
        assertEquals(49, map.wallOrnament(2, 0, S), "the button shows");

        assertTrue(click(2).fired());
        assertEquals(1, map.wallOrnament(2, 0, S), "the alcove is revealed");
        assertNull(party.held(), "the button click doesn't reach into the alcove");

        DungeonMap.WallClick c = click(2);
        assertFalse(c.fired(), "the button doesn't fire through the alcove");
        assertSame(falchion, party.held());
        assertEquals(1, map.wallOrnament(2, 0, S), "and the alcove stays");

        click(2);
        assertNull(party.held());
        assertEquals(List.of(falchion), map.itemsAt(2, 0, S.ordinal()));
        assertEquals(1, map.wallOrnament(2, 0, S), "putting it back keeps the alcove");
    }

    /** A lever as in DM's data (#16): a local rotating sensor (45) and a remote TOGGLE sensor (44). */
    private void lever(int x, int tx, int ty, int cell) {
        local(x, WallSensor.TYPE_CLICK, 0, 45);
        remote(x, WallSensor.TYPE_CLICK, 0, TOGGLE, true, false, tx, ty, cell, 44);
    }

    @Test
    void aLeverOpensAndClosesItsDoorInTurn() {
        lever(1, 3, 1, 0);
        assertEquals(44, map.wallOrnament(1, 0, S));
        click(1);
        assertTrue(doorHeadingOpen());
        assertEquals(45, map.wallOrnament(1, 0, S));
        click(1);
        assertFalse(doorHeadingOpen(), "the second pull closes it");
        assertEquals(44, map.wallOrnament(1, 0, S));
        click(1);
        assertTrue(doorHeadingOpen(), "and the third opens it again");
    }

    @Test
    void aLeverClosesAndReopensAnOpenPit() {
        // As on Level 2: (6,8) north -> the pit at (7,8), which starts open.
        lever(1, 5, 1, 1);
        click(1);
        assertFalse(map.isPitOpen(5, 1), "the first pull closes it");
        click(1);
        assertTrue(map.isPitOpen(5, 1), "the next opens it again");
        click(1);
        assertFalse(map.isPitOpen(5, 1));
    }

    @Test
    void twoSetLeversFeedAnAndGate() {
        WallSensor gate = new WallSensor(6, 0, S, WallSensor.TYPE_AND_OR_GATE, 0x30, HOLD, false, false, true, false,
                0, 3, 1, 0, -1);
        map.addWallSensor(gate);
        lever(1, 6, 0, 0);
        lever(2, 6, 0, 1);

        click(1);
        assertFalse(doorHeadingOpen(), "one lever isn't enough");
        click(2);
        assertTrue(doorHeadingOpen(), "both levers on");
        click(1);
        assertFalse(doorHeadingOpen(), "flipping one back clears its input");
        click(1);
        assertTrue(doorHeadingOpen(), "and on again");
    }

    @Test
    void anAndGateFedBySetSwitchesWithoutLevers() {
        WallSensor gate = new WallSensor(6, 0, S, WallSensor.TYPE_AND_OR_GATE, 0x30, HOLD, false, false, true, false,
                0, 3, 1, 0, -1);
        map.addWallSensor(gate);
        remote(1, WallSensor.TYPE_CLICK, 0, SET, true, false, 6, 0, 0, -1);
        remote(2, WallSensor.TYPE_CLICK, 0, SET, true, false, 6, 0, 1, -1);
        click(1);
        click(1);
        assertFalse(doorHeadingOpen(), "a plain button keeps setting the same input");
        click(2);
        assertTrue(doorHeadingOpen());
    }

    @Test
    void doorButtonsToggleTheirDoor() {
        assertTrue(map.pressDoorButton(3, 1));
        assertTrue(doorHeadingOpen());
        assertFalse(map.pressDoorButton(2, 1), "not a door");
    }
}
