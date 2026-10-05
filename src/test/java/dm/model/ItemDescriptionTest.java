package dm.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemDescriptionTest {

    /** ELIJA with no skills at all, so a priest level of 1. */
    private static final String UNSKILLED = "ELIJA\nLION OF YAITOPYA\n\nM\nAADMACEEAABG\nDCCKCICKCEDFCI\nAAAAAAAAAAAAAAAA";

    private Party party;
    private Champion viewer;

    @BeforeEach
    void setUp() {
        party = new Party(List.of(DungeonMap.fromAscii(0, "###", "#.#", "###")), 0, 1, 1, Direction.EAST);
        party.recruit(new ChampionMirror(0, 0, Direction.SOUTH, Champion.parse(UNSKILLED, 0)));
        viewer = party.members().get(0);
    }

    private ItemDescription describe(Item item) {
        return ItemDescription.of(item, viewer, party);
    }

    @Test
    void everythingEndsWithItsWeight() {
        Item dagger = ItemCatalog.item(Item.Category.WEAPON, 8);
        assertEquals(List.of("WEIGHS 0.5 KG."), describe(dagger).lines());
        assertEquals("DAGGER", describe(dagger).name());
    }

    @Test
    void aBurntOutTorchSaysSo() {
        Item torch = ItemCatalog.item(Item.Category.WEAPON, 2, 0);
        assertTrue(Light.isTorch(torch));
        assertEquals("(BURNT OUT)", describe(torch).lines().get(0));
        assertFalse(describe(torch.withCharges(5)).lines().contains("(BURNT OUT)"));
    }

    @Test
    void aWaterskinTellsHowFullItIs() {
        String[] expected = {"(EMPTY)", "(ALMOST EMPTY)", "(ALMOST FULL)", "(FULL)"};
        for (int draughts = 0; draughts <= 3; draughts++) {
            Item skin = ItemCatalog.item(Item.Category.JUNK, ItemCatalog.WATERSKIN, draughts);
            assertEquals(expected[draughts], describe(skin).lines().get(0));
        }
    }

    @Test
    void theCompassTellsTheFacing() {
        Item compass = ItemCatalog.item(Item.Category.JUNK, 0);
        assertEquals("PARTY FACING EAST", describe(compass).lines().get(0));
    }

    @Test
    void foodIsConsumable() {
        Item apple = ItemCatalog.item(Item.Category.JUNK, ItemCatalog.FIRST_FOOD);
        assertEquals("(CONSUMABLE)", describe(apple).lines().get(0));
    }

    @Test
    void attributesAreListedAsDmDoes() {
        assertEquals("", ItemDescription.attributes(Item.CURSED, 0));
        assertEquals("(CURSED)", ItemDescription.attributes(Item.CURSED, Item.CURSED));
        assertEquals("(POISONED AND CURSED)", ItemDescription.attributes(14, Item.POISONED | Item.CURSED));
        assertEquals("(POISONED, BROKEN AND CURSED)", ItemDescription.attributes(14, 14));
    }

    @Test
    void longLinesWrapAtASpace() {
        Item dagger = ItemCatalog.item(Item.Category.WEAPON, 8).withFlags(Item.POISONED | Item.BROKEN | Item.CURSED);
        assertEquals(List.of("(POISONED, BROKEN", "AND CURSED)", "WEIGHS 0.5 KG."), describe(dagger).lines());
    }

    @Test
    void aPriestSeesAPotionsPower() {
        Item potion = ItemCatalog.item(Item.Category.POTION, 6, 120);
        assertEquals(potion.name(), describe(potion).name(), "priest level 1 sees only the name");
        viewer.addExperience(Champion.PRIEST, 1000);
        assertTrue(viewer.skillLevel(Champion.PRIEST) > 1);
        assertEquals((char) ('_' + 3) + " " + potion.name(), describe(potion).name());
        Item water = ItemCatalog.item(Item.Category.POTION, ItemCatalog.WATER_FLASK, 120);
        assertEquals(water.name(), describe(water).name(), "never for water");
    }

    @Test
    void bonesCarryTheirChampionsName() {
        Item bones = ItemCatalog.item(Item.Category.JUNK, Party.BONES, 0);
        assertEquals("ELIJA " + bones.name(), describe(bones).name());
    }

    @Test
    void scrollsAndChestsOpenInsteadOfBeingDescribed() {
        assertFalse(ItemDescription.describes(ItemCatalog.item(Item.Category.SCROLL, 0)));
        assertFalse(ItemDescription.describes(ItemCatalog.item(Item.Category.CONTAINER, 0)));
        assertTrue(ItemDescription.describes(ItemCatalog.item(Item.Category.JUNK, 0)));
    }
}
