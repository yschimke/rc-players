@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@file:JsModule("./skiko.mjs")

package ee.schimke.composeai.rcplayer.threed

/**
 * Skiko's own module, the one its Kotlin bindings import from: `loadedWasm._` holds the exports of
 * Skia's Wasm instance, whose `memory` is where every Skia bitmap keeps its pixels.
 */
internal external val loadedWasm: JsAny
