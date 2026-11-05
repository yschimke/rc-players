package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `toRemoteComposeKotlin` over every document in the catalog corpus: it never throws, every file it
 * writes is lexically balanced, and every gap it leaves is marked in the source as well as listed.
 * Whether the source compiles and re-captures is `rc-creation-bridge`'s round trip.
 */
class RcCorpusKotlinSourceTest {
  private val corpus =
    File("../../scripts/rc-catalog-corpus/corpus")
      .listFiles { file -> file.extension == "rc" }!!
      .sortedBy { it.name }

  @Test
  fun everyCorpusDocumentBecomesABalancedSourceFile() {
    assertTrue(corpus.size > 100, "Corpus not found at ${File(".").absolutePath}")
    val gaps = mutableMapOf<String, Int>()
    var complete = 0
    corpus.forEach { file ->
      val source = RcDocumentCodec.decode(file.readBytes()).toRemoteComposeKotlin()
      assertBalanced(source.code, file.name)
      assertEquals(
        source.unsupported.size,
        Regex("// TODO\\(rc-to-kotlin\\):").findAll(source.code).count(),
        file.name,
      )
      if (source.isComplete) complete++
      source.unsupported
        .map { it.replace(Regex("-?\\d+"), "#") }
        .toSet()
        .forEach { gaps[it] = (gaps[it] ?: 0) + 1 }
    }
    println("complete: $complete of ${corpus.size}")
    gaps.entries
      .sortedByDescending { it.value }
      .forEach { (gap, documents) -> println("$documents\t$gap") }
  }

  @Test
  fun everyCorpusDocumentBecomesAUiBuilderDesign() {
    val out = File("build/rc-to-uid").apply { mkdirs() }
    val gaps = mutableMapOf<String, Int>()
    var complete = 0
    corpus.forEach { file ->
      val design =
        RcDocumentCodec.decode(file.readBytes())
          .toUiBuilderDesign(RcUiBuilderOptions(designId = file.nameWithoutExtension))
      File(out, file.nameWithoutExtension + ".uid").writeText(design.json)
      if (design.isComplete) complete++
      design.unsupported
        .map { it.replace(Regex("-?\\d+"), "#") }
        .toSet()
        .forEach { gaps[it] = (gaps[it] ?: 0) + 1 }
    }
    println("complete: $complete of ${corpus.size}")
    gaps.entries
      .sortedByDescending { it.value }
      .forEach { (gap, documents) -> println("$documents\t$gap") }
  }

  /** Brackets balance outside string literals and comments. */
  private fun assertBalanced(code: String, name: String) {
    val stack = ArrayDeque<Char>()
    var index = 0
    while (index < code.length) {
      val char = code[index]
      when {
        code.startsWith("//", index) ->
          index = code.indexOf('\n', index).let { if (it < 0) code.length else it }
        char == '"' -> {
          index++
          while (code[index] != '"') index += if (code[index] == '\\') 2 else 1
        }
        char in "({[" -> stack.addLast(char)
        char in ")}]" -> {
          val open = stack.removeLastOrNull()
          assertEquals(
            "({[".indexOf(open ?: ' '),
            ")}]".indexOf(char),
            "$name: unbalanced at $index",
          )
        }
      }
      index++
    }
    assertTrue(stack.isEmpty(), "$name: unclosed $stack")
  }
}
