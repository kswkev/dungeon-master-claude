package dm.data;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextDecoderTest {

    /** Packs A-Z, space, '.', and '\n' into DM's 3-codes-per-word text, ending with code 31. */
    static int[] encode(String s) {
        List<Integer> codes = new ArrayList<>();
        for (char c : s.toCharArray()) {
            codes.add(c == ' ' ? 26 : c == '.' ? 27 : c == '\n' ? 28 : c - 'A');
        }
        codes.add(31);
        while (codes.size() % 3 != 0) {
            codes.add(31);
        }
        int[] words = new int[codes.size() / 3];
        for (int i = 0; i < words.length; i++) {
            words[i] = (codes.get(i * 3) << 10) | (codes.get(i * 3 + 1) << 5) | codes.get(i * 3 + 2);
        }
        return words;
    }

    @Test
    void decodesLettersSpacesAndLineBreaks() {
        String text = "HALL OF\nCHAMPIONS.";
        assertEquals(text, TextDecoder.decode(encode(text), 0));
    }

    @Test
    void stopsAtEndCode() {
        int[] words = {(7 << 10) | (8 << 5) | 31, (0 << 10) | (0 << 5) | 0};
        assertEquals("HI", TextDecoder.decode(words, 0));
    }

    @Test
    void expandsCommonStringEscape() {
        // "HAWK|" then escape 30 + code 2 ("THE ") then "FEARLESS"
        String tail = "FEARLESS";
        List<Integer> codes = new ArrayList<>(List.of(7, 0, 22, 10, 28, 30, 2));
        for (char c : tail.toCharArray()) {
            codes.add(c - 'A');
        }
        codes.add(31);
        while (codes.size() % 3 != 0) {
            codes.add(31);
        }
        int[] words = new int[codes.size() / 3];
        for (int i = 0; i < words.length; i++) {
            words[i] = (codes.get(i * 3) << 10) | (codes.get(i * 3 + 1) << 5) | codes.get(i * 3 + 2);
        }
        assertEquals("HAWK\nTHE FEARLESS", TextDecoder.decode(words, 0));
    }

    @Test
    void decodesFromOffset() {
        int[] first = encode("AB");
        int[] second = encode("CD");
        int[] all = new int[first.length + second.length];
        System.arraycopy(first, 0, all, 0, first.length);
        System.arraycopy(second, 0, all, first.length, second.length);
        assertEquals("CD", TextDecoder.decode(all, first.length));
    }
}
