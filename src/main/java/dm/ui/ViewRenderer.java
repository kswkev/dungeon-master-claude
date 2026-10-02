package dm.ui;

import dm.model.Party;

import java.awt.Graphics2D;
import java.awt.Rectangle;

/** Draws the first-person dungeon view into the viewport. */
public interface ViewRenderer {

    /** Viewport position and size on the 320x200 screen, matching the original. */
    Rectangle VIEWPORT = new Rectangle(0, 33, 224, 136);

    void draw(Graphics2D g, Party party);

    /**
     * Screen rectangle of the champion portrait on the wall straight ahead,
     * from the last {@link #draw}, or null. DM only lets you click a portrait
     * from the adjacent square.
     */
    Rectangle portraitHit();

    /**
     * Screen rectangle of the decoration on the wall straight ahead (a
     * switch, keyhole, alcove...), from the last {@link #draw}, or null.
     * Like portraits, wall decorations can only be used from the adjacent square.
     */
    Rectangle wallHit();

    /** Screen rectangle of the button on the door straight ahead, from the last {@link #draw}, or null. */
    Rectangle doorButtonHit();

    /** The original-art renderer when GRAPHICS.DAT is loaded, otherwise the flat fallback. */
    static ViewRenderer forArt(Art art) {
        return art.available() ? new TexturedViewRenderer(art) : new FlatViewRenderer(art);
    }
}
