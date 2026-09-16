package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
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
