package dm.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** DM's F318/F319: what happens when a champion's health runs out. */
class DeathTest {

    private static final Item SWORD = ItemCatalog.item(Item.Category.WEAPON, 10);
    private static final Item HELM = ItemCatalog.item(Item.Category.ARMOUR, 25);
    private static final Item APPLE = ItemCatalog.item(Item.Category.JUNK, 29);

    private DungeonMap map;
    private Party party;
    private Champion elija;
    private Champion halk;

    @BeforeEach
    void setUp() {
        map = DungeonMap.fromAscii(0, "#####", "#...#", "#####");
        party = new Party(List.of(map), 0, 2, 1, Direction.EAST);
        elija = Champion.parse(ChampionTest.ELIJA, 0);
        halk = Champion.parse("HALK\nTHE BARBARIAN\n\nM\nAAFKACOOAAAA\nCIDHCLBOCOCGDA\nEAEAAAAAAAAAAAAA", 1);
        elija.addStartingItem(SWORD);
        elija.addStartingItem(HELM);
        elija.addStartingItem(APPLE);
        party.recruit(new ChampionMirror(1, 0, Direction.SOUTH, elija)); // front left
        party.recruit(new ChampionMirror(2, 0, Direction.SOUTH, halk));  // front right
    }

    @Test
    void theDeadDropEverythingWithTheirBonesOnTop() {
        elija.takeDamage(elija.health());
        assertEquals(List.of(elija), party.bury());
        assertTrue(elija.items().isEmpty());
        // Front left, facing east, is the north-east cell.
        List<Item> pile = map.itemsAt(2, 1, 1);
        assertEquals(4, pile.size());
        assertEquals("BONES", pile.get(3).name());
        assertEquals(0, pile.get(3).charges(), "the bones remember they were the first member");
        assertSame(SWORD, pile.get(2), "the action hand falls last");
        assertEquals(List.of(APPLE, HELM), pile.subList(0, 2), "the pack before the head");
    }

    @Test
    void theDeadLeaveTheFormationAndAreBuriedOnce() {
        elija.takeDamage(elija.health());
        party.bury();
        assertNull(party.at(Party.FRONT_LEFT));
        assertSame(halk, party.at(Party.FRONT_RIGHT));
        assertTrue(party.bury().isEmpty(), "already buried");
        assertSame(halk, party.leader());
        assertFalse(party.allDead());
    }

    @Test
    void theGameEndsWhenEveryoneIsDead() {
        elija.takeDamage(elija.health());
        halk.takeDamage(halk.health());
        assertEquals(2, party.bury().size());
        assertTrue(party.allDead());
        assertNull(party.leader());
    }

    @Test
    void theDeadNeitherTireNorHungerNorFeel() {
        elija.takeDamage(elija.health());
        party.bury();
        int food = elija.food();
        for (int i = 0; i < Upkeep.PERIOD; i++) {
            party.tick();
        }
        assertEquals(food, elija.food());
        int[] bump = party.bump(Party.Move.FORWARD);
        assertEquals(0, bump[0]);
        assertEquals(Party.BUMP_DAMAGE, bump[1], "Halk still takes the bump");
        party.setHeld(APPLE);
        assertFalse(party.feed(elija));
    }

    @Test
    void aNewPartyIsNotDead() {
        Party empty = new Party(map, 1, 1, Direction.EAST);
        assertFalse(empty.allDead(), "no champions yet is the ghost, not the end");
    }
}
