package dm.data;

import dm.model.Champion;
import dm.model.Party;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Saved games: {@link #SLOTS} numbered slots, one file each in a folder
 * ({@code saves/} unless {@code -Ddm.saves} says otherwise).
 *
 * A file is the text {@link #MAGIC}, a format version, a {@link Header} for
 * the slot list, then the whole {@link Party}, written with Java
 * serialization. The party reaches everything that changes in play: every
 * map with its doors, sensors, piles, pits and teleporters, and the champions.
 * That includes data parsed from the user's own DUNGEON.DAT, so saves stay
 * out of the repository like the game files.
 */
public final class SaveGames {

    public static final int SLOTS = 4;
    static final String MAGIC = "DMREMAKE-SAVE";
    /** Bump when the saved classes change incompatibly; older saves are then refused. */
    static final int VERSION = 3; // 2: creature groups (Sprint 14); 3: creature AI, wounds, poison (Sprint 15)

    /** What a slot button shows without loading the whole game. */
    public record Header(int level, long gameTime, long savedAt, List<String> champions) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    /** A save may only bring back the game's own classes and the JDK's (lists, maps, Random...). */
    private static final ObjectInputFilter ONLY_GAME_CLASSES =
            ObjectInputFilter.Config.createFilter("dm.**;java.**;!*");

    private final Path folder;

    public SaveGames(Path folder) {
        this.folder = folder;
    }

    /** The folder from {@code -Ddm.saves}, or {@code saves/}. */
    public static SaveGames standard() {
        return new SaveGames(Path.of(System.getProperty("dm.saves", "saves")));
    }

    public Path file(int slot) {
        if (slot < 1 || slot > SLOTS) {
            throw new IllegalArgumentException("no save slot " + slot);
        }
        return folder.resolve("slot" + slot + ".dmsave");
    }

    /** Slot {@code slot}'s header, or null if it is empty or unreadable. */
    public Header header(int slot) {
        Path f = file(slot);
        if (!Files.isRegularFile(f)) {
            return null;
        }
        try (ObjectInputStream in = open(f)) {
            return (Header) in.readObject();
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            return null;
        }
    }

    /**
     * Saves {@code party} in slot {@code slot}. It is written to a temporary
     * file first, so a failed save leaves the slot's previous game intact.
     */
    public void save(int slot, Party party) throws IOException {
        Files.createDirectories(folder);
        Path target = file(slot);
        Path temp = Files.createTempFile(folder, "slot" + slot, ".tmp");
        try {
            try (ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(temp)))) {
                out.writeUTF(MAGIC);
                out.writeInt(VERSION);
                out.writeObject(header(party));
                out.writeObject(party);
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** The game saved in slot {@code slot}; throws with a reason if there is none or it can't be read. */
    public Party load(int slot) throws IOException {
        Path f = file(slot);
        if (!Files.isRegularFile(f)) {
            throw new IOException("slot " + slot + " is empty");
        }
        try (ObjectInputStream in = open(f)) {
            in.readObject(); // the header
            return (Party) in.readObject();
        } catch (ClassNotFoundException | ClassCastException e) {
            throw new IOException("slot " + slot + " isn't a saved game of this version", e);
        }
    }

    private static Header header(Party party) {
        return new Header(party.level(), party.time(), System.currentTimeMillis(),
                party.members().stream().map(Champion::name).toList());
    }

    /** Opens a save and checks its magic text and version. */
    private static ObjectInputStream open(Path f) throws IOException {
        ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(f)));
        in.setObjectInputFilter(ONLY_GAME_CLASSES);
        try {
            if (!MAGIC.equals(in.readUTF())) {
                throw new IOException(f.getFileName() + " isn't a saved game");
            }
            int version = in.readInt();
            if (version != VERSION) {
                throw new IOException(f.getFileName() + " was saved by another version (" + version + ")");
            }
            return in;
        } catch (IOException e) {
            in.close();
            throw e;
        }
    }
}
