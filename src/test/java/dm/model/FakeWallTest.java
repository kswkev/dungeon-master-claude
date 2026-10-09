package dm.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** #65: fake walls as DM has them (F242, F172, F267): closed ones are solid until a sensor opens them. */
class FakeWallTest {

    private static WallSensor button(int x, int y, Direction side, FloorSensor.Effect effect, int tx, int ty) {
        return new WallSensor(x, y, side, WallSensor.TYPE_CLICK, 0, effect, false, false, false, false, 0,
                tx, ty, 0, -1);
    }

    @Test
    void aClosedFakeWallBlocksUntilAButtonOpensIt() {
        DungeonMap m = DungeonMap.fromAscii(0, "#####", "#.F.#", "#####");
        m.addWallSensor(button(1, 0, Direction.SOUTH, FloorSensor.Effect.TOGGLE, 2, 1));
        Party p = new Party(List.of(m), 0, 1, 1, Direction.EAST);
        assertTrue(m.blocksView(2, 1));
        p.step(Party.Move.FORWARD);
        assertEquals(1, p.x(), "solid while closed");

        m.clickWall(1, 0, Direction.SOUTH, p, i -> 0);
        assertTrue(m.get(2, 1).fakeWallOpen());
        assertFalse(m.get(2, 1).looksSolid(), "drawn as floor");
        assertFalse(m.blocksView(2, 1));
        p.step(Party.Move.FORWARD);
        assertEquals(2, p.x());
    }

    @Test
    void itWontCloseOnTheParty() {
        DungeonMap m = DungeonMap.fromAscii(0, "#####", "#.f.#", "#####");
        m.addWallSensor(button(1, 0, Direction.SOUTH, FloorSensor.Effect.CLEAR, 2, 1));
        Party p = new Party(List.of(m), 0, 2, 1, Direction.EAST);
        m.clickWall(1, 0, Direction.SOUTH, p, i -> 0);
        assertTrue(m.get(2, 1).fakeWallOpen(), "DM's F242 waits while the party stands there");
        p.step(Party.Move.FORWARD);
        p.tick();
        p.tick();
        assertFalse(m.get(2, 1).fakeWallOpen(), "and closes once it has gone");
    }

    @Test
    void anImaginaryWallIsWalkedThrough() {
        DungeonMap m = DungeonMap.fromAscii(0, "#####", "#.I.#", "#####");
        Party p = new Party(List.of(m), 0, 1, 1, Direction.EAST);
        assertTrue(m.get(2, 1).looksSolid(), "drawn as a wall");
        p.step(Party.Move.FORWARD);
        assertEquals(2, p.x());
    }
}
