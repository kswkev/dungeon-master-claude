package dm;

import dm.data.DungeonFile;
import dm.data.GraphicsFile;
import dm.data.SongFile;
import dm.data.Sound;
import dm.model.Champion;
import dm.model.ChampionMirror;
import dm.model.Decorations;
import dm.model.Direction;
import dm.model.DungeonMap;
import dm.model.Item;
import dm.model.ItemCatalog;
import dm.model.Party;
import dm.ui.Art;
import dm.ui.GameWindow;
import dm.ui.SoundPlayer;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
        Party party = debugParty(dungeon);
        if (debug) {
            System.out.printf("Loaded %s: %d maps (%s). Level 1 is %dx%d.%n",
                    path, dungeon.maps().size(), dungeon.format(), level.width(), level.height());
            System.out.print(level.toAscii(party));
            printChampions(level);
            printDecorations(level);
        }

        SoundPlayer sounds = SoundPlayer.javaSound();
        // The testing options go straight into the game; otherwise it starts at DM's entrance.
        boolean testing = System.getProperty("dm.start") != null || System.getProperty("dm.recruit") != null
                || System.getProperty("dm.give") != null;
        Sound song = song(path);
        SwingUtilities.invokeLater(() -> new GameWindow(party, art, sounds, debug, () -> newGame(path), !testing, song)
                .setVisible(true));
    }

    /** The PC version's entrance music, SONG.DAT beside DUNGEON.DAT, or null when it isn't there. */
    static Sound song(Path dungeonPath) {
        Path songPath = dungeonPath.resolveSibling("SONG.DAT");
        if (!Files.exists(songPath)) {
            return null;
        }
        try {
            return SongFile.load(songPath);
        } catch (Exception e) {
            System.err.println("Warning: could not read " + songPath + " (" + e.getMessage() + "), no music");
            return null;
        }
    }

    /** THE END's NEW GAME (not in DM): the dungeon read afresh, the party at its start with nobody recruited. */
    static Party newGame(Path path) {
        try {
            DungeonFile fresh = DungeonFile.load(path);
            return new Party(fresh.maps(), 0, fresh.startX(), fresh.startY(), fresh.startFacing());
        } catch (Exception e) {
            throw new IllegalStateException("Could not reload the dungeon: " + e.getMessage(), e);
        }
    }

    /**
     * The party at the dungeon's start, or as the testing options (not in
     * DM) set it up:
     * <ul>
     *   <li>{@code -Ddm.start=level,x,y,dir}: starts on that level (1-based) and square, facing N, E, S or W;</li>
     *   <li>{@code -Ddm.recruit=all} (or a number): recruits the first Level 1 mirrors' champions;</li>
     *   <li>{@code -Ddm.give=weapon:45,junk:51,potion:6:120}: gives the first champion those items
     *       (category:type[:charges]), placed as starting items are.</li>
     * </ul>
     * A malformed option is reported and ignored.
     */
    static Party debugParty(DungeonFile dungeon) {
        Party party = new Party(dungeon.maps(), 0, dungeon.startX(), dungeon.startY(), dungeon.startFacing());
        String start = System.getProperty("dm.start");
        if (start != null) {
            try {
                String[] p = start.split(",");
                int index = Integer.parseInt(p[0].trim()) - 1;
                Direction facing = Direction.fromIndex("NESW".indexOf(Character.toUpperCase(p[3].trim().charAt(0))));
                party = new Party(dungeon.maps(), index, Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()),
                        facing);
            } catch (RuntimeException e) {
                System.err.println("Ignoring -Ddm.start=" + start + " (expected level,x,y,N|E|S|W)");
            }
        }
        String recruit = System.getProperty("dm.recruit");
        if (recruit != null) {
            List<ChampionMirror> mirrors = dungeon.maps().get(0).mirrors();
            int count = recruit.equals("all") ? Party.MAX_MEMBERS : Integer.parseInt(recruit.trim());
            for (int i = 0; i < Math.min(count, mirrors.size()); i++) {
                party.recruit(mirrors.get(i));
            }
        }
        String give = System.getProperty("dm.give");
        if (give != null && !party.members().isEmpty()) {
            for (String spec : give.split(",")) {
                try {
                    String[] p = spec.trim().split(":");
                    Item item = ItemCatalog.item(Item.Category.valueOf(p[0].toUpperCase()), Integer.parseInt(p[1]),
                            p.length > 2 ? Integer.parseInt(p[2]) : 0);
                    party.members().get(0).addStartingItem(item);
                } catch (RuntimeException e) {
                    System.err.println("Ignoring -Ddm.give item " + spec + " (expected category:type[:charges])");
                }
            }
        }
        return party;
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

    /** Every decoration on a wall side that faces an open square, plus floor and door decorations. */
    private static void printDecorations(DungeonMap level) {
        Decorations d = level.decorations();
        System.out.println("Decorations (global ornament index):");
        for (int x = 0; x < level.width(); x++) {
            for (int y = 0; y < level.height(); y++) {
                for (Direction side : Direction.values()) {
                    String text = d.inscription(x, y, side);
                    boolean faces = !level.get(x + side.dx, y + side.dy).looksSolid();
                    if (faces && (d.wall(x, y, side) >= 0 || text != null)) {
                        System.out.printf("  wall (%d,%d) %-5s %d%s%n", x, y, side, d.wall(x, y, side),
                                text == null ? "" : " \"" + text.replace('\n', ' ') + "\"");
                    }
                }
                if (level.floorOrnament(x, y) >= 0) {
                    System.out.printf("  floor (%d,%d) %d%n", x, y, level.floorOrnament(x, y));
                }
                if (d.door(x, y) >= 0 || d.doorButton(x, y)) {
                    System.out.printf("  door (%d,%d) %d%s%n", x, y, d.door(x, y), d.doorButton(x, y) ? " with button" : "");
                }
            }
        }
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
