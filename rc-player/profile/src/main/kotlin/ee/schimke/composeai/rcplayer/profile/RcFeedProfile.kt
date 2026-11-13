package ee.schimke.composeai.rcplayer.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ee.schimke.composeai.rcplayer.compose.LocalRcDeferLayoutAnimations
import ee.schimke.composeai.rcplayer.compose.RcComposePlayer
import ee.schimke.composeai.rcplayer.compose.RcPreparedDocument
import ee.schimke.composeai.rcplayer.edit.forDisplay
import ee.schimke.composeai.rcplayer.edit.namedVariables
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.runtime.RcNamedValue
import java.io.File
import kotlin.math.ceil

/**
 * How a host hands a feed's cards to the player. Every card of a feed is the same template with
 * different values, so the modes differ only in how much of the template's preparation each new
 * card repeats.
 */
public enum class RcFeedMode(public val description: String) {
  /** Each card decodes the template's bytes itself: decode, link, layout tree, state. */
  Bytes("bytes per card (decode + link + layout + state)"),
  /** One decoded document shared by every card: link, layout tree and state per card. */
  Document("shared RcDocument (link + layout + state)"),
  /** One prepared document shared by every card: only the state per card. */
  Prepared("shared RcPreparedDocument (state only)"),
  /**
   * [Prepared], under a host that lets players defer the lookahead pass behind layout animations
   * until a card's layout can first change (`LocalRcDeferLayoutAnimations`), as a feed does.
   */
  Deferred("shared RcPreparedDocument, layout animations deferred"),
  /**
   * No player at all: [RcReferenceCard], a session-shaped card written directly in Compose UI
   * (foundation), as the bar a player card has to beat.
   */
  ComposeUi("Compose UI reference card (no player)"),
  /**
   * One prepared document, edited first as a host serving one display would (`rc-player-edit`):
   * density and font scale hardcoded, named values no card binds frozen, constant state layouts
   * collapsed and the result optimized. Every `USER:` value stays bound.
   */
  Edited("shared, edited for this display (density baked, optimized)"),
  /** As [Edited], with the theme (`theme.*`, the same for every card of a screen) frozen too. */
  EditedTheme("shared, edited, theme baked too"),
}

/** One mode's frame times over a simulated fling. */
public data class RcFeedResult(
  val template: String,
  val mode: RcFeedMode,
  /** Wall time of each frame that brought [arrivalsPerFrame] new cards on screen, in nanos. */
  val frameNanos: List<Long>,
  val arrivalsPerFrame: Int,
) {
  val meanMicros: Double
    get() = frameNanos.average() / 1_000.0

  public fun percentileMicros(p: Double): Double {
    val sorted = frameNanos.sorted()
    return sorted[(ceil(p * sorted.size).toInt() - 1).coerceIn(0, sorted.lastIndex)] / 1_000.0
  }

  val perCardMicros: Double
    get() = meanMicros / arrivalsPerFrame
}

/**
 * A fling through a feed, without a list: a window of [visible] cards, each frame scrolled on by
 * [arrivalsPerFrame] cards that compose for the first time and as many that leave. Each frame's
 * wall time (composition, layout and a software draw of the whole window) is what a card arriving
 * costs, beside the cards that were already there.
 *
 * It is deliberately not a `LazyColumn`: prefetch and slot reuse would hide the per-card cost this
 * measures, and they are the platform's business, not the player's. A real fling is measured on a
 * device (`FrameTimingMetric`), where this number is what prefetch has to fit into idle time.
 */
public class RcFeedProfile(
  private val visible: Int = 4,
  private val arrivalsPerFrame: Int = 2,
  private val frames: Int = 300,
  private val warmupFrames: Int = 60,
  /** The modes to run; all by default (`-Prc.feed.modes=Prepared,ComposeUi` narrows it). */
  private val modes: List<RcFeedMode> =
    System.getProperty("rc.feed.modes")?.split(',')?.map { RcFeedMode.valueOf(it.trim()) }
      ?: RcFeedMode.entries,
) {
  /**
   * Every mode over every template. All of them are warmed first and the modes then interleave over
   * [rounds], rotating their order, so no mode pays for JIT or a busy moment on the others' behalf.
   */
  public fun run(templates: List<Pair<String, ByteArray>>, rounds: Int = 5): List<RcFeedResult> {
    for ((_, bytes) in templates) modes.forEach { play(bytes, it, warmupFrames) }
    val samples = mutableMapOf<Pair<String, RcFeedMode>, MutableList<Long>>()
    repeat(rounds) { round ->
      for ((name, bytes) in templates) {
        modes.indices.forEach { i ->
          val mode = modes[(i + round) % modes.size]
          samples.getOrPut(name to mode) { mutableListOf() } += play(bytes, mode, frames / rounds)
        }
      }
    }
    return templates.flatMap { (name, _) ->
      modes.map { RcFeedResult(name, it, samples.getValue(name to it), arrivalsPerFrame) }
    }
  }

  private fun play(bytes: ByteArray, mode: RcFeedMode, frames: Int): List<Long> {
    // Built once, as a host's template cache would: the cost each mode leaves per card is the
    // point.
    val document = RcDocumentCodec.decode(bytes)
    val prepared = RcPreparedDocument(document)
    val edited =
      when (mode) {
        RcFeedMode.Edited -> RcPreparedDocument(document.editedFor(bindTheme = true))
        RcFeedMode.EditedTheme -> RcPreparedDocument(document.editedFor(bindTheme = false))
        else -> prepared
      }
    var first by mutableIntStateOf(0)
    val scene =
      ImageComposeScene(width = CARD_WIDTH, height = CARD_HEIGHT * visible, density = Density(1f)) {
        Column {
          for (item in first until first + visible) {
            key(item) {
              // A card's own data. Undeclared names are ignored, as AndroidX ignores them, so the
              // map costs what a host's map costs whichever template plays.
              val values = remember {
                mutableStateMapOf<String, RcNamedValue>(
                  "USER:title" to RcNamedValue.Text("Item $item"),
                  "USER:index" to RcNamedValue.Integer(item),
                  // A Livery session card's inputs, by their contract names.
                  "title" to
                    RcNamedValue.Text("Session $item: building adaptive apps with Compose"),
                  "speakers" to RcNamedValue.Text("Jane Doe, John Smith"),
                )
              }
              val modifier = Modifier.size(CARD_WIDTH.dp, CARD_HEIGHT.dp)
              when (mode) {
                RcFeedMode.Bytes -> RcComposePlayer(bytes, modifier, namedValues = values)
                RcFeedMode.Document -> RcComposePlayer(document, modifier, namedValues = values)
                RcFeedMode.Prepared -> RcComposePlayer(prepared, modifier, namedValues = values)
                RcFeedMode.Deferred ->
                  CompositionLocalProvider(LocalRcDeferLayoutAnimations provides true) {
                    RcComposePlayer(prepared, modifier, namedValues = values)
                  }
                RcFeedMode.ComposeUi -> RcReferenceCard(item, modifier)
                RcFeedMode.Edited,
                RcFeedMode.EditedTheme -> RcComposePlayer(edited, modifier, namedValues = values)
              }
            }
          }
        }
      }
    try {
      var time = 0L
      scene.render(time)
      return List(frames) {
        first += arrivalsPerFrame
        time += FRAME_INTERVAL_NANOS
        val start = System.nanoTime()
        scene.render(time)
        System.nanoTime() - start
      }
    } finally {
      scene.close()
    }
  }

  public companion object {
    public const val CARD_WIDTH: Int = 360
    public const val CARD_HEIGHT: Int = 160
    private const val FRAME_INTERVAL_NANOS = 16_666_667L

    public fun report(
      results: List<RcFeedResult>,
      environment: List<Pair<String, String>>,
    ): String = buildString {
      appendLine("# Feed profile: the cost of a card arriving")
      appendLine()
      environment.forEach { (k, v) -> appendLine("- **$k** — $v") }
      appendLine()
      results
        .groupBy { it.template }
        .forEach { (template, rows) ->
          val baseline = rows.first().meanMicros
          appendLine("## `$template`")
          appendLine()
          appendLine(
            "${rows.first().frameNanos.size} frames, ${rows.first().arrivalsPerFrame} new cards each.",
          )
          appendLine()
          appendLine(
            "| Mode | Frame mean | p50 | p95 | Per new card | vs ${rows.first().mode.name} |",
          )
          appendLine("| --- | ---: | ---: | ---: | ---: | ---: |")
          rows.forEach { r ->
            appendLine(
              "| ${r.mode.description} | ${us(r.meanMicros)} | ${us(r.percentileMicros(0.5))} | " +
                "${us(r.percentileMicros(0.95))} | ${us(r.perCardMicros)} | " +
                "${"%.0f".format(100 * r.meanMicros / baseline)}% |",
            )
          }
          appendLine()
        }
    }

    private fun us(micros: Double): String =
      if (micros >= 1_000) "%.2f ms".format(micros / 1_000) else "%.0f µs".format(micros)
  }
}

/**
 * `rcPlayerFeedProfile`: [RcFeedProfile] over card templates from the catalog corpus. Arguments:
 * the output directory, then `.rc` files to profile.
 */
public object RcFeedProfileMain {
  @JvmStatic
  public fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "build/profile").also { it.mkdirs() }
    val templates = args.drop(1).map(::File)
    require(templates.isNotEmpty()) { "name at least one .rc template to profile" }
    val profile = RcFeedProfile()
    val results = profile.run(templates.map { it.nameWithoutExtension to it.readBytes() })
    val environment =
      listOf(
        "JVM" to "${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}",
        "OS" to "${System.getProperty("os.name")} ${System.getProperty("os.arch")}",
        "CPUs" to Runtime.getRuntime().availableProcessors().toString(),
        "Renderer" to "Compose Desktop ImageComposeScene (skiko software raster), density 1",
      )
    val report = RcFeedProfile.report(results, environment)
    File(out, "feed.md").writeText(report)
    println(report)
  }
}

/**
 * This document as a host serving one display at density 1 plays it: see [RcFeedMode.Edited]. With
 * [bindTheme] false the `theme.*` values are frozen at their recorded values, as a host that bakes
 * the screen's theme in would freeze them at its own.
 */
internal fun RcDocument.editedFor(bindTheme: Boolean): RcDocument =
  if (bindTheme) forDisplay(density = 1f)
  else
    forDisplay(
      density = 1f,
      bound =
        namedVariables
          .map { it.name }
          .filter { it.startsWith("USER:") && !it.startsWith("USER:theme.") }
          .toSet(),
    )
