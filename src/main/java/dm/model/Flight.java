package dm.model;

import java.util.ArrayList;
import java.util.Random;

/**
 * Things in flight and explosions, ported from ReDMCSB's PROJEXPL.C: F212
 * creates a projectile, F219 moves it, F217 decides what it hits and F216
 * how hard; F213 creates an explosion and F220 runs it.
 *
 * <p>A projectile moves half a square per game tick on the party's map (DM
 * 1.x moves those elsewhere every 3 ticks). Before each move but the first
 * it may hit a champion (on the party's square) or a creature standing on
 * its cell; after that, if its kinetic energy is spent it drops where it is,
 * and otherwise it loses its step energy from both its energy and its
 * attack. Leaving a square it may hit a wall (it drops in front of it) and
 * crossing into the middle of a square a closed door. It flies over pits,
 * and teleporters that take things send it on, turned. An item ends up on
 * the floor (or in the creature that caught it), with a metallic thud for a
 * weapon and a wooden one for anything else.
 *
 * <p>A spell in flight is an explosion type. Where it hits, most spells burst
 * into an explosion on that cell: a fireball or a lightning bolt burns
 * everything on the square at once (the party too, if that is where it
 * burst) and may break a door next tick; harm non-material hurts only
 * non-material creatures; a poison cloud lingers on the whole square,
 * hurting what is in it while it thins; open door opens a door with a
 * button. A poison bolt poisons what it hits and doesn't burst. A spell whose
 * energy runs out just fizzles.
 *
 * <p>A thrown VEN potion or FUL bomb bursts where it hits (a poison cloud or a
 * fireball, as strong as its power); the other bombs just land, as in DM. The
 * party walking into projectiles is hit by them (F266). Not yet: creatures
 * walking into projectiles. As in DM, a poison dart carries no poison.
 */
final class Flight {

    private Flight() {
    }

    /** DM's attack types (F321). */
    static final int ATTACK_NORMAL = 0;
    static final int ATTACK_FIRE = 1;
    static final int ATTACK_BLUNT = 3;
    static final int ATTACK_MAGIC = 5;
    static final int ATTACK_LIGHTNING = 7;

    /** DM's sounds for explosions. */
    static final int SOUND_STRONG_EXPLOSION = 5;
    static final int SOUND_SPELL = 13;
    static final int SOUND_WEAK_EXPLOSION = 20;

    /** Every body part (F324's wounds for a fire blast). */
    private static final int ALL_WOUNDS = 0x3F;

    /** DM's F212: {@code item} takes flight from cell {@code cell} of (x, y), heading {@code direction}. */
    static void launch(Party party, Item item, DungeonMap m, int x, int y, int cell, Direction direction,
                       int kineticEnergy, int attack, int stepEnergy) {
        m.projectileList().add(new Projectile(item, x, y, cell, direction, kineticEnergy, attack, stepEnergy,
                party.time() + 1));
        party.dungeon().creatures().changed(party);
    }

    /** DM's F212 for a spell: explosion type {@code spell} takes flight. */
    static void launchSpell(Party party, int spell, DungeonMap m, int x, int y, int cell, Direction direction,
                            int kineticEnergy, int attack, int stepEnergy) {
        m.projectileList().add(new Projectile(spell, x, y, cell, direction, kineticEnergy, attack, stepEnergy,
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
            // F217: things pass through non-material creatures, except harm non-material (and a black flame eats fireballs)
            if (g != null && CreatureAI.creatureOrdinalInCell(g, p.cell) != 0
                    && (!g.type().nonMaterial() || p.spell() == Explosion.HARM_NON_MATERIAL
                    || p.spell() == Explosion.FIREBALL && g.type() == CreatureType.BLACK_FLAME)) {
                return hitCreature(party, m, p, g, CreatureAI.creatureOrdinalInCell(g, p.cell) - 1);
            }
            if (p.kineticEnergy <= p.stepEnergy) {
                if (p.isSpell()) { // a spell that runs out of energy is gone
                    m.projectileList().remove(p);
                    creatures.changed(party);
                    return false;
                }
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
            return impact(party, m, p, null); // against the wall
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
                return impact(party, m, p, null);
            }
            p.cell = cell;
        }
        p.nextMove = party.time() + (to == party.map() ? 1 : 3);
        if (to == party.map() || m == party.map()) {
            creatures.changed(party);
        }
        return false;
    }

    /**
     * DM's F266 for the party, as it moves off (sx, sy) of {@code m} toward
     * (dx, dy): the projectiles on its square hit the champions in their
     * cells. Stepping to the next square, the back row passes through the
     * front cells, and the front row through the back cells of the square
     * ahead, where projectiles hit them too.
     */
    static void partyMoves(Party party, DungeonMap m, int sx, int sy, int dx, int dy) {
        if (party.members().isEmpty()) {
            return;
        }
        int[] cells = new int[4]; // the ordinal of the party cell crossing each cell, 0 for none
        for (int c = 0; c < 4; c++) {
            if (party.memberInCell(c) >= 0) {
                cells[c] = c + 1;
            }
        }
        int[] ahead = null;
        if (Math.abs(sx - dx) + Math.abs(sy - dy) == 1) {
            int primary = 0;
            for (Direction d : Direction.values()) {
                if (sx + d.dx == dx && sy + d.dy == dy) {
                    primary = d.ordinal();
                }
            }
            int secondary = (primary + 1) & 3;
            ahead = new int[4];
            ahead[(primary + 3) & 3] = cells[primary];
            ahead[(secondary + 1) & 3] = cells[secondary];
            if (cells[primary] == 0) {
                cells[primary] = cells[(primary + 3) & 3];
            }
            if (cells[secondary] == 0) {
                cells[secondary] = cells[(secondary + 1) & 3];
            }
        }
        hitCrossing(party, m, sx, sy, cells);
        if (ahead != null) {
            hitCrossing(party, m, dx, dy, ahead);
        }
    }

    private static void hitCrossing(Party party, DungeonMap m, int x, int y, int[] cells) {
        boolean hit;
        do {
            hit = false;
            for (Projectile p : new ArrayList<>(m.projectileList())) {
                int member = p.x == x && p.y == y && !p.ignoreImpacts && cells[p.cell] != 0
                        ? party.memberInCell(cells[p.cell] - 1) : -1;
                if (member >= 0) {
                    hitChampion(party, m, p, member);
                    hit = true;
                    break;
                }
            }
        } while (hit);
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
     * one barely closed) lets it by, and a portcullis lets small things and
     * the weaker spells through; otherwise it hits the door, which may break.
     * The open door spell instead works a door that has a button.
     */
    private static boolean hitsDoor(Party party, DungeonMap m, Projectile p) {
        int state = m.doorState(p.x, p.y);
        Random random = party.random();
        if (p.spell() == Explosion.OPEN_DOOR && state != DungeonMap.DOOR_BROKEN) {
            Decorations decorations = m.decorations();
            if (decorations != null && decorations.doorButton(p.x, p.y)) {
                m.toggleDoor(p.x, p.y);
            }
            return true;
        }
        if (state == DungeonMap.DOOR_BROKEN || state <= 1) {
            return false;
        }
        if (m.doorLetsProjectilesThrough(p.x, p.y)) {
            if (p.isSpell() ? p.spell() >= Explosion.HARM_NON_MATERIAL
                    : p.attack > random.nextInt(128) && ItemCatalog.passesThroughDoors(p.item())) {
                return false;
            }
        }
        int attack = impactAttack(p, random).attack() + 1;
        if (m.breakDoor(p.x, p.y, attack + random.nextInt(attack), false, 0)) {
            party.dungeon().creatures().changed(party);
        }
        return true;
    }

    /** F217 for a champion: hurt in the head or torso, and perhaps poisoned. */
    private static boolean hitChampion(Party party, DungeonMap m, Projectile p, int member) {
        Random random = party.random();
        Impact hit = impactAttack(p, random);
        int attack = p.isSpell() ? party.difficulty().creatureDamage(hit.attack(), random) : hit.attack();
        CreatureAI creatures = party.dungeon().creatures();
        int damage = creatures.hurtChampion(party, member, attack, Champion.WOUND_HEAD | Champion.WOUND_TORSO,
                hit.attackType());
        if (damage > 0 && hit.poison() != 0 && random.nextInt(2) != 0) {
            creatures.poisonChampion(party, member, party.difficulty().creatureDamage(hit.poison(), random));
        }
        return impact(party, m, p, null);
    }

    /** The potions that burst where they hit (DM's C03 VEN potion and C19 FUL bomb). */
    private static final int VEN_POTION = 3;
    private static final int FUL_BOMB = 19;

    /** Weapons some creatures keep when they're hit but not killed: dagger, arrow, slayer, poison dart, throwing star. */
    private static final int[] KEPT_WEAPONS = {8, 27, 28, 31, 32};

    /**
     * F217 for a creature: the impact against its defense, plus any poison
     * against its resistance. A group that survives reacts to being hit, and
     * some creatures keep a sharp weapon that hit them. A fireball feeds a
     * black flame instead.
     */
    private static boolean hitCreature(Party party, DungeonMap m, Projectile p, Group g, int creature) {
        CreatureAI creatures = party.dungeon().creatures();
        CreatureType info = g.type();
        Random random = party.random();
        Impact hit = impactAttack(p, random);
        if (p.spell() == Explosion.FIREBALL && info == CreatureType.BLACK_FLAME) {
            g.setHealth(creature, Math.min(1000, g.health(creature) + hit.attack()));
            m.projectileList().remove(p);
            creatures.changed(party);
            return false;
        }
        int attack = (hit.attack() << 6) / Math.max(1, info.defense());
        Group keeper = null;
        if (attack != 0) {
            attack += CreatureAI.resistedPoisonAttack(info, hit.poison(), random);
            attack = party.difficulty().partyDamage(attack, random);
            int outcome = creatures.hitCreature(party, m, g, creature, attack);
            if (outcome != CreatureAI.KILLED_ALL) {
                if (m == party.map()) {
                    creatures.react(party, m, g.x(), g.y(), CreatureAI.HIT_BY_PROJECTILE);
                }
                if (!p.isSpell() && outcome == CreatureAI.KILLED_NONE && info.keepsThrownSharpWeapons()
                        && p.item().category() == Item.Category.WEAPON) {
                    for (int type : KEPT_WEAPONS) {
                        if (p.item().type() == type) {
                            keeper = g;
                        }
                    }
                }
            }
        }
        return impact(party, m, p, keeper);
    }

    /** F216's result: the attack, any poison it carries, and DM's attack type. */
    record Impact(int attack, int poison, int attackType) {
    }

    /**
     * DM's F216: how hard the projectile hits. An item: a weapon's kinetic
     * energy (a little for anything else) and half its weight. A fireball:
     * 10-40 of fire, a lightning bolt five times that; harm non-material,
     * open door and poison cloud do nothing on impact, and a poison bolt
     * carries its energy as poison. Then the projectile's energy counts,
     * worn down as its attack fades.
     */
    static Impact impactAttack(Projectile p, Random random) {
        int poison = 0;
        int type = ATTACK_BLUNT;
        int attack;
        if (!p.isSpell()) {
            Item item = p.item();
            attack = item.category() == Item.Category.WEAPON
                    ? ItemCatalog.weaponKineticEnergy(item) : random.nextInt(4);
            attack += item.weight() >> 1;
        } else if (p.spell() == Explosion.SLIME) {
            attack = random.nextInt(16);
            poison = attack + 10;
            attack += random.nextInt(32);
        } else if (p.spell() >= Explosion.HARM_NON_MATERIAL) {
            if (p.spell() == Explosion.POISON_BOLT) {
                return new Impact(1, p.kineticEnergy, ATTACK_MAGIC);
            }
            return new Impact(0, 0, ATTACK_MAGIC);
        } else {
            type = ATTACK_FIRE;
            attack = random.nextInt(16) + random.nextInt(16) + 10;
            if (p.spell() == Explosion.LIGHTNING_BOLT) {
                type = ATTACK_LIGHTNING;
                attack *= 5;
            }
        }
        attack = ((attack + p.kineticEnergy) >> 4) + 1;
        attack += random.nextInt((attack >> 1) + 1) + random.nextInt(4);
        return new Impact(Math.max(attack >> 1, attack - (32 - (p.attack >> 3))), poison, type);
    }

    /**
     * F217's end: the flight is over where it hit. Most spells burst there
     * (a poison cloud over the whole square; a lightning bolt only if it
     * still has energy); a poison bolt makes the spell sound; an item drops,
     * with a thud.
     */
    private static boolean impact(Party party, DungeonMap m, Projectile p, Group keeper) {
        CreatureAI creatures = party.dungeon().creatures();
        if (!p.isSpell()) {
            Item item = p.item();
            int burst = item.category() == Item.Category.POTION ? switch (item.type()) {
                case VEN_POTION -> Explosion.POISON_CLOUD;
                case FUL_BOMB -> Explosion.FIREBALL;
                default -> -1;
            } : -1;
            if (burst < 0) {
                return land(party, m, p, p.x, p.y, p.cell, keeper, true);
            }
            // F217: a VEN potion or FUL bomb bursts with its power, and is gone
            m.projectileList().remove(p);
            creatures.changed(party);
            explode(party, m, burst, item.charges(), p.x, p.y,
                    burst == Explosion.POISON_CLOUD ? Group.CENTRED : p.cell);
            return false;
        }
        m.projectileList().remove(p);
        creatures.changed(party);
        int spell = p.spell();
        if (spell == Explosion.SLIME || spell == Explosion.POISON_BOLT) {
            creatures.soundAt(party, spell == Explosion.POISON_BOLT ? SOUND_SPELL : CreatureAI.SOUND_WOODEN_THUD,
                    m, p.x, p.y);
            return false;
        }
        int attack = p.kineticEnergy;
        if (spell == Explosion.LIGHTNING_BOLT && (attack >>= 1) == 0) {
            return false;
        }
        explode(party, m, spell, attack, p.x, p.y, spell == Explosion.POISON_CLOUD ? Group.CENTRED : p.cell);
        return false;
    }

    /**
     * DM's F213: an explosion of type {@code type} bursts on (x, y)'s
     * {@code cell}, with its sound. A fireball or lightning bolt burns at
     * once: the whole party if it is on that square (F324), or every
     * creature there that isn't immune to fire (non-material ones take a
     * quarter). The explosion's event runs next tick.
     */
    static void explode(Party party, DungeonMap m, int type, int attack, int x, int y, int cell) {
        CreatureAI creatures = party.dungeon().creatures();
        Random random = party.random();
        m.explosionList().add(new Explosion(type, x, y, cell, attack, party.time() + 1));
        if (type < Explosion.HARM_NON_MATERIAL) {
            creatures.soundAt(party, attack > 80 ? SOUND_STRONG_EXPLOSION : SOUND_WEAK_EXPLOSION, m, x, y);
        } else if (type != Explosion.SMOKE) {
            creatures.soundAt(party, SOUND_SPELL, m, x, y);
        }
        creatures.changed(party);
        if (type != Explosion.FIREBALL && type != Explosion.LIGHTNING_BOLT) {
            return;
        }
        attack = (attack >> 1) + 1;
        attack += random.nextInt(attack) + 1;
        if (type == Explosion.LIGHTNING_BOLT && (attack >>= 1) == 0) {
            return;
        }
        if (m == party.map() && x == party.x() && y == party.y()) {
            creatures.hurtParty(party, party.difficulty().creatureDamage(attack, random), ALL_WOUNDS, ATTACK_FIRE);
            return;
        }
        Group g = m.groupAt(x, y);
        if (g == null) {
            return;
        }
        int resistance = g.type().fireResistance();
        if (resistance == 15) {
            return;
        }
        if (g.type().nonMaterial()) {
            attack >>= 2;
        }
        attack -= random.nextInt((resistance << 1) + 1);
        if (attack > 0) {
            creatures.blastGroup(party, m, g, party.difficulty().partyDamage(attack, random));
        }
    }

    /**
     * DM's F224 (without Lord Chaos's capture, which comes with FUSE): a
     * fluxcage on (x, y), unless it is a wall or stairs, for 100 ticks. Only
     * Lord Chaos is held back by it (F202).
     */
    static void fluxcage(Party party, DungeonMap m, int x, int y) {
        SquareType type = m.get(x, y).type();
        if (type == SquareType.WALL || type == SquareType.STAIRS) {
            return;
        }
        m.explosionList().add(new Explosion(Explosion.FLUXCAGE, x, y, 0, 0, party.time() + 100));
        party.dungeon().creatures().changed(party);
    }

    /**
     * DM's F220, every tick for each explosion whose event is due, on every
     * map: a fireball or lightning bolt may break the door it burst on; harm
     * non-material hurts non-material creatures (a materializer only while it
     * attacks); smoke thins; a poison cloud hurts the party or the creatures
     * on its square (a group that survives a strong dose moves away) and
     * thins by 3 a tick until it is gone. The rest last one tick. Returns
     * whether there was any explosion on the party's map.
     */
    static boolean tickExplosions(Party party) {
        boolean changed = false;
        Random random = party.random();
        CreatureAI creatures = party.dungeon().creatures();
        for (DungeonMap m : party.dungeon().maps()) {
            for (Explosion e : new ArrayList<>(m.explosionList())) {
                if (e.due > party.time() || !m.explosionList().contains(e)) {
                    continue;
                }
                changed |= m == party.map();
                boolean onParty = m == party.map() && e.x() == party.x() && e.y() == party.y();
                Group g = m.groupAt(e.x(), e.y());
                int attack;
                if (e.type() == Explosion.POISON_CLOUD) {
                    attack = Math.max(1, Math.min(e.attack() >> 5, 4) + random.nextInt(2));
                } else {
                    attack = (e.attack() >> 1) + 1;
                    attack += random.nextInt(attack) + 1;
                }
                boolean lasts = false;
                switch (e.type()) {
                    case Explosion.LIGHTNING_BOLT, Explosion.FIREBALL -> {
                        if (e.type() == Explosion.LIGHTNING_BOLT) {
                            attack >>= 1;
                        }
                        if (attack != 0 && m.get(e.x(), e.y()).type() == SquareType.DOOR) {
                            m.breakDoor(e.x(), e.y(), attack, true, 0);
                        }
                    }
                    case Explosion.HARM_NON_MATERIAL -> {
                        if (g != null && g.type().nonMaterial()) {
                            harmNonMaterial(party, m, g, attack, random);
                        }
                    }
                    case Explosion.SMOKE -> {
                        if (e.attack() > 55) {
                            e.setAttack(e.attack() - 40);
                            lasts = true;
                        }
                    }
                    case Explosion.POISON_CLOUD -> {
                        if (onParty) {
                            creatures.hurtParty(party, party.difficulty().creatureDamage(attack, random), 0,
                                    ATTACK_NORMAL);
                        } else if (g != null) {
                            int poison = CreatureAI.resistedPoisonAttack(g.type(), attack, random);
                            if (poison != 0) {
                                poison = party.difficulty().partyDamage(poison, random);
                                if (creatures.blastGroup(party, m, g, poison) != CreatureAI.KILLED_ALL && poison > 2) {
                                    creatures.react(party, m, e.x(), e.y(), CreatureAI.DANGER_ON_SQUARE);
                                }
                            }
                        }
                        if (e.attack() >= 6) {
                            e.setAttack(e.attack() - 3);
                            lasts = true;
                        }
                    }
                    default -> { }
                }
                if (lasts) {
                    e.due = party.time() + 1;
                } else {
                    m.explosionList().remove(e);
                }
            }
        }
        return changed;
    }

    /** F220's harm non-material: a materializer only hurts while it attacks; other groups all take it. */
    private static void harmNonMaterial(Party party, DungeonMap m, Group g, int attack, Random random) {
        CreatureAI creatures = party.dungeon().creatures();
        attack = party.difficulty().partyDamage(attack, random);
        if (g.type() != CreatureType.MATERIALIZER || m != party.map()) {
            creatures.blastGroup(party, m, g, attack);
            return;
        }
        int extra = attack >> 3;
        attack -= extra;
        extra = (extra << 1) + 1;
        for (int i = g.count() - 1; i >= 0 && m.groupAt(g.x(), g.y()) == g; i--) {
            if (i < g.count() && g.attacking(i)) {
                creatures.hitCreature(party, m, g, i, attack + random.nextInt(extra) + random.nextInt(4));
            }
        }
    }

    /**
     * F215 (and F217's end): an item's flight is over. It drops on cell
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
