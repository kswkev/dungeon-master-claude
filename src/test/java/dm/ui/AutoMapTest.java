package dm.ui;

import dm.model.Direction;
import dm.model.DungeonMap;
import dm.model.Party;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The map (not in DM): the scroll button, paging floors, pausing, and the party's arrow. */
class AutoMapTest {

    private Party party;
    private GameScreen screen;

    @BeforeEach
    void setUp() {
        // An open pit at (1,1) drops the party from Level 1 to Level 2.
        DungeonMap top = DungeonMap.fromAscii(0, "###", "#O#", "#.#", "###");
        DungeonMap bottom = DungeonMap.fromAscii(1, "###", "#.#", "#.#", "###");
        DungeonMap unseen = DungeonMap.fromAscii(2, "###", "#.#", "###");
        party = new Party(List.of(top, bottom, unseen), 0, 1, 2, Direction.NORTH);
        screen = new GameScreen(party, Art.none(), sound -> { }, false);
        party.explore();
        screen.press(MovementPanel.AREA.x + 40, MovementPanel.AREA.y + 10); // forward, into the pit
        assertEquals(1, party.level());
    }

    private void clickButton() {
        screen.press(AutoMap.BUTTON.x + 4, AutoMap.BUTTON.y + 4);
    }

    private BufferedImage render() {
        BufferedImage img = new BufferedImage(GameScreen.WIDTH, GameScreen.HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        screen.render(g);
        g.dispose();
        return img;
    }

    private static boolean has(BufferedImage img, Rectangle r, int rgb) {
        for (int x = r.x; x < r.x + r.width; x++) {
            for (int y = r.y; y < r.y + r.height; y++) {
                if ((img.getRGB(x, y) & 0xFFFFFF) == (rgb & 0xFFFFFF)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void theScrollOpensTheMapOnThePartysFloorAndPausesTheGame() {
        clickButton();
        assertTrue(screen.automap().isOpen());
        assertEquals(1, screen.automap().level());
        long time = party.time();
        assertFalse(screen.tick());
        assertEquals(time, party.time(), "paused");
        BufferedImage img = render();
        assertTrue(has(img, AutoMap.MAP_AREA, AutoMap.PARTY.getRGB()), "the party's green arrow");
        assertTrue(has(img, AutoMap.MAP_AREA, AutoMap.INK.getRGB()), "walls");
    }

    @Test
    void theArrowsPageThroughSeenFloorsOnly() {
        clickButton();
        Rectangle up = AutoMap.UP;
        Rectangle down = AutoMap.DOWN;
        screen.press(up.x + 5, up.y + 5);
        assertEquals(0, screen.automap().level());
        assertFalse(has(render(), AutoMap.MAP_AREA, AutoMap.PARTY.getRGB()), "the party isn't on this floor");
        screen.press(up.x + 5, up.y + 5);
        assertEquals(0, screen.automap().level(), "no floor above");
        screen.press(down.x + 5, down.y + 5);
        screen.press(down.x + 5, down.y + 5);
        assertEquals(1, screen.automap().level(), "the third floor hasn't been seen");
        assertTrue(screen.automap().isOpen());
    }

    @Test
    void anyOtherClickOrEscClosesIt() {
        clickButton();
        int x = party.x();
        screen.press(MovementPanel.AREA.x + 40, MovementPanel.AREA.y + 10); // the forward arrow
        assertFalse(screen.automap().isOpen());
        assertEquals(x, party.x(), "the click only closed the map");
        clickButton();
        screen.escape();
        assertFalse(screen.automap().isOpen());
        assertFalse(screen.menu().isOpen(), "Esc closed the map, not opened the menu");
    }

    @Test
    void movementKeysAreIgnoredWhileItIsOpen() {
        clickButton();
        Direction facing = party.facing();
        screen.key(MovementPanel.Action.TURN_RIGHT);
        assertEquals(facing, party.facing());
    }
}
