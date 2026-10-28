package ee.schimke.composeai.rccreation.bridge

import androidx.compose.remote.creation.common.RemoteWriter
import androidx.compose.remote.creation.common.Utils
import androidx.compose.remote.creation.compose.modifier.RemoteEnterTransition
import androidx.compose.remote.creation.compose.modifier.RemoteExitTransition

/**
 * Defines a custom visibility animation in the document [this] writes, and returns its function id,
 * for [rcCustomEnterTransition] or [rcCustomExitTransition].
 *
 * remote-creation-core's `RcScope.defineVisibilityAnimation`, over the creation fork's
 * [RemoteWriter]: AndroidX has no Compose-level way to define one, so rc-players carries this one
 * here rather than in the fork. Its block receives what a player binds when it runs the function:
 * the animating component's id, the animation's progress (0 to 1) and the component's bounds in its
 * parent's coordinates. Draw the component with [RemoteWriter.drawComponentContent], or render it
 * into an offscreen buffer ([RemoteWriter.createOffscreenBitmap] and
 * [RemoteWriter.drawComponentToBitmap]) and draw that. The floats are NaN-encoded variable ids,
 * usable anywhere the writer takes one.
 *
 * Define the animations on the writer before capturing content into it, with
 * `captureCommonRemoteDocumentInto`, so the content can name them:
 * ```
 * val writer = RemoteDocumentWriter(width, height, densityBehavior)
 * val dissolve = writer.defineVisibilityAnimation { component, progress, w, h, x, y ->
 *   val buffer = createOffscreenBitmap(component)
 *   drawComponentToBitmap(component, buffer)
 *   // ... draw buffer
 * }
 * captureCommonRemoteDocumentInto(writer, displayInfo) {
 *   RemoteBox(RemoteModifier.visibility(visible).animateEnterExit(exit = rcCustomExitTransition(dissolve)))
 * }
 * ```
 *
 * `RemoteDocumentWriter` writes the definition ahead of the layout whenever it is called, with its
 * body inline; see its `createFloatFunction`.
 */
@ExperimentalRcCreationApi
public fun RemoteWriter.defineVisibilityAnimation(
  block:
    RemoteWriter.(
      component: Int,
      progress: Float,
      width: Float,
      height: Float,
      x: Float,
      y: Float,
    ) -> Unit
): Int {
  val args = FloatArray(VisibilityFunctionParameters)
  val id = createFloatFunction(args)
  block(Utils.idFromNan(args[5]), args[0], args[1], args[2], args[3], args[4])
  endFloatFunction()
  return id
}

/** An enter transition drawn by the visibility animation [functionId] defines. */
@ExperimentalRcCreationApi
@Suppress("RestrictedApi")
public fun rcCustomEnterTransition(functionId: Int): RemoteEnterTransition =
  RemoteEnterTransition.Custom(functionId)

/** An exit transition drawn by the visibility animation [functionId] defines. */
@ExperimentalRcCreationApi
@Suppress("RestrictedApi")
public fun rcCustomExitTransition(functionId: Int): RemoteExitTransition =
  RemoteExitTransition.Custom(functionId)

/** Progress, width, height, x, y and the component id, in the order a player binds them. */
private const val VisibilityFunctionParameters = 6
