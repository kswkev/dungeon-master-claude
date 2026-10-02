package dm.data;

/** A decoded GRAPHICS.DAT image: one 4-bit palette index per pixel, row-major. */
public record IndexedImage(int width, int height, byte[] pixels) {

    public int pixel(int x, int y) {
        return pixels[y * width + x];
    }
}
