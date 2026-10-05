package dm.data;

import dm.model.Item;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemReaderTest {

    private static final int[] SIZES = {4, 6, 4, 8, 16, 4, 4, 4, 4, 8, 4, 0, 0, 0, 8, 4};
    private static final int END = 0xFFFE;

    private static int id(int type, int index) {
        return (type << 10) | index;
    }

    /** A store with a chest (holding an apple and a dagger), a scroll and its text thing. */
    private static Thing.Store store() {
        int[][] records = new int[16][];
        for (int t = 0; t < 16; t++) {
            records[t] = new int[0];
        }
        records[Thing.TEXT] = new int[]{END, 0}; // text at offset 0
        records[Thing.SCROLL] = new int[]{END, 0}; // text thing 0
        records[Thing.CONTAINER] = new int[]{END, id(Thing.JUNK, 0), 0, 0};
        records[Thing.JUNK] = new int[]{END, 29}; // an apple
        records[Thing.WEAPON] = new int[]{END, 8}; // a dagger
        records[Thing.JUNK][0] = id(Thing.WEAPON, 0);
        return new Thing.Store(records, SIZES, new int[0]);
    }

    @Test
    void aChestHoldsItsThingList() {
        Thing.Store store = store();
        Item chest = new ItemReader(store, new int[0]).toItem(new Thing(Thing.CONTAINER, 0, 0,
                store.record(Thing.CONTAINER, 0)));
        assertEquals(List.of("APPLE", "DAGGER"), chest.contents().stream().map(Item::name).toList());
        assertEquals(50 + chest.contents().get(0).weight() + chest.contents().get(1).weight(), chest.weight());
    }

    @Test
    void aScrollReadsItsText() {
        Thing.Store store = store();
        Item scroll = new ItemReader(store, TextDecoderTest.encode("NEW LIVES\nFOR\nOLD BONES"))
                .toItem(new Thing(Thing.SCROLL, 0, 0, store.record(Thing.SCROLL, 0)));
        assertEquals("NEW LIVES\nFOR\nOLD BONES", scroll.text());
    }

    @Test
    void withoutTheFileChestsAreEmptyAndScrollsBlank() {
        Thing.Store store = store();
        assertTrue(ItemReader.BARE.toItem(new Thing(Thing.CONTAINER, 0, 0, store.record(Thing.CONTAINER, 0)))
                .contents().isEmpty());
        assertNull(ItemReader.BARE.toItem(new Thing(Thing.SCROLL, 0, 0, store.record(Thing.SCROLL, 0))).text());
    }
}
