package dm.ui;

import dm.data.DungeonFile;
import dm.model.Direction;
import dm.model.DungeonMap;
import dm.model.Item;
import dm.model.ItemCatalog;
import dm.model.Party;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The Sprint 11 bug reports, replayed on the user's own Level 2. The game
 * files are copyrighted and not in the repository, so without them (as on
 * CI) these tests are skipped.
 */
class RealDungeonTest {

    private static final Path DUNGEON = Path.of("data/DUNGEON.DAT");
    private static final Path GRAPHICS = Path.of("data/GRAPHICS.DAT");

    private DungeonFile dungeon;

    @BeforeEach
    void load() throws Exception {
        assumeTrue(Files.exists(DUNGEON), "needs the original DUNGEON.DAT");
        dungeon = DungeonFile.load(DUNGEON);
    }

    /** A party on Level 2 (map 1). */
    private Party level2(int x, int y, Direction facing) {
        return new Party(dungeon.maps(), 1, x, y, facing);
    }

    private static void settleDoors(DungeonMap map) {
        while (map.tickDoors().moved()) {
            // let the doors finish moving
        }
    }

    /** #14: the button at (6,4) north reveals an alcove with a falchion, which then stays. */
    @Test
    void theRevealedAlcoveStays() {
        Party p = level2(6, 3, Direction.SOUTH);
        DungeonMap map = p.map();
        int shown = map.wallOrnament(6, 4, Direction.NORTH);
        map.clickWall(6, 4, Direction.NORTH, p, i -> 0);
        int alcove = map.wallOrnament(6, 4, Direction.NORTH);
        assertTrue(alcove != shown && DungeonMap.isAlcove(alcove), "revealed");
        map.clickWall(6, 4, Direction.NORTH, p, i -> 0);
        assertNotNull(p.held());
        assertEquals("FALCHION", p.held().name());
        assertEquals(alcove, map.wallOrnament(6, 4, Direction.NORTH), "still an alcove");
    }

    /** #16: the lever at (6,8) north closes and reopens the pit at (7,8). */
    @Test
    void theLeverWorksThePit() {
        Party p = level2(6, 7, Direction.SOUTH);
        DungeonMap map = p.map();
        assertTrue(map.isPitOpen(7, 8));
        map.clickWall(6, 8, Direction.NORTH, p, i -> 0);
        assertFalse(map.isPitOpen(7, 8));
        map.clickWall(6, 8, Direction.NORTH, p, i -> 0);
        assertTrue(map.isPitOpen(7, 8));
    }

    /** #16: the lever at (4,10) south opens and closes the door at (5,9). */
    @Test
    void theLeverWorksTheDoor() {
        Party p = level2(4, 11, Direction.NORTH);
        DungeonMap map = p.map();
        boolean was = map.isPassable(5, 9);
        map.clickWall(4, 10, Direction.SOUTH, p, i -> 0);
        settleDoors(map);
        assertEquals(!was, map.isPassable(5, 9));
        map.clickWall(4, 10, Direction.SOUTH, p, i -> 0);
        settleDoors(map);
        assertEquals(was, map.isPassable(5, 9));
    }

    /** #17: an item on the plate at (25,1) holds the door at (27,0) open. */
    @Test
    void anItemHoldsThePlateDown() {
        Party p = level2(24, 1, Direction.EAST);
        DungeonMap map = p.map();
        assertFalse(map.isPassable(27, 0));
        Item sword = ItemCatalog.item(Item.Category.WEAPON, 10);
        map.dropItem(25, 1, 0, sword);
        settleDoors(map);
        assertTrue(map.isPassable(27, 0), "opened by the item");
        map.pickUpItem(25, 1, 0);
        settleDoors(map);
        assertFalse(map.isPassable(27, 0), "closed when the plate is empty");
    }

    /** Pits: walking into the open pit at (7,8) drops the party onto Level 3. */
    @Test
    void thePitDropsThePartyALevel() {
        Party p = level2(7, 7, Direction.SOUTH);
        DungeonMap.StepResult r = p.step(Party.Move.FORWARD);
        assertNotNull(r, "(7,7) leads onto the pit");
        assertTrue(r.fell());
        assertEquals(2, p.level());
        assertSame(dungeon.maps().get(2), p.map());
        assertFalse(p.map().get(p.x(), p.y()).looksSolid());
    }

    /** #20: the scope-0 teleporter at (13,16) lets the party walk onto it... */
    @Test
    void theItemsOnlyTeleporterLeavesThePartyAlone() {
        Party p = level2(12, 16, Direction.EAST);
        DungeonMap.StepResult r = p.step(Party.Move.FORWARD);
        assertFalse(r.teleported());
        assertEquals(13, p.x());
        assertEquals(16, p.y());
    }

    /** ...but a thrown item is sent to the plate at (14,14), which closes the pit at (13,15), as in the original. */
    @Test
    void anItemThrownIntoTheTeleporterClosesThePit() {
        Party p = level2(12, 16, Direction.EAST);
        DungeonMap map = p.map();
        assertTrue(map.isPitOpen(13, 15));
        map.throwItem(ItemCatalog.item(Item.Category.WEAPON, 10), 12, 16, Direction.EAST, false, Party.THROW_RANGE);
        for (int i = 0; i < Party.THROW_RANGE + 1; i++) {
            map.tickProjectiles();
        }
        assertTrue(map.hasItems(14, 14), "teleported onto the plate");
        assertFalse(map.isPitOpen(13, 15));
    }

    /** #20's sensor bits: the HOLD + revert plate at (25,3) keeps the pit at (24,5) shut while an item is on it. */
    @Test
    void anItemOnThePlateHoldsThePitShut() {
        DungeonMap map = level2(25, 4, Direction.NORTH).map();
        assertTrue(map.isPitOpen(24, 5));
        map.dropItem(25, 3, 0, ItemCatalog.item(Item.Category.WEAPON, 10));
        assertFalse(map.isPitOpen(24, 5));
        map.pickUpItem(25, 3, 0);
        assertTrue(map.isPitOpen(24, 5));
    }

    /** #15: the gold keyhole at (0,3) north is centred at eye level, row 48 of the viewport. */
    @Test
    void theKeyholeHangsAtEyeLevel() {
        assumeTrue(Files.exists(GRAPHICS), "needs the original GRAPHICS.DAT");
        Rectangle hit = render(level2(0, 2, Direction.SOUTH)).wallHit();
        assertNotNull(hit);
        assertEquals(48, hit.y - ViewRenderer.VIEWPORT.y + hit.height / 2, 1.0);
    }

    /** #15: the lever at (4,10) south is drawn at the same height in both positions. */
    @Test
    void theLeverDoesntJumpWhenPulled() {
        assumeTrue(Files.exists(GRAPHICS), "needs the original GRAPHICS.DAT");
        Party p = level2(4, 11, Direction.NORTH);
        Rectangle before = render(p).wallHit();
        p.map().clickWall(4, 10, Direction.SOUTH, p, i -> 0);
        Rectangle after = render(p).wallHit();
        assertNotNull(before);
        assertNotNull(after);
        assertEquals(before.y + before.height / 2, after.y + after.height / 2);
        assertEquals(48, before.y - ViewRenderer.VIEWPORT.y + before.height / 2, 1.0);
    }

    private static ViewRenderer render(Party party) {
        ViewRenderer view = ViewRenderer.forArt(Art.load(GRAPHICS));
        BufferedImage img = new BufferedImage(320, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        view.draw(g, party);
        g.dispose();
        return view;
    }
}
