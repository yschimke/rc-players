package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.runtime.RcDocumentLinker
import ee.schimke.composeai.rcplayer.runtime.RcLayoutTree
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every edit, over every document in the catalog corpus: the result must still encode, decode to
 * itself, and build the layout tree the player would build — whenever the original did.
 *
 * The corpus is what real writers emit, which the hand-built documents in `commonTest` only
 * approximate: id orders, nesting, the 1,500-odd named theme colours.
 */
class RcCorpusEditTest {
  private val corpus =
    File("../../scripts/rc-catalog-corpus/corpus")
      .listFiles { file -> file.extension == "rc" }!!
      .sortedBy { it.name }

  @Test
  fun everyEditKeepsEveryCorpusDocumentPlayable() {
    assertTrue(corpus.size > 100, "Corpus not found at ${File(".").absolutePath}")
    corpus.forEach { file ->
      val original = RcDocumentCodec.decode(file.readBytes())
      val playable = runCatching { RcLayoutTree.build(RcDocumentLinker.link(original)) }.isSuccess

      val edited =
        original.hardcodeUnboundNamedValues().collapseConstantStateLayouts().embedBitmapUrls {
          null
        }

      assertTrue(edited.namedVariables.isEmpty(), file.name)
      val reread = RcDocumentCodec.decode(RcDocumentCodec.encode(edited))
      assertEquals(edited, reread, file.name)
      if (playable) RcLayoutTree.build(RcDocumentLinker.link(reread))
    }
  }

  @Test
  fun editsThatFindNothingToDoLeaveTheBytesExactlyAsTheyWere() {
    corpus.forEach { file ->
      val bytes = file.readBytes()
      val original = RcDocumentCodec.decode(bytes)
      if (original.operations.any { it is RcNamedVariable }) return@forEach
      val edited = original.hardcodeUnboundNamedValues().embedBitmapUrls(emptyMap())
      assertTrue(RcDocumentCodec.encode(edited).contentEquals(bytes), file.name)
    }
  }
}
