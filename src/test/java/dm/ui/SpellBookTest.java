package dm.ui;

import dm.model.Champion;
import dm.model.ChampionMirror;
import dm.model.Direction;
import dm.model.DungeonMap;
import dm.model.Party;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The spell lists (not in DM): the two scrolls, switching, pausing and closing. */
class SpellBookTest {

    private Party party;
    private GameScreen screen;

    @BeforeEach
    void setUp() {
        party = new Party(List.of(DungeonMap.fromAscii(0, "###", "#.#", "#.#", "###")), 0, 1, 2, Direction.NORTH);
        party.recruit(new ChampionMirror(0, 0, Direction.SOUTH,
                Champion.parse("ELIJA\nLION OF YAITOPYA\n\nM\nAADMACEEAABG\nDCCKCICKCEDFCI\nBBCAAAAACBECAAAA", 0)));
        screen = new GameScreen(party, Art.none(), sound -> { }, false);
    }

    private void click(java.awt.Rectangle r) {
        screen.press(r.x + 4, r.y + 4);
    }

    private void render() {
        BufferedImage img = new BufferedImage(GameScreen.WIDTH, GameScreen.HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        screen.render(g);
        g.dispose();
    }

    @Test
    void eachScrollOpensItsListAndTheOtherSwitches() {
        click(SpellBook.WIZARD_BUTTON);
        assertTrue(screen.spellBook().isOpen());
        assertTrue(screen.spellBook().wizard());
        render();
        click(SpellBook.PRIEST_BUTTON);
        assertTrue(screen.spellBook().isOpen());
        assertFalse(screen.spellBook().wizard());
        render();
        click(SpellBook.PRIEST_BUTTON);
        assertFalse(screen.spellBook().isOpen(), "its own scroll closes it");
    }

    @Test
    void theGameIsPausedAndAnyClickOrEscClosesIt() {
        click(SpellBook.PRIEST_BUTTON);
        long time = party.time();
        assertFalse(screen.tick());
        assertEquals(time, party.time());
        screen.press(MovementPanel.AREA.x + 40, MovementPanel.AREA.y + 10);
        assertFalse(screen.spellBook().isOpen());
        assertEquals(2, party.y(), "the click only closed it");
        click(SpellBook.WIZARD_BUTTON);
        screen.escape();
        assertFalse(screen.spellBook().isOpen());
        assertFalse(screen.menu().isOpen());
    }

    @Test
    void theListsSplitTheSpellTable() {
        assertEquals(13, SpellBook.spells(true).size());
        assertEquals(12, SpellBook.spells(false).size());
    }
}
