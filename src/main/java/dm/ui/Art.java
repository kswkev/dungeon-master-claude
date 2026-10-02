package dm.ui;

import dm.data.GraphicsFile;
import dm.data.IndexedImage;
import dm.data.Sound;
import dm.model.Item;

import java.awt.Color;
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

    private final GraphicsFile gfx;
    private final Map<Integer, BufferedImage> cache = new HashMap<>();
    private final Map<String, Integer> iconIndexes = new HashMap<>();

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

    /** The 16x16 inventory icon for an item, found by its name in GRAPHICS.DAT's object name list. */
    public BufferedImage icon(Item item) {
        if (gfx == null) {
            return null;
        }
        int index = iconIndexes.computeIfAbsent(item.name() + "#" + item.nameVariant(),
                k -> findIcon(gfx.objectNames(), item.name(), item.nameVariant()));
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
