package ee.schimke.composeai.rcplayer.edit

import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import kotlin.test.assertEquals

internal val END: RcOperation = RcNoArg(RcOpcodes.CONTAINER_END)

internal fun document(vararg operations: RcOperation): RcDocument =
  RcDocument(RcHeader(RcVersion(1, 1, 0)), operations.toList())

/** A root layout around [content], as AndroidX's writer frames a document's component tree. */
internal fun rootLayout(vararg content: RcOperation): List<RcOperation> =
  listOf(RcRootLayout(-1), RcLayoutContent(-2), *content, END, END)

/** A box component [componentId] with a content wrapper holding [content]. */
internal fun box(componentId: Int, vararg content: RcOperation): List<RcOperation> =
  listOf(
    RcBoxLayout(componentId, -1, 0, 0),
    RcLayoutContent(componentId - 1000),
    *content,
    END,
    END,
  )

/** Re-encodes and decodes [document], asserting the rewrite is still a well-formed document. */
internal fun assertRoundTrips(document: RcDocument): RcDocument =
  RcDocumentCodec.decode(RcDocumentCodec.encode(document)).also { assertEquals(document, it) }
