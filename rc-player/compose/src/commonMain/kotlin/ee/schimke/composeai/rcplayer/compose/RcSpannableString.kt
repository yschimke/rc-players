package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * The `SupportSpannableString` custom component: a run of text carrying link spans.
 *
 * Remote Compose has no span-annotated text operation. A document that needs one — terms of service
 * with two clickable clauses, a caption whose middle word opens a URL — authors a `Custom`
 * component with this config name and lets the host draw it, which is what AndroidX's embedded
 * player does ("Add Custom operation and SupportSpannableString support to embedded player",
 * androidx-main `27f14858b16`). This is the same contract on this player: the property ids below
 * are upstream's, so a document authored for either renders on both.
 *
 * It is opt-in, like every custom component. A host that wants it registers [renderer]:
 * ```
 * RcComposePlayer(
 *   document,
 *   customComponents = RcCustomComponentRegistry(RcSpannableString.CONFIG to RcSpannableString.renderer),
 * )
 * ```
 *
 * A document whose component the registry does not carry draws nothing and is reported by
 * `composeSupportReport`, so registering is a decision the host makes rather than a default it
 * inherits.
 *
 * Links are ordinary Compose [LinkAnnotation.Url] annotations, so the platform opens them: the
 * browser follows the URL in the Wasm host, and the JVM and iOS hosts hand it to the system
 * handler. A span whose range is empty or reversed, or whose URL is blank, is dropped rather than
 * being drawn as an unclickable decoration.
 */
public object RcSpannableString {
  /** The document-authored config name this renderer answers to. */
  public const val CONFIG: String = "SupportSpannableString"

  /** The text itself (`STRING_PROP`). */
  public const val PROP_TEXT: Int = 1

  /** Colour of the whole run — literal, or a colour id so a theme can move it. */
  public const val PROP_TEXT_COLOR: Int = 2

  /** Font size in sp; a value of zero or less leaves the inherited size alone. */
  public const val PROP_TEXT_SIZE: Int = 3

  /** How many link spans follow, at the three bases below. */
  public const val PROP_LINK_COUNT: Int = 10

  /** `PROP_LINK_URL_BASE + i` is link *i*'s URL (`STRING_PROP`). */
  public const val PROP_LINK_URL_BASE: Int = 1000

  /** `PROP_LINK_START_BASE + i` is link *i*'s first character index. */
  public const val PROP_LINK_START_BASE: Int = 2000

  /** `PROP_LINK_END_BASE + i` is link *i*'s end index, exclusive. */
  public const val PROP_LINK_END_BASE: Int = 3000

  /** The style links are drawn in. Underlined blue is what every other player draws. */
  public val LinkStyle: SpanStyle =
    SpanStyle(
      color = Color(0xff1a73e8.toInt()),
      textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
    )

  /** Builds the annotated string a component describes, without drawing it. */
  public fun annotate(component: RcCustomComponent): AnnotatedString {
    val text = component.text(PROP_TEXT)
    val color =
      component.color(PROP_TEXT_COLOR).let { if (it == 0) Color.Unspecified else Color(it) }
    val size = component.float(PROP_TEXT_SIZE).let { if (it > 0f) it.sp else TextUnit.Unspecified }
    return buildAnnotatedString {
      append(text)
      if (color != Color.Unspecified || size != TextUnit.Unspecified) {
        addStyle(SpanStyle(color = color, fontSize = size), 0, text.length)
      }
      repeat(component.integer(PROP_LINK_COUNT)) { index ->
        val url = component.text(PROP_LINK_URL_BASE + index)
        val start = component.integer(PROP_LINK_START_BASE + index).coerceIn(0, text.length)
        val end = component.integer(PROP_LINK_END_BASE + index).coerceIn(0, text.length)
        if (start < end && url.isNotEmpty()) {
          addStyle(LinkStyle, start, end)
          addLink(LinkAnnotation.Url(url), start, end)
        }
      }
    }
  }

  /** The host content to register under [CONFIG]. */
  public val renderer: RcCustomContent = { component, modifier ->
    BasicText(text = annotate(component), modifier = modifier)
  }
}
