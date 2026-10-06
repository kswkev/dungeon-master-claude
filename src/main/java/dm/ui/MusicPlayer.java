package dm.ui;

import dm.data.Sound;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;

/** Plays the entrance's music on a loop (SONG.DAT). {@link #silent()} for tests or when there is no audio. */
public interface MusicPlayer {

    /** Starts {@code song} looping from its beginning, at full volume; a song already playing goes on. */
    void loop(Sound song);

    /** Fades the music out over {@code millis}, then stops it. */
    void fadeOut(long millis);

    void stop();

    static MusicPlayer silent() {
        return new MusicPlayer() {
            @Override
            public void loop(Sound song) {
            }

            @Override
            public void fadeOut(long millis) {
            }

            @Override
            public void stop() {
            }
        };
    }

    /** Plays through Java Sound; silent for the session if no audio line is available. */
    static MusicPlayer javaSound() {
        return new JavaMusicPlayer();
    }

    final class JavaMusicPlayer implements MusicPlayer {
        /** The quietest a fade goes before it stops. */
        private static final float SILENT_DB = -40f;
        private static final int FADE_STEPS = 20;

        private Clip clip;
        private Sound playing;
        private boolean disabled;
        /** Bumped by every loop and stop, so an older fade gives up. */
        private int generation;

        private JavaMusicPlayer() {
        }

        @Override
        public synchronized void loop(Sound song) {
            if (song == null || disabled) {
                return;
            }
            generation++;
            try {
                if (clip == null || playing != song) {
                    if (clip != null) {
                        clip.close();
                    }
                    clip = AudioSystem.getClip();
                    clip.open(new AudioFormat(song.sampleRate(), 8, 1, false, false), song.pcm(), 0,
                            song.pcm().length);
                    playing = song;
                }
                gain(0f);
                if (!clip.isRunning()) {
                    clip.setFramePosition(0);
                    clip.loop(Clip.LOOP_CONTINUOUSLY);
                }
            } catch (Exception | LinkageError e) {
                disabled = true;
                System.err.println("Warning: music disabled (" + e.getMessage() + ")");
            }
        }

        @Override
        public synchronized void fadeOut(long millis) {
            if (clip == null || !clip.isRunning()) {
                return;
            }
            int fade = ++generation;
            Thread t = new Thread(() -> {
                for (int step = 1; step <= FADE_STEPS; step++) {
                    try {
                        Thread.sleep(Math.max(1, millis / FADE_STEPS));
                    } catch (InterruptedException e) {
                        return;
                    }
                    synchronized (this) {
                        if (generation != fade) {
                            return; // a new loop or a stop took over
                        }
                        gain(SILENT_DB * step / FADE_STEPS);
                    }
                }
                synchronized (this) {
                    if (generation == fade) {
                        clip.stop();
                    }
                }
            }, "music fade");
            t.setDaemon(true);
            t.start();
        }

        @Override
        public synchronized void stop() {
            generation++;
            if (clip != null) {
                clip.stop();
            }
        }

        private void gain(float db) {
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl control = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                control.setValue(Math.max(control.getMinimum(), db));
            }
        }
    }
}
