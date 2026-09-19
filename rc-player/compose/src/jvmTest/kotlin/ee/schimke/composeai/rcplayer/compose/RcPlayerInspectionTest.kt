package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcScrollModifier
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The inspection seam's two load-bearing properties.
 *
 * The first is the cost claim, and it is the reason this file exists: with inspection off the
 * player must publish nothing, because that is what justifies the seam living in the shipped player
 * rather than behind a debug build. It decays silently — move the `semantics {}` call out of its
 * guarded branch and every document starts paying for a semantics node per component, with no test
 * failing and no visible symptom.
 *
 * The second is that the published vocabulary is AndroidX's, not this player's Kotlin type names,
 * which is what makes a cross-player comparison mean anything.
 */
class RcPlayerInspectionTest {
  private val end = RcNoArg(RcOpcodes.CONTAINER_END)

  /** A root containing one fixed-size box — the smallest document with a real component tree. */
  private val document =
    RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 100, legacyHeight = 100, modern = false),
      listOf(
        RcRootLayout(1),
        RcLayoutContent(2),
        RcBoxLayout(
          componentId = 3,
          animationId = -1,
          horizontalPositioning = 1,
          verticalPositioning = 4,
        ),
        RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)),
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)),
        end,
        end,
        end,
      ),
    )

  @OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
  private fun publishedComponents(inspecting: Boolean): List<SemanticsNode> {
    val scene =
      ImageComposeScene(width = 100, height = 100, density = Density(1f)) {
        CompositionLocalProvider(LocalRcInspection provides inspecting) {
          RcComposePlayer(document, Modifier.fillMaxSize())
        }
      }
    return try {
      scene.render(0L)
      buildList {
        fun walk(node: SemanticsNode) {
          if (node.config.getOrElseNullable(RcComponentIdKey) { null } != null) add(node)
          node.children.forEach(::walk)
        }
        scene.semanticsOwners.firstOrNull()?.unmergedRootSemanticsNode?.let(::walk)
      }
    } finally {
      scene.close()
    }
  }

  @Test
  fun publishesNothingWhenNobodyIsInspecting() {
    assertEquals(
      emptyList(),
      publishedComponents(inspecting = false),
      "the player published components with inspection off, so every document now pays for a " +
        "semantics node per component",
    )
  }

  /**
   * A scroll offset is modifier-induced translation: Compose realises it by moving the children, so
   * it lands in their `positionInRoot`, while the corpus keeps it out of `x`/`y` and reports it as
   * `scroll_x`/`scroll_y` instead (`CONFORMANCE_FORMAT.md` §2.7 and §4.3). Both halves are asserted
   * here because either one alone is satisfiable by accident: a container that never scrolled
   * reports `y: 0` too.
   */
  @OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
  @Test
  fun publishesAScrollContainersOffsetAndKeepsItOutOfItsChildrensPositions() {
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0), legacyWidth = 40, legacyHeight = 40, modern = false),
        listOf(
          RcRootLayout(1),
          RcLayoutContent(2),
          RcColumnLayout(3, 30, 1, 4, RcFloatWord.literal(0f)),
          RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)),
          RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)),
          RcScrollModifier(
            RcScrollModifier.VERTICAL,
            RcFloatWord.literal(40f),
            RcFloatWord.literal(0f),
            RcFloatWord.literal(0f),
          ),
          end,
          RcLayoutContent(4),
          RcCanvasLayout(5, 50),
          RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(40f)),
          RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(80f)),
          end,
          end,
          end,
          end,
          end,
        ),
      )
    val scene =
      ImageComposeScene(width = 40, height = 40, density = Density(1f)) {
        CompositionLocalProvider(LocalRcInspection provides true) {
          RcComposePlayer(document, Modifier.fillMaxSize())
        }
      }
    try {
      scene.render(0L)
      val nodes = buildList {
        fun walk(node: SemanticsNode) {
          if (node.config.getOrElseNullable(RcComponentIdKey) { null } != null) add(node)
          node.children.forEach(::walk)
        }
        scene.semanticsOwners.firstOrNull()?.unmergedRootSemanticsNode?.let(::walk)
      }
      val container =
        requireNotNull(
          nodes.firstOrNull { it.config.getOrElseNullable(RcComponentIdKey) { null } == 3 }
        )
      val scrollOffset =
        requireNotNull(container.config.getOrElseNullable(RcScrollOffsetKey) { null }) {
          "the container did not publish the offset it scrolled its content by"
        }
      assertEquals(Offset(0f, -40f), scrollOffset)
      val child =
        requireNotNull(
          nodes.firstOrNull { it.config.getOrElseNullable(RcComponentIdKey) { null } == 5 }
        )
      // `positionInRoot` carries the translation, because Compose realises a scroll by moving the
      // children. Taking the published offset back out — what a tree reader does — is what leaves
      // the layout manager's assignment.
      assertEquals(
        0f,
        child.positionInRoot.y - (container.positionInRoot.y + scrollOffset.y),
        "the scroll offset leaked into the child's reported position",
      )
    } finally {
      scene.close()
    }
  }

  @Test
  fun publishesTheComponentTreeWhenInspecting() {
    val components = publishedComponents(inspecting = true)
    assertTrue(components.isNotEmpty(), "inspection was on and nothing was published")

    val kinds = components.mapNotNull { it.config.getOrElseNullable(RcComponentKindKey) { null } }
    assertTrue("RootLayoutComponent" in kinds, "expected AndroidX's vocabulary, got $kinds")
    assertTrue(
      kinds.none { it.startsWith("Rc") },
      "this player's own type names leaked into the published tree: $kinds",
    )
  }
}
