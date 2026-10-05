package dm.model;

import dm.ui.Art;
import dm.ui.GameScreen;
import dm.ui.MovementPanel;
import dm.ui.SoundPlayer;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sprint 22: the fuse sequence on the screen, and DM's end screen for a won game. */
class EndgameScreenTest {

    @Test
    void theSequencePlaysOutWithoutInputThenTheEndScreenWaitsForEsc() {
        DungeonMap map = DungeonMap.fromAscii(0, "#####", "#...#", "#####");
        Party party = new Party(map, 1, 1, Direction.EAST);
        party.setRandom(new Random(3));
        Champion elija = Champion.parse(ChampionTest.ELIJA, 0);
        party.recruit(new ChampionMirror(0, 0, Direction.SOUTH, elija));
        elija.replace(Slot.ACTION_HAND, ItemCatalog.item(Item.Category.WEAPON, 45));
        Group chaos = new Group(CreatureType.LORD_CHAOS, 2, 1, Group.CENTRED, new int[] {1000, 0, 0, 0}, 1,
                Direction.WEST, List.of());
        map.addGroup(chaos);
        map.addGroup(new Group(CreatureType.MUMMY, 3, 1, Group.CENTRED, new int[] {50, 0, 0, 0}, 1,
                Direction.WEST, List.of()));
        map.setEndgameTexts(List.of("ATHE END\nOF CHAOS"));
        GameScreen screen = new GameScreen(party, Art.none(), SoundPlayer.silent(), false);
        Flight.fluxcage(party, map, 1, 1);
        Flight.fluxcage(party, map, 3, 1);
        party.act(0, Actions.FUSE);
        assertNotNull(party.endgame(), "caught");

        screen.press(MovementPanel.AREA.x + 40, MovementPanel.AREA.y + 10);
        screen.escape();
        assertEquals(1, party.x(), "no moving during the sequence");
        assertFalse(screen.menu().isOpen(), "nor the menu");

        for (int i = 0; i < 3000 && !screen.gameWon(); i++) {
            screen.tick();
            render(screen);
        }
        assertTrue(screen.gameWon());
        assertEquals(CreatureType.GREY_LORD, chaos.type());
        assertTrue(map.groups().size() == 1, "the mummy is gone");
        render(screen);
        screen.press(160, 100);
        screen.escape();
        assertTrue(screen.menu().isOpen(), "Esc opens the game menu on the end screen");
    }

    private static void render(GameScreen s) {
        BufferedImage img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        s.render(g);
        g.dispose();
    }
}
