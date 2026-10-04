package dm.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    /** An empty party on (x, y) facing north, to run the clock that moves things in flight. */
    private static Party clock(DungeonMap m, int x, int y) {
        Party p = new Party(m, x, y, Direction.NORTH);
        p.setRandom(new Random(1));
        return p;
    }

    @Test
    void thrownItemFliesHalfASquareATickUntilTheWallAndLandsOnItsSide() {
        // From (1,4) north; open squares up to (1,1), wall at (1,0).
        DungeonMap m = corridor("###", "#.#", "#.#", "#.#", "#.#", "###");
        Party p = clock(m, 1, 4);
        Flight.launch(p, SWORD, m, 1, 4, 1, Direction.NORTH, 200, 100, 5);
        assertEquals(1, m.projectiles().size());
        for (int y = 3; y >= 1; y--) {
            p.tick();
            assertEquals(y, m.projectiles().get(0).y(), "into the next square");
            assertEquals(2, m.projectiles().get(0).cell(), "its near (south-east) cell");
            p.tick();
            assertEquals(1, m.projectiles().get(0).cell(), "then its far (north-east) cell");
        }
        p.tick();
        assertTrue(m.projectiles().isEmpty(), "the wall stops it");
        assertEquals(List.of(SWORD), m.itemsAt(1, 1, Direction.NORTH.cellOf(1)), "far-right cell, north-east");
    }

    @Test
    void thrownItemDropsWhenItsEnergyIsSpent() {
        DungeonMap m = corridor("###", "#.#", "#.#", "#.#", "#.#", "###");
        Party p = clock(m, 1, 4);
        Flight.launch(p, APPLE, m, 1, 4, 0, Direction.NORTH, 12, 50, 5);
        p.tick(); // the first move is free: (1,3)
        p.tick(); // 12 -> 7
        p.tick(); // 7 -> 2: (1,2)
        assertEquals(1, m.projectiles().size());
        p.tick(); // 2 is no more than a step: it drops
        assertTrue(m.projectiles().isEmpty());
        assertEquals(List.of(APPLE), m.itemsAt(1, 2, 3), "on its near-left (south-west) cell");
    }

    @Test
    void closedDoorStopsAThrowButAnOpenOneDoesNot() {
        DungeonMap closed = corridor("###", "#.#", "#D#", "#.#", "###");
        Party p = clock(closed, 1, 3);
        Flight.launch(p, SWORD, closed, 1, 3, 0, Direction.NORTH, 200, 100, 5);
        p.tick();
        p.tick();
        assertTrue(closed.projectiles().isEmpty(), "it hits the door");
        assertEquals(List.of(SWORD), closed.itemsAt(1, 2, 3), "and drops in front of it");

        DungeonMap open = corridor("###", "#.#", "#d#", "#.#", "###");
        p = clock(open, 1, 3);
        Flight.launch(p, SWORD, open, 1, 3, 0, Direction.NORTH, 200, 100, 5);
        for (int t = 0; t < 6; t++) {
            p.tick();
        }
        assertEquals(List.of(SWORD), open.itemsAt(1, 1, 0), "through the doorway to the far wall");
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
