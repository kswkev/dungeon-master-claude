package dm.data;

import java.awt.Point;
import java.io.IOException;

/**
 * DM's screen layout ("zones"), GRAPHICS.DAT entry {@link GraphicsFile#ZONES}.
 *
 * Layout: magic word 0xFC0D, a range count n, then n (first, last) id pairs,
 * then one 4-word record for every id in those ranges, in range order. A
 * record is (type, parent, a, b), all signed little-endian words:
 * <ul>
 *   <li>type 9: a size, a x b;</li>
 *   <li>types 1-4: a rectangle of the parent's size, anchored at (a, b) by its
 *       top-left, top-right, bottom-right or bottom-left corner;</li>
 *   <li>type 7: a point (a, b) relative to the parent; the parents used here
 *       lead to the dungeon viewport, so these points are viewport coordinates.</li>
 * </ul>
 * For example zone 7 places the 224x136 viewport (zone 3) at (0, 33).
 * Ids 2500-2547 are where objects lie on the floor and 2900-2947 where they
 * fly through the air: 4 cells for each of 12 view squares, see
 * {@link #FLOOR_OBJECTS}.
 */
public final class Zones {

    public static final int MAGIC = 0xFC0D;
    /**
     * First floor-object point. Id = FLOOR_OBJECTS + viewSquare * 4 + viewCell,
     * view squares in the order D3 centre, left, right, far left, far right,
     * D2 centre, left, right, D1 centre, left, right, D0 centre; view cells
     * back-left, back-right, front-right, front-left. Hidden cells are (0, 0).
     */
    public static final int FLOOR_OBJECTS = 2500;
    /** First in-flight object point, laid out like {@link #FLOOR_OBJECTS}. */
    public static final int FLYING_OBJECTS = 2900;

    private final int[][] ranges;
    private final int[] records;

    private Zones(int[][] ranges, int[] records) {
        this.ranges = ranges;
        this.records = records;
    }

    static Zones parse(byte[] data, int offset, int size) throws IOException {
        if (size < 4 || u16(data, offset) != MAGIC) {
            throw new IOException("zone table missing its 0xFC0D magic word");
        }
        int n = u16(data, offset + 2);
        int[][] ranges = new int[n][];
        int ids = 0;
        for (int r = 0; r < n; r++) {
            ranges[r] = new int[] {u16(data, offset + 4 + r * 4), u16(data, offset + 6 + r * 4)};
            ids += ranges[r][1] - ranges[r][0] + 1;
        }
        int start = offset + 4 + n * 4;
        if (start + ids * 8 != offset + size) {
            throw new IOException("zone table has " + ids + " ids but " + (offset + size - start) + " record bytes");
        }
        int[] records = new int[ids * 4];
        for (int i = 0; i < records.length; i++) {
            records[i] = (short) u16(data, start + i * 2);
        }
        return new Zones(ranges, records);
    }

    /** The (type, parent, a, b) record for zone {@code id}, or null if there is none. */
    public int[] record(int id) {
        int index = 0;
        for (int[] r : ranges) {
            if (id >= r[0] && id <= r[1]) {
                int at = (index + id - r[0]) * 4;
                return new int[] {records[at], records[at + 1], records[at + 2], records[at + 3]};
            }
            index += r[1] - r[0] + 1;
        }
        return null;
    }

    /** A point zone (type 0 or 7), or null if {@code id} isn't one or is a hidden (0, 0) cell. */
    public Point point(int id) {
        int[] r = record(id);
        if (r == null || (r[0] != 7 && r[0] != 0) || (r[2] == 0 && r[3] == 0)) {
            return null;
        }
        return new Point(r[2], r[3]);
    }

    private static int u16(byte[] d, int p) {
        return (d[p] & 0xFF) | ((d[p + 1] & 0xFF) << 8);
    }
}
