package dm.ui;

import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;

import static dm.ui.MovementPanel.Action.BACKWARD;
import static dm.ui.MovementPanel.Action.FORWARD;
import static dm.ui.MovementPanel.Action.STRAFE_LEFT;
import static dm.ui.MovementPanel.Action.STRAFE_RIGHT;
import static dm.ui.MovementPanel.Action.TURN_LEFT;
import static dm.ui.MovementPanel.Action.TURN_RIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KeyMapTest {

    private static final int PAD = KeyEvent.KEY_LOCATION_NUMPAD;
    private static final int STD = KeyEvent.KEY_LOCATION_STANDARD;

    @Test
    void theNumpadIsLaidOutLikeTheArrowPanel() {
        assertEquals(TURN_LEFT, KeyMap.action(KeyEvent.VK_NUMPAD7, PAD));
        assertEquals(FORWARD, KeyMap.action(KeyEvent.VK_NUMPAD8, PAD));
        assertEquals(TURN_RIGHT, KeyMap.action(KeyEvent.VK_NUMPAD9, PAD));
        assertEquals(STRAFE_LEFT, KeyMap.action(KeyEvent.VK_NUMPAD4, PAD));
        assertEquals(BACKWARD, KeyMap.action(KeyEvent.VK_NUMPAD5, PAD));
        assertEquals(STRAFE_RIGHT, KeyMap.action(KeyEvent.VK_NUMPAD6, PAD));
    }

    @Test
    void theNumpadWorksWithNumLockOff() {
        assertEquals(TURN_LEFT, KeyMap.action(KeyEvent.VK_HOME, PAD));
        assertEquals(FORWARD, KeyMap.action(KeyEvent.VK_UP, PAD));
        assertEquals(TURN_RIGHT, KeyMap.action(KeyEvent.VK_PAGE_UP, PAD));
        assertEquals(STRAFE_LEFT, KeyMap.action(KeyEvent.VK_LEFT, PAD), "keypad 4 sidesteps");
        assertEquals(BACKWARD, KeyMap.action(KeyEvent.VK_CLEAR, PAD));
        assertEquals(STRAFE_RIGHT, KeyMap.action(KeyEvent.VK_RIGHT, PAD));
    }

    @Test
    void arrowKeysMoveAndTurn() {
        assertEquals(FORWARD, KeyMap.action(KeyEvent.VK_UP, STD));
        assertEquals(BACKWARD, KeyMap.action(KeyEvent.VK_DOWN, STD));
        assertEquals(TURN_LEFT, KeyMap.action(KeyEvent.VK_LEFT, STD), "the arrow key turns");
        assertEquals(TURN_RIGHT, KeyMap.action(KeyEvent.VK_RIGHT, STD));
    }

    @Test
    void wasdWithQAndEToTurn() {
        assertEquals(FORWARD, KeyMap.action(KeyEvent.VK_W, STD));
        assertEquals(STRAFE_LEFT, KeyMap.action(KeyEvent.VK_A, STD));
        assertEquals(BACKWARD, KeyMap.action(KeyEvent.VK_S, STD));
        assertEquals(STRAFE_RIGHT, KeyMap.action(KeyEvent.VK_D, STD));
        assertEquals(TURN_LEFT, KeyMap.action(KeyEvent.VK_Q, STD));
        assertEquals(TURN_RIGHT, KeyMap.action(KeyEvent.VK_E, STD));
        assertNull(KeyMap.action(KeyEvent.VK_X, STD));
    }

    @Test
    void theTopRowDigitsEnterSpellSymbols() {
        assertEquals(0, KeyMap.spellSymbol(KeyEvent.VK_1, STD));
        assertEquals(5, KeyMap.spellSymbol(KeyEvent.VK_6, STD));
        assertEquals(-1, KeyMap.spellSymbol(KeyEvent.VK_7, STD));
        assertEquals(-1, KeyMap.spellSymbol(KeyEvent.VK_NUMPAD4, PAD), "the keypad stays movement");
        assertEquals(-1, KeyMap.spellSymbol(KeyEvent.VK_4, PAD));
        assertNull(KeyMap.action(KeyEvent.VK_1, STD), "and the digits don't move");
    }
}
