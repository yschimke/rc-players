@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package ee.schimke.composeai.rcplayer.threed

/**
 * A `Uint8Array` over [length] bytes of Skia's Wasm memory at [address], such as a bitmap's pixels
 * (`Pixmap.addr`), so WebGL reads into and uploads from them directly. Made fresh each time: a view
 * over the memory's buffer is detached when the memory grows. Null if Skiko's module does not have
 * the shape this expects.
 */
internal fun skikoHeapBytes(address: Int, length: Int): JsAny? =
  heapBytes(loadedWasm, address, length)

private fun heapBytes(module: JsAny, address: Int, length: Int): JsAny? =
  js(
    """
    (() => {
      const memory = module && module._ && module._.memory;
      if (!memory || !(memory.buffer instanceof ArrayBuffer) || address === 0) return null;
      if (address + length > memory.buffer.byteLength) return null;
      return new Uint8Array(memory.buffer, address, length);
    })()
    """
  )
