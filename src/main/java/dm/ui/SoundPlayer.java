package dm.ui;

import dm.data.Sound;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import java.util.IdentityHashMap;
import java.util.Map;

/** Plays sound effects. {@link #silent()} for tests or when there is no audio. */
public interface SoundPlayer {

    void play(Sound sound);

    /** Plays {@code sound}, quieter when {@code soft} (DM's soft sounds, heard from further away, #37). */
    default void play(Sound sound, boolean soft) {
        play(sound);
    }

    static SoundPlayer silent() {
        return sound -> { };
    }

    /** Plays through Java Sound; falls back to silence for the session if no audio line is available. */
    static SoundPlayer javaSound() {
        return new JavaSoundPlayer();
    }

    final class JavaSoundPlayer implements SoundPlayer {
        private final Map<Sound, Clip> clips = new IdentityHashMap<>();
        private boolean disabled;

        private JavaSoundPlayer() {
        }

        /** A soft sound plays at half the volume. */
        static final float SOFT_GAIN_DB = -6f;

        @Override
        public void play(Sound sound) {
            play(sound, false);
        }

        @Override
        public synchronized void play(Sound sound, boolean soft) {
            if (sound == null || disabled) {
                return;
            }
            try {
                Clip clip = clips.get(sound);
                if (clip == null) {
                    AudioFormat format = new AudioFormat(sound.sampleRate(), 8, 1, false, false);
                    clip = AudioSystem.getClip();
                    clip.open(format, sound.pcm(), 0, sound.pcm().length);
                    clips.put(sound, clip);
                }
                if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    ((FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN)).setValue(soft ? SOFT_GAIN_DB : 0f);
                }
                // Restart from the top so repeated bumps each get a full thud.
                clip.stop();
                clip.setFramePosition(0);
                clip.start();
            } catch (Exception | LinkageError e) {
                disabled = true;
                System.err.println("Warning: sound disabled (" + e.getMessage() + ")");
            }
        }
    }
}
