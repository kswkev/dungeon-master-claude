package dm.ui;

import dm.model.Direction;
import dm.model.Party;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** #38: DM's champion icon boxes and the figure each champion's facing picks. */
class FormationBoxTest {

    @Test
    void theFigureFollowsTheChampionsFacingRelativeToTheParty() {
        assertEquals(0, FormationBox.iconIndex(Direction.NORTH, Direction.NORTH), "facing with the party");
        assertEquals(1, FormationBox.iconIndex(Direction.EAST, Direction.NORTH), "turned right");
        assertEquals(2, FormationBox.iconIndex(Direction.WEST, Direction.EAST), "turned round");
        assertEquals(3, FormationBox.iconIndex(Direction.NORTH, Direction.EAST), "turned left");
    }

    @Test
    void clicksFollowDmsBoxes() {
        assertEquals(-1, FormationBox.hitTest(280, 5), "left of DM's boxes");
        assertEquals(Party.FRONT_LEFT, FormationBox.hitTest(281, 0));
        assertEquals(Party.FRONT_LEFT, FormationBox.hitTest(299, 13));
        assertEquals(Party.FRONT_RIGHT, FormationBox.hitTest(301, 13));
        assertEquals(Party.BACK_RIGHT, FormationBox.hitTest(319, 28));
        assertEquals(Party.BACK_LEFT, FormationBox.hitTest(281, 15));
    }
}
