package dm.ui;

import dm.data.GraphicsFile;
import dm.data.Zones;
import dm.model.ChampionMirror;
import dm.model.Direction;
import dm.model.DungeonMap;
import dm.model.Item;
import dm.model.ItemCatalog;
import dm.model.Party;
import dm.model.Projectile;
import dm.model.Square;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

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
    private static final int[][] WALL_X = {
            {0, 0, 0, 191, 0},
            {0, 0, 32, 164, 0},
            {0, 0, 59, 146, 216},
            {0, 6, 77, 135, 180},
    };
    private static final int[][] WALL_Y = {
            {0, 0, 0, 0, 0},
            {8, 8, 8, 8, 8},
            {24, 18, 18, 18, 24},
            {25, 25, 25, 25, 25},
    };
    /** Front face of the centre square at each depth: x, y, width, height. */
    static final Rectangle[] FRONT = {
            null,
            new Rectangle(32, 8, 160, 111),
            new Rectangle(59, 18, 106, 74),
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
    private Rectangle portraitHit;
    private Rectangle wallHit;
    private Rectangle doorButtonHit;

    public TexturedViewRenderer(Art art) {
        this.art = art;
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
    public void draw(Graphics2D screen, Party party) {
        portraitHit = null;
        wallHit = null;
        doorButtonHit = null;
        Graphics2D g = (Graphics2D) screen.create(VIEWPORT.x, VIEWPORT.y, VIEWPORT.width, VIEWPORT.height);
        try {
            DungeonMap map = party.map();
            Direction fwd = party.facing();
            Direction right = fwd.turnRight();
            boolean flipped = ((party.x() + party.y() + fwd.ordinal()) & 1) != 0;

            paste(g, CEILING, 0, 0, flipped);
            paste(g, FLOOR, 0, FLOOR_Y, flipped);

            for (int d = MAX_DEPTH; d >= 0; d--) {
                for (int l : new int[] {-2, 2, -1, 1, 0}) {
                    int mx = party.x() + fwd.dx * d + right.dx * l;
                    int my = party.y() + fwd.dy * d + right.dy * l;
                    Square sq = map.get(mx, my);
                    if (sq.looksSolid()) {
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
                    } else if (Math.abs(l) <= 1) {
                        drawFeature(g, map, sq, d, l, fwd, mx, my);
                    } else {
                        drawItems(g, map, d, l, fwd, mx, my, true);
                        drawItems(g, map, d, l, fwd, mx, my, false);
                    }
                }
            }
        } finally {
            g.dispose();
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
        BufferedImage img = art.sprite(FIRST_WALL_ORNAMENT + 2 * ornament + 1);
        if (img == null) {
            return null;
        }
        double scale = face.width / (double) FRONT[1].width;
        int w = (int) Math.round(img.getWidth() * scale);
        int h = (int) Math.round(img.getHeight() * scale);
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
        g.drawImage(img, x, y, w, h, null);
        return new Rectangle(x, y, w, h);
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
        int cx = SIDE_FACE_CENTRE[d][0];
        if (rightSide) {
            cx = VIEWPORT.width - cx;
        }
        int y = SIDE_FACE_CENTRE[d][1] - h / 2;
        g.drawImage(img, cx - w / 2, y, w, h, null);
    }

    // ---- open squares: doors, stairs, pits, teleporters -------------------
    //
    // Unlike walls, these pieces don't tile edge to edge, and their entries in
    // DM's zone table (Zones) haven't been identified yet. Their positions are
    // fitted to the wall geometry instead: doors stand on the plane halfway
    // through their square, pits are centred on that plane's floor line, and
    // stairs sit on their square's near floor edge. Depth index: [1]=D1 .. [3]=D3.

    /** Horizontal distance between neighbouring squares on each depth's mid-square plane. */
    private static final int[] MID_SPACING = {0, 128, 84, 56};

    private static final int FIRST_DOOR = 246;
    private static final Rectangle[] DOOR_PANEL = {
            null, new Rectangle(64, 14, 96, 88), new Rectangle(80, 20, 64, 61), new Rectangle(90, 28, 44, 38)};
    private static final int[] DOOR_PILLAR = {86, 87, 88, 89};
    private static final int[] DOOR_PILLAR_Y = {6, 11, 18, 26};
    private static final int[] DOOR_LINTEL = {-1, 91, 92, -1};
    private static final int[][] DOOR_LINTEL_XY = {null, {61, 10}, {77, 17}, null};

    /** Pit holes (graphics 50-55): the centre and left square at each depth. */
    private static final int[] PIT_C = {-1, 55, 53, 51};
    private static final int[][] PIT_C_XY = {null, {43, 90}, {66, 74}, {80, 62}};
    private static final int[] PIT_L = {-1, 54, 52, 50};
    private static final int[][] PIT_L_XY = {null, {0, 90}, {2, 74}, {10, 62}};

    /**
     * Floor ornaments (pressure plates, grates, moss...): 6 pieces each from
     * graphic 385, ordered D3L, D3C, D2L, D2C, D1L, D1C. They are centred on
     * the square's mid-square floor line; D1 side pieces are pre-cropped at
     * the screen edge.
     */
    static final int FIRST_FLOOR_ORNAMENT = 385;
    private static final int[] FLOOR_CENTRE_Y = {0, 102, 80, 66};

    /** Stairs climbing into darkness, with handrails rising away (graphics 108-113). */
    private static final int[] STAIRS_UP_C = {-1, 113, 111, 109};
    private static final int[][] STAIRS_UP_C_XY = {null, {32, 19}, {62, 29}, {78, 28}};
    private static final int[] STAIRS_UP_L = {-1, 112, 110, 108};
    private static final int[][] STAIRS_UP_L_XY = {null, {0, 19}, {-1, 30}, {14, 29}};
    /** A stairwell opening in the floor, steps and rails dropping away (graphics 115-120). */
    private static final int[] STAIRS_DOWN_C = {-1, 120, 118, 116};
    private static final int[][] STAIRS_DOWN_C_XY = {null, {36, 27}, {63, 31}, {75, 25}};
    private static final int[] STAIRS_DOWN_L = {-1, 119, 117, 115};
    private static final int[][] STAIRS_DOWN_L_XY = {null, {0, 28}, {-2, 30}, {2, 33}};

    private void drawFeature(Graphics2D g, DungeonMap map, Square sq, int d, int l, Direction fwd, int mx, int my) {
        int ornament = map.floorOrnament(mx, my);
        if (ornament >= 0 && d > 0) {
            drawFloorOrnament(g, d, l, ornament);
        }
        // Items in the far cells sit behind a door; the near ones in front of it.
        drawItems(g, map, d, l, fwd, mx, my, true);
        drawSquareFeature(g, map, sq, d, l, fwd, mx, my);
        drawItems(g, map, d, l, fwd, mx, my, false);
    }

    private void drawSquareFeature(Graphics2D g, DungeonMap map, Square sq, int d, int l, Direction fwd,
                                   int mx, int my) {
        switch (sq.type()) {
            case DOOR -> {
                if (sq.facesAlong(fwd)) {
                    drawDoor(g, d, l, map.doorStyle(mx, my), map.doorState(mx, my),
                            map.decorations().door(mx, my), map.decorations().doorButton(mx, my));
                }
            }
            case STAIRS -> {
                if (d > 0 && sq.facesAlong(fwd)) {
                    if (sq.stairsUp()) {
                        drawLeftOrCentre(g, d, l, STAIRS_UP_C, STAIRS_UP_C_XY, STAIRS_UP_L, STAIRS_UP_L_XY);
                    } else {
                        drawLeftOrCentre(g, d, l, STAIRS_DOWN_C, STAIRS_DOWN_C_XY, STAIRS_DOWN_L, STAIRS_DOWN_L_XY);
                    }
                }
            }
            case PIT -> {
                if (d > 0 && map.isPitOpen(mx, my)) {
                    drawLeftOrCentre(g, d, l, PIT_C, PIT_C_XY, PIT_L, PIT_L_XY);
                }
            }
            case TELEPORTER -> {
                if (d > 0) {
                    drawTeleporter(g, d, l, mx, my);
                }
            }
            default -> { }
        }
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
                    if (at != null) {
                        drawObject(g, p.item(), at, OBJECT_SCALE[d][viewCell >= 2 ? 0 : 1], false);
                    }
                }
            }
        }
    }

    private void drawObject(Graphics2D g, Item item, Point at, int scale32, boolean onFloor) {
        int graphic = ItemCatalog.floorGraphic(item);
        BufferedImage img = graphic < 0 ? null : art.sprite(graphic);
        if (img == null) {
            BufferedImage icon = art.iconSprite(item);
            if (icon == null) {
                return;
            }
            img = icon;
        }
        int w = Math.max(1, img.getWidth() * scale32 / 32);
        int h = Math.max(1, img.getHeight() * scale32 / 32);
        int y = onFloor ? at.y - h : at.y - h / 2;
        g.drawImage(img, at.x - w / 2, y, w, h, null);
    }

    /**
     * Pieces drawn for the centre square and the left square only; the right
     * square uses the left piece mirrored at the mirrored position.
     */
    private void drawLeftOrCentre(Graphics2D g, int d, int l, int[] centre, int[][] centreXY, int[] left, int[][] leftXY) {
        if (l == 0) {
            paste(g, centre[d], centreXY[d][0], centreXY[d][1], false);
            return;
        }
        BufferedImage img = art.sprite(left[d]);
        if (img == null) {
            return;
        }
        int x = leftXY[d][0];
        if (l > 0) {
            x = VIEWPORT.width - x - img.getWidth();
        }
        paste(g, left[d], x, leftXY[d][1], l > 0);
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
        paste(g, l == 0 ? base + 1 : base, x, y, l > 0);
    }

    /** Door decorations (441 + k) are drawn for the D1 panel (96 wide) and scaled down with it. */
    static final int FIRST_DOOR_ORNAMENT = 441;
    /** The button set into the right-hand door pillar (8x9, with a bevelled edge), checked against the original. */
    static final int DOOR_BUTTON = 453;
    /** Button size at D1 relative to its graphic, and its height up the pillar (0 = top of the panel). */
    private static final double DOOR_BUTTON_SCALE = 1.0;
    private static final double DOOR_BUTTON_HEIGHT = 0.35;
    /** How far right of the pillar's centre the button sits at D1, in pixels (scaled with distance). */
    private static final int DOOR_BUTTON_RIGHT = 4;

    /**
     * Door frame pillars and lintel, plus the panel unless the door is open or
     * broken. A decoration rides on the panel; a button sits on the right pillar.
     */
    private void drawDoor(Graphics2D g, int d, int l, int style, int state, int ornament, boolean button) {
        if (d == 0) {
            // Standing in the doorway: only the pillars at the screen edges show.
            if (l == 0) {
                BufferedImage pillar = art.sprite(DOOR_PILLAR[0]);
                if (pillar != null) {
                    paste(g, DOOR_PILLAR[0], 0, DOOR_PILLAR_Y[0], false);
                    paste(g, DOOR_PILLAR[0], VIEWPORT.width - pillar.getWidth(), DOOR_PILLAR_Y[0], true);
                }
            }
            return;
        }
        int shift = MID_SPACING[d] * l;
        Rectangle panel = DOOR_PANEL[d];
        if (state >= 1 && state <= 4) {
            // DM doors slide up into the lintel; states 1-3 are part-way, 4 is shut.
            BufferedImage img = art.sprite(FIRST_DOOR + style * 3 + (MAX_DEPTH - d));
            if (img != null) {
                int raised = panel.height * (4 - state) / 4;
                Graphics2D clip = (Graphics2D) g.create();
                clip.clipRect(panel.x + shift, panel.y, panel.width, panel.height);
                clip.drawImage(img, panel.x + shift, panel.y - raised, null);
                BufferedImage deco = ornament >= 0 ? art.doorSprite(FIRST_DOOR_ORNAMENT + ornament) : null;
                if (deco != null) {
                    double scale = panel.width / (double) DOOR_PANEL[1].width;
                    int w = (int) Math.round(deco.getWidth() * scale);
                    int h = (int) Math.round(deco.getHeight() * scale);
                    // Full-panel designs cover the door; small ones (grilles, locks) sit in its upper part.
                    int y = deco.getHeight() >= DOOR_PANEL[1].height * 0.7 ? panel.y : panel.y + (int) (12 * scale);
                    clip.drawImage(deco, panel.x + shift + (panel.width - w) / 2, y - raised, w, h, null);
                }
                clip.dispose();
            }
        }
        BufferedImage pillar = art.sprite(DOOR_PILLAR[d]);
        if (pillar != null) {
            paste(g, DOOR_PILLAR[d], panel.x + shift - pillar.getWidth(), DOOR_PILLAR_Y[d], false);
            paste(g, DOOR_PILLAR[d], panel.x + panel.width + shift, DOOR_PILLAR_Y[d], true);
            BufferedImage buttonImg = button ? art.sprite(DOOR_BUTTON) : null;
            if (buttonImg != null) {
                double scale = DOOR_BUTTON_SCALE * panel.width / (double) DOOR_PANEL[1].width;
                int w = Math.max(1, (int) Math.round(buttonImg.getWidth() * scale));
                int h = Math.max(1, (int) Math.round(buttonImg.getHeight() * scale));
                double distance = panel.width / (double) DOOR_PANEL[1].width;
                int px = panel.x + panel.width + shift + (pillar.getWidth() - w) / 2
                        + (int) Math.round(DOOR_BUTTON_RIGHT * distance);
                int py = panel.y + (int) Math.round(panel.height * DOOR_BUTTON_HEIGHT) - h / 2;
                g.drawImage(buttonImg, px, py, w, h, null);
                if (d == 1 && l == 0) {
                    doorButtonHit = new Rectangle(px + VIEWPORT.x, py + VIEWPORT.y, w, h);
                }
            }
        }
        if (DOOR_LINTEL[d] >= 0) {
            paste(g, DOOR_LINTEL[d], DOOR_LINTEL_XY[d][0] + shift, DOOR_LINTEL_XY[d][1], false);
        }
    }

    /** DM's teleporters are a shimmering blue field; drawn as a translucent overlay with fixed sparkles. */
    private void drawTeleporter(Graphics2D g, int d, int l, int mx, int my) {
        Rectangle panel = DOOR_PANEL[d];
        int shift = MID_SPACING[d] * l;
        int pad = panel.width / 6;
        Rectangle field = new Rectangle(panel.x - pad + shift, panel.y, panel.width + 2 * pad, panel.height);
        g.setColor(new java.awt.Color(80, 140, 255, 90));
        g.fillRect(field.x, field.y, field.width, field.height);
        long seed = mx * 31L + my * 17L;
        g.setColor(new java.awt.Color(200, 230, 255, 170));
        for (int i = 0; i < 40; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int px = field.x + (int) (((seed >>> 33) & 0xFFFF) % field.width);
            int py = field.y + (int) (((seed >>> 17) & 0xFFFF) % field.height);
            g.fillRect(px, py, 1, 1);
        }
    }

    private void drawWall(Graphics2D g, int d, int l, boolean flipped) {
        int x = WALL_X[d][l + 2];
        int y = WALL_Y[d][l + 2];
        if (!flipped) {
            g.drawImage(art.sprite(WALL[d][l + 2]), x, y, null);
        } else {
            // The opposite side's piece, mirrored, has this side's silhouette.
            g.drawImage(art.spriteFlipped(WALL[d][-l + 2]), x, y, null);
        }
    }

    private void paste(Graphics2D g, int graphic, int x, int y, boolean flipped) {
        BufferedImage img = flipped ? art.spriteFlipped(graphic) : art.sprite(graphic);
        if (img != null) {
            g.drawImage(img, x, y, null);
        }
    }

    /** The mirror sits centred on the front wall, a little above its middle; smaller with distance. */
    private void drawMirror(Graphics2D g, int d, ChampionMirror mirror) {
        Rectangle face = FRONT[d];
        double scale = face.width / (double) FRONT[1].width;
        int w = (int) Math.round(MIRROR_W * scale);
        int h = (int) Math.round(MIRROR_H * scale);
        int x = face.x + (face.width - w) / 2;
        int y = (int) Math.round(face.y + face.height / 2.0 - h / 2.0 - 6 * scale);
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
