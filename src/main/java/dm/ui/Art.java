package dm.ui;

import dm.data.GraphicsFile;
import dm.data.IndexedImage;
import dm.data.Sound;
import dm.model.Direction;
import dm.model.Item;
import dm.model.ItemCatalog;

import java.awt.Color;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Original artwork and sounds from GRAPHICS.DAT, images converted to Java images on demand.
 * When the file is missing every accessor returns null and callers draw
 * placeholders instead, so the game still runs.
 */
public final class Art {

    /** DM's dungeon palette. Index 10 doubles as the transparent colour in sprites. */
    public static final Color[] PALETTE = {
            new Color(0x000000), new Color(0x666666), new Color(0x888888), new Color(0x662200),
            new Color(0x00CCCC), new Color(0x884400), new Color(0x008800), new Color(0x00CC00),
            new Color(0xFF0000), new Color(0xFFAA00), new Color(0xCC8866), new Color(0xFFFF00),
            new Color(0x444444), new Color(0xAAAAAA), new Color(0x0000FF), new Color(0xFFFFFF)};
    public static final int TRANSPARENT = 10;

    private static final int PORTRAIT_W = 32;
    private static final int PORTRAIT_H = 29;
    private static final int PORTRAITS_PER_ROW = 8;
    private static final int ICON_SIZE = 16;
    private static final int ICONS_PER_SHEET = 32;
    private static final int ICON_BACKGROUND = 12;

    private final GraphicsFile gfx;
    private final Map<Integer, BufferedImage> cache = new HashMap<>();
    private final Map<String, Integer> iconIndexes = new HashMap<>();
    private final Map<String, BufferedImage> iconSprites = new HashMap<>();

    private Art(GraphicsFile gfx) {
        this.gfx = gfx;
    }

    public static Art none() {
        return new Art(null);
    }

    /** Loads GRAPHICS.DAT, or falls back to placeholders with a warning on stderr. */
    public static Art load(Path path) {
        try {
            return new Art(GraphicsFile.load(path));
        } catch (Exception e) {
            System.err.println("Warning: " + e.getMessage() + " - using placeholder graphics.");
            return none();
        }
    }

    public static Art of(GraphicsFile gfx) {
        return new Art(gfx);
    }

    public boolean available() {
        return gfx != null;
    }

    private DmFont font;

    /** DM's font from GRAPHICS.DAT (spell symbols included), or null without it. */
    public DmFont font() {
        if (font == null && gfx != null) {
            font = DmFont.from(gfx);
        }
        return font;
    }

    /** A point zone from DM's screen layout (viewport coordinates), or null. */
    public Point zone(int id) {
        return gfx == null || gfx.zones() == null ? null : gfx.zones().point(id);
    }

    /**
     * Where DM's layout puts a {@code width} x {@code height} picture in zone
     * {@code id}: {x, y, w, h, srcX, srcY} in viewport coordinates (see
     * {@link dm.data.Zones#coord}), or null without the zone table or when
     * nothing shows.
     */
    public int[] coord(int id, int width, int height) {
        return gfx == null || gfx.zones() == null ? null : gfx.zones().coord(id, width, height);
    }

    /** The raw palette-indexed picture {@code index}, or null; for drawing with DM's own colour rules. */
    public IndexedImage indexed(int index) {
        return gfx == null ? null : gfx.image(index);
    }

    /** Sound entry {@code index}, or null when it isn't available. */
    public Sound sound(int index) {
        return gfx == null ? null : gfx.sound(index);
    }

    /** Entry {@code index} as an opaque image, or null. */
    public BufferedImage image(int index) {
        return convert(index, false);
    }

    /** Entry {@code index} with palette colour 10 made transparent, or null. */
    public BufferedImage sprite(int index) {
        return convert(index, true);
    }

    /**
     * Door decorations are drawn on an orange (colour 9) background as well
     * as the usual colour 10; both are see-through.
     */
    public BufferedImage doorSprite(int index) {
        int key = Integer.MIN_VALUE / 2 + index;
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        BufferedImage src = sprite(index);
        BufferedImage out = null;
        if (src != null) {
            out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            int orange = PALETTE[9].getRGB();
            for (int y = 0; y < src.getHeight(); y++) {
                for (int x = 0; x < src.getWidth(); x++) {
                    int argb = src.getRGB(x, y);
                    out.setRGB(x, y, argb == orange ? 0 : argb);
                }
            }
        }
        cache.put(key, out);
        return out;
    }

    /**
     * Entry {@code index} with palette colour {@code transparent} see-through
     * instead of colour 10, as DM blits some panels (the inventory's empty
     * panel keys out red, its labels dark grey). Null if missing.
     */
    public BufferedImage keyed(int index, int transparent) {
        int key = Integer.MIN_VALUE / 4 + index * 16 + transparent;
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        BufferedImage src = image(index);
        BufferedImage out = null;
        if (src != null) {
            out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            int clear = PALETTE[transparent].getRGB();
            for (int y = 0; y < src.getHeight(); y++) {
                for (int x = 0; x < src.getWidth(); x++) {
                    int argb = src.getRGB(x, y);
                    out.setRGB(x, y, argb == clear ? 0 : argb);
                }
            }
        }
        cache.put(key, out);
        return out;
    }

    /** Entry {@code index} as a transparent sprite, mirrored left-to-right, or null. */
    public BufferedImage spriteFlipped(int index) {
        int key = Integer.MIN_VALUE + index;
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        BufferedImage src = sprite(index);
        BufferedImage out = null;
        if (src != null) {
            out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < src.getHeight(); y++) {
                for (int x = 0; x < src.getWidth(); x++) {
                    out.setRGB(src.getWidth() - 1 - x, y, src.getRGB(x, y));
                }
            }
        }
        cache.put(key, out);
        return out;
    }

    public BufferedImage portrait(int n) {
        BufferedImage sheet = image(GraphicsFile.PORTRAITS);
        if (sheet == null) {
            return null;
        }
        int x = (n % PORTRAITS_PER_ROW) * PORTRAIT_W;
        int y = (n / PORTRAITS_PER_ROW) * PORTRAIT_H;
        if (x + PORTRAIT_W > sheet.getWidth() || y + PORTRAIT_H > sheet.getHeight()) {
            return null;
        }
        return sheet.getSubimage(x, y, PORTRAIT_W, PORTRAIT_H);
    }

    /**
     * An item's inventory icon number (its index in GRAPHICS.DAT's object name
     * list), or -1. Wall sensors name the item they want by this number,
     * e.g. 184 for a gold key.
     */
    public int iconIndex(Item item) {
        if (gfx == null) {
            return -1;
        }
        return iconIndexes.computeIfAbsent(item.name() + "#" + item.nameVariant(),
                k -> findIcon(gfx.objectNames(), item.name(), item.nameVariant()));
    }

    /** The way the party faces, which a compass's icon points (DM's F033 reads it too, #63). */
    private Direction partyFacing = Direction.NORTH;

    /** Sets the party's facing for the compass's icon; {@link GameScreen} sets it before each frame. */
    public void setPartyFacing(Direction facing) {
        partyFacing = facing;
    }

    /**
     * The 16x16 inventory icon for an item, found by its name in GRAPHICS.DAT's
     * object name list; a compass points the party's way.
     */
    public BufferedImage icon(Item item) {
        return icon(iconIndex(ItemCatalog.pointing(item, partyFacing)));
    }

    /**
     * Icon number {@code index} from the icon sheets, or null. Icons past the
     * object names are the interface's own, e.g. 202/203 the inventory eye
     * not looking and looking.
     */
    public BufferedImage icon(int index) {
        if (index < 0) {
            return null;
        }
        BufferedImage sheet = image(GraphicsFile.FIRST_ICON_SHEET + index / ICONS_PER_SHEET);
        if (sheet == null) {
            return null;
        }
        int cell = index % ICONS_PER_SHEET;
        int perRow = sheet.getWidth() / ICON_SIZE;
        return sheet.getSubimage((cell % perRow) * ICON_SIZE, (cell / perRow) * ICON_SIZE, ICON_SIZE, ICON_SIZE);
    }

    /**
     * An item's icon for the mouse pointer: the icon sheets' background,
     * colour 12, is see-through, as when DM draws an icon as the pointer.
     */
    public BufferedImage iconSprite(Item item) {
        BufferedImage src = icon(item);
        if (src == null) {
            return null;
        }
        Item shown = ItemCatalog.pointing(item, partyFacing);
        return iconSprites.computeIfAbsent(shown.name() + "#" + shown.nameVariant(), k -> {
            BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            int background = PALETTE[ICON_BACKGROUND].getRGB();
            for (int y = 0; y < src.getHeight(); y++) {
                for (int x = 0; x < src.getWidth(); x++) {
                    int argb = src.getRGB(x, y);
                    out.setRGB(x, y, argb == background ? 0 : argb);
                }
            }
            return out;
        });
    }

    private static int findIcon(List<String> names, String name, int variant) {
        int seen = 0;
        for (int i = 0; i < names.size(); i++) {
            if (names.get(i).equals(name) && seen++ == variant) {
                return i;
            }
        }
        return -1;
    }

    private BufferedImage convert(int index, boolean transparent) {
        if (gfx == null) {
            return null;
        }
        int key = transparent ? -index - 1 : index;
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        IndexedImage img = gfx.image(index);
        BufferedImage out = null;
        if (img != null) {
            out = new BufferedImage(img.width(), img.height(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < img.height(); y++) {
                for (int x = 0; x < img.width(); x++) {
                    int p = img.pixel(x, y);
                    out.setRGB(x, y, transparent && p == TRANSPARENT ? 0 : PALETTE[p].getRGB());
                }
            }
        }
        cache.put(key, out);
        return out;
    }
}
