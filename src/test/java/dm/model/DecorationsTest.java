package dm.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DecorationsTest {

    /** #73: a save from before #67 kept -1 on a text's side; it still shows the inscription stone. */
    @Test
    void aTextIsAlwaysTheInscriptionStone() {
        Decorations d = new Decorations(3, 3);
        d.setWall(1, 1, Direction.SOUTH, -1, "I AM ALL\nI AM NONE");
        assertEquals(Decorations.INSCRIPTION, d.wall(1, 1, Direction.SOUTH));
        d.setTextVisible(1, 1, Direction.SOUTH, false);
        assertEquals(-1, d.wall(1, 1, Direction.SOUTH), "hidden");
        d.setWall(1, 1, Direction.NORTH, 7, null);
        assertEquals(7, d.wall(1, 1, Direction.NORTH), "no text: its own decoration");
    }
}
