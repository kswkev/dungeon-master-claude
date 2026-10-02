package dm.ui;

import dm.model.Champion;
import dm.model.ChampionMirror;
import dm.model.Direction;
import dm.model.DungeonMap;
import dm.model.FloorSensor;
import dm.model.Item;
import dm.model.ItemCatalog;
import dm.model.Party;
import dm.model.Slot;
import dm.model.Square;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Drives the screen with clicks at 320x200 coordinates, using placeholder art. */
class GameScreenTest {

    private static final String ELIJA = "ELIJA\nLION OF YAITOPYA\n\nM\nAADMACEEAABG\nDCCKCICKCEDFCI\nBBCAAAAACBECAAAA";
    private static final Rectangle VIEW = ViewRenderer.VIEWPORT;

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
    void formationBoxPicksAndPlacesAChampion() {
        recruitElija();
        Champion elija = party.members().get(0);
        Rectangle box = FormationBox.AREA;
        screen.press(box.x + 5, box.y + 5);          // front-left: Elija
        assertEquals(Party.FRONT_LEFT, screen.formation().picked());
        screen.press(box.x + box.width - 5, box.y + box.height - 5); // back-right, empty
        assertEquals(-1, screen.formation().picked());
        assertSame(elija, party.at(Party.BACK_RIGHT));
        assertEquals(null, party.at(Party.FRONT_LEFT));
        render();
    }

    @Test
    void clickingAnEmptyFormationCellPicksNothing() {
        screen.press(FormationBox.AREA.x + 5, FormationBox.AREA.y + 5);
        assertEquals(-1, screen.formation().picked());
    }

    @Test
    void backingIntoAWallHurtsTheBackRow() {
        recruitElija();
        Champion elija = party.members().get(0);
        party.swap(Party.FRONT_LEFT, Party.BACK_LEFT);
        party.turnRight();
        party.turnRight(); // face south: the mirror wall is now behind
        screen.press(MovementPanel.AREA.x + 40, MovementPanel.AREA.y + 32); // backward arrow
        assertEquals(elija.maxHealth() - 1, elija.health());
        assertEquals(1, screen.bars().damageShown(0, now));
    }

    @Test
    void plateOpensTheDoorOverGameTicks() {
        // Corridor running west: door (1,1), plate (2,1), party starts at (3,1).
        DungeonMap ascii = DungeonMap.fromAscii(0, "#####", "#D..#", "#####");
        Square[][] squares = new Square[5][3];
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 3; y++) {
                squares[x][y] = ascii.get(x, y);
            }
        }
        ChampionMirror hallMirror = new ChampionMirror(3, 0, Direction.SOUTH, Champion.parse(ELIJA, 0));
        DungeonMap map = new DungeonMap(0, squares, List.of(hallMirror));
        map.addSensor(new FloorSensor(2, 1, FloorSensor.TYPE_PARTY, FloorSensor.Effect.SET,
                false, false, false, 1, 1, 1));
        Party p = new Party(map, 3, 1, Direction.WEST);
        p.recruit(hallMirror);
        GameScreen s = new GameScreen(p, Art.none(), sound -> soundsPlayed++, false);

        s.press(MovementPanel.AREA.x + 40, MovementPanel.AREA.y + 10); // forward onto the plate
        assertEquals(0, soundsPlayed, "the door hasn't moved yet");
        assertFalse(map.isPassable(1, 1));
        int ticks = 0;
        while (s.tick()) {
            ticks++;
        }
        assertEquals(4, ticks);
        assertEquals(3, soundsPlayed, "the door rattles 3 times while opening, as in DM");
        assertTrue(map.isPassable(1, 1));
        s.press(MovementPanel.AREA.x + 40, MovementPanel.AREA.y + 10);
        assertEquals(1, p.x(), "walked into the open doorway");
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

    // ---- moving items on the character sheet ----

    private static final Item SWORD = ItemCatalog.item(Item.Category.WEAPON, 10);
    private static final Item HELM = ItemCatalog.item(Item.Category.ARMOUR, 25);

    private void click(Slot slot) {
        Point p = CharacterSheet.slotCentre(slot);
        screen.press(p.x, p.y);
        screen.release();
    }

    /** Recruits Elija with a sword in the action hand and a helm on, and opens her sheet. */
    private Champion openElijaWithItems() {
        mirror.champion().addStartingItem(SWORD);
        mirror.champion().addStartingItem(HELM);
        recruitElija();
        screen.press(10, 10);
        return party.members().get(0);
    }

    @Test
    void clickingAnItemPicksItUpAndAnEmptyCellTakesIt() {
        Champion elija = openElijaWithItems();
        click(Slot.ACTION_HAND);
        assertSame(SWORD, party.held());
        assertTrue(screen.holding());
        assertNull(elija.items().get(Slot.ACTION_HAND));
        screen.hover(150, 100);
        render(); // the held icon is drawn at the pointer

        click(Slot.BACKPACK_3);
        assertNull(party.held());
        assertSame(SWORD, elija.items().get(Slot.BACKPACK_3));
    }

    @Test
    void clickingAnEmptyCellWithNothingHeldDoesNothing() {
        Champion elija = openElijaWithItems();
        click(Slot.BACKPACK_1);
        assertNull(party.held());
        assertEquals(2, elija.items().size());
    }

    @Test
    void anItemThatDoesNotFitStaysInHand() {
        Champion elija = openElijaWithItems();
        click(Slot.ACTION_HAND);
        click(Slot.TORSO);
        assertSame(SWORD, party.held(), "a sword can't be worn on the torso");
        assertNull(elija.items().get(Slot.TORSO));
    }

    @Test
    void droppingOnAnOccupiedCellSwaps() {
        Champion elija = openElijaWithItems();
        click(Slot.HEAD);
        click(Slot.ACTION_HAND);
        assertSame(HELM, elija.items().get(Slot.ACTION_HAND));
        assertSame(SWORD, party.held());
    }

    @Test
    void theHeldItemSurvivesSwitchingChampionsAndClosingTheSheet() {
        openElijaWithItems();
        ChampionMirror second = new ChampionMirror(2, 0, Direction.SOUTH, Champion.parse(ELIJA, 1));
        party.recruit(second);
        click(Slot.ACTION_HAND);
        screen.press(69 + 10, 10); // second champion's box
        assertSame(second.champion(), screen.sheet().champion());
        assertSame(SWORD, party.held());
        screen.press(69 + 10, 10); // close the sheet
        assertFalse(screen.sheet().isOpen());
        assertSame(SWORD, party.held());

        screen.press(69 + 10, 10);
        click(Slot.READY_HAND);
        assertSame(SWORD, second.champion().items().get(Slot.READY_HAND));
        assertNull(party.held());
    }

    @Test
    void aCandidatesItemsCannotBeTaken() {
        mirror.champion().addStartingItem(SWORD);
        clickPortrait();
        click(Slot.ACTION_HAND);
        assertNull(party.held());
        assertSame(SWORD, mirror.champion().items().get(Slot.ACTION_HAND));
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
