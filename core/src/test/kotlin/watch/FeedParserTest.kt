package watch

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FeedParserTest {
    private fun res(name: String) =
        FeedParserTest::class.java.getResource("/$name")!!.readText()

    private fun slide(ref: String, title: String, url: String, visible: Boolean = true) =
        """{"slideType":5,"objectRef":"$ref","title":"$title","url":"$url","isVisible":$visible}"""

    private fun feed(vararg slides: String) =
        """[{"sliderType":5,"slides":[${slides.joinToString(",")}]}]"""

    @Test
    fun parsesSampleFeed() {
        val r = FeedParser.parse(res("feed-sample.json"))
        assertTrue(r is ParseResult.Ok)
        val events = (r as ParseResult.Ok).events
        assertEquals(64, events.size)
        val first = events.first()
        assertEquals("6ab51e3c9b753c2507ea46f5", first.id)
        assertEquals("נועם צוריאלי", first.title)
        assertEquals("https://www.eventer.co.il/vsh2f", first.url)
        assertTrue(first.onSale)
    }

    @Test
    fun emptyListIsShapeChanged() {
        assertEquals(ParseResult.ShapeChanged("empty"), FeedParser.parse(res("feed-empty.json")))
    }

    @Test
    fun cloudflareChallengeIsShapeChanged() {
        assertTrue(FeedParser.parse(res("feed-cloudflare-challenge.html")) is ParseResult.ShapeChanged)
    }

    @Test
    fun garbageIsShapeChanged() {
        assertTrue(FeedParser.parse("{\"x\":1}") is ParseResult.ShapeChanged)
        assertTrue(FeedParser.parse("") is ParseResult.ShapeChanged)
    }

    @Test
    fun hebrewTitleAndNonAsciiUrlSurvive() {
        val r = FeedParser.parse(feed(slide("a1", "הופעה שלום", "/אירוע-1"))) as ParseResult.Ok
        assertEquals("הופעה שלום", r.events.single().title)
        assertEquals("https://www.eventer.co.il/אירוע-1", r.events.single().url)
    }

    @Test
    fun duplicateIdsKeepFirst() {
        val r = FeedParser.parse(feed(slide("a1", "first", "/x"), slide("a1", "second", "/y"))) as ParseResult.Ok
        assertEquals(1, r.events.size)
        assertEquals("first", r.events.single().title)
    }
}
