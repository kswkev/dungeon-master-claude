package dm.ui;

import dm.data.GraphicsFile;
import dm.model.ChampionMirror;
import dm.model.Direction;
import dm.model.DungeonMap;
import dm.model.Party;
import dm.model.Square;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

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

    public TexturedViewRenderer(Art art) {
        this.art = art;
    }

    @Override
    public Rectangle portraitHit() {
        return portraitHit;
    }

    @Override
    public void draw(Graphics2D screen, Party party) {
        portraitHit = null;
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
                        }
                        if (l == 0 && d > 0) {
                            ChampionMirror mirror = map.mirrorAt(mx, my, fwd.opposite());
                            if (mirror != null) {
                                drawMirror(g, d, mirror);
                            }
                        }
                    } else if (Math.abs(l) <= 1) {
                        drawFeature(g, sq, d, l, map.doorStyle(mx, my), fwd, mx, my);
                    }
                }
            }
        } finally {
            g.dispose();
        }
    }

    // ---- open squares: doors, stairs, pits, teleporters -------------------
    //
    // Unlike walls, these pieces don't tile edge to edge, and the PC
    // GRAPHICS.DAT doesn't carry DM's coordinate tables. Their positions are
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

    private static final int[] PIT_C = {-1, 420, 418, 416};
    private static final int[][] PIT_C_XY = {null, {82, 90}, {92, 75}, {99, 63}};
    private static final int[] PIT_L = {-1, 419, 417, 415};
    private static final int[][] PIT_L_XY = {null, {0, 92}, {4, 75}, {40, 63}};

    private static final int[] STAIRS_DOWN_C = {-1, 113, 111, 109};
    private static final int[][] STAIRS_DOWN_C_XY = {null, {32, 19}, {62, 29}, {78, 28}};
    private static final int[] STAIRS_DOWN_L = {-1, 112, 110, 108};
    private static final int[][] STAIRS_DOWN_L_XY = {null, {0, 19}, {-1, 30}, {14, 29}};
    private static final int[] STAIRS_UP_C = {-1, 120, 118, 116};
    private static final int[][] STAIRS_UP_C_XY = {null, {36, 27}, {63, 31}, {75, 25}};
    private static final int[] STAIRS_UP_L = {-1, 119, 117, 115};
    private static final int[][] STAIRS_UP_L_XY = {null, {0, 28}, {-2, 30}, {2, 33}};

    private void drawFeature(Graphics2D g, Square sq, int d, int l, int doorStyle, Direction fwd, int mx, int my) {
        switch (sq.type()) {
            case DOOR -> {
                if (sq.facesAlong(fwd)) {
                    drawDoor(g, sq, d, l, doorStyle);
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
                if (d > 0 && sq.pitOpen()) {
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

    /** Door frame pillars and lintel, plus the panel unless the door is open or broken. */
    private void drawDoor(Graphics2D g, Square sq, int d, int l, int style) {
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
        int state = sq.doorState();
        if (state >= 1 && state <= 4) {
            // DM doors slide up into the lintel; states 1-3 are part-way, 4 is shut.
            BufferedImage img = art.sprite(FIRST_DOOR + style * 3 + (MAX_DEPTH - d));
            if (img != null) {
                int raised = panel.height * (4 - state) / 4;
                Graphics2D clip = (Graphics2D) g.create();
                clip.clipRect(panel.x + shift, panel.y, panel.width, panel.height);
                clip.drawImage(img, panel.x + shift, panel.y - raised, null);
                clip.dispose();
            }
        }
        BufferedImage pillar = art.sprite(DOOR_PILLAR[d]);
        if (pillar != null) {
            paste(g, DOOR_PILLAR[d], panel.x + shift - pillar.getWidth(), DOOR_PILLAR_Y[d], false);
            paste(g, DOOR_PILLAR[d], panel.x + panel.width + shift, DOOR_PILLAR_Y[d], true);
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
