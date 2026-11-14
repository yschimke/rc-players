package ee.schimke.composeai.rcplayer.compose

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcClickModifier
import ee.schimke.composeai.rcplayer.protocol.RcColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHostAction
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import ee.schimke.composeai.rcplayer.protocol.RcZIndexModifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A flattened clickable reports the same bounds to accessibility services as its own component
 * does: its touch bounds, which Compose widens to the minimum touch target around a small text.
 *
 * Android reports a node's bounds less what the nodes placed above it cover. A run of the flat
 * container's texts (the ones after a composed child, read after it) spans every text it reads,
 * so placed above the tap targets it covered a clickable text between them, and a speaker's name
 * on a session card was reported as the strip below the name, 14 dp tall, instead of 48 dp around
 * it. (This document is drawn at its own pixels, so the name's touch target is clipped to the card.)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-xxhdpi")
class RcAndroidFlatTouchBoundsTest {
  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun aFlattenedClickableTextReportsItsComponentsAccessibilityBounds() {
    val prepared = RcPreparedDocument(card())
    val flatten = mutableStateOf(false)
    rule.setContent {
      CompositionLocalProvider(
        LocalRcDeferLayoutAnimations provides true,
        LocalRcFlattenLayout provides flatten.value,
      ) {
        Box { RcComposePlayer(prepared, Modifier.width(400.dp), systemColors = { null }) }
      }
    }
    rule.waitForIdle()
    val components = clickableBounds()
    assertTrue("the fixture has no clickable name: $components", components.containsKey("Ada"))
    val name = components.getValue("Ada")
    val laidOut = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().first {
      it.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text == "Ada"
    }.boundsInRoot
    assertTrue(
      "the name's touch bounds are not widened past its text: $name, laid out at $laidOut",
      name.height() > laidOut.height + 1,
    )
    flatten.value = true
    rule.waitForIdle()
    assertEquals(components, clickableBounds())
  }

  /** Each clickable node's accessibility bounds, by its first text. */
  private fun clickableBounds(): Map<String, Rect> {
    val provider = composeView().accessibilityNodeProvider!!
    return rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().associate { node ->
      val bounds = Rect()
      provider.createAccessibilityNodeInfo(node.id)!!.getBoundsInScreen(bounds)
      (node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text ?: "") to bounds
    }
  }

  private fun composeView(): View =
    generateSequence(listOf<View>(rule.activity.window.decorView)) { level ->
        level
          .flatMap { view ->
            (view as? ViewGroup)?.let { group -> (0 until group.childCount).map(group::getChildAt) }
              .orEmpty()
          }
          .takeIf { it.isNotEmpty() }
      }
      .flatten()
      .first { it.javaClass.simpleName == "AndroidComposeView" }

  /**
   * A card's column: a badge the flat layout leaves composed (it has a z-index), then a room, a
   * clickable speaker's name and a tag. The room and the tag are read after the badge, so they go on
   * one run, which spans the name between them and is wider than the name's touch target.
   */
  private fun card(): RcDocument {
    fun text(id: Int, textId: Int, extra: List<RcOperation> = emptyList()) =
      listOf<RcOperation>(
        RcCoreText(
          textId = textId,
          properties =
            listOf(
              RcTextStyleProperty.IntValue(1, id),
              RcTextStyleProperty.IntValue(2, id + 1),
              RcTextStyleProperty.IntValue(3, 0xff000000.toInt()),
              RcTextStyleProperty.FloatValue(5, RcFloatWord.literal(14f)),
            ),
        ),
      ) + extra + listOf(END)
    return RcDocument(
      RcHeader(RcVersion(1, 1, 0), legacyWidth = 400, legacyHeight = 200, modern = false),
      listOf(
        RcTextData(BADGE_TEXT, "Live"),
        RcTextData(ROOM_TEXT, "Room 1, the main hall"),
        RcTextData(NAME_TEXT, "Ada"),
        RcTextData(TAG_TEXT, "#Kotlin"),
        RcRootLayout(1),
        RcLayoutContent(2),
        RcColumnLayout(COLUMN, -1, 1, 1, RcFloatWord.literal(4f)),
        RcWidthModifier(RcDimensionType.FILL, RcFloatWord.literal(1f)),
        RcLayoutContent(COLUMN + 1),
        RcBoxLayout(BADGE, -1, 1, 1),
        RcZIndexModifier(RcFloatWord.literal(0f)),
        RcLayoutContent(BADGE + 1),
      ) +
        text(BADGE + 10, BADGE_TEXT) +
        listOf(END, END) +
        text(ROOM, ROOM_TEXT) +
        text(NAME, NAME_TEXT, listOf(RcClickModifier, RcHostAction(1), END)) +
        text(TAG, TAG_TEXT) +
        listOf(END, END, END, END),
    )
  }

  private companion object {
    val END = RcNoArg(RcOpcodes.CONTAINER_END)
    const val COLUMN = 3
    const val BADGE = 10
    const val ROOM = 30
    const val NAME = 40
    const val TAG = 50
    const val BADGE_TEXT = 100
    const val ROOM_TEXT = 101
    const val NAME_TEXT = 102
    const val TAG_TEXT = 103
  }
}
