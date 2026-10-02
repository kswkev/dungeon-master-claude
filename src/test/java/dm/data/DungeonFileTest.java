package dm.data;

import dm.model.Champion;
import dm.model.ChampionMirror;
import dm.model.Direction;
import dm.model.DungeonMap;
import dm.model.Item;
import dm.model.Slot;
import dm.model.SquareType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonFileTest {

    private static final int WALL = 0x00;
    private static final int FLOOR = 0x20;
    private static final int CLOSED_DOOR = 0x84;
    private static final int HAS_THINGS = 0x10;

    /**
     * 4 wide, 3 high: a corridor along y=1 with a closed door at x=2. Wall
     * (1,0) has a champion mirror on its south side; the champion's text is
     * on the floor square (1,1) below it.
     */
    private static final int[][] MAP0 = {
            {WALL, WALL, WALL},
            {WALL | HAS_THINGS, FLOOR | HAS_THINGS, WALL},
            {WALL, CLOSED_DOOR | HAS_THINGS, WALL},
            {WALL, FLOOR, WALL},
    };

    static final String HALK = "HALK\nTHE BARBARIAN\n\nM\nAAFKACOOAAAA\nCIDHCLBOCOCGDA\nEAEAAAAAAAAAAAAA";

    /** 2x2 second map, to exercise multiple map definitions. */
    private static final int[][] MAP1 = {
            {FLOOR, FLOOR},
            {FLOOR, WALL},
    };

    @Test
    void parsesBigEndian() throws IOException {
        assertSampleDungeon(DungeonFile.parse(build(true)), "big-endian");
    }

    @Test
    void parsesLittleEndian() throws IOException {
        assertSampleDungeon(DungeonFile.parse(build(false)), "little-endian");
    }

    @Test
    void parsesCompressed() throws IOException {
        byte[] packed = compress(build(true));
        assertTrue(Decompressor.isCompressed(packed));
        DungeonFile f = DungeonFile.parse(packed);
        assertTrue(f.format().startsWith("compressed"), f.format());
        assertSampleDungeon(f, "big-endian");
    }

    @Test
    void rejectsGarbage() {
        byte[] junk = new byte[200];
        for (int i = 0; i < junk.length; i++) {
            junk[i] = (byte) 0xFF;
        }
        IOException e = assertThrows(IOException.class, () -> DungeonFile.parse(junk));
        assertTrue(e.getMessage().contains("Not a recognised DUNGEON.DAT"));
    }

    @Test
    void rejectsTruncatedFile() throws IOException {
        byte[] full = build(true);
        byte[] cut = java.util.Arrays.copyOf(full, full.length - 5);
        assertThrows(IOException.class, () -> DungeonFile.parse(cut));
    }

    @Test
    void reportsMissingFile() {
        IOException e = assertThrows(IOException.class, () -> DungeonFile.load(Path.of("no/such/DUNGEON.DAT")));
        assertTrue(e.getMessage().startsWith("DUNGEON.DAT not found at"));
    }

    private static void assertSampleDungeon(DungeonFile f, String expectedFormat) {
        assertTrue(f.format().contains(expectedFormat), f.format());
        assertEquals(2, f.maps().size());
        DungeonMap m = f.firstLevel();
        assertEquals(4, m.width());
        assertEquals(3, m.height());
        assertEquals(SquareType.WALL, m.get(0, 1).type());
        assertEquals(SquareType.CORRIDOR, m.get(1, 1).type());
        assertEquals(SquareType.DOOR, m.get(2, 1).type());
        assertTrue(!m.get(2, 1).isDoorOpen());
        assertEquals(SquareType.CORRIDOR, m.get(3, 1).type());
        assertEquals(1, f.startX());
        assertEquals(1, f.startY());
        assertEquals(Direction.EAST, f.startFacing());
        assertEquals(1, f.maps().get(1).level());
        assertEquals(SquareType.WALL, f.maps().get(1).get(1, 1).type());

        assertEquals(1, m.mirrors().size());
        ChampionMirror mirror = m.mirrorAt(1, 0, Direction.SOUTH);
        assertNotNull(mirror);
        Champion halk = mirror.champion();
        assertEquals("HALK", halk.name());
        assertEquals("THE BARBARIAN", halk.title());
        assertEquals(5, halk.portrait());
        assertEquals(90, halk.maxHealth());
        assertEquals(Item.Category.WEAPON, halk.items().get(Slot.ACTION_HAND).category());
        assertEquals("CLUB", halk.items().get(Slot.ACTION_HAND).name());
        assertTrue(f.maps().get(1).mirrors().isEmpty());

        // The door's thing selects door set 1, which map 0 sets to style 1 (wood).
        assertEquals(1, m.doorStyle(2, 1));
        assertEquals(0, m.doorStyle(1, 1), "not a door");
    }

    /** Thing ids: bits 14-15 cell, 10-13 type, 0-9 index. */
    private static int thingId(int cell, int type, int index) {
        return (cell << 14) | (type << 10) | index;
    }

    /** Builds a minimal but structurally complete DUNGEON.DAT. */
    private static byte[] build(boolean bigEndian) {
        Out out = new Out(bigEndian);
        int map0Bytes = 4 * 3;
        int map1Bytes = 2 * 2;
        int rawBytes = map0Bytes + map1Bytes + 3; // trailing per-map tables
        int[] text = TextDecoderTest.encode(HALK);
        int squareFirstThings = 3;
        int[] thingCounts = new int[16];
        thingCounts[0] = 1;  // one door (4 bytes)
        thingCounts[2] = 1;  // the champion's text (4 bytes)
        thingCounts[3] = 1;  // the portrait sensor (8 bytes)
        thingCounts[4] = 2;  // two creature groups (16 bytes each)
        thingCounts[5] = 1;  // the champion's club (4 bytes)

        out.u16(0x1234);            // ornament seed
        out.u16(rawBytes);
        out.u8(2);                  // map count
        out.u8(0);
        out.u16(text.length);
        out.u16(1 | (1 << 5) | (1 << 10)); // start (1,1) facing east
        out.u16(squareFirstThings);
        for (int c : thingCounts) {
            out.u16(c);
        }

        mapDef(out, 0, 4, 3, 0, 0x1000);   // door set 1 = style 1
        mapDef(out, map0Bytes, 2, 2, 1, 0);

        // Squares with things before each column: map 0 has two in column 1 and one in column 2.
        int[] columnCounts = {0, 0, 2, 3, 3, 3};
        for (int c : columnCounts) {
            out.u16(c);
        }
        out.u16(thingId(2, 3, 0));  // wall (1,0): sensor on its south side
        out.u16(thingId(0, 2, 0));  // floor (1,1): the text
        out.u16(thingId(0, 0, 0));  // door (2,1): its door record
        for (int w : text) {
            out.u16(w);
        }
        out.u16(0xFFFE);            // door: end of list, uses door set 1
        out.u16(1);
        out.u16(0xFFFE);            // text: end of list, offset 0 << 3, visible
        out.u16(1);
        out.u16(thingId(2, 5, 0));  // sensor -> club; type 127, portrait 5
        out.u16((5 << 7) | 127);
        out.u16(0);
        out.u16(0);
        out.fill(2 * 16, 0xAA);     // creature groups
        out.u16(0xFFFE);            // club: end of list, weapon type 23
        out.u16(23);

        writeSquares(out, MAP0);
        writeSquares(out, MAP1);
        out.fill(3, 0);             // extra per-map tables
        out.u16(0);                 // checksum
        return out.bytes();
    }

    private static void mapDef(Out out, int offset, int width, int height, int level, int graphicsSets) {
        out.u16(offset);
        out.fill(4, 0);
        out.u8(0);
        out.u8(0);
        out.u16(((height - 1) << 11) | ((width - 1) << 6) | level);
        out.fill(4, 0);
        out.u16(graphicsSets);
    }

    private static void writeSquares(Out out, int[][] columns) {
        for (int[] column : columns) {
            for (int sq : column) {
                out.u8(sq);
            }
        }
    }

    /** Packs with the same code scheme {@link Decompressor} reads, MSB-first, big-endian size. */
    private static byte[] compress(byte[] data) {
        int[] freq = new int[256];
        for (byte b : data) {
            freq[b & 0xFF]++;
        }
        Integer[] order = new Integer[256];
        for (int i = 0; i < 256; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> freq[b] - freq[a]);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(0x81);
        bytes.write(0x04);
        bytes.write(data.length >>> 24);
        bytes.write(data.length >>> 16);
        bytes.write(data.length >>> 8);
        bytes.write(data.length);
        for (int i = 0; i < 20; i++) {
            bytes.write(order[i]);
        }

        StringBuilder bits = new StringBuilder();
        for (byte b : data) {
            int v = b & 0xFF;
            int idx = java.util.Arrays.asList(order).indexOf(v);
            if (idx < 4) {
                bits.append('0').append(bin(idx, 2));
            } else if (idx < 20) {
                bits.append("10").append(bin(idx - 4, 4));
            } else {
                bits.append("11").append(bin(v, 8));
            }
        }
        while (bits.length() % 8 != 0) {
            bits.append('0');
        }
        for (int i = 0; i < bits.length(); i += 8) {
            bytes.write(Integer.parseInt(bits.substring(i, i + 8), 2));
        }
        return bytes.toByteArray();
    }

    private static String bin(int v, int width) {
        StringBuilder s = new StringBuilder(Integer.toBinaryString(v));
        while (s.length() < width) {
            s.insert(0, '0');
        }
        return s.toString();
    }

    private static final class Out {
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        private final boolean bigEndian;

        Out(boolean bigEndian) {
            this.bigEndian = bigEndian;
        }

        void u8(int v) {
            buf.write(v & 0xFF);
        }

        void u16(int v) {
            if (bigEndian) {
                u8(v >>> 8);
                u8(v);
            } else {
                u8(v);
                u8(v >>> 8);
            }
        }

        void fill(int n, int v) {
            for (int i = 0; i < n; i++) {
                u8(v);
            }
        }

        byte[] bytes() {
            return buf.toByteArray();
        }
    }
}
