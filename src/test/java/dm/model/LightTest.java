package dm.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** DM's light (F337, F338), with amounts worked out from its tables by hand. */
class LightTest {

    private static Item torch(int charges) {
        return ItemCatalog.item(Item.Category.WEAPON, 2, charges);
    }

    private static int palette(int magical, Item... hands) {
        return Light.palette(Light.amount(Arrays.asList(hands), magical));
    }

    @Test
    void torchesAddUpEachWorthHalfTheOneBefore() {
        assertEquals(100, Light.amount(List.of(torch(15)), 0));
        assertEquals(150, Light.amount(List.of(torch(15), torch(15)), 0));
        assertEquals(100 + 16, Light.amount(Arrays.asList(null, torch(4), torch(15)), 0),
                "the brightest first, then 33 at half");
    }

    @Test
    void lightPicksOneOfSixPalettes() {
        assertEquals(0, palette(0, torch(15)));
        assertEquals(3, palette(0, torch(4)), "33 light");
        assertEquals(4, palette(Light.illumulet()), "an Illumulet alone: 12 light");
        assertEquals(Light.DARKEST, palette(0));
        assertEquals(Light.DARKEST, palette(0, torch(0)), "a burnt-out torch");
    }

    private static Party partyOn(int difficulty) {
        DungeonMap m = DungeonMap.fromAscii(0, "#####", "#...#", "#####");
        m.setDifficulty(difficulty);
        Party p = new Party(List.of(m), 0, 1, 1, Direction.EAST);
        Champion c = Champion.parse(ChampionTest.ELIJA, 0);
        p.recruit(new ChampionMirror(1, 0, Direction.SOUTH, c));
        return p;
    }

    @Test
    void levelOneIsAlwaysLit() {
        assertEquals(0, partyOn(0).paletteIndex());
    }

    @Test
    void deeperLevelsAreDarkWithoutATorch() {
        Party p = partyOn(1);
        assertEquals(Light.DARKEST, p.paletteIndex());
        Champion c = p.members().get(0);
        c.place(Slot.ACTION_HAND, torch(15));
        assertEquals(0, p.paletteIndex());
        c.place(Slot.BACKPACK_1, c.take(Slot.ACTION_HAND));
        assertEquals(Light.DARKEST, p.paletteIndex(), "a torch in the pack gives no light");
    }

    @Test
    void torchesBurnDownEvery512Ticks() {
        Party p = partyOn(1);
        Champion c = p.members().get(0);
        c.place(Slot.READY_HAND, torch(5));
        for (int i = 0; i < Light.BURN_PERIOD - 1; i++) {
            p.tick();
        }
        assertEquals(5, c.items().get(Slot.READY_HAND).charges());
        p.tick();
        assertEquals(4, c.items().get(Slot.READY_HAND).charges());
        assertEquals(3, p.paletteIndex(), "33 light now");
    }

    @Test
    void aTorchInHandIsDrawnLitAndShrinks() {
        assertEquals(3, ItemCatalog.shownIn(torch(15), Slot.ACTION_HAND).nameVariant());
        assertEquals(1, ItemCatalog.shownIn(torch(2), Slot.READY_HAND).nameVariant());
        assertEquals(0, ItemCatalog.shownIn(torch(0), Slot.READY_HAND).nameVariant(), "burnt out");
        Item packed = torch(15);
        assertSame(packed, ItemCatalog.shownIn(packed, Slot.BACKPACK_1), "unlit away from the hands");
    }

    @Test
    void anIllumuletOnTheNeckGivesLight() {
        Party p = partyOn(1);
        p.members().get(0).place(Slot.NECK, ItemCatalog.item(Item.Category.JUNK, 3));
        assertEquals(4, p.paletteIndex());
    }
}
