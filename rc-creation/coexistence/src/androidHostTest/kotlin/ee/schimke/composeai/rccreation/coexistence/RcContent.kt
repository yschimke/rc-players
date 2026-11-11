@file:Suppress("RestrictedApi", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package ee.schimke.composeai.rccreation.coexistence

import androidx.compose.runtime.Composable
import ee.schimke.composeai.rccreation.common.AnimatedFloatExpression
import ee.schimke.composeai.rccreation.common.RemoteContext
import ee.schimke.composeai.rccreation.common.Utils
import ee.schimke.composeai.rccreation.compose.action.hostAction
import ee.schimke.composeai.rccreation.compose.capture.RemoteComposeCreationState
import ee.schimke.composeai.rccreation.compose.layout.RemoteBox
import ee.schimke.composeai.rccreation.compose.layout.RemoteCanvas
import ee.schimke.composeai.rccreation.compose.layout.RemoteOffset
import ee.schimke.composeai.rccreation.compose.layout.RemoteSize
import ee.schimke.composeai.rccreation.compose.layout.RemoteStateLayout
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxSize
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxWidth
import ee.schimke.composeai.rccreation.compose.modifier.height
import ee.schimke.composeai.rccreation.compose.modifier.onTouchDown
import ee.schimke.composeai.rccreation.compose.modifier.onTouchUp
import ee.schimke.composeai.rccreation.compose.state.LazyRemoteString
import ee.schimke.composeai.rccreation.compose.state.MutableRemoteFloat
import ee.schimke.composeai.rccreation.compose.state.MutableRemoteString
import ee.schimke.composeai.rccreation.compose.state.RemoteInt
import ee.schimke.composeai.rccreation.compose.state.RemotePaint
import ee.schimke.composeai.rccreation.compose.state.RemoteStateInstanceKey
import ee.schimke.composeai.rccreation.compose.state.animateRemoteFloatAsState
import ee.schimke.composeai.rccreation.compose.state.rdp
import ee.schimke.composeai.rccreation.compose.state.remoteTween
import ee.schimke.composeai.rccreation.compose.state.rf
import ee.schimke.composeai.rccreation.compose.state.rs

/** The parity content, on rc-players' creation API. [AndroidxContent] is the same text. */
internal object RcContent {
  /** An id reserved in a document before the operation that defines it is written. */
  private class Slot {
    private val ids = HashMap<Any, Int>()

    fun id(state: RemoteComposeCreationState): Int =
      ids.getOrPut(state.document) { state.document.nextId() }
  }

  /**
   * A slider, as a consumer writes one: a canvas whose touch expression moves a value by half a
   * unit per pixel dragged from 30, text made from the value in the canvas, a bar twice the value
   * wide, and a touch-up that sends the text.
   */
  @Composable
  fun Slider() {
    val valueSlot = Slot()
    val labelSlot = Slot()
    val value =
      MutableRemoteFloat(RemoteStateInstanceKey()) { state -> Utils.asNan(valueSlot.id(state)) }
    val label =
      MutableRemoteString(
        null,
        RemoteStateInstanceKey(),
        object : LazyRemoteString {
          override fun reserveTextId(creationState: RemoteComposeCreationState): Int =
            labelSlot.id(creationState)

          override fun computeRequiredCodePointSet(
            creationState: RemoteComposeCreationState
          ): Set<String>? = null
        },
      )
    RemoteBox(modifier = RemoteModifier.fillMaxSize()) {
      RemoteCanvas(
        modifier =
          RemoteModifier.fillMaxWidth()
            .height(40.rdp)
            .onTouchDown(hostAction("down".rs))
            .onTouchUp(hostAction("set".rs, label))
      ) {
        val state = remoteComposeCreationState
        state.document.addTouchExpression(
          valueSlot.id(state),
          30f,
          0f,
          100f,
          0f,
          0,
          floatArrayOf(RemoteContext.FLOAT_TOUCH_POS_X, 0.5f, AnimatedFloatExpression.MUL),
          0,
          null,
          null,
        )
        state.document.textMerge(
          labelSlot.id(state),
          state.document.createTextFromFloat(Utils.asNan(valueSlot.id(state)), 3, 0, 0),
          state.document.addText(""),
        )
        drawRect(RemotePaint(), RemoteOffset(0f.rf, 0f.rf), RemoteSize(value * 2f, 40f.rf))
      }
    }
  }

  /** A canvas that draws a bar half its own measured width, eased in: a canvas-local expression. */
  @Composable
  fun MeasuredCanvas() {
    RemoteBox(modifier = RemoteModifier.fillMaxSize()) {
      RemoteCanvas(modifier = RemoteModifier.fillMaxWidth().height(40.rdp)) {
        val grown = animateRemoteFloatAsState(width * 0.5f, remoteTween(200))
        drawRect(RemotePaint(), RemoteOffset(0f.rf, 0f.rf), RemoteSize(grown, height))
      }
    }
  }

  /**
   * The same bar in one branch of a state layout: an animated float that reads a measured size
   * inside a branch.
   */
  @Composable
  fun MeasuredInStateLayout() {
    val mode = RemoteInt.createNamedRemoteInt("mode", 0)
    RemoteBox(modifier = RemoteModifier.fillMaxSize()) {
      RemoteStateLayout(mode, 0, 1, modifier = RemoteModifier.fillMaxSize()) { branch ->
        if (branch == 0) {
          RemoteCanvas(modifier = RemoteModifier.fillMaxWidth().height(40.rdp)) {
            val grown = animateRemoteFloatAsState(width * 0.25f, remoteTween(200))
            drawRect(RemotePaint(), RemoteOffset(0f.rf, 0f.rf), RemoteSize(grown, height))
          }
        } else {
          RemoteBox(modifier = RemoteModifier.fillMaxWidth().height(20.rdp))
        }
      }
    }
  }
}
