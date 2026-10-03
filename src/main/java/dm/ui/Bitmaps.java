package dm.ui;

import dm.data.IndexedImage;

import java.awt.image.BufferedImage;

/**
 * DM's bitmap operations on palette-indexed pictures: shrinking for distance
 * while swapping colours ("palette changes", ReDMCSB F0129), mirroring, and
 * turning the result into a Java image with a chosen see-through colour.
 */
final class Bitmaps {

    private Bitmaps() {
    }

    /** DM's size for a dimension at a scale in 32nds (F0459): rounded to nearest. */
    static int scaled(int dimension, int scale) {
        return (dimension * scale + scale / 2) / 32;
    }

    /**
     * {@code src} shrunk to {@code w} x {@code h} by picking source pixels,
     * each colour c becoming {@code changes[c] / 10} (DM stores the new
     * colour times ten), or kept when {@code changes} is null.
     */
    static IndexedImage shrink(IndexedImage src, int w, int h, int[] changes) {
        w = Math.max(1, w);
        h = Math.max(1, h);
        byte[] out = new byte[w * h];
        for (int y = 0; y < h; y++) {
            int sy = y * src.height() / h;
            for (int x = 0; x < w; x++) {
                int c = src.pixel(x * src.width() / w, sy);
                out[y * w + x] = (byte) (changes == null ? c : changes[c] / 10);
            }
        }
        return new IndexedImage(w, h, out);
    }

    static IndexedImage flip(IndexedImage src) {
        byte[] out = new byte[src.pixels().length];
        for (int y = 0; y < src.height(); y++) {
            for (int x = 0; x < src.width(); x++) {
                out[y * src.width() + x] = (byte) src.pixel(src.width() - 1 - x, y);
            }
        }
        return new IndexedImage(src.width(), src.height(), out);
    }

    /** The picture in {@code colours} (16 RGB values), with colour {@code transparent} see-through (-1 for none). */
    static BufferedImage toImage(IndexedImage src, int transparent, int[] colours) {
        BufferedImage out = new BufferedImage(src.width(), src.height(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.height(); y++) {
            for (int x = 0; x < src.width(); x++) {
                int c = src.pixel(x, y);
                out.setRGB(x, y, c == transparent ? 0 : 0xFF000000 | colours[c]);
            }
        }
        return out;
    }

    /** DM's 16 dungeon colours as RGB values. */
    static int[] palette() {
        int[] rgb = new int[16];
        for (int i = 0; i < 16; i++) {
            rgb[i] = Art.PALETTE[i].getRGB() & 0xFFFFFF;
        }
        return rgb;
    }

    /** {@code img} mirrored left to right. */
    static BufferedImage flip(BufferedImage img) {
        BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                out.setRGB(img.getWidth() - 1 - x, y, img.getRGB(x, y));
            }
        }
        return out;
    }
}
