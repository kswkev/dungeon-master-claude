package dm.ui;

import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static dm.ui.MovementPanel.Action.FORWARD;
import static dm.ui.MovementPanel.Action.STRAFE_RIGHT;
import static dm.ui.MovementPanel.Action.TURN_LEFT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The arrow panel's click boxes are DM's (#28), and it still draws without GRAPHICS.DAT. */
class MovementPanelTest {

    private final MovementPanel panel = new MovementPanel(Art.none());

    @Test
    void clicksLandInDmsBoxes() {
        assertEquals(TURN_LEFT, panel.hitTest(234, 125));
        assertEquals(FORWARD, panel.hitTest(276, 135));
        assertEquals(STRAFE_RIGHT, panel.hitTest(318, 167));
        assertNull(panel.hitTest(262, 135), "the line between two arrows");
        assertNull(panel.hitTest(233, 124), "the panel's outer edge");
    }

    @Test
    void theFallbackPanelDraws() {
        BufferedImage img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        panel.setPressed(FORWARD);
        panel.draw(g);
        g.dispose();
        assertEquals(true, (img.getRGB(276, 135) & 0xFFFFFF) != 0);
    }

    @Test
    void theMirrorHangsAtTheStraightOnMirrorsHeight() {
        assertEquals(41.5 / 111, TexturedViewRenderer.MIRROR_LEVEL, 1e-9);
        assertEquals(TexturedViewRenderer.MIRROR_LEVEL, TexturedViewRenderer.level(TexturedViewRenderer.MIRROR_ORNAMENT));
        assertEquals(TexturedViewRenderer.EYE_LEVEL, TexturedViewRenderer.level(44), "a lever");
        assertEquals(0, TexturedViewRenderer.level(1), "an alcove keeps its zone centre");
    }
}
