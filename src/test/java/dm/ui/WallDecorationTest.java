package dm.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** #67-#71: wall decorations go where DM's coordinate sets put them. */
class WallDecorationTest {

    private static final Path GRAPHICS = Path.of("data/GRAPHICS.DAT");

    @Test
    void eachDecorationHasDmsCoordinateSet() {
        assertEquals(1, TexturedViewRenderer.coordinateSet(1), "square alcove");
        assertEquals(1, TexturedViewRenderer.coordinateSet(2), "VI altar");
        assertEquals(1, TexturedViewRenderer.coordinateSet(37), "ghoul's head");
        assertEquals(0, TexturedViewRenderer.coordinateSet(44), "lever up");
        assertEquals(2, TexturedViewRenderer.coordinateSet(33), "slime");
        assertEquals(2, TexturedViewRenderer.coordinateSet(12), "slime outlet, at the foot of the wall (#71)");
        assertEquals(5, TexturedViewRenderer.coordinateSet(TexturedViewRenderer.MIRROR_ORNAMENT));
        assertEquals(7, TexturedViewRenderer.coordinateSet(59), "Lord Order outside");
    }

    @Test
    void zonesAreFifteenPerSet() {
        assertEquals(1033, TexturedViewRenderer.frontZone(1, 1, 0), "set 1, D1 centre");
        assertEquals(1028, TexturedViewRenderer.frontZone(1, 2, -1), "set 1, D2 left");
        assertEquals(1010, TexturedViewRenderer.frontZone(44, 3, 1), "set 0, D3 right");
        assertEquals(1046, TexturedViewRenderer.sideZone(33, 1, false), "set 2, D1 left side");
        assertEquals(1037, TexturedViewRenderer.sideZone(33, 3, true), "set 2, D3 right side");
        assertEquals(1108, TexturedViewRenderer.frontZone(59, 1, 0), "set 7 stands on the floor like set 6");
    }

    @Test
    void anInscriptionFromAfarIsCutByItsLines() {
        int[] d2 = {10, 17, 27, 37};
        assertEquals(10, TexturedViewRenderer.inscriptionRows(37, d2, "ONE LINE"));
        assertEquals(27, TexturedViewRenderer.inscriptionRows(37, d2, "A\nB\nC"));
        assertEquals(37, TexturedViewRenderer.inscriptionRows(37, d2, "A\nB\nC\nD"));
        assertEquals(5, TexturedViewRenderer.inscriptionRows(19, d2, "HALF"), "scaled to the picture");
    }

    @Test
    void thePcZonesMatchG0205sBoxes() {
        assumeTrue(Files.exists(GRAPHICS), "needs the original GRAPHICS.DAT");
        Art art = Art.load(GRAPHICS);
        // A 96x56 alcove fills G0205 set 1's D1 box (64,36)-(159,91), and a 32x28 slime outlet set 2's (96,92)-(127,119).
        assertArrayEquals(new int[] {64, 36, 96, 56, 0, 0}, art.coord(TexturedViewRenderer.frontZone(1, 1, 0), 96, 56));
        assertArrayEquals(new int[] {96, 92, 32, 28, 0, 0}, art.coord(TexturedViewRenderer.frontZone(12, 1, 0), 32, 28));
    }
}
