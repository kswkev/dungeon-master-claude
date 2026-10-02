package dm.data;

import java.io.IOException;

/**
 * Decoder for the compressed images in the PC GRAPHICS.DAT.
 *
 * Layout: width and height (little-endian words), then a stream of 4-bit
 * nibbles, high nibble first:
 * <pre>
 *   6 nibbles   local palette: the image's 6 most common colours
 *   commands    nibble c, where bits 0-2 pick the pixel source:
 *                 0-5  local palette colour
 *                 6    copy the pixel one row above
 *                 7    literal colour in the next nibble
 *               bit 3 clear: a single pixel
 *               bit 3 set:   a run, whose length follows:
 *                 nibble n &lt; 15         n + 2
 *                 15, byte b &lt; 255      b + 17
 *                 15, 255, 16-bit word   word
 * </pre>
 */
final class ImageDecoder {

    private final byte[] data;
    private final int start;
    private final int end;
    private int nibblePos;

    private ImageDecoder(byte[] data, int offset, int length) {
        this.data = data;
        this.start = offset;
        this.end = offset + length;
    }

    static IndexedImage decode(byte[] data, int offset, int length) throws IOException {
        if (length < 4) {
            throw new IOException("image entry too short");
        }
        int width = (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
        int height = (data[offset + 2] & 0xFF) | ((data[offset + 3] & 0xFF) << 8);
        return new ImageDecoder(data, offset + 4, length - 4).run(width, height);
    }

    private IndexedImage run(int width, int height) throws IOException {
        int[] local = new int[6];
        for (int i = 0; i < local.length; i++) {
            local[i] = nibble();
        }
        int total = width * height;
        byte[] px = new byte[total];
        int p = 0;
        while (p < total) {
            int command = nibble();
            int source = command & 7;
            int color = source < 6 ? local[source] : source == 7 ? nibble() : -1;
            int count = (command & 8) != 0 ? runLength() : 1;
            if (p + count > total) {
                throw new IOException("image data overruns " + width + "x" + height);
            }
            for (int i = 0; i < count; i++, p++) {
                if (color >= 0) {
                    px[p] = (byte) color;
                } else if (p >= width) {
                    px[p] = px[p - width];
                } else {
                    throw new IOException("copy-above command on the first row");
                }
            }
        }
        return new IndexedImage(width, height, px);
    }

    private int runLength() throws IOException {
        int n = nibble();
        if (n < 15) {
            return n + 2;
        }
        int b = (nibble() << 4) | nibble();
        if (b < 255) {
            return b + 17;
        }
        return (nibble() << 12) | (nibble() << 8) | (nibble() << 4) | nibble();
    }

    private int nibble() throws IOException {
        int index = start + (nibblePos >> 1);
        if (index >= end) {
            throw new IOException("image data ends early");
        }
        int b = data[index] & 0xFF;
        int value = (nibblePos & 1) == 0 ? b >>> 4 : b & 15;
        nibblePos++;
        return value;
    }
}
