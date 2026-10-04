package dm.ui;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** DM's message area: messages on new rows at the bottom, scrolling, wrapping and expiring. */
class MessageAreaTest {

    @Test
    void messagesScrollUpFromTheBottomRow() {
        MessageArea area = new MessageArea();
        area.print("FIRST", Color.WHITE, 0);
        assertEquals("FIRST", area.rowText(3));
        area.print("SECOND", Color.WHITE, 0);
        assertEquals("FIRST", area.rowText(2));
        assertEquals("SECOND", area.rowText(3));
    }

    @Test
    void longMessagesWrapIndented() {
        MessageArea area = new MessageArea();
        area.print("HALK THE BARBARIAN JUST GAINED A FIGHTER LEVEL! AND THEN SOME MORE WORDS HERE", Color.WHITE, 0);
        assertEquals("HALK THE BARBARIAN JUST GAINED A FIGHTER LEVEL! AND", area.rowText(2));
        assertEquals("  THEN SOME MORE WORDS HERE", area.rowText(3));
    }

    @Test
    void rowsClearAfter200Ticks() {
        MessageArea area = new MessageArea();
        area.print("HELLO", Color.WHITE, 10);
        area.clearExpired(209);
        assertTrue(area.hasText());
        area.clearExpired(210);
        assertFalse(area.hasText());
        assertEquals("", area.rowText(3));
    }
}
