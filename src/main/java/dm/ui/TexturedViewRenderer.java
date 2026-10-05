package dm.ui;

import dm.data.GraphicsFile;
import dm.data.IndexedImage;
import dm.data.Zones;
import dm.model.ChampionMirror;
import dm.model.CreatureType;
import dm.model.Direction;
import dm.model.DungeonMap;
import dm.model.Explosion;
import dm.model.Group;
import dm.model.Item;
import dm.model.ItemCatalog;
import dm.model.Party;
import dm.model.Projectile;
import dm.model.Square;
import dm.model.SquareType;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Draws the first-person view with the original GRAPHICS.DAT art, the way DM
 * does: no 3D maths, just pre-drawn pieces pasted at fixed viewport positions,
 * back to front. A square is addressed by depth (0 = the party's own square)
 * and lateral offset (-2..2, negative = left).
 *
 * Each side piece contains the visible part of its square's front face plus
 * its side face, so pieces tile seamlessly. Positions were measured from the
 * art: each front face ends exactly where the next nearer centre piece starts.
 *
 * On alternate squares (by party position and facing parity) DM mirrors the
 * floor, ceiling and walls, which makes walking look like movement. Mirrored
 * side walls use the opposite side's piece flipped, so the silhouette stays right.
 */
public final class TexturedViewRenderer implements ViewRenderer {

    static final int CEILING = 79;
    static final int FLOOR = 78;
    static final int FLOOR_Y = 39;

    /** Wall pieces by [depth][lateral + 2]; -1 where nothing is visible. */
    private static final int[][] WALL = {
            {-1, 94, -1, 93, -1},
            {-1, 96, 97, 95, -1},
            {99, 101, 102, 100, 98},
            {104, 106, 107, 105, 103},
    };
    /** Where the wall pieces go without the zone table (the same as DM's zones 702-717 give). */
    private static final int[][] WALL_X = {
            {0, 0, 0, 191, 0},
            {0, 0, 32, 164, 0},
            {0, 0, 59, 146, 216},
            {0, 7, 77, 134, 180},
    };
    private static final int[][] WALL_Y = {
            {0, 0, 0, 0, 0},
            {9, 9, 9, 9, 9},
            {24, 19, 19, 19, 24},
            {25, 25, 25, 25, 25},
    };
    /** Front face of the centre square at each depth: x, y, width, height (DM's wall zones 712, 709, 704). */
    static final Rectangle[] FRONT = {
            null,
            new Rectangle(32, 9, 160, 111),
            new Rectangle(59, 19, 106, 74),
            new Rectangle(77, 25, 70, 49),
    };

    private static final int MAX_DEPTH = 3;
    private static final int MIRROR_W = 48;
    private static final int MIRROR_H = 43;
    private static final int GLASS_X = 8;
    private static final int GLASS_Y = 6;
    private static final int PORTRAIT_W = 32;
    private static final int PORTRAIT_H = 29;

    private final Art art;
    private final CreatureArt creatureArt;
    private Rectangle portraitHit;
    private Rectangle wallHit;
    private Rectangle doorButtonHit;

    public TexturedViewRenderer(Art art) {
        this.art = art;
        this.creatureArt = new CreatureArt(art);
    }

    @Override
    public Rectangle portraitHit() {
        return portraitHit;
    }

    @Override
    public Rectangle wallHit() {
        return wallHit;
    }

    @Override
    public Rectangle doorButtonHit() {
        return doorButtonHit;
    }

    @Override
    public boolean animated() {
        return fieldDrawn;
    }

    /** The party being drawn for, and whether its view is mirrored, during {@link #draw}. */
    private Party viewer;
    private boolean viewFlipped;

    @Override
    public void draw(Graphics2D screen, Party party) {
        portraitHit = null;
        wallHit = null;
        doorButtonHit = null;
        fieldDrawn = false;
        viewer = party;
        // With a thieves' eye the view is drawn off-screen, so the wall ahead can show what is behind it.
        BufferedImage buffer = party.thievesEye()
                ? new BufferedImage(VIEWPORT.width, VIEWPORT.height, BufferedImage.TYPE_INT_RGB) : null;
        Graphics2D g = buffer != null ? buffer.createGraphics()
                : (Graphics2D) screen.create(VIEWPORT.x, VIEWPORT.y, VIEWPORT.width, VIEWPORT.height);
        try {
            DungeonMap map = party.map();
            Direction fwd = party.facing();
            Direction right = fwd.turnRight();
            boolean flipped = ((party.x() + party.y() + fwd.ordinal()) & 1) != 0;
            viewFlipped = flipped;

            paste(g, CEILING, 0, 0, flipped);
            paste(g, FLOOR, 0, FLOOR_Y, flipped);

            for (int d = MAX_DEPTH; d >= 0; d--) {
                for (int l : new int[] {-2, 2, -1, 1, 0}) {
                    int mx = party.x() + fwd.dx * d + right.dx * l;
                    int my = party.y() + fwd.dy * d + right.dy * l;
                    Square sq = map.get(mx, my);
                    if (sq.looksSolid()) {
                        int[] behind = buffer != null && d == 1 && l == 0 ? holeArea(buffer) : null;
                        if (WALL[d][l + 2] >= 0) {
                            drawWall(g, d, l, flipped);
                            drawWallDecorations(g, map, d, l, mx, my, fwd);
                        }
                        if (l == 0 && d > 0) {
                            ChampionMirror mirror = map.mirrorAt(mx, my, fwd.opposite());
                            if (mirror != null) {
                                drawMirror(g, d, mirror);
                            }
                        }
                        if (behind != null) {
                            drawHoleInWall(buffer, behind);
                        }
                    } else if (Math.abs(l) <= 1) {
                        drawFeature(g, map, sq, d, l, fwd, mx, my);
                    } else {
                        drawItems(g, map, d, l, fwd, mx, my, true);
                        drawItems(g, map, d, l, fwd, mx, my, false);
                        if (showsField(map, sq, mx, my)) {
                            drawTeleporter(g, d, l);
                        }
                    }
                }
            }
        } finally {
            g.dispose();
        }
        if (buffer != null) {
            screen.drawImage(buffer, VIEWPORT.x, VIEWPORT.y, null);
        }
    }

    // ---- the thieves' eye (DM's F0124 wall case, BUG0_74 fixed) ---------------
    //
    // Before the wall straight ahead at D1 is drawn, the view behind it is
    // kept (box 64-159 x 19-113); afterwards the hole picture (graphic 41) is
    // laid over the wall there: its gold shows the wall, its flesh colour the
    // view behind, and its other colours (the stone rim) themselves.

    static final int HOLE_IN_WALL = 41;
    private static final Rectangle HOLE_AREA = new Rectangle(64, 19, 96, 95);

    private static int[] holeArea(BufferedImage buffer) {
        return buffer.getRGB(HOLE_AREA.x, HOLE_AREA.y, HOLE_AREA.width, HOLE_AREA.height, null, 0, HOLE_AREA.width);
    }

    private void drawHoleInWall(BufferedImage buffer, int[] behind) {
        BufferedImage hole = art.image(HOLE_IN_WALL);
        if (hole == null) {
            return;
        }
        int gold = Art.PALETTE[9].getRGB();
        int flesh = Art.PALETTE[10].getRGB();
        int w = Math.min(hole.getWidth(), HOLE_AREA.width);
        int h = Math.min(hole.getHeight(), HOLE_AREA.height);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = hole.getRGB(x, y);
                if (c == gold) {
                    continue;
                }
                buffer.setRGB(HOLE_AREA.x + x, HOLE_AREA.y + y, c == flesh ? behind[y * HOLE_AREA.width + x] : c);
            }
        }
    }

    // ---- wall decorations ---------------------------------------------------
    //
    // Decoration k has a side view (259 + 2k) and a front view (260 + 2k),
    // both drawn for D1 and scaled down for D2/D3. Front views are centred on
    // DM's own points from the zone table (FIRST_WALL_ORNAMENT_ZONE); without
    // it, and for side views, positions are fitted: centred on the face
    // slightly above its middle (like the mirrors). Full-face pictures fill the face.

    static final int FIRST_WALL_ORNAMENT = 259;
    /** Centre of each depth's visible left side face (x, y), for side decorations. */
    private static final int[][] SIDE_FACE_CENTRE = {null, {46, 59}, {68, 52}, {82, 47}};
    private static final double[] SIDE_SCALE = {0, 1.0, 0.66, 0.44};

    private void drawWallDecorations(Graphics2D g, DungeonMap map, int d, int l, int mx, int my, Direction fwd) {
        if (d == 0) {
            return;
        }
        // Front face, for the centre and the visible slivers of the neighbouring squares.
        if (Math.abs(l) <= 1) {
            Direction front = fwd.opposite();
            int ornament = map.wallOrnament(mx, my, front);
            String text = map.decorations().inscription(mx, my, front);
            if (text != null && d == 1 && l == 0) {
                Inscription.draw(g, art, text, FRONT[1]);
            } else if (l == 0 && map.mirrorAt(mx, my, front) != null) {
                // drawMirror owns the front view: frame, portrait and click target.
            } else if (ornament >= 0) {
                Rectangle face = new Rectangle(FRONT[d]);
                face.x += l * face.width;
                Rectangle drawn = drawFrontDecoration(g, ornament, face, d, l);
                if (DungeonMap.isAlcove(ornament)) {
                    drawAlcoveItems(g, map.itemsAt(mx, my, front.ordinal()), d, l);
                    drawRebirth(g, map, mx, my, front, d, l);
                }
                if (d == 1 && l == 0 && drawn != null) {
                    wallHit = new Rectangle(drawn.x + VIEWPORT.x, drawn.y + VIEWPORT.y, drawn.width, drawn.height);
                }
            }
        }
        // Side face turned toward the middle of the view.
        if (l != 0 && Math.abs(l) <= 1) {
            Direction side = l < 0 ? fwd.turnRight() : fwd.turnLeft();
            int ornament = map.wallOrnament(mx, my, side);
            if (ornament >= 0) {
                drawSideDecoration(g, ornament, d, l > 0);
            }
        }
    }

    /**
     * Objects in an alcove, at DM's alcove points from the zone table (2548:
     * D3 centre, left, right, D2 centre, left, right, D1 centre), standing on
     * the alcove's shelf. Scaled for the wall face's distance, between the
     * object scales of the cells on either side of it.
     */
    private static final int FIRST_ALCOVE_ZONE = 2548;
    private static final int[] ALCOVE_SCALE = {0, 30, 19, 13};

    private void drawAlcoveItems(Graphics2D g, List<Item> items, int d, int l) {
        if (items.isEmpty() || (d == 1 && l != 0)) {
            return; // at D1 only the centre wall's alcove is in view
        }
        int index = (MAX_DEPTH - d) * 3 + (l == 0 ? 0 : l < 0 ? 1 : 2);
        Point at = art.zone(FIRST_ALCOVE_ZONE + index);
        if (at == null) {
            return;
        }
        for (Item item : items) {
            drawObject(g, item, at, ALCOVE_SCALE[d], true);
        }
    }

    /**
     * First of DM's front wall decoration centres in the zone table: D3
     * centre, left, right, D2 centre, left, right, D1 centre. A second set
     * follows at 3007 (a few pixels lower); which decorations use it isn't
     * known, so all use the first. With these, objects at the alcove points
     * (2548) sit on the alcove's shelf, which confirms the reading.
     */
    static final int FIRST_WALL_ORNAMENT_ZONE = 3000;

    /** Draws a front-view decoration on {@code face} and returns where it went (viewport coordinates), or null. */
    private Rectangle drawFrontDecoration(Graphics2D g, int ornament, Rectangle face, int d, int l) {
        int graphic = FIRST_WALL_ORNAMENT + 2 * ornament + 1;
        BufferedImage img = art.sprite(graphic);
        if (img == null) {
            return null;
        }
        double scale = face.width / (double) FRONT[1].width;
        int w = (int) Math.round(img.getWidth() * scale);
        int h = (int) Math.round(img.getHeight() * scale);
        img = distant(graphic, w, h, wallOrnamentChanges(d), false);
        int x;
        int y;
        Point centre = d == 1 && l != 0 ? null
                : art.zone(FIRST_WALL_ORNAMENT_ZONE + (MAX_DEPTH - d) * 3 + (l == 0 ? 0 : l < 0 ? 1 : 2));
        if (img.getWidth() >= FRONT[1].width * 0.9) {
            x = face.x;            // full-face pictures
            y = face.y;
        } else if (centre != null) {
            x = centre.x - w / 2;
            y = centre.y - h / 2;
        } else {
            x = face.x + (face.width - w) / 2;
            y = (int) Math.round(face.y + face.height / 2.0 - h / 2.0 - 6 * scale);
        }
        if (standsOnFloor(ornament, art.sprite(graphic))) {
            y = face.y + face.height - h;
        } else if (level(ornament) > 0) {
            y = (int) Math.round(face.y + level(ornament) * face.height - h / 2.0);
        }
        g.drawImage(img, x, y, w, h, null);
        return new Rectangle(x, y, w, h);
    }

    /**
     * Decorations DM hangs higher than the zone centres, at eye level (#15),
     * all confirmed by the user against the original: the hook and ring (4, 6),
     * the keyholes, locks and slots (5, 17-24, 26-32), the gems (15, 16,
     * 51-53), the skull (25) and both positions of the lever (44, 45). Their
     * centre is at row 48 of the D1 front face (40 of its 111 rows down), and
     * at the same fraction of every other face.
     */
    static final Set<Integer> EYE_LEVEL_ORNAMENTS = Set.of(
            4, 5, 6, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 44, 45, 51, 52, 53);
    static final double EYE_LEVEL = 40 / 111.0;

    /** The champion mirror's frame, as a wall decoration (side views and other front faces). */
    static final int MIRROR_ORNAMENT = 43;
    /**
     * Where the mirror's frame is centred on every wall face, as a fraction
     * of the face's height (#30): the same as the straight-on D1 mirror,
     * which the user confirmed against the original (frame top at the
     * portrait zone's y 35 less the glass offset, so its centre is 41.5 of
     * the D1 face's 111 rows down). The straight-on D2/D3 mirrors already
     * sit there.
     */
    static final double MIRROR_LEVEL = (35 - GLASS_Y + MIRROR_H / 2.0 - FRONT[1].y) / FRONT[1].height;

    /** The fraction of a face's height a decoration is centred on, or 0 for the default placement. */
    static double level(int ornament) {
        if (ornament == MIRROR_ORNAMENT) {
            return MIRROR_LEVEL;
        }
        return EYE_LEVEL_ORNAMENTS.contains(ornament) ? EYE_LEVEL : 0;
    }

    /**
     * Decorations that sit at the foot of the wall rather than around its
     * middle: the moss tuft (33) and the drain grate (34), checked against the original, and the
     * full-height pictures (the cracked and creature walls, 56-58), which
     * would otherwise hang below the floor line. DM keeps a coordinate set per
     * decoration in its program; until more are known, everything else uses
     * the zone centres.
     */
    private static final Set<Integer> FLOOR_LEVEL_ORNAMENTS = Set.of(33, 34);

    private static boolean standsOnFloor(int ornament, BufferedImage img) {
        return FLOOR_LEVEL_ORNAMENTS.contains(ornament) || img.getHeight() >= FRONT[1].height * 0.9;
    }

    private void drawSideDecoration(Graphics2D g, int ornament, int d, boolean rightSide) {
        int index = FIRST_WALL_ORNAMENT + 2 * ornament;
        BufferedImage img = rightSide ? art.spriteFlipped(index) : art.sprite(index);
        if (img == null) {
            return;
        }
        double scale = SIDE_SCALE[d];
        int w = Math.max(1, (int) Math.round(img.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(img.getHeight() * scale));
        img = distant(index, w, h, wallOrnamentChanges(d), rightSide);
        int cx = SIDE_FACE_CENTRE[d][0];
        if (rightSide) {
            cx = VIEWPORT.width - cx;
        }
        int y = SIDE_FACE_CENTRE[d][1] - h / 2;
        BufferedImage front = art.sprite(index + 1);
        if (front != null && standsOnFloor(ornament, front)) {
            y = SIDE_FACE_CENTRE[d][1] + SIDE_FACE_HEIGHT[d] / 2 - h;
        } else if (level(ornament) > 0) {
            int top = SIDE_FACE_CENTRE[d][1] - SIDE_FACE_HEIGHT[d] / 2;
            y = (int) Math.round(top + level(ornament) * SIDE_FACE_HEIGHT[d] - h / 2.0);
        }
        g.drawImage(img, cx - w / 2, y, w, h, null);
    }

    /** Height of each depth's side face at its centre: halfway between the front faces it joins. */
    private static final int[] SIDE_FACE_HEIGHT = {0, (111 + 74) / 2, (74 + 49) / 2, (49 + 33) / 2};

    // ---- open squares: doors, stairs, pits, teleporters -------------------
    //
    // Placed by DM's own layout zones (ScummVM's PC zone numbers), with DM's
    // PC graphics; [depth][column] tables, column 0 left, 1 centre, 2 right.
    // A right-hand square uses the left piece mirrored, as DM does.

    /** Horizontal distance between neighbouring squares on each depth's mid-square plane (floor decorations). */
    private static final int[] MID_SPACING = {0, 128, 84, 56};

    private static final int FIRST_DOOR = 246;
    /** Door panels: zone for a shut door; a part-open one (states 1-3) uses the zone plus its state. */
    private static final int[][] DOOR_ZONE = {null, {3780, 3790, 3800}, {3750, 3760, 3770}, {3720, 3730, 3740}};
    private static final int DOOR_FRAME_LEFT_D1 = 87;
    private static final int DOOR_FRAME_LEFT_D2 = 88;
    private static final int DOOR_FRAME_LEFT_D3C = 89;
    private static final int DOOR_FRAME_LEFT_D3SIDE = 90;
    private static final int DOOR_FRAME_TOP_D1 = 91;
    private static final int DOOR_FRAME_TOP_D2 = 92;
    /** The graphic DM cuts a broken door with (door ornament 15). */
    private static final int DOOR_DESTROYED_MASK = 439;
    private static final int DOOR_THIEVES_EYE_MASK = 440;

    /** Floor pits: graphics for the left and centre squares, zones for left, centre, right. */
    private static final int[][] PIT_GRAPHIC = {{56, 57}, {54, 55}, {52, 53}, {50, 51}};
    /** DM's fainter pictures for invisible pits (none at D3, where DM shows the plain one). */
    private static final int[][] INVISIBLE_PIT_GRAPHIC = {{62, 63}, {60, 61}, {58, 59}, {50, 51}};
    private static final int[][] PIT_ZONE = {{861, 862, 863}, {858, 859, 860}, {855, 856, 857}, {852, 853, 854}};
    /** Holes in the ceiling under an open pit on the level above (none at D3). */
    private static final int[][] CEILING_PIT_GRAPHIC = {{68, 69}, {66, 67}, {64, 65}, null};
    private static final int[][] CEILING_PIT_ZONE = {{870, 871, 872}, {867, 868, 869}, {864, 865, 866}, null};

    /** Stairs seen from the front: up 108-113, down 115-120 (left, centre per depth). */
    private static final int[][] STAIRS_UP_GRAPHIC = {null, {112, 113}, {110, 111}, {108, 109}};
    private static final int[][] STAIRS_DOWN_GRAPHIC = {null, {119, 120}, {117, 118}, {115, 116}};
    private static final int[][] STAIRS_UP_ZONE = {null, {808, 809, 810}, {805, 806, 807}, {802, 803, 804}};
    private static final int[][] STAIRS_DOWN_ZONE = {null, {821, 822, 823}, {818, 819, 820}, {815, 816, 817}};
    /** Stairs seen side-on beside the view (left zone, right zone mirrored). */
    private static final int STAIRS_SIDE_D2 = 122;
    private static final int STAIRS_UP_SIDE_D1 = 123;
    private static final int STAIRS_DOWN_SIDE_D1 = 124;
    private static final int STAIRS_SIDE_D0 = 125;

    /**
     * Floor ornaments (pressure plates, grates, moss...): 6 pieces each from
     * graphic 385, ordered D3L, D3C, D2L, D2C, D1L, D1C. They are centred on
     * the square's mid-square floor line; D1 side pieces are pre-cropped at
     * the screen edge.
     */
    static final int FIRST_FLOOR_ORNAMENT = 385;
    private static final int[] FLOOR_CENTRE_Y = {0, 102, 80, 66};

    /**
     * An open square, in DM's order (F0116-F0127): the pit or stairs, the
     * floor decoration, the hole in the ceiling, then the things on the
     * square. A door seen head-on splits the things: the far cells behind
     * its frame and panel, the near cells in front.
     */
    private void drawFeature(Graphics2D g, DungeonMap map, Square sq, int d, int l, Direction fwd, int mx, int my) {
        int col = l + 1;
        boolean doorFront = sq.type() == SquareType.DOOR && sq.facesAlong(fwd);
        boolean stairsSide = sq.type() == SquareType.STAIRS && !sq.facesAlong(fwd);
        if (sq.type() == SquareType.STAIRS) {
            if (stairsSide) {
                drawStairsSide(g, d, l, sq.stairsUp());
            } else if (d > 0) {
                int[][] graphic = sq.stairsUp() ? STAIRS_UP_GRAPHIC : STAIRS_DOWN_GRAPHIC;
                int[][] zone = sq.stairsUp() ? STAIRS_UP_ZONE : STAIRS_DOWN_ZONE;
                drawZoned(g, graphic[d][l == 0 ? 1 : 0], zone[d][col], l > 0);
            }
        } else if (sq.type() == SquareType.PIT && map.isPitOpen(mx, my)) {
            int[][] graphic = sq.pitInvisible() ? INVISIBLE_PIT_GRAPHIC : PIT_GRAPHIC;
            drawZoned(g, graphic[d][l == 0 ? 1 : 0], PIT_ZONE[d][col], l > 0);
        }
        int ornament = map.floorOrnament(mx, my);
        if (ornament >= 0 && d > 0) {
            drawFloorOrnament(g, d, l, ornament);
        }
        // DM's F172/F108: magic footprints over the floor decoration, not on stairs or open pits
        if (d > 0 && sq.type() != SquareType.STAIRS && !(sq.type() == SquareType.PIT && map.isPitOpen(mx, my))
                && viewer.footprintsAt(map, mx, my)) {
            drawFloorOrnament(g, d, l, FOOTPRINTS);
        }
        if (!doorFront && !stairsSide && CEILING_PIT_GRAPHIC[d] != null && map.ceilingPit(mx, my)) {
            drawZoned(g, CEILING_PIT_GRAPHIC[d][l == 0 ? 1 : 0], CEILING_PIT_ZONE[d][col], l > 0);
        }
        // Things in the far cells sit behind a door; the near ones in front of it.
        drawItems(g, map, d, l, fwd, mx, my, true);
        if (!doorFront) {
            drawCreatures(g, map, d, l, fwd, mx, my);
        }
        if (doorFront && d > 0) {
            drawDoor(g, map, d, l, mx, my);
        }
        drawItems(g, map, d, l, fwd, mx, my, false);
        if (doorFront) {
            drawCreatures(g, map, d, l, fwd, mx, my);
        }
        drawExplosions(g, map, d, l, fwd, mx, my);
        if (showsField(map, sq, mx, my)) {
            drawTeleporter(g, d, l); // over everything on the square, as DM's F0113 comes last
        }
    }

    private static boolean showsField(DungeonMap map, Square sq, int mx, int my) {
        return sq.type() == SquareType.TELEPORTER && sq.teleporterVisible() && map.isTeleporterOpen(mx, my);
    }

    // ---- explosions (DM's F0115 explosion block) --------------------------------

    /**
     * The PC's explosion pictures are 486 fire, 487 spell and 488 poison (DM's
     * C348-C350, found by size and look); smoke is the poison picture in smoke
     * colours. On the party's own square DM fills the whole view with an
     * explosion pattern instead: 489 + 3 × aspect + size, 48×31 each.
     */
    private static final int FIRST_EXPLOSION = 486;
    private static final int FIRST_EXPLOSION_PATTERN = 489;
    /** DM's explosion aspects (G211's order). */
    private static final int ASPECT_FIRE = 0;
    private static final int ASPECT_SPELL = 1;
    private static final int ASPECT_POISON = 2;
    private static final int ASPECT_SMOKE = 3;

    /** F0115: which picture an explosion type uses. */
    static int explosionAspect(int type) {
        return switch (type) {
            case Explosion.FIREBALL, Explosion.LIGHTNING_BOLT -> ASPECT_FIRE;
            case Explosion.POISON_BOLT, Explosion.POISON_CLOUD -> ASPECT_POISON;
            case Explosion.SMOKE -> ASPECT_SMOKE;
            default -> ASPECT_SPELL;
        };
    }

    /** DM's G212 palette changes for smoke (new colour x 10). */
    private static final int[] SMOKE_CHANGES = {0, 10, 20, 30, 40, 50, 120, 10, 80, 90, 100, 110, 120, 130, 140, 150};
    /** DM's G216 explosion base scales for D3, D2, D1, D0 (in 32nds, times the explosion's size / 256). */
    private static final int[] EXPLOSION_SCALE = {16, 23, 32, 32};
    /**
     * DM's G226: an explosion's centre on the left or right column of a
     * square, by explosion view square: D3 C/L/R, D2 C/L/R, D1 C/L/R, D0 C/L/R.
     */
    private static final int[][][] EXPLOSION_XY = {
            {{95, 50}, {127, 50}}, {{31, 50}, {63, 50}}, {{159, 50}, {191, 50}},
            {{92, 53}, {131, 53}}, {{-3, 53}, {46, 53}}, {{177, 53}, {226, 53}},
            {{83, 57}, {141, 57}}, {{-54, 57}, {18, 57}}, {{207, 57}, {277, 57}},
            {{0, 0}, {0, 0}}, {{-73, 60}, {-33, 60}}, {{256, 60}, {296, 60}}};
    /** DM's G225: a centred explosion's centre, in the same order. */
    private static final int[][] CENTRED_EXPLOSION_XY = {
            {111, 50}, {45, 50}, {179, 50}, {111, 53}, {20, 53}, {205, 53},
            {111, 57}, {-30, 57}, {253, 57}, {111, 60}, {-53, 60}, {276, 60}};

    private final java.util.Map<Integer, BufferedImage> explosionImages = new java.util.HashMap<>();
    private final java.util.Random explosionRandom = new java.util.Random();

    /**
     * The explosions on a square, as DM's F0115 draws them: on the left or
     * right column of the square (or its centre), the type's picture scaled
     * by the explosion's attack and the distance (F0114), and flipped at
     * random each time it is drawn, so it churns. On the party's own square
     * the whole view fills with the type's explosion pattern.
     */
    private void drawExplosions(Graphics2D g, DungeonMap map, int d, int l, Direction fwd, int mx, int my) {
        if (Math.abs(l) > 1 || d > MAX_DEPTH) {
            return;
        }
        List<Explosion> here = map.explosionsAt(mx, my);
        // DM's F0115: a fluxcage is no burst; its field is drawn over the square instead
        boolean fluxcage = here.removeIf(e -> e.type() == Explosion.FLUXCAGE);
        boolean hidden = viewer != null && viewer.endgame() != null && viewer.endgame().fluxcagesHidden();
        here.removeIf(e -> e.type() >= Explosion.REBIRTH_1); // drawn on the altar's wall face (drawRebirth)
        if (fluxcage && !hidden) { // DM's G077: not once the Grey Lord stands there
            drawField(g, d, l, FIELD_FLUXCAGE);
        }
        if (here.isEmpty()) {
            return;
        }
        if (d == 0 && l == 0) {
            for (Explosion e : here) {
                drawExplosionPattern(g, e);
            }
            return;
        }
        int index = (MAX_DEPTH - d) * 3 + (l == 0 ? 0 : l < 0 ? 1 : 2);
        for (Explosion e : here) {
            int[] at;
            if (e.centred()) {
                at = CENTRED_EXPLOSION_XY[index];
            } else {
                boolean left = e.cell() == fwd.ordinal() || e.cell() == fwd.turnLeft().ordinal();
                at = EXPLOSION_XY[index][left ? 0 : 1];
            }
            int scale = Math.min(32, Math.max(4, (Math.max(48, e.attack() + 1) * EXPLOSION_SCALE[MAX_DEPTH - d]) >> 8) & ~1);
            BufferedImage img = explosionImage(explosionAspect(e.type()), scale);
            if (img == null) {
                return;
            }
            int w = img.getWidth();
            int h = img.getHeight();
            int x = at[0] - w / 2 + 1;
            int y = at[1] - (h >> 1) + ((h & 1) == 0 ? 1 : 0);
            boolean flipX = explosionRandom.nextBoolean();
            boolean flipY = explosionRandom.nextBoolean();
            g.drawImage(img, flipX ? x + w : x, flipY ? y + h : y, flipX ? -w : w, flipY ? -h : h, null);
        }
    }

    /** DM's G228 and G227: the rebirth sparkle's and burst's centre and scale, D3 C/L/R, D2 C/L/R, D1 C. */
    private static final int[][] REBIRTH_1_XY = {
            {112, 53, 15}, {24, 53, 15}, {194, 53, 15}, {112, 59, 20}, {15, 59, 20}, {208, 59, 20}, {112, 70, 32}};
    private static final int[][] REBIRTH_2_XY = {
            {113, 57, 12}, {24, 57, 12}, {195, 57, 12}, {111, 63, 16}, {12, 63, 16}, {213, 63, 16}, {112, 76, 24}};

    /**
     * F0115 for a VI altar's rebirth, seen only on the wall face toward the
     * party: first the sparkle (the lightning bolt's head-on picture) at
     * G228's points, then the fire burst at G227's, flipped at random.
     */
    private void drawRebirth(Graphics2D g, DungeonMap map, int mx, int my, Direction front, int d, int l) {
        if (d > MAX_DEPTH || d == 1 && l != 0) {
            return;
        }
        int index = (MAX_DEPTH - d) * 3 + (l == 0 ? 0 : l < 0 ? 1 : 2);
        for (Explosion e : map.explosionsAt(mx, my)) {
            if (e.cell() != front.ordinal()
                    || e.type() != Explosion.REBIRTH_1 && e.type() != Explosion.REBIRTH_2) {
                continue;
            }
            int[] at = e.type() == Explosion.REBIRTH_1 ? REBIRTH_1_XY[index] : REBIRTH_2_XY[index];
            BufferedImage img;
            if (e.type() == Explosion.REBIRTH_1) {
                int graphic = FIRST_PROJECTILE + PROJECTILE_LIGHTNING;
                IndexedImage src = art.indexed(graphic);
                img = src == null ? null : distant(graphic, Math.max(1, Bitmaps.scaled(src.width(), at[2])),
                        Math.max(1, Bitmaps.scaled(src.height(), at[2])), null, false);
            } else {
                img = explosionImage(ASPECT_FIRE, at[2]);
            }
            if (img == null) {
                continue;
            }
            int w = img.getWidth();
            int h = img.getHeight();
            int x = at[0] - w / 2 + 1;
            int y = at[1] - (h >> 1) + ((h & 1) == 0 ? 1 : 0);
            boolean flipX = explosionRandom.nextBoolean();
            boolean flipY = explosionRandom.nextBoolean();
            g.drawImage(img, flipX ? x + w : x, flipY ? y + h : y, flipX ? -w : w, flipY ? -h : h, null);
        }
    }

    /** DM's F0114: aspect {@code aspect}'s picture at {@code scale}/32, smoke in its own colours. */
    private BufferedImage explosionImage(int aspect, int scale) {
        return explosionImages.computeIfAbsent(aspect * 64 + scale, key -> {
            IndexedImage src = art.indexed(FIRST_EXPLOSION + Math.min(aspect, ASPECT_POISON));
            if (src == null) {
                return null;
            }
            IndexedImage small = Bitmaps.shrink(src, Bitmaps.scaled(src.width(), scale),
                    Bitmaps.scaled(src.height(), scale), aspect == ASPECT_SMOKE ? SMOKE_CHANGES : null);
            return Bitmaps.toImage(small, Art.TRANSPARENT, Bitmaps.palette());
        });
    }

    /** The explosion pattern's 16-pixel units, as DM's F0133 copies them (like the teleporter field's). */
    private static final int PATTERN_UNIT = 16;

    /**
     * F0115 for an explosion on the party's square: the pattern for its
     * aspect and size (attack under 32 small, under 128 medium, else large)
     * fills the whole view, copied unit after unit along its rows from a
     * random unit, wrapping after a random last unit (87-90), so it boils.
     * Smoke uses the poison pattern in smoke colours.
     */
    private void drawExplosionPattern(Graphics2D g, Explosion e) {
        int aspect = explosionAspect(e.type());
        boolean smoke = aspect == ASPECT_SMOKE;
        int index = (smoke ? ASPECT_POISON : aspect) * 3;
        int size = e.attack() >> 5;
        if (size != 0) {
            index += size > 3 ? 2 : 1;
        }
        IndexedImage pattern = art.indexed(FIRST_EXPLOSION_PATTERN + index);
        if (pattern == null) {
            return;
        }
        if (smoke) {
            pattern = Bitmaps.shrink(pattern, pattern.width(), pattern.height(), SMOKE_CHANGES);
        }
        int last = explosionRandom.nextInt(4) + 87;
        int unit = explosionRandom.nextInt(64);
        int perRow = Math.max(1, pattern.width() / PATTERN_UNIT);
        int[] colours = Bitmaps.palette();
        BufferedImage out = new BufferedImage(VIEWPORT.width, VIEWPORT.height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < VIEWPORT.height; y++) {
            for (int ux = 0; ux < VIEWPORT.width; ux += PATTERN_UNIT) {
                int srcY = Math.min(unit / perRow, pattern.height() - 1);
                int srcX = (unit % perRow) * PATTERN_UNIT;
                for (int x = ux; x < Math.min(ux + PATTERN_UNIT, VIEWPORT.width); x++) {
                    int c = pattern.pixel(srcX + x - ux, srcY);
                    if (c != Art.TRANSPARENT) {
                        out.setRGB(x, y, 0xFF000000 | colours[c]);
                    }
                }
                if (++unit > last) {
                    unit = 0;
                }
            }
        }
        g.drawImage(out, 0, 0, null);
    }

    // ---- spells in flight (DM's F0115 projectile block) -------------------------

    /** DM's G210 projectile pictures for the spells, relative to the PC's first projectile, 454. */
    private static final int FIRST_PROJECTILE = 454;
    private static final int PROJECTILE_FIREBALL = 28;
    private static final int PROJECTILE_DEFAULT = 29;
    private static final int PROJECTILE_SLIME = 30;
    private static final int PROJECTILE_POISON = 31;
    /** The lightning bolt has a picture seen head-on and one seen side-on, after it. */
    private static final int PROJECTILE_LIGHTNING = 9;
    /** DM's G215 projectile scales: D3 back/front, D2 back/front, D1 back/front, D0 back. */
    private static final int[] PROJECTILE_SCALE = {13, 16, 19, 22, 25, 28, 32};

    /**
     * A spell in flight, as F0115 draws projectile aspects: centred on the
     * flight point, scaled by the distance and by its energy (a spell at full
     * energy on the party's own square is drawn as is), in the distance
     * colours of objects. A lightning bolt seen side-on uses its side picture,
     * turned the way it flies; head-on it is mirrored on the right.
     */
    private void drawSpell(Graphics2D g, Projectile p, Point at, int d, int l, int viewCell, Direction fwd) {
        int spell = p.spell();
        int graphic = FIRST_PROJECTILE + switch (spell) {
            case Explosion.FIREBALL -> PROJECTILE_FIREBALL;
            case Explosion.SLIME -> PROJECTILE_SLIME;
            case Explosion.LIGHTNING_BOLT -> PROJECTILE_LIGHTNING;
            case Explosion.POISON_BOLT, Explosion.POISON_CLOUD -> PROJECTILE_POISON;
            default -> PROJECTILE_DEFAULT;
        };
        boolean flip = false;
        if (spell == Explosion.LIGHTNING_BOLT) {
            boolean sideOn = (p.direction().ordinal() & 1) != (fwd.ordinal() & 1);
            if (sideOn) {
                graphic++;
                flip = fwd.turnRight() == p.direction();
            } else {
                flip = !(l > 0 || (l == 0 && (viewCell == 1 || viewCell == 2)));
            }
        }
        IndexedImage src = art.indexed(graphic);
        if (src == null) {
            return;
        }
        int w = src.width();
        int h = src.height();
        int scaleIndex = Math.min(PROJECTILE_SCALE.length - 1, (MAX_DEPTH - d) * 2 + (viewCell >= 2 ? 1 : 0));
        int[] changes = null;
        if (!(p.kineticEnergy() == 255 && d == 0 && l == 0)) {
            int scale = (PROJECTILE_SCALE[scaleIndex] * Math.max(96, p.kineticEnergy() + 1)) >> 8;
            w = Math.max(1, Bitmaps.scaled(w, scale));
            h = Math.max(1, Bitmaps.scaled(h, scale));
            changes = (scaleIndex >> 1) == 0 ? PAL_CHANGES_OBJECT_D3
                    : (scaleIndex >> 1) == 1 ? PAL_CHANGES_OBJECT_D2 : null;
        }
        BufferedImage img = distant(graphic, w, h, changes, flip);
        if (img != null) {
            g.drawImage(img, at.x - w / 2, at.y - h / 2, null);
        }
    }

    // ---- creatures -----------------------------------------------------------

    /** DM's creature view square for (depth, lateral): D3 C/L/R, D2 C/L/R, D1 C/L/R, D0 L/R; -1 if not drawn. */
    static int creatureSquare(int d, int l) {
        if (Math.abs(l) > 1 || (d == 0 && l == 0) || d > MAX_DEPTH) {
            return -1;
        }
        if (d == 0) {
            return l < 0 ? 9 : 10;
        }
        return (MAX_DEPTH - d) * 3 + (l == 0 ? 0 : l < 0 ? 1 : 2);
    }

    /**
     * The creatures on a square, as DM's F0115 places them: by the type's
     * coordinate set, at the cell each creature stands on (quarter-square
     * creatures), its row or column (half-square ones, which turn sideways
     * as a pair), or the centre (full-square ones and lone centred creatures).
     * The facing difference picks the picture: front, side (mirrored when
     * seen from the right) or back. Creatures at the back are drawn first.
     */
    private void drawCreatures(Graphics2D g, DungeonMap map, int d, int l, Direction fwd, int mx, int my) {
        Group group = map.groupAt(mx, my);
        int square = creatureSquare(d, l);
        if (group == null || square < 0) {
            return;
        }
        CreatureType type = group.type();
        // {coordinate cell, creature} for the back row, then the front row.
        List<int[]> back = new ArrayList<>();
        List<int[]> front = new ArrayList<>();
        switch (type.size()) {
            case FULL -> front.add(new int[] {4, 0});
            case HALF -> {
                boolean side = (delta(fwd, group, 0) & 1) != 0;
                if (group.centred()) {
                    front.add(new int[] {side ? 3 : 4, 0});
                } else {
                    for (int i = 0; i < group.count(); i++) {
                        int vc = fwd.viewCellOf(group.cellOf(i));
                        if (side) {
                            (vc <= 1 ? back : front).add(new int[] {vc <= 1 ? 2 : 4, i});
                        } else {
                            front.add(new int[] {vc == 0 || vc == 3 ? 0 : 1, i});
                        }
                    }
                }
            }
            case QUARTER -> {
                if (group.centred()) {
                    front.add(new int[] {4, 0});
                } else {
                    for (int i = 0; i < group.count(); i++) {
                        int vc = fwd.viewCellOf(group.cellOf(i));
                        (vc <= 1 ? back : front).add(new int[] {vc, i});
                    }
                }
            }
        }
        int[][] coords = CreatureArt.COORDINATES[Math.min(type.coordinateSet(), 2)][square];
        int[] shift = SHIFT_SETS[Math.min(Math.max(d, 1), 3) - 1];
        for (List<int[]> row : List.of(back, front)) {
            for (int[] placed : row) {
                int[] at = coords[placed[0]];
                if (at[0] == 0 && at[1] == 0) {
                    continue; // DM hides this cell from here
                }
                int i = placed[1];
                BufferedImage img = creaturePicture(map, group, i, fwd, d);
                if (img == null) {
                    continue;
                }
                int x = at[0] + shift[group.jitterX(i) & 7] - img.getWidth() / 2 + 1;
                int y = at[1] + shift[group.jitterY(i) & 7] - img.getHeight() + 1;
                g.drawImage(img, x, y, null);
            }
        }
    }

    /** DM's G223 shift sets: a creature's jitter (its aspect's offsets) in pixels at D1, D2 and D3. */
    private static final int[][] SHIFT_SETS = {
            {0, 1, 2, 3, 0, -3, -2, -1}, {0, 1, 1, 2, 0, -2, -1, -1}, {0, 1, 1, 1, 0, -1, -1, -1}};

    /** How creature {@code i} faces relative to the view: 0 away from it, odd side-on, 2 toward it. */
    private static int delta(Direction fwd, Group group, int i) {
        return Math.floorMod(fwd.ordinal() - group.facing(i).ordinal(), 4);
    }

    /**
     * Creature {@code i}'s picture, as DM's F115 picks it: the side view
     * (mirrored seen from the right) when side-on, the back when facing
     * away, otherwise the attack picture while it strikes or the front;
     * those two are mirrored when its look says so.
     */
    private BufferedImage creaturePicture(DungeonMap map, Group group, int i, Direction fwd, int d) {
        CreatureType type = group.type();
        int delta = delta(fwd, group, i);
        CreatureType.View view;
        boolean flip;
        if ((delta & 1) != 0 && type.hasSide()) {
            view = CreatureType.View.SIDE;
            flip = delta == 1;
        } else if (delta == 0 && type.hasBack()) {
            view = CreatureType.View.BACK;
            flip = false;
        } else {
            view = group.attacking(i) && type.hasAttack() ? CreatureType.View.ATTACK : CreatureType.View.FRONT;
            flip = group.flipped(i);
            if (d == 2 && view == CreatureType.View.FRONT && type.specialD2Front() && type.specialD2FrontIsFlipped()) {
                flip = true;
            }
        }
        return creatureArt.picture(type, view, Math.max(d, 1), flip, map);
    }

    /** Stairs seen side-on, beside the view at D2, D1 or D0 (DM draws none at D3 or straight ahead). */
    private void drawStairsSide(Graphics2D g, int d, int l, boolean up) {
        if (l == 0) {
            return;
        }
        int graphic;
        int zone;
        switch (d) {
            case 2 -> {
                graphic = STAIRS_SIDE_D2;
                zone = 826;
            }
            case 1 -> {
                graphic = up ? STAIRS_UP_SIDE_D1 : STAIRS_DOWN_SIDE_D1;
                zone = up ? 828 : 830;
            }
            case 0 -> {
                graphic = STAIRS_SIDE_D0;
                zone = 832;
            }
            default -> {
                return;
            }
        }
        drawZoned(g, graphic, zone + (l > 0 ? 1 : 0), l > 0);
    }

    // ---- objects on the floor and in the air ------------------------------
    //
    // Positions come from DM's own screen layout in GRAPHICS.DAT (Zones):
    // the bottom centre of an object lying on a cell, and the centre of one
    // in flight. The pictures are drawn full size on the party's own square
    // and scaled down with distance, like DM's object scales (32/32 at D0,
    // 27 and 21 for D1's near and far cells, 18 and 14 at D2, 12 at D3).

    /** Object scale by [depth][near ? 0 : 1], in 32nds. */
    private static final int[][] OBJECT_SCALE = {{32, 32}, {27, 21}, {18, 14}, {12, 12}};

    /** DM's view square number for (depth, lateral), as used by the zone table, or -1 if not shown. */
    static int viewSquare(int d, int l) {
        return switch (d) {
            case 3 -> switch (l) {
                case 0 -> 0;
                case -1 -> 1;
                case 1 -> 2;
                case -2 -> 3;
                case 2 -> 4;
                default -> -1;
            };
            case 2, 1 -> Math.abs(l) <= 1 ? (3 - d) * 3 + 2 + (l == 0 ? 0 : l < 0 ? 1 : 2) : -1;
            case 0 -> l == 0 ? 11 : -1;
            default -> -1;
        };
    }

    /**
     * Draws the piles on a square's far cells (view cells 0 and 1) or near
     * cells (2 and 3), each pile bottom first. Items in flight over the
     * square are drawn with the near cells, in front of everything on it.
     */
    private void drawItems(Graphics2D g, DungeonMap map, int d, int l, Direction fwd, int mx, int my, boolean far) {
        int square = viewSquare(d, l);        if (square < 0) {
            return;
        }
        int[] viewCells = far ? new int[] {0, 1} : new int[] {3, 2};
        for (int viewCell : viewCells) {
            Point at = art.zone(Zones.FLOOR_OBJECTS + square * 4 + viewCell);
            if (at == null) {
                continue;
            }
            for (Item item : map.itemsAt(mx, my, fwd.cellOf(viewCell))) {
                drawObject(g, item, at, OBJECT_SCALE[d][viewCell >= 2 ? 0 : 1], true);
            }
        }
        if (!far) {
            for (Projectile p : map.projectiles()) {
                if (p.x() == mx && p.y() == my) {
                    int viewCell = fwd.viewCellOf(p.cell());
                    Point at = art.zone(Zones.FLYING_OBJECTS + square * 4 + viewCell);
                    if (at != null && p.isSpell()) {
                        drawSpell(g, p, at, d, l, viewCell, fwd);
                    } else if (at != null) {
                        drawObject(g, p.item(), at, OBJECT_SCALE[d][viewCell >= 2 ? 0 : 1], false);
                    }
                }
            }
        }
    }

    private void drawObject(Graphics2D g, Item item, Point at, int scale32, boolean onFloor) {
        int graphic = ItemCatalog.floorGraphic(item);
        BufferedImage img = graphic < 0 ? null : art.sprite(graphic);
        boolean own = img != null;
        if (img == null) {
            BufferedImage icon = art.iconSprite(item);
            if (icon == null) {
                return;
            }
            img = icon;
        }
        int w = Math.max(1, img.getWidth() * scale32 / 32);
        int h = Math.max(1, img.getHeight() * scale32 / 32);
        if (own) {
            img = distant(graphic, w, h, objectChanges(scale32), false);
        }
        int y = onFloor ? at.y - h : at.y - h / 2;
        g.drawImage(img, at.x - w / 2, y, w, h, null);
    }

    // ---- distance colours -----------------------------------------------------
    //
    // DM shrinks pictures for D2 and D3 itself, picking source pixels, and
    // swaps colours as it does (F0129's palette changes): far things lose
    // their highlights and darken, before the light's palette dims the view.

    /** G0213/G0214: objects (and things in flight) shrunk to D3 and D2 sizes. */
    private static final int[] PAL_CHANGES_OBJECT_D3 = {0, 120, 10, 30, 40, 30, 0, 60, 30, 90, 100, 110, 0, 20, 140, 130};
    private static final int[] PAL_CHANGES_OBJECT_D2 = {0, 10, 20, 30, 40, 30, 60, 70, 50, 90, 100, 110, 120, 130, 140, 150};

    private static int[] wallOrnamentChanges(int d) {
        return d == 3 ? PAL_CHANGES_WALL_ORNAMENT_D3 : d == 2 ? PAL_CHANGES_WALL_ORNAMENT_D2 : null;
    }

    /**
     * DM keeps two shrunk object sizes, each with its colours: the D2 one
     * (our D1 far and D2 near scales) and the D3 one (D2 far and D3). Larger
     * pictures keep their colours.
     */
    static int[] objectChanges(int scale32) {
        return scale32 >= 27 ? null : scale32 >= 16 ? PAL_CHANGES_OBJECT_D2 : PAL_CHANGES_OBJECT_D3;
    }

    private final java.util.Map<String, BufferedImage> distantImages = new java.util.HashMap<>();

    /**
     * Graphic {@code graphic} shrunk to w x h DM's way with colour changes
     * {@code changes} (null: none), colour 10 see-through, mirrored if asked.
     */
    private BufferedImage distant(int graphic, int w, int h, int[] changes, boolean flip) {
        String key = graphic + "/" + w + "x" + h + "/" + (changes == null ? 0 : changes.hashCode()) + "/" + flip;
        return distantImages.computeIfAbsent(key, k -> {
            IndexedImage src = art.indexed(graphic);
            if (src == null) {
                return null;
            }
            IndexedImage small = Bitmaps.shrink(src, w, h, changes);
            return Bitmaps.toImage(flip ? Bitmaps.flip(small) : small, Art.TRANSPARENT, Bitmaps.palette());
        });
    }

    private void drawFloorOrnament(Graphics2D g, int d, int l, int ornament) {
        int base = FIRST_FLOOR_ORNAMENT + ornament * 6 + (MAX_DEPTH - d) * 2;
        BufferedImage img = art.sprite(l == 0 ? base + 1 : base);
        if (img == null) {
            return;
        }
        int y = FLOOR_CENTRE_Y[d] - img.getHeight() / 2;
        int x;
        if (l == 0) {
            x = (VIEWPORT.width - img.getWidth()) / 2;
        } else if (d == 1) {
            x = 0;
        } else {
            x = VIEWPORT.width / 2 - MID_SPACING[d] - img.getWidth() / 2;
        }
        if (l > 0) {
            x = VIEWPORT.width - x - img.getWidth();
        }
        // DM flips footprints straight ahead with the walls (G076)
        paste(g, l == 0 ? base + 1 : base, x, y, l > 0 || (l == 0 && ornament == FOOTPRINTS && viewFlipped));
    }

    /** The magic footprints' pieces (DM's floor ornament 15) come just before the first floor decoration's: 379-384. */
    private static final int FOOTPRINTS = -1;

    /** Door decorations (441 + k); D2 and D3 versions are shrunk with DM's palette changes. */
    static final int FIRST_DOOR_ORNAMENT = 441;
    /** The button set into the right-hand door pillar (8x9, with a bevelled edge). */
    static final int DOOR_BUTTON = 453;
    /** DM's door button zone (1950) plus D3R 0, D3C 1, D2C 2, D1C 3; the heights it shrinks to. */
    private static final int DOOR_BUTTON_ZONE = 1950;
    private static final int[] DOOR_BUTTON_HEIGHT = {4, 4, 6, 9};

    /**
     * G0207: where each kind of door decoration goes on the panel, by
     * coordinate set and depth (D3, D2, D1): x, y, width, height. G0196 picks
     * the set for each of the 12 door decorations; the broken-door mask uses set 1.
     */
    private static final int[][][] DOOR_ORNAMENT_BOX = {
            {{17, 8, 15, 10}, {22, 11, 21, 13}, {32, 13, 32, 19}},
            {{0, 0, 48, 41}, {0, 0, 64, 61}, {0, 0, 96, 88}},
            {{17, 15, 15, 10}, {22, 22, 21, 13}, {32, 31, 32, 19}},
            {{23, 31, 13, 9}, {30, 41, 19, 12}, {44, 61, 32, 19}}};
    private static final int[] DOOR_ORNAMENT_SET = {0, 1, 1, 1, 0, 2, 3, 1, 2, 2, 1, 1};
    private static final int[] PAL_CHANGES_DOOR_ORNAMENT_D3 = {0, 120, 10, 30, 40, 30, 0, 60, 30, 90, 100, 110, 0, 20, 0, 130};
    private static final int[] PAL_CHANGES_DOOR_ORNAMENT_D2 = {0, 10, 20, 30, 40, 30, 60, 70, 50, 90, 100, 110, 120, 130, 140, 150};
    /** G0198/G0199: door buttons and wall decorations shrunk for D3 and D2. */
    private static final int[] PAL_CHANGES_WALL_ORNAMENT_D3 = {0, 0, 120, 30, 40, 30, 0, 60, 30, 90, 100, 110, 0, 10, 0, 20};
    private static final int[] PAL_CHANGES_WALL_ORNAMENT_D2 = {0, 120, 10, 30, 40, 30, 60, 70, 50, 90, 100, 110, 0, 20, 140, 130};

    /**
     * A door seen head-on, as DM draws it on the PC (F0111, ScummVM's DOS
     * path): the frame (pillars and lintel, where that depth shows them), the
     * button on the right pillar, then the panel in its zone. A part-open door
     * (states 1-3) uses the next zones, which cut it as it rises; a broken one
     * is cut by DM's mask. Standing in the doorway, only the frame's edges show.
     */
    private void drawDoor(Graphics2D g, DungeonMap map, int d, int l, int mx, int my) {
        if (d == 0) {
            if (l == 0) {
                drawZoned(g, 86, 728, false);
            }
            return;
        }
        int col = l + 1;
        switch (d) {
            case 3 -> {
                int frame = l == 0 ? DOOR_FRAME_LEFT_D3C : DOOR_FRAME_LEFT_D3SIDE;
                int zone = l == 0 ? 722 : l < 0 ? 718 : 720; // left pillar zone; the right one follows
                drawZoned(g, frame, zone, false);
                drawZoned(g, frame, zone + 1, true);
            }
            case 2 -> {
                drawZoned(g, DOOR_FRAME_TOP_D2, 729 + col, false);
                if (l == 0) {
                    drawZoned(g, DOOR_FRAME_LEFT_D2, 724, false);
                    drawZoned(g, DOOR_FRAME_LEFT_D2, 725, true);
                }
            }
            default -> {
                drawZoned(g, DOOR_FRAME_TOP_D1, 732 + col, false);
                if (l == 0) {
                    drawZoned(g, DOOR_FRAME_LEFT_D1, 726, false);
                    drawZoned(g, DOOR_FRAME_LEFT_D1, 727, true);
                }
            }
        }
        if (map.decorations().doorButton(mx, my)) {
            int button = d == 3 ? (l == 0 ? 1 : l > 0 ? 0 : -1) : (l == 0 ? 4 - d : -1);
            if (button >= 0) {
                drawDoorButton(g, button, d == 1);
            }
        }
        int state = map.doorState(mx, my);
        if (state == DungeonMap.DOOR_OPEN) {
            return;
        }
        BufferedImage panel = doorPanel(map.doorStyle(mx, my), d, map.decorations().door(mx, my),
                state == DungeonMap.DOOR_BROKEN, d == 1 && l == 0 && viewer.thievesEye());
        if (panel == null) {
            return;
        }
        int zone = DOOR_ZONE[d][col] + (state >= 1 && state <= 3 ? state : 0);
        drawZoned(g, panel, zone, false);
    }

    /**
     * A door's panel for depth {@code d}, its decoration (or the broken-door
     * mask) painted on as DM does; straight ahead at D1 a thieves' eye cuts
     * its hole (mask 440, DM's door ornament 16) before anything else.
     */
    private BufferedImage doorPanel(int style, int d, int ornament, boolean broken, boolean thievesEye) {
        IndexedImage door = art.indexed(FIRST_DOOR + style * 3 + (MAX_DEPTH - d));
        if (door == null) {
            return null;
        }
        byte[] pixels = door.pixels().clone();
        if (ornament >= 0 && ornament < DOOR_ORNAMENT_SET.length) {
            paint(pixels, door.width(), art.indexed(FIRST_DOOR_ORNAMENT + ornament), DOOR_ORNAMENT_SET[ornament], d);
        }
        if (thievesEye) {
            // the PC's mask (80x74) is smaller than its box, the whole 96x88 door: centre it
            paint(pixels, door.width(), art.indexed(DOOR_THIEVES_EYE_MASK), 1, d, true);
        }
        if (broken) {
            paint(pixels, door.width(), art.indexed(DOOR_DESTROYED_MASK), 1, d);
        }
        return Bitmaps.toImage(new IndexedImage(door.width(), door.height(), pixels), Art.TRANSPARENT,
                Bitmaps.palette());
    }

    /** DM's F0109: a decoration pasted onto a door, its gold (colour 9) see-through; its colour 10 cuts holes. */
    private static void paint(byte[] door, int doorWidth, IndexedImage ornament, int set, int d) {
        paint(door, doorWidth, ornament, set, d, false);
    }

    /** {@link #paint}, with a picture smaller than its box centred in it if {@code centred}. */
    private static void paint(byte[] door, int doorWidth, IndexedImage ornament, int set, int d, boolean centred) {
        if (ornament == null) {
            return;
        }
        int[] box = DOOR_ORNAMENT_BOX[set][MAX_DEPTH - d];
        IndexedImage img = d == 1 ? ornament
                : Bitmaps.shrink(ornament, box[2], box[3], d == 2 ? PAL_CHANGES_DOOR_ORNAMENT_D2 : PAL_CHANGES_DOOR_ORNAMENT_D3);
        int height = door.length / doorWidth;
        int offsetX = centred ? Math.max(0, (box[2] - img.width()) / 2) : 0;
        int offsetY = centred ? Math.max(0, (box[3] - img.height()) / 2) : 0;
        for (int y = 0; y < Math.min(img.height(), box[3]); y++) {
            for (int x = 0; x < Math.min(img.width(), box[2]); x++) {
                int c = img.pixel(x, y);
                int dx = box[0] + offsetX + x;
                int dy = box[1] + offsetY + y;
                if (c != 9 && dx < doorWidth && dy < height) {
                    door[dy * doorWidth + dx] = (byte) c;
                }
            }
        }
    }

    /** DM's door button (F0110) in its zone, shrunk for D2 and D3; at D1 it can be pressed. */
    private void drawDoorButton(Graphics2D g, int index, boolean clickable) {
        IndexedImage src = art.indexed(DOOR_BUTTON);
        if (src == null) {
            return;
        }
        IndexedImage img = src;
        if (index < 3) {
            int h = DOOR_BUTTON_HEIGHT[index];
            int w = Math.max(1, Math.round(src.width() * h / (float) src.height()));
            img = Bitmaps.shrink(src, w, h, index == 2 ? PAL_CHANGES_WALL_ORNAMENT_D2 : PAL_CHANGES_WALL_ORNAMENT_D3);
        }
        Rectangle drawn = drawZoned(g, Bitmaps.toImage(img, Art.TRANSPARENT, Bitmaps.palette()),
                DOOR_BUTTON_ZONE + index, false);
        if (clickable && drawn != null) {
            doorButtonHit = new Rectangle(drawn.x + VIEWPORT.x, drawn.y + VIEWPORT.y, drawn.width, drawn.height);
        }
    }
    // ---- teleporter fields (DM's F0113) ----------------------------------------
    //
    // A visible, open teleporter fills its square's outline with the 32x32
    // field pattern (graphic 76). The outline is where the square's wall
    // piece would be: the front face for the centre column, and for the side
    // squares the PC's field masks 70-75, which are exactly the left wall
    // pieces' silhouettes (mirrored on the right). The pattern isn't tiled:
    // DM copies it 16 pixels at a time, unit after unit, along the rows of
    // the box, starting at a random unit and wrapping a little before or
    // after the pattern's 64 units, so it comes out scrambled and different
    // every frame.

    private static final int FIELD_TELEPORTER = 76;
    private static final int FIELD_FLUXCAGE = 77;
    /** Masks for the side squares by [depth][|lateral|] (none for the centre). */
    private static final int[][] FIELD_MASK = {{-1, 75}, {-1, 74}, {-1, 73, 72}, {-1, 71, 70}};
    /** G0188's base unit count by [depth][centre 0, side 1]; DM adds random(2). */
    private static final int[][] FIELD_UNITS = {{59, 63}, {61, 63}, {60, 63}, {63, 63}};
    private static final int FIELD_UNIT = 16;

    private final java.util.Random fieldRandom = new java.util.Random();
    private boolean fieldDrawn;

    private void drawTeleporter(Graphics2D g, int d, int l) {
        drawField(g, d, l, FIELD_TELEPORTER);
    }

    /** F0113 with graphic {@code field}: the teleporter's (76) or a fluxcage's (77). */
    private void drawField(Graphics2D g, int d, int l, int field) {
        IndexedImage pattern = art.indexed(field);
        if (pattern == null) {
            return;
        }
        // The box (viewport coordinates) and where the unclipped wall piece would start.
        Rectangle box;
        int pieceX;
        int pieceY;
        int pieceW;
        if (d == 0 && l == 0) {
            box = new Rectangle(0, 0, VIEWPORT.width, VIEWPORT.height);
            pieceX = 0;
            pieceY = 0;
            pieceW = box.width;
        } else {
            IndexedImage piece = art.indexed(WALL[d][l + 2]);
            int[] c = piece == null ? null : art.coord(WALL_ZONE[d][l + 2], piece.width(), piece.height());
            if (c == null) {
                return;
            }
            box = new Rectangle(c[0], c[1], c[2], c[3]);
            pieceX = c[0] - c[4];
            pieceY = c[1] - c[5];
            pieceW = piece.width();
        }
        IndexedImage mask = l == 0 ? null : art.indexed(FIELD_MASK[d][Math.abs(l)]);
        boolean right = l > 0;
        int maskX = mask == null ? 0 : right ? pieceX + pieceW - mask.width() : pieceX;

        int units = FIELD_UNITS[d][l == 0 ? 0 : 1] + fieldRandom.nextInt(2);
        int unit = fieldRandom.nextInt(32);
        int perRow = pattern.width() / FIELD_UNIT;
        int firstUnitX = Math.floorDiv(box.x, FIELD_UNIT) * FIELD_UNIT;
        BufferedImage out = new BufferedImage(box.width, box.height, BufferedImage.TYPE_INT_ARGB);
        int[] colours = Bitmaps.palette();
        for (int y = box.y; y < box.y + box.height; y++) {
            for (int ux = firstUnitX; ux < box.x + box.width; ux += FIELD_UNIT) {
                int srcY = unit / perRow;
                int srcX = (unit % perRow) * FIELD_UNIT;
                for (int x = Math.max(ux, box.x); x < Math.min(ux + FIELD_UNIT, box.x + box.width); x++) {
                    int c = pattern.pixel(srcX + x - ux, srcY);
                    if (c == Art.TRANSPARENT || c == 0 || !inMask(mask, right, x - maskX, y - pieceY)) {
                        continue;
                    }
                    out.setRGB(x - box.x, y - box.y, 0xFF000000 | colours[c]);
                }
                if (++unit >= units) {
                    unit = 0;
                }
            }
        }
        g.drawImage(out, box.x, box.y, null);
        fieldDrawn = true;
    }

    /** Whether mask pixel (x, y) (mirrored for a right-hand square) lets the field through: white does, black and outside don't. */
    private static boolean inMask(IndexedImage mask, boolean mirrored, int x, int y) {
        if (mask == null) {
            return true;
        }
        if (x < 0 || y < 0 || x >= mask.width() || y >= mask.height()) {
            return false;
        }
        return mask.pixel(mirrored ? mask.width() - 1 - x : x, y) != 0;
    }

    /** DM's wall zones by [depth][lateral + 2] (ScummVM's PC zone numbers 702-717); -1 where none. */
    private static final int[][] WALL_ZONE = {
            {-1, 716, -1, 717, -1},
            {-1, 713, 712, 714, -1},
            {707, 710, 709, 711, 708},
            {702, 705, 704, 706, 703},
    };

    private void drawWall(Graphics2D g, int d, int l, boolean flipped) {
        // The opposite side's piece, mirrored, has this side's silhouette.
        int graphic = flipped ? WALL[d][-l + 2] : WALL[d][l + 2];
        if (WALL_ZONE[d][l + 2] >= 0 && drawZoned(g, graphic, WALL_ZONE[d][l + 2], flipped) != null) {
            return;
        }
        int x = WALL_X[d][l + 2];
        int y = WALL_Y[d][l + 2];
        g.drawImage(flipped ? art.spriteFlipped(graphic) : art.sprite(graphic), x, y, null);
    }

    // ---- DM's layout zones ---------------------------------------------------

    /**
     * Draws graphic {@code graphic} (colour 10 see-through) where DM's layout
     * puts it in zone {@code zone}, mirrored if asked; returns where it went
     * (viewport coordinates), or null if the zone doesn't place it.
     */
    private Rectangle drawZoned(Graphics2D g, int graphic, int zone, boolean flip) {
        BufferedImage img = flip ? art.spriteFlipped(graphic) : art.sprite(graphic);
        return drawZoned(g, img, zone, flip);
    }

    /**
     * Draws {@code img} (already mirrored when {@code flipped}) in zone
     * {@code zone}. As in DM, the zone may cut the picture; for a mirrored
     * picture the cut is measured from its other edge.
     */
    private Rectangle drawZoned(Graphics2D g, BufferedImage img, int zone, boolean flipped) {
        if (img == null) {
            return null;
        }
        int[] c = art.coord(zone, img.getWidth(), img.getHeight());
        if (c == null) {
            return null;
        }
        int sx = flipped ? img.getWidth() - c[4] - c[2] : c[4];
        g.drawImage(img.getSubimage(sx, c[5], c[2], c[3]), c[0], c[1], null);
        return new Rectangle(c[0], c[1], c[2], c[3]);
    }

    private void paste(Graphics2D g, int graphic, int x, int y, boolean flipped) {
        BufferedImage img = flipped ? art.spriteFlipped(graphic) : art.sprite(graphic);
        if (img != null) {
            g.drawImage(img, x, y, null);
        }
    }

    /** DM's zone for the champion portrait in a mirror at D1 (737), which puts it at (96, 35). */
    private static final int PORTRAIT_ZONE = 737;
    /** How far above the face's middle the mirror's centre sits at D1, so the glass meets the portrait zone. */
    private static final int MIRROR_RAISE = 14;

    /** The mirror sits centred on the front wall, above its middle; smaller with distance. */
    private void drawMirror(Graphics2D g, int d, ChampionMirror mirror) {
        Rectangle face = FRONT[d];
        double scale = face.width / (double) FRONT[1].width;
        int w = (int) Math.round(MIRROR_W * scale);
        int h = (int) Math.round(MIRROR_H * scale);
        int x = face.x + (face.width - w) / 2;
        int y = (int) Math.round(face.y + face.height / 2.0 - h / 2.0 - MIRROR_RAISE * scale);
        int[] portraitZone = d == 1 ? art.coord(PORTRAIT_ZONE, PORTRAIT_W, PORTRAIT_H) : null;
        if (portraitZone != null) {
            x = portraitZone[0] - GLASS_X;
            y = portraitZone[1] - GLASS_Y;
        }
        BufferedImage frame = art.sprite(GraphicsFile.MIRROR_FRONT);
        if (frame != null) {
            g.drawImage(frame, x, y, w, h, null);
        }
        if (mirror.taken()) {
            return;
        }
        Rectangle glass = new Rectangle(x + scaled(GLASS_X, scale), y + scaled(GLASS_Y, scale),
                scaled(PORTRAIT_W, scale), scaled(PORTRAIT_H, scale));
        BufferedImage portrait = art.portrait(mirror.champion().portrait());
        if (portrait != null) {
            g.drawImage(portrait, glass.x, glass.y, glass.width, glass.height, null);
        } else {
            Placeholders.portrait(g, mirror.champion(), glass);
        }
        if (d == 1) {
            portraitHit = new Rectangle(glass.x + VIEWPORT.x, glass.y + VIEWPORT.y, glass.width, glass.height);
        }
    }

    private static int scaled(int v, double scale) {
        return (int) Math.round(v * scale);
    }
}
