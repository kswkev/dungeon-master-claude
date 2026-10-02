package dm.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loader for the PC GRAPHICS.DAT. All words are little-endian.
 * <pre>
 *   word        signature 0x8001
 *   word        entry count N
 *   word x N    stored size of each entry
 *   word x N    second size table (equal to the first in this version)
 *   2 words x N width and height of each entry (meaningless for non-images)
 *   entries     back to back
 * </pre>
 * Images are decoded on demand by {@link ImageDecoder}. Non-image entries
 * live at the end of the file: sounds ({@link #sound}), the object names,
 * and data tables.
 */
public final class GraphicsFile {

    public static final int SIGNATURE = 0x8001;

    /** Entry indexes in the PC release, found by dumping the file. */
    public static final int INVENTORY = 17;
    public static final int SLOT_BOX = 33;
    public static final int RESURRECT_PANEL = 40;
    public static final int FIRST_ICON_SHEET = 42;
    public static final int ICON_SHEET_COUNT = 7;
    public static final int PORTRAITS = 26;
    public static final int MIRROR_FRONT = 346;
    /** 32x29 red burst drawn over a champion's status box when they take damage. */
    public static final int DAMAGE_TO_CHAMPION = 16;
    public static final int OBJECT_NAMES = 694;

    /** Sound entries: 8-bit PCM with a big-endian sample count in front. */
    public static final int FIRST_SOUND = 671;
    public static final int LAST_SOUND = 712;
    /** The thud when the party walks into a wall; confirmed by ear against the original. */
    public static final int SOUND_BUMP = 687;
    /** Playback rate of the PC samples. */
    public static final int SOUND_SAMPLE_RATE = 5500;

    private final byte[] data;
    private final int[] offsets;
    private final int[] sizes;
    private final Map<Integer, IndexedImage> cache = new HashMap<>();
    private final List<String> objectNames;

    private GraphicsFile(byte[] data, int[] offsets, int[] sizes) {
        this.data = data;
        this.offsets = offsets;
        this.sizes = sizes;
        this.objectNames = readNames(OBJECT_NAMES);
    }

    public static GraphicsFile load(Path path) throws IOException {
        byte[] data;
        try {
            data = Files.readAllBytes(path);
        } catch (NoSuchFileException e) {
            throw new IOException("GRAPHICS.DAT not found at " + path.toAbsolutePath());
        }
        return parse(data);
    }

    public static GraphicsFile parse(byte[] data) throws IOException {
        if (data.length < 4 || u16(data, 0) != SIGNATURE) {
            throw new IOException("not a PC GRAPHICS.DAT (missing 0x8001 signature)");
        }
        int count = u16(data, 2);
        int base = 4 + count * 8;
        int[] offsets = new int[count];
        int[] sizes = new int[count];
        int pos = base;
        for (int i = 0; i < count; i++) {
            offsets[i] = pos;
            sizes[i] = u16(data, 4 + i * 2);
            pos += sizes[i];
        }
        if (pos != data.length) {
            throw new IOException("entry sizes add up to " + pos + " bytes, file is " + data.length);
        }
        return new GraphicsFile(data, offsets, sizes);
    }

    public int count() {
        return offsets.length;
    }

    /** Decodes entry {@code index} as an image, or returns null if it isn't one. */
    public IndexedImage image(int index) {
        if (index < 0 || index >= offsets.length || sizes[index] == 0) {
            return null;
        }
        return cache.computeIfAbsent(index, i -> {
            try {
                return ImageDecoder.decode(data, offsets[i], sizes[i]);
            } catch (IOException e) {
                return null;
            }
        });
    }

    /**
     * Decodes entry {@code index} as a sound, or returns null if it isn't one.
     * Layout: big-endian sample count, then that many unsigned 8-bit samples
     * (the entry may be padded by a byte or two after them).
     */
    public Sound sound(int index) {
        if (index < FIRST_SOUND || index > LAST_SOUND || index >= offsets.length || sizes[index] < 2) {
            return null;
        }
        int start = offsets[index];
        int count = ((data[start] & 0xFF) << 8) | (data[start + 1] & 0xFF);
        if (count == 0 || 2 + count > sizes[index]) {
            return null;
        }
        byte[] pcm = new byte[count];
        System.arraycopy(data, start + 2, pcm, 0, count);
        return new Sound(pcm, SOUND_SAMPLE_RATE);
    }

    /** Object names indexed by icon number (several icons can share a name). */
    public List<String> objectNames() {
        return objectNames;
    }

    /** Strings stored back to back, each ending with a character whose top bit is set. */
    private List<String> readNames(int index) {
        if (index >= offsets.length) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sizes[index]; i++) {
            int c = data[offsets[index] + i] & 0xFF;
            sb.append((char) (c & 0x7F));
            if ((c & 0x80) != 0) {
                names.add(sb.toString());
                sb.setLength(0);
            }
        }
        return Collections.unmodifiableList(names);
    }

    private static int u16(byte[] d, int p) {
        return (d[p] & 0xFF) | ((d[p + 1] & 0xFF) << 8);
    }
}
