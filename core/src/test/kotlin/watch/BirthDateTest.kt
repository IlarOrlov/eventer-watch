package watch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BirthDateTest {
    @Test fun typingInsertsDashesProgressively() {
        assertEquals("", BirthDate.typing(""))
        assertEquals("0", BirthDate.typing("0"))
        assertEquals("05", BirthDate.typing("05"))
        assertEquals("05-0", BirthDate.typing("050"))
        assertEquals("05-04", BirthDate.typing("0504"))
        assertEquals("05-04-1", BirthDate.typing("05041"))
        assertEquals("05-04-1986", BirthDate.typing("05041986"))
    }

    @Test fun typingIgnoresNonDigitsAndExtraDigits() {
        assertEquals("05-04-1986", BirthDate.typing("05-04-1986"))
        assertEquals("05-04-1986", BirthDate.typing("0504198612345"))
        assertEquals("05-04", BirthDate.typing("05-04-"))
    }

    @Test fun deletingBackPastADashDropsIt() {
        assertEquals("05", BirthDate.typing("05-"))
    }

    @Test fun normalizeAcceptsOtherSeparatorsAndShortParts() {
        assertEquals("05-04-1986", BirthDate.normalize("05/04/1986"))
        assertEquals("05-04-1986", BirthDate.normalize("5.4.1986"))
        assertEquals("05-04-1986", BirthDate.normalize("05041986"))
        assertEquals("05-04-1986", BirthDate.normalize(" 5-4-1986 "))
    }

    @Test fun normalizeLeavesUnrecognisedTextAlone() {
        assertEquals("", BirthDate.normalize(""))
        assertEquals("1986-04-05x", BirthDate.normalize("1986-04-05x"))
    }
}
