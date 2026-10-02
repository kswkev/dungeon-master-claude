package dm.data;

import dm.model.Square;

import java.util.ArrayList;
import java.util.List;

/**
 * One record from a square's thing list.
 *
 * A thing id is a 16-bit word: bits 14-15 cell (the side or corner of the
 * square), bits 10-13 type, bits 0-9 index into that type's records. Each
 * record's first word links to the next thing, ending with 0xFFFE.
 *
 * @param words the whole record, including the link word
 */
record Thing(int type, int index, int cell, int[] words) {

    static final int DOOR = 0;
    static final int TEXT = 2;
    static final int SENSOR = 3;
    static final int WEAPON = 5;
    static final int ARMOUR = 6;
    static final int SCROLL = 7;
    static final int POTION = 8;
    static final int CONTAINER = 9;
    static final int JUNK = 10;

    private static final int END_OF_LIST = 0xFFFE;
    private static final int NONE = 0xFFFF;
    private static final int MAX_LIST_LENGTH = 1024;

    /** All thing records plus the square-to-first-thing table. */
    static final class Store {
        private final int[][] records;
        private final int[] sizes;
        private final int[] firstThings;

        Store(int[][] records, int[] sizes, int[] firstThings) {
            this.records = records;
            this.sizes = sizes;
            this.firstThings = firstThings;
        }

        /**
         * Thing lists for each square of one map, indexed [x][y]. Squares with
         * things take entries from the first-things table in column-major
         * order, starting at that column's cumulative count.
         */
        @SuppressWarnings("unchecked")
        List<List<Thing>>[] listsFor(Square[][] squares, int[] columnFirstThing, int columnBase) {
            List<List<Thing>>[] out = new List[squares.length];
            for (int x = 0; x < squares.length; x++) {
                out[x] = new ArrayList<>();
                int next = columnFirstThing[columnBase + x];
                for (int y = 0; y < squares[x].length; y++) {
                    if (squares[x][y].hasThings() && next < firstThings.length) {
                        out[x].add(chain(firstThings[next++]));
                    } else {
                        out[x].add(List.of());
                    }
                }
            }
            return out;
        }

        private List<Thing> chain(int id) {
            List<Thing> list = new ArrayList<>();
            while (id != END_OF_LIST && id != NONE && list.size() < MAX_LIST_LENGTH) {
                int type = (id >>> 10) & 15;
                int index = id & 0x3FF;
                int words = sizes[type] / 2;
                if (words == 0 || (index + 1) * words > records[type].length) {
                    break; // corrupt link; keep what we have
                }
                int[] rec = new int[words];
                System.arraycopy(records[type], index * words, rec, 0, words);
                list.add(new Thing(type, index, id >>> 14, rec));
                id = rec[0];
            }
            return list;
        }
    }
}
