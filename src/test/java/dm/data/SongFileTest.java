package dm.data;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** SONG.DAT: the play order and the clips' 4-bit changes with a byte escape. */
class SongFileTest {

    @Test
    void clipsAreChangesFromZeroWithAByteEscape() {
        // 4 samples: +1, -2, then 8 escapes a byte change of +0x40, then +7; a pad nibble ends it.
        byte[] raw = {0, 4, (byte) 0x1E, (byte) 0x84, (byte) 0x07};
        assertArrayEquals(new byte[] {(byte) 129, (byte) 127, (byte) 191, (byte) 198}, SongFile.decode(raw));
    }

    @Test
    void thePlayOrderEndsAtTheLoopMarker() throws Exception {
        byte[] raw = {1, 0, 2, 0, 2, 0, 1, (byte) 0x80};
        assertEquals(List.of(1, 2, 2), SongFile.order(raw));
    }

    @Test
    void theUsersSongDecodesToItsClips() throws Exception {
        Path song = Path.of("data/SONG.DAT");
        Assumptions.assumeTrue(Files.exists(song), "needs the user's SONG.DAT");
        Sound s = SongFile.load(song);
        assertEquals(476_494, s.pcm().length, "19 clips in the play order");
        assertEquals(SongFile.SAMPLE_RATE, s.sampleRate());
    }
}
