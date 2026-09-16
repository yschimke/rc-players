package ee.schimke.composeai.rcplayer.demos

import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import java.io.File

/**
 * Writes the wire fixtures used by native-player host conformance checks.
 *
 * `editable-text` is the custom-component demo. `host-density-text` is the deferred-density capture
 * — the only document in this repository that reads the density built-ins, and so the only one that
 * fails against a player which does not load them.
 */
public fun main(args: Array<String>) {
  require(args.isNotEmpty() && args.size <= 2) {
    "usage: RcDemoFixtureMainKt <editable-text.rc> [host-density.rc]"
  }
  write(args[0], RcDocumentCodec.encode(RcDemoDocuments.editableText()))
  if (args.size == 2) write(args[1], RcDocumentCodec.encode(RcDemoDocuments.hostDensityText()))
}

private fun write(path: String, bytes: ByteArray) {
  File(path).apply {
    parentFile?.mkdirs()
    writeBytes(bytes)
  }
}
