package dm.ui;

import dm.data.IndexedImage;
import dm.model.CreatureType;
import dm.model.DungeonMap;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Creature pictures and DM's rules for them (ReDMCSB F0115's creature block,
 * ScummVM's DM engine for the tables):
 * <ul>
 *   <li>the PC's first creature graphic is 584; each type's front, side,
 *       back and attack pictures follow in that order, where it has them;</li>
 *   <li>at D2 and D3 pictures shrink to 20/32 and 16/32 with DM's creature
 *       palette changes (G0221, G0222);</li>
 *   <li>a map's creature types replace palette colours 9 and 10 with their
 *       own colour sets (G0220), at every light level.</li>
 * </ul>
 */
final class CreatureArt {

    static final int FIRST_CREATURE = 584;

    /** G0224: creature points (x, bottom y) by coordinate set, view square, and cell 0-4. */
    static final int[][][][] COORDINATES = {
            {
                    {{95, 70}, {127, 70}, {129, 75}, {93, 75}, {111, 72}},      // D3C
                    {{131, 70}, {163, 70}, {158, 75}, {120, 75}, {145, 72}},    // D3L
                    {{59, 70}, {91, 70}, {107, 75}, {66, 75}, {79, 72}},        // D3R
                    {{92, 81}, {131, 81}, {132, 90}, {91, 90}, {111, 85}},      // D2C
                    {{99, 81}, {146, 81}, {135, 90}, {80, 90}, {120, 85}},      // D2L
                    {{77, 81}, {124, 81}, {143, 90}, {89, 90}, {105, 85}},      // D2R
                    {{83, 103}, {141, 103}, {148, 119}, {76, 119}, {109, 111}}, // D1C
                    {{46, 103}, {118, 103}, {101, 119}, {0, 0}, {79, 111}},     // D1L
                    {{107, 103}, {177, 103}, {0, 0}, {123, 119}, {144, 111}},   // D1R
                    {{0, 0}, {67, 135}, {0, 0}, {0, 0}, {0, 0}},                // D0L
                    {{156, 135}, {0, 0}, {0, 0}, {0, 0}, {0, 0}}                // D0R
            },
            {
                    {{94, 75}, {128, 75}, {111, 70}, {111, 72}, {111, 75}},
                    {{120, 75}, {158, 75}, {149, 70}, {145, 72}, {150, 75}},
                    {{66, 75}, {104, 75}, {75, 70}, {79, 72}, {73, 75}},
                    {{91, 90}, {132, 90}, {111, 83}, {111, 85}, {111, 90}},
                    {{80, 90}, {135, 90}, {125, 83}, {120, 85}, {125, 90}},
                    {{89, 90}, {143, 90}, {99, 83}, {105, 85}, {98, 90}},
                    {{81, 119}, {142, 119}, {111, 105}, {111, 111}, {111, 119}},
                    {{0, 0}, {101, 119}, {84, 105}, {70, 111}, {77, 119}},
                    {{123, 119}, {0, 0}, {139, 105}, {153, 111}, {146, 119}},
                    {{0, 0}, {83, 130}, {57, 121}, {47, 126}, {57, 130}},
                    {{140, 130}, {0, 0}, {166, 121}, {176, 126}, {166, 130}}
            },
            {
                    {{95, 59}, {127, 59}, {129, 61}, {93, 61}, {111, 60}},
                    {{131, 59}, {163, 59}, {158, 61}, {120, 61}, {145, 60}},
                    {{59, 59}, {91, 59}, {107, 61}, {66, 61}, {79, 60}},
                    {{92, 65}, {131, 65}, {132, 67}, {91, 67}, {111, 66}},
                    {{99, 65}, {146, 65}, {135, 67}, {80, 67}, {120, 66}},
                    {{77, 65}, {124, 65}, {143, 67}, {89, 67}, {105, 66}},
                    {{83, 79}, {141, 79}, {148, 85}, {76, 85}, {111, 81}},
                    {{46, 79}, {118, 79}, {101, 85}, {0, 0}, {79, 81}},
                    {{107, 79}, {177, 79}, {0, 0}, {123, 85}, {144, 81}},
                    {{0, 0}, {67, 96}, {0, 0}, {0, 0}, {0, 0}},
                    {{156, 96}, {0, 0}, {0, 0}, {0, 0}, {0, 0}}
            }};

    /** G0221/G0222: colour changes for creatures at D3 and D2 (new colour x 10). */
    private static final int[] PAL_CHANGES_D3 = {0, 120, 10, 30, 40, 30, 0, 60, 30, 0, 0, 110, 0, 20, 0, 130};
    private static final int[] PAL_CHANGES_D2 = {0, 10, 20, 30, 40, 30, 60, 70, 50, 0, 0, 110, 120, 130, 140, 150};

    /**
     * G0220: the 13 replacement colour sets, each a 12-bit colour for the six
     * light levels (ScummVM's PC/Amiga values), then the colour that replaces
     * it at D2 and at D3 (x 10).
     */
    private static final int[][] REPLACEMENT_SETS = {
            {0x0CA0, 0x0A80, 0x0860, 0x0640, 0x0420, 0x0200, 90, 90},
            {0x0060, 0x0040, 0x0020, 0x0000, 0x0000, 0x0000, 0, 0},
            {0x0860, 0x0640, 0x0420, 0x0200, 0x0000, 0x0000, 100, 100},
            {0x0640, 0x0420, 0x0200, 0x0000, 0x0000, 0x0000, 90, 0},
            {0x000A, 0x0008, 0x0006, 0x0004, 0x0002, 0x0000, 90, 100},
            {0x0008, 0x0006, 0x0004, 0x0002, 0x0000, 0x0000, 100, 0},
            {0x0808, 0x0606, 0x0404, 0x0202, 0x0000, 0x0000, 90, 0},
            {0x0A0A, 0x0808, 0x0606, 0x0404, 0x0202, 0x0000, 100, 90},
            {0x0FA0, 0x0C80, 0x0A60, 0x0840, 0x0620, 0x0400, 100, 50},
            {0x0F80, 0x0C60, 0x0A40, 0x0820, 0x0600, 0x0200, 50, 70},
            {0x0800, 0x0600, 0x0400, 0x0200, 0x0000, 0x0000, 100, 120},
            {0x0600, 0x0400, 0x0200, 0x0000, 0x0000, 0x0000, 120, 0},
            {0x0C86, 0x0A64, 0x0842, 0x0620, 0x0400, 0x0200, 100, 50}};

    private final Art art;
    private final Map<String, BufferedImage> cache = new HashMap<>();

    CreatureArt(Art art) {
        this.art = art;
    }

    /**
     * A map's replacement sets for colours 9 and 10 (index 0 and 1; -1 for
     * none), from its creature list: each type may name one for either colour,
     * and later types win, as in DM's loop over the list.
     */
    static int[] replacementSets(List<CreatureType> types) {
        int[] sets = {-1, -1};
        for (CreatureType t : types) {
            if (t.replacementSet9() > 0) {
                sets[0] = t.replacementSet9() - 1;
            }
            if (t.replacementSet10() > 0) {
                sets[1] = t.replacementSet10() - 1;
            }
        }
        return sets;
    }

    /** The six light levels' RGB colours of replacement set {@code set}, for {@link Darkness}. */
    static int[] levels(int set) {
        int[] out = new int[6];
        for (int i = 0; i < 6; i++) {
            int c = REPLACEMENT_SETS[set][i];
            out[i] = (((c >> 8) & 15) * 17 << 16) | (((c >> 4) & 15) * 17 << 8) | (c & 15) * 17;
        }
        return out;
    }

    /**
     * The picture of {@code view} of {@code type} at depth {@code d} (1-3),
     * mirrored if asked, with the type's own colour see-through; null if the
     * type has no such view or GRAPHICS.DAT is missing.
     */
    BufferedImage picture(CreatureType type, CreatureType.View view, int d, boolean flip, DungeonMap map) {
        int offset = type.graphicOffset(view);
        if (offset < 0) {
            return null;
        }
        int[] sets = replacementSets(map.creatureTypes());
        String key = type + ":" + view + ":" + d + ":" + flip + ":" + sets[0] + ":" + sets[1];
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        IndexedImage src = art.indexed(FIRST_CREATURE + type.firstGraphic() + offset);
        BufferedImage out = null;
        if (src != null) {
            int transparent = type.transparentColour();
            IndexedImage img = src;
            if (d > 1) {
                int[] changes = (d == 2 ? PAL_CHANGES_D2 : PAL_CHANGES_D3).clone();
                for (int c = 0; c < 2; c++) {
                    if (sets[c] >= 0) {
                        changes[9 + c] = REPLACEMENT_SETS[sets[c]][d == 2 ? 6 : 7];
                    }
                }
                int scale = d == 2 ? 20 : 16;
                img = Bitmaps.shrink(src, Bitmaps.scaled(src.width(), scale), Bitmaps.scaled(src.height(), scale),
                        changes);
                transparent = changes[transparent] / 10;
            }
            if (flip) {
                img = Bitmaps.flip(img);
            }
            out = Bitmaps.toImage(img, transparent, Bitmaps.palette());
        }
        cache.put(key, out);
        return out;
    }
}
