package watch

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

/** The buyer profile, encrypted at rest and kept only in the app's private storage. */
class ProfileStore(private val file: File, private val box: SecretBox) {
    fun load(): Profile? = try {
        if (!file.exists()) null
        else box.decrypt(file.readBytes())?.let { Json.decodeFromString(Profile.serializer(), it.toString(Charsets.UTF_8)) }
    } catch (e: Exception) {
        null
    }

    fun save(p: Profile) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(box.encrypt(Json.encodeToString(Profile.serializer(), p).toByteArray(Charsets.UTF_8)))
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "could not store profile" }
        }
    }

    fun clear() {
        file.delete()
    }

    companion object {
        fun forContext(c: Context) = ProfileStore(File(c.filesDir, "profile.bin"), SecretBox.android())
    }
}
