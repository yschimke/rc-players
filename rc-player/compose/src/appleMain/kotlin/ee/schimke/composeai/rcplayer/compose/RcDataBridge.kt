package ee.schimke.composeai.rcplayer.compose

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
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

/** Copies Kotlin bytes into Foundation data with one native bulk copy. */
@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
public fun rcData(bytes: ByteArray): NSData =
  if (bytes.isEmpty()) {
    NSData()
  } else {
    bytes.usePinned { pinned ->
      NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
  }
