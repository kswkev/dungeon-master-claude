package dm.data;

import dm.model.Champion;
import dm.model.ChampionMirror;
import dm.model.Direction;
import dm.model.DungeonMap;
import dm.model.FloorSensor;
import dm.model.Item;
import dm.model.ItemCatalog;
import dm.model.Party;
import dm.model.Slot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaveGamesTest {

    private static final String ELIJA = "ELIJA\nLION OF YAITOPYA\n\nM\nAADMACEEAABG\nDCCKCICKCEDFCI\nBBCAAAAACBECAAAA";
    private static final Item SWORD = ItemCatalog.item(Item.Category.WEAPON, 10);
    private static final Item APPLE = ItemCatalog.item(Item.Category.JUNK, 29);
    private static final Item TORCH = ItemCatalog.item(Item.Category.WEAPON, 2, 9);

    @TempDir
    Path dir;

    private SaveGames saves;
    private DungeonMap map;
    private Party party;
    private ChampionMirror second;

    /** Corridor y = 1: door (1,1), plate (3,1) holding the door open, party at (4,1) facing west. */
    @BeforeEach
    void setUp() {
        saves = new SaveGames(dir);
        map = DungeonMap.fromAscii(0, "#######", "#D....#", "#######");
        map.addSensor(new FloorSensor(3, 1, FloorSensor.TYPE_ANY, FloorSensor.Effect.HOLD, false, false, false,
                1, 1, 1));
        ChampionMirror first = new ChampionMirror(4, 0, Direction.SOUTH, Champion.parse(ELIJA, 0));
        second = new ChampionMirror(5, 0, Direction.SOUTH, Champion.parse(ELIJA, 1));
        party = new Party(List.of(map), 0, 4, 1, Direction.WEST);
        party.recruit(first);
        party.recruit(second);
        Champion c = first.champion();
        c.place(Slot.ACTION_HAND, TORCH);
        c.place(Slot.BACKPACK_3, SWORD);
        party.setHeld(APPLE);
        map.dropItem(3, 1, 0, SWORD); // presses the plate: the door opens
        while (map.tickDoors().moved()) {
            // let it open
        }
        for (int i = 0; i < 130; i++) {
            party.tick(); // the clock runs and the champions get hungrier
        }
        Champion dead = second.champion();
        dead.takeDamage(dead.health());
        party.bury();
    }

    @Test
    void aSavedGameComesBackAsItWas() throws IOException {
        saves.save(2, party);
        Party loaded = saves.load(2);
        assertNotSame(party, loaded);
        assertEquals(4, loaded.x());
        assertEquals(1, loaded.y());
        assertEquals(Direction.WEST, loaded.facing());
        assertEquals(party.time(), loaded.time());
        assertEquals(APPLE, loaded.held());

        Champion before = party.members().get(0);
        Champion after = loaded.members().get(0);
        assertEquals(before.name(), after.name());
        assertEquals(before.food(), after.food());
        assertEquals(before.water(), after.water());
        assertEquals(before.stamina(), after.stamina());
        assertEquals(before.items(), after.items());
        assertEquals(0, loaded.members().get(1).health(), "the dead stay dead");
        assertTrue(loaded.bury().isEmpty(), "and already buried");

        DungeonMap m = loaded.map();
        assertTrue(m.isPassable(1, 1), "the door is still open");
        assertTrue(m.sensors().get(0).pressed(), "the sword still presses the plate");
        assertEquals(List.of(SWORD), m.itemsAt(3, 1, 0));
        // Front right, facing west, is the north-west cell.
        assertTrue(m.itemsAt(4, 1, 0).stream().anyMatch(i -> i.name().equals("BONES")), "the bones lie where she fell");

        m.pickUpItem(3, 1, 0);
        while (m.tickDoors().moved()) {
            // the plate is released: the loaded map still works
        }
        assertFalse(m.isPassable(1, 1));
    }

    @Test
    void theHeaderNamesTheLevelAndChampions() throws IOException {
        assertNull(saves.header(1), "empty");
        saves.save(1, party);
        SaveGames.Header h = saves.header(1);
        assertEquals(0, h.level());
        assertEquals(party.time(), h.gameTime());
        assertEquals(List.of("ELIJA", "ELIJA"), h.champions());
        assertTrue(Files.exists(dir.resolve("slot1.dmsave")));
    }

    @Test
    void savingAgainOverwritesTheSlot() throws IOException {
        saves.save(3, party);
        party.turnLeft();
        saves.save(3, party);
        assertEquals(Direction.SOUTH, saves.load(3).facing());
    }

    @Test
    void emptyOrBrokenSlotsAreRefused() throws IOException {
        assertThrows(IOException.class, () -> saves.load(4));
        Files.writeString(dir.resolve("slot4.dmsave"), "not a saved game");
        assertNull(saves.header(4));
        assertThrows(IOException.class, () -> saves.load(4));
        assertThrows(IllegalArgumentException.class, () -> saves.file(5));
    }
}
