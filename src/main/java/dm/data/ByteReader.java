package dm.data;

import java.io.IOException;

/** Sequential reader over a byte array with a selectable word byte order. */
final class ByteReader {

    private final byte[] data;
    private final boolean bigEndian;
    private int pos;

    ByteReader(byte[] data, boolean bigEndian) {
        this.data = data;
        this.bigEndian = bigEndian;
    }

    int position() {
        return pos;
    }

    int length() {
        return data.length;
    }

    int u8() throws IOException {
        require(1);
        return data[pos++] & 0xFF;
    }

    int u16() throws IOException {
        require(2);
        int a = data[pos] & 0xFF;
        int b = data[pos + 1] & 0xFF;
        pos += 2;
        return bigEndian ? (a << 8) | b : (b << 8) | a;
    }

    void skip(long n) throws IOException {
        if (n < 0 || pos + n > data.length) {
            throw new IOException("section of " + n + " bytes at offset " + pos + " runs past end of file (" + data.length + " bytes)");
        }
        pos += (int) n;
    }

    private void require(int n) throws IOException {
        if (pos + n > data.length) {
            throw new IOException("unexpected end of file at offset " + pos);
        }
    }
}
