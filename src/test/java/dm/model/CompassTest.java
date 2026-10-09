package dm.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** #63: the compass's icon points the party's way, as DM's F033 adds the direction to icon 0. */
class CompassTest {

    @Test
    void theCompassPointsThePartysWay() {
        Item compass = ItemCatalog.item(Item.Category.JUNK, ItemCatalog.COMPASS);
        assertEquals("COMPASS", compass.name());
        for (Direction d : Direction.values()) {
            Item shown = ItemCatalog.pointing(compass, d);
            assertEquals(d.ordinal(), shown.nameVariant(), "icons 0-3 are N, E, S, W");
            assertEquals(compass.type(), shown.type());
        }
    }

    @Test
    void otherItemsAreUnchanged() {
        Item torch = ItemCatalog.item(Item.Category.WEAPON, 2);
        assertSame(torch, ItemCatalog.pointing(torch, Direction.WEST));
    }
}
