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

    /**
     * Where a {@code width} x {@code height} picture goes in zone {@code id}:
     * DM's layout engine (F0635 GET_COORD, as ported for the PC version by
     * ScummVM's DisplayMan::getCoord). It walks up the zone's parents,
     * anchoring the picture by the zone's type (0 centre, 1 top-left,
     * 2 top-right, 3 bottom-right, 4 bottom-left, 5 top-centre, 6 right-middle,
     * 7 bottom-centre, 8 left-middle; 10-18 the same, relative to a
     * grandparent's size) and clipping it to the size zones (type 9) above it.
     *
     * Returns {x, y, w, h, srcX, srcY} in viewport coordinates for the zones
     * under the viewport: the visible part of the picture and where in the
     * picture it starts. Null if the zone doesn't exist or nothing is visible.
     */
    public int[] coord(int id, int width, int height) {
        int[] rec = record(id);
        if (rec == null) {
            return null;
        }
        int[] parentXYZ = {0, 0, 20000, 20000};
        int recType = rec[0];
        int xOffset;
        int yOffset;
        if (recType <= 8) {
            xOffset = rec[2];
            yOffset = rec[3];
        } else {
            if (recType == 9) {
                return null;
            }
            recType -= 10;
            xOffset = 0;
            yOffset = 0;
        }
        boolean flag = false;
        int[] current = rec;
        while (current[1] != 0) {
            int[] parent = record(current[1]);
            if (parent == null) {
                break;
            }
            if (current[0] >= 10 && current[0] <= 18) {
                int data1 = parent[2];
                int data2 = parent[3];
                int[] grand = record(parent[1]);
                if (grand == null) {
                    break;
                }
                switch (parent[0]) { // C fall-throughs written out
                    case 0 -> {
                        data2 -= (grand[3] + 1) >> 1;
                        data1 -= (grand[2] + 1) >> 1;
                    }
                    case 5 -> data1 -= (grand[2] + 1) >> 1;
                    case 3 -> {
                        data2 -= grand[3] - 1;
                        data1 -= grand[2] - 1;
                    }
                    case 2 -> data1 -= grand[2] - 1;
                    case 6 -> {
                        data1 -= grand[2] - 1;
                        data2 -= (grand[3] + 1) >> 1;
                    }
                    case 8 -> data2 -= (grand[3] + 1) >> 1;
                    case 7 -> {
                        data1 -= (grand[2] + 1) >> 1;
                        data2 -= grand[3] - 1;
                    }
                    case 4 -> data2 -= grand[3] - 1;
                    case 1 -> { }
                    default -> {
                        return null;
                    }
                }
                if ((parentXYZ[0] += data1) < data1) {
                    parentXYZ[0] = data1;
                }
                if (parentXYZ[0] + parentXYZ[2] > grand[2] + data1) {
                    parentXYZ[2] = grand[2] - parentXYZ[0] + data1;
                }
                if ((parentXYZ[1] += data2) < data2) {
                    parentXYZ[1] = data2;
                }
                if (parentXYZ[1] + parentXYZ[3] > grand[3] + data2) {
                    parentXYZ[3] = grand[3] - parentXYZ[1] + data2;
                }
                switch (current[0]) {
                    case 10 -> {
                        data2 += (grand[3] + 1) >> 1;
                        data1 += (grand[2] + 1) >> 1;
                    }
                    case 15 -> data1 += (grand[2] + 1) >> 1;
                    case 13 -> {
                        data2 += grand[3] - 1;
                        data1 += grand[2] - 1;
                    }
                    case 12 -> data1 += grand[2] - 1;
                    case 16 -> {
                        data1 += grand[2] - 1;
                        data2 += (grand[3] + 1) >> 1;
                    }
                    case 18 -> data2 += (grand[3] + 1) >> 1;
                    case 17 -> {
                        data1 += (grand[2] + 1) >> 1;
                        data2 += grand[3] - 1;
                    }
                    case 14 -> data2 += grand[3] - 1;
                    case 11 -> { }
                    default -> {
                        return null;
                    }
                }
                xOffset += data1 + current[2];
                yOffset += data2 + current[3];
                current = grand;
            } else {
                int data1 = parent[2];
                int data2 = parent[3];
                if (parent[0] == 1) {
                    xOffset += data1;
                    yOffset += data2;
                    parentXYZ[0] += data1;
                    parentXYZ[1] += data2;
                } else if (parent[0] == 9) {
                    switch (current[0]) {
                        case 0 -> {
                            data1 = current[2] - ((data1 + 1) >> 1);
                            data2 = current[3] - ((data2 + 1) >> 1);
                        }
                        case 1 -> {
                            data1 = current[2];
                            data2 = current[3];
                        }
                        case 2 -> {
                            data1 = current[2] - (data1 - 1);
                            data2 = current[3];
                        }
                        case 3 -> {
                            data1 = current[2] - (data1 - 1);
                            data2 = current[3] - (data2 - 1);
                        }
                        case 4 -> {
                            data1 = current[2];
                            data2 = current[3] - (data2 - 1);
                        }
                        case 5 -> {
                            data1 = current[2] - ((data1 + 1) >> 1);
                            data2 = current[3];
                        }
                        case 6 -> {
                            data1 = current[2] - (data1 - 1);
                            data2 = current[3] - ((data2 + 1) >> 1);
                        }
                        case 7 -> {
                            data1 = current[2] - ((data1 + 1) >> 1);
                            data2 = current[3] - (data2 - 1);
                        }
                        case 8 -> {
                            data1 = current[2];
                            data2 = current[3] - ((data2 + 1) >> 1);
                        }
                        default -> { }
                    }
                    if (flag) {
                        flag = false;
                        xOffset += data1;
                        yOffset += data2;
                        parentXYZ[0] += data1;
                        parentXYZ[1] += data2;
                    }
                    if (parentXYZ[0] < data1) {
                        parentXYZ[0] = data1;
                    }
                    if (parentXYZ[0] + parentXYZ[2] > parent[2] + data1) {
                        parentXYZ[2] = parent[2] - parentXYZ[0] + data1;
                    }
                    if (parentXYZ[1] < data2) {
                        parentXYZ[1] = data2;
                    }
                    if (parentXYZ[1] + parentXYZ[3] > parent[3] + data2) {
                        parentXYZ[3] = parent[3] - parentXYZ[1] + data2;
                    }
                } else if (parent[0] <= 8) {
                    flag = true;
                }
                current = parent;
            }
        }
        int x;
        int y;
        switch (recType) {
            case 0 -> {
                x = xOffset - ((width + 1) >> 1);
                y = yOffset - ((height + 1) >> 1);
            }
            case 1 -> {
                x = xOffset;
                y = yOffset;
            }
            case 2 -> {
                x = xOffset - (width - 1);
                y = yOffset;
            }
            case 3 -> {
                x = xOffset - (width - 1);
                y = yOffset - (height - 1);
            }
            case 4 -> {
                x = xOffset;
                y = yOffset - (height - 1);
            }
            case 5 -> {
                x = xOffset - ((width + 1) >> 1);
                y = yOffset;
            }
            case 6 -> {
                x = xOffset - (width - 1);
                y = yOffset - ((height + 1) >> 1);
            }
            case 7 -> {
                x = xOffset - ((width + 1) >> 1);
                y = yOffset - (height - 1);
            }
            case 8 -> {
                x = xOffset;
                y = yOffset - ((height + 1) >> 1);
            }
            default -> {
                return null;
            }
        }
        int w;
        int h;
        int srcX = 0;
        int srcY = 0;
        int clipX = parentXYZ[0] - x;
        int clipY = parentXYZ[1] - y;
        if (clipX <= 0) {
            w = Math.min(width, parentXYZ[2] + clipX);
        } else {
            srcX = clipX;
            x = parentXYZ[0];
            w = Math.min(width - clipX, parentXYZ[2]);
        }
        if (clipY <= 0) {
            h = Math.min(height, parentXYZ[3] + clipY);
        } else {
            srcY = clipY;
            y = parentXYZ[1];
            h = Math.min(height - clipY, parentXYZ[3]);
        }
        if (w <= 0 || h <= 0) {
            return null;
        }
        return new int[] {x, y, w, h, srcX, srcY};
    }

    private static int u16(byte[] d, int p) {
        return (d[p] & 0xFF) | ((d[p + 1] & 0xFF) << 8);
    }
}
