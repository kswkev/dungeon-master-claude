package dm.ui;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * DM's six dungeon view palettes (G021, brightest first), which darken the
 * dungeon view, and only the view, as the party's light fades. Palette 0 is
 * {@link Art#PALETTE}. The values are ScummVM's for the PC and Amiga (12-bit
 * RGB), which ScummVM also uses for the PC version's dungeon view. That table
 * blacks out colours 9 and 10 in the darker palettes, so those two are
 * scaled from ScummVM's Atari ST rows instead.
 *
 * Every viewport pixel drawn in palette colour i becomes colour i of the
 * chosen palette. Anything else (the flat renderer's shaded colours, the
 * translucent teleporter overlay) is dimmed by the same palette's grey.
 */
public final class Darkness {

    private static final int[][] PALETTES = {
            {0x000, 0x666, 0x888, 0x620, 0x0CC, 0x840, 0x080, 0x0C0, 0xF00, 0xFA0, 0xC86, 0xFF0, 0x444, 0xAAA, 0x00F, 0xFFF},
            {0x000, 0x444, 0x666, 0x620, 0x0CC, 0x820, 0x060, 0x0A0, 0xC00, 0xC80, 0xA64, 0xFC0, 0x222, 0x888, 0x00C, 0xCCC},
            {0x000, 0x222, 0x444, 0x420, 0x0CC, 0x620, 0x040, 0x080, 0xA00, 0xA60, 0x842, 0xFA0, 0x000, 0x666, 0x00A, 0xAAA},
            {0x000, 0x000, 0x222, 0x200, 0x0CC, 0x420, 0x020, 0x060, 0x800, 0x840, 0x620, 0xC80, 0x000, 0x444, 0x008, 0x888},
            {0x000, 0x000, 0x000, 0x000, 0x0CC, 0x200, 0x000, 0x040, 0x600, 0x620, 0x400, 0xA60, 0x000, 0x222, 0x006, 0x666},
            {0x000, 0x000, 0x000, 0x000, 0x0CC, 0x000, 0x000, 0x020, 0x400, 0x400, 0x200, 0x640, 0x000, 0x000, 0x004, 0x444}};

    /** Palette colour (RGB) to its darker versions, for each palette. */
    private static final Map<Integer, int[]> BY_COLOUR = new HashMap<>();

    static {
        for (int i = 0; i < 16; i++) {
            int[] versions = new int[PALETTES.length];
            for (int p = 0; p < PALETTES.length; p++) {
                versions[p] = rgb(PALETTES[p][i]);
            }
            BY_COLOUR.putIfAbsent(rgb(PALETTES[0][i]), versions);
        }
    }

    private Darkness() {
    }

    /** 12-bit 0xRGB to 24-bit, each 4-bit component repeated (0xC -> 0xCC). */
    private static int rgb(int c) {
        int r = (c >> 8) & 15;
        int g = (c >> 4) & 15;
        int b = c & 15;
        return (r * 17 << 16) | (g * 17 << 8) | b * 17;
    }

    /** Colour {@code index} of dungeon palette {@code palette}, for tests and swatches. */
    static int colour(int palette, int index) {
        return rgb(PALETTES[palette][index]);
    }

    /** Redraws {@code area} of {@code image} in dungeon palette {@code palette} (0 changes nothing). */
    public static void apply(BufferedImage image, Rectangle area, int palette) {
        apply(image, area, palette, null, null);
    }

    /**
     * As {@link #apply(BufferedImage, Rectangle, int)}, with colours 9 and 10
     * replaced by a map's creature colour sets ({@code colour9} and
     * {@code colour10}: RGB for the six light levels, or null to keep DM's
     * own), as DM does on maps whose creatures name them. That applies at
     * full light too.
     */
    public static void apply(BufferedImage image, Rectangle area, int palette, int[] colour9, int[] colour10) {
        if (palette <= 0 && colour9 == null && colour10 == null) {
            return;
        }
        Map<Integer, int[]> byColour = BY_COLOUR;
        if (colour9 != null || colour10 != null) {
            byColour = new HashMap<>(BY_COLOUR);
            if (colour9 != null) {
                byColour.put(rgb(PALETTES[0][9]), colour9);
            }
            if (colour10 != null) {
                byColour.put(rgb(PALETTES[0][10]), colour10);
            }
        }
        int grey = rgb(PALETTES[palette][15]) & 0xFF; // how bright white still is
        for (int y = area.y; y < area.y + area.height; y++) {
            for (int x = area.x; x < area.x + area.width; x++) {
                int argb = image.getRGB(x, y);
                int[] versions = byColour.get(argb & 0xFFFFFF);
                int out;
                if (versions != null) {
                    out = versions[palette];
                } else {
                    int r = ((argb >> 16) & 0xFF) * grey / 255;
                    int g = ((argb >> 8) & 0xFF) * grey / 255;
                    int b = (argb & 0xFF) * grey / 255;
                    out = (r << 16) | (g << 8) | b;
                }
                image.setRGB(x, y, (argb & 0xFF000000) | out);
            }
        }
    }
}
