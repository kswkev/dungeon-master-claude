package dm.data;

/**
 * A sound effect from GRAPHICS.DAT: unsigned 8-bit mono PCM.
 *
 * @param pcm        samples, 0x80 = silence
 * @param sampleRate playback rate in Hz
 */
public record Sound(byte[] pcm, int sampleRate) {

    public double seconds() {
        return pcm.length / (double) sampleRate;
    }
}
