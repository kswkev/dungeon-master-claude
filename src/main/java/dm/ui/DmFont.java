package dm.ui;

import dm.data.GraphicsFile;

import java.awt.Color;
import java.awt.Graphics2D;

/**
 * DM's own font, GRAPHICS.DAT entry {@link GraphicsFile#FONT}: 128
 * characters of 5×6 pixels, each drawn in a 6-pixel cell whose first column
 * is blank. Characters 96-119 are the spell symbols (runes). DM's text
 * routine (F040) puts a string's top-left one pixel left of and four above
 * the point it is given; {@link #draw} takes that point the same way.
 */
public final class DmFont {

    public static final int HEIGHT = 6;
    public static final int ADVANCE = 6;
    private static final int CHARS = 128;

    private final byte[] rows;

    private DmFont(byte[] rows) {
        this.rows = rows;
    }

    /** The font in {@code gfx}, or null when the entry isn't there or isn't a font. */
    public static DmFont from(GraphicsFile gfx) {
        byte[] raw = gfx == null ? null : gfx.raw(GraphicsFile.FONT);
        return raw == null || raw.length < CHARS * HEIGHT ? null : new DmFont(raw);
    }

    /** Whether pixel (x, y) of character {@code c} (x 0-4, y 0-5) is set. */
    public boolean pixel(char c, int x, int y) {
        return ((rows[y * CHARS + (c & 0x7F)] >> (4 - x)) & 1) != 0;
    }

    /**
     * Draws {@code text} as DM's F040 does at (x, y): each character fills its
     * 6×6 cell, the text colour on {@code background} (null leaves the
     * background as it is).
     */
    public void draw(Graphics2D g, String text, int x, int y, Color color, Color background) {
        int left = x - 1;
        int top = y - 4;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int cellX = left + i * ADVANCE;
            if (background != null) {
                g.setColor(background);
                g.fillRect(cellX, top, ADVANCE, HEIGHT);
            }
            g.setColor(color);
            for (int py = 0; py < HEIGHT; py++) {
                for (int px = 0; px < 5; px++) {
                    if (pixel(c, px, py)) {
                        g.fillRect(cellX + 1 + px, top + py, 1, 1);
                    }
                }
            }
        }
    }
}
