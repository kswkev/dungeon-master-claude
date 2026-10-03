package dm.model;

import java.util.List;

/**
 * How much the party sees, ported from ReDMCSB's F337 (light), F338 (torches
 * burning down) and F301 (an Illumulet worn on the neck). The tables are
 * graphics.dat item 562's on the ST; the PC keeps them in its program, so
 * these values come from ScummVM's DM engine and the DM Encyclopaedia.
 */
public final class Light {

    /** G039: the light a light power of 0-15 gives (a torch's power is its charge count). */
    static final int[] POWER_TO_AMOUNT = {0, 5, 12, 24, 33, 40, 46, 51, 59, 68, 76, 82, 89, 94, 97, 100};
    /** G040: the least light for each of DM's six dungeon palettes, brightest first. */
    static final int[] PALETTE_LIGHT = {99, 75, 50, 25, 1, 0};
    /** G029: which of the 4 torch icons (TORCH 4-7) shows a lit torch with 0-15 charges. */
    private static final int[] TORCH_ICON = {0, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3};
    /** Torches lose a charge every 512 game ticks (about 87 seconds). */
    public static final int BURN_PERIOD = 512;
    /** The darkest palette. */
    public static final int DARKEST = 5;

    private static final int TORCH = 2;       // weapon type
    private static final int ILLUMULET = 3;   // junk type

    private Light() {
    }

    public static boolean isTorch(Item item) {
        return item != null && item.category() == Item.Category.WEAPON && item.type() == TORCH;
    }

    static boolean isIllumulet(Item item) {
        return item != null && item.category() == Item.Category.JUNK && item.type() == ILLUMULET;
    }

    /**
     * The icon variant (0-3, icons 4-7) of a torch held in a hand: a lit
     * torch flames less as its charges run down. Elsewhere a torch is unlit
     * (variant 0), as DM puts it out when it leaves a hand.
     */
    public static int litTorchVariant(Item torch) {
        return TORCH_ICON[Math.max(0, Math.min(torch.charges(), 15))];
    }

    /**
     * F337: the torches in the champions' hands, the brightest four first
     * (each worth half the one before) and one more, plus magical light.
     * {@code hands} lists each champion's action hand then ready hand, champion
     * by champion, as DM scans them; null or non-torch entries give nothing.
     */
    static int amount(List<Item> hands, int magical) {
        int[] powers = new int[8];
        for (int i = 0; i < powers.length && i < hands.size(); i++) {
            Item item = hands.get(i);
            powers[i] = isTorch(item) ? item.charges() : 0;
        }
        // DM's partial sort: the four highest first, in decreasing order; the rest unsorted.
        for (int i = 0; i != 4; i++) {
            for (int j = i + 1; j < 8; j++) {
                if (powers[j] > powers[i]) {
                    int t = powers[j];
                    powers[j] = powers[i];
                    powers[i] = t;
                }
            }
        }
        int multiplier = 6;
        int total = 0;
        for (int i = 0; i < 5; i++) {
            if (powers[i] != 0) {
                total += (POWER_TO_AMOUNT[powers[i]] << multiplier) >> 6;
                multiplier = Math.max(0, multiplier - 1);
            }
        }
        return total + magical;
    }

    /** F337: the palette (0 brightest .. 5 darkest) for a total amount of light. */
    static int palette(int amount) {
        if (amount <= 0) {
            return DARKEST;
        }
        int index = 0;
        while (PALETTE_LIGHT[index] > amount) {
            index++;
        }
        return index;
    }

    /** The magical light an Illumulet worn on the neck gives (light power 2). */
    static int illumulet() {
        return POWER_TO_AMOUNT[2];
    }
}
