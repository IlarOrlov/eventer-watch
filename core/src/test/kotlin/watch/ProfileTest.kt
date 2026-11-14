package watch

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ProfileTest {
    // Synthetic data only. 123456782 is a well-known checksum-valid test ID.
    private val ok = Profile(
        fullName = "Dana Levi", idNumber = "123456782", phone = "+972501234567",
        email = "dana@example.com", gender = Gender.FEMALE, birthDate = "05-04-1986", age = 40,
        city = "Haifa", facebookUrl = "https://facebook.com/x", instagramUrl = "https://instagram.com/x",
        smoker = false, ticketCount = 2,
    )

    @Test fun validProfileHasNoProblems() = assertEquals(emptyList<String>(), ok.problems())

    @Test fun localPhoneConvertsInternationalPrefix() {
        assertEquals("0501234567", ok.localPhone())
        assertEquals("0501234567", ok.copy(phone = "050-123-4567").localPhone())
        assertEquals("0501234567", ok.copy(phone = "972501234567").localPhone())
    }

    @Test fun idChecksum() {
        assertTrue(IsraeliId.isValid("123456782"))
        assertTrue(IsraeliId.isValid("10000008"), "short IDs are zero padded")
        assertFalse(IsraeliId.isValid("123456789"))
        assertFalse(IsraeliId.isValid("12345"))
        assertFalse(IsraeliId.isValid("abcdefghi"))
    }

    @Test fun badIdIsReported() =
        assertTrue(ok.copy(idNumber = "123456789").problems().any { it.contains("ID") })

    @Test fun missingGenderIsReported() =
        assertTrue(ok.copy(gender = null).problems().any { it.contains("Gender") })

    @Test fun badPhoneEmailAndNameAreReported() {
        val p = ok.copy(phone = "12", email = "nope", fullName = "Dana").problems()
        assertTrue(p.any { it.contains("Phone") })
        assertTrue(p.any { it.contains("Email") })
        assertTrue(p.any { it.contains("name") })
    }

    @Test fun birthDateMustBeDdMmYyyyWhenPresent() {
        assertTrue(ok.copy(birthDate = "1986-04-05").problems().any { it.contains("Birth") })
        assertTrue(ok.copy(birthDate = "31-02-1986").problems().any { it.contains("Birth") })
        assertEquals(emptyList<String>(), ok.copy(birthDate = "").problems())
    }

    @Test fun ticketCountMustBeOneToTen() {
        assertTrue(ok.copy(ticketCount = 0).problems().any { it.contains("Tickets") })
        assertTrue(ok.copy(ticketCount = 11).problems().any { it.contains("Tickets") })
    }
}
