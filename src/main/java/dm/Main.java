package dm;

import dm.data.DungeonFile;
import dm.model.DungeonMap;
import dm.model.Party;
import dm.ui.GameWindow;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.nio.file.Path;

/**
 * Entry point. The DUNGEON.DAT path comes from the first argument, then the
 * {@code dm.dungeon} system property, then {@code data/DUNGEON.DAT}.
 * Run with {@code -Ddm.debug=true} to print the level map and log every move.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Path path = Path.of(args.length > 0 ? args[0] : System.getProperty("dm.dungeon", "data/DUNGEON.DAT"));
        boolean debug = Boolean.getBoolean("dm.debug");

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

        DungeonMap level = dungeon.firstLevel();
        Party party = new Party(level, dungeon.startX(), dungeon.startY(), dungeon.startFacing());
        if (debug) {
            System.out.printf("Loaded %s: %d maps (%s). Level 1 is %dx%d.%n",
                    path, dungeon.maps().size(), dungeon.format(), level.width(), level.height());
            System.out.print(level.toAscii(party));
        }

        SwingUtilities.invokeLater(() -> new GameWindow(party, debug).setVisible(true));
    }
}
