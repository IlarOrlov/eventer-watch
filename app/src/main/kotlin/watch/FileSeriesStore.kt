package watch

import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

class FileSeriesStore(private val file: File) : SeriesStore {
    private val serializer = SetSerializer(String.serializer())

    override fun load(): Set<String>? = try {
        if (!file.exists()) null else Json.decodeFromString(serializer, file.readText())
    } catch (e: Exception) {
        null
    }

    override fun save(codes: Set<String>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(Json.encodeToString(serializer, codes))
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "could not replace series list" }
        }
    }
}
