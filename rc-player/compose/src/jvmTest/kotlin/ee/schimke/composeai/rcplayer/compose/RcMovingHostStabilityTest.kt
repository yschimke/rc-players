package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.skia.Bitmap

/**
 * A player in a scrolling list is moved by its host on every frame of the scroll, and the card
 * under the finger is recomposed on every pointer move when its document reads the touch position.
 * Neither may make the player redo work for components that are not changing.
 */
class RcMovingHostStabilityTest {

  /**
   * `ROOT_Y` is measured from the document's root, as AndroidX measures it: the player 20 px down
   * its host still reads 0 for a component at its top, rather than 20, and a scroll does not change
   * it on every frame.
   */
  @Test
  fun aRootPositionIsMeasuredFromThePlayerNotTheWindow() {
    val scene =
      ImageComposeScene(width = 80, height = 60, density = Density(1f)) {
        Column {
          Spacer(Modifier.height(20.dp))
          RcComposePlayer(document())
        }
      }
    try {
      // The child is `ROOT_Y + 10` wide and 20 tall: 10 wide at the player's top.
      for (frame in 0..10) scene.render(frame * FRAME_NANOS)
      assertEquals(10 * 20, scene.render(11 * FRAME_NANOS).greenPixels())
    } finally {
      scene.close()
    }
  }

  /**
   * Moving the player and touching it recompose it, and neither changes a component's visibility or
   * its bounds within its parent. `AnimatedVisibility` around every component, and an idle bounds
   * animation handed the same target, still wrote their transition state on every one of those
   * frames — for every component of every card on screen.
   */
  @OptIn(ExperimentalComposeUiApi::class)
  @Test
  fun aMovedAndTouchedPlayerWritesNoTransitionState() {
    var top by mutableIntStateOf(0)
    val scene =
      ImageComposeScene(width = 80, height = 80, density = Density(1f)) {
        Column {
          Spacer(Modifier.height(top.dp))
          RcComposePlayer(document())
        }
      }
    val transitionWrites = AtomicInteger()
    val observer = Snapshot.registerGlobalWriteObserver {
      if (
        Thread.currentThread().stackTrace.any { frame ->
          TRANSITION_WRITERS.any { frame.className.contains(it) }
        }
      ) {
        transitionWrites.incrementAndGet()
      }
    }
    try {
      for (frame in 0..10) scene.render(frame * FRAME_NANOS)
      transitionWrites.set(0)
      scene.sendPointerEvent(PointerEventType.Press, Offset(5f, top + 5f), type = PointerType.Touch)
      for (frame in 11..30) {
        top += 1
        scene.sendPointerEvent(
          PointerEventType.Move,
          Offset(5f + frame % 7, top + 5f),
          type = PointerType.Touch,
        )
        scene.render(frame * FRAME_NANOS)
      }
      assertEquals(0, transitionWrites.get())
      // Still drawn where it was: 10 wide, 20 tall.
      assertEquals(10 * 20, scene.render(31 * FRAME_NANOS).greenPixels())
    } finally {
      observer.dispose()
      scene.close()
    }
  }

  /**
   * A card scrolling on screen lays out for the first time, and a component's first layout has
   * nothing to animate from. Making each component's size and offset animations up front wrote
   * their state and launched a coroutine for every component of every card, on the frame it
   * appeared.
   */
  @Test
  fun aFirstLayoutMakesNoBoundsAnimation() {
    val animationWrites = AtomicInteger()
    val observer = Snapshot.registerGlobalWriteObserver {
      if (
        Thread.currentThread().stackTrace.any { it.className.contains("DeferredTargetAnimation") }
      ) {
        animationWrites.incrementAndGet()
      }
    }
    val scene =
      ImageComposeScene(width = 80, height = 80, density = Density(1f)) {
        RcComposePlayer(document())
      }
    try {
      for (frame in 0..10) scene.render(frame * FRAME_NANOS)
      assertEquals(0, animationWrites.get())
      assertEquals(10 * 20, scene.render(11 * FRAME_NANOS).greenPixels())
    } finally {
      observer.dispose()
      scene.close()
    }
  }

  private fun org.jetbrains.skia.Image.greenPixels(): Int {
    val image = this
    val bitmap = Bitmap().apply { allocN32Pixels(image.width, image.height) }
    check(image.readPixels(bitmap))
    var count = 0
    for (y in 0 until image.height) {
      for (x in 0 until image.width) if (bitmap.getColor(x, y) == GREEN) count++
    }
    return count
  }

  /**
   * A 40 x 30 box whose child is `ROOT_Y + 10` wide, in a document that also reads the touch
   * position (so a pointer move recomposes it).
   */
  private fun document(): RcDocument =
    RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 80, legacyHeight = 40, modern = false),
      listOf(
        RcRootLayout(1),
        RcLayoutContent(2),
        RcBoxLayout(3, -1, 1, 4),
        RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)),
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(30f)),
        RcLayoutContent(4),
        RcComponentValue(RcComponentValue.ROOT_Y, componentId = 4, valueId = PARENT_ROOT_Y),
        RcFloatExpression(
          CHILD_WIDTH,
          listOf(reference(PARENT_ROOT_Y), RcFloatWord.literal(10f), ADD),
          animation = null,
        ),
        RcFloatExpression(
          TOUCHED_X,
          listOf(reference(TOUCH_X), RcFloatWord.literal(0f), ADD),
          animation = null,
        ),
        RcBoxLayout(5, -1, 1, 4),
        RcWidthModifier(RcDimensionType.EXACT, reference(CHILD_WIDTH)),
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(20f)),
        RcBackgroundModifier(
          flags = 0,
          colorId = 0,
          reserved1 = 0,
          reserved2 = 0,
          red = RcFloatWord.literal(0f),
          green = RcFloatWord.literal(1f),
          blue = RcFloatWord.literal(0f),
          alpha = RcFloatWord.literal(1f),
          shapeType = RcBackgroundModifier.SHAPE_RECTANGLE,
        ),
        RcLayoutContent(6),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcNoArg(RcOpcodes.CONTAINER_END),
      ),
    )

  private fun reference(id: Int) = RcFloatWord(0x7fc00000 or id)

  private companion object {
    const val GREEN = 0xff00ff00.toInt()
    const val TOUCH_X = 13
    const val PARENT_ROOT_Y = 42
    const val CHILD_WIDTH = 43
    const val TOUCHED_X = 44
    /** AndroidX `AnimatedFloatExpression.ADD`: `OFFSET + 1` in an `0xff8`-prefixed NaN. */
    val ADD = RcFloatWord(0xffb10001.toInt())
    const val FRAME_NANOS = 16_666_667L
    val TRANSITION_WRITERS =
      listOf("AnimatedVisibility", "RcVisibilityTransition", "RcAnimateBoundsNode")
  }
}
