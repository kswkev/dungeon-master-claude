package dm.data;

import dm.model.Champion;
import dm.model.ChampionMirror;
import dm.model.Direction;
import dm.model.Item;
import dm.model.ItemCatalog;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the Hall of Champions mirrors in a map's thing lists.
 *
 * A mirror is a sensor of type 127 on a wall square; the sensor's data is
 * the portrait number and its cell is the side of the wall it hangs on. The
 * champion's starting items are the objects on the same wall side. The
 * champion's text (name, stats...) is on the floor square the mirror faces.
 */
final class ChampionFinder {

    private static final int SENSOR_CHAMPION_PORTRAIT = 127;

    private ChampionFinder() {
    }

    static List<ChampionMirror> find(List<List<Thing>>[] squareThings, int[] text) {
        List<ChampionMirror> mirrors = new ArrayList<>();
        for (int x = 0; x < squareThings.length; x++) {
            for (int y = 0; y < squareThings[x].size(); y++) {
                for (Thing t : squareThings[x].get(y)) {
                    if (t.type() != Thing.SENSOR || (t.words()[1] & 0x7F) != SENSOR_CHAMPION_PORTRAIT) {
                        continue;
                    }
                    Direction side = Direction.fromIndex(t.cell());
                    String champText = textOn(squareThings, x + side.dx, y + side.dy, text);
                    if (champText == null) {
                        continue;
                    }
                    Champion champion;
                    try {
                        champion = Champion.parse(champText, t.words()[1] >>> 7);
                    } catch (IllegalArgumentException e) {
                        continue; // not champion text; ignore this sensor
                    }
                    for (Thing item : squareThings[x].get(y)) {
                        if (item.cell() == t.cell()) {
                            Item i = toItem(item);
                            if (i != null) {
                                champion.addStartingItem(i);
                            }
                        }
                    }
                    mirrors.add(new ChampionMirror(x, y, side, champion));
                }
            }
        }
        return mirrors;
    }

    private static String textOn(List<List<Thing>>[] squareThings, int x, int y, int[] text) {
        if (x < 0 || x >= squareThings.length || y < 0 || y >= squareThings[x].size()) {
            return null;
        }
        for (Thing t : squareThings[x].get(y)) {
            if (t.type() == Thing.TEXT) {
                return TextDecoder.decode(text, t.words()[1] >>> 3);
            }
        }
        return null;
    }

    /**
     * The item an object thing stands for. Weapons keep their charges in
     * bits 10-13 (a torch's light power), junk in bits 14-15 (a waterskin's
     * draughts), as the DM Encyclopaedia documents. A potion's power (bits
     * 0-7) is kept as its charges.
     */
    static Item toItem(Thing t) {
        int w = t.words()[1];
        return switch (t.type()) {
            case Thing.WEAPON -> ItemCatalog.item(Item.Category.WEAPON, w & 0x7F, (w >>> 10) & 15);
            case Thing.ARMOUR -> ItemCatalog.item(Item.Category.ARMOUR, w & 0x7F);
            case Thing.SCROLL -> ItemCatalog.item(Item.Category.SCROLL, 0);
            case Thing.POTION -> ItemCatalog.item(Item.Category.POTION, (w >>> 8) & 0x7F, w & 0xFF); // power
            case Thing.CONTAINER -> ItemCatalog.item(Item.Category.CONTAINER, 0);
            case Thing.JUNK -> ItemCatalog.item(Item.Category.JUNK, w & 0x7F, (w >>> 14) & 3);
            default -> null;
        };
    }
}
