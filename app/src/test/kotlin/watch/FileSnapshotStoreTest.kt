package watch

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class FileSnapshotStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun roundTripPreservesHebrewTitles() {
        val store = FileSnapshotStore(tmp.newFile("snap.json"))
        val snap = mapOf("a" to Event("a", "נועם צוריאלי – הופעה", "https://www.eventer.co.il/אירוע", true))
        store.save(snap)
        assertEquals(snap, FileSnapshotStore(java.io.File(tmp.root, "snap.json")).load())
    }

    @Test
    fun missingFileLoadsNull() {
        assertNull(FileSnapshotStore(java.io.File(tmp.root, "nope.json")).load())
    }

    @Test
    fun corruptFileLoadsNull() {
        val f = tmp.newFile("bad.json").apply { writeText("{not json") }
        assertNull(FileSnapshotStore(f).load())
    }

    @Test
    fun saveLeavesNoTempFile() {
        val f = java.io.File(tmp.root, "snap.json")
        FileSnapshotStore(f).save(mapOf("a" to Event("a", "t", "u", false)))
        assertEquals(listOf("snap.json"), tmp.root.list()!!.toList())
    }

    @Test
    fun non200Throws() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(429))
            server.start()
            val f = OkHttpFeedFetcher(OkHttpClient(), server.url("/sliders/list.js").toString())
            assertThrows(IOException::class.java) { runBlocking { f.fetch() } }
        }
    }

    @Test
    fun sendsUserAgentAndReturnsBody() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("[1]"))
            server.start()
            val f = OkHttpFeedFetcher(OkHttpClient(), server.url("/x").toString())
            assertEquals("[1]", runBlocking { f.fetch() })
            assertEquals("EventerWatch/1.0", server.takeRequest().getHeader("User-Agent"))
        }
    }
}
