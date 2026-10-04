package dm.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** DM's combat tables (Sprint 16), spot-checked against ReDMCSB/ScummVM. */
class CombatDataTest {

    private static Item weapon(int type) {
        return ItemCatalog.item(Item.Category.WEAPON, type);
    }

    @Test
    void weaponInfo() {
        Item sword = weapon(10);
        assertEquals("SWORD", sword.name());
        assertEquals(ItemCatalog.CLASS_SWING_WEAPON, ItemCatalog.weaponClass(sword));
        assertEquals(34, ItemCatalog.weaponStrength(sword));
        assertEquals(10, ItemCatalog.weaponKineticEnergy(sword));
        Item bow = weapon(25);
        assertEquals("BOW", bow.name());
        assertEquals(20, ItemCatalog.weaponClass(bow), "a bow (16-31)");
        assertEquals(0x32, ItemCatalog.shootAttack(bow));
        assertEquals(ItemCatalog.CLASS_BOW_AMMUNITION, ItemCatalog.weaponClass(weapon(27)), "arrow");
        assertEquals(-1, ItemCatalog.weaponClass(ItemCatalog.item(Item.Category.JUNK, 0)));
    }

    @Test
    void actionSets() {
        assertEquals(12, ItemCatalog.actionSet(weapon(8)), "dagger: throw, stab, slash");
        assertEquals(Actions.THROW, Actions.setAction(12, 0));
        assertEquals(Actions.STAB_9, Actions.setAction(12, 1));
        assertEquals(Actions.SLASH, Actions.setAction(12, 2));
        assertEquals(2, Actions.setProperty(12, 2), "slash needs skill level 2");
        assertEquals(13, ItemCatalog.actionSet(weapon(10)), "sword: swing, parry, chop");
        assertEquals(Actions.CHOP, Actions.setAction(13, 2));
        assertEquals(14, ItemCatalog.actionSet(weapon(11)), "rapier: jab, parry, thrust");
        assertEquals(41, ItemCatalog.actionSet(ItemCatalog.item(Item.Category.ARMOUR, 30)), "wooden shield: block, hit");
        assertEquals(42, ItemCatalog.actionSet(ItemCatalog.item(Item.Category.POTION, 3)), "a ven potion is thrown");
        assertEquals(37, ItemCatalog.actionSet(ItemCatalog.item(Item.Category.JUNK, 6)), "a copper coin flips");
        assertEquals(0, ItemCatalog.actionSet(ItemCatalog.item(Item.Category.JUNK, 29)), "an apple does nothing");
        assertEquals("PUNCH", Actions.name(Actions.setAction(Actions.EMPTY_HAND_SET, 0)));
    }

    @Test
    void actionTables() {
        assertEquals("SWING", Actions.name(Actions.SWING));
        assertEquals(Champion.SWING, Actions.skill(Actions.SWING));
        assertEquals(6, Actions.disabledTicks(Actions.SWING));
        assertEquals(2, Actions.stamina(Actions.SWING));
        assertEquals(6, Actions.experience(Actions.SWING));
        assertEquals(5, Actions.defense(Actions.SWING));
        assertEquals(32, Actions.hitProbability(Actions.SWING));
        assertEquals(16, Actions.damageFactor(Actions.SWING));
        assertEquals(29, Actions.defense(Actions.PARRY));
        assertTrue(Actions.isMagic(Actions.FIREBALL));
        assertFalse(Actions.isMagic(Actions.THROW));
    }

    @Test
    void fixedPossessions() {
        assertArrayEquals(new int[][] {{Item.Category.WEAPON.ordinal(), 9, 0}, {Item.Category.ARMOUR.ordinal(), 30, 0}},
                CreatureType.SKELETON.fixedPossessions(), "a falchion and a wooden shield");
        assertTrue(CreatureType.SKELETON.dropsFixedPossessions());
        assertEquals(0, CreatureType.MUMMY.fixedPossessions().length);
        assertFalse(CreatureType.MUMMY.dropsFixedPossessions());
    }

    @Test
    void championsTurnWithTheParty() {
        DungeonMap map = DungeonMap.fromAscii(0, "###", "#.#", "###");
        Party p = new Party(List.of(map), 0, 1, 1, Direction.EAST);
        p.recruit(new ChampionMirror(0, 0, Direction.SOUTH, Champion.parse(ChampionTest.ELIJA, 0)));
        Champion c = p.members().get(0);
        assertEquals(Direction.EAST, c.facing());
        p.turnRight();
        assertEquals(Direction.SOUTH, c.facing());
        c.face(Direction.WEST); // turned to an attacker on the right
        p.turnLeft();
        assertEquals(Direction.SOUTH, c.facing(), "stays turned relative to the party");
    }

    @Test
    void theFirestaffRaisesEverySkill() {
        Champion c = Champion.parse(ChampionTest.ELIJA, 0);
        int before = c.skillLevel(Champion.WIZARD);
        c.take(Slot.ACTION_HAND);
        c.place(Slot.ACTION_HAND, weapon(7));
        assertEquals(before + 1, c.skillLevel(Champion.WIZARD));
        assertEquals(before, c.baseLevel(Champion.WIZARD, true), "but not its underlying level");
    }
}
