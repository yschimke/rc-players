@file:Suppress("RestrictedApi", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package ee.schimke.composeai.rccreation.coexistence

import androidx.compose.runtime.Composable
import androidx.compose.remote.core.operations.utilities.AnimatedFloatExpression
import androidx.compose.remote.core.RemoteContext
import androidx.compose.remote.core.operations.Utils
import androidx.compose.remote.creation.compose.action.hostAction
import androidx.compose.remote.creation.compose.capture.RemoteComposeCreationState
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteArrangement
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.clip
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.modifier.visibility
import androidx.compose.remote.creation.compose.shapes.RemoteCircleShape
import androidx.compose.remote.creation.compose.shapes.RemoteRoundedCornerShape
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.ri
import androidx.compose.remote.creation.compose.layout.RemoteCanvas
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteFlowRow
import androidx.compose.remote.creation.compose.layout.RemoteOffset
import androidx.compose.remote.creation.compose.layout.RemoteSize
import androidx.compose.remote.creation.compose.layout.RemoteStateLayout
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.clickable
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.height
import androidx.compose.remote.creation.compose.modifier.onTouchDown
import androidx.compose.remote.creation.compose.modifier.onTouchUp
import androidx.compose.remote.creation.compose.state.LazyRemoteString
import androidx.compose.remote.creation.compose.state.MutableRemoteFloat
import androidx.compose.remote.creation.compose.state.MutableRemoteString
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemoteInt
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.RemoteState
import androidx.compose.remote.creation.compose.state.RemoteStateInstanceKey
import androidx.compose.remote.creation.compose.state.animateRemoteFloatAsState
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.remoteTween
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.creation.compose.state.rsp

/** The parity content, on AndroidX's creation API. [RcContent] is the same text. */
internal object AndroidxContent {
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
        state.document.buffer.addTouchExpression(
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
        state.document.buffer.textMerge(
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

  /** Two texts and a canvas: what a restricted profile writes otherwise (text) and as it is. */
  @Composable
  fun Labelled() {
    RemoteColumn(modifier = RemoteModifier.fillMaxSize()) {
      RemoteText("Title".rs, fontSize = 20.rsp)
      RemoteText("Body".rs)
      RemoteCanvas(modifier = RemoteModifier.fillMaxWidth().height(20.rdp)) {
        drawRect(RemotePaint(), RemoteOffset(0f.rf, 0f.rf), RemoteSize(width, height))
      }
    }
  }

  /** A flow row, which only AndroidX's experimental profile has an operation for. */
  @Composable
  fun Flowing() {
    RemoteFlowRow(modifier = RemoteModifier.fillMaxSize()) { RemoteText("Body".rs) }
  }

  /** A box that runs a host action when clicked. */
  @Composable
  fun Clickable() {
    RemoteBox(modifier = RemoteModifier.fillMaxSize()) {
      RemoteBox(
        modifier =
          RemoteModifier.fillMaxWidth().height(40.rdp).clickable(hostAction("tap".rs))
      )
    }
  }

  /** A named float two canvases and a box's width read. */
  @Composable
  fun NamedInTwoCanvases() {
    val level = RemoteFloat.createNamedRemoteFloat("level", 10f)
    RemoteColumn(modifier = RemoteModifier.fillMaxSize()) {
      RemoteCanvas(modifier = RemoteModifier.fillMaxWidth().height(20.rdp)) {
        drawRect(RemotePaint(), RemoteOffset(0f.rf, 0f.rf), RemoteSize(level * 2f, 20f.rf))
      }
      RemoteCanvas(modifier = RemoteModifier.fillMaxWidth().height(20.rdp)) {
        drawRect(RemotePaint(), RemoteOffset(0f.rf, 0f.rf), RemoteSize(level * 3f, 20f.rf))
      }
    }
  }

  /**
   * A named float that starts as its component's measured width, read by text and by a state
   * layout: how a consumer sizes or switches content by the space it is given. It is asked for in
   * a layout, as `remoteComponent()` has to be: the root is not a component it can name.
   * (AndroidX 1.0.0-alpha23 spells it `createNamedRemoteFloatExpression { componentWidth() }`.)
   */
  @Composable
  fun NamedMeasuredWidth() {
    RemoteColumn(modifier = RemoteModifier.fillMaxSize()) {
      val width = RemoteFloat.createNamedRemoteFloatExpression("measured", RemoteState.Domain.User) { componentWidth() }
      RemoteText(width.toRemoteStringWithPadding(4, 0, 0))
      RemoteStateLayout(width.isGreaterThanOrEqualTo(200f.rf)) { wide ->
        RemoteText(if (wide) "WIDE".rs else "NARROW".rs)
      }
    }
  }

  /**
   * A column of three texts whose middle child, a clipped box of a named integer's visibility, is
   * hidden: it takes no room and no spacing. [clipped] is whether that child has a rounded clip.
   * [shown] is the named integer's start, above 3 shows the child.
   */
  @Composable
  fun HiddenClipped(clipped: Boolean, shown: Int) {
    RemoteColumn(
      modifier = RemoteModifier.fillMaxSize(),
      verticalArrangement = RemoteArrangement.spacedBy(10.rdp),
    ) {
      val state = RemoteInt.createNamedRemoteInt("poll_state", shown)
      RemoteText("HEAD".rs)
      val visible = state.isGreaterThan(3.ri).toRemoteInt()
      var box = RemoteModifier.fillMaxWidth().visibility(visible)
      if (clipped) box = box.clip(RemoteRoundedCornerShape(12.rdp))
      RemoteColumn(modifier = box.background(0xFF3366FF.toInt().rc)) { RemoteText("MIDDLE".rs) }
      RemoteText("FOOT".rs)
    }
  }

  /** A 40 px box clipped to a circle, with a named integer's visibility on it (1) or on its parent (2). */
  @Composable
  fun CircleClipUnderVisibility(where: Int) {
    val shown = RemoteInt.createNamedRemoteInt("poll_state", 5).isGreaterThan(3.ri).toRemoteInt()
    RemoteBox(
      (if (where == 2) RemoteModifier.visibility(shown) else RemoteModifier).fillMaxSize()
    ) {
      RemoteBox(
        (if (where == 1) RemoteModifier.visibility(shown) else RemoteModifier)
          .size(40.rdp)
          .clip(RemoteCircleShape)
          .background(0xFF3366FF.toInt().rc)
      ) {}
    }
  }
}
