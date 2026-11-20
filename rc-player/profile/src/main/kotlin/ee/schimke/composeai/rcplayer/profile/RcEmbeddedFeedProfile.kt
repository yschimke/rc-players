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
import ee.schimke.composeai.rcplayer.compose.LocalRcEmbeddedDocuments
import ee.schimke.composeai.rcplayer.compose.RcComposePlayer
import ee.schimke.composeai.rcplayer.compose.RcDocumentResolver
import ee.schimke.composeai.rcplayer.compose.RcEmbeddedDocuments
import ee.schimke.composeai.rcplayer.compose.RcPreparedDocument
import ee.schimke.composeai.rcplayer.compose.RcPreparedDocumentCache
import ee.schimke.composeai.rcplayer.edit.inlineEmbeddedDocuments
import ee.schimke.composeai.rcplayer.edit.optimize
import ee.schimke.composeai.rcplayer.protocol.RcBackgroundModifier
import ee.schimke.composeai.rcplayer.protocol.RcBinaryData
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcColumnLayout
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocLayout
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcMapRcDocVariables
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcPaddingModifier
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcRowLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcTextLayout
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import ee.schimke.composeai.rcplayer.runtime.RcNamedValue
import java.io.File
import kotlin.math.ceil

/**
 * Modes compared by [RcEmbeddedFeedProfile]: a scrolling feed of cards that embed a child `.rc`
 * document (`LAYOUT_RC_DOC`) against an equivalent flat card with no nesting.
 */
public enum class RcEmbeddedFeedMode(public val description: String) {
  /** Equivalent single flat `RcPreparedDocument` (baseline, no embedded doc). */
  FlatPrepared("flat card, shared RcPreparedDocument (baseline)"),
  /** Inline `BinaryData(TYPE_RC_DOC)`, decoded from bytes on every arriving card. */
  InlineBytes("embedded inline (bytes per card)"),
  /**
   * Inline `BinaryData(TYPE_RC_DOC)`, shared parent `RcPreparedDocument` (child cached on parent).
   */
  InlinePrepared("embedded inline (shared RcPreparedDocument)"),
  /** [InlinePrepared] with `LocalRcDeferLayoutAnimations` enabled. */
  InlineDeferred("embedded inline (shared, animations deferred)"),
  /** Inline `BinaryData(TYPE_RC_DOC)` statically flattened via `inlineEmbeddedDocuments()`. */
  InlineFlattened("embedded inline, statically inlined (rc-player-edit)"),
  /**
   * URL-resolved via `RcDocumentResolver` with no `RcPreparedDocumentCache` (decodes child per
   * card).
   */
  UrlUncached("embedded URL (resolver, uncached child)"),
  /** URL-resolved via `RcDocumentResolver` with shared `RcPreparedDocumentCache`. */
  UrlCached("embedded URL (resolver + RcPreparedDocumentCache)"),
  /** [UrlCached] with `LocalRcDeferLayoutAnimations` enabled. */
  UrlDeferred("embedded URL (cached, animations deferred)"),
  /** Inline embedded document played with default `LocalRcEmbeddedDocuments.Disabled`. */
  Disabled("embedded inline (feature disabled)"),
}

/** One mode's frame times over a simulated feed fling with embedded documents. */
public data class RcEmbeddedFeedResult(
  val mode: RcEmbeddedFeedMode,
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
 * Benchmarks a scrolling feed of cards that compose a child `.rc` document via `LAYOUT_RC_DOC` and
 * forward per-card values via `MAP_RC_DOC_VARIABLES`, alongside an equivalent flat card baseline.
 */
public class RcEmbeddedFeedProfile(
  private val visible: Int = 4,
  private val arrivalsPerFrame: Int = 2,
  private val frames: Int = 240,
  private val warmupFrames: Int = 48,
  private val modes: List<RcEmbeddedFeedMode> =
    System.getProperty("rc.embeddedFeed.modes")?.split(',')?.map {
      RcEmbeddedFeedMode.valueOf(it.trim())
    } ?: RcEmbeddedFeedMode.entries,
) {
  private val bundle = RcEmbeddedFeedDocuments.build()

  public fun run(rounds: Int = 4): List<RcEmbeddedFeedResult> {
    modes.forEach { play(it, warmupFrames) }
    val samples = mutableMapOf<RcEmbeddedFeedMode, MutableList<Long>>()
    repeat(rounds) { round ->
      modes.indices.forEach { i ->
        val mode = modes[(i + round) % modes.size]
        samples.getOrPut(mode) { mutableListOf() } += play(mode, frames / rounds)
      }
    }
    return modes.map { RcEmbeddedFeedResult(it, samples.getValue(it), arrivalsPerFrame) }
  }

  private fun play(mode: RcEmbeddedFeedMode, frames: Int): List<Long> {
    val flatPrepared = RcPreparedDocument.decode(bundle.flatCardBytes)
    val inlinePrepared =
      RcPreparedDocument.decode(bundle.inlineCardBytes).apply { prepareEmbeddedDocuments() }
    val flattenedPrepared =
      RcPreparedDocument(
        RcDocumentCodec.decode(bundle.inlineCardBytes).inlineEmbeddedDocuments().optimize(),
      )
    val urlPrepared = RcPreparedDocument.decode(bundle.urlCardBytes)
    val resolver = RcDocumentResolver { url ->
      if (url == RcEmbeddedFeedDocuments.CHILD_URL) bundle.childBytes else null
    }
    val cache = RcPreparedDocumentCache()

    var first by mutableIntStateOf(0)
    val scene =
      ImageComposeScene(
        width = RcFeedProfile.CARD_WIDTH,
        height = RcFeedProfile.CARD_HEIGHT * visible,
        density = Density(1f),
      ) {
        val embeddedConfig =
          when (mode) {
            RcEmbeddedFeedMode.FlatPrepared,
            RcEmbeddedFeedMode.InlineFlattened,
            RcEmbeddedFeedMode.Disabled -> RcEmbeddedDocuments.Disabled
            RcEmbeddedFeedMode.InlineBytes,
            RcEmbeddedFeedMode.InlinePrepared,
            RcEmbeddedFeedMode.InlineDeferred -> RcEmbeddedDocuments()
            RcEmbeddedFeedMode.UrlUncached -> RcEmbeddedDocuments(resolver = resolver)
            RcEmbeddedFeedMode.UrlCached,
            RcEmbeddedFeedMode.UrlDeferred ->
              RcEmbeddedDocuments(resolver = resolver, cache = cache)
          }
        val deferAnimations =
          mode == RcEmbeddedFeedMode.InlineDeferred || mode == RcEmbeddedFeedMode.UrlDeferred

        CompositionLocalProvider(
          LocalRcEmbeddedDocuments provides embeddedConfig,
          LocalRcDeferLayoutAnimations provides deferAnimations,
        ) {
          Column {
            for (item in first until first + visible) {
              key(item) {
                val values = remember {
                  mutableStateMapOf<String, RcNamedValue>(
                    "USER:title" to
                      RcNamedValue.Text("Session $item: building adaptive apps with Compose"),
                    "USER:badge" to RcNamedValue.Text("Track ${item % 4 + 1}"),
                  )
                }
                val modifier =
                  Modifier.size(RcFeedProfile.CARD_WIDTH.dp, RcFeedProfile.CARD_HEIGHT.dp)
                when (mode) {
                  RcEmbeddedFeedMode.FlatPrepared ->
                    RcComposePlayer(flatPrepared, modifier, namedValues = values)
                  RcEmbeddedFeedMode.InlineBytes ->
                    RcComposePlayer(bundle.inlineCardBytes, modifier, namedValues = values)
                  RcEmbeddedFeedMode.InlinePrepared,
                  RcEmbeddedFeedMode.InlineDeferred,
                  RcEmbeddedFeedMode.Disabled ->
                    RcComposePlayer(inlinePrepared, modifier, namedValues = values)
                  RcEmbeddedFeedMode.InlineFlattened ->
                    RcComposePlayer(flattenedPrepared, modifier, namedValues = values)
                  RcEmbeddedFeedMode.UrlUncached,
                  RcEmbeddedFeedMode.UrlCached,
                  RcEmbeddedFeedMode.UrlDeferred ->
                    RcComposePlayer(urlPrepared, modifier, namedValues = values)
                }
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
    private const val FRAME_INTERVAL_NANOS = 16_666_667L

    public fun report(
      results: List<RcEmbeddedFeedResult>,
      environment: List<Pair<String, String>>,
    ): String = buildString {
      appendLine("# Embedded documents feed profile: docs within docs")
      appendLine()
      environment.forEach { (k, v) -> appendLine("- **$k** — $v") }
      appendLine()
      val baseline = results.first().meanMicros
      appendLine(
        "${results.first().frameNanos.size} frames, ${results.first().arrivalsPerFrame} new cards each.",
      )
      appendLine()
      appendLine(
        "| Mode | Frame mean | p50 | p95 | Per new card | vs ${results.first().mode.name} |",
      )
      appendLine("| --- | ---: | ---: | ---: | ---: | ---: |")
      results.forEach { r ->
        appendLine(
          "| ${r.mode.description} | ${us(r.meanMicros)} | ${us(r.percentileMicros(0.5))} | " +
            "${us(r.percentileMicros(0.95))} | ${us(r.perCardMicros)} | " +
            "${"%.0f".format(100 * r.meanMicros / baseline)}% |",
        )
      }
      appendLine()
    }

    private fun us(micros: Double): String =
      if (micros >= 1_000) "%.2f ms".format(micros / 1_000) else "%.0f µs".format(micros)
  }
}

/** Synthetic feed-card documents used by [RcEmbeddedFeedProfile]. */
public data class RcEmbeddedFeedDocumentBundle(
  val childBytes: ByteArray,
  val flatCardBytes: ByteArray,
  val inlineCardBytes: ByteArray,
  val urlCardBytes: ByteArray,
)

public object RcEmbeddedFeedDocuments {
  public const val CHILD_URL: String = "rc://feed/badge"

  public fun build(): RcEmbeddedFeedDocumentBundle {
    val childDoc = buildChildBadgeDoc()
    val childBytes = RcDocumentCodec.encode(childDoc)
    val flatCardBytes = RcDocumentCodec.encode(buildFlatCardDoc())
    val inlineCardBytes =
      RcDocumentCodec.encode(buildEmbeddedCardDoc(inlineChildBytes = childBytes))
    val urlCardBytes = RcDocumentCodec.encode(buildEmbeddedCardDoc(inlineChildBytes = null))
    return RcEmbeddedFeedDocumentBundle(
      childBytes = childBytes,
      flatCardBytes = flatCardBytes,
      inlineCardBytes = inlineCardBytes,
      urlCardBytes = urlCardBytes,
    )
  }

  private fun bg(r: Float, g: Float, b: Float, a: Float = 1f): RcBackgroundModifier =
    RcBackgroundModifier(
      flags = 0,
      colorId = 0,
      reserved1 = 0,
      reserved2 = 0,
      red = RcFloatWord.literal(r),
      green = RcFloatWord.literal(g),
      blue = RcFloatWord.literal(b),
      alpha = RcFloatWord.literal(a),
      shapeType = 0,
    )

  private fun pad(left: Float, top: Float, right: Float, bottom: Float): RcPaddingModifier =
    RcPaddingModifier(
      RcFloatWord.literal(left),
      RcFloatWord.literal(top),
      RcFloatWord.literal(right),
      RcFloatWord.literal(bottom),
    )

  private fun buildChildBadgeDoc(): RcDocument =
    RcDocument(
      header = RcHeader(RcVersion(1, 1, 0), legacyWidth = 120, legacyHeight = 32),
      operations =
        listOf(
          RcTextData(id = 42, text = "Track 1"),
          RcNamedVariable(id = 42, type = RcNamedVariable.STRING_TYPE, name = "USER:label"),
          RcRootLayout(componentId = 1),
          RcLayoutContent(componentId = 2),
          RcBoxLayout(
            componentId = 3,
            animationId = -1,
            horizontalPositioning = 2,
            verticalPositioning = 2,
          ),
          RcWidthModifier(RcDimensionType.FILL, RcFloatWord.literal(1f)),
          RcHeightModifier(RcDimensionType.FILL, RcFloatWord.literal(1f)),
          bg(0.78f, 0.79f, 0.84f),
          pad(8f, 4f, 8f, 4f),
          RcLayoutContent(componentId = 4),
          RcTextLayout(
            componentId = 5,
            animationId = -1,
            textId = 42,
            color = 0xFF1F6A80.toInt(),
            fontSize = RcFloatWord.literal(13f),
            fontStyle = 0,
            fontWeight = RcFloatWord.literal(600f),
            fontFamilyId = -1,
            textAlignAndFlags = RcTextLayout.ALIGN_LEFT,
            overflow = 1,
            maxLines = 1,
          ),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
        ),
    )

  private fun buildFlatCardDoc(): RcDocument =
    RcDocument(
      header =
        RcHeader(
          RcVersion(1, 1, 0),
          legacyWidth = RcFeedProfile.CARD_WIDTH,
          legacyHeight = RcFeedProfile.CARD_HEIGHT,
        ),
      operations =
        listOf(
          RcTextData(id = 42, text = "Session title"),
          RcNamedVariable(id = 42, type = RcNamedVariable.STRING_TYPE, name = "USER:title"),
          RcTextData(id = 43, text = "Track 1"),
          RcNamedVariable(id = 43, type = RcNamedVariable.STRING_TYPE, name = "USER:badge"),
          RcTextData(id = 44, text = "Jane Doe, John Smith"),
          RcRootLayout(componentId = 1),
          RcLayoutContent(componentId = 2),
          RcColumnLayout(
            componentId = 3,
            animationId = -1,
            horizontalPositioning = 0,
            verticalPositioning = 0,
            spacedBy = RcFloatWord.literal(8f),
          ),
          RcWidthModifier(RcDimensionType.FILL, RcFloatWord.literal(1f)),
          RcHeightModifier(RcDimensionType.FILL, RcFloatWord.literal(1f)),
          bg(0.94f, 0.87f, 0.90f),
          pad(16f, 16f, 16f, 16f),
          RcLayoutContent(componentId = 4),
          RcRowLayout(
            componentId = 5,
            animationId = -1,
            horizontalPositioning = 0,
            verticalPositioning = 2,
            spacedBy = RcFloatWord.literal(8f),
          ),
          RcWidthModifier(RcDimensionType.FILL, RcFloatWord.literal(1f)),
          RcLayoutContent(componentId = 6),
          RcBoxLayout(
            componentId = 10,
            animationId = -1,
            horizontalPositioning = 2,
            verticalPositioning = 2,
          ),
          RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(120f)),
          RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(32f)),
          bg(0.78f, 0.79f, 0.84f),
          pad(8f, 4f, 8f, 4f),
          RcLayoutContent(componentId = 11),
          RcTextLayout(
            componentId = 12,
            animationId = -1,
            textId = 43,
            color = 0xFF1F6A80.toInt(),
            fontSize = RcFloatWord.literal(13f),
            fontStyle = 0,
            fontWeight = RcFloatWord.literal(600f),
            fontFamilyId = -1,
            textAlignAndFlags = RcTextLayout.ALIGN_LEFT,
            overflow = 1,
            maxLines = 1,
          ),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcTextLayout(
            componentId = 20,
            animationId = -1,
            textId = 42,
            color = 0xFF1E1A1D.toInt(),
            fontSize = RcFloatWord.literal(18f),
            fontStyle = 0,
            fontWeight = RcFloatWord.literal(700f),
            fontFamilyId = -1,
            textAlignAndFlags = RcTextLayout.ALIGN_LEFT,
            overflow = 1,
            maxLines = 2,
          ),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcTextLayout(
            componentId = 21,
            animationId = -1,
            textId = 44,
            color = 0xFF7A7078.toInt(),
            fontSize = RcFloatWord.literal(14f),
            fontStyle = 0,
            fontWeight = RcFloatWord.literal(400f),
            fontFamilyId = -1,
            textAlignAndFlags = RcTextLayout.ALIGN_LEFT,
            overflow = 1,
            maxLines = 1,
          ),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
          RcNoArg(RcOpcodes.CONTAINER_END),
        ),
    )

  private fun buildEmbeddedCardDoc(inlineChildBytes: ByteArray?): RcDocument {
    val ops =
      mutableListOf(
        RcTextData(id = 42, text = "Session title"),
        RcNamedVariable(id = 42, type = RcNamedVariable.STRING_TYPE, name = "USER:title"),
        RcTextData(id = 43, text = "Track 1"),
        RcNamedVariable(id = 43, type = RcNamedVariable.STRING_TYPE, name = "USER:badge"),
        RcTextData(id = 44, text = "Jane Doe, John Smith"),
        RcTextData(id = 45, text = "USER:label"),
      )
    val urlId =
      if (inlineChildBytes != null) {
        ops +=
          RcBinaryData(id = 50, type = RcBinaryData.TYPE_RC_DOC, flags = 0, data = inlineChildBytes)
        50
      } else {
        ops += RcTextData(id = 50, text = CHILD_URL)
        50
      }
    ops +=
      listOf(
        RcRootLayout(componentId = 1),
        RcLayoutContent(componentId = 2),
        RcColumnLayout(
          componentId = 3,
          animationId = -1,
          horizontalPositioning = 0,
          verticalPositioning = 0,
          spacedBy = RcFloatWord.literal(8f),
        ),
        RcWidthModifier(RcDimensionType.FILL, RcFloatWord.literal(1f)),
        RcHeightModifier(RcDimensionType.FILL, RcFloatWord.literal(1f)),
        bg(0.94f, 0.87f, 0.90f),
        pad(16f, 16f, 16f, 16f),
        RcLayoutContent(componentId = 4),
        RcRowLayout(
          componentId = 5,
          animationId = -1,
          horizontalPositioning = 0,
          verticalPositioning = 2,
          spacedBy = RcFloatWord.literal(8f),
        ),
        RcWidthModifier(RcDimensionType.FILL, RcFloatWord.literal(1f)),
        RcLayoutContent(componentId = 6),
        RcDocLayout(componentId = 10, animationId = -1, urlId = urlId),
        RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(120f)),
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(32f)),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcTextLayout(
          componentId = 20,
          animationId = -1,
          textId = 42,
          color = 0xFF1E1A1D.toInt(),
          fontSize = RcFloatWord.literal(18f),
          fontStyle = 0,
          fontWeight = RcFloatWord.literal(700f),
          fontFamilyId = -1,
          textAlignAndFlags = RcTextLayout.ALIGN_LEFT,
          overflow = 1,
          maxLines = 2,
        ),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcTextLayout(
          componentId = 21,
          animationId = -1,
          textId = 44,
          color = 0xFF7A7078.toInt(),
          fontSize = RcFloatWord.literal(14f),
          fontStyle = 0,
          fontWeight = RcFloatWord.literal(400f),
          fontFamilyId = -1,
          textAlignAndFlags = RcTextLayout.ALIGN_LEFT,
          overflow = 1,
          maxLines = 1,
        ),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcNoArg(RcOpcodes.CONTAINER_END),
        RcMapRcDocVariables(
          docId = 10,
          mode = RcMapRcDocVariables.MAP_FORWARD,
          localId = 43,
          remoteTarget = 45,
        ),
      )
    return RcDocument(
      header =
        RcHeader(
          RcVersion(1, 1, 0),
          legacyWidth = RcFeedProfile.CARD_WIDTH,
          legacyHeight = RcFeedProfile.CARD_HEIGHT,
        ),
      operations = ops,
    )
  }
}

/**
 * `rcPlayerEmbeddedFeedProfile`: [RcEmbeddedFeedProfile] comparing flat vs embedded-doc feed cards.
 */
public object RcEmbeddedFeedProfileMain {
  @JvmStatic
  public fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "build/profile").also { it.mkdirs() }
    val profile = RcEmbeddedFeedProfile()
    val results = profile.run()
    val environment =
      listOf(
        "JVM" to "${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}",
        "OS" to "${System.getProperty("os.name")} ${System.getProperty("os.arch")}",
        "CPUs" to Runtime.getRuntime().availableProcessors().toString(),
        "Renderer" to "Compose Desktop ImageComposeScene (skiko software raster), density 1",
      )
    val report = RcEmbeddedFeedProfile.report(results, environment)
    File(out, "embedded-feed.md").writeText(report)
    println(report)
  }
}
