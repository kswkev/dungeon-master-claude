package dm.ui;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * Wall inscriptions, carved in DM's inscription font (GRAPHICS.DAT 258: a
 * strip of 8x8 cells, A-Z in cells 0-25, space 26, full stop 27). DM shows
 * the text only on the wall straight ahead; the lines are centred on it.
 */
final class Inscription {

    static final int FONT = 258;
    private static final int CELL = 8;
    private static final int LINE_HEIGHT = 10;

    private Inscription() {
    }

    /** Cell for a character, or -1 to leave a gap. */
    static int glyph(char c) {
        if (c >= 'A' && c <= 'Z') {
            return c - 'A';
        }
        return c == '.' ? 27 : -1;
    }

    static void draw(Graphics2D g, Art art, String text, Rectangle face) {
        String[] lines = text.split("\n");
        BufferedImage font = art.sprite(FONT);
        int top = face.y + face.height / 2 - lines.length * LINE_HEIGHT / 2 - 6;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int x = face.x + (face.width - line.length() * CELL) / 2;
            int y = top + i * LINE_HEIGHT;
            if (font == null) {
                PixelFont.draw(g, line, face.x + (face.width - PixelFont.width(line)) / 2, y, Art.PALETTE[13]);
                continue;
            }
            for (int c = 0; c < line.length(); c++) {
                int cell = glyph(line.charAt(c));
                if (cell >= 0 && (cell + 1) * CELL <= font.getWidth()) {
                    g.drawImage(font.getSubimage(cell * CELL, 0, CELL, CELL), x + c * CELL, y, null);
                }
            }
        }
    }
}
