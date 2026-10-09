package dm.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Teleporters on a corridor along y = 1, from x = 1 to 7. */
class TeleporterTest {

    private static final Item SWORD = ItemCatalog.item(Item.Category.WEAPON, 10);
    private static final int OPEN = (5 << 5) | 8 | 4;
    private static final int CLOSED = (5 << 5) | 4;
    private static final int BOTH = Teleporter.SCOPE_EVERYTHING;

    /** A level with the given teleporter square bytes at (x, 1). */
    private static DungeonMap level(int level, int... squaresAt) {
        DungeonMap ascii = DungeonMap.fromAscii(level, "#########", "#.......#", "#########");
        Square[][] squares = new Square[ascii.width()][ascii.height()];
        for (int x = 0; x < ascii.width(); x++) {
            for (int y = 0; y < ascii.height(); y++) {
                squares[x][y] = ascii.get(x, y);
            }
        }
        for (int i = 0; i + 1 < squaresAt.length; i += 2) {
            squares[squaresAt[i]][1] = new Square(squaresAt[i + 1]);
        }
        return new DungeonMap(level, squares);
    }

    private static Teleporter to(int x, int map, int tx, int rotation, boolean absolute, int scope) {
        return new Teleporter(x, 1, map, tx, 1, rotation, absolute, scope, true);
    }

    @Test
    void thePartyIsMovedAndTurned() {
        DungeonMap m = level(0, 2, OPEN);
        m.addTeleporter(to(2, 0, 6, 1, false, BOTH));
        Party p = new Party(List.of(m), 0, 1, 1, Direction.EAST);
        DungeonMap.StepResult r = p.step(Party.Move.FORWARD);
        assertTrue(r.teleported());
        assertTrue(r.buzz(), "#53: an audible teleporter buzzes");
        assertFalse(r.click(), "#53: not the switch click");
        assertFalse(r.levelChanged());
        assertEquals(6, p.x());
        assertEquals(Direction.SOUTH, p.facing(), "a quarter-turn clockwise from east");
    }

    @Test
    void anAbsoluteRotationSetsTheFacing() {
        DungeonMap m = level(0, 2, OPEN);
        m.addTeleporter(to(2, 0, 6, 3, true, BOTH));
        Party p = new Party(List.of(m), 0, 1, 1, Direction.EAST);
        p.step(Party.Move.FORWARD);
        assertEquals(Direction.WEST, p.facing());
    }

    @Test
    void aTeleporterCanLeadToAnotherLevel() {
        DungeonMap upper = level(0, 2, OPEN);
        DungeonMap lower = level(1);
        upper.addTeleporter(to(2, 1, 5, 0, false, BOTH));
        Party p = new Party(List.of(upper, lower), 0, 1, 1, Direction.EAST);
        DungeonMap.StepResult r = p.step(Party.Move.FORWARD);
        assertTrue(r.levelChanged());
        assertSame(lower, p.map());
        assertEquals(5, p.x());
    }

    @Test
    void aClosedTeleporterDoesNothingUntilASensorOpensIt() {
        DungeonMap m = level(0, 3, CLOSED);
        m.addTeleporter(to(3, 0, 6, 0, false, BOTH));
        m.addSensor(new FloorSensor(1, 1, FloorSensor.TYPE_ANY, FloorSensor.Effect.SET, false, false, false,
                3, 1, -1));
        Party p = new Party(List.of(m), 0, 2, 1, Direction.EAST);
        assertFalse(p.step(Party.Move.FORWARD).teleported());
        assertEquals(3, p.x());

        p.turnRight();
        p.turnRight();
        p.move(Party.Move.FORWARD);
        p.move(Party.Move.FORWARD); // onto the plate at (1,1)
        assertTrue(m.isTeleporterOpen(3, 1));
        p.move(Party.Move.BACKWARD);
        assertTrue(p.step(Party.Move.BACKWARD).teleported());
        assertEquals(6, p.x());
    }

    @Test
    void anItemsOnlyTeleporterLetsThePartyThroughButMovesItems() {
        DungeonMap m = level(0, 3, OPEN);
        m.addTeleporter(to(3, 0, 6, 0, false, Teleporter.SCOPE_ITEMS));
        Party p = new Party(List.of(m), 0, 2, 1, Direction.EAST);
        assertFalse(p.step(Party.Move.FORWARD).teleported());
        assertEquals(3, p.x());

        m.dropItem(3, 1, 0, SWORD);
        assertFalse(m.hasItems(3, 1));
        assertEquals(List.of(SWORD), m.itemsAt(6, 1, 0));
    }

    @Test
    void scopesFollowDm() {
        boolean[][] moves = { // item, creature, party
                {true, false, false}, {false, true, false}, {true, false, true}, {true, true, true}};
        for (int scope = 0; scope < 4; scope++) {
            Teleporter t = to(1, 0, 2, 0, false, scope);
            assertEquals(moves[scope][0], t.moves(Teleporter.Kind.ITEM), "scope " + scope);
            assertEquals(moves[scope][1], t.moves(Teleporter.Kind.CREATURE), "scope " + scope);
            assertEquals(moves[scope][2], t.moves(Teleporter.Kind.PARTY), "scope " + scope);
        }
    }

    @Test
    void anItemsAndPartyTeleporterMovesTheParty() {
        DungeonMap m = level(0, 2, OPEN);
        m.addTeleporter(to(2, 0, 6, 0, false, Teleporter.SCOPE_ITEMS_AND_PARTY));
        Party p = new Party(List.of(m), 0, 1, 1, Direction.EAST);
        assertTrue(p.step(Party.Move.FORWARD).teleported());
        assertEquals(6, p.x());
    }

    @Test
    void aCreaturesOnlyTeleporterLeavesItemsBehind() {
        DungeonMap m = level(0, 3, OPEN);
        m.addTeleporter(to(3, 0, 6, 0, false, Teleporter.SCOPE_CREATURES));
        new Dungeon(List.of(m));
        m.dropItem(3, 1, 0, SWORD);
        assertEquals(List.of(SWORD), m.itemsAt(3, 1, 0));
    }

    @Test
    void aThrownItemFliesOnFromTheTarget() {
        DungeonMap upper = level(0, 3, OPEN);
        DungeonMap lower = level(1);
        upper.addTeleporter(to(3, 1, 2, 0, false, BOTH));
        Party p = new Party(List.of(upper, lower), 0, 1, 1, Direction.EAST);
        Flight.launch(p, SWORD, upper, 1, 1, 1, Direction.EAST, 200, 100, 5);
        p.tick(); // to (2,1)
        p.tick(); // across it
        p.tick(); // into the teleporter: on to the lower level's (2,1)
        assertTrue(upper.projectiles().isEmpty());
        assertEquals(1, lower.projectiles().size());
        assertEquals(2, lower.projectiles().get(0).x());
        assertEquals(Direction.EAST, lower.projectiles().get(0).direction());
        for (int i = 0; i < 40; i++) {
            p.tick();
        }
        assertTrue(lower.hasItems(7, 1), "it kept flying east, to the far wall");
    }

    @Test
    void aSpinnerOnlyTurnsTheParty() {
        DungeonMap m = level(0, 2, OPEN);
        m.addTeleporter(to(2, 0, 2, 2, false, BOTH));
        Party p = new Party(List.of(m), 0, 1, 1, Direction.EAST);
        DungeonMap.StepResult r = p.step(Party.Move.FORWARD);
        assertTrue(r.teleported());
        assertFalse(r.buzz(), "#53: a spinner is silent");
        assertEquals(2, p.x());
        assertEquals(Direction.WEST, p.facing(), "turned round once, not spun forever");
    }

    @Test
    void teleportersChain() {
        DungeonMap m = level(0, 2, OPEN, 5, OPEN);
        m.addTeleporter(to(2, 0, 5, 0, false, BOTH));
        m.addTeleporter(to(5, 0, 7, 0, false, BOTH));
        Party p = new Party(List.of(m), 0, 1, 1, Direction.EAST);
        p.step(Party.Move.FORWARD);
        assertEquals(7, p.x());
    }
}
