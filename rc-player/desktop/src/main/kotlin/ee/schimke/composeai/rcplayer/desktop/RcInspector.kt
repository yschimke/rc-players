package ee.schimke.composeai.rcplayer.desktop

import com.sun.net.httpserver.HttpServer
import java.awt.Desktop
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLEncoder
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Hands a document to the Remote Compose binary inspector, the web tool from
 * `camaelon/remotecompose-experiments` (the upstream of `third_party/remote-compose-player`).
 *
 * The inspector loads whatever its `#url=` fragment names with the browser's `fetch`, and `fetch`
 * reads `data:` URLs, so the document travels inside the link itself: no upload, and the bytes
 * never reach any host — a fragment is not sent with the page request. The data URL carries a
 * `#/<name>` fragment of its own, which `fetch` ignores and the inspector reads as the file name,
 * the last `/` segment of what it fetched.
 */
internal object RcInspector {
  const val URL: String = "https://camaelon.github.io/remotecompose-experiments/inspector/"

  /**
   * Chromium refuses URLs past 2 MB (2,097,152 characters); stay under that with room to spare.
   * Base64 and percent-encoding its `+` and `/` grow a document by about 1.4×, so this admits
   * documents up to roughly 1.4 MB.
   */
  const val MAX_URL_CHARS: Int = 2_000_000

  /**
   * Linux caps one command-line argument at 128 KiB (`MAX_ARG_STRLEN`), and every way of opening a
   * browser ends in a process launched with the URL as an argument. A longer link is opened through
   * a one-shot loopback page that navigates to it from inside the browser, where only
   * [MAX_URL_CHARS] applies.
   */
  const val MAX_DIRECT_URL_CHARS: Int = 120_000

  /** The inspector link for [bytes], or null if it would be too long for a browser to open. */
  fun url(name: String, bytes: ByteArray): String? {
    val dataUrl =
      "data:application/octet-stream;base64," +
        Base64.getEncoder().encodeToString(bytes) +
        "#/" +
        displayName(name)
    // `URLEncoder` writes a space as `+`, but neither a data URL nor the name below has one.
    val url = URL + "#url=" + URLEncoder.encode(dataUrl, Charsets.UTF_8)
    return url.takeIf { it.length <= MAX_URL_CHARS }
  }

  /**
   * The inspector percent-decodes the fragment twice, so a name is kept to characters that mean the
   * same thing after any number of decodes.
   */
  private fun displayName(name: String): String =
    name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "document.rc" }

  sealed interface Result {
    /** [handoff] is the loopback page still waiting for the browser, if a long link needed one. */
    class Opened(val handoff: Handoff?) : Result

    class Failed(val message: String) : Result
  }

  /** Opens [file] in the inspector in the default browser. */
  fun open(file: File): Result {
    val bytes =
      try {
        file.readBytes()
      } catch (e: Exception) {
        return Result.Failed("Could not read ${file.path}: ${e.message ?: e}")
      }
    val url =
      url(file.name, bytes)
        ?: return Result.Failed(
          "${file.name} is too large to pass to the inspector in a link " +
            "(${bytes.size} bytes); open $URL and drop the file onto it instead."
        )
    if (url.length <= MAX_DIRECT_URL_CHARS) {
      return browse(url)?.let(Result::Failed) ?: Result.Opened(null)
    }
    val handoff =
      try {
        serveRedirect(url)
      } catch (e: Exception) {
        return Result.Failed("Could not start the local handoff page: ${e.message ?: e}")
      }
    browse(handoff.url)?.let {
      handoff.stop()
      return Result.Failed(it)
    }
    return Result.Opened(handoff)
  }

  class Handoff(val url: String, private val stopServer: () -> Unit) {
    private val done = CountDownLatch(1)
    private val stopped = AtomicBoolean(false)

    /** Idempotent: the first request, the timeout and a failed browse may all call it. */
    fun stop() {
      if (!stopped.compareAndSet(false, true)) return
      stopServer()
      done.countDown()
    }

    /** Blocks until the browser has fetched the page or the handoff timed out. */
    fun await() {
      done.await()
    }
  }

  /**
   * Serves one page on 127.0.0.1 that replaces itself with [url], and stops serving after the first
   * request or a minute, whichever comes first. The path carries a random token so nothing else on
   * the machine can guess it before the browser asks.
   */
  fun serveRedirect(url: String): Handoff {
    val token = UUID.randomUUID().toString()
    val body =
      ("""<!doctype html><meta charset="utf-8"><title>Opening the inspector…</title>""" +
          "<script>location.replace(${jsString(url)})</script>")
        .encodeToByteArray()
    val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    val handoff = Handoff("http://127.0.0.1:${server.address.port}/$token") { server.stop(0) }
    server.createContext("/$token") { exchange ->
      exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
      exchange.responseHeaders.add("Cache-Control", "no-store")
      exchange.sendResponseHeaders(200, body.size.toLong())
      exchange.responseBody.use { it.write(body) }
      thread(isDaemon = true) { handoff.stop() }
    }
    server.start()
    thread(isDaemon = true) {
      Thread.sleep(60_000)
      handoff.stop()
    }
    return handoff
  }

  /** [value] as a JavaScript string literal; the URL is ASCII, so escaping quotes is enough. */
  private fun jsString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("<", "\\u003c") + "\""

  private fun browse(url: String): String? {
    try {
      if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop().browse(URI(url))
        return null
      }
    } catch (_: Exception) {
      // AWT's BROWSE needs GNOME libraries on Linux; fall through to the freedesktop opener.
    }
    return try {
      ProcessBuilder("xdg-open", url).redirectErrorStream(true).start()
      null
    } catch (e: Exception) {
      "Could not start a browser (${e.message ?: e}). Open $URL and drop the file onto it."
    }
  }
}
