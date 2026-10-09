package dm.ui;

import dm.model.Champion;
import dm.model.ChampionMirror;
import dm.model.Direction;
import dm.model.DungeonMap;
import dm.model.FloorSensor;
import dm.model.Party;
import dm.model.WallSensor;
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
        party.recruit(new ChampionMirror(1, 0, Direction.SOUTH, Champion.parse(
                "ELIJA\nLION OF YAITOPYA\n\nM\nAADMACEEAABG\nDCCKCICKCEDFCI\nBBCAAAAACBECAAAA", 1))); // #58
        screen = new GameScreen(party, Art.none(), sound -> { }, false);
        party.explore();
        screen.press(MovementPanel.AREA.x + 40, MovementPanel.AREA.y + 10); // forward, into the pit
        assertEquals(1, party.level());
    }

    @Test
    void wallsBeyondTheMapsEdgeAreShownBesideSeenSquares() {
        // A corridor along the map's west edge: x = -1 is the wall beyond it.
        DungeonMap edge = DungeonMap.fromAscii(0, "..", "..");
        edge.markSeen(0, 0);
        edge.markSeen(1, 1);
        List<DungeonMap> floor = List.of(edge);
        assertTrue(AutoMap.shown(floor, edge, -1, 0), "#59: the wall beyond a seen square");
        assertTrue(AutoMap.shown(floor, edge, 0, -1));
        assertFalse(AutoMap.shown(floor, edge, -1, 1), "beside an unseen square");
        assertFalse(AutoMap.shown(floor, edge, -1, -1), "corners touch no square");
        assertTrue(AutoMap.shown(floor, edge, 2, 1));
        assertFalse(AutoMap.shown(floor, edge, 0, 1), "inside, only what was seen");
    }

    @Test
    void anIllusionaryWallIsKnownOnceThePartyStepsIntoIt() {
        DungeonMap m = DungeonMap.fromAscii(0, "######", "#.I.f#", "######");
        Party p = new Party(m, 1, 1, Direction.EAST);
        assertFalse(m.knownIllusion(2, 1));
        p.step(Party.Move.FORWARD);
        assertEquals(2, p.x());
        assertTrue(m.knownIllusion(2, 1));
        assertFalse(m.knownIllusion(1, 1), "floor is no illusion");
        p.step(Party.Move.FORWARD);
        p.step(Party.Move.FORWARD);
        assertEquals(4, p.x());
        assertFalse(m.knownIllusion(4, 1), "#65: an open fake wall is floor, not an illusion");
    }

    @Test
    void aClickSensorWithADecorationIsAWallButton() {
        DungeonMap m = DungeonMap.fromAscii(0, "###", "#.#", "###");
        m.addWallSensor(new WallSensor(1, 0, Direction.SOUTH, WallSensor.TYPE_CLICK, 0, FloorSensor.Effect.SET,
                false, false, false, true, 1, 0, 0, 0, 10));
        m.addWallSensor(new WallSensor(1, 2, Direction.NORTH, WallSensor.TYPE_CLICK, 0, FloorSensor.Effect.SET,
                false, false, false, true, 1, 0, 0, 0, -1));
        m.addWallSensor(new WallSensor(0, 1, Direction.EAST, WallSensor.TYPE_CLICK_WITH_ITEM_USED_UP, 184,
                FloorSensor.Effect.SET, false, false, false, true, 1, 0, 0, 0, 4));
        assertTrue(m.hasWallButton(1, 0, Direction.SOUTH));
        assertFalse(m.hasWallButton(1, 0, Direction.NORTH), "only the side it is on");
        assertFalse(m.hasWallButton(1, 2, Direction.NORTH), "a hidden click sensor shows nothing");
        assertFalse(m.hasWallButton(0, 1, Direction.EAST), "a keyhole is no button");
    }

    @Test
    void aSeenButtonIsDrawnAsADot() {
        DungeonMap m = party.map();
        m.addWallSensor(new WallSensor(1, 0, Direction.SOUTH, WallSensor.TYPE_CLICK, 0, FloorSensor.Effect.SET,
                false, false, false, true, 1, 0, 0, 0, 10));
        party.explore();
        clickButton();
        assertTrue(has(render(), AutoMap.MAP_AREA, AutoMap.BUTTON_DOT.getRGB()), "the button's dot");
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
