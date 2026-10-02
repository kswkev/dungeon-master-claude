package dm.data;

import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ZonesTest {

    /** A table with two ranges, 3-4 and 2544-2545, written like entry 696 (little-endian words). */
    private static byte[] table(boolean extraByte) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int[] words = {
                Zones.MAGIC, 2,
                3, 4, 2544, 2545,
                9, 0, 224, 136,     // 3: the viewport's size
                1, 3, 0, 33,        // 4: the viewport, top-left at (0,33)
                7, 4, 66, 133,      // 2544: an object on D0's left cell
                7, 4, 0, 0,         // 2545: a hidden cell
        };
        for (int w : words) {
            out.write(w & 0xFF);
            out.write((w >>> 8) & 0xFF);
        }
        if (extraByte) {
            out.write(0);
        }
        return out.toByteArray();
    }

    @Test
    void looksUpRecordsAcrossRanges() throws IOException {
        byte[] data = table(false);
        Zones z = Zones.parse(data, 0, data.length);
        assertArrayEquals(new int[] {9, 0, 224, 136}, z.record(3));
        assertArrayEquals(new int[] {1, 3, 0, 33}, z.record(4));
        assertArrayEquals(new int[] {7, 4, 66, 133}, z.record(2544));
        assertNull(z.record(5));
        assertNull(z.record(2500));
    }

    @Test
    void pointsAreTypeSevenAndNotHidden() throws IOException {
        byte[] data = table(false);
        Zones z = Zones.parse(data, 0, data.length);
        assertEquals(new Point(66, 133), z.point(2544));
        assertNull(z.point(2545), "(0, 0) marks a cell that isn't shown");
        assertNull(z.point(3), "a size, not a point");
    }

    @Test
    void rejectsBadMagicAndSizeMismatch() {
        byte[] data = table(false);
        byte[] wrongMagic = data.clone();
        wrongMagic[0] = 0;
        assertThrows(IOException.class, () -> Zones.parse(wrongMagic, 0, wrongMagic.length));
        byte[] padded = table(true);
        assertThrows(IOException.class, () -> Zones.parse(padded, 0, padded.length));
    }
}
