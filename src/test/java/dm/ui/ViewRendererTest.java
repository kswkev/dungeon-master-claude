package dm.ui;

import dm.model.Direction;
import dm.model.DungeonMap;
import dm.model.Party;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

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
}
