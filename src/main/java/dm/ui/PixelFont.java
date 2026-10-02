package dm.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.HashMap;
import java.util.Map;

/**
 * A 5x5 upper-case bitmap font in the style of DM's interface text.
 * Each character advances 6 pixels. Lower case is drawn as upper case;
 * unknown characters are drawn as blanks.
 */
public final class PixelFont {

    public static final int ADVANCE = 6;
    public static final int HEIGHT = 5;

    private static final Map<Character, String[]> GLYPHS = new HashMap<>();

    static {
        glyph('A', ".###.", "#...#", "#####", "#...#", "#...#");
        glyph('B', "####.", "#...#", "####.", "#...#", "####.");
        glyph('C', ".####", "#....", "#....", "#....", ".####");
        glyph('D', "####.", "#...#", "#...#", "#...#", "####.");
        glyph('E', "#####", "#....", "####.", "#....", "#####");
        glyph('F', "#####", "#....", "####.", "#....", "#....");
        glyph('G', ".####", "#....", "#..##", "#...#", ".###.");
        glyph('H', "#...#", "#...#", "#####", "#...#", "#...#");
        glyph('I', "#####", "..#..", "..#..", "..#..", "#####");
        glyph('J', "....#", "....#", "....#", "#...#", ".###.");
        glyph('K', "#...#", "#..#.", "###..", "#..#.", "#...#");
        glyph('L', "#....", "#....", "#....", "#....", "#####");
        glyph('M', "#...#", "##.##", "#.#.#", "#...#", "#...#");
        glyph('N', "#...#", "##..#", "#.#.#", "#..##", "#...#");
        glyph('O', ".###.", "#...#", "#...#", "#...#", ".###.");
        glyph('P', "####.", "#...#", "####.", "#....", "#....");
        glyph('Q', ".###.", "#...#", "#.#.#", "#..#.", ".##.#");
        glyph('R', "####.", "#...#", "####.", "#..#.", "#...#");
        glyph('S', ".####", "#....", ".###.", "....#", "####.");
        glyph('T', "#####", "..#..", "..#..", "..#..", "..#..");
        glyph('U', "#...#", "#...#", "#...#", "#...#", ".###.");
        glyph('V', "#...#", "#...#", "#...#", ".#.#.", "..#..");
        glyph('W', "#...#", "#...#", "#.#.#", "##.##", "#...#");
        glyph('X', "#...#", ".#.#.", "..#..", ".#.#.", "#...#");
        glyph('Y', "#...#", ".#.#.", "..#..", "..#..", "..#..");
        glyph('Z', "#####", "...#.", "..#..", ".#...", "#####");
        glyph('0', ".###.", "#..##", "#.#.#", "##..#", ".###.");
        glyph('1', "..#..", ".##..", "..#..", "..#..", ".###.");
        glyph('2', ".###.", "#...#", "..##.", ".#...", "#####");
        glyph('3', "####.", "....#", ".###.", "....#", "####.");
        glyph('4', "#..#.", "#..#.", "#####", "...#.", "...#.");
        glyph('5', "#####", "#....", "####.", "....#", "####.");
        glyph('6', ".###.", "#....", "####.", "#...#", ".###.");
        glyph('7', "#####", "....#", "...#.", "..#..", "..#..");
        glyph('8', ".###.", "#...#", ".###.", "#...#", ".###.");
        glyph('9', ".###.", "#...#", ".####", "....#", ".###.");
        glyph('.', ".....", ".....", ".....", ".....", "..#..");
        glyph(',', ".....", ".....", ".....", "..#..", ".#...");
        glyph('\'', "..#..", "..#..", ".....", ".....", ".....");
        glyph('/', "....#", "...#.", "..#..", ".#...", "#....");
        glyph('-', ".....", ".....", ".###.", ".....", ".....");
        glyph(':', ".....", "..#..", ".....", "..#..", ".....");
        glyph('!', "..#..", "..#..", "..#..", ".....", "..#..");
        glyph('?', ".###.", "#...#", "..##.", ".....", "..#..");
    }

    private PixelFont() {
    }

    private static void glyph(char c, String... rows) {
        GLYPHS.put(c, rows);
    }

    public static int width(String text) {
        return text.isEmpty() ? 0 : text.length() * ADVANCE - 1;
    }

    /** Draws {@code text} with its top-left corner at (x, y). */
    public static void draw(Graphics2D g, String text, int x, int y, Color color) {
        g.setColor(color);
        for (int i = 0; i < text.length(); i++) {
            String[] rows = GLYPHS.get(Character.toUpperCase(text.charAt(i)));
            if (rows == null) {
                continue;
            }
            int gx = x + i * ADVANCE;
            for (int r = 0; r < rows.length; r++) {
                for (int c = 0; c < rows[r].length(); c++) {
                    if (rows[r].charAt(c) == '#') {
                        g.fillRect(gx + c, y + r, 1, 1);
                    }
                }
            }
        }
    }
}
