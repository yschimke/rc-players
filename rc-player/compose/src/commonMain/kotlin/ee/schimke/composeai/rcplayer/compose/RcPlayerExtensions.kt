package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import ee.schimke.composeai.rcplayer.protocol.ExperimentalRcWireExtension
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcExtensionOperation
import ee.schimke.composeai.rcplayer.protocol.RcWireExtension
import ee.schimke.composeai.rcplayer.protocol.RcWireExtensions
import ee.schimke.composeai.rcplayer.runtime.RcPlayerState

/**
 * A player extension: operations the player does not ship, decoded by [wire] and drawn by a
 * [RcExtensionSession] the extension creates for each document.
 *
 * Extensions are opt-in. A host installs them with [LocalRcPlayerExtensions]; with none installed
 * the player refuses an unknown opcode exactly as it always has, which is also what AndroidX's own
 * players do.
 */
@ExperimentalRcPlayerApi
@ExperimentalRcWireExtension
public interface RcPlayerExtension {
  public val wire: RcWireExtension

  /** Called once per played document that contains at least one of this extension's operations. */
  public fun createSession(document: RcDocument, state: RcPlayerState): RcExtensionSession
}

/** The per-document half of an [RcPlayerExtension]. */
@ExperimentalRcPlayerApi
@ExperimentalRcWireExtension
public interface RcExtensionSession {
  /**
   * Called for each of the extension's operations, in document order, every time the stream that
   * holds it is drawn. Values read through [RcExtensionDrawScope.state] are tracked as the player's
   * own reads are, so a document whose 3D camera follows the clock is redrawn with it.
   */
  public fun draw(operation: RcExtensionOperation, scope: RcExtensionDrawScope)

  /** Called when the document leaves composition. */
  public fun dispose() {}
}

/** What the player hands an extension while drawing one of its operations. */
@ExperimentalRcPlayerApi
public interface RcExtensionDrawScope {
  /** The canvas, in the coordinate space of the component being drawn. */
  public val drawScope: DrawScope

  public val state: RcPlayerState

  /** The current paint colour as unpremultiplied ARGB, with the paint's alpha applied. */
  public val paintColor: Int

  /** A bitmap the document declared (or drew into), or null if it is not loaded. */
  public fun bitmap(id: Int): ImageBitmap?
}

/** The extensions a player decodes and draws with. */
@ExperimentalRcPlayerApi
@ExperimentalRcWireExtension
public class RcPlayerExtensions(extensions: List<RcPlayerExtension>) {
  public constructor(vararg extensions: RcPlayerExtension) : this(extensions.toList())

  public val extensions: List<RcPlayerExtension> = extensions.toList()

  /**
   * The wire half of every extension, for
   * [ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec.decode].
   */
  public val wire: RcWireExtensions = RcWireExtensions(this.extensions.map { it.wire })

  private val byId: Map<String, RcPlayerExtension> = this.extensions.associateBy { it.wire.id }

  internal fun extension(id: String): RcPlayerExtension? = byId[id]

  public companion object {
    public val None: RcPlayerExtensions = RcPlayerExtensions(emptyList())
  }
}

/**
 * The extensions a player below this point decodes and draws with. [RcComposePlayer] overloads that
 * take bytes decode with them; a host that decodes a document itself passes
 * [RcPlayerExtensions.wire] to `RcDocumentCodec.decode`.
 */
@ExperimentalRcPlayerApi
@ExperimentalRcWireExtension
public val LocalRcPlayerExtensions: ProvidableCompositionLocal<RcPlayerExtensions> =
  staticCompositionLocalOf {
    RcPlayerExtensions.None
  }

/** The sessions of one played document, created only for extensions it actually uses. */
@OptIn(ExperimentalRcWireExtension::class)
internal class RcExtensionSessions
private constructor(private val sessions: Map<String, RcExtensionSession>) {
  fun draw(operation: RcExtensionOperation, scope: RcExtensionDrawScope) {
    sessions[operation.extensionId]?.draw(operation, scope)
  }

  fun dispose() {
    sessions.values.forEach { it.dispose() }
  }

  companion object {
    fun create(
      extensions: RcPlayerExtensions,
      document: RcDocument,
      state: RcPlayerState,
    ): RcExtensionSessions? {
      if (extensions.extensions.isEmpty()) return null
      val used =
        document.operations.mapNotNullTo(LinkedHashSet()) {
          (it as? RcExtensionOperation)?.extensionId
        }
      if (used.isEmpty()) return null
      return RcExtensionSessions(
        used
          .mapNotNull { id ->
            extensions.extension(id)?.let { id to it.createSession(document, state) }
          }
          .toMap()
      )
    }
  }
}

internal val LocalRcExtensionSessions: ProvidableCompositionLocal<RcExtensionSessions?> =
  staticCompositionLocalOf {
    null
  }
