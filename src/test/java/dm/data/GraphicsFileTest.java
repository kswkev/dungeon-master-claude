package dm.data;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GraphicsFileTest {

    /** A 16x4 image: local palette 0-5, then one run of colour 0 for 64 pixels (15, then byte 47 + 17). */
    private static final byte[] SOLID_16X4 = bytes(0x10, 0x00, 0x04, 0x00, 0x01, 0x23, 0x45, 0x8F, 0x2F);

    /**
     * An 18x18 box from the real file: colour 1 border, colour 12 inside.
     * Run of 19 x colour 1, run of 16 x colour 12, copy-above for 272
     * pixels (15, 255, word 0x0110), run of 17 x colour 1.
     */
    private static final byte[] BOX_18X18 = bytes(0x12, 0x00, 0x12, 0x00,
            0x1C, 0x02, 0x34, 0x8F, 0x02, 0x9E, 0xEF, 0xFF, 0x01, 0x10, 0x8F, 0x00);

    @Test
    void decodesLongRun() throws IOException {
        IndexedImage img = ImageDecoder.decode(SOLID_16X4, 0, SOLID_16X4.length);
        assertEquals(16, img.width());
        assertEquals(4, img.height());
        for (byte p : img.pixels()) {
            assertEquals(0, p);
        }
    }

    @Test
    void decodesRunsAndCopyAbove() throws IOException {
        IndexedImage img = ImageDecoder.decode(BOX_18X18, 0, BOX_18X18.length);
        assertEquals(1, img.pixel(0, 0));
        assertEquals(1, img.pixel(17, 0));
        assertEquals(1, img.pixel(0, 9));
        assertEquals(12, img.pixel(1, 1));
        assertEquals(12, img.pixel(16, 16));
        assertEquals(1, img.pixel(17, 9));
        assertEquals(1, img.pixel(0, 17));
        assertEquals(1, img.pixel(17, 17));
    }

    @Test
    void rejectsTruncatedImage() {
        byte[] cut = java.util.Arrays.copyOf(BOX_18X18, BOX_18X18.length - 2);
        assertThrows(IOException.class, () -> ImageDecoder.decode(cut, 0, cut.length));
    }

    @Test
    void readsIndexAndDecodesEntries() throws IOException {
        GraphicsFile g = GraphicsFile.parse(file(List.of(SOLID_16X4, new byte[0], BOX_18X18), new int[][] {
                {16, 4}, {0, 0}, {18, 18}}));
        assertEquals(3, g.count());
        assertEquals(16, g.image(0).width());
        assertNull(g.image(1), "empty entry");
        assertEquals(18, g.image(2).height());
        assertNull(g.image(99), "out of range");
    }

    @Test
    void readsSoundsOnlyFromTheSoundRange() throws IOException {
        // Sample count 3 (big-endian), 3 samples, 1 padding byte.
        byte[] sound = bytes(0x00, 0x03, 0x80, 0xFF, 0x00, 0x7F);
        List<byte[]> entries = new ArrayList<>();
        int[][] dims = new int[GraphicsFile.FIRST_SOUND + 2][];
        for (int i = 0; i < dims.length; i++) {
            entries.add(i == GraphicsFile.FIRST_SOUND || i == 0 ? sound : new byte[0]);
            dims[i] = new int[] {0, 0};
        }
        // A too-short entry: claims 9 samples but holds 2.
        entries.set(GraphicsFile.FIRST_SOUND + 1, bytes(0x00, 0x09, 0x80, 0x80));
        GraphicsFile g = GraphicsFile.parse(file(entries, dims));

        Sound s = g.sound(GraphicsFile.FIRST_SOUND);
        assertEquals(3, s.pcm().length);
        assertEquals((byte) 0xFF, s.pcm()[1]);
        assertEquals(GraphicsFile.SOUND_SAMPLE_RATE, s.sampleRate());
        assertNull(g.sound(0), "entries before the sound range are never sounds");
        assertNull(g.sound(GraphicsFile.FIRST_SOUND + 1), "truncated sample data");
        assertNull(g.sound(GraphicsFile.LAST_SOUND), "beyond the end of this file");
    }

    @Test
    void rejectsWrongSignature() {
        assertThrows(IOException.class, () -> GraphicsFile.parse(bytes(0x00, 0x80, 0, 0)));
    }

    @Test
    void rejectsSizeMismatch() {
        byte[] f = file(List.of(SOLID_16X4), new int[][] {{16, 4}});
        byte[] extra = java.util.Arrays.copyOf(f, f.length + 1);
        assertThrows(IOException.class, () -> GraphicsFile.parse(extra));
    }

    static byte[] file(List<byte[]> entries, int[][] dims) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        word(out, GraphicsFile.SIGNATURE);
        word(out, entries.size());
        for (int pass = 0; pass < 2; pass++) {
            for (byte[] e : entries) {
                word(out, e.length);
            }
        }
        for (int[] d : dims) {
            word(out, d[0]);
            word(out, d[1]);
        }
        for (byte[] e : entries) {
            out.writeBytes(e);
        }
        return out.toByteArray();
    }

    private static void word(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
    }

    private static byte[] bytes(int... values) {
        byte[] b = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            b[i] = (byte) values[i];
        }
        return b;
    }
}
