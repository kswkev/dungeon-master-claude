package dm.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * DM's fuse sequence (ReDMCSB STARTND2.C F446), started when FUSE catches
 * Lord Chaos on (x, y). It is a script the screen plays one step per game
 * tick ({@link #next}). Most steps do something and then let the game run
 * one tick (DM's F445: the timeline, the view, the sounds). DM played
 * these as fast as the machine could (BUG0_71); here each takes a tick.
 * The waits after each closing message keep the game still, as DM's
 * delays do.
 *
 * <ol>
 *   <li>The party is lit and shielded. Lord Chaos gets 10000 health, stands
 *       centred facing the party, and the fluxcages on his square and the
 *       party's go.</li>
 *   <li>Fireballs of attack 55, 95 ... 255 burst on him, then he becomes
 *       Lord Order with a buzz, and harm non-material bursts at 55 ... 255.</li>
 *   <li>Three cycles of four switches between Lord Order and Lord Chaos,
 *       each with a buzz, held 3, 2, then 1 ticks.</li>
 *   <li>A fireball and a harm non-material at 255, and he becomes the Grey
 *       Lord. Fluxcages are no longer drawn, and every other group on the
 *       map is gone.</li>
 *   <li>The texts on square (0,0) of the map are printed in white, one at a
 *       time in the order of their first letter (not printed), each on a
 *       cleared message area, 13 seconds apart. Ten seconds later the game
 *       is won and the end screen shows.</li>
 * </ol>
 */
public final class Endgame {

    /** DM's 780 vertical blanks (13 s) after each message, in game ticks of 170 ms. */
    static final int MESSAGE_TICKS = 76;
    /** DM's 600 vertical blanks (10 s) before the end screen. */
    static final int FINAL_TICKS = 59;

    private final Party party;
    private final DungeonMap map;
    private final int x;
    private final int y;
    private final Group lord;
    /** What is left of the script: each entry runs an action, then lets the game tick (or not, for a wait). */
    private final Deque<Step> script = new ArrayDeque<>();
    private boolean fluxcagesHidden;
    private boolean won;

    private record Step(Runnable action, boolean tick) {
    }

    Endgame(Party party, DungeonMap map, int x, int y) {
        this.party = party;
        this.map = map;
        this.x = x;
        this.y = y;
        this.lord = map.groupAt(x, y);
        build();
    }

    private void update(Runnable action) {
        script.add(new Step(action, true));
    }

    private void update() {
        update(() -> { });
    }

    private void wait(int ticks) {
        for (int i = 0; i < ticks; i++) {
            script.add(new Step(() -> { }, false));
        }
    }

    private void explode(int type, int attack) {
        Flight.explode(party, map, type, attack, x, y, Group.CENTRED);
    }

    private void buzz() {
        party.dungeon().creatures().soundAt(party, CreatureAI.SOUND_BUZZ, map, x, y);
    }

    private void become(CreatureType type) {
        if (lord != null) {
            lord.setType(type);
        }
        party.dungeon().creatures().changed(party);
    }

    private void build() {
        update(party::shineForTheEndgame);
        update(() -> {
            if (lord != null) {
                lord.setHealth(0, 10_000);
                lord.setCells(Group.CENTRED);
                lord.face(party.facing().opposite());
            }
            map.explosionList().removeIf(e -> e.type() == Explosion.FLUXCAGE
                    && (e.x() == x && e.y() == y || e.x() == party.x() && e.y() == party.y()));
        });
        for (int attack = 55; attack <= 255; attack += 40) {
            int a = attack;
            update(() -> explode(Explosion.FIREBALL, a));
        }
        update(() -> {
            buzz();
            become(CreatureType.LORD_ORDER);
        });
        for (int attack = 55; attack <= 255; attack += 40) {
            int a = attack;
            update(() -> explode(Explosion.HARM_NON_MATERIAL, a));
        }
        for (int cycle = 3; cycle >= 1; cycle--) {
            for (int switchCount = 4; switchCount >= 1; switchCount--) {
                CreatureType type = (switchCount & 1) != 0 ? CreatureType.LORD_ORDER : CreatureType.LORD_CHAOS;
                update(() -> {
                    buzz();
                    become(type);
                });
                for (int i = 1; i < cycle; i++) {
                    update();
                }
            }
        }
        update(() -> {
            explode(Explosion.FIREBALL, 255);
            explode(Explosion.HARM_NON_MATERIAL, 255);
        });
        update(() -> become(CreatureType.GREY_LORD));
        update(() -> fluxcagesHidden = true);
        update(() -> {
            for (Group g : new ArrayList<>(map.groups())) {
                if (g != lord) {
                    map.removeGroup(g);
                }
            }
            party.dungeon().creatures().changed(party);
        });
        for (String text : closingTexts(map.endgameTexts())) {
            update(() -> {
                party.message(null, Party.MESSAGE_CLEAR);
                party.message(text, Party.MESSAGE_WHITE);
            });
            wait(MESSAGE_TICKS);
        }
        wait(FINAL_TICKS);
        script.add(new Step(() -> won = true, false));
    }

    /**
     * F446: the texts in the order of their first letter, A, B, C ...,
     * without it; it stops at the first letter with no text.
     */
    static List<String> closingTexts(List<String> texts) {
        List<String> out = new ArrayList<>();
        for (char letter = 'A'; ; letter++) {
            String found = null;
            for (String t : texts) {
                if (!t.isEmpty() && t.charAt(0) == letter) {
                    found = t.substring(1);
                    break;
                }
            }
            if (found == null || out.size() >= texts.size()) {
                return out;
            }
            out.add(found.startsWith("\n") ? found.substring(1) : found);
        }
    }

    /**
     * Plays the next step. Returns whether the game should run a tick now
     * (DM's F445); waits return false and keep everything still.
     */
    public boolean next() {
        Step step = script.poll();
        if (step == null) {
            return false;
        }
        step.action().run();
        return step.tick();
    }

    /** Whether the sequence is over: the game is won and the end screen shows. */
    public boolean won() {
        return won;
    }

    /** DM's G077: fluxcages aren't drawn any more once the Grey Lord stands there. */
    public boolean fluxcagesHidden() {
        return fluxcagesHidden;
    }

    /** Where Lord Chaos stands. */
    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public DungeonMap map() {
        return map;
    }
}
