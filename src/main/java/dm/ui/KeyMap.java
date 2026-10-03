package dm.ui;

import java.awt.event.KeyEvent;

/**
 * Keyboard movement. DM on the PC moves with the numeric keypad, laid out
 * like the arrow panel: 7 turn left, 8 forward, 9 turn right, 4 left,
 * 5 back, 6 right. The arrow keys (up/down move, left/right turn) and
 * W/A/S/D with Q/E to turn work too.
 *
 * With Num Lock off the keypad sends Home, Up, PgUp, Left, Clear and Right,
 * so keys from the keypad are told apart by their location: the keypad's
 * Left is a sidestep while the arrow key's Left is a turn.
 */
public final class KeyMap {

    private KeyMap() {
    }

    /** The movement a key asks for, or null. */
    public static MovementPanel.Action action(int keyCode, int location) {
        if (location == KeyEvent.KEY_LOCATION_NUMPAD) {
            MovementPanel.Action pad = keypad(keyCode);
            if (pad != null) {
                return pad;
            }
        }
        return switch (keyCode) {
            case KeyEvent.VK_NUMPAD7, KeyEvent.VK_LEFT, KeyEvent.VK_Q -> MovementPanel.Action.TURN_LEFT;
            case KeyEvent.VK_NUMPAD8, KeyEvent.VK_UP, KeyEvent.VK_W -> MovementPanel.Action.FORWARD;
            case KeyEvent.VK_NUMPAD9, KeyEvent.VK_RIGHT, KeyEvent.VK_E -> MovementPanel.Action.TURN_RIGHT;
            case KeyEvent.VK_NUMPAD4, KeyEvent.VK_A -> MovementPanel.Action.STRAFE_LEFT;
            case KeyEvent.VK_NUMPAD5, KeyEvent.VK_DOWN, KeyEvent.VK_S -> MovementPanel.Action.BACKWARD;
            case KeyEvent.VK_NUMPAD6, KeyEvent.VK_D -> MovementPanel.Action.STRAFE_RIGHT;
            default -> null;
        };
    }

    /** The keypad with Num Lock off. */
    private static MovementPanel.Action keypad(int keyCode) {
        return switch (keyCode) {
            case KeyEvent.VK_HOME -> MovementPanel.Action.TURN_LEFT;
            case KeyEvent.VK_UP, KeyEvent.VK_KP_UP -> MovementPanel.Action.FORWARD;
            case KeyEvent.VK_PAGE_UP -> MovementPanel.Action.TURN_RIGHT;
            case KeyEvent.VK_LEFT, KeyEvent.VK_KP_LEFT -> MovementPanel.Action.STRAFE_LEFT;
            case KeyEvent.VK_CLEAR, KeyEvent.VK_BEGIN, KeyEvent.VK_DOWN, KeyEvent.VK_KP_DOWN -> MovementPanel.Action.BACKWARD;
            case KeyEvent.VK_RIGHT, KeyEvent.VK_KP_RIGHT -> MovementPanel.Action.STRAFE_RIGHT;
            default -> null;
        };
    }
}
