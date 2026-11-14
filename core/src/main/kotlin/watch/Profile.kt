package watch

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

@Serializable
enum class Gender { MALE, FEMALE }

/** Buyer details used to fill the checkout. Stored encrypted on the phone only. */
@Serializable
data class Profile(
    val fullName: String = "",
    val idNumber: String = "",
    val phone: String = "",
    val email: String = "",
    val gender: Gender? = null,
    val birthDate: String = "", // dd-mm-yyyy, optional
    val age: Int? = null,
    val city: String = "",
    val facebookUrl: String = "",
    val instagramUrl: String = "",
    val smoker: Boolean? = null,
    val ticketCount: Int = 1,
) {
    fun localPhone(): String {
        val digits = phone.filter { it.isDigit() }
        return if (digits.startsWith("972")) "0" + digits.removePrefix("972") else digits
    }

    fun problems(): List<String> {
        val p = mutableListOf<String>()
        if (fullName.trim().split(Regex("\\s+")).size < 2) p += "Enter the full name (first and last name)"
        if (!IsraeliId.isValid(idNumber)) p += "ID number is not valid"
        if (!Regex("^0\\d{8,9}$").matches(localPhone())) p += "Phone number is not valid"
        if (!Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(email.trim())) p += "Email is not valid"
        if (gender == null) p += "Gender is required"
        if (birthDate.isNotBlank() && !validDate(birthDate)) p += "Birth date must be dd-mm-yyyy"
        if (ticketCount !in 1..10) p += "Tickets must be between 1 and 10"
        return p
    }

    private fun validDate(s: String) = try {
        LocalDate.parse(s, DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT)); true
    } catch (e: Exception) { false }
}

object IsraeliId {
    fun isValid(raw: String): Boolean {
        val s = raw.trim()
        if (s.length !in 5..9 || !s.all { it.isDigit() }) return false
        val sum = s.padStart(9, '0').mapIndexed { i, c ->
            val v = (c - '0') * (if (i % 2 == 0) 1 else 2)
            if (v > 9) v - 9 else v
        }.sum()
        return sum % 10 == 0
    }
}
