package dm.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Decompresses the packed DUNGEON.DAT format used by later DM releases.
 *
 * Layout: signature 0x8104, 32-bit unpacked size, a table of the 4 most common
 * bytes, a table of the next 16, then a bit stream of variable-length codes:
 * <pre>
 *   0  + 2 bits  -> one of the 4 most common bytes
 *   10 + 4 bits  -> one of the next 16 common bytes
 *   11 + 8 bits  -> literal byte
 * </pre>
 * The header byte order and bit order differ between ports, so
 * {@link #candidates} returns every plausible decoding. The caller keeps the
 * one that parses as a valid dungeon.
 */
final class Decompressor {

    static final int SIGNATURE = 0x8104;
    private static final int HEADER_SIZE = 2 + 4 + 4 + 16;
    private static final int MAX_UNPACKED = 1 << 20;

    private Decompressor() {
    }

    static boolean isCompressed(byte[] data) {
        if (data.length < HEADER_SIZE) {
            return false;
        }
        int a = data[0] & 0xFF;
        int b = data[1] & 0xFF;
        return (a == 0x81 && b == 0x04) || (a == 0x04 && b == 0x81);
    }

    static List<byte[]> candidates(byte[] data) {
        List<byte[]> out = new ArrayList<>();
        for (boolean bigEndianSize : new boolean[] {true, false}) {
            long size = bigEndianSize
                    ? ((data[2] & 0xFFL) << 24) | ((data[3] & 0xFFL) << 16) | ((data[4] & 0xFFL) << 8) | (data[5] & 0xFFL)
                    : ((data[5] & 0xFFL) << 24) | ((data[4] & 0xFFL) << 16) | ((data[3] & 0xFFL) << 8) | (data[2] & 0xFFL);
            if (size <= 0 || size > MAX_UNPACKED) {
                continue;
            }
            for (boolean msbFirst : new boolean[] {true, false}) {
                byte[] unpacked = decompress(data, (int) size, msbFirst);
                if (unpacked != null) {
                    out.add(unpacked);
                }
            }
        }
        return out;
    }

    /** Returns null if the bit stream runs out before {@code size} bytes are produced. */
    static byte[] decompress(byte[] data, int size, boolean msbFirst) {
        byte[] common4 = new byte[4];
        byte[] common16 = new byte[16];
        System.arraycopy(data, 6, common4, 0, 4);
        System.arraycopy(data, 10, common16, 0, 16);

        BitStream bits = new BitStream(data, HEADER_SIZE, msbFirst);
        byte[] out = new byte[size];
        for (int i = 0; i < size; i++) {
            int prefix = bits.read(1);
            int value;
            if (prefix == 0) {
                int idx = bits.read(2);
                value = idx < 0 ? -1 : common4[idx];
            } else if (prefix == 1) {
                int second = bits.read(1);
                if (second == 0) {
                    int idx = bits.read(4);
                    value = idx < 0 ? -1 : common16[idx];
                } else {
                    value = bits.read(8);
                }
            } else {
                value = -1;
            }
            if (bits.exhausted()) {
                return null;
            }
            out[i] = (byte) value;
        }
        return out;
    }

    private static final class BitStream {
        private final byte[] data;
        private final boolean msbFirst;
        private long bitPos;
        private boolean exhausted;

        BitStream(byte[] data, int start, boolean msbFirst) {
            this.data = data;
            this.msbFirst = msbFirst;
            this.bitPos = (long) start * 8;
        }

        /** Reads n bits, most significant first in the result; returns -1 past end of data. */
        int read(int n) {
            int v = 0;
            for (int i = 0; i < n; i++) {
                int byteIndex = (int) (bitPos >>> 3);
                if (byteIndex >= data.length) {
                    exhausted = true;
                    return -1;
                }
                int bit = (int) (bitPos & 7);
                int shift = msbFirst ? 7 - bit : bit;
                v = (v << 1) | ((data[byteIndex] >>> shift) & 1);
                bitPos++;
            }
            return v;
        }

        boolean exhausted() {
            return exhausted;
        }
    }
}
