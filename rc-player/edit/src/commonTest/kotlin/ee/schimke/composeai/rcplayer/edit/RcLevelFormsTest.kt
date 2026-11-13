package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.edit.RcProfileViolation.Kind
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBitmapTextMeasure
import ee.schimke.composeai.rcplayer.protocol.RcBorderModifier
import ee.schimke.composeai.rcplayer.protocol.RcCanvasContent
import ee.schimke.composeai.rcplayer.protocol.RcCanvasLayout
import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDraw4
import ee.schimke.composeai.rcplayer.protocol.RcDrawBitmapFontTextRun
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeaderProperty
import ee.schimke.composeai.rcplayer.protocol.RcHeaderValue
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcOffsetModifier
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPathData
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The forms the creation writer writes for API levels up to 7 and not above, and what [adaptTo] and
 * [validateFor] do with each: `docs/design/VERSION_QUIRKS.md`. The writer's own output, held to
 * these, is `:rc-creation-bridge`'s `VersionParityEditTest`.
 */
class RcLevelFormsTest {
  private val androidx7 = RcTargetProfile.of(7, RcProfiles.ANDROIDX)
  private val display = RcDisplay(density = 3f)

  private fun header(
    version: RcVersion = RcVersion(1, 1, 0),
    profiles: Int = RcProfiles.ANDROIDX,
    vararg more: RcHeaderProperty,
  ) =
    RcHeader(
      version,
      properties =
        listOf(
          RcHeaderProperty(RcHeader.DOC_WIDTH, RcHeaderValue.IntValue(200)),
          RcHeaderProperty(RcHeader.DOC_HEIGHT, RcHeaderValue.IntValue(100)),
          RcHeaderProperty(DOC_PROFILES, RcHeaderValue.IntValue(profiles)),
        ) + more,
    )

  private fun document(header: RcHeader, vararg operations: RcOperation) =
    RcDocument(header, operations.toList())

  private val rect: RcOperation =
    RcDraw4(
      RcOpcodes.DRAW_RECT,
      RcFloatWord.literal(0f),
      RcFloatWord.literal(0f),
      RcFloatWord.literal(10f),
      RcFloatWord.literal(10f),
    )

  /** A root with a canvas (id -10) that reads its own size, and a box (id -20) that is not one. */
  private fun withCanvas(vararg content: RcOperation, header: RcHeader = header()) =
    document(
      header,
      RcRootLayout(-2),
      RcLayoutContent(-3),
      RcCanvasLayout(-10, -1),
      RcLayoutContent(-11),
      *content,
      END,
      END,
      END,
      END,
    )

  private val widthOfCanvas = RcComponentValue(0, -10, 50)
  private val heightOfCanvas = RcComponentValue(1, -10, 51)
  private val widthOfBox = RcComponentValue(0, -20, 52)

  // --- A canvas's drawing is in a CanvasContent up to level 7 -----------------------------------

  @Test
  fun aCanvasDrawsInACanvasContentForALevelSevenTarget() {
    val document = withCanvas(widthOfCanvas, heightOfCanvas, widthOfBox, rect)

    for (target in listOf(RcTargetProfiles.WIDGETS_V7, androidx7)) {
      val adapted = document.adaptTo(target, emptySet(), display).document
      val content = adapted.operations.filterIsInstance<RcCanvasContent>().single()
      assertEquals(
        listOf(
          RcRootLayout(-2),
          RcLayoutContent(-3),
          RcCanvasLayout(-10, -1),
          RcLayoutContent(-11),
          content,
          RcComponentValue(0, content.componentId, 50),
          RcComponentValue(1, content.componentId, 51),
          widthOfBox,
          rect,
          END,
          END,
          END,
          END,
          END,
        ),
        adapted.operations,
        target.name,
      )
      assertTrue(content.componentId < -11, "a fresh component id: $content")
      assertRoundTrips(adapted)
    }
  }

  @Test
  fun aLevelSixTargetGetsTheSameCanvasContent() {
    val adapted =
      withCanvas(widthOfCanvas, rect, header = header(RcVersion(1, 0, 0), profiles = 0))
        .adaptTo(RcTargetProfiles.WIDGETS_V6, emptySet(), display)
    assertTrue(adapted.isPlayable, adapted.violations.toString())
    val content = adapted.document.operations.filterIsInstance<RcCanvasContent>().single()
    assertEquals(
      content.componentId,
      adapted.document.operations.filterIsInstance<RcComponentValue>().single().componentId,
    )
  }

  @Test
  fun aCanvasThatHasItsContentIsLeftAloneAndOneWithNoDrawingGetsAnEmptyOne() {
    val target = RcTargetProfiles.WIDGETS_V7
    val once = withCanvas(widthOfCanvas, rect).adaptTo(target, emptySet(), display).document
    assertEquals(once, once.adaptTo(target, emptySet(), display).document, "idempotent")
    // As the writer writes a canvas that draws nothing.
    val empty = withCanvas().adaptTo(target, emptySet(), display).document
    assertEquals(1, empty.operations.count { it is RcCanvasContent })
    assertEquals(empty, empty.adaptTo(target, emptySet(), display).document)
  }

  @Test
  fun aLevelEightTargetKeepsTheCanvasAsItIs() {
    val document = withCanvas(widthOfCanvas, rect)
    val adapted = document.adaptTo(RcTargetProfiles.ANDROIDX, emptySet(), display).document
    assertEquals(document.operations, adapted.operations)
    // The other way, a CanvasContent is read by a level 8 player: nothing is rewritten.
    val level7 = document.adaptTo(androidx7, emptySet(), display).document
    assertEquals(
      level7.operations,
      level7.adaptTo(RcTargetProfiles.ANDROIDX, emptySet(), display).document.operations,
    )
  }

  @Test
  fun aLauncherPlayerReportsACanvasWithoutContentAsNothingDrawn() {
    val document = withCanvas(widthOfCanvas, rect)
    for (target in listOf(RcTargetProfiles.WIDGETS_V7)) {
      val violations = document.validateFor(target)
      assertEquals(listOf(Kind.UNSUPPORTED_FEATURE), violations.map { it.kind }, target.name)
      assertEquals(2, violations.single().operationIndex)
      assertTrue("CanvasContent" in violations.single().message)
    }
    // Another level 7 player is not known to draw it as nothing; level 8 reads it.
    assertEquals(emptyList(), document.validateFor(androidx7))
    assertEquals(emptyList(), document.validateFor(RcTargetProfiles.ANDROIDX))
    assertEquals(
      emptyList(),
      document.adaptTo(RcTargetProfiles.WIDGETS_V7, emptySet(), display).violations,
    )
  }

  // --- A border is in the legacy form up to level 7 --------------------------------------------

  private fun border(wireVersion: Int, width: RcFloatWord = RcFloatWord.literal(2f)) =
    RcBorderModifier(
      0,
      0,
      wireVersion,
      0,
      width,
      RcFloatWord.literal(0f),
      RcFloatWord.literal(1f),
      RcFloatWord.literal(0f),
      RcFloatWord.literal(0f),
      RcFloatWord.literal(1f),
      RcBackgroundModifier.SHAPE_RECTANGLE,
    )

  @Test
  fun aBorderIsTheLegacyFormUpToLevelSevenAndTheInsideOneAbove() {
    val document = document(header(), border(wireVersion = 1))
    fun wireVersion(target: RcTargetProfile) =
      (document.adaptTo(target, emptySet(), display).document.operations.last() as RcBorderModifier)
        .wireVersion
    assertEquals(0, wireVersion(androidx7))
    assertEquals(0, wireVersion(RcTargetProfiles.WIDGETS_V7))
    assertEquals(0, wireVersion(RcTargetProfiles.WIDGETS_V6))
    assertEquals(1, wireVersion(RcTargetProfiles.ANDROIDX))
  }

  // --- Density behaviour is read from level 8 ---------------------------------------------------

  private fun dpHeader(behavior: Int = RcHeader.DENSITY_BEHAVIOR_DP) =
    header(
      more =
        arrayOf(RcHeaderProperty(RcHeader.DOC_DENSITY_BEHAVIOR, RcHeaderValue.IntValue(behavior))),
    )

  @Test
  fun aDensityBehaviourBelowLevelEightIsRefusedNamingTheFeatureAndTheTarget() {
    for (behavior in listOf(RcHeader.DENSITY_BEHAVIOR_DP, RcHeader.DENSITY_BEHAVIOR_PIXELS)) {
      val document = document(dpHeader(behavior), rect)
      for (target in listOf(androidx7, RcTargetProfiles.WIDGETS_V7, RcTargetProfiles.WIDGETS_V6)) {
        val violation =
          document.validateFor(target).single {
            it.kind == Kind.UNSUPPORTED_FEATURE && it.operationIndex == null
          }
        assertTrue("density behaviour" in violation.message, violation.message)
        assertTrue(target.name in violation.message, violation.message)
        assertFalse(document.adaptTo(target, emptySet(), display).isPlayable, target.name)
      }
      assertEquals(emptyList(), document.validateFor(RcTargetProfiles.ANDROIDX))
    }
    // The legacy behaviour, written or not, is read everywhere.
    val legacy = document(dpHeader(RcHeader.DENSITY_BEHAVIOR_LEGACY), rect)
    assertEquals(emptyList(), legacy.validateFor(RcTargetProfiles.WIDGETS_V7))
  }

  @Test
  fun scrollDirectionsAreRefusedForALevelSixHeaderNotDropped() {
    val scrolling =
      header(
        more =
          arrayOf(
            RcHeaderProperty(RcHeader.DOC_SCROLL, RcHeaderValue.IntValue(RcHeader.SCROLL_VERTICAL)),
          ),
      )
    val document = document(scrolling, rect)
    val violation =
      document.validateFor(RcTargetProfiles.WIDGETS_V6).single {
        it.kind == Kind.UNSUPPORTED_FEATURE && it.operationIndex == null
      }
    assertTrue("scroll" in violation.message && "API level 7" in violation.message)
    val adapted = document.adaptTo(RcTargetProfiles.WIDGETS_V6, emptySet(), display)
    assertTrue(adapted.document.header.modern, "the header that can carry it is kept")
    assertFalse(adapted.isPlayable)
    assertEquals(emptyList(), document.validateFor(RcTargetProfiles.WIDGETS_V7))
  }

  // --- A winding rule from level 7, glyph spacing from level 8 ---------------------------------

  @Test
  fun aWindingRuleIsRefusedBelowLevelSeven() {
    val document =
      document(header(RcVersion(1, 0, 0), 0), RcPathData(70 or (1 shl 24), emptyList()))
    val violation =
      document.validateFor(RcTargetProfiles.WIDGETS_V6).single {
        it.kind == Kind.UNSUPPORTED_FEATURE
      }
    assertEquals(0, violation.operationIndex)
    assertTrue("winding" in violation.message && "API level 7" in violation.message)
    assertEquals(
      emptyList(),
      document(header(), RcPathData(70 or (1 shl 24), emptyList()))
        .validateFor(RcTargetProfiles.WIDGETS_V7),
    )
  }

  @Test
  fun aGlyphSpacingIsRefusedBelowLevelEight() {
    val spacing = RcFloatWord.literal(1.5f)
    val operations =
      listOf(
        RcDrawBitmapFontTextRun(
          70,
          71,
          0,
          2,
          RcFloatWord.literal(0f),
          RcFloatWord.literal(0f),
          spacing,
        ),
        RcBitmapTextMeasure(72, 70, 71, RcBitmapTextMeasure.MEASURE_WIDTH, spacing),
      )
    val target = androidx7
    val document = RcDocument(header(), operations)
    val violations = document.validateFor(target).filter { it.kind == Kind.UNSUPPORTED_FEATURE }
    assertEquals(listOf(0, 1), violations.map { it.operationIndex })
    assertTrue(violations.all { "glyph spacing" in it.message && "API level 8" in it.message })
    assertTrue(
      document.validateFor(RcTargetProfiles.ANDROIDX).none { it.kind == Kind.UNSUPPORTED_FEATURE },
    )
    // No spacing is no spacing, at any level.
    val none =
      RcDocument(
        header(),
        listOf(
          RcDrawBitmapFontTextRun(70, 71, 0, 2, RcFloatWord.literal(0f), RcFloatWord.literal(0f)),
        ),
      )
    assertTrue(none.validateFor(target).none { it.kind == Kind.UNSUPPORTED_FEATURE })
  }

  // --- A launcher's paint measuring is off ------------------------------------------------------

  private fun paintMeasure(document: RcDocument): Int? =
    (document.header.properties.singleOrNull { it.key == RcHeader.FEATURE_PAINT_MEASURE }?.value
        as? RcHeaderValue.IntValue)
      ?.value

  @Test
  fun aLauncherHeaderTurnsPaintMeasuringOffUnlessItSaysOtherwise() {
    val document = document(header(), rect)
    assertEquals(
      0,
      paintMeasure(document.adaptTo(RcTargetProfiles.WIDGETS_V7, emptySet(), display).document),
    )
    // Not for a player that is sized by its parent.
    assertEquals(
      null,
      paintMeasure(document.adaptTo(RcTargetProfiles.ANDROIDX, emptySet(), display).document),
    )
    assertEquals(null, paintMeasure(document.adaptTo(androidx7, emptySet(), display).document))
    val explicit =
      document(
        header(
          more =
            arrayOf(RcHeaderProperty(RcHeader.FEATURE_PAINT_MEASURE, RcHeaderValue.IntValue(1))),
        ),
        rect,
      )
    assertEquals(
      1,
      paintMeasure(explicit.adaptTo(RcTargetProfiles.WIDGETS_V7, emptySet(), display).document),
    )
    // A level 6 header has no properties to carry it.
    assertFalse(
      document.adaptTo(RcTargetProfiles.WIDGETS_V6, emptySet(), display).document.header.modern,
    )
  }

  // --- The header names the player it is for ----------------------------------------------------

  @Test
  fun aHeaderNamingAnotherPlayerIsRedeclaredForTheTarget() {
    val document = document(header(profiles = RcProfiles.ANDROIDX), rect)
    val launcher = document.adaptTo(RcTargetProfiles.WIDGETS_V7, emptySet(), display).document
    assertEquals(RcProfiles.WIDGETS, launcher.header.profiles)
    // The target's own, additive and baseline bits are not a different player.
    val experimental = document(header(profiles = RcProfiles.ANDROIDX), rect)
    assertEquals(
      RcProfiles.ANDROIDX,
      experimental.adaptTo(RcTargetProfiles.ANDROIDX, emptySet(), display).document.header.profiles,
    )
    assertEquals(
      0,
      document(header(profiles = 0), rect)
        .adaptTo(RcTargetProfiles.WIDGETS_V7, emptySet(), display)
        .document
        .header
        .profiles,
    )
  }

  // --- A level 6 header read as level 7's -------------------------------------------------------

  private val coreText = RcCoreText(42, listOf(RcTextStyleProperty.IntValue(1, 7)))

  @Test
  fun aLevelSixDocumentRewrittenForLevelSevenHasItsDpSizesInPixels() {
    // Android 16 multiplies a level 6 border's width and an offset by the density; the level 7
    // header reads them as pixels. Rewriting the header carries each over.
    val level6 = RcHeader(RcVersion(1, 0, 0), legacyWidth = 200, legacyHeight = 100, modern = false)
    val document =
      RcDocument(
        level6,
        listOf(
          border(0, RcFloatWord.literal(2f)),
          RcOffsetModifier(RcFloatWord.literal(4f), RcFloatWord.literal(0f)),
          coreText,
        ),
      )
    assertTrue(document.needsDisplay(RcTargetProfiles.ANDROIDX))

    val adapted = document.adaptTo(RcTargetProfiles.ANDROIDX, emptySet(), display)

    assertTrue(adapted.isPlayable, adapted.violations.toString())
    assertTrue(adapted.document.header.modern)
    assertEquals(6f, (adapted.document.operations[0] as RcBorderModifier).borderWidth.value)
    val offset = adapted.document.operations[1] as RcOffsetModifier
    assertEquals(12f, offset.x.value)
    assertEquals(0, offset.y.bits)
  }

  @Test
  fun aLevelSixHeaderThatStaysKeepsItsDpSizes() {
    val level6 = RcHeader(RcVersion(1, 0, 0), legacyWidth = 200, legacyHeight = 100, modern = false)
    val document = RcDocument(level6, listOf(border(0, RcFloatWord.literal(2f))))
    assertFalse(document.needsDisplay(RcTargetProfiles.ANDROIDX))
    val adapted = document.adaptTo(RcTargetProfiles.ANDROIDX, emptySet(), display).document
    assertEquals(document, adapted)
  }

  @Test
  fun aVariableBorderWidthCannotBeCarriedToLevelSeven() {
    val level6 = RcHeader(RcVersion(1, 0, 0), legacyWidth = 200, legacyHeight = 100, modern = false)
    val variable = RcFloatWord(0x7fc00000 or 60)
    val document = RcDocument(level6, listOf(border(0, variable), coreText))
    val adapted = document.adaptTo(RcTargetProfiles.ANDROIDX, emptySet(), display)
    assertTrue(
      Kind.DENSITY_SCALED in adapted.violations.map { it.kind },
      adapted.violations.toString(),
    )
    assertNotEquals(true, adapted.document.header.modern)
  }
}
