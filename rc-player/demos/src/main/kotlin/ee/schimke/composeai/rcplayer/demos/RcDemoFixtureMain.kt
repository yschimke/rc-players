package ee.schimke.composeai.rcplayer.demos

import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import java.io.File

/** Writes the editable-text wire fixture used by native-player host conformance checks. */
public fun main(args: Array<String>) {
  require(args.size == 1) { "usage: RcDemoFixtureMainKt <output.rc>" }
  File(args.single()).apply {
    parentFile?.mkdirs()
    writeBytes(RcDocumentCodec.encode(RcDemoDocuments.editableText()))
  }
}
