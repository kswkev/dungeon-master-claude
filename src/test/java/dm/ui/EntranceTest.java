package dm.ui;

import dm.model.Direction;
import dm.model.DungeonMap;
import dm.model.Party;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** DM's entrance (F438-F441): ENTER opens the doors, RESUME offers the saves, QUIT quits. */
class EntranceTest {

    private Party party;
    private GameScreen screen;
    private long now;
    private int sounds;
    private boolean quit;

    @BeforeEach
    void setUp() {
        party = new Party(DungeonMap.fromAscii(0, "###", "#.#", "#.#", "###"), 1, 2, Direction.NORTH);
        screen = new GameScreen(party, Art.none(), sound -> sounds++, false);
        screen.setClock(() -> now);
        screen.setOnQuit(() -> quit = true);
        screen.showEntrance();
        render();
    }

    private void render() {
        BufferedImage img = new BufferedImage(GameScreen.WIDTH, GameScreen.HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        screen.render(g);
        g.dispose();
    }

    private void click(Rectangle r) {
        screen.press(r.x + r.width / 2, r.y + r.height / 2);
    }

    @Test
    void theGameWaitsBehindTheDoors() {
        long time = party.time();
        assertFalse(screen.tick());
        assertEquals(time, party.time());
        screen.key(MovementPanel.Action.FORWARD);
        assertEquals(2, party.y(), "no walking at the entrance");
        assertFalse(screen.animate(), "nothing moves until ENTER");
    }

    @Test
    void enterOpensTheDoorsWithARattleEveryThirdStepThenTheGameBegins() {
        click(Entrance.ENTER);
        assertEquals(1, sounds, "the switch");
        assertTrue(screen.entrance().opening());
        now += Entrance.STEP_MS * 10;
        assertTrue(screen.animate());
        render();
        assertNotNull(screen.entrance(), "still opening");
        now += Entrance.STEP_MS * Entrance.STEPS;
        screen.animate();
        assertNull(screen.entrance(), "open: the game begins");
        assertEquals(1 + 11, sounds, "rattles on steps 1, 4, ... 31");
        screen.key(MovementPanel.Action.FORWARD);
        assertEquals(1, party.y());
    }

    @Test
    void resumeShowsTheSavedGamesAndEscGoesBack() {
        click(Entrance.RESUME);
        assertTrue(screen.menu().isOpen());
        assertEquals(GameMenu.Screen.LOAD_SLOTS, screen.menu().screen());
        render();
        screen.escape();
        assertFalse(screen.menu().isOpen());
        assertNotNull(screen.entrance(), "back at the doors");
    }

    @Test
    void theMusicLoopsAtTheEntranceAndFadesAsTheDoorsOpen() {
        java.util.List<String> calls = new java.util.ArrayList<>();
        dm.data.Sound song = new dm.data.Sound(new byte[10], 11025);
        screen.setMusic(new MusicPlayer() {
            @Override
            public void loop(dm.data.Sound s) {
                calls.add(s == song ? "loop" : "loop other");
            }

            @Override
            public void fadeOut(long millis) {
                calls.add("fade " + millis);
            }

            @Override
            public void stop() {
                calls.add("stop");
            }
        }, song);
        screen.showEntrance();
        click(Entrance.ENTER);
        assertEquals(java.util.List.of("loop", "fade " + Entrance.STEPS * Entrance.STEP_MS), calls);
    }

    @Test
    void quitQuits() {
        click(Entrance.QUIT);
        assertTrue(quit);
    }
}
