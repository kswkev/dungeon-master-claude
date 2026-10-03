package dm.ui;

import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DarknessTest {

    private static BufferedImage pixel(int rgb) {
        BufferedImage img = new BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, rgb);
        img.setRGB(1, 0, rgb);
        return img;
    }

    @Test
    void paletteZeroIsArtsPalette() {
        for (int i = 0; i < 16; i++) {
            assertEquals(Art.PALETTE[i].getRGB() & 0xFFFFFF, Darkness.colour(0, i), "colour " + i);
        }
    }

    @Test
    void paletteColoursBecomeTheirDarkerVersions() {
        BufferedImage img = pixel(0xFFFFFF);
        Darkness.apply(img, new Rectangle(0, 0, 1, 1), 3);
        assertEquals(0x888888, img.getRGB(0, 0) & 0xFFFFFF, "white in palette 3");
        assertEquals(0xFFFFFF, img.getRGB(1, 0) & 0xFFFFFF, "outside the area");
    }

    @Test
    void cyanStaysBright() {
        BufferedImage img = pixel(0x00CCCC);
        Darkness.apply(img, new Rectangle(0, 0, 1, 1), 5);
        assertEquals(0x00CCCC, img.getRGB(0, 0) & 0xFFFFFF, "DM keeps colour 4 in every palette");
    }

    @Test
    void otherColoursAreDimmed() {
        BufferedImage img = pixel(0x808080);
        Darkness.apply(img, new Rectangle(0, 0, 1, 1), 5);
        assertEquals(0x222222, img.getRGB(0, 0) & 0xFFFFFF, "scaled by the darkest white, 0x44 / 0xFF");
    }
}
