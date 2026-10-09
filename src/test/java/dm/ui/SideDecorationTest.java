package dm.ui;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** #54: floor-level decorations on side walls stand on the sloping floor edge. */
class SideDecorationTest {

    /** A 4x5 picture whose only pixels are its bottom row. */
    private static BufferedImage bar() {
        BufferedImage img = new BufferedImage(4, 5, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 4; x++) {
            img.setRGB(x, 4, 0xFF00FF00);
        }
        return img;
    }

    @Test
    void theLowestPixelTouchesTheLeftWallsLastRow() {
        // Columns 40-43 end at rows 110-107 (150 - x); the edge rises toward the middle, so
        // column 43 is the one the flat bar touches, and none goes below the floor.
        assertEquals(107 - 4, TexturedViewRenderer.onSideFloor(bar(), 40, false, -1));
    }

    @Test
    void rightFacesMirrorIt() {
        // Columns 180-183 mirror 43-40: column 180 is the one touched.
        assertEquals(107 - 4, TexturedViewRenderer.onSideFloor(bar(), 180, true, -1));
    }

    @Test
    void anEmptyPictureKeepsTheFallback() {
        assertEquals(-1, TexturedViewRenderer.onSideFloor(new BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB), 40, false, -1));
    }
}
