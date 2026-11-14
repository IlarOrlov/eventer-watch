package watch

import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator

private fun softwareBox(): SecretBox {
    val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    return SecretBox { key }
}

private val sample = Profile(
    fullName = "Dana Levi", idNumber = "123456782", phone = "0501234567", email = "dana@example.com",
    gender = Gender.FEMALE, city = "Haifa", smoker = false, ticketCount = 2,
)

class SecretBoxTest {
    @Test fun roundTrip() {
        val box = softwareBox()
        assertArrayEquals("שלום".toByteArray(), box.decrypt(box.encrypt("שלום".toByteArray())))
    }

    @Test fun eachEncryptionUsesAFreshIv() {
        val box = softwareBox()
        assertFalse(box.encrypt("a".toByteArray()).contentEquals(box.encrypt("a".toByteArray())))
    }

    @Test fun tamperedDataIsRejected() {
        val box = softwareBox()
        val enc = box.encrypt("secret".toByteArray())
        enc[enc.size - 1] = (enc[enc.size - 1].toInt() xor 1).toByte()
        assertNull(box.decrypt(enc))
    }

    @Test fun otherKeyCannotDecrypt() {
        assertNull(softwareBox().decrypt(softwareBox().encrypt("x".toByteArray())))
    }
}

class ProfileStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun roundTripPreservesHebrew() {
        val file = java.io.File(tmp.root, "profile.bin")
        val box = softwareBox()
        val p = sample.copy(fullName = "הלנה עזרא", city = "רמת ישי")
        ProfileStore(file, box).save(p)
        assertEquals(p, ProfileStore(file, box).load())
    }

    @Test fun fileOnDiskContainsNoPlaintext() {
        val file = java.io.File(tmp.root, "profile.bin")
        ProfileStore(file, softwareBox()).save(sample)
        val raw = String(file.readBytes(), Charsets.ISO_8859_1)
        assertFalse(raw.contains("123456782"))
        assertFalse(raw.contains("dana@example.com"))
    }

    @Test fun missingOrCorruptFileLoadsNull() {
        val file = java.io.File(tmp.root, "profile.bin")
        assertNull(ProfileStore(file, softwareBox()).load())
        file.writeText("garbage")
        assertNull(ProfileStore(file, softwareBox()).load())
    }

    @Test fun clearRemovesTheProfile() {
        val file = java.io.File(tmp.root, "profile.bin")
        val s = ProfileStore(file, softwareBox()).apply { save(sample) }
        s.clear()
        assertNull(s.load())
        assertFalse(file.exists())
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileActivityTest {
    private val context: android.content.Context = ApplicationProvider.getApplicationContext()

    private fun open(): ProfileActivity {
        ProfileActivity.boxOverride = softwareBox()
        return Robolectric.buildActivity(ProfileActivity::class.java).setup().get()
    }

    private fun ProfileActivity.type(id: Int, text: String) = findViewById<EditText>(id).setText(text)

    @Test fun savingAValidProfileStoresIt() {
        val a = open()
        a.type(ProfileActivity.ID_NAME, "Dana Levi")
        a.type(ProfileActivity.ID_ID, "123456782")
        a.type(ProfileActivity.ID_PHONE, "+972501234567")
        a.type(ProfileActivity.ID_EMAIL, "dana@example.com")
        a.findViewById<RadioButton>(ProfileActivity.ID_FEMALE).isChecked = true
        a.type(ProfileActivity.ID_TICKETS, "2")
        a.findViewById<Button>(ProfileActivity.ID_SAVE).performClick()
        val saved = ProfileStore.forContext(context).load()
        assertEquals("Dana Levi", saved!!.fullName)
        assertEquals(Gender.FEMALE, saved.gender)
        assertEquals(2, saved.ticketCount)
        assertTrue(a.isFinishing)
    }

    @Test fun birthDateFieldAddsDashesWhileTypingOnANumberKeyboard() {
        val a = open()
        val f = a.findViewById<EditText>(ProfileActivity.ID_BIRTH)
        assertEquals(android.text.InputType.TYPE_CLASS_NUMBER, f.inputType and android.text.InputType.TYPE_MASK_CLASS)
        f.setText("05041986")
        assertEquals("05-04-1986", f.text.toString())
    }

    @Test fun birthDateIsSavedInDdMmYyyy() {
        val a = open()
        a.type(ProfileActivity.ID_NAME, "Dana Levi")
        a.type(ProfileActivity.ID_ID, "123456782")
        a.type(ProfileActivity.ID_PHONE, "0501234567")
        a.type(ProfileActivity.ID_EMAIL, "dana@example.com")
        a.findViewById<RadioButton>(ProfileActivity.ID_FEMALE).isChecked = true
        a.type(ProfileActivity.ID_BIRTH, "5/4/1986")
        a.findViewById<Button>(ProfileActivity.ID_SAVE).performClick()
        assertEquals("05-04-1986", ProfileStore.forContext(context).load()!!.birthDate)
    }

    @Test fun invalidProfileShowsProblemsAndIsNotSaved() {
        val a = open()
        a.type(ProfileActivity.ID_NAME, "Dana")
        a.findViewById<Button>(ProfileActivity.ID_SAVE).performClick()
        assertNull(ProfileStore.forContext(context).load())
        assertFalse(a.isFinishing)
        val shown = a.findViewById<android.widget.TextView>(ProfileActivity.ID_PROBLEMS).text.toString()
        assertTrue(shown.contains("ID number"))
    }
}
