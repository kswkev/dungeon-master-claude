package dm;

import dm.data.DungeonFile;
import dm.model.Direction;
import dm.model.Party;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Sprint 22: the testing options -Ddm.start, -Ddm.recruit and -Ddm.give (not in DM). */
class MainTest {

    private static final Path DUNGEON = Path.of("data/DUNGEON.DAT");

    @AfterEach
    void clear() {
        System.clearProperty("dm.start");
        System.clearProperty("dm.recruit");
        System.clearProperty("dm.give");
    }

    @Test
    void theOptionsStartTheGameNextToLordChaosWithTheFirestaff() throws Exception {
        assumeTrue(Files.exists(DUNGEON), "needs the original DUNGEON.DAT");
        System.setProperty("dm.start", "13,15,2,N");
        System.setProperty("dm.recruit", "all");
        System.setProperty("dm.give", "weapon:45,junk:51,nonsense:1");
        Party party = Main.debugParty(DungeonFile.load(DUNGEON));
        assertEquals(12, party.level());
        assertEquals(15, party.x());
        assertEquals(2, party.y());
        assertEquals(Direction.NORTH, party.facing());
        assertEquals(4, party.members().size());
        assertTrue(party.members().get(0).items().values().stream()
                .anyMatch(i -> i.category() == dm.model.Item.Category.WEAPON && i.type() == 45));
    }

    @Test
    void withoutOptionsThePartyStartsAsUsual() throws Exception {
        assumeTrue(Files.exists(DUNGEON), "needs the original DUNGEON.DAT");
        DungeonFile dungeon = DungeonFile.load(DUNGEON);
        Party party = Main.debugParty(dungeon);
        assertEquals(0, party.level());
        assertEquals(dungeon.startX(), party.x());
        assertTrue(party.members().isEmpty());
    }
}
