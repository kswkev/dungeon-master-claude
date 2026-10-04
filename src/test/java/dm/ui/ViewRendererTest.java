package dm.ui;

import dm.model.Direction;
import dm.model.DungeonMap;
import dm.model.Party;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class ViewRendererTest {

    @Test
    void fallsBackToFlatRendererWithoutGraphics() {
        assertInstanceOf(FlatViewRenderer.class, ViewRenderer.forArt(Art.none()));
    }

    @Test
    void flatRendererDrawsEveryFeatureWithoutGraphics() {
        // Door (closed and open), pit, stairs and teleporter all in view.
        DungeonMap map = DungeonMap.fromAscii(0,
                "#####",
                "#.D.#",
                "#.d.#",
                "#.O.#",
                "#.S.#",
                "#.T.#",
                "#...#",
                "#####");
        Party party = new Party(map, 2, 6, Direction.NORTH);
        ViewRenderer view = ViewRenderer.forArt(Art.none());
        BufferedImage img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        view.draw(g, party);
        g.dispose();
        assertNull(view.portraitHit(), "no mirror in view");
    }

    /** Objects keep their colours near by and take DM's G0214 (D2) or G0213 (D3) colours as they shrink. */
    @Test
    void distantObjectsChangeColour() {
        assertNull(TexturedViewRenderer.objectChanges(32));
        assertNull(TexturedViewRenderer.objectChanges(27));
        assertEquals(50, TexturedViewRenderer.objectChanges(21)[8], "D2: red becomes light brown");
        assertEquals(50, TexturedViewRenderer.objectChanges(18)[8]);
        assertEquals(120, TexturedViewRenderer.objectChanges(14)[1], "D3: dark grey becomes darkest grey");
        assertEquals(120, TexturedViewRenderer.objectChanges(12)[1]);
    }
}
