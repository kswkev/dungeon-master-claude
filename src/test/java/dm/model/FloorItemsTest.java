package dm.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloorItemsTest {

    private static final Item APPLE = ItemCatalog.item(Item.Category.JUNK, 29);
    private static final Item SWORD = ItemCatalog.item(Item.Category.WEAPON, 10);

    /** A map from ASCII rows (top row is y = 0, so north is up); see {@link DungeonMap#fromAscii}. */
    private static DungeonMap corridor(String... rows) {
        return DungeonMap.fromAscii(0, rows);
    }

    @Test
    void pilesAreLastInFirstOut() {
        DungeonMap m = corridor("###", "#.#", "###");
        assertTrue(m.itemsAt(1, 1, 0).isEmpty());
        m.addItem(1, 1, 0, APPLE);
        m.addItem(1, 1, 0, SWORD);
        assertEquals(List.of(APPLE, SWORD), m.itemsAt(1, 1, 0), "bottom first");
        assertTrue(m.itemsAt(1, 1, 1).isEmpty(), "cells are separate");
        assertSame(SWORD, m.takeItem(1, 1, 0));
        assertSame(APPLE, m.takeItem(1, 1, 0));
        assertNull(m.takeItem(1, 1, 0));
        assertNull(m.takeItem(-1, 9, 0), "out of bounds");
    }

    @Test
    void thrownItemFliesUntilTheWallAndLandsOnItsSide() {
        // Party at (1,4) facing north; open squares up to (1,1), wall at (1,0).
        DungeonMap m = corridor("###", "#.#", "#.#", "#.#", "#.#", "###");
        m.throwItem(SWORD, 1, 4, Direction.NORTH, true, 10);
        assertEquals(1, m.projectiles().size());
        for (int y = 3; y >= 1; y--) {
            assertTrue(m.tickProjectiles().moved());
            assertEquals(y, m.projectiles().get(0).y());
        }
        assertTrue(m.tickProjectiles().moved(), "the tick it lands");
        assertTrue(m.projectiles().isEmpty());
        assertEquals(List.of(SWORD), m.itemsAt(1, 1, Direction.NORTH.cellOf(1)), "far-right cell, north-east");
        assertFalse(m.tickProjectiles().moved(), "nothing left in flight");
    }

    @Test
    void thrownItemStopsAtTheEndOfItsRange() {
        DungeonMap m = corridor("###", "#.#", "#.#", "#.#", "#.#", "###");
        m.throwItem(APPLE, 1, 4, Direction.NORTH, false, 2);
        m.tickProjectiles();
        m.tickProjectiles();
        m.tickProjectiles();
        assertTrue(m.projectiles().isEmpty());
        assertEquals(List.of(APPLE), m.itemsAt(1, 2, 0), "2 squares out, far-left cell (north-west)");
    }

    @Test
    void closedDoorStopsAThrowButAnOpenOneDoesNot() {
        DungeonMap closed = corridor("###", "#.#", "#D#", "#.#", "###");
        closed.throwItem(APPLE, 1, 3, Direction.NORTH, false, 5);
        closed.tickProjectiles();
        assertTrue(closed.projectiles().isEmpty(), "blocked by the door straight away");
        assertEquals(List.of(APPLE), closed.itemsAt(1, 3, 0), "lands in the thrower's own square");

        DungeonMap open = corridor("###", "#.#", "#d#", "#.#", "###");
        open.throwItem(APPLE, 1, 3, Direction.NORTH, false, 5);
        open.tickProjectiles();
        open.tickProjectiles();
        open.tickProjectiles();
        assertEquals(List.of(APPLE), open.itemsAt(1, 1, 0), "through the doorway to the far square");
    }

    @Test
    void everyCatalogItemHasAFloorPicture() {
        for (Item.Category c : Item.Category.values()) {
            int count = switch (c) {
                case WEAPON -> 46;
                case ARMOUR -> 58;
                case POTION -> 21;
                case JUNK -> 53;
                case SCROLL, CONTAINER -> 1;
            };
            for (int type = 0; type < count; type++) {
                Item item = ItemCatalog.item(c, type);
                int g = ItemCatalog.floorGraphic(item);
                assertTrue(g >= 498 && g <= 583, item + " -> " + g);
            }
        }
    }
}
