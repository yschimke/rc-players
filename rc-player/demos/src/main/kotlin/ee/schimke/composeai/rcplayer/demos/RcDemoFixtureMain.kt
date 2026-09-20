package ee.schimke.composeai.rcplayer.demos

import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import java.io.File

/**
 * Writes the wire fixtures used by native-player host conformance checks.
 *
 * The first two outputs are shared native-player fixtures. The optional final three are the sample
 * app's Swift custom-component showcase documents.
 */
public fun main(args: Array<String>) {
  require(args.isNotEmpty() && args.size <= 5) {
    "usage: RcDemoFixtureMainKt <editable.rc> [density.rc controls.rc pulse.rc chart.rc]"
  }
  write(args[0], RcDocumentCodec.encode(RcDemoDocuments.editableText()))
  if (args.size >= 2) write(args[1], RcDocumentCodec.encode(RcDemoDocuments.hostDensityText()))
  if (args.size >= 3) write(args[2], RcDocumentCodec.encode(RcDemoDocuments.swiftControls()))
  if (args.size >= 4) write(args[3], RcDocumentCodec.encode(RcDemoDocuments.swiftPulse()))
  if (args.size >= 5) write(args[4], RcDocumentCodec.encode(RcDemoDocuments.swiftChart()))
}

private fun write(path: String, bytes: ByteArray) {
  File(path).apply {
    parentFile?.mkdirs()
    writeBytes(bytes)
  }
}
