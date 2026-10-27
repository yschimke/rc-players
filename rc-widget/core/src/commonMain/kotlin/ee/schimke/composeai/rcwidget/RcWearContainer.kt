package ee.schimke.composeai.rcwidget

import ee.schimke.composeai.rcplayer.edit.RcHardcodedValue
import ee.schimke.composeai.rcplayer.edit.namedVariables
import ee.schimke.composeai.rcplayer.protocol.RcDocument

/**
 * The named values a Wear widget template leaves to the watch: the widget container's padding and
 * corner radius (dp) and its fill colour.
 *
 * A Wear widget's container — its size, padding and corner radius — belongs to the watch's widget
 * host and differs by device: a Pixel Watch's Large container is 200×108 dp with 26 dp corners, a
 * Galaxy Watch's a stadium with 35 dp side insets. Glance Wear's own container writes the host's
 * values into a document as constants at capture time, so a document captured for one host keeps
 * its shape on another. A template declares them as these `USER` named values instead, each
 * defaulting to a Large round container, and [RcWearTemplates.instantiate] writes in the values of
 * the host it plays on: one capture fits every host.
 *
 * These names are a contract between whoever captures a template (an app, a design server, a UI
 * design exported with the container's `hostFrame: device`) and whoever instantiates it.
 */
public object RcWearContainer {
  public const val HORIZONTAL_PADDING: String = "wear.container.horizontal_padding"
  public const val VERTICAL_PADDING: String = "wear.container.vertical_padding"
  public const val CORNER_RADIUS: String = "wear.container.corner_radius"

  /** The container's fill, which a host may write per widget (a state tint). */
  public const val COLOR: String = "wear.container.color"

  /** The frame values, without [COLOR]. */
  public val FRAME_NAMES: Set<String> = setOf(HORIZONTAL_PADDING, VERTICAL_PADDING, CORNER_RADIUS)

  /** A Large round container's padding: what a template defaults to. */
  public const val DEFAULT_PADDING_DP: Float = 8f

  /** A Large round container's corner radius: what a template defaults to. */
  public const val DEFAULT_CORNER_RADIUS_DP: Float = 26f

  /**
   * The colour androidx's `WearWidgetContainer` fills for an empty brush, Wear Material 3's dark
   * `surfaceContainerLow`.
   */
  public const val DEFAULT_SURFACE_ARGB: Int = 0xFF272430.toInt()

  /**
   * Whether [document] leaves its container frame to the watch: it declares one of the frame's
   * named values.
   */
  public fun namesFrame(document: RcDocument): Boolean =
    document.namedVariables.any {
      it.name.removePrefix(RcBindingContract.USER_DOMAIN) in FRAME_NAMES
    }
}

/**
 * A Wear widget container size class, with Glance Wear's `ContainerInfo.CONTAINER_TYPE_*` value
 * ([glanceType]: Large is 1, Small 2).
 */
public enum class RcWearContainerType(public val glanceType: Int) {
  SMALL(2),
  LARGE(1);

  public companion object {
    public fun ofGlance(type: Int): RcWearContainerType? = entries.firstOrNull {
      it.glanceType == type
    }
  }
}

/**
 * A Wear widget container's frame on one host, in dp: its content size, padding and corner radius,
 * as the host reports them (Glance Wear's `WearWidgetParams`).
 */
public data class RcWearFrame(
  val type: RcWearContainerType,
  val widthDp: Float,
  val heightDp: Float,
  val horizontalPaddingDp: Float,
  val verticalPaddingDp: Float,
  val cornerRadiusDp: Float,
) {
  /** The whole container, padding included. */
  val outerWidthDp: Float
    get() = widthDp + 2f * horizontalPaddingDp

  val outerHeightDp: Float
    get() = heightDp + 2f * verticalPaddingDp

  /**
   * This frame as the named values a template reads ([RcWearContainer]), with the container filled
   * in [containerArgb]; null leaves the fill the template's own.
   */
  public fun namedValues(containerArgb: Int? = null): Map<String, RcHardcodedValue> = buildMap {
    put(RcWearContainer.HORIZONTAL_PADDING, RcHardcodedValue.FloatValue(horizontalPaddingDp))
    put(RcWearContainer.VERTICAL_PADDING, RcHardcodedValue.FloatValue(verticalPaddingDp))
    put(RcWearContainer.CORNER_RADIUS, RcHardcodedValue.FloatValue(cornerRadiusDp))
    containerArgb?.let { put(RcWearContainer.COLOR, RcHardcodedValue.Color(it)) }
  }
}

/**
 * A Wear widget host's containers, for previews and for an app that draws its cards as the watch's
 * widgets will before any widget has been placed.
 */
public data class RcWearHost(val name: String, val small: RcWearFrame, val large: RcWearFrame) {
  public fun frame(type: RcWearContainerType): RcWearFrame =
    when (type) {
      RcWearContainerType.SMALL -> small
      RcWearContainerType.LARGE -> large
    }

  public companion object {
    /** Pixel Watch: a squircle, 8 dp in from the edge, 26 dp corners. */
    public val Pixel: RcWearHost =
      RcWearHost(
        "Pixel",
        RcWearFrame(RcWearContainerType.SMALL, 200f, 60f, 8f, 8f, 26f),
        RcWearFrame(RcWearContainerType.LARGE, 200f, 108f, 8f, 8f, 26f),
      )

    /** Samsung Galaxy Watch: a stadium, its Large container narrower and taller. */
    public val Galaxy: RcWearHost =
      RcWearHost(
        "Galaxy",
        RcWearFrame(RcWearContainerType.SMALL, 200f, 60f, 15f, 8f, 999f),
        RcWearFrame(RcWearContainerType.LARGE, 160f, 136f, 35f, 16f, 999f),
      )

    /**
     * Glance Wear's own preview values for a round watch: what a host that has reported nothing yet
     * is assumed to be.
     */
    public val Default: RcWearHost =
      RcWearHost(
        "Default",
        RcWearFrame(RcWearContainerType.SMALL, 200f, 60f, 8f, 8f, 26f),
        RcWearFrame(RcWearContainerType.LARGE, 200f, 112f, 8f, 8f, 26f),
      )

    public val All: List<RcWearHost> = listOf(Pixel, Galaxy)
  }
}
