package dm.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChampionTest {

    /** Elija's text exactly as stored in the original DUNGEON.DAT. */
    static final String ELIJA = "ELIJA\nLION OF YAITOPYA\n\nM\nAADMACEEAABG\nDCCKCICKCEDFCI\nBBCAAAAACBECAAAA";

    @Test
    void parsesNameAndVitals() {
        Champion c = Champion.parse(ELIJA, 0);
        assertEquals("ELIJA", c.name());
        assertEquals("LION OF YAITOPYA", c.title());
        assertEquals("ELIJA, LION OF YAITOPYA", c.fullName());
        assertEquals('M', c.gender());
        assertEquals(60, c.maxHealth());
        assertEquals(58, c.maxStamina(), "stamina is stored x10");
        assertEquals(22, c.maxMana());
        assertEquals(60, c.health());
    }

    @Test
    void levelProgressIsThePercentOfTheWayToTheNextLevel() {
        Champion c = Champion.parse(ELIJA, 0);
        c.reincarnate("ELIJA", "", new java.util.Random(1)); // no experience
        assertEquals(0, c.levelProgress(Champion.FIGHTER));
        long[] steps = {250, 250, 250, 249, 1, (500L << 14) - 1000};
        int[] expected = {50, 0, 50, 99, 0, 100};
        int[] levels = {1, 2, 2, 2, 3, 16};
        for (int i = 0; i < steps.length; i++) {
            c.addExperience(Champion.FIGHTER, steps[i]);
            assertEquals(levels[i], c.lastingSkillLevel(Champion.FIGHTER));
            assertEquals(expected[i], c.levelProgress(Champion.FIGHTER), "after " + steps[i]);
        }
        c.addTemporaryExperience(Champion.PRIEST, 250);
        assertEquals(0, c.levelProgress(Champion.PRIEST), "temporary experience doesn't count");
    }

    @Test
    void movementTicksFollowTheLoad() {
        Champion c = Champion.parse(ELIJA, 0);
        int max = c.maxLoad();
        assertEquals(2, c.movementTicks(max * 5 / 8), "up to five eighths");
        assertEquals(3, c.movementTicks(max * 5 / 8 + 1));
        assertEquals(3, c.movementTicks(max), "BUG0_72 fixed: the maximum isn't over");
        assertEquals(4, c.movementTicks(max + 1));
        assertEquals(8, c.movementTicks(max * 2));
        c.addWounds(1 << 5); // feet
        assertEquals(3, c.movementTicks(0));
        assertEquals(6, c.movementTicks(max + 1), "2 more when overloaded");
        Item boots = null;
        for (int type = 0; boots == null; type++) {
            Item i = ItemCatalog.item(Item.Category.ARMOUR, type);
            if (i.name().equals("BOOTS OF SPEED")) {
                boots = i;
            }
        }
        c.place(Slot.FEET, boots);
        assertEquals(2, c.movementTicks(0), "Boots of Speed take 1 off");
    }

    @Test
    void parsesStatsInDmOrder() {
        Champion c = Champion.parse(ELIJA, 0);
        assertEquals(50, c.stat(Champion.Stat.LUCK));
        assertEquals(42, c.stat(Champion.Stat.STRENGTH));
        assertEquals(40, c.stat(Champion.Stat.DEXTERITY));
        assertEquals(42, c.stat(Champion.Stat.WISDOM));
        assertEquals(36, c.stat(Champion.Stat.VITALITY));
        assertEquals(53, c.stat(Champion.Stat.ANTI_MAGIC));
        assertEquals(40, c.maxStat(Champion.Stat.ANTI_FIRE));
    }

    @Test
    void derivesSkillTitlesFromHiddenSkills() {
        Champion c = Champion.parse(ELIJA, 0);
        // Fighter hidden skills 1,1,2,0 -> 250+250+500 = 1000 exp -> level 3.
        assertEquals("NOVICE", c.skillTitle(0));
        assertNull(c.skillTitle(1), "no ninja skill");
        // Priest 2,1,4,2 -> 500+250+2000+500 = 3250 exp -> level 4.
        assertEquals("APPRENTICE", c.skillTitle(2));
        assertNull(c.skillTitle(3), "no wizard skill");
    }

    @Test
    void titleStartingWithTheJoinsWithoutComma() {
        Champion c = Champion.parse("HALK\nTHE BARBARIAN\n\nM\nAAFKACOOAAAA\nCIDHCLBOCOCGDA\nEAEAAAAAAAAAAAAA", 1);
        assertEquals("HALK THE BARBARIAN", c.fullName());
        assertEquals("JOURNEYMAN", c.skillTitle(0));
        assertEquals(0, c.maxMana());
    }

    @Test
    void emptyTitleShowsNameOnly() {
        Champion c = Champion.parse("GOTHMOG\n\n\nM\nAADMACCGAABC\nBOCICDDACCDCDL\nAAAAAAAAAAAAEDCC", 21);
        assertEquals("GOTHMOG", c.fullName());
        assertEquals(21, c.portrait());
    }

    @Test
    void damageStopsAtZeroHealth() {
        Champion c = Champion.parse(ELIJA, 0);
        assertEquals(1, c.takeDamage(1));
        assertEquals(59, c.health());
        assertEquals(60, c.maxHealth());
        assertEquals(59, c.takeDamage(500), "only the remaining health is lost");
        assertEquals(0, c.health());
        assertEquals(0, c.takeDamage(1));
        assertEquals(0, c.takeDamage(-5), "negative damage does nothing");
    }

    @Test
    void rejectsMalformedText() {
        assertThrows(IllegalArgumentException.class, () -> Champion.parse("WELCOME BACK\nBRAVE\nADVENTURERS.", 0));
        assertThrows(IllegalArgumentException.class,
                () -> Champion.parse("X\nY\n\nM\nZZZZZZZZZZZZ\nAAAAAAAAAAAAAA\nAAAAAAAAAAAAAAAA", 0));
    }

    @Test
    void startingItemsGoWhereDmPutsThem() {
        Champion c = Champion.parse(ELIJA, 0);
        assertEquals(Slot.HEAD, c.addStartingItem(ItemCatalog.item(Item.Category.ARMOUR, 25)));   // helm
        assertEquals(Slot.LEGS, c.addStartingItem(ItemCatalog.item(Item.Category.ARMOUR, 6)));    // robe (legs)
        assertEquals(Slot.ACTION_HAND, c.addStartingItem(ItemCatalog.item(Item.Category.WEAPON, 32))); // star
        assertEquals(Slot.QUIVER_1, c.addStartingItem(ItemCatalog.item(Item.Category.WEAPON, 32)));
        assertEquals(Slot.READY_HAND, c.addStartingItem(ItemCatalog.item(Item.Category.ARMOUR, 29))); // shield
        assertEquals(Slot.POUCH_1, c.addStartingItem(ItemCatalog.item(Item.Category.POTION, 20)));
        assertEquals(Slot.NECK, c.addStartingItem(ItemCatalog.item(Item.Category.JUNK, 39)));     // moonstone
        assertEquals(Slot.BACKPACK_1, c.addStartingItem(ItemCatalog.item(Item.Category.JUNK, 29))); // apple
        // A second helm has nowhere to be worn, so it goes in the backpack.
        assertEquals(Slot.BACKPACK_2, c.addStartingItem(ItemCatalog.item(Item.Category.ARMOUR, 26)));
    }

    @Test
    void takeEmptiesTheSlotAndPlaceReturnsWhatWasThere() {
        Champion c = Champion.parse(ELIJA, 0);
        Item sword = ItemCatalog.item(Item.Category.WEAPON, 10);
        Item apple = ItemCatalog.item(Item.Category.JUNK, 29);
        c.addStartingItem(sword);   // action hand
        assertEquals(sword, c.take(Slot.ACTION_HAND));
        assertNull(c.items().get(Slot.ACTION_HAND));
        assertNull(c.take(Slot.ACTION_HAND), "nothing left to take");

        assertNull(c.place(Slot.BACKPACK_5, sword), "empty cell");
        assertEquals(sword, c.place(Slot.BACKPACK_5, apple), "a swap hands back the sword");
        assertEquals(apple, c.items().get(Slot.BACKPACK_5));
        assertThrows(IllegalArgumentException.class, () -> c.place(Slot.HEAD, sword));
    }

    @Test
    void slotsFollowDmRules() {
        Item sword = ItemCatalog.item(Item.Category.WEAPON, 10);
        Item arrow = ItemCatalog.item(Item.Category.WEAPON, 27);
        Item helm = ItemCatalog.item(Item.Category.ARMOUR, 25);
        Item moonstone = ItemCatalog.item(Item.Category.JUNK, 39);
        Item key = ItemCatalog.item(Item.Category.JUNK, 9);
        Item apple = ItemCatalog.item(Item.Category.JUNK, 29);
        Item potion = ItemCatalog.item(Item.Category.POTION, 20);
        Item chest = ItemCatalog.item(Item.Category.CONTAINER, 0);

        for (Item any : new Item[] {sword, helm, apple, chest}) {
            assertTrue(any.fits(Slot.READY_HAND) && any.fits(Slot.ACTION_HAND) && any.fits(Slot.BACKPACK_17));
        }
        assertTrue(helm.fits(Slot.HEAD));
        assertFalse(sword.fits(Slot.HEAD));
        assertFalse(helm.fits(Slot.TORSO));
        assertTrue(moonstone.fits(Slot.NECK));
        assertFalse(apple.fits(Slot.NECK));

        assertTrue(potion.fits(Slot.POUCH_1));
        assertTrue(key.fits(Slot.POUCH_2));
        assertTrue(apple.fits(Slot.POUCH_1), "G237 lets small food into a pouch");
        assertTrue(ItemCatalog.item(Item.Category.JUNK, ItemCatalog.WATERSKIN).fits(Slot.POUCH_1), "#45");
        assertTrue(ItemCatalog.item(Item.Category.JUNK, ItemCatalog.WATERSKIN, 3).fits(Slot.POUCH_2), "#45");
        assertFalse(sword.fits(Slot.POUCH_1));
        assertFalse(chest.fits(Slot.POUCH_1));

        assertTrue(sword.fits(Slot.QUIVER_1), "the first quiver cell takes any weapon");
        assertFalse(sword.fits(Slot.QUIVER_2));
        assertTrue(arrow.fits(Slot.QUIVER_4));
        assertFalse(potion.fits(Slot.QUIVER_1));
        Item dagger = ItemCatalog.item(Item.Category.WEAPON, ItemCatalog.DAGGER);
        for (Slot quiver : new Slot[] {Slot.QUIVER_1, Slot.QUIVER_2, Slot.QUIVER_3, Slot.QUIVER_4}) {
            assertTrue(dagger.fits(quiver), "#52: a dagger fits every quiver cell");
        }
        assertFalse(ItemCatalog.item(Item.Category.WEAPON, 23).fits(Slot.QUIVER_2), "#52: a club only the first");
    }

    @Test
    void catalogNamesDuplicateIconsByVariant() {
        assertEquals("ROBE", ItemCatalog.item(Item.Category.ARMOUR, 5).name());
        assertEquals(0, ItemCatalog.item(Item.Category.ARMOUR, 5).nameVariant());
        assertEquals(1, ItemCatalog.item(Item.Category.ARMOUR, 6).nameVariant());
        assertEquals("UNKNOWN", ItemCatalog.item(Item.Category.WEAPON, 99).name());
    }
}
