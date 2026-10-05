package dm.data;

import dm.model.CreatureType;
import dm.model.Direction;
import dm.model.DungeonMap;
import dm.model.Square;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Loader for the original Dungeon Master DUNGEON.DAT.
 *
 * File layout (all words 16-bit, byte order depends on the platform):
 * <pre>
 *   header (44 bytes)
 *     word  ornament random seed
 *     word  raw map data byte count
 *     byte  map count, byte padding
 *     word  text data word count
 *     word  party start: bits 0-4 X, 5-9 Y, 10-11 facing
 *     word  square-first-thing count
 *     word  thing count x16 (one per thing type)
 *   map definitions (16 bytes each)
 *     word  raw map data offset, 4 unused bytes, byte X offset, byte Y offset
 *     word  bits 11-15 height-1, bits 6-10 width-1, bits 0-5 level
 *     word  ornament counts: bits 8-11 floor, 0-3 wall (plus random-ornament counts)
 *     word  bits 12-15 difficulty (0: always lit), 4-7 creature type count, 0-3 door ornament count
 *     word  graphics sets: bits 12-15 door set 1, 8-11 door set 0
 *   column cumulative square-thing counts (word per map column, all maps)
 *   square first things (words), text data (words), thing data per type
 *   raw map data: one byte per square, column-major, then per-map extra tables
 * </pre>
 * Squares with bit 4 set own a thing list; see {@link Thing}. From the things
 * we build the Hall of Champions mirrors ({@link ChampionFinder}), floor
 * sensors such as pressure plates ({@link FloorSensorFinder}), wall sensors
 * such as switches and keyholes ({@link WallSensorFinder}), the loose objects
 * on floors and in alcoves ({@link FloorItemFinder}) and door styles.
 */
public final class DungeonFile {

    /** Byte size of one thing record per thing type, as stored in the file. */
    private static final int[] THING_SIZES = {4, 6, 4, 8, 16, 4, 4, 4, 4, 8, 4, 0, 0, 0, 8, 4};
    private static final int MAX_MAPS = 64;

    private final List<DungeonMap> maps;
    private final int startX;
    private final int startY;
    private final Direction startFacing;
    private final String format;

    private DungeonFile(List<DungeonMap> maps, int startX, int startY, Direction startFacing, String format) {
        this.maps = maps;
        this.startX = startX;
        this.startY = startY;
        this.startFacing = startFacing;
        this.format = format;
    }

    public static DungeonFile load(Path path) throws IOException {
        byte[] data;
        try {
            data = Files.readAllBytes(path);
        } catch (NoSuchFileException e) {
            throw new IOException("DUNGEON.DAT not found at " + path.toAbsolutePath());
        }
        return parse(data);
    }

    /** Tries every supported encoding (raw/compressed, big/little endian) and returns the first that is consistent. */
    public static DungeonFile parse(byte[] data) throws IOException {
        List<byte[]> bodies = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        if (Decompressor.isCompressed(data)) {
            for (byte[] unpacked : Decompressor.candidates(data)) {
                bodies.add(unpacked);
                labels.add("compressed");
            }
        }
        bodies.add(data);
        labels.add("uncompressed");

        StringBuilder errors = new StringBuilder();
        for (int i = 0; i < bodies.size(); i++) {
            for (boolean bigEndian : new boolean[] {true, false}) {
                String label = labels.get(i) + ", " + (bigEndian ? "big-endian (Atari ST/Amiga)" : "little-endian (PC)");
                try {
                    return parse(bodies.get(i), bigEndian, label);
                } catch (IOException e) {
                    errors.append("\n  ").append(label).append(": ").append(e.getMessage());
                }
            }
        }
        throw new IOException("Not a recognised DUNGEON.DAT:" + errors);
    }

    private static DungeonFile parse(byte[] data, boolean bigEndian, String label) throws IOException {
        ByteReader r = new ByteReader(data, bigEndian);

        int ornamentSeed = r.u16();
        int rawMapBytes = r.u16();
        int mapCount = r.u8();
        r.u8();
        int textWords = r.u16();
        int start = r.u16();
        int squareFirstThings = r.u16();
        int[] thingCounts = new int[THING_SIZES.length];
        long thingBytes = 0;
        for (int t = 0; t < THING_SIZES.length; t++) {
            thingCounts[t] = r.u16();
            thingBytes += (long) thingCounts[t] * THING_SIZES[t];
        }

        if (mapCount < 1 || mapCount > MAX_MAPS) {
            throw new IOException("implausible map count " + mapCount);
        }

        int[] offsets = new int[mapCount];
        int[] widths = new int[mapCount];
        int[] heights = new int[mapCount];
        int[] levels = new int[mapCount];
        int[] graphicsSets = new int[mapCount];
        int[] ornamentCounts = new int[mapCount];
        int[] otherCounts = new int[mapCount];
        int[] offsetX = new int[mapCount];
        int[] offsetY = new int[mapCount];
        int columnCount = 0;
        for (int m = 0; m < mapCount; m++) {
            offsets[m] = r.u16();
            r.skip(4);
            offsetX[m] = r.u8(); // where the map sits in dungeon-wide coordinates; stairs line up through these
            offsetY[m] = r.u8();
            int dims = r.u16();
            ornamentCounts[m] = r.u16();
            otherCounts[m] = r.u16();
            graphicsSets[m] = r.u16();
            widths[m] = ((dims >>> 6) & 0x1F) + 1;
            heights[m] = (dims >>> 11) + 1;
            levels[m] = dims & 0x3F;
            if (offsets[m] + widths[m] * heights[m] > rawMapBytes) {
                throw new IOException("map " + m + " (" + widths[m] + "x" + heights[m] + " at " + offsets[m]
                        + ") overflows raw map data of " + rawMapBytes + " bytes");
            }
            columnCount += widths[m];
        }

        int startX = start & 0x1F;
        int startY = (start >>> 5) & 0x1F;
        Direction facing = Direction.fromIndex(start >>> 10);
        if (startX >= widths[0] || startY >= heights[0]) {
            throw new IOException("party start (" + startX + "," + startY + ") outside first map");
        }

        long sectionEnd = r.position() + columnCount * 2L + squareFirstThings * 2L + textWords * 2L + thingBytes;
        if (sectionEnd + rawMapBytes > data.length) {
            throw new IOException("raw map data (" + rawMapBytes + " bytes at " + sectionEnd + ") runs past end of file");
        }
        int[] columnFirstThing = words(r, columnCount);
        int[] firstThings = words(r, squareFirstThings);
        int[] text = words(r, textWords);
        int[][] things = new int[THING_SIZES.length][];
        for (int t = 0; t < THING_SIZES.length; t++) {
            things[t] = words(r, thingCounts[t] * THING_SIZES[t] / 2);
        }
        int rawStart = r.position();

        Thing.Store store = new Thing.Store(things, THING_SIZES, firstThings);
        ItemReader items = new ItemReader(store, text);
        List<DungeonMap> maps = new ArrayList<>(mapCount);
        int columnBase = 0;
        for (int m = 0; m < mapCount; m++) {
            Square[][] squares = new Square[widths[m]][heights[m]];
            int base = rawStart + offsets[m];
            for (int x = 0; x < widths[m]; x++) {
                for (int y = 0; y < heights[m]; y++) {
                    squares[x][y] = new Square(data[base + x * heights[m] + y] & 0xFF);
                }
            }
            List<List<Thing>>[] squareThings = store.listsFor(squares, columnFirstThing, columnBase);
            DungeonMap map = new DungeonMap(levels[m], squares, ChampionFinder.find(squareThings, text, items),
                    doorStyles(squares, squareThings, graphicsSets[m]));
            map.setOffset(offsetX[m], offsetY[m]);
            map.setDifficulty(otherCounts[m] >>> 12);
            OrnamentLists lists = OrnamentLists.read(data, base + widths[m] * heights[m],
                    rawStart + rawMapBytes, ornamentCounts[m], otherCounts[m]);
            FloorSensorFinder.find(squares, squareThings, lists.floor()).forEach(map::addSensor);
            FloorItemFinder.place(map, squares, squareThings, items);
            WallSensorFinder.find(squares, squareThings, lists.wall()).forEach(map::addWallSensor);
            TeleporterFinder.find(squares, squareThings).forEach(map::addTeleporter);
            GroupFinder.find(squareThings, store, items).forEach(map::addGroup);
            map.setCreatureTypes(Arrays.stream(lists.creatures()).mapToObj(CreatureType::of)
                    .filter(Objects::nonNull).toList());
            map.initSensors();
            map.setDecorations(new DecorationFinder(lists, ornamentSeed, m, text)
                    .find(squares, squareThings));
            if (squareThings.length > 0 && !squareThings[0].isEmpty()) {
                map.setEndgameTexts(squareThings[0].get(0).stream().filter(t -> t.type() == Thing.TEXT)
                        .map(t -> TextDecoder.decode(text, t.words()[1] >>> 3)).toList());
            }
            maps.add(map);
            columnBase += widths[m];
        }

        if (!maps.get(0).isPassable(startX, startY)) {
            throw new IOException("party start (" + startX + "," + startY + ") is not walkable");
        }
        return new DungeonFile(List.copyOf(maps), startX, startY, facing, label);
    }

    /**
     * Each map offers two door designs: set 0 in bits 8-11 of its graphics-set
     * word, set 1 in bits 12-15. Bit 0 of a door square's door thing picks
     * which set that door uses, bit 5 whether it opens upward
     * ({@link DungeonMap#DOOR_VERTICAL}), and bits 7 and 8 whether magic or
     * blows can break it.
     */
    private static int[][] doorStyles(Square[][] squares, List<List<Thing>>[] squareThings, int graphicsSets) {
        int[][] styles = new int[squares.length][];
        for (int x = 0; x < squares.length; x++) {
            styles[x] = new int[squares[x].length];
            for (int y = 0; y < squares[x].length; y++) {
                for (Thing t : squareThings[x].get(y)) {
                    if (t.type() == Thing.DOOR) {
                        boolean secondSet = (t.words()[1] & 1) != 0;
                        styles[x][y] = (graphicsSets >>> (secondSet ? 12 : 8)) & 3;
                        if ((t.words()[1] & 0x20) != 0) {
                            styles[x][y] |= DungeonMap.DOOR_VERTICAL;
                        }
                        if ((t.words()[1] & 0x80) != 0) {
                            styles[x][y] |= DungeonMap.DOOR_MAGIC_DESTRUCTIBLE;
                        }
                        if ((t.words()[1] & 0x100) != 0) {
                            styles[x][y] |= DungeonMap.DOOR_MELEE_DESTRUCTIBLE;
                        }
                        break;
                    }
                }
            }
        }
        return styles;
    }


    private static int[] words(ByteReader r, int count) throws IOException {
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            out[i] = r.u16();
        }
        return out;
    }

    public List<DungeonMap> maps() {
        return maps;
    }

    /** The first level of the game (map 0, the Hall of Champions level). */
    public DungeonMap firstLevel() {
        return maps.get(0);
    }

    public int startX() {
        return startX;
    }

    public int startY() {
        return startY;
    }

    public Direction startFacing() {
        return startFacing;
    }

    /** Human-readable description of the detected encoding. */
    public String format() {
        return format;
    }
}
