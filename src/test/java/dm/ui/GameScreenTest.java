package dm.ui;

import dm.data.SaveGames;
import dm.data.Sound;
import dm.model.Actions;
import dm.model.Champion;
import dm.model.ChampionMirror;
import dm.model.CreatureType;
import dm.model.Decorations;
import dm.model.Difficulty;
import dm.model.Direction;
import dm.model.DungeonMap;
import dm.model.FloorSensor;
import dm.model.Group;
import dm.model.Item;
import dm.model.ItemCatalog;
import dm.model.Party;
import dm.model.Slot;
import dm.model.Spells;
import dm.model.Square;
import dm.model.WallSensor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
    /** A point on the first champion's name in their status box (the hands are below it). */
    private static final int NAME_X = 10;
    private static final int NAME_Y = 4;

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
        press(CharacterSheet.resurrectCentre());
        render();
    }

    @Test
    void anEmptyHandKnockingOnAWallThumps() {
        recruitElija();
        party.turnLeft(); // the plain wall at (0,1)
        render();
        int before = soundsPlayed;
        Point face = new Point(GameScreen.WALL_FACE.x + 80, GameScreen.WALL_FACE.y + 50);
        screen.press(face.x, face.y);
        assertEquals(before + 1, soundsPlayed, "the thump");
        assertEquals(0, bumps, "a knock isn't a bump: no flash, no damage");
        party.setHeld(ItemCatalog.item(Item.Category.WEAPON, 8));
        screen.press(GameScreen.PILE_BOXES[0].x + 5, GameScreen.PILE_BOXES[0].y + 5); // dropped, not knocked
        assertEquals(before + 1, soundsPlayed);
    }

    @Test
    void anIllusionaryWallMakesNoSound() {
        Party p = new Party(DungeonMap.fromAscii(0, "#F#", "#.#", "###"), 1, 1, Direction.NORTH);
        p.recruit(new ChampionMirror(0, 1, Direction.EAST, Champion.parse(ELIJA, 0)));
        GameScreen s = new GameScreen(p, Art.none(), sound -> soundsPlayed++, false);
        BufferedImage img = new BufferedImage(GameScreen.WIDTH, GameScreen.HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        s.render(g);
        g.dispose();
        int before = soundsPlayed;
        s.press(GameScreen.WALL_FACE.x + 80, GameScreen.WALL_FACE.y + 50);
        assertEquals(before, soundsPlayed);
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

    // ---- the game menu: save, load, quit ----

    @TempDir
    Path saveDir;

    private void clickMenu(GameMenu.Choice choice) {
        clickMenu(choice, 0);
    }

    private void clickMenu(GameMenu.Choice choice, int slot) {
        Point p = GameMenu.centre(screen.menu().screen(), choice, slot);
        screen.press(p.x, p.y);
        screen.release();
    }

    /** Recruits Elija, opens her sheet and clicks the disk icon. */
    private void openMenu() {
        screen.setSaveGames(new SaveGames(saveDir));
        if (party.members().isEmpty()) {
            recruitElija();
        }
        if (!screen.sheet().isOpen()) {
            screen.rightPress(NAME_X, NAME_Y);
        }
        Point disk = CharacterSheet.diskCentre();
        screen.press(disk.x, disk.y);
        assertTrue(screen.menu().isOpen());
        assertEquals(GameMenu.Screen.MAIN, screen.menu().screen());
        render();
    }

    @Test
    void cancelGoesBackToTheSheetAndTheGameIsPausedMeanwhile() {
        openMenu();
        long time = party.time();
        assertFalse(screen.tick());
        assertEquals(time, party.time(), "the clock stops while the menu is up");
        screen.key(MovementPanel.Action.BACKWARD);
        assertEquals(1, party.y(), "and the party can't move");
        clickMenu(GameMenu.Choice.CANCEL);
        assertFalse(screen.menu().isOpen());
        assertTrue(screen.sheet().isOpen(), "back to the sheet");
    }

    @Test
    void optionsSetTheDifficultyAndGodMode() {
        openMenu();
        clickMenu(GameMenu.Choice.OPTIONS);
        assertEquals(GameMenu.Screen.OPTIONS, screen.menu().screen());
        assertEquals(Difficulty.NORMAL, party.difficulty());
        render();
        clickMenu(GameMenu.Choice.DIFFICULTY, Difficulty.HARD.ordinal());
        assertEquals(Difficulty.HARD, party.difficulty());
        clickMenu(GameMenu.Choice.DIFFICULTY, Difficulty.EASY.ordinal());
        assertEquals(Difficulty.EASY, party.difficulty(), "one at a time, as a radio group");
        Point gem = GameMenu.centre(GameMenu.Screen.OPTIONS, GameMenu.Choice.GOD_MODE, 0);
        screen.press(gem.x + 40, gem.y); // the label, not the gem
        screen.release();
        assertFalse(party.godMode(), "only the gem toggles it");
        clickMenu(GameMenu.Choice.GOD_MODE);
        assertTrue(party.godMode());
        render();
        clickMenu(GameMenu.Choice.GOD_MODE);
        assertFalse(party.godMode(), "god mode toggles");
        clickMenu(GameMenu.Choice.DEEP_SLEEP);
        assertTrue(party.deepSleep());
        assertFalse(party.godMode(), "each toggle is its own");
        clickMenu(GameMenu.Choice.DEEP_SLEEP);
        assertFalse(party.deepSleep());
        clickMenu(GameMenu.Choice.LOCK_MASTER);
        assertTrue(party.lockMaster());
        render();
        clickMenu(GameMenu.Choice.LOCK_MASTER);
        assertFalse(party.lockMaster());
        clickMenu(GameMenu.Choice.BACK);
        assertEquals(GameMenu.Screen.MAIN, screen.menu().screen());
    }

    @Test
    void theOptionsAreSavedWithTheGame() throws Exception {
        openMenu();
        party.setDifficulty(Difficulty.HARD);
        party.setGodMode(true);
        party.setDeepSleep(true);
        party.setLockMaster(true);
        new SaveGames(saveDir).save(1, party);
        Party loaded = new SaveGames(saveDir).load(1);
        assertEquals(Difficulty.HARD, loaded.difficulty());
        assertTrue(loaded.godMode());
        assertTrue(loaded.deepSleep());
        assertTrue(loaded.lockMaster());
        assertEquals(0, loaded.members().get(0).takeDamage(5), "the champions keep it too");
    }

    @Test
    void theWholeDiskIconOpensTheMenu() {
        screen.setSaveGames(new SaveGames(saveDir));
        recruitElija();
        screen.rightPress(NAME_X, NAME_Y);
        // The disk spans viewport x 180-188, y 3-11 on DM's inventory graphic.
        for (int[] p : new int[][] {{180, 3}, {188, 3}, {180, 11}, {188, 11}, {184, 7}}) {
            screen.press(VIEW.x + p[0], VIEW.y + p[1]);
            assertTrue(screen.menu().isOpen(), "at " + p[0] + "," + p[1]);
            screen.escape();
        }
        screen.press(VIEW.x + 178, VIEW.y + 7);
        assertFalse(screen.menu().isOpen(), "left of the disk");
    }

    @Test
    void escapeCancels() {
        openMenu();
        screen.escape();
        assertFalse(screen.menu().isOpen());
    }

    @Test
    void escapeOpensTheMenuFromTheDungeonView() {
        screen.setSaveGames(new SaveGames(saveDir));
        assertFalse(screen.sheet().isOpen());
        screen.escape();
        assertTrue(screen.menu().isOpen());
        assertEquals(GameMenu.Screen.MAIN, screen.menu().screen());
        render();
        clickMenu(GameMenu.Choice.CANCEL);
        assertFalse(screen.menu().isOpen());
        assertFalse(screen.sheet().isOpen(), "back to the dungeon, where it was opened");
    }

    private void dieOfABump() {
        Champion elija = party.members().get(0);
        elija.takeDamage(elija.health() - 1);
        pressForward(); // the fatal bump
        assertTrue(screen.gameOver());
    }

    private static Point centre(Rectangle r) {
        return new Point(r.x + r.width / 2, r.y + r.height / 2);
    }

    @Test
    void fiveSecondsAfterTheEndRestartLoadsTheNewestSave() {
        screen.setSaveGames(new SaveGames(saveDir));
        recruitElija();
        screen.escape();
        clickMenu(GameMenu.Choice.SAVE);
        clickMenu(GameMenu.Choice.SLOT, 2);
        clickMenu(GameMenu.Choice.OK);
        dieOfABump();
        Point restart = centre(GameScreen.RESTART_BOX);
        screen.press(restart.x, restart.y);
        assertTrue(screen.gameOver(), "not offered yet");
        assertFalse(screen.tick());
        now += GameScreen.RESTART_DELAY_MS;
        assertTrue(screen.tick(), "a repaint as the buttons appear");
        assertTrue(screen.restartShown());
        render();
        screen.press(restart.x, restart.y);
        assertFalse(screen.gameOver(), "the saved game is back");
        assertEquals(1, screen.party().members().size());
        assertTrue(screen.party().members().get(0).health() > 0);
    }

    @Test
    void newGameStartsAgainAndRestartNeedsASave() {
        screen.setSaveGames(new SaveGames(saveDir)); // empty
        Party fresh = new Party(DungeonMap.fromAscii(0, "###", "#.#", "###"), 1, 1, Direction.NORTH);
        screen.setNewGame(() -> fresh);
        recruitElija();
        dieOfABump();
        now += GameScreen.RESTART_DELAY_MS;
        Point restart = centre(GameScreen.RESTART_BOX);
        screen.press(restart.x, restart.y);
        assertTrue(screen.gameOver(), "no save to restart from");
        Point newGame = centre(GameScreen.NEW_GAME_BOX);
        screen.press(newGame.x, newGame.y);
        assertFalse(screen.gameOver());
        assertSame(fresh, screen.party());
    }

    @Test
    void afterTheEndEscapeStillOffersALoad() {
        screen.setSaveGames(new SaveGames(saveDir));
        recruitElija();
        screen.escape();
        clickMenu(GameMenu.Choice.SAVE);
        clickMenu(GameMenu.Choice.SLOT, 1);
        clickMenu(GameMenu.Choice.OK);
        Champion elija = party.members().get(0);
        elija.takeDamage(elija.health() - 1);
        pressForward(); // the fatal bump
        assertTrue(screen.gameOver());
        screen.escape();
        render(); // the menu over the last scene
        clickMenu(GameMenu.Choice.LOAD);
        clickMenu(GameMenu.Choice.SLOT, 1);
        clickMenu(GameMenu.Choice.OK);
        assertFalse(screen.gameOver());
        assertTrue(screen.party().members().get(0).health() > 0, "alive again, as saved");
    }

    @Test
    void aSavedGameCanBeLoadedBack() {
        openMenu();
        clickMenu(GameMenu.Choice.SAVE);
        assertEquals(GameMenu.Screen.SAVE_SLOTS, screen.menu().screen());
        render();
        clickMenu(GameMenu.Choice.SLOT, 1);
        assertEquals(GameMenu.Screen.MESSAGE, screen.menu().screen(), "GAME SAVED");
        render();
        clickMenu(GameMenu.Choice.OK);
        assertTrue(screen.sheet().isOpen());
        screen.rightPress(NAME_X, NAME_Y); // close the sheet

        screen.key(MovementPanel.Action.BACKWARD);
        assertEquals(2, party.y(), "walked off after saving");

        openMenu();
        clickMenu(GameMenu.Choice.LOAD);
        render(); // slot 1 full, the rest empty
        clickMenu(GameMenu.Choice.SLOT, 2);
        assertEquals(GameMenu.Screen.LOAD_SLOTS, screen.menu().screen(), "an empty slot loads nothing");
        clickMenu(GameMenu.Choice.SLOT, 1);
        assertEquals(GameMenu.Screen.MESSAGE, screen.menu().screen(), "GAME LOADED");
        clickMenu(GameMenu.Choice.OK);
        assertEquals(1, screen.party().y(), "back where it was saved");
        assertEquals("ELIJA", screen.party().members().get(0).name());
        assertFalse(screen.sheet().isOpen());
        render();
    }

    @Test
    void saveAndQuitSavesThenQuits() {
        int[] quits = {0};
        screen.setOnQuit(() -> quits[0]++);
        openMenu();
        clickMenu(GameMenu.Choice.QUIT);
        assertEquals(GameMenu.Screen.QUIT, screen.menu().screen());
        render();
        clickMenu(GameMenu.Choice.SAVE_AND_QUIT);
        assertEquals(GameMenu.Screen.SAVE_SLOTS, screen.menu().screen());
        clickMenu(GameMenu.Choice.SLOT, 3);
        assertEquals(1, quits[0]);
        assertTrue(Files.exists(saveDir.resolve("slot3.dmsave")));
    }

    @Test
    void quitWithoutSaving() {
        int[] quits = {0};
        screen.setOnQuit(() -> quits[0]++);
        openMenu();
        clickMenu(GameMenu.Choice.QUIT);
        clickMenu(GameMenu.Choice.CANCEL);
        assertEquals(0, quits[0]);
        assertFalse(screen.menu().isOpen());
        openMenu();
        clickMenu(GameMenu.Choice.QUIT);
        clickMenu(GameMenu.Choice.QUIT_NOW);
        assertEquals(1, quits[0]);
        assertFalse(Files.exists(saveDir.resolve("slot1.dmsave")));
    }

    @Test
    void keysMoveLikeTheArrows() {
        screen.key(MovementPanel.Action.BACKWARD); // from (1,1) facing north to (1,2)
        assertEquals(2, party.y());
        screen.keyReleased();
        screen.tick(); // DM's movement ticks: at least 1 after a step
        screen.key(MovementPanel.Action.TURN_LEFT);
        assertEquals(Direction.WEST, party.facing());
        screen.key(MovementPanel.Action.FORWARD); // into the wall
        assertEquals(1, bumps, "a bump, as with the arrow");
        render();
    }

    @Test
    void aStepWaitsForTheSlowestChampionsMovementTicks() {
        recruitElija();
        screen.key(MovementPanel.Action.BACKWARD); // (1,1) to (1,2)
        screen.keyReleased();
        screen.key(MovementPanel.Action.FORWARD);
        screen.keyReleased();
        assertEquals(2, party.y(), "dropped: still 2 ticks to go");
        screen.key(MovementPanel.Action.TURN_LEFT);
        assertEquals(Direction.WEST, party.facing(), "turning is never held up");
        screen.key(MovementPanel.Action.TURN_RIGHT);
        screen.tick();
        screen.key(MovementPanel.Action.FORWARD);
        assertEquals(2, party.y(), "1 tick to go");
        screen.keyReleased();
        screen.tick();
        screen.key(MovementPanel.Action.FORWARD);
        assertEquals(1, party.y());
    }

    @Test
    void thePartyCannotFollowWhatItThrewFor4Ticks() {
        recruitElija();
        party.setHeld(ItemCatalog.item(Item.Category.WEAPON, 10));
        assertTrue(party.throwHeld(false)); // north
        assertFalse(party.canStep(Party.Move.FORWARD));
        assertTrue(party.canStep(Party.Move.BACKWARD), "only the projectile's way is blocked");
        for (int i = 0; i < 3; i++) {
            party.tick();
        }
        assertFalse(party.canStep(Party.Move.FORWARD));
        party.tick();
        assertTrue(party.canStep(Party.Move.FORWARD));
    }

    @Test
    void keysAreIgnoredWhileASheetIsOpen() {
        recruitElija();
        screen.rightPress(NAME_X, NAME_Y);
        screen.key(MovementPanel.Action.BACKWARD);
        assertEquals(1, party.y());
    }

    @Test
    void aFatalBumpKillsAndEndsTheGame() {
        recruitElija();
        Champion elija = party.members().get(0);
        elija.takeDamage(elija.health() - 1);
        pressForward();
        assertEquals(0, elija.health());
        assertEquals(2, soundsPlayed, "the thud, then the scream");
        assertEquals(0, screen.bars().damageShown(0, now), "no burst for a killing blow");
        assertTrue(screen.gameOver());
        assertEquals("BONES", last(party.map().itemsAt(1, 1, 0)).name(), "front left facing north: the NW cell");
        render(); // THE END
        int y = party.y();
        pressForward();
        assertEquals(y, party.y());
        assertEquals(2, soundsPlayed, "input is ignored after the end");
    }

    private static Item last(List<Item> pile) {
        return pile.get(pile.size() - 1);
    }

    @Test
    void rightClickOnAStatusBoxTogglesThatSheet() {
        recruitElija();
        ChampionMirror second = new ChampionMirror(2, 0, Direction.SOUTH, Champion.parse(ELIJA, 1));
        party.recruit(second);
        screen.rightPress(66, 20); // G0447: the whole box, hands too, out to x 66
        assertSame(party.members().get(0), screen.sheet().champion());
        render();
        screen.rightPress(69 + 30, 4);
        assertSame(party.members().get(1), screen.sheet().champion(), "switches to the other member");
        screen.rightPress(69 + 30, 4);
        assertFalse(screen.sheet().isOpen(), "the one shown closes");
        screen.rightPress(67, 4); // the gap between boxes
        assertFalse(screen.sheet().isOpen());
    }

    @Test
    void rightClickInsideTheSheetClosesItKeepingTheHeldItem() {
        recruitElija();
        screen.rightPress(NAME_X, NAME_Y);
        Item held = ItemCatalog.item(Item.Category.JUNK, 0);
        party.setHeld(held);
        screen.rightPress(VIEW.x + 100, VIEW.y + 60);
        assertFalse(screen.sheet().isOpen());
        assertSame(held, party.held());
        screen.rightPress(NAME_X, NAME_Y);
        screen.rightPress(300, 190); // anywhere on the screen, as G0449
        assertFalse(screen.sheet().isOpen());
    }

    @Test
    void rightClickBelowTheBarsOpensTheLeadersSheet() {
        screen.rightPress(VIEW.x + 100, VIEW.y + 60);
        assertFalse(screen.sheet().isOpen(), "no leader yet");
        recruitElija();
        screen.rightPress(VIEW.x + 100, VIEW.y + 60);
        assertSame(party.members().get(0), screen.sheet().champion());
        screen.rightPress(VIEW.x + 100, VIEW.y + 60);
        assertFalse(screen.sheet().isOpen());
        screen.rightPress(290, 10); // the formation box: nothing for the right button
        assertFalse(screen.sheet().isOpen());
    }

    @Test
    void rightClickLeavesACandidateAlone() {
        clickPortrait();
        assertNotNull(screen.sheet().candidate());
        screen.rightPress(VIEW.x + 100, VIEW.y + 60);
        screen.rightPress(NAME_X, NAME_Y);
        assertNotNull(screen.sheet().candidate(), "only DM's CANCEL closes it");
    }

    @Test
    void rightClickIgnoresADeadChampionAndWakesTheParty() {
        recruitElija();
        ChampionMirror second = new ChampionMirror(2, 0, Direction.SOUTH, Champion.parse(ELIJA, 1));
        party.recruit(second);
        Champion first = party.members().get(0);
        first.takeDamage(first.health());
        party.bury();
        screen.rightPress(NAME_X, NAME_Y);
        assertFalse(screen.sheet().isOpen());
        assertTrue(party.sleep());
        screen.rightPress(VIEW.x + 100, VIEW.y + 60);
        assertFalse(party.sleeping());
        assertFalse(screen.sheet().isOpen(), "waking opens nothing");
    }

    @Test
    void theSheetHasNoCloseButtonButShowsTheLoad() {
        assertEquals(" 12.5/ 45 KG", CharacterSheet.loadText(125, 445));
        assertEquals("  0.0/  1 KG", CharacterSheet.loadText(0, 5));
        assertEquals(Art.PALETTE[13], CharacterSheet.loadColour(25, 40));
        assertEquals(Art.PALETTE[11], CharacterSheet.loadColour(26, 40), "over five eighths");
        assertEquals(Art.PALETTE[8], CharacterSheet.loadColour(41, 40), "over the maximum");
        recruitElija();
        screen.rightPress(NAME_X, NAME_Y);
        screen.press(VIEW.x + 190, VIEW.y + 128); // where ours had CLOSE
        assertTrue(screen.sheet().isOpen());
        render();
    }

    @Test
    void scrollsShowTheSymbolsOfTheSpellsTheyName() {
        String fulIr = "" + (char) (Spells.FIRST_SYMBOL + 9) + (char) (Spells.FIRST_SYMBOL + 15);
        assertEquals(List.of("FIREBALL", "", "FUL IR. " + fulIr),
                CharacterSheet.scrollLines("FIREBALL\n\nFUL IR."));
        assertEquals(List.of("FUL BRO NETA.", "" + (char) 105 + (char) 112 + (char) 117),
                CharacterSheet.scrollLines("FUL BRO NETA."), "too wide: on a line of its own");
        assertEquals(List.of("BALANCE IS THE", "ULTIMATE GOOD"),
                CharacterSheet.scrollLines("BALANCE IS THE\nULTIMATE GOOD"));
    }

    @Test
    void aDeadChampionsBoxDoesNotOpenTheirSheet() {
        recruitElija();
        ChampionMirror second = new ChampionMirror(2, 0, Direction.SOUTH, Champion.parse(ELIJA, 1));
        party.recruit(second);
        Champion first = party.members().get(0);
        first.takeDamage(first.health());
        party.bury();
        screen.rightPress(NAME_X, NAME_Y);
        assertFalse(screen.sheet().isOpen());
        assertFalse(screen.gameOver());
        render(); // the dead box
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
        p.takeMessages(); // "ELIJA RESURRECTED." would keep the message area changing
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

    private BufferedImage render() {
        BufferedImage img = new BufferedImage(GameScreen.WIDTH, GameScreen.HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        screen.render(g);
        g.dispose();
        return img;
    }

    private void clickPortrait() {
        screen.press(VIEW.x + VIEW.width / 2, VIEW.y + VIEW.height / 2 - 6);
        render();
    }

    private void press(Point p) {
        screen.press(p.x, p.y);
        screen.release();
    }

    private void typeText(String text) {
        for (char c : text.toCharArray()) {
            screen.type(c);
        }
    }

    /** The rename panel's OK button (DM's screen box 197-215 x 147-155). */
    private void pressRenameOk() {
        screen.press(205, 150);
        render();
    }

    @Test
    void theZzzIconPutsThePartyToSleepUntilWoken() {
        recruitElija();
        screen.rightPress(NAME_X, NAME_Y); // open Elija's sheet
        render();
        assertTrue(screen.sheet().isOpen());
        press(CharacterSheet.sleepCentre());
        assertTrue(party.sleeping());
        assertFalse(screen.sheet().isOpen(), "the sheet closes");
        render();
        long before = party.time();
        screen.tick();
        assertEquals(before + GameScreen.SLEEP_TICKS, party.time(), "time runs faster asleep");
        pressForward();
        assertEquals(1, party.y(), "no moving in your sleep");
        assertTrue(party.sleeping(), "the arrows don't wake the party");
        screen.press(VIEW.x + 100, VIEW.y + 60);
        assertFalse(party.sleeping(), "a click in the view wakes it");
        party.sleep();
        screen.pressReturn();
        assertFalse(party.sleeping(), "so does Return");
    }

    @Test
    void reincarnateRenamesAndJoins() {
        clickPortrait();
        press(CharacterSheet.reincarnateCentre());
        assertTrue(screen.sheet().renaming());
        assertTrue(screen.typing());
        render();
        typeText("bob\nthe brave");
        pressRenameOk();
        assertFalse(screen.sheet().isOpen());
        Champion bob = party.members().get(0);
        assertSame(mirror.champion(), bob);
        assertEquals("BOB", bob.name());
        assertEquals("THE BRAVE", bob.title());
        assertEquals(1, bob.skillLevel(Champion.FIGHTER));
    }

    @Test
    void renamePanelKeysAreClickable() {
        clickPortrait();
        press(CharacterSheet.reincarnateCentre());
        screen.press(111, 120); // A: the first key, at DM's (107,116)
        screen.press(121, 130); // M: second row, second key
        screen.press(111, 140); // V: third row starts after RETURN's half
        screen.press(201, 140); // the space key, last of the specials
        screen.press(116, 120); // a gap between keys: nothing
        assertEquals("AMV ", screen.sheet().newName());
        screen.press(140, 150); // BACKSPACE
        assertEquals("AMV", screen.sheet().newName());
        screen.press(211, 130); // RETURN, upper half
        typeText("X");
        assertEquals("X", screen.sheet().newTitle());
    }

    @Test
    void okNeedsAName() {
        clickPortrait();
        press(CharacterSheet.reincarnateCentre());
        pressRenameOk();
        assertTrue(screen.sheet().renaming(), "no name yet: OK does nothing");
        assertTrue(party.members().isEmpty());
    }

    @Test
    void candidateNeedsAnEmptyHand() {
        party.setHeld(ItemCatalog.item(Item.Category.JUNK, 0));
        clickPortrait();
        assertFalse(screen.sheet().isOpen());
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
        press(CharacterSheet.resurrectCentre());
        assertEquals(1, party.members().size());
        assertTrue(mirror.taken());
        assertFalse(screen.sheet().isOpen());
    }

    @Test
    void cancelLeavesMirrorUntouched() {
        clickPortrait();
        press(CharacterSheet.candidateCancelCentre());
        assertFalse(screen.sheet().isOpen());
        assertTrue(party.members().isEmpty());
        assertFalse(mirror.taken());
    }

    @Test
    void championBarReopensMemberSheet() {
        clickPortrait();
        press(CharacterSheet.resurrectCentre());
        render();
        screen.rightPress(NAME_X, NAME_Y); // first champion box
        assertTrue(screen.sheet().isOpen());
        assertNotNull(screen.sheet().champion());
        assertEquals(null, screen.sheet().candidate());
        screen.rightPress(NAME_X, NAME_Y); // clicking the same box again closes it
        assertFalse(screen.sheet().isOpen());
    }

    @Test
    void aLeftClickOnAChampionBoxOpensNoSheet() {
        clickPortrait();
        press(CharacterSheet.resurrectCentre());
        render();
        screen.press(NAME_X, NAME_Y);
        assertFalse(screen.sheet().isOpen(), "#60: only the right button opens a sheet");
        screen.rightPress(NAME_X, NAME_Y);
        screen.press(NAME_X, NAME_Y);
        assertTrue(screen.sheet().isOpen(), "#60: nor closes one");
    }

    @Test
    void theScrollButtonsWaitForTheFirstChampion() {
        screen.press(AutoMap.BUTTON.x + 4, AutoMap.BUTTON.y + 4);
        assertFalse(screen.overlayOpen(), "#58: no map before anyone joins");
        screen.press(SpellBook.WIZARD_BUTTON.x + 4, SpellBook.WIZARD_BUTTON.y + 4);
        assertFalse(screen.overlayOpen(), "#58: no spell list either");
        clickPortrait();
        press(CharacterSheet.resurrectCentre());
        render();
        screen.press(AutoMap.BUTTON.x + 4, AutoMap.BUTTON.y + 4);
        assertTrue(screen.overlayOpen(), "the map opens once a champion has joined");
    }

    @Test
    void emptyMirrorCannotBeClicked() {
        clickPortrait();
        press(CharacterSheet.resurrectCentre());
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
        screen.rightPress(NAME_X, NAME_Y);
        return party.members().get(0);
    }

    // ---- chests and scrolls (Sprint 21) ----

    private static final Item CHEST = ItemCatalog.item(Item.Category.CONTAINER, 0)
            .withContents(List.of(ItemCatalog.item(Item.Category.JUNK, 29), HELM));

    private void clickChestCell(int i) {
        Point p = CharacterSheet.chestCellCentre(i);
        screen.press(p.x, p.y);
        screen.release();
    }

    /** Elija with the chest in the action hand, her sheet open, so the chest is open in the panel. */
    private Champion openChest() {
        Champion elija = openElijaWithItems();
        elija.take(Slot.ACTION_HAND);
        elija.place(Slot.ACTION_HAND, CHEST);
        render();
        return elija;
    }

    @Test
    void aChestInTheActionHandOpens() {
        openChest();
        assertSame(CHEST, screen.sheet().openChest());
        click(Slot.ACTION_HAND); // taking the chest out of the hand closes it
        assertSame(CHEST, party.held());
        assertNull(screen.sheet().openChest());
    }

    @Test
    void chestCellsGiveAndTakeItems() {
        Champion elija = openChest();
        clickChestCell(0);
        assertEquals(APPLE, party.held());
        assertEquals(List.of(HELM), elija.items().get(Slot.ACTION_HAND).contents());
        clickChestCell(5);
        assertNull(party.held());
        assertEquals(List.of(HELM, APPLE), elija.items().get(Slot.ACTION_HAND).contents());
        render();
        clickChestCell(5); // the apple stays in the cell it was put in while the chest is open
        assertEquals(APPLE, party.held());
    }

    @Test
    void aChestDoesNotGoInAChest() {
        Champion elija = openChest();
        Item other = ItemCatalog.item(Item.Category.CONTAINER, 0);
        party.setHeld(other);
        clickChestCell(3);
        assertSame(other, party.held());
        assertEquals(2, elija.items().get(Slot.ACTION_HAND).contents().size());
    }

    @Test
    void aChestWeighsWhatItHolds() {
        Item empty = ItemCatalog.item(Item.Category.CONTAINER, 0);
        assertEquals(50, empty.weight());
        assertEquals(50 + APPLE.weight() + HELM.weight(), CHEST.weight());
    }

    @Test
    void theEyeHidesTheHeldItemAndShowsItsPanel() {
        openElijaWithItems();
        party.setHeld(APPLE);
        screen.press(VIEW.x + 20, VIEW.y + 21); // the eye, held down
        assertTrue(screen.sheet().pressingEye());
        assertNull(screen.sheet().openChest());
        render();
        screen.release();
        assertFalse(screen.sheet().pressingEye());
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
        screen.rightPress(69 + NAME_X, NAME_Y); // second champion's box
        assertSame(second.champion(), screen.sheet().champion());
        assertSame(SWORD, party.held());
        screen.rightPress(69 + NAME_X, NAME_Y); // close the sheet
        assertFalse(screen.sheet().isOpen());
        assertSame(SWORD, party.held());

        screen.rightPress(69 + NAME_X, NAME_Y);
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

    // ---- eating, drinking and the eye ----

    /** DM's mouth and eye on the inventory background, in screen coordinates. */
    private static final Point MOUTH = new Point(VIEW.x + 63, VIEW.y + 20);
    private static final Point EYE = new Point(VIEW.x + 19, VIEW.y + 20);

    @Test
    void clickingTheMouthWithFoodEatsIt() {
        Champion elija = openElijaWithItems();
        int food = elija.food();
        party.setHeld(ItemCatalog.item(Item.Category.JUNK, 30)); // corn
        int before = soundsPlayed;
        screen.press(MOUTH.x, MOUTH.y);
        screen.release();
        assertNull(party.held());
        assertEquals(before + 1, soundsPlayed, "the swallow");
        assertEquals(Math.min(food + 600, Champion.MAX_FOOD), elija.food());
        render(); // the food and water panel
    }

    @Test
    void clickingTheMouthWithASwordDoesNothing() {
        openElijaWithItems();
        click(Slot.ACTION_HAND);
        int before = soundsPlayed;
        screen.press(MOUTH.x, MOUTH.y);
        assertSame(SWORD, party.held());
        assertEquals(before, soundsPlayed, "nothing swallowed");
    }

    @Test
    void holdingTheEyeShowsSkillsAndStatistics() {
        openElijaWithItems();
        assertFalse(screen.sheet().pressingEye());
        screen.press(EYE.x, EYE.y);
        assertTrue(screen.sheet().pressingEye());
        BufferedImage img = render();
        // Every skill row has its progress bar (not in DM): green up to the progress, black after.
        Champion elija = screen.party().members().get(0);
        for (int s = 0; s < Champion.BASE_SKILLS.size(); s++) {
            int y = VIEW.y + 58 + 7 * s + 2;
            int filled = CharacterSheet.BAR_WIDTH * elija.levelProgress(s) / 100;
            assertEquals(Art.PALETTE[0].getRGB(), img.getRGB(VIEW.x + 107 + CharacterSheet.BAR_WIDTH - 1, y), "track " + s);
            if (filled > 0) {
                assertEquals(Art.PALETTE[7].getRGB(), img.getRGB(VIEW.x + 107, y), "fill " + s);
            }
        }
        screen.release();
        assertFalse(screen.sheet().pressingEye());
    }

    // ---- items on the floor ----

    private static final Item APPLE = ItemCatalog.item(Item.Category.JUNK, 29);

    /** Bottom of the view: the cells of the party's own square ahead of it. */
    private void clickFloor(boolean right) {
        screen.press(VIEW.x + (right ? 170 : 50), VIEW.y + 120);
        screen.release();
    }

    /** DM's throw zone (screen y 47-102), clear of the mirror portrait in the middle. */
    private void clickAir(boolean right) {
        screen.press(VIEW.x + (right ? 185 : 40), VIEW.y + 20);
        screen.release();
    }

    /** DM's drop boxes for the near row of the square ahead (screen y 122-147). */
    private void clickAhead(boolean right) {
        screen.press(right ? 150 : 70, 135);
        screen.release();
    }

    @Test
    void clickingTheFloorPicksUpTheTopItemAndDropsTheHeldOne() {
        recruitElija(); // DM's F373 needs a leader's hand
        DungeonMap map = party.map();
        map.addItem(1, 1, Direction.NORTH.cellOf(1), SWORD);
        map.addItem(1, 1, Direction.NORTH.cellOf(1), APPLE);
        clickFloor(true);
        assertSame(APPLE, party.held(), "the top of the pile");
        assertTrue(screen.holding());
        assertEquals(List.of(SWORD), map.itemsAt(1, 1, 1));
        render();

        clickFloor(false);
        assertNull(party.held());
        assertEquals(List.of(APPLE), map.itemsAt(1, 1, 0), "dropped on the far-left cell");
    }

    @Test
    void clickingAnEmptyFloorOrTheAirWithNothingHeldDoesNothing() {
        clickFloor(false);
        clickAir(true);
        assertNull(party.held());
        assertTrue(party.map().projectiles().isEmpty());
        assertFalse(screen.sheet().isOpen());
    }

    @Test
    void clickingAChampionsActionIconOpensTheirMenuAndAnActionUsesIt() {
        recruitElija();
        screen.press(240, 100); // Elija's icon, the first
        assertEquals(0, screen.actionArea().acting());
        assertEquals(List.of(Actions.PUNCH, Actions.KICK, Actions.WAR_CRY), screen.actionArea().menu());
        render(); // the menu
        screen.press(260, 115); // the third row: WAR CRY
        assertEquals(-1, screen.actionArea().acting());
        assertTrue(party.members().get(0).actionDisabled());
        render(); // the icon, shaded
        screen.press(240, 100);
        assertEquals(-1, screen.actionArea().acting(), "no menu while recovering");
    }

    @Test
    void passClosesTheMenuWithoutActing() {
        recruitElija();
        screen.press(240, 100);
        screen.press(300, 80); // the right end of the name row
        assertEquals(-1, screen.actionArea().acting());
        assertFalse(party.members().get(0).actionDisabled());
    }

    @Test
    void theActionAreaShowsTheDamageForATick() {
        ActionArea area = screen.actionArea();
        area.performed(25);
        assertEquals(0, area.shownDamage(), "from the next tick");
        assertTrue(screen.tick());
        assertEquals(25, area.shownDamage());
        recruitElija();
        render(); // the burst, drawn by hand without GRAPHICS.DAT
        assertTrue(screen.tick());
        assertEquals(0, area.shownDamage(), "then the icons are back");
    }

    @Test
    void anEmptyPartyCannotThrow() {
        party.setHeld(SWORD);
        clickAir(false);
        assertSame(SWORD, party.held(), "DM's F329 needs a leader to throw");
        assertTrue(party.map().projectiles().isEmpty());
    }

    @Test
    void withAWallAheadNothingIsThrownOrDroppedBeyondIt() {
        recruitElija();
        party.setHeld(SWORD);
        clickAir(false);
        assertSame(SWORD, party.held(), "DM's F377 doesn't throw at a wall straight ahead");
        assertTrue(party.map().projectiles().isEmpty());
        clickAhead(false);
        assertSame(SWORD, party.held(), "nor drops into it");
        clickFloor(false);
        assertEquals(List.of(SWORD), party.map().itemsAt(1, 1, 0), "only on the party's own square");
    }

    @Test
    void itemsGoOnAndComeOffTheNearRowOfTheSquareAhead() {
        recruitElija();
        party.turnRight();
        party.turnRight(); // face south: (1,2) is ahead
        DungeonMap map = party.map();
        party.setHeld(SWORD);
        clickAhead(false);
        assertNull(party.held());
        int nearLeft = Direction.SOUTH.cellOf(3);
        assertEquals(List.of(SWORD), map.itemsAt(1, 2, nearLeft), "facing south the near-left cell is north-east");
        render(); // records the pile's box
        Rectangle pile = screen.view().pileHit(3);
        screen.press(pile.x + pile.width / 2, pile.y + pile.height / 2);
        assertSame(SWORD, party.held());
        assertTrue(map.itemsAt(1, 2, nearLeft).isEmpty());
    }

    @Test
    void aCreatureOnTheGroundGuardsTheCellAhead() {
        recruitElija();
        party.turnRight();
        party.turnRight();
        DungeonMap map = party.map();
        int nearLeft = Direction.SOUTH.cellOf(3);
        map.addItem(1, 2, nearLeft, APPLE);
        map.addGroup(new Group(CreatureType.MUMMY, 1, 2, Group.CENTRED, new int[] {200, 0, 0, 0}, 1,
                Direction.NORTH, List.of()));
        render();
        Rectangle pile = screen.view().pileHit(3);
        screen.press(pile.x + pile.width / 2, pile.y + pile.height / 2);
        assertNull(party.held(), "DM: not from under a creature that walks");
        assertEquals(List.of(APPLE), map.itemsAt(1, 2, nearLeft));
    }

    @Test
    void aThrowFromTheAirZoneOnly() {
        recruitElija();
        party.turnRight();
        party.turnRight();
        party.setHeld(SWORD);
        screen.press(VIEW.x + 14, VIEW.y + 20); // left of DM's zone
        assertSame(SWORD, party.held());
        clickAir(false);
        assertNull(party.held());
        assertEquals(1, party.map().projectiles().size());
        render(); // drawn in flight
    }

    @Test
    void aThrowFliesDownTheCorridorHalfASquarePerTick() {
        recruitElija();
        party.turnRight();
        party.turnRight(); // face south: (1,2) is open, (1,3) is wall
        party.setHeld(SWORD);
        clickAir(false);
        screen.tick();
        assertEquals(2, party.map().projectiles().get(0).y(), "into the next square");
        screen.tick();
        assertEquals(2, party.map().projectiles().get(0).y(), "across it");
        screen.tick();
        assertTrue(party.map().projectiles().isEmpty());
        assertEquals(List.of(SWORD), party.map().itemsAt(1, 2, Direction.SOUTH.cellOf(0)),
                "far-left facing south is the south-east cell");
    }

    @Test
    void theFloorCannotBeReachedWhileASheetIsOpen() {
        recruitElija();
        party.map().addItem(1, 1, 0, APPLE);
        screen.rightPress(NAME_X, NAME_Y); // open Elija's sheet over the view
        screen.press(VIEW.x + 110, VIEW.y + 104);
        assertNull(party.held());
        assertEquals(List.of(APPLE), party.map().itemsAt(1, 1, 0));
    }

    // ---- hands in the status boxes (issue #12) ----

    /** Centre of a hand box in status box {@code box}: ready hand at x + 3, action hand at x + 23, 18x18 from y = 10. */
    private void clickHand(int box, Slot slot) {
        screen.press(69 * box + (slot == Slot.READY_HAND ? 3 : 23) + 9, 19);
        screen.release();
    }

    @Test
    void clickingAHandPicksUpPlacesAndSwapsWithoutOpeningTheSheet() {
        Champion elija = openElijaWithItems();          // sword in the action hand
        screen.rightPress(NAME_X, NAME_Y);                    // close the sheet again
        assertFalse(screen.sheet().isOpen());

        clickHand(0, Slot.ACTION_HAND);
        assertSame(SWORD, party.held());
        assertNull(elija.items().get(Slot.ACTION_HAND));
        assertFalse(screen.sheet().isOpen(), "a hand click doesn't open the sheet");

        clickHand(0, Slot.READY_HAND);
        assertNull(party.held());
        assertSame(SWORD, elija.items().get(Slot.READY_HAND));

        party.setHeld(APPLE);
        clickHand(0, Slot.READY_HAND);
        assertSame(SWORD, party.held(), "swapped");
        assertSame(APPLE, elija.items().get(Slot.READY_HAND));
    }

    @Test
    void anotherChampionsHandsWorkWhileASheetIsOpen() {
        openElijaWithItems();
        ChampionMirror second = new ChampionMirror(2, 0, Direction.SOUTH, Champion.parse(ELIJA, 1));
        party.recruit(second);
        render();
        assertTrue(screen.sheet().isOpen());
        party.setHeld(APPLE);
        clickHand(1, Slot.ACTION_HAND);
        assertSame(APPLE, second.champion().items().get(Slot.ACTION_HAND));
        assertSame(party.members().get(0), screen.sheet().champion(), "the open sheet stays");

        // The open champion's own box shows their portrait, not hands: a left click there does nothing (#60).
        clickHand(0, Slot.READY_HAND);
        assertTrue(screen.sheet().isOpen());
    }

    // ---- walls ----

    /** A party at (1,1) facing north at wall (1,0), with a closed door at (1,2) behind it. */
    private GameScreen wallScreen(Party p) {
        GameScreen s = new GameScreen(p, Art.none(), sound -> soundsPlayed++, false);
        s.setClock(() -> now);
        BufferedImage img = new BufferedImage(GameScreen.WIDTH, GameScreen.HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        s.render(g);
        g.dispose();
        return s;
    }

    @Test
    void clickingTheWallDecorationAheadRunsItsSensors() {
        DungeonMap map = DungeonMap.fromAscii(0, "###", "#.#", "#D#", "###");
        map.addWallSensor(new WallSensor(1, 0, Direction.SOUTH, WallSensor.TYPE_CLICK, 0, FloorSensor.Effect.TOGGLE,
                false, false, true, false, 0, 1, 2, 0, 10));
        Party p = new Party(map, 1, 1, Direction.NORTH);
        GameScreen s = wallScreen(p);
        Rectangle wall = s.view().wallHit();
        assertNotNull(wall, "the decoration ahead is clickable");
        s.press(wall.x + wall.width / 2, wall.y + wall.height / 2);
        assertEquals(1, soundsPlayed, "the switch clicks");
        while (s.tick()) {
            // door opening
        }
        assertTrue(map.isPassable(1, 2));
    }

    @Test
    void clickingADoorButtonTogglesTheDoor() {
        DungeonMap map = DungeonMap.fromAscii(0, "###", "#D#", "#.#", "###");
        Decorations deco = new Decorations(3, 4);
        deco.setDoor(1, 1, -1, true);
        map.setDecorations(deco);
        Party p = new Party(map, 1, 2, Direction.NORTH);
        GameScreen s = wallScreen(p);
        Rectangle button = s.view().doorButtonHit();
        assertNotNull(button);
        s.press(button.x + button.width / 2, button.y + button.height / 2);
        while (s.tick()) {
            // door opening
        }
        assertTrue(map.isPassable(1, 1));
    }

    @Test
    void theForwardArrowTakesTheStairs() {
        // Level 0: down stairs at (1,1), running north-south, entered from (1,2).
        DungeonMap top = DungeonMap.fromAscii(0, "###", "#.#", "#.#", "###");
        Square[][] a = new Square[3][4];
        Square[][] b = new Square[3][4];
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 4; y++) {
                a[x][y] = x == 1 && y == 1 ? new Square((3 << 5) | 8) : top.get(x, y);
                b[x][y] = x == 1 && y == 1 ? new Square((3 << 5) | 4 | 8) : top.get(x, y);
            }
        }
        DungeonMap upper = new DungeonMap(0, a);
        DungeonMap lower = new DungeonMap(1, b);
        Party p = new Party(List.of(upper, lower), 0, 1, 2, Direction.NORTH);
        GameScreen s = new GameScreen(p, Art.none(), sound -> soundsPlayed++, false);
        s.press(MovementPanel.AREA.x + 40, MovementPanel.AREA.y + 10);
        assertSame(lower, p.map());
        assertEquals(1, p.level());
        assertEquals(2, p.y(), "beside the up stairs, at the open end");
        assertEquals(Direction.SOUTH, p.facing(), "facing away from them");
    }

    @Test
    void fallingIntoAPitScreams() {
        // An open pit at (1,1) over a plain floor one level down; no plates, so the scream is the only sound.
        DungeonMap top = DungeonMap.fromAscii(0, "###", "#O#", "#.#", "###");
        DungeonMap bottom = DungeonMap.fromAscii(1, "###", "#.#", "#.#", "###");
        Party p = new Party(List.of(top, bottom), 0, 1, 2, Direction.NORTH);
        List<Sound> played = new ArrayList<>();
        GameScreen s = new GameScreen(p, Art.none(), played::add, false);
        s.press(MovementPanel.AREA.x + 40, MovementPanel.AREA.y + 10); // forward
        assertSame(bottom, p.map());
        assertEquals(1, played.size(), "the fall's scream");
    }

    @Test
    void arrowsAreIgnoredWhileSheetIsOpen() {
        clickPortrait();
        party.turnRight();
        party.turnRight(); // face south, toward the open corridor
        screen.press(MovementPanel.AREA.x + 40, MovementPanel.AREA.y + 10); // forward arrow
        assertEquals(1, party.y(), "party must not move");
    }

    // ---- the spell area (Sprint 19) -----------------------------------------------------------

    @Test
    void clickingSymbolsEntersThemRowByRow() {
        recruitElija();
        Champion elija = party.members().get(0);
        screen.press(235 + 14 * 3 + 5, 56); // EE
        screen.press(235 + 14 * 3 + 5, 56); // FUL
        assertEquals("" + (char) 99 + (char) 105, elija.symbols());
        screen.press(310, 68); // backspace
        assertEquals("" + (char) 99, elija.symbols());
        render();
    }

    @Test
    void keysEnterSymbolsAndEnterCasts() {
        recruitElija();
        party.setGodMode(true);
        Champion elija = party.members().get(0);
        screen.spellSymbol(0); // LO FUL IR: a fireball
        screen.spellSymbol(3);
        screen.spellSymbol(3);
        screen.backspace();
        screen.spellSymbol(3);
        assertEquals(3, elija.symbols().length());
        screen.pressReturn();
        assertEquals("", elija.symbols(), "cast");
        assertEquals(1, party.map().projectiles().size());
        render();
    }

    @Test
    void theCastBarCastsAndAnEmptySpellDoesNothing() {
        recruitElija();
        party.setGodMode(true);
        screen.press(260, 68);
        assertTrue(party.takeMessages().isEmpty(), "nothing to cast");
        screen.spellSymbol(0);
        screen.spellSymbol(3); // LO FUL: a magic torch
        screen.press(260, 68);
        assertTrue(party.magicalLight() > 0);
    }

    @Test
    void theTabsChooseTheCaster() {
        recruitElija();
        Champion second = Champion.parse(ELIJA.replace("ELIJA", "HALK"), 1);
        party.recruit(new ChampionMirror(1, 0, Direction.SOUTH, second));
        assertEquals(0, party.magicCaster());
        screen.press(285, 45); // the second champion's small tab
        assertEquals(1, party.magicCaster());
        screen.spellSymbol(2);
        assertEquals(1, second.symbols().length());
        assertEquals("", party.members().get(0).symbols());
        screen.press(238, 45); // back to the first
        assertEquals(0, party.magicCaster());
        render();
    }

    @Test
    void nobodyCastsInTheirSleep() {
        recruitElija();
        party.sleep();
        screen.spellSymbol(0);
        screen.press(240, 56);
        assertEquals("", party.members().get(0).symbols());
        render();
    }
}
