@file:Suppress("RestrictedApi")

package ee.schimke.composeai.rccreation.coexistence

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.creation.compose.capture.RemoteCreationDisplayInfo as AndroidxDisplayInfo
import androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument
import androidx.compose.remote.creation.profile.Profile
import androidx.compose.remote.player.core.RemoteDocument
import androidx.compose.remote.player.view.RemoteComposePlayer
import androidx.compose.runtime.Composable
import ee.schimke.composeai.rccreation.common.Profile as RcProfile
import ee.schimke.composeai.rccreation.common.RcPlatformProfiles
import ee.schimke.composeai.rccreation.compose.capture.RemoteCreationDisplayInfo as RcDisplayInfo
import ee.schimke.composeai.rccreation.compose.capture.captureCommonRemoteDocument
import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import kotlinx.coroutines.runBlocking

internal const val Width = 300
internal const val Height = 160

/** [content] as AndroidX's creation library writes it under [profile]. */
internal fun androidxDocument(
  context: Context,
  profile: Profile,
  content: @Composable () -> Unit,
): ByteArray = runBlocking {
  captureSingleRemoteDocument(
      context = context,
      creationDisplayInfo = AndroidxDisplayInfo(Width, Height, 160),
      profile = profile,
      content = content,
    )
    .bytes
}

/** The operations of [bytes] in the order they are written, without the header. */
internal fun wireOperations(bytes: ByteArray): List<RcOperation> =
  RcDocumentCodec.decode(bytes).operations

/**
 * The operations of [bytes] as text, one a line, in wire order, with every field but a component's
 * id: rc-players' creation numbers components from -1000000 (so that a retained composition keeps
 * them across renders) and AndroidX's from -2, so each is written as the order it first appears in.
 * Operations whose class is named in [without], or that [skip] takes, are left out.
 */
internal fun wireText(
  bytes: ByteArray,
  without: Set<String> = emptySet(),
  skip: (RcOperation) -> Boolean = { false },
): String {
  val components = HashMap<String, Int>()
  return wireOperations(bytes)
    .filter { it::class.simpleName !in without && !skip(it) }
    .joinToString("\n") { operation ->
      val text =
        ComponentId.replace(operation.toString()) { match ->
          "omponentId=#" + components.getOrPut(match.groupValues[1]) { components.size }
        }
      // A `ComponentValue` is its component's by where it is written (a player gives it to the
      // component it finds it in). The id it carries is the canvas's content with AndroidX's
      // writer, which writes the last component started, and the canvas itself with rc-players'.
      if (operation is RcComponentValue) OrderedComponentId.replace(text, "omponentId=#own") else text
    }
}

// A component's id as an operation's field, or as a `CoreText`'s first property.
private val ComponentId = Regex("(?:omponentId=|IntValue\\(id=1, value=)(-\\d+)")
private val OrderedComponentId = Regex("omponentId=#\\d+")

/** AndroidX's View player on [bytes], laid out at the document's size. */
internal class Played(context: Context, bytes: ByteArray) {
  val sent = mutableListOf<String>()
  private val remoteDocument = RemoteDocument(bytes)
  private val document: CoreDocument = remoteDocument.document
  val player = RemoteComposePlayer(context).apply { setDocument(remoteDocument) }

  init {
    document.addActionCallback { name, value -> sent.add("$name=$value") }
    frame()
  }

  fun frame(): Bitmap {
    player.measure(
      View.MeasureSpec.makeMeasureSpec(Width, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(Height, View.MeasureSpec.EXACTLY),
    )
    player.layout(0, 0, Width, Height)
    val image = Bitmap.createBitmap(Width, Height, Bitmap.Config.ARGB_8888)
    player.draw(Canvas(image))
    return image
  }

  fun drag(fromX: Float, toX: Float, y: Float) {
    val down = SystemClock.uptimeMillis()
    fun event(action: Int, x: Float) =
      MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
    player.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, fromX))
    frame()
    val steps = 8
    for (i in 1..steps) {
      player.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, fromX + (toX - fromX) * i / steps))
      frame()
    }
    // Held still before lifting: the release has no fling in it.
    SystemClock.sleep(120)
    player.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, toX))
    player.dispatchTouchEvent(event(MotionEvent.ACTION_UP, toX))
    repeat(10) {
      SystemClock.sleep(16)
      Thread.sleep(16)
      frame()
    }
  }
}

internal fun rcDocument(
  profile: RcProfile = RcPlatformProfiles.ANDROIDX,
  content: @Composable () -> Unit,
): ByteArray = runBlocking {
  captureCommonRemoteDocument(RcDisplayInfo(Width, Height, 160), profile = profile, content = content)
}

/** The header of [bytes], as written: everything before the first operation after it. */
internal fun headerBytes(bytes: ByteArray): List<Byte> {
  val document = RcDocumentCodec.decode(bytes)
  val operations = RcDocumentCodec.encode(document.copy(operations = emptyList()))
  check(bytes.take(operations.size) == operations.toList()) { "the header does not round-trip" }
  return operations.toList()
}
