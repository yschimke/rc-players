package ee.schimke.composeai.rcplayer.demos

import ee.schimke.composeai.rcplayer.compose.RcSpannableString
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcColorConstant
import ee.schimke.composeai.rcplayer.protocol.RcColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcCustomLayout
import ee.schimke.composeai.rcplayer.protocol.RcCustomProperty
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcPaddingModifier
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextLayout
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier

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
}
