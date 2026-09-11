package ee.schimke.composeai.rcplayer.compose

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.posix.memcpy

/** Copies Foundation data into Kotlin memory with one native bulk copy. */
@OptIn(ExperimentalForeignApi::class)
public fun rcByteArray(data: NSData): ByteArray {
  val result = ByteArray(data.length.toInt())
  if (result.isNotEmpty()) {
    result.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, data.length) }
  }
  return result
}
