package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcAnimationSpec
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcLayoutAnimation
import ee.schimke.composeai.rcplayer.runtime.RcDocumentLinker
import ee.schimke.composeai.rcplayer.runtime.RcLayoutNode
import ee.schimke.composeai.rcplayer.runtime.RcLayoutTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RcWithoutDefaultAnimationsTest {
  private val chosen =
    RcAnimationSpec(
      animationId = 7,
      motionDurationMillis = RcFloatWord.literal(500f),
      motionEasingType = 2,
      visibilityDurationMillis = RcFloatWord.literal(200f),
      visibilityEasingType = 1,
      enterAnimation = RcLayoutAnimation.FadeIn,
      exitAnimation = RcLayoutAnimation.FadeOut,
    )

  @Test
  fun everyComponentWithoutASpecGetsADisabledOne() {
    val rewritten =
      assertRoundTrips(
        document(*rootLayout(*box(10).toTypedArray(), *box(20).toTypedArray()).toTypedArray())
          .withoutDefaultAnimations(),
      )
    val specs = specsByComponent(rewritten)
    assertEquals(setOf(-1, 10, 20), specs.keys, "the root and both boxes, never a content wrapper")
    specs.values.forEach { assertEquals(false, it?.isEnabled) }
  }

  @Test
  fun aSpecTheDocumentChoseIsKept() {
    val chosenBox = listOf(box(10)[0], chosen) + box(10).drop(1)
    val rewritten =
      assertRoundTrips(
        document(*rootLayout(*chosenBox.toTypedArray(), *box(20).toTypedArray()).toTypedArray())
          .withoutDefaultAnimations(),
      )
    val specs = specsByComponent(rewritten)
    assertEquals(chosen, specs[10])
    assertEquals(false, specs[20]?.isEnabled)
  }

  @Test
  fun aDocumentWithNothingToAddIsReturnedAsItIs() {
    val once =
      document(*rootLayout(*box(10).toTypedArray()).toTypedArray()).withoutDefaultAnimations()
    assertSame(once, once.withoutDefaultAnimations())
  }

  private fun specsByComponent(
    document: ee.schimke.composeai.rcplayer.protocol.RcDocument,
  ): Map<Int, RcAnimationSpec?> {
    val root = assertNotNull(RcLayoutTree.build(RcDocumentLinker.link(document)))
    val specs = mutableMapOf<Int, RcAnimationSpec?>()
    fun visit(node: RcLayoutNode) {
      if (node !is RcLayoutNode.Content) specs[node.componentId] = node.modifiers.animationSpec
      when (node) {
        is RcLayoutNode.Root -> node.children.forEach(::visit)
        is RcLayoutNode.Content -> node.children.forEach(::visit)
        is RcLayoutNode.Box -> node.content?.let(::visit)
        else -> Unit
      }
    }
    visit(root)
    assertTrue(specs.isNotEmpty())
    return specs
  }
}
