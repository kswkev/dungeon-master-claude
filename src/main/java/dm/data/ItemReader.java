package dm.data;

import dm.model.Item;
import dm.model.ItemCatalog;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns object things into {@link Item}s, with what only the whole file
 * knows: a chest's contents (its second word links a thing list of its own)
 * and a scroll's text (its second word's bits 0-9 index a text thing, whose
 * data word holds the text's offset in bits 3-15).
 */
final class ItemReader {

    /** Reads items without the rest of the file: chests come out empty and scrolls blank. */
    static final ItemReader BARE = new ItemReader(null, null);

    private final Thing.Store store;
    private final int[] text;

    ItemReader(Thing.Store store, int[] text) {
        this.store = store;
        this.text = text;
    }

    /**
     * The item an object thing stands for, or null for other things.
     * Weapons keep their charges in bits 10-13 (a torch's light power), junk
     * in bits 14-15 (a waterskin's draughts), as the DM Encyclopaedia
     * documents. A potion's power (bits 0-7) is kept as its charges.
     */
    Item toItem(Thing t) {
        return toItem(t, 0);
    }

    private Item toItem(Thing t, int depth) {
        int w = t.words()[1];
        return switch (t.type()) {
            case Thing.WEAPON -> ItemCatalog.item(Item.Category.WEAPON, w & 0x7F, (w >>> 10) & 15);
            case Thing.ARMOUR -> ItemCatalog.item(Item.Category.ARMOUR, w & 0x7F);
            case Thing.SCROLL -> ItemCatalog.item(Item.Category.SCROLL, 0).withText(scrollText(w & 0x3FF));
            case Thing.POTION -> ItemCatalog.item(Item.Category.POTION, (w >>> 8) & 0x7F, w & 0xFF); // power
            case Thing.CONTAINER -> ItemCatalog.item(Item.Category.CONTAINER, 0).withContents(contents(w, depth));
            case Thing.JUNK -> ItemCatalog.item(Item.Category.JUNK, w & 0x7F, (w >>> 14) & 3);
            default -> null;
        };
    }

    private String scrollText(int textThing) {
        if (store == null || text == null) {
            return null;
        }
        int[] rec = store.record(Thing.TEXT, textThing);
        return rec == null ? null : TextDecoder.decode(text, rec[1] >>> 3);
    }

    private List<Item> contents(int firstThing, int depth) {
        List<Item> out = new ArrayList<>();
        if (store == null || depth > 0) { // DM never puts a chest in a chest
            return out;
        }
        for (Thing inside : store.chain(firstThing)) {
            Item item = toItem(inside, depth + 1);
            if (item != null && out.size() < Item.CHEST_CELLS) {
                out.add(item);
            }
        }
        return out;
    }
}
