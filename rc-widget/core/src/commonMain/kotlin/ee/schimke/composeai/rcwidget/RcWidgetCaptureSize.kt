package ee.schimke.composeai.rcwidget

/**
 * The size a launcher widget is captured for, in dp. A launcher host recaptures on every resize
 * (`onAppWidgetOptionsChanged`), and Android 16's widget player does not reliably re-evaluate state
 * layouts driven by component dimensions, so inside a launcher capture the size breakpoints
 * (`rc-widget-creation`'s `RcSizeBreakpoint`) pick their layout from this size at capture time
 * instead of recording a ladder.
 *
 * One type for both creation bindings: `rc-widget-creation` and `rc-widget-creation-androidx` each
 * name it as a typealias in their own package (`...rcwidget.creation.RcWidgetCaptureSize`,
 * `...rcwidget.creation.androidx.RcWidgetCaptureSize`), and `rc-widget-launcher-core`'s sizing
 * returns it, so one slot can be captured through either.
 */
public data class RcWidgetCaptureSize(val widthDp: Int, val heightDp: Int)
