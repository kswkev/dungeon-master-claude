package dm.ui;

import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InscriptionTest {

    @Test
    void mapsLettersToFontCells() {
        assertEquals(0, Inscription.glyph('A'));
        assertEquals(8, Inscription.glyph('I'));
        assertEquals(25, Inscription.glyph('Z'));
        assertEquals(27, Inscription.glyph('.'));
        assertEquals(-1, Inscription.glyph(' '));
    }

    @Test
    void drawsWithoutGraphics() {
        BufferedImage img = new BufferedImage(224, 136, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        Inscription.draw(g, Art.none(), "HALL OF\nCHAMPIONS", new Rectangle(32, 8, 160, 111));
        g.dispose();
        boolean ink = false;
        for (int x = 32; x < 192 && !ink; x++) {
            for (int y = 8; y < 119 && !ink; y++) {
                ink = img.getRGB(x, y) != 0xFF000000;
            }
        }
        assertEquals(true, ink, "falls back to the pixel font");
    }
}
