package org.v.shortly;                                   // same package as Base62, so no import is needed

import org.junit.jupiter.api.Test;                       // marks a method as a test
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;        // assertEquals, assertTrue...

class Base62Test {

    @Test
    void encodesZero() {
        assertEquals("0", Base62.encode(0));             // assertEquals(expected, actual)
    }

    @Test
    void usesDigitsThenUppercaseThenLowercase() {
        assertEquals("9", Base62.encode(9));
        assertEquals("A", Base62.encode(10));            // after the 10 digits comes A
        assertEquals("z", Base62.encode(61));            // the last of the 62 characters
    }

    @Test
    void rollsOverToTwoCharacters() {
        assertEquals("10", Base62.encode(62));           // like 9 -> 10 in decimal
        assertEquals("21", Base62.encode(125));          // 2*62 + 1
    }

    @Test
    void neverProducesTheSameCodeTwice() {
        Set<String> seen = new HashSet<>();              // a Set rejects duplicates: add() returns false
        for (long id = 1; id <= 100_000; id++) {
            assertTrue(seen.add(Base62.encode(id)), "duplicate code for id " + id);
        }
    }

    @Test
    void generatedCodesStayBelowSixCharactersUntil916MillionLinks() {
        // This is why custom aliases must be 6-10 characters: they can never collide with a generated code.
        assertEquals(5, Base62.encode(916_132_831L).length());   // 62^5 - 1
        assertEquals(6, Base62.encode(916_132_832L).length());   // 62^5
    }
}