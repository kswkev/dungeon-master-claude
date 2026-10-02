package dm;

import dm.data.DungeonFile;
import dm.data.GraphicsFile;
import dm.data.Sound;
import dm.model.Champion;
import dm.model.ChampionMirror;
import dm.model.DungeonMap;
import dm.model.Party;
import dm.ui.Art;
import dm.ui.GameWindow;
import dm.ui.SoundPlayer;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.nio.file.Path;

/**
 * Entry point. The DUNGEON.DAT path comes from the first argument, then the
 * {@code dm.dungeon} system property, then {@code data/DUNGEON.DAT}.
 * GRAPHICS.DAT is read from the same folder unless {@code dm.graphics} says
 * otherwise; without it the game runs with placeholder art.
 * Run with {@code -Ddm.debug=true} to print the level map and champions and log every move,
 * or {@code -Ddm.soundtest=<index>|all} to play GRAPHICS.DAT sounds and exit.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Path path = Path.of(args.length > 0 ? args[0] : System.getProperty("dm.dungeon", "data/DUNGEON.DAT"));
        boolean debug = Boolean.getBoolean("dm.debug");
        Path graphicsPath = Path.of(System.getProperty("dm.graphics",
                path.resolveSibling("GRAPHICS.DAT").toString()));

        String soundTest = System.getProperty("dm.soundtest");
        if (soundTest != null) {
            soundTest(graphicsPath, soundTest);
            return;
        }

        DungeonFile dungeon;
        try {
            dungeon = DungeonFile.load(path);
        } catch (Exception e) {
            String message = "Could not load the dungeon.\n\n" + e.getMessage()
                    + "\n\nSee data/README.md for where to put DUNGEON.DAT.";
            System.err.println(message);
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, message, "Dungeon Master", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            });
            return;
        }

        Art art = Art.load(graphicsPath);

        DungeonMap level = dungeon.firstLevel();
        Party party = new Party(level, dungeon.startX(), dungeon.startY(), dungeon.startFacing());
        if (debug) {
            System.out.printf("Loaded %s: %d maps (%s). Level 1 is %dx%d.%n",
                    path, dungeon.maps().size(), dungeon.format(), level.width(), level.height());
            System.out.print(level.toAscii(party));
            printChampions(level);
        }

        SoundPlayer sounds = SoundPlayer.javaSound();
        SwingUtilities.invokeLater(() -> new GameWindow(party, art, sounds, debug).setVisible(true));
    }

    /**
     * {@code -Ddm.soundtest=<index>} plays one GRAPHICS.DAT sound and exits;
     * {@code -Ddm.soundtest=all} plays every sound in turn, printing its index.
     */
    private static void soundTest(Path graphicsPath, String which) {
        GraphicsFile gfx;
        try {
            gfx = GraphicsFile.load(graphicsPath);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return;
        }
        int from = which.equals("all") ? GraphicsFile.FIRST_SOUND : Integer.parseInt(which);
        int to = which.equals("all") ? GraphicsFile.LAST_SOUND : from;
        SoundPlayer player = SoundPlayer.javaSound();
        for (int i = from; i <= to; i++) {
            Sound s = gfx.sound(i);
            if (s == null) {
                System.out.println(i + ": not a sound");
                continue;
            }
            System.out.printf("%d: %.2fs%s%n", i, s.seconds(), i == GraphicsFile.SOUND_BUMP ? "  <- bump" : "");
            player.play(s);
            try {
                Thread.sleep((long) (s.seconds() * 1000) + 700);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        System.exit(0);
    }

    private static void printChampions(DungeonMap level) {
        System.out.println(level.mirrors().size() + " champions:");
        for (ChampionMirror m : level.mirrors()) {
            Champion c = m.champion();
            System.out.printf("  mirror (%d,%d) %-5s %-30s health %d stamina %d mana %d, %d items%n",
                    m.x(), m.y(), m.side(), c.fullName(), c.maxHealth(), c.maxStamina(), c.maxMana(),
                    c.items().size());
        }
    }
}
