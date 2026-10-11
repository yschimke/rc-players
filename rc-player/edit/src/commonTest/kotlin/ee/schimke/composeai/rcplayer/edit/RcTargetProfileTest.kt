package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.edit.RcProfileViolation.Kind
import ee.schimke.composeai.rcplayer.protocol.RcClickModifier
import ee.schimke.composeai.rcplayer.protocol.RcColorAttribute
import ee.schimke.composeai.rcplayer.protocol.RcConditionalOperations
import ee.schimke.composeai.rcplayer.protocol.RcCoreText
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeaderProperty
import ee.schimke.composeai.rcplayer.protocol.RcHeaderValue
import ee.schimke.composeai.rcplayer.protocol.RcImageLayout
import ee.schimke.composeai.rcplayer.protocol.RcMacroCall
import ee.schimke.composeai.rcplayer.protocol.RcMacroDefine
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcRemark
import ee.schimke.composeai.rcplayer.protocol.RcSkip
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextStyleProperty
import ee.schimke.composeai.rcplayer.protocol.RcValueIntegerChangeAction
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWireWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RcTargetProfileTest {
  /** A level 8 AndroidX header, as `RcPlatformProfiles.ANDROIDX` writes one. */
  private fun androidxDocument(vararg operations: RcOperation) =
    RcDocument(
      RcHeader(
        RcVersion(1, 2, 0),
        properties =
          listOf(
            RcHeaderProperty(RcHeader.DOC_WIDTH, RcHeaderValue.IntValue(200)),
            RcHeaderProperty(RcHeader.DOC_HEIGHT, RcHeaderValue.IntValue(100)),
            RcHeaderProperty(DOC_PROFILES, RcHeaderValue.IntValue(RcProfiles.ANDROIDX)),
          ),
      ),
      operations.toList(),
    )

  private val coreText = RcCoreText(42, listOf(RcTextStyleProperty.IntValue(1, 7)))

  private fun image(alpha: RcFloatWord) = RcImageLayout(7, -1, bitmapId = 50, scaleType = 1, alpha)

  private fun ref(id: Int) = RcFloatWord(0x7fc00000 or id)

  private fun RcProfileAdaptation.kinds() = violations.map { it.kind }

  @Test
  fun theProfileTablesFollowAndroidX() {
    val v6 = RcTargetProfiles.WIDGETS_V6.supportedOpcodes
    assertTrue(RcOpcodes.LAYOUT_TEXT in v6 && RcOpcodes.DATA_SHADER in v6)
    assertFalse(RcOpcodes.CORE_TEXT in v6 || RcOpcodes.DATA_FONT in v6 || RcOpcodes.REM in v6)

    assertTrue(RcOpcodes.CORE_TEXT in RcTargetProfiles.ANDROIDX.supportedOpcodes)
    assertFalse(RcOpcodes.MACRO_CALL in RcTargetProfiles.ANDROIDX.supportedOpcodes)
    assertTrue(RcOpcodes.MACRO_CALL in RcTargetProfiles.ANDROIDX_EXPERIMENTAL.supportedOpcodes)

    val wear = RcTargetProfiles.WEAR_WIDGETS.supportedOpcodes
    assertTrue(RcOpcodes.DATA_FONT in wear)
    assertFalse(RcOpcodes.CORE_TEXT in wear)

    // Two profiles intersect: what both players read.
    val both = RcTargetProfile.of(7, RcProfiles.ANDROIDX or RcProfiles.WIDGETS).supportedOpcodes
    assertTrue(RcOpcodes.CORE_TEXT in both)
    assertFalse(RcOpcodes.DATA_FONT in both)
  }

  @Test
  fun anAndroidXDocumentFailsOnBaklavaWidgetsForEachReason() {
    val document =
      androidxDocument(
        RcFloatConstant(60, RcFloatWord.literal(0.5f)),
        RcNamedVariable(60, RcNamedVariable.FLOAT_TYPE, "USER:fade"),
        image(ref(60)),
        RcFloatExpression(61, listOf(RcFloatWord.literal(1f), ref(0x310_000 + 50)), null),
        coreText,
      )

    val violations = document.validateFor(RcTargetProfiles.WIDGETS_V6)

    assertEquals(
      listOf(
        Kind.HEADER_API_LEVEL to null,
        Kind.HEADER_FORMAT to null,
        Kind.DYNAMIC_VALUE to 2,
        Kind.FLOAT_OPERATOR to 3,
        Kind.UNSUPPORTED_OPERATION to 4,
      ),
      violations.map { it.kind to it.operationIndex },
    )
    assertEquals(
      "CORE_TEXT is not supported by Launcher widgets (Android 16, API level 6)",
      violations.last().message,
    )
    assertTrue(document.validateFor(RcTargetProfiles.ANDROIDX).isEmpty())
  }

  @Test
  fun adaptingForBaklavaFoldsConstantsDropsRemarksAndWritesTheLevelSixHeader() {
    val document =
      androidxDocument(
        RcRemark("generated"),
        RcFloatConstant(60, RcFloatWord.literal(0.5f)),
        image(ref(60)),
        END,
      )

    val adaptation = document.adaptTo(RcTargetProfiles.WIDGETS_V6)

    assertTrue(adaptation.isPlayable, adaptation.violations.toString())
    val adapted = assertRoundTrips(adaptation.document)
    assertEquals(RcVersion(1, 0, 0), adapted.header.version)
    assertFalse(adapted.header.modern)
    assertEquals(200 to 100, adapted.header.width to adapted.header.height)
    assertEquals(
      listOf(RcFloatConstant(60, RcFloatWord.literal(0.5f)), image(RcFloatWord.literal(0.5f)), END),
      adapted.operations,
    )
  }

  @Test
  fun aHostBoundValueIsNotFoldedUntilItIsHardcoded() {
    val document =
      androidxDocument(
        RcFloatConstant(60, RcFloatWord.literal(0.5f)),
        RcNamedVariable(60, RcNamedVariable.FLOAT_TYPE, "USER:fade"),
        image(ref(60)),
        END,
      )
    assertEquals(listOf(Kind.DYNAMIC_VALUE), document.adaptTo(RcTargetProfiles.WIDGETS_V6).kinds())

    val baked =
      document
        .hardcodeNamedValues(mapOf("fade" to RcHardcodedValue.FloatValue(0.25f)))
        .adaptTo(RcTargetProfiles.WIDGETS_V6)
    assertTrue(baked.isPlayable, baked.violations.toString())
  }

  @Test
  fun aLevelSixHeaderOverLevelSevenOperationsIsUpgradedToDeclareThem() {
    val document =
      RcDocument(
        RcHeader(RcVersion(1, 0, 0), legacyWidth = 200, legacyHeight = 100, modern = false),
        listOf(coreText),
      )
    assertEquals(
      listOf(Kind.UNDECLARED_OPERATION),
      document.validateFor(RcTargetProfiles.ANDROIDX).map { it.kind },
    )

    val adapted = document.adaptTo(RcTargetProfiles.ANDROIDX)

    assertTrue(adapted.isPlayable, adapted.violations.toString())
    assertEquals(8, assertRoundTrips(adapted.document).header.apiLevel)
    assertEquals(RcProfiles.ANDROIDX, adapted.document.header.profiles)
  }

  @Test
  fun macrosAreInlinedForAPlayerWithoutThem() {
    val body = RcWireWriter().also { RcDocumentCodec.encodeOperation(it, RcTextData(70, "hi")) }
    val document =
      androidxDocument(
        RcMacroDefine(500, emptyList(), body.toByteArray()),
        RcMacroCall(500, emptyList()),
        END,
      )
    assertTrue(
      document.validateFor(RcTargetProfiles.ANDROIDX).any { it.kind == Kind.UNSUPPORTED_OPERATION }
    )

    val adapted = document.adaptTo(RcTargetProfiles.ANDROIDX)

    assertTrue(adapted.isPlayable, adapted.violations.toString())
    // The call site becomes the body. The body's declared id is the fresh one the player's linker
    // gives each expansion, so it can never collide with an id outside the macro.
    val text = adapted.document.operations.single() as RcTextData
    assertEquals("hi", text.text)
  }

  @Test
  fun aTargetDecodesSkipGuardsAgainstItsOwnLevel() {
    // A writer guarding level 7 content: skip it on any player below level 7.
    val guarded =
      RcWireWriter()
        .also { RcDocumentCodec.encodeOperation(it, RcTextData(71, "new")) }
        .toByteArray()
    val writer = RcWireWriter()
    RcDocumentCodec.encodeOperation(
      writer,
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 10, legacyHeight = 10, modern = false),
    )
    RcDocumentCodec.encodeOperation(writer, RcSkip(RcSkip.IF_API_LESS_THAN, 7, guarded.size))
    writer.writeRawBytes(guarded)
    val bytes = writer.toByteArray()

    assertEquals(emptyList(), RcTargetProfiles.WIDGETS_V6.decode(bytes).operations)
    assertEquals(
      listOf<RcOperation>(RcTextData(71, "new")),
      RcTargetProfiles.ANDROIDX.decode(bytes).operations,
    )
  }

  @Test
  fun aConstantAnotherProducerAlsoWritesIsNotFolded() {
    val document =
      androidxDocument(
        RcFloatConstant(60, RcFloatWord.literal(0.5f)),
        RcColorAttribute(60, 5, RcColorAttribute.COLOR_HUE),
        image(ref(60)),
        END,
      )
    assertEquals(listOf(Kind.DYNAMIC_VALUE), document.adaptTo(RcTargetProfiles.WIDGETS_V6).kinds())
  }

  @Test
  fun textOnACircleIsDecodedOnlyByWear() {
    assertFalse(RcOpcodes.DRAW_TEXT_ON_CIRCLE in RcTargetProfiles.WIDGETS_V6.supportedOpcodes)
    assertFalse(RcOpcodes.DRAW_TEXT_ON_CIRCLE in RcTargetProfiles.ANDROIDX.supportedOpcodes)
    assertTrue(RcOpcodes.DRAW_TEXT_ON_CIRCLE in RcTargetProfiles.WEAR_WIDGETS.supportedOpcodes)
  }

  @Test
  fun aConstantWrittenUnderAConditionIsNotFolded() {
    val document =
      androidxDocument(
        RcConditionalOperations(
          RcConditionalOperations.EQUAL,
          RcFloatWord.literal(0f),
          RcFloatWord.literal(1f),
        ),
        RcFloatConstant(60, RcFloatWord.literal(0.5f)),
        END,
        image(ref(60)),
        END,
      )
    assertEquals(listOf(Kind.DYNAMIC_VALUE), document.adaptTo(RcTargetProfiles.WIDGETS_V6).kinds())
  }

  @Test
  fun aConstantAnIntegerActionAlsoWritesIsNotFolded() {
    val document =
      androidxDocument(
        RcFloatConstant(60, RcFloatWord.literal(0.5f)),
        RcClickModifier,
        RcValueIntegerChangeAction(60, 1),
        END,
        image(ref(60)),
        END,
      )
    assertEquals(listOf(Kind.DYNAMIC_VALUE), document.adaptTo(RcTargetProfiles.WIDGETS_V6).kinds())
  }
}
