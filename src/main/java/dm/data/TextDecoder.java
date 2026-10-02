package dm.data;

/**
 * Decodes DUNGEON.DAT text: three 5-bit codes per word (bits 14-10, 9-5, 4-0).
 * <pre>
 *   0-25  A-Z        26  space     27  '.'
 *   28    line break ('\n')
 *   29    escape: next code is an inscription glyph (not needed yet, skipped)
 *   30    escape: next code picks a common string ("THE ", "YOU "...)
 *   31    end of text
 * </pre>
 */
public final class TextDecoder {

    private static final String[] ESCAPE_30 = {"?", "!", "THE ", "YOU "};
    private static final int MAX_WORDS = 512;

    private TextDecoder() {
    }

    public static String decode(int[] words, int offset) {
        StringBuilder sb = new StringBuilder();
        int escape = 0;
        for (int i = offset; i < words.length && i < offset + MAX_WORDS; i++) {
            for (int shift = 10; shift >= 0; shift -= 5) {
                int code = (words[i] >>> shift) & 31;
                if (escape != 0) {
                    if (escape == 30 && code < ESCAPE_30.length) {
                        sb.append(ESCAPE_30[code]);
                    }
                    escape = 0;
                } else if (code < 26) {
                    sb.append((char) ('A' + code));
                } else if (code == 26) {
                    sb.append(' ');
                } else if (code == 27) {
                    sb.append('.');
                } else if (code == 28) {
                    sb.append('\n');
                } else if (code == 31) {
                    return sb.toString();
                } else {
                    escape = code;
                }
            }
        }
        return sb.toString();
    }
}
