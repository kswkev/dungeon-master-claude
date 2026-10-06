package dm.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** DM's F299: what worn and held things do to their bearer's statistics. */
class ItemEffectsTest {

    private Champion c;

    @BeforeEach
    void setUp() {
        c = Champion.parse(ChampionTest.ELIJA, 0);
        for (Slot s : Slot.values()) {
            c.take(s); // Elija's own things out of the way
        }
    }

    @Test
    void theCrownOfNerraAddsWisdomOnlyOnTheHead() {
        int wisdom = c.stat(Champion.Stat.WISDOM);
        int max = c.maxStat(Champion.Stat.WISDOM);
        Item crown = ItemCatalog.item(Item.Category.ARMOUR, 24);
        c.place(Slot.HEAD, crown);
        assertEquals(wisdom + 10, c.stat(Champion.Stat.WISDOM));
        assertEquals(max + 10, c.maxStat(Champion.Stat.WISDOM));
        c.take(Slot.HEAD);
        c.place(Slot.BACKPACK_1, crown);
        assertEquals(wisdom, c.stat(Champion.Stat.WISDOM), "not in the pack");
    }

    @Test
    void staffsAddManaInTheActionHand() {
        int mana = c.maxMana();
        c.place(Slot.ACTION_HAND, ItemCatalog.item(Item.Category.WEAPON, 40)); // The Conduit
        assertEquals(mana + 16, c.maxMana());
        c.place(Slot.ACTION_HAND, ItemCatalog.item(Item.Category.WEAPON, 21)); // the Mace of Order
        assertEquals(mana, c.maxMana());
        assertEquals(5, c.itemBonus(Champion.Stat.STRENGTH));
        c.take(Slot.ACTION_HAND);
        c.place(Slot.READY_HAND, ItemCatalog.item(Item.Category.WEAPON, 40));
        assertEquals(mana, c.maxMana(), "only in the action hand");
    }

    @Test
    void aCurseCostsLuckInsteadOfTheBonusAndARabbitsFootAddsItAnywhere() {
        c.place(Slot.HEAD, ItemCatalog.item(Item.Category.ARMOUR, 24).withFlags(Item.CURSED));
        assertEquals(-3, c.itemBonus(Champion.Stat.LUCK));
        assertEquals(0, c.itemBonus(Champion.Stat.WISDOM));
        c.place(Slot.BACKPACK_5, ItemCatalog.item(Item.Category.JUNK, 46));
        assertEquals(7, c.itemBonus(Champion.Stat.LUCK));
        c.place(Slot.BACKPACK_6, ItemCatalog.item(Item.Category.WEAPON, 8).withFlags(Item.CURSED));
        assertEquals(7, c.itemBonus(Champion.Stat.LUCK), "a cursed thing in the pack does nothing");
    }

    @Test
    void settingAStatisticCountsTheBonus() {
        c.place(Slot.LEGS, ItemCatalog.item(Item.Category.ARMOUR, 55)); // Powertowers
        c.setStat(Champion.Stat.STRENGTH, 80);
        assertEquals(80, c.stat(Champion.Stat.STRENGTH));
        c.take(Slot.LEGS);
        assertEquals(70, c.stat(Champion.Stat.STRENGTH));
    }

    @Test
    void luckNeverFallsBelowItsMinimumMovedByTheRabbitsFoot() {
        c.place(Slot.POUCH_1, ItemCatalog.item(Item.Category.JUNK, 46));
        c.setStat(Champion.Stat.LUCK, 0);
        Random random = new Random(3);
        for (int i = 0; i < 50; i++) {
            c.isLucky(-1, random);
        }
        assertEquals(true, c.stat(Champion.Stat.LUCK) >= 20, "10 + the foot's 10");
    }
}
