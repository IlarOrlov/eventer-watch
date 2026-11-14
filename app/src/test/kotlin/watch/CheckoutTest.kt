package watch

import android.content.Context
import android.content.Intent
import android.widget.Button
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CheckoutTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val profile = Profile(
        fullName = "Dana \"D\" Levi</script>", idNumber = "123456782", phone = "0501234567",
        email = "dana@example.com", gender = Gender.FEMALE, city = "Haifa", smoker = false, ticketCount = 2,
    )

    @After fun reset() { SecretBox.override = null }

    private fun saveProfile() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        SecretBox.override = SecretBox { key }
        ProfileStore.forContext(context).save(profile)
    }

    @Test fun scriptEmbedsTheProfileAsValidJson() {
        val js = CheckoutScript.build("/* engine */", profile)
        val arg = js.substringAfter("__ew.start(").substringBeforeLast(", {})")
        assertEquals(profile, Json.decodeFromString(Profile.serializer(), arg))
        assertTrue(js.startsWith("/* engine */"))
    }

    @Test fun onlyEventerHttpsUrlsAreAccepted() {
        assertTrue(CheckoutActivity.isAllowed("https://www.eventer.co.il/mesibatapijama"))
        assertFalse(CheckoutActivity.isAllowed("http://www.eventer.co.il/x"))
        assertFalse(CheckoutActivity.isAllowed("https://evil.example.com/www.eventer.co.il"))
        assertFalse(CheckoutActivity.isAllowed("javascript:alert(1)"))
    }

    @Test fun checkoutScreenLoadsTheEventPage() {
        saveProfile()
        val intent = Intent(context, CheckoutActivity::class.java).putExtra(CheckoutActivity.EXTRA_URL, "https://www.eventer.co.il/mesibatapijama")
        val a = Robolectric.buildActivity(CheckoutActivity::class.java, intent).setup().get()
        assertEquals("https://www.eventer.co.il/mesibatapijama", shadowOf(a.webView).lastLoadedUrl)
    }

    private fun openCheckout(): CheckoutActivity {
        saveProfile()
        val intent = Intent(context, CheckoutActivity::class.java).putExtra(CheckoutActivity.EXTRA_URL, "https://www.eventer.co.il/mesibatapijama")
        return Robolectric.buildActivity(CheckoutActivity::class.java, intent).setup().get()
    }

    @Test fun buyerDetailsAreNeverInjectedIntoForeignPages() {
        val a = openCheckout()
        a.webView.webViewClient.onPageFinished(a.webView, "https://evil.example.com/www.eventer.co.il")
        val js = shadowOf(a.webView).lastEvaluatedJavascript.orEmpty()
        assertFalse(js, js.contains("__ew.start"))
    }

    @Test fun buyerDetailsAreInjectedIntoEventerPages() {
        val a = openCheckout()
        a.webView.webViewClient.onPageFinished(a.webView, "https://www.eventer.co.il/mesibatapijama")
        assertTrue(shadowOf(a.webView).lastEvaluatedJavascript.orEmpty().contains("__ew.start"))
    }

    @Test fun leavingForThePaymentProviderRingsForThePerson() {
        val a = openCheckout()
        a.webView.webViewClient.onPageFinished(a.webView, "https://payments.payplus.co.il/pay/abc")
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        assertTrue(shadowOf(nm).allNotifications.any { it.channelId == Alarm.CHANNEL_CHECKOUT })
        assertFalse(shadowOf(a.webView).lastEvaluatedJavascript.orEmpty().contains("__ew.start"))
    }

    @Test fun checkoutScreenRefusesForeignUrls() {
        val intent = Intent(context, CheckoutActivity::class.java).putExtra(CheckoutActivity.EXTRA_URL, "https://evil.example.com/")
        val a = Robolectric.buildActivity(CheckoutActivity::class.java, intent).setup().get()
        assertTrue(a.isFinishing)
    }

    @Test fun seriesAlertOpensCheckoutWhenProfileSavedAndOverlayAllowed() {
        saveProfile()
        ShadowSettings.setCanDrawOverlays(true)
        Alarm(context).also { it.ensureChannels() }.raise(listOf(Alert.NewEvent(Event("m", "מסיבתה פיג׳מות", "https://www.eventer.co.il/mesibatapijama4", false))))
        val started = shadowOf(context as android.app.Application).nextStartedActivity
        assertEquals(CheckoutActivity::class.java.name, started.component!!.className)
        assertEquals("https://www.eventer.co.il/mesibatapijama4", started.getStringExtra(CheckoutActivity.EXTRA_URL))
    }

    @Test fun nonSeriesAlertStillOpensTheAlarmScreen() {
        saveProfile()
        ShadowSettings.setCanDrawOverlays(true)
        Alarm(context).also { it.ensureChannels() }.raise(listOf(Alert.NewEvent(Event("x", "Other", "https://www.eventer.co.il/vsh2f", false))))
        val started = shadowOf(context as android.app.Application).nextStartedActivity
        assertEquals(AlarmActivity::class.java.name, started.component!!.className)
    }

    @Test fun alarmScreenOpensCheckoutForSeriesWhenProfileSaved() {
        saveProfile()
        val a = Robolectric.buildActivity(
            AlarmActivity::class.java,
            Intent(context, AlarmActivity::class.java)
                .putExtra(AlarmActivity.EXTRA_URLS, arrayOf("https://www.eventer.co.il/mesibatapijama4"))
                .putExtra(AlarmActivity.EXTRA_TITLES, arrayOf("מסיבתה")),
        ).setup().get()
        a.findViewById<Button>(AlarmActivity.ID_OPEN).performClick()
        val next = shadowOf(a).nextStartedActivity
        assertEquals(CheckoutActivity::class.java.name, next.component!!.className)
    }

    // ---- discovery adapters ----

    private val client = OkHttpClient()

    @Test fun probeReturnsEventOn200() {
        MockWebServer().use { s ->
            s.enqueue(MockResponse().setBody("""{"event":{"_id":"abc123","name":"מסיבתה פיג׳מות","producer":{"privateTerminal":{"secret":"x"}}}}"""))
            s.start()
            val r = runBlocking { OkHttpEventProbe(client, s.url("/").toString().trimEnd('/')).probe("mesibatapijama4") }
            val e = (r as ProbeResult.Exists).event
            assertEquals("abc123", e.id)
            assertEquals("מסיבתה פיג׳מות", e.title)
            assertTrue(e.url.endsWith("/mesibatapijama4"))
            assertEquals("EventerWatch/1.0", s.takeRequest().getHeader("User-Agent"))
        }
    }

    @Test fun probeIsMissingOn403And404() {
        MockWebServer().use { s ->
            s.enqueue(MockResponse().setResponseCode(403)); s.enqueue(MockResponse().setResponseCode(404)); s.start()
            val p = OkHttpEventProbe(client, s.url("/").toString().trimEnd('/'))
            assertEquals(ProbeResult.Missing, runBlocking { p.probe("a") })
            assertEquals(ProbeResult.Missing, runBlocking { p.probe("b") })
        }
    }

    @Test fun probeThrowsOnServerErrors() {
        MockWebServer().use { s ->
            s.enqueue(MockResponse().setResponseCode(500)); s.start()
            assertThrows(java.io.IOException::class.java) {
                runBlocking { OkHttpEventProbe(client, s.url("/").toString().trimEnd('/')).probe("a") }
            }
        }
    }

    @Test fun sitemapSourceReadsTheLastFileOfTheIndex() {
        MockWebServer().use { s ->
            s.start()
            val base = s.url("/").toString().trimEnd('/')
            s.enqueue(MockResponse().setBody("<sitemapindex><sitemap><loc>$base/sitemap_1.xml</loc></sitemap><sitemap><loc>$base/sitemap_2.xml</loc></sitemap></sitemapindex>"))
            s.enqueue(MockResponse().setBody("<urlset><url><loc>https://www.eventer.co.il/mesibatapijama9</loc></url></urlset>"))
            val codes = runBlocking { OkHttpSitemapSource(client, base).latestCodes() }
            assertEquals(listOf("mesibatapijama9"), codes)
            assertEquals("/sitemap.xml", s.takeRequest().path)
            assertEquals("/sitemap_2.xml", s.takeRequest().path)
        }
    }
}
