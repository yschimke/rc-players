package ee.schimke.composeai.rcplayer.desktop

import java.net.URI
import java.net.URLDecoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RcInspectorTest {
  /** Decode the link the way the inspector does: the fragment's `url`, then `fetch` of it. */
  @Test
  fun theLinkCarriesTheDocumentAndItsName() {
    val bytes = Random(7).nextBytes(4096)
    val url = assertNotNull(RcInspector.url("Pressure Gauge (1).rc", bytes))
    assertTrue(url.startsWith(RcInspector.URL + "#url="), url.take(120))

    val dataUrl = URLDecoder.decode(url.substringAfter("#url="), Charsets.UTF_8)
    assertTrue(dataUrl.startsWith("data:application/octet-stream;base64,"))
    val (payload, name) = dataUrl.substringAfter("base64,").split("#/")
    assertContentEquals(bytes, Base64.getDecoder().decode(payload))
    // The inspector names the file after the last `/` segment, decoded twice.
    assertEquals("Pressure_Gauge__1_.rc", name)
  }

  @Test
  fun refusesADocumentTooLargeForABrowserUrl() {
    assertNotNull(RcInspector.url("fits.rc", Random(1).nextBytes(1_300_000)))
    assertNull(RcInspector.url("big.rc", Random(2).nextBytes(1_500_000)))
  }

  @Test
  fun theHandoffPageNavigatesToTheLinkAndServesOnce() {
    val target = RcInspector.URL + "#url=data%3Aapplication%2Foctet-stream%3Bbase64%2CAAAA"
    val handoff = RcInspector.serveRedirect(target)
    val client = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).build()
    val request = HttpRequest.newBuilder(URI(handoff.url)).build()

    val page = client.send(request, HttpResponse.BodyHandlers.ofString())
    assertEquals(200, page.statusCode())
    assertTrue(page.body().contains("location.replace(\"$target\")"), page.body())

    handoff.await()
    assertFailsWith<java.io.IOException> {
      client.send(request, HttpResponse.BodyHandlers.ofString())
    }
  }
}
