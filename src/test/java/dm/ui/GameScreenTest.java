package dm.ui;

import dm.model.Champion;
import dm.model.ChampionMirror;
import dm.model.Direction;
import dm.model.DungeonMap;
import dm.model.Party;
import dm.model.Square;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Drives the screen with clicks at 320x200 coordinates, using placeholder art. */
class GameScreenTest {

    private static final String ELIJA = "ELIJA\nLION OF YAITOPYA\n\nM\nAADMACEEAABG\nDCCKCICKCEDFCI\nBBCAAAAACBECAAAA";
    private static final Rectangle VIEW = DungeonViewRenderer.VIEWPORT;

    private ChampionMirror mirror;
    private Party party;
    private GameScreen screen;
    private int soundsPlayed;
    private int bumps;
    private long now = 1_000;

    @BeforeEach
    void setUp() {
        DungeonMap ascii = DungeonMap.fromAscii(0, "###", "#.#", "#.#", "###");
        Square[][] squares = new Square[3][4];
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 4; y++) {
                squares[x][y] = ascii.get(x, y);
            }
        }
        mirror = new ChampionMirror(1, 0, Direction.SOUTH, Champion.parse(ELIJA, 0));
        party = new Party(new DungeonMap(0, squares, List.of(mirror)), 1, 1, Direction.NORTH);
        screen = new GameScreen(party, Art.none(), sound -> soundsPlayed++, false);
        screen.setClock(() -> now);
        screen.setOnBump(() -> bumps++);
        render();
    }

    private void pressForward() {
        screen.press(MovementPanel.AREA.x + 40, MovementPanel.AREA.y + 10);
        screen.release();
    }

    private void recruitElija() {
        clickPortrait();
        screen.press(VIEW.x + 120, VIEW.y + 128); // RESURRECT
        render();
    }

    @Test
    void bumpWithNoPartyOnlyPlaysTheThud() {
        pressForward(); // the mirror wall is straight ahead
        assertEquals(1, soundsPlayed);
        assertEquals(1, bumps, "red flash still triggers");
        assertEquals(1, party.y());
    }

    @Test
    void bumpDamagesTheFrontRowAndShowsTheBurst() {
        recruitElija();
        Champion elija = party.members().get(0);
        pressForward();
        assertEquals(1, soundsPlayed);
        assertEquals(elija.maxHealth() - 1, elija.health());
        assertEquals(1, screen.bars().damageShown(0, now));
        now += GameScreen.DAMAGE_SHOWN_MS;
        assertEquals(0, screen.bars().damageShown(0, now), "burst expires");
    }

    @Test
    void successfulMovesDoNotBump() {
        party.turnRight();
        party.turnRight(); // face south, down the corridor
        pressForward();
        assertEquals(2, party.y());
        assertEquals(0, soundsPlayed);
        assertEquals(0, bumps);
    }

    private void render() {
        BufferedImage img = new BufferedImage(GameScreen.WIDTH, GameScreen.HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        screen.render(g);
        g.dispose();
    }

    private void clickPortrait() {
        screen.press(VIEW.x + VIEW.width / 2, VIEW.y + VIEW.height / 2 - 6);
        render();
    }

    @Test
    void clickingPortraitOpensCandidateSheet() {
        clickPortrait();
        assertTrue(screen.sheet().isOpen());
        assertSame(mirror, screen.sheet().candidate());
    }

    @Test
    void resurrectAddsChampionAndCloses() {
        clickPortrait();
        screen.press(VIEW.x + 120, VIEW.y + 128); // RESURRECT
        assertEquals(1, party.members().size());
        assertTrue(mirror.taken());
        assertFalse(screen.sheet().isOpen());
    }

    @Test
    void cancelLeavesMirrorUntouched() {
        clickPortrait();
        screen.press(VIEW.x + 190, VIEW.y + 128); // CANCEL
        assertFalse(screen.sheet().isOpen());
        assertTrue(party.members().isEmpty());
        assertFalse(mirror.taken());
    }

    @Test
    void championBarReopensMemberSheet() {
        clickPortrait();
        screen.press(VIEW.x + 120, VIEW.y + 128);
        render();
        screen.press(10, 10); // first champion box
        assertTrue(screen.sheet().isOpen());
        assertNotNull(screen.sheet().champion());
        assertEquals(null, screen.sheet().candidate());
        screen.press(10, 10); // clicking the same box again closes it
        assertFalse(screen.sheet().isOpen());
    }

    @Test
    void emptyMirrorCannotBeClicked() {
        clickPortrait();
        screen.press(VIEW.x + 120, VIEW.y + 128);
        render();
        clickPortrait();
        assertFalse(screen.sheet().isOpen());
    }

    @Test
    void arrowsAreIgnoredWhileSheetIsOpen() {
        clickPortrait();
        party.turnRight();
        party.turnRight(); // face south, toward the open corridor
        screen.press(MovementPanel.AREA.x + 40, MovementPanel.AREA.y + 10); // forward arrow
        assertEquals(1, party.y(), "party must not move");
    }
}
