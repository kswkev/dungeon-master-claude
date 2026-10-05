package dm.data;

import dm.model.Champion;
import dm.model.ChampionMirror;
import dm.model.Direction;
import dm.model.Item;

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
        return find(squareThings, text, ItemReader.BARE);
    }

    static List<ChampionMirror> find(List<List<Thing>>[] squareThings, int[] text, ItemReader items) {
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
                            Item i = items.toItem(item);
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

    /** The item an object thing stands for, without chest contents or scroll text ({@link ItemReader}). */
    static Item toItem(Thing t) {
        return ItemReader.BARE.toItem(t);
    }
}
