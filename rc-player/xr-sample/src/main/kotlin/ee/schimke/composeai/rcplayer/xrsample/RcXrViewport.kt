package ee.schimke.composeai.rcplayer.xrsample

/**
 * Where the player draws on its panel, in the panel's own metres: the viewport's height, and its
 * centre's offset from the panel's centre (+x right, +y up, as the panel's local space runs).
 *
 * The document's camera maps onto the player's viewport, not onto the whole panel: a panel holds
 * padding and other UI too. Handing meshes off against the panel would scale and shift them
 * relative to the 2D drawing the player still does.
 */
internal data class RcXrViewport(
  val heightMeters: Float,
  val offsetXMeters: Float,
  val offsetYMeters: Float,
) {
  companion object {
    /**
     * From the player's bounds in the panel's pixels ([left], [top], [right], [bottom], with y
     * down) and the panel's size in pixels and metres.
     */
    fun of(
      left: Float,
      top: Float,
      right: Float,
      bottom: Float,
      panelWidthPx: Int,
      panelHeightPx: Int,
      panelWidthMeters: Float,
      panelHeightMeters: Float,
    ): RcXrViewport? {
      if (panelWidthPx <= 0 || panelHeightPx <= 0 || bottom <= top || right <= left) return null
      val metersPerPxX = panelWidthMeters / panelWidthPx
      val metersPerPxY = panelHeightMeters / panelHeightPx
      val centreX = (left + right) / 2f - panelWidthPx / 2f
      val centreY = (top + bottom) / 2f - panelHeightPx / 2f
      return RcXrViewport(
        heightMeters = (bottom - top) * metersPerPxY,
        offsetXMeters = centreX * metersPerPxX,
        offsetYMeters = -centreY * metersPerPxY, // pixels run down, the panel's y runs up
      )
    }
  }
}
