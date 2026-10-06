package dm.data;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * SONG.DAT, the PC version's entrance music, as found by reverse-checking
 * the user's file. It has GRAPHICS.DAT's layout ({@link GraphicsFile}): 10
 * entries.
 * <ul>
 *   <li>Entry 0 is the play order: little-endian words naming the clips
 *       (1-9) in turn, ended by a word with bit 15 set whose low bits are the
 *       position (1-based) to loop back to: 1,2,3,2,3,2,3,2,4,5,6,2,3,2,4,5,7,8,9
 *       then 0x8001.</li>
 *   <li>Entries 1-9 are clips: a big-endian sample count, then the samples as
 *       changes from the previous one (starting from 0), 4 bits each, high
 *       nibble first: -7 to +7, or 8 followed by a signed byte for a bigger
 *       change. The samples are signed 8-bit; the stream may end with a pad
 *       nibble.</li>
 * </ul>
 */
public final class SongFile {

    /** The clips' playback rate; GRAPHICS.DAT's sound effects play at {@link GraphicsFile#SOUND_SAMPLE_RATE}. */
    public static final int SAMPLE_RATE = 11025;

    private SongFile() {
    }

    /** The whole song, every clip in order, as unsigned 8-bit PCM; the play order loops back to its start. */
    public static Sound load(Path path) throws IOException {
        GraphicsFile file = GraphicsFile.load(path);
        List<Integer> order = order(file.raw(0));
        List<byte[]> clips = new ArrayList<>();
        int length = 0;
        byte[][] decoded = new byte[file.count()][];
        for (int clip : order) {
            if (decoded[clip] == null) {
                decoded[clip] = decode(file.raw(clip));
            }
            clips.add(decoded[clip]);
            length += decoded[clip].length;
        }
        byte[] pcm = new byte[length];
        int at = 0;
        for (byte[] clip : clips) {
            System.arraycopy(clip, 0, pcm, at, clip.length);
            at += clip.length;
        }
        return new Sound(pcm, SAMPLE_RATE);
    }

    /** Entry 0's play order, up to the loop marker (a word with bit 15 set). */
    static List<Integer> order(byte[] raw) throws IOException {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i + 1 < raw.length; i += 2) {
            int word = (raw[i] & 0xFF) | (raw[i + 1] & 0xFF) << 8;
            if ((word & 0x8000) != 0) {
                return order;
            }
            order.add(word);
        }
        throw new IOException("SONG.DAT's play order has no end");
    }

    /** One clip, decoded to unsigned 8-bit PCM (silence 128). */
    static byte[] decode(byte[] raw) {
        int count = (raw[0] & 0xFF) << 8 | (raw[1] & 0xFF);
        byte[] pcm = new byte[count];
        int nibble = 4; // in nibbles from the start: after the count
        int value = 0;
        for (int i = 0; i < count; i++) {
            int n = nibble(raw, nibble++);
            if (n == 8) {
                value += (byte) (nibble(raw, nibble) << 4 | nibble(raw, nibble + 1));
                nibble += 2;
            } else {
                value += n >= 8 ? n - 16 : n;
            }
            pcm[i] = (byte) (Math.max(-128, Math.min(127, value)) + 128);
        }
        return pcm;
    }

    private static int nibble(byte[] raw, int index) {
        int b = index / 2 < raw.length ? raw[index / 2] & 0xFF : 0;
        return index % 2 == 0 ? b >> 4 : b & 15;
    }
}
