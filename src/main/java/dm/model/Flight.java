package dm.model;

import java.util.ArrayList;
import java.util.Random;

/**
 * Things in flight, ported from ReDMCSB's PROJEXPL.C: F212 creates a
 * projectile, F219 moves it, F217 decides what it hits and F216 how hard.
 *
 * <p>A projectile moves half a square per game tick on the party's map (DM
 * 1.x moves those elsewhere every 3 ticks). Before each move but the first
 * it may hit a champion (on the party's square) or a creature standing on
 * its cell; after that, if its kinetic energy is spent it drops where it is,
 * and otherwise it loses its step energy from both its energy and its
 * attack. Leaving a square it may hit a wall (it drops in front of it) and
 * crossing into the middle of a square a closed door. It flies over pits,
 * and teleporters that take things send it on, turned. Whatever it was, it
 * ends up on the floor (or in the creature that caught it), with a metallic
 * thud for a weapon and a wooden one for anything else.
 *
 * <p>Not yet: explosions (spells, and bombs and venom potions bursting),
 * the party or creatures walking into projectiles (F266), and poison darts'
 * poison.
 */
final class Flight {

    private Flight() {
    }

    /** DM's F212: {@code item} takes flight from cell {@code cell} of (x, y), heading {@code direction}. */
    static void launch(Party party, Item item, DungeonMap m, int x, int y, int cell, Direction direction,
                       int kineticEnergy, int attack, int stepEnergy) {
        m.projectileList().add(new Projectile(item, x, y, cell, direction, kineticEnergy, attack, stepEnergy,
                party.time() + 1));
        party.dungeon().creatures().changed(party);
    }

    /** Moves every projectile whose move is due, on every map. Returns whether a landing clicked a sensor. */
    static boolean tick(Party party) {
        boolean click = false;
        for (DungeonMap m : party.dungeon().maps()) {
            for (Projectile p : new ArrayList<>(m.projectileList())) {
                if (p.nextMove <= party.time() && m.projectileList().contains(p)) {
                    click |= move(party, m, p);
                }
            }
        }
        return click;
    }

    /** DM's F219. Returns whether the projectile landed on a sensor that clicked. */
    private static boolean move(Party party, DungeonMap m, Projectile p) {
        CreatureAI creatures = party.dungeon().creatures();
        if (p.ignoreImpacts) {
            p.ignoreImpacts = false;
        } else {
            if (m == party.map() && p.x == party.x() && p.y == party.y() && party.memberInCell(p.cell) >= 0) {
                return hitChampion(party, m, p, party.memberInCell(p.cell));
            }
            Group g = m.groupAt(p.x, p.y);
            if (g != null && CreatureAI.creatureOrdinalInCell(g, p.cell) != 0 && !g.type().nonMaterial()) {
                return hitCreature(party, m, p, g, CreatureAI.creatureOrdinalInCell(g, p.cell) - 1);
            }
            if (p.kineticEnergy <= p.stepEnergy) {
                return land(party, m, p, p.x, p.y, p.cell, null, false);
            }
            p.kineticEnergy -= p.stepEnergy;
            p.attack = Math.max(0, p.attack - p.stepEnergy);
        }
        int dir = p.direction.ordinal();
        boolean toNextSquare = dir == p.cell || ((dir + 1) & 3) == p.cell;
        int nx = p.x + p.direction.dx;
        int ny = p.y + p.direction.dy;
        if (toNextSquare && blocks(m, p.x, p.y, nx, ny)) {
            return land(party, m, p, p.x, p.y, p.cell, null, true); // against the wall
        }
        int cell = ((dir & 1) == (p.cell & 1) ? p.cell - 1 : p.cell + 1) & 3;
        DungeonMap to = m;
        if (toNextSquare) {
            p.x = nx;
            p.y = ny;
            p.cell = cell;
            for (int hop = 0; hop < 16; hop++) { // F267: teleporters send it on
                Teleporter t = to.activeTeleporter(p.x, p.y, Teleporter.Kind.ITEM);
                Dungeon.Location target = t == null ? null : to.destination(t);
                if (target == null) {
                    break;
                }
                if (t.audible()) {
                    creatures.soundAt(party, CreatureAI.SOUND_BUZZ, target.map(), target.x(), target.y());
                }
                boolean self = target.map() == to && target.x() == p.x && target.y() == p.y;
                p.direction = t.turn(p.direction);
                if (!t.absolute()) {
                    p.cell = (p.cell + t.rotation()) & 3;
                }
                to = target.map();
                p.x = target.x();
                p.y = target.y();
                if (self) {
                    break;
                }
            }
            if (to != m) {
                m.projectileList().remove(p);
                to.projectileList().add(p);
            }
        } else {
            if (m.get(p.x, p.y).type() == SquareType.DOOR && hitsDoor(party, m, p)) {
                return land(party, m, p, p.x, p.y, p.cell, null, true);
            }
            p.cell = cell;
        }
        p.nextMove = party.time() + (to == party.map() ? 1 : 3);
        if (to == party.map() || m == party.map()) {
            creatures.changed(party);
        }
        return false;
    }

    /** F219's walls: a wall, a fake wall that is neither open nor imaginary, or stairs met from stairs. */
    private static boolean blocks(DungeonMap m, int fromX, int fromY, int x, int y) {
        Square sq = m.get(x, y);
        return switch (sq.type()) {
            case WALL -> true;
            case FAKEWALL -> (sq.raw() & 0x05) == 0;
            case STAIRS -> m.get(fromX, fromY).type() == SquareType.STAIRS;
            default -> false;
        };
    }

    /**
     * F217 for a door in the projectile's way. An open or broken door (or
     * one barely closed) lets it by, and a portcullis lets small things
     * through often enough; otherwise it hits the door, which may break.
     */
    private static boolean hitsDoor(Party party, DungeonMap m, Projectile p) {
        int state = m.doorState(p.x, p.y);
        Random random = party.random();
        if (state == DungeonMap.DOOR_BROKEN || state <= 1) {
            return false;
        }
        if (m.doorLetsProjectilesThrough(p.x, p.y) && p.attack > random.nextInt(128)
                && ItemCatalog.passesThroughDoors(p.item())) {
            return false;
        }
        int attack = impactAttack(p, random) + 1;
        if (m.breakDoor(p.x, p.y, attack + random.nextInt(attack), false, 0)) {
            party.dungeon().creatures().changed(party);
        }
        return true;
    }

    /** F217 for a champion: hurt in the head or torso by a blunt blow. */
    private static boolean hitChampion(Party party, DungeonMap m, Projectile p, int member) {
        int attack = impactAttack(p, party.random());
        party.dungeon().creatures().hurtChampion(party, member, attack,
                Champion.WOUND_HEAD | Champion.WOUND_TORSO, ATTACK_BLUNT);
        return land(party, m, p, p.x, p.y, p.cell, null, true);
    }

    /** DM's attack type for things that hit (C3, blunt). */
    private static final int ATTACK_BLUNT = 3;

    /** Weapons some creatures keep when they're hit but not killed: dagger, arrow, slayer, poison dart, throwing star. */
    private static final int[] KEPT_WEAPONS = {8, 27, 28, 31, 32};

    /**
     * F217 for a creature: the impact against its defense. A group that
     * survives reacts to being hit, and some creatures keep a sharp weapon
     * that hit them.
     */
    private static boolean hitCreature(Party party, DungeonMap m, Projectile p, Group g, int creature) {
        CreatureAI creatures = party.dungeon().creatures();
        CreatureType info = g.type();
        int attack = (impactAttack(p, party.random()) << 6) / Math.max(1, info.defense());
        Group keeper = null;
        if (attack != 0) {
            int outcome = creatures.hitCreature(party, m, g, creature, attack);
            if (outcome != CreatureAI.KILLED_ALL) {
                if (m == party.map()) {
                    creatures.react(party, m, g.x(), g.y(), CreatureAI.HIT_BY_PROJECTILE);
                }
                if (outcome == CreatureAI.KILLED_NONE && info.keepsThrownSharpWeapons()
                        && p.item().category() == Item.Category.WEAPON) {
                    for (int type : KEPT_WEAPONS) {
                        if (p.item().type() == type) {
                            keeper = g;
                        }
                    }
                }
            }
        }
        return land(party, m, p, p.x, p.y, p.cell, keeper, true);
    }

    /**
     * DM's F216: how hard the projectile hits: a weapon's kinetic energy (a
     * little for anything else) and half its weight, with the projectile's
     * energy, worn down as its attack fades.
     */
    static int impactAttack(Projectile p, Random random) {
        Item item = p.item();
        int attack = item.category() == Item.Category.WEAPON
                ? ItemCatalog.weaponKineticEnergy(item) : random.nextInt(4);
        attack += item.weight() >> 1;
        attack = ((attack + p.kineticEnergy) >> 4) + 1;
        attack += random.nextInt((attack >> 1) + 1) + random.nextInt(4);
        return Math.max(attack >> 1, attack - (32 - (p.attack >> 3)));
    }

    /**
     * F215 (and F217's end): the flight is over. The item drops on cell
     * {@code cell} of (x, y), through pits and teleporters and onto plates,
     * or goes into {@code keeper}'s possessions. An impact makes a thud.
     */
    private static boolean land(Party party, DungeonMap m, Projectile p, int x, int y, int cell, Group keeper,
                                boolean impact) {
        m.projectileList().remove(p);
        CreatureAI creatures = party.dungeon().creatures();
        if (impact) {
            boolean weapon = p.item().category() == Item.Category.WEAPON;
            creatures.soundAt(party, weapon ? CreatureAI.SOUND_METALLIC_THUD : CreatureAI.SOUND_WOODEN_THUD, m, x, y);
        }
        creatures.changed(party);
        if (keeper != null) {
            keeper.possessions().add(p.item());
            return false;
        }
        return m.dropItem(x, y, cell, p.item()).click();
    }
}
