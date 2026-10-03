package dm.data;

import dm.model.Square;
import dm.model.SquareType;
import dm.model.Teleporter;

import java.util.ArrayList;
import java.util.List;

/**
 * Decodes the teleporter things on teleporter squares. Record words after
 * the link, as in ReDMCSB's TELEPORTER:
 * <pre>
 *   word 1  bits 0-4 target X, bits 5-9 target Y, bits 10-11 rotation,
 *           bit 12 absolute rotation, bits 13-14 scope (1 objects,
 *           2 creatures and the party), bit 15 audible
 *   word 2  bits 8-15 target map index
 * </pre>
 */
final class TeleporterFinder {

    private TeleporterFinder() {
    }

    static List<Teleporter> find(Square[][] squares, List<List<Thing>>[] squareThings) {
        List<Teleporter> teleporters = new ArrayList<>();
        for (int x = 0; x < squares.length; x++) {
            for (int y = 0; y < squares[x].length; y++) {
                if (squares[x][y].type() != SquareType.TELEPORTER) {
                    continue;
                }
                for (Thing t : squareThings[x].get(y)) {
                    if (t.type() == Thing.TELEPORTER && t.words().length >= 3) {
                        teleporters.add(decode(x, y, t.words()));
                        break;
                    }
                }
            }
        }
        return teleporters;
    }

    static Teleporter decode(int x, int y, int[] words) {
        int w = words[1];
        return new Teleporter(x, y, words[2] >>> 8, w & 0x1F, (w >>> 5) & 0x1F, (w >>> 10) & 3,
                (w & 0x1000) != 0, (w >>> 13) & 3, (w & 0x8000) != 0);
    }
}
