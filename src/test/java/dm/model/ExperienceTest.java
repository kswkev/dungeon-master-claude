package dm.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** DM's F304: experience, levels and what a new level brings. */
class ExperienceTest {

    private static Party party(int difficulty) {
        DungeonMap map = DungeonMap.fromAscii(0, "###", "#.#", "###");
        map.setDifficulty(difficulty);
        Party p = new Party(List.of(map), 0, 1, 1, Direction.NORTH);
        p.setRandom(new Random(5));
        p.recruit(new ChampionMirror(0, 0, Direction.SOUTH, Champion.parse(ChampionTest.ELIJA, 0)));
        return p;
    }

    @Test
    void hiddenSkillExperienceAlsoGoesToItsBaseSkill() {
        Party p = party(0);
        Champion c = p.members().get(0);
        long swing = c.experience(Champion.SWING);
        long fighter = c.experience(Champion.FIGHTER);
        p.creatureAttacked(); // in a fight: no halving, and doubled within 25 ticks
        p.addSkillExperience(0, Champion.SWING, 10);
        assertEquals(swing + 20, c.experience(Champion.SWING));
        assertEquals(fighter + 20, c.experience(Champion.FIGHTER));
        assertEquals(2, c.temporaryExperience(Champion.SWING), "an eighth, at least 1");
    }

    @Test
    void fightingSkillsLearnSlowlyOutOfAFightAndFasterOnDeeperLevels() {
        Party quiet = party(0);
        long before = quiet.members().get(0).experience(Champion.SWING);
        quiet.addSkillExperience(0, Champion.SWING, 10);
        assertEquals(before + 5, quiet.members().get(0).experience(Champion.SWING), "halved: no creature has attacked");
        Party deep = party(3);
        before = deep.members().get(0).experience(Champion.PRIEST);
        deep.addSkillExperience(0, Champion.PRIEST, 10);
        assertEquals(before + 30, deep.members().get(0).experience(Champion.PRIEST), "x difficulty 3");
    }

    @Test
    void aNewLevelRaisesTheChampionAndIsAnnounced() {
        Party p = party(0);
        Champion c = p.members().get(0);
        int level = c.baseLevel(Champion.FIGHTER, false);
        int health = c.maxHealth();
        int strength = c.maxStat(Champion.Stat.STRENGTH);
        long needed = (500L << (level - 1)) - c.experience(Champion.FIGHTER);
        p.addSkillExperience(0, Champion.FIGHTER, (int) needed);
        assertEquals(level + 1, c.baseLevel(Champion.FIGHTER, false));
        assertTrue(c.maxHealth() > health, "a fighter level adds 3x the level in health");
        assertTrue(c.maxStat(Champion.Stat.STRENGTH) > strength, "and 1-2 strength");
        List<Party.Message> messages = p.takeMessages();
        assertEquals(1, messages.size());
        assertEquals(c.name() + " JUST GAINED A FIGHTER LEVEL!", messages.get(0).text());
        assertEquals(0, messages.get(0).member());
        assertTrue(p.takeMessages().isEmpty(), "taken once");
    }

    @Test
    void temporaryExperienceFades() {
        Party p = party(0);
        Champion c = p.members().get(0);
        p.addSkillExperience(0, Champion.PRIEST, 80);
        assertEquals(10, c.temporaryExperience(Champion.PRIEST));
        for (int t = 0; t < Upkeep.PERIOD * 3; t++) {
            p.tick();
        }
        assertEquals(7, c.temporaryExperience(Champion.PRIEST), "1 every 64 ticks");
    }
}
