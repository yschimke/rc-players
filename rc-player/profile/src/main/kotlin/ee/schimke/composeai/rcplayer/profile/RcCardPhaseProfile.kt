package ee.schimke.composeai.rcplayer.profile

import ee.schimke.composeai.rcplayer.compose.RcPreparedDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.runtime.RcNamedValue
import ee.schimke.composeai.rcplayer.runtime.RcPlayerState
import java.io.File

/**
 * `rcPlayerCardPhases`: the document work a new card can cost, each phase timed on its own.
 * - **decode** — `.rc` bytes to an `RcDocument`;
 * - **prepare** — linking, the layout tree, the state seed and every other per-document index
 *   (`RcPreparedDocument`);
 * - **state** — one card's `RcPlayerState`, scanning the document;
 * - **state (seeded)** — the same state copied from the prepared document's `RcPlayerStateSeed`.
 *
 * The first two are what `RcPreparation.Background` takes off the main thread, once per template;
 * the last is what each card still pays during composition. Composing, laying out and drawing the
 * card is [RcFeedProfile]'s business. Arguments: the output directory, then `.rc` files.
 */
public object RcCardPhaseProfileMain {
  @JvmStatic
  public fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "build/profile").also { it.mkdirs() }
    val templates = args.drop(1).map(::File)
    require(templates.isNotEmpty()) { "name at least one .rc template to profile" }
    val report = buildString {
      appendLine("# Card phases: the document work one new card can cost")
      appendLine()
      appendLine("Median of $RUNS runs after $WARMUP warm-up runs.")
      appendLine()
      appendLine("| Template | Bytes | Ops | decode | prepare | state | state (seeded) |")
      appendLine("| --- | ---: | ---: | ---: | ---: | ---: | ---: |")
      for (file in templates) {
        val bytes = file.readBytes()
        val document = RcDocumentCodec.decode(bytes)
        val prepared = RcPreparedDocument(document)
        val values = mapOf("title" to RcNamedValue.Text("A session title"))
        val decode = time { RcDocumentCodec.decode(bytes) }
        val prepare = time { RcPreparedDocument(document) }
        val state = time { RcPlayerState(document, values) }
        val seeded = time { RcPlayerState(prepared.stateSeed, values) }
        appendLine(
          "| `${file.nameWithoutExtension.take(40)}` | ${bytes.size} | " +
            "${document.operations.size} | ${us(decode)} | ${us(prepare)} | ${us(state)} | " +
            "${us(seeded)} |",
        )
      }
    }
    File(out, "card-phases.md").writeText(report)
    println(report)
  }

  /** The median of [RUNS] calls of [block], in microseconds, after [WARMUP] calls. */
  private fun time(block: () -> Any?): Double {
    repeat(WARMUP) { block() }
    val samples =
      LongArray(RUNS) {
        val start = System.nanoTime()
        block()
        System.nanoTime() - start
      }
    samples.sort()
    return samples[RUNS / 2] / 1_000.0
  }

  private fun us(micros: Double): String =
    if (micros >= 1_000) "%.2f ms".format(micros / 1_000) else "%.1f µs".format(micros)

  private const val WARMUP = 2_000
  private const val RUNS = 2_000
}
