package ee.schimke.composeai.rcplayer.demos

import ee.schimke.composeai.rcplayer.compose.RcSpannableString
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcColorConstant
import ee.schimke.composeai.rcplayer.protocol.RcColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcCustomLayout
import ee.schimke.composeai.rcplayer.protocol.RcCustomProperty
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatExpression
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeaderProperty
import ee.schimke.composeai.rcplayer.protocol.RcHeaderValue
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaddingModifier
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcSystemVariables
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextLayout
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import ee.schimke.composeai.rcplayer.runtime.RcFloatExpressionEvaluator

/**
 * The two demo documents, written the way a document that reached this player from the wire would
 * look — operations, not composables. Both are authored here rather than captured so the ids the
 * host renderers read are visible next to the renderers that read them.
 */
public object RcDemoDocuments {
  public const val WIDTH: Int = 360

  /** The spannable-string document is two lines of text and nothing else. */
  public const val SPANNABLE_HEIGHT: Int = 96

  /** The editable-text document is a field, a caption and the document's own echo of the value. */
  public const val EDITABLE_HEIGHT: Int = 150

  /** The deferred-density document is one label. */
  public const val HOST_DENSITY_HEIGHT: Int = 80

  /** Native SwiftUI controls bound in both directions through custom-component properties. */
  public const val SWIFT_CONTROLS_HEIGHT: Int = 220

  /** A document-configured SwiftUI PhaseAnimator. */
  public const val SWIFT_PULSE_HEIGHT: Int = 212

  /** An interactive Swift Charts component with its selection returned to the document. */
  public const val SWIFT_CHART_HEIGHT: Int = 252

  /** The text id the editable-text demo both reads and writes; see [RcEditableText]. */
  public const val EDITED_TEXT_ID: Int = 60

  /**
   * A paragraph with two link spans, drawn by [RcSpannableString].
   *
   * The colour arrives as a `COLOR_ID_PROP` — a reference to the document's own colour table rather
   * than a literal — because that is the case a custom component could not express before: a themed
   * or host-overridden colour reaching a host-drawn component.
   */
  public fun spannableString(): RcDocument {
    val text = "Read the terms and the privacy notice before continuing."
    val termsStart = text.indexOf("terms")
    val privacyStart = text.indexOf("privacy notice")
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    val operations =
      listOf<RcOperation>(
        RcColorConstant(TEXT_COLOR_ID, 0xff202124.toInt()),
        RcTextData(CONFIG_ID, RcSpannableString.CONFIG),
        RcTextData(TEXT_ID, text),
        RcTextData(TERMS_URL_ID, "https://example.com/terms"),
        RcTextData(PRIVACY_URL_ID, "https://example.com/privacy"),
        RcRootLayout(1),
        RcLayoutContent(2),
        RcCustomLayout(
          componentId = 3,
          animationId = 0,
          configId = CONFIG_ID,
          properties =
            listOf(
              RcCustomProperty.string(RcSpannableString.PROP_TEXT, TEXT_ID),
              RcCustomProperty.colorId(RcSpannableString.PROP_TEXT_COLOR, TEXT_COLOR_ID),
              RcCustomProperty.float(RcSpannableString.PROP_TEXT_SIZE, RcFloatWord.literal(15f)),
              RcCustomProperty.int(RcSpannableString.PROP_LINK_COUNT, 2),
              RcCustomProperty.string(RcSpannableString.PROP_LINK_URL_BASE, TERMS_URL_ID),
              RcCustomProperty.int(RcSpannableString.PROP_LINK_START_BASE, termsStart),
              RcCustomProperty.int(RcSpannableString.PROP_LINK_END_BASE, termsStart + 5),
              RcCustomProperty.string(RcSpannableString.PROP_LINK_URL_BASE + 1, PRIVACY_URL_ID),
              RcCustomProperty.int(RcSpannableString.PROP_LINK_START_BASE + 1, privacyStart),
              RcCustomProperty.int(RcSpannableString.PROP_LINK_END_BASE + 1, privacyStart + 14),
            ),
        ),
        background(0.97f, 0.97f, 0.98f),
        padding(16f),
        width(WIDTH.toFloat()),
        height(SPANNABLE_HEIGHT.toFloat()),
        end,
        end,
        end,
      )
    return RcDocument(header(SPANNABLE_HEIGHT), operations)
  }

  /**
   * A text field whose edits go back into the document.
   *
   * The custom component carries the same text id twice — once as a `STRING_PROP` it reads, once as
   * a `TEXT_RETURN` it writes — and the label underneath is an ordinary document text operation
   * reading that same id. Nothing wires the two together at the host: typing updates the document,
   * and the document redraws whatever else depends on it.
   */
  public fun editableText(): RcDocument {
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    val operations =
      listOf<RcOperation>(
        RcTextData(CONFIG_ID, RcEditableText.CONFIG),
        RcTextData(EDITED_TEXT_ID, "Hello from the document"),
        RcTextData(LABEL_ID, "The document sees:"),
        RcRootLayout(1),
        RcLayoutContent(2),
        RcColumnLayout(3, 0, 1, 4, RcFloatWord.literal(12f)),
        background(0.97f, 0.97f, 0.98f),
        padding(16f),
        width(WIDTH.toFloat()),
        height(EDITABLE_HEIGHT.toFloat()),
        RcLayoutContent(4),
        RcCustomLayout(
          componentId = 5,
          animationId = 0,
          configId = CONFIG_ID,
          properties =
            listOf(
              RcCustomProperty.string(RcEditableText.PROP_TEXT, EDITED_TEXT_ID),
              RcCustomProperty.textReturn(RcEditableText.PROP_TEXT_RETURN, EDITED_TEXT_ID),
              RcCustomProperty.colorId(RcEditableText.PROP_TEXT_COLOR, TEXT_COLOR_ID),
            ),
        ),
        width(WIDTH - 32f),
        height(40f),
        end,
      ) +
        label(componentId = 6, textId = LABEL_ID, size = 12f, color = 0xff5f6368.toInt()) +
        label(componentId = 7, textId = EDITED_TEXT_ID, size = 15f, color = 0xff202124.toInt()) +
        List(4) { end }
    return RcDocument(
      header(EDITABLE_HEIGHT),
      listOf(RcColorConstant(TEXT_COLOR_ID, 0xff202124.toInt())) + operations,
    )
  }

  /** A native SwiftUI text field and slider that both write their edits back to this document. */
  public fun swiftControls(): RcDocument {
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    val operations =
      listOf<RcOperation>(
        RcTextData(CONFIG_ID, "demo:SwiftControls"),
        RcTextData(SWIFT_NAME_ID, "Ada"),
        RcTextData(LABEL_ID, "The document sees:"),
        RcFloatConstant(SWIFT_LEVEL_ID, RcFloatWord.literal(0.68f)),
        RcColorConstant(TEXT_COLOR_ID, 0xff6750a4.toInt()),
        RcRootLayout(1),
        RcLayoutContent(2),
        RcColumnLayout(3, 0, 1, 4, RcFloatWord.literal(8f)),
        background(0.97f, 0.97f, 0.99f),
        padding(16f),
        width(WIDTH.toFloat()),
        height(SWIFT_CONTROLS_HEIGHT.toFloat()),
        RcLayoutContent(4),
        RcCustomLayout(
          componentId = 5,
          animationId = 0,
          configId = CONFIG_ID,
          properties =
            listOf(
              RcCustomProperty.string(1, SWIFT_NAME_ID),
              RcCustomProperty.textReturn(2, SWIFT_NAME_ID),
              RcCustomProperty.float(3, reference(SWIFT_LEVEL_ID)),
              RcCustomProperty.floatReturn(4, reference(SWIFT_LEVEL_ID)),
              RcCustomProperty.colorId(5, TEXT_COLOR_ID),
            ),
        ),
        width(WIDTH - 32f),
        height(130f),
        end,
      ) +
        label(componentId = 6, textId = LABEL_ID, size = 12f, color = 0xff6f6f78.toInt()) +
        label(componentId = 7, textId = SWIFT_NAME_ID, size = 16f, color = 0xff202124.toInt()) +
        List(4) { end }
    return RcDocument(header(SWIFT_CONTROLS_HEIGHT), operations)
  }

  /** A SwiftUI PhaseAnimator whose title, tint, pace and amplitude come from the document. */
  public fun swiftPulse(): RcDocument {
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    return RcDocument(
      header(SWIFT_PULSE_HEIGHT),
      listOf(
        RcTextData(CONFIG_ID, "demo:SwiftPulse"),
        RcTextData(SWIFT_TITLE_ID, "Remote heartbeat"),
        RcColorConstant(TEXT_COLOR_ID, 0xffff4f87.toInt()),
        RcRootLayout(1),
        RcLayoutContent(2),
        RcCustomLayout(
          componentId = 3,
          animationId = 0,
          configId = CONFIG_ID,
          properties =
            listOf(
              RcCustomProperty.string(1, SWIFT_TITLE_ID),
              RcCustomProperty.colorId(2, TEXT_COLOR_ID),
              RcCustomProperty.float(3, RcFloatWord.literal(0.72f)),
              RcCustomProperty.float(4, RcFloatWord.literal(0.18f)),
            ),
        ),
        background(0.05f, 0.05f, 0.08f),
        padding(16f),
        width(WIDTH.toFloat()),
        height(SWIFT_PULSE_HEIGHT.toFloat()),
        end,
        end,
        end,
      ),
    )
  }

  /** A native Swift Chart whose drag selection is returned to ordinary document text. */
  public fun swiftChart(): RcDocument {
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    val samples = listOf(18f, 27f, 22f, 36f, 31f, 44f, 39f)
    val values = samples.mapIndexed { index, value ->
      RcFloatConstant(SWIFT_CHART_BASE_ID + index, RcFloatWord.literal(value))
    }
    val properties =
      listOf(
        RcCustomProperty.string(1, SWIFT_TITLE_ID),
        RcCustomProperty.textReturn(2, SWIFT_SELECTION_ID),
        RcCustomProperty.colorId(3, TEXT_COLOR_ID),
      ) +
        samples.indices.map { index ->
          RcCustomProperty.float(10 + index, reference(SWIFT_CHART_BASE_ID + index))
        }
    val operations =
      listOf<RcOperation>(
        RcTextData(CONFIG_ID, "demo:SwiftChart"),
        RcTextData(SWIFT_TITLE_ID, "A week of momentum"),
        RcTextData(SWIFT_SELECTION_ID, "Drag across the chart"),
        RcTextData(LABEL_ID, "Selection returned to the document:"),
        RcColorConstant(TEXT_COLOR_ID, 0xff5b5bd6.toInt()),
      ) +
        values +
        listOf(
          RcRootLayout(1),
          RcLayoutContent(2),
          RcColumnLayout(3, 0, 1, 4, RcFloatWord.literal(6f)),
          background(0.97f, 0.97f, 0.99f),
          padding(16f),
          width(WIDTH.toFloat()),
          height(SWIFT_CHART_HEIGHT.toFloat()),
          RcLayoutContent(4),
          RcCustomLayout(5, 0, CONFIG_ID, properties),
          width(WIDTH - 32f),
          height(174f),
          end,
        ) +
        label(componentId = 6, textId = LABEL_ID, size = 11f, color = 0xff6f6f78.toInt()) +
        label(
          componentId = 7,
          textId = SWIFT_SELECTION_ID,
          size = 14f,
          color = 0xff202124.toInt(),
        ) +
        List(4) { end }
    return RcDocument(header(SWIFT_CHART_HEIGHT), operations)
  }

  /**
   * A capture that DEFERS its density instead of folding it in — `RemoteDensity.Host`.
   *
   * Every other fixture in this repository folds the capture device's density and font scale into
   * literal constants, so none of them reads [RcSystemVariables.DENSITY] or
   * [RcSystemVariables.FONT_SIZE] and none of them notices a player that fails to load those ids.
   * This one is the other kind: its text size is the expression such a capture writes for a 15sp
   * label — `([33] 14.0 / [27] / 15.0 *)`, which recovers the host's font scale from the two
   * built-ins and applies it to the authored size.
   *
   * A player that loads neither id resolves both references to their own raw `NaN` bits (or to
   * zero, in the Swift core's value map) and the whole expression collapses — which is the bug this
   * fixture exists to catch, in any player, without needing a device to see it.
   *
   * The header declares dp behavior at a real device density so the document is also a specimen of
   * the case worth distinguishing: dp-typed geometry whose density is nonetheless *not* baked in.
   */
  public fun hostDensityText(): RcDocument {
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    val divide = RcFloatExpressionEvaluator.operatorWord(RcFloatExpressionEvaluator.OFFSET + 4)
    val multiply = RcFloatExpressionEvaluator.operatorWord(RcFloatExpressionEvaluator.OFFSET + 3)
    val operations =
      listOf<RcOperation>(
        RcTextData(LABEL_ID, "Deferred density"),
        RcFloatExpression(
          id = HOST_TEXT_SIZE_ID,
          expression =
            listOf(
              reference(RcSystemVariables.FONT_SIZE),
              RcFloatWord.literal(DEFAULT_FONT_SIZE_SP),
              divide,
              reference(RcSystemVariables.DENSITY),
              divide,
              RcFloatWord.literal(HOST_TEXT_SIZE_SP),
              multiply,
            ),
          animation = null,
        ),
        RcRootLayout(1),
        RcLayoutContent(2),
        RcColumnLayout(3, 0, 1, 4, RcFloatWord.literal(0f)),
        background(1f, 1f, 1f),
        padding(16f),
        width(WIDTH.toFloat()),
        height(HOST_DENSITY_HEIGHT.toFloat()),
        RcLayoutContent(4),
        RcTextLayout(
          componentId = 5,
          animationId = 0,
          textId = LABEL_ID,
          color = 0xff202124.toInt(),
          // The whole point: a size the player has to resolve, not a constant it can read.
          fontSize = reference(HOST_TEXT_SIZE_ID),
          fontStyle = 0,
          fontWeight = RcFloatWord.literal(400f),
          fontFamilyId = -1,
          textAlignAndFlags = RcTextLayout.ALIGN_LEFT,
          overflow = RcTextLayout.OVERFLOW_CLIP,
          maxLines = 1,
        ),
        // Five containers are opened above — root, content, column, content, text — so five close.
        // Shipping four left the document unclosed, which every player rejects at decode.
        end,
      ) + List(4) { end }
    return RcDocument(
      RcHeader(
        RcVersion(1, 0, 0),
        properties =
          listOf(
            RcHeaderProperty(RcHeader.DOC_WIDTH, RcHeaderValue.IntValue(WIDTH)),
            RcHeaderProperty(RcHeader.DOC_HEIGHT, RcHeaderValue.IntValue(HOST_DENSITY_HEIGHT)),
            RcHeaderProperty(
              RcHeader.DOC_DENSITY_AT_GENERATION,
              RcHeaderValue.FloatValue(RcFloatWord.literal(CAPTURE_DENSITY)),
            ),
            RcHeaderProperty(
              RcHeader.DOC_DENSITY_BEHAVIOR,
              RcHeaderValue.IntValue(RcHeader.DENSITY_BEHAVIOR_DP),
            ),
          ),
      ),
      operations,
    )
  }

  private fun reference(id: Int) = RcFloatWord(NAN_REFERENCE or id)

  private fun header(height: Int) =
    RcHeader(RcVersion(1, 0, 0), legacyWidth = WIDTH, legacyHeight = height, modern = false)

  private fun label(componentId: Int, textId: Int, size: Float, color: Int): List<RcOperation> =
    listOf(
      RcTextLayout(
        componentId = componentId,
        animationId = 0,
        textId = textId,
        color = color,
        fontSize = RcFloatWord.literal(size),
        fontStyle = 0,
        fontWeight = RcFloatWord.literal(400f),
        fontFamilyId = -1,
        textAlignAndFlags = RcTextLayout.ALIGN_LEFT,
        overflow = RcTextLayout.OVERFLOW_CLIP,
        maxLines = 1,
      ),
      RcNoArg(RcOpcodes.CONTAINER_END),
    )

  private fun width(value: Float) =
    RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(value))

  private fun height(value: Float) =
    RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(value))

  private fun padding(value: Float) =
    RcPaddingModifier(
      RcFloatWord.literal(value),
      RcFloatWord.literal(value),
      RcFloatWord.literal(value),
      RcFloatWord.literal(value),
    )

  private fun background(red: Float, green: Float, blue: Float) =
    RcBackgroundModifier(
      flags = 0,
      colorId = 0,
      reserved1 = 0,
      reserved2 = 0,
      red = RcFloatWord.literal(red),
      green = RcFloatWord.literal(green),
      blue = RcFloatWord.literal(blue),
      alpha = RcFloatWord.literal(1f),
      shapeType = RcBackgroundModifier.SHAPE_RECTANGLE,
    )

  private const val CONFIG_ID = 40
  private const val TEXT_ID = 41
  private const val TERMS_URL_ID = 42
  private const val PRIVACY_URL_ID = 43
  private const val LABEL_ID = 44
  private const val TEXT_COLOR_ID = 50
  private const val HOST_TEXT_SIZE_ID = 70
  private const val SWIFT_NAME_ID = 80
  private const val SWIFT_LEVEL_ID = 81
  private const val SWIFT_TITLE_ID = 82
  private const val SWIFT_SELECTION_ID = 83
  private const val SWIFT_CHART_BASE_ID = 90

  /** The document's authored text size, in `sp`. */
  private const val HOST_TEXT_SIZE_SP = 15f

  /** The `sp` behind `ID_FONT_SIZE`; every player has to agree on it. */
  private const val DEFAULT_FONT_SIZE_SP = 14f

  /** A real device density, recorded but deliberately not folded into the geometry. */
  private const val CAPTURE_DENSITY = 2.2625f

  private const val NAN_REFERENCE = 0x7fc00000
}
