package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.runtime.RcDocumentLinker
import ee.schimke.composeai.rcplayer.runtime.RcLayoutTree
import ee.schimke.composeai.rcplayer.runtime.RcPlayerState
import ee.schimke.composeai.rcplayer.runtime.RcTimeSnapshot
import ee.schimke.composeai.rcplayer.runtime.RcTimeSource
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [optimize] over the whole catalog corpus: every result must still decode to itself, still build
 * the layout tree the player would build, have no new profile violations — and, played, give every
 * value it still defines exactly what the original gave it.
 */
class RcCorpusOptimizeTest {
  private val corpus =
    File("../../scripts/rc-catalog-corpus/corpus")
      .listFiles { file -> file.extension == "rc" }!!
      .sortedBy { it.name }

  /** A clock that never moves, so two documents played side by side see the same instant. */
  private val frozen =
    object : RcTimeSource {
      override fun currentTimeMillis(): Long = EPOCH

      override fun snapshot(epochMillis: Long): RcTimeSnapshot =
        RcTimeSource.System.snapshot(EPOCH).copy(offsetSeconds = 0)
    }

  @Test
  fun optimizingKeepsEveryCorpusDocumentPlayingTheSame() {
    assertTrue(corpus.size > 100, "Corpus not found")
    var before = 0
    var after = 0
    var bakedBytes = 0
    val removedByOperation = mutableMapOf<String, Int>()
    val sizes = mutableMapOf<String, Int>()
    corpus.forEach { file ->
      val bytes = file.readBytes()
      val original = RcDocumentCodec.decode(bytes)
      val optimized = original.optimize()
      val encoded = RcDocumentCodec.encode(optimized)
      assertEquals(optimized, RcDocumentCodec.decode(encoded), file.name)
      before += bytes.size
      after += encoded.size

      val tree = runCatching { RcLayoutTree.build(RcDocumentLinker.link(original)) }
      if (tree.isSuccess) RcLayoutTree.build(RcDocumentLinker.link(optimized))
      assertTrue(
        optimized.validateFor(RcTargetProfiles.ANDROIDX).size <=
          original.validateFor(RcTargetProfiles.ANDROIDX).size,
        file.name,
      )
      if (tree.isSuccess) {
        assertSameValues(original, optimized, file.name)
        // A widget whose host values are baked in: unbound, the original plays its defaults, so
        // hardcoding them and optimising must leave every value where it was.
        val baked = original.hardcodeUnboundNamedValues().optimize()
        RcLayoutTree.build(RcDocumentLinker.link(baked))
        assertSameValues(original, baked, "${file.name} baked")
        bakedBytes += RcDocumentCodec.encode(baked).size
      }

      optimized.sizeReport().byOperation.forEach { (op, size) ->
        sizes[op] = (sizes[op] ?: 0) + size
      }
      (original.sizeReport().byOperation.keys + optimized.sizeReport().byOperation.keys).forEach {
        val gone =
          original.operations.count { op -> operationName(op.opcode) == it } -
            optimized.operations.count { op -> operationName(op.opcode) == it }
        if (gone > 0) removedByOperation[it] = (removedByOperation[it] ?: 0) + gone
      }
    }
    println(
      "Corpus of ${corpus.size}: $before -> $after bytes (${100 - after * 100 / before}% smaller); " +
        "hardcoded and optimised: $bakedBytes bytes\n" +
        "Removed or folded: " +
        removedByOperation.entries
          .sortedByDescending { it.value }
          .joinToString { "${it.key} ${it.value}" } +
        "\nLargest after: " +
        sizes.entries
          .sortedByDescending { it.value }
          .take(10)
          .joinToString { "${it.key} ${it.value * 100 / after}%" }
    )
  }

  /** Plays both documents at one frozen instant and compares every value [optimized] defines. */
  private fun assertSameValues(original: RcDocument, optimized: RcDocument, name: String) {
    fun play(document: RcDocument): RcPlayerState {
      val state = RcPlayerState(document, timeSource = frozen)
      state.applyLayoutContentStateOperations(RcDocumentLinker.link(document).operations)
      return state
    }
    val expected = play(original)
    val actual = play(optimized)
    optimized.operations
      .flatMap { it.definedIds().orEmpty() }
      .distinct()
      .forEach { id ->
        val word = RcFloatWord(0x7fc00000 or id)
        assertEquals(
          expected.resolve(word).toRawBits(),
          actual.resolve(word).toRawBits(),
          "$name float $id",
        )
        assertEquals(expected.integer(id), actual.integer(id), "$name integer $id")
        assertEquals(expected.text(id), actual.text(id), "$name text $id")
        assertEquals(expected.color(id), actual.color(id), "$name color $id")
      }
  }

  private companion object {
    const val EPOCH = 1_760_000_000_000L
  }
}
