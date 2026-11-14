package watch

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

class FileSnapshotStore(private val file: File) : SnapshotStore {
    private val serializer = MapSerializer(String.serializer(), Event.serializer())

    override fun load(): Map<String, Event>? = try {
        if (!file.exists()) null else Json.decodeFromString(serializer, file.readText(Charsets.UTF_8))
    } catch (e: Exception) {
        null
    }

    override fun save(s: Map<String, Event>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(Json.encodeToString(serializer, s), Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "could not replace snapshot" }
        }
    }
}
