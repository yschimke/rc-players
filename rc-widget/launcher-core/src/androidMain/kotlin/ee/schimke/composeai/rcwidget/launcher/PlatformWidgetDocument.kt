@file:Suppress("RestrictedApi")

package ee.schimke.composeai.rcwidget.launcher

import androidx.compose.remote.core.Operation
import androidx.compose.remote.core.Operations
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.core.WireBuffer
import androidx.compose.remote.core.operations.ColorAttribute
import androidx.compose.remote.core.operations.ColorConstant
import androidx.compose.remote.core.operations.ColorExpression
import androidx.compose.remote.core.operations.ComponentValue
import androidx.compose.remote.core.operations.DataListIds
import androidx.compose.remote.core.operations.FloatConstant
import androidx.compose.remote.core.operations.FloatExpression
import androidx.compose.remote.core.operations.IntegerExpression
import androidx.compose.remote.core.operations.NamedVariable
import androidx.compose.remote.core.operations.TextData
import androidx.compose.remote.core.operations.TextFromFloat
import androidx.compose.remote.core.operations.TextLength
import androidx.compose.remote.core.operations.TextLookup
import androidx.compose.remote.core.operations.TextLookupInt
import androidx.compose.remote.core.operations.TextMerge
import androidx.compose.remote.core.operations.layout.CanvasContent
import androidx.compose.remote.core.operations.layout.CanvasOperations
import androidx.compose.remote.core.operations.layout.Component
import androidx.compose.remote.core.operations.layout.Container
import androidx.compose.remote.core.operations.layout.ContainerEnd
import androidx.compose.remote.core.types.IntegerConstant
import ee.schimke.composeai.rcplayer.edit.RcTargetProfile
import ee.schimke.composeai.rcplayer.edit.RcTargetProfiles

/**
 * The bytes of any Remote Compose widget capture, made ready for `RemoteViews.DrawInstructions`: this
 * is API-neutral, a pass over the wire format, and both creation bindings' captures go through it.
 * [platformWidgetDocument] for an Android 16 launcher: [RcTargetProfiles.WIDGETS_V6]. */
public fun platformWidgetDocument(bytes: ByteArray): ByteArray =
  platformWidgetDocument(bytes, RcTargetProfiles.WIDGETS_V6)

/**
 * The final platform-bound document for a launcher reading [target], after the baking/codec
 * passes, whichever creation API authored the bytes: resource definitions lifted ahead of the layout, and the header's wire
 * version the one [target] reads.
 *
 * The platform player picks its operation table from that version: 1.0 is document level 6, whose
 * table has no profiles and no ColorTheme; 1.1 is level 7 with the header's profile bits. So only a
 * level 6 [target] (Android 16, which accepts nothing newer) gets 1.0; a document captured for
 * API 37's ColorTheme (`systemThemedWidgetsProfile`, `RcCaptureProfiles.systemThemedLauncherWidgets`)
 * downgraded too would fail on an API 37 launcher with "Unknown operation encountered 196".
 */
public fun platformWidgetDocument(bytes: ByteArray, target: RcTargetProfile): ByteArray {
  require(bytes.size >= 13 && bytes[0].toInt() == Operations.HEADER) {
    "Expected an uncompressed RemoteCompose widget header"
  }
  // Android 16's LayoutComponent.inflate drops data operations that were not yet
  // ComponentData in the platform version (notably TextFromFloat and named variables).
  // Keep the original flat operation stream and lift resource definitions/expressions
  // ahead of the layout. Canvas expressions remain local: evaluating an inactive
  // canvas globally can read an unmeasured dimension (NaN). Actions remain local.
  val operations = arrayListOf<Operation>()
  RemoteComposeBuffer.fromInputStream(bytes.inputStream()).inflateFromBuffer(operations)
  val containerStack = ArrayDeque<Boolean>()
  val resources = arrayListOf<Operation>()
  val body = arrayListOf<Operation>()
  val components = operations.filterIsInstance<Component>().map { it.componentId }.toSet()
  val componentValues =
    operations
      .filterIsInstance<ComponentValue>()
      .filter { it.componentId in components }
      .groupBy { it.componentId }
  for (op in operations) {
    val inCanvas = containerStack.lastOrNull() == true
    when {
      op is ComponentValue && op.componentId in componentValues -> Unit
      !inCanvas && op.javaClass in widgetResourceOperations -> resources.add(op)
      else -> body.add(op)
    }
    when (op) {
      is Container ->
        containerStack.addLast(inCanvas || op is CanvasOperations || op is CanvasContent)
      is ContainerEnd -> containerStack.removeLast()
    }
  }
  val buffer = WireBuffer()
  body.first().write(buffer)
  resources.forEach { it.write(buffer) }
  body.drop(1).forEach { op ->
    op.write(buffer)
    // Older LayoutComponentContent retains only text and bitmap data. Values must
    // sit directly on their owning component, not inside its content container.
    if (op is Component) componentValues[op.componentId]?.forEach { it.write(buffer) }
  }
  return buffer.buffer.copyOf(buffer.size).also { compatible ->
    // The writer says 1.1 (level 7); a level 6 target reads 1.0. Zero the minor version, the int
    // after the opcode and the tagged major, keeping the major and every layout/data/profile field.
    if (target.apiLevel < 7) compatible.fill(0, fromIndex = MINOR_VERSION_START, toIndex = MINOR_VERSION_END)
  }
}

/** The header's minor version: the int after the opcode byte and the major version int. */
private const val MINOR_VERSION_START = 5
private const val MINOR_VERSION_END = 9

private val widgetResourceOperations =
  setOf<Class<out Operation>>(
    TextData::class.java,
    TextFromFloat::class.java,
    TextMerge::class.java,
    TextLookup::class.java,
    TextLookupInt::class.java,
    TextLength::class.java,
    FloatConstant::class.java,
    FloatExpression::class.java,
    IntegerConstant::class.java,
    IntegerExpression::class.java,
    ColorConstant::class.java,
    ColorExpression::class.java,
    ColorAttribute::class.java,
    NamedVariable::class.java,
    DataListIds::class.java,
  )
