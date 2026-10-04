package dm.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** DM's creature, armour and wound data, spot-checked against ScummVM's tables. */
class CreatureDataTest {

    @Test
    void creatureInfoMatchesDm() {
        assertEquals(17, CreatureType.MUMMY.movementTicks());
        assertEquals(12, CreatureType.MUMMY.attackTicks());
        assertEquals(20, CreatureType.MUMMY.attack());
        assertEquals(4, CreatureType.MUMMY.sightRange());
        assertEquals(2, CreatureType.MUMMY.smellRange());
        assertEquals(1, CreatureType.MUMMY.attackRange());
        assertEquals(7, CreatureType.MUMMY.attackSound(), "C07 attack mummy/ghost");
        assertEquals(24, CreatureType.MUMMY.movementSound());
        assertEquals(240, CreatureType.GIANT_SCORPION.poisonAttack());
        assertEquals(CreatureType.IMMOBILE, CreatureType.BLACK_FLAME.movementTicks());
        assertTrue(CreatureType.GIANT_WASP.levitates());
        assertTrue(CreatureType.GHOST.nonMaterial());
        assertFalse(CreatureType.MUMMY.levitates());
        assertTrue(CreatureType.LORD_CHAOS.archenemy());
        assertEquals(-1, CreatureType.SWAMP_SLIME.attackSound());
        assertEquals(4, CreatureType.STONE_GOLEM.attackSound(), "C04 wooden thud");
    }

    @Test
    void armourDefense() {
        Item helmet = ItemCatalog.item(Item.Category.ARMOUR, 26);
        assertEquals("HELMET", helmet.name());
        assertEquals(17, ItemCatalog.armourDefense(helmet, false));
        assertEquals(17 * 9 >> 3, ItemCatalog.armourDefense(helmet, true), "sharp defense 5: x9/8");
        assertEquals(0, ItemCatalog.armourDefense(ItemCatalog.item(Item.Category.WEAPON, 10), false));
    }

    @Test
    void removingACreatureClosesTheGap() {
        Group g = new Group(CreatureType.MUMMY, 1, 1, 0 | (1 << 2) | (2 << 4), new int[] {10, 20, 30, 0}, 3,
                Direction.NORTH, List.of());
        assertTrue(g.remove(0));
        assertEquals(2, g.count());
        assertEquals(20, g.health(0));
        assertEquals(30, g.health(1));
        assertEquals(1, g.cellOf(0));
        assertEquals(2, g.cellOf(1));
    }

    @Test
    void viPotionHealsAWound() {
        Champion c = Champion.parse(ChampionTest.ELIJA, 0);
        c.setWounds(Champion.WOUND_HEAD | Champion.WOUND_LEGS);
        Upkeep.consume(c, ItemCatalog.item(Item.Category.POTION, 14, 100), new Random(1));
        assertTrue(Integer.bitCount(c.wounds()) < 2, "at least one wound heals");
    }

    @Test
    void antiveninCuresPoison() {
        Champion c = Champion.parse(ChampionTest.ELIJA, 0);
        c.poisons().add(new Champion.Poison(40, 10));
        assertTrue(c.poisoned());
        Upkeep.consume(c, ItemCatalog.item(Item.Category.POTION, 10, 100));
        assertFalse(c.poisoned());
    }
}
