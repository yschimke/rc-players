@file:OptIn(
  androidx.compose.ui.ExperimentalComposeUiApi::class,
  kotlin.js.ExperimentalWasmJsInterop::class,
  kotlin.io.encoding.ExperimentalEncodingApi::class,
)

package ee.schimke.composeai.rcplayer.wasm

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.ComposeViewport
import ee.schimke.composeai.rcplayer.compose.RcComposePlayer
import ee.schimke.composeai.rcplayer.compose.RcManifestTypefaceLoader
import ee.schimke.composeai.rcplayer.compose.RcPlayerTheme
import ee.schimke.composeai.rcplayer.compose.RcTypefaceLoader
import ee.schimke.composeai.rcplayer.compose.composeSupportReport
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcOperationProfiles
import ee.schimke.composeai.rcplayer.runtime.RcHostActionValue
import ee.schimke.composeai.rcplayer.runtime.RcNamedValue
import ee.schimke.composeai.rcplayer.runtime.RcPlayerEvent
import ee.schimke.composeai.rcplayer.trace.RcTraceCategory
import ee.schimke.composeai.rcplayer.trace.rcTrace
import ee.schimke.composeai.rcplayer.trace.setRcPlatformTracingEnabled
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.io.encoding.Base64
import kotlin.js.Promise
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

private sealed interface LoadState {
  data object Loading : LoadState

  data class Ready(
    val document: RcDocument,
    val typefaces: RcTypefaceLoader,
    val namedValues: Map<String, RcNamedValue>,
  ) : LoadState

  /**
   * [generation] is the [LoadRequest] that failed. A swap changes the request a frame before the
   * load effect resets the state, so without it the outgoing failure would be reported again as the
   * incoming document's — after a `?src=` that 404s, or the missing `?src=` a bytes-only host
   * starts from, the next document would read as failed before it had loaded.
   */
  data class Failed(val message: String, val generation: Int) : LoadState
}

/**
 * Which document the player is showing, and how many times it has been asked.
 *
 * [generation] is not decoration: `?src` is usually a *stable* URL whose bytes change underneath it
 * (the parity driver serves every preview from one `/document.rc`), so the source alone cannot key
 * a reload — and two consecutive documents can decode to equal [RcDocument]s. Counting the requests
 * gives both the fetch and the readiness signal a key that always moves.
 */
private data class LoadRequest(
  val source: DocumentSource?,
  val generation: Int,
  /**
   * The `id` of the `rc-player-load` message that asked for this document, echoed in the
   * `rc-player-state` reply so a host — possibly on another origin, with no access to the marker —
   * can tell its own result from an earlier one. Null for `?src=` and the `window` functions.
   */
  val hostId: String? = null,
)

/**
 * Where a document's bytes come from: a URL the player fetches (`?src=`, `window.rcPlayerLoad`), or
 * bytes the host already holds (`window.rcPlayerLoadBytes`).
 *
 * [Bytes] compares by identity, which is fine: [LoadRequest.generation] is what keys a reload.
 */
private sealed interface DocumentSource {
  data class Url(val url: String) : DocumentSource

  class Bytes(val bytes: ByteArray) : DocumentSource
}

private var loadRequest by mutableStateOf(LoadRequest(null, 0))
private var loadState by mutableStateOf<LoadState>(LoadState.Loading)

public fun main() {
  // Browser User Timing marks are opt-in: `performance`'s entry buffer is finite and shared with
  // whatever else the embedding page measures, so a player embedded in someone's dashboard should
  // not be filling it on every frame. `?rcTrace=1` turns the player's spans on for a session; the
  // span names match the ones the desktop player writes into Perfetto.
  // Publish the embed contract's version before anything else, so a host that loaded this bundle
  // from npm can tell what it is talking to. The npm package's major tracks this number; the
  // contract itself is written up in docs/design/RC_PLAYER_EMBED.md (#4067). It is a separate
  // number from the repo's release version deliberately: the bundle ships on every release, and a
  // host cares about whether `?src=` and `window.rcPlayerLoad` still mean what it coded against,
  // not about which release it happens to have.
  publishContractVersion(RC_PLAYER_EMBED_CONTRACT_VERSION)
  setRcPlatformTracingEnabled(queryParameter("rcTrace") == "1")
  loadRequest = LoadRequest(queryParameter("src")?.let(DocumentSource::Url), generation = 0)
  installDocumentSwap()
  // `?theme=` is part of the embed contract, so the accepted spellings stay exactly as they were;
  // only the type the player takes has changed. Anything else — including no parameter at all —
  // follows the browser's `prefers-color-scheme`, which is what `RcTheme.UNSPECIFIED` resolved to
  // here before.
  val theme =
    when (queryParameter("theme")?.lowercase()) {
      "light" -> RcPlayerTheme.Light
      "dark" -> RcPlayerTheme.Dark
      else -> RcPlayerTheme.System
    }
  ComposeViewport(viewportContainerId = "rcPlayer") {
    LaunchedEffect(Unit) {
      // One waiter, re-armed after every swap: `window.rcPlayerLoad(src)` and
      // `window.rcPlayerLoadBytes(bytes)` hand the next source in here instead of the host
      // navigating the page again. See [installDocumentSwap].
      while (true) {
        val (next, hostId) = awaitDocumentSwap()
        loadRequest = LoadRequest(next, loadRequest.generation + 1, hostId)
      }
    }

    val request = loadRequest
    LaunchedEffect(request) {
      val source = request.source
      // Drop the previous document before fetching the next: the player leaves composition, so no
      // state from the document being replaced can reach the one replacing it, and a host that
      // waits on the readiness marker cannot mistake the outgoing render for the incoming one.
      loadState = LoadState.Loading
      loadState =
        if (source == null) LoadState.Failed("Missing ?src=<document.rc>", request.generation)
        else
          runCatching {
              val bytes =
                when (source) {
                  is DocumentSource.Url ->
                    rcTrace(RcTraceCategory.DOCUMENT, "rc:fetchDocument") { fetchBytes(source.url) }
                  is DocumentSource.Bytes -> source.bytes
                }
              val document = RcDocumentCodec.decode(bytes)
              // Async work stays in construction: the manifest is fetched and decoded here, and the
              // player is handed a loader that only looks things up. `RcTypefaceLoader.typeface` is
              // called during composition and draw and cannot suspend.
              val typefaces = withTimeout(8_000) { loadHostTypefaces() }
              document
                .composeSupportReport(
                  RcOperationProfiles.CMP_WASM_ALPHA18,
                  availableFontFamilies = typefaces.families,
                  allowExternalImagePlaceholders =
                    queryParameter("allowExternalImagePlaceholders") == "1",
                )
                // `?lenient=1` plays a document carrying any operation the player knows,
                // drawing nothing for the ones this backend has no branch for, rather than
                // refusing the whole document. Everything that would throw mid-draw still fails.
                .requireRenderable(queryParameter("lenient") == "1")
              LoadState.Ready(document, typefaces, namedValuesFromLocation())
            }
            .fold(
              onSuccess = { it },
              onFailure = { LoadState.Failed(it.message ?: "load failed", request.generation) },
            )
    }

    when (val state = loadState) {
      LoadState.Loading -> Unit
      // Keyed by the failed request: two documents can fail the same way, and the second failure
      // still has to be reported — `rcPlayerLoad` cleared the marker the first one set. Reported
      // only while that request is still the current one; see [LoadState.Failed.generation].
      is LoadState.Failed ->
        if (state.generation == request.generation) {
          LaunchedEffect(state) { reportFailure(state.message, request.hostId) }
        }
      is LoadState.Ready -> {
        // Keyed on the document, not the page: `?namedValues=` belongs to the page, but the
        // *variables* belong to the document, so a swap starts from the URL's values again rather
        // than carrying the previous document's overrides into one that may not declare them.
        // Seeded during composition so the player's `RcPlayerState` is constructed with them
        // already in place, exactly as the old `Map` parameter was.
        val namedValues =
          remember(state.document) {
            mutableStateMapOf<String, RcNamedValue>().apply { putAll(state.namedValues) }
          }
        RcComposePlayer(
          state.document,
          Modifier.fillMaxSize().drawWithContent {
            drawRect(Color.Transparent, blendMode = BlendMode.Clear)
            drawContent()
          },
          theme = theme,
          namedValues = namedValues,
          onEvent = ::postPlayerEvent,
          typefaces = state.typefaces,
        )
        LaunchedEffect(request) {
          // Compose schedules Skiko's raster work after composition. One frame only proves the
          // composition ran; waiting through two further browser frames prevents the host from
          // revealing an iframe whose backing surface is still blank on a cold Wasm start.
          repeat(3) { withFrameNanos {} }
          // Chromium can acknowledge those frames before the Skiko surface is presented to the
          // compositor. Keep the parent snapshot visible through that short cold-start tail.
          delay(handoffDelayMs)
          postReady(request.hostId)
        }
      }
    }
  }
}

private fun fetchAsBase64(url: String): Promise<JsString> =
  js(
    """fetch(url).then(function (response) {
      if (!response.ok) throw new Error('HTTP ' + response.status);
      return response.arrayBuffer();
    }).then(function (buffer) {
      var bytes = new Uint8Array(buffer), chunks = [], chunkSize = 0x8000;
      for (var i = 0; i < bytes.length; i += chunkSize) {
        chunks.push(String.fromCharCode.apply(null, bytes.subarray(i, i + chunkSize)));
      }
      return btoa(chunks.join(''));
    })"""
  )

private suspend fun fetchBytes(url: String): ByteArray =
  suspendCancellableCoroutine { continuation ->
    fetchAsBase64(url)
      .then { encoded ->
        if (continuation.isActive) continuation.resume(Base64.decode(encoded.toString()))
        null
      }
      .catch { failure ->
        if (continuation.isActive) {
          continuation.resumeWithException(IllegalStateException(failure.toString()))
        }
        null
      }
  }

/**
 * The host's typefaces, from the `fonts.json` manifest under `?fontsBase=` (default `./fonts/`).
 *
 * Everything this used to do — fetch the manifest, parse it, group faces by family, lowercase the
 * keys, register the `default`-role family under the literal `"default"` a document asks for when
 * it names none, and cache the lot per base — now lives in [RcManifestTypefaceLoader], in shared
 * code. All that is left here is the fetch, which really is the browser's business. Those rules are
 * protocol facts, and keeping them in this file is what made them unavailable to the iOS host and
 * cost the remote-m3 catalog its body face there (#4061).
 *
 * The instance is held for the life of the page, which is what keeps its cache useful: a swapped-in
 * document (`window.rcPlayerLoad`) reuses the faces the first load fetched rather than paying for a
 * whole catalog's fonts again.
 */
private val manifestTypefaces = RcManifestTypefaceLoader(::fetchBytes)

private suspend fun loadHostTypefaces(): RcTypefaceLoader {
  val rawBase = queryParameter("fontsBase") ?: DEFAULT_FONTS_BASE
  // A page-supplied parameter, so the scheme check stays here rather than moving into shared code:
  // it is about what this *page* may be pointed at, not about how a manifest is shaped.
  val base =
    rawBase.takeIf { !it.contains(':') || it.startsWith("http:") || it.startsWith("https:") }
      ?: DEFAULT_FONTS_BASE
  return manifestTypefaces.load(base)
}

private const val DEFAULT_FONTS_BASE = "./fonts/"

/** The default cold-start tail. Every render pays it, so a host that cannot flash should not. */
private const val DEFAULT_HANDOFF_DELAY_MS = 1_500L

/**
 * How long to keep saying "not ready" after the frames have gone through, so a host that reveals
 * this player on `ready` cannot swap a snapshot for a surface the compositor has not presented yet.
 *
 * `?handoffDelayMs=0` turns the tail off, and **only a host that composites the result itself**
 * should ask for that. The parity driver is the case that exists: it screenshots through CDP, which
 * drives its own compositor frame, then verifies the size and every pixel of what came back — so a
 * surface that was not presented yet cannot slip past it, and the 1.5 s is dead weight repeated
 * once per preview (~3 minutes across a 122-preview catalog). The viewer's iframe handoff has no
 * such check and keeps the default: it is the one that would show a blank frame to a human, and
 * that failure could not be reproduced under CDP capture in the first place — screenshots and
 * screencasts both drive frames of their own, so neither can observe it. An unverifiable hazard
 * keeps its guard.
 */
private val handoffDelayMs: Long
  get() =
    queryParameter("handoffDelayMs")?.toLongOrNull()?.coerceIn(0L, 10_000L)
      ?: DEFAULT_HANDOFF_DELAY_MS

/**
 * Install `window.rcPlayerLoad(src)` and `window.rcPlayerLoadBytes(bytes)`: show another document
 * in the player that is already running, instead of navigating the page again.
 *
 * A navigation is the honest way to load the *first* document, but it is a poor way to load the
 * next one — it throws away the instantiated Wasm module, the Compose runtime and the host fonts,
 * then rebuilds all three to draw a document that is usually a few dozen operations long. The
 * parity driver renders a whole catalog through one page, so it pays that teardown once per
 * preview; a 122-preview catalog spends minutes on it. Handing over just the source keeps the
 * player warm and leaves the reload contract unchanged: the marker on `<html>` goes back to
 * `loading` synchronously here, so a host that waits for `ready` cannot read the outgoing render's
 * marker and screenshot the document it just replaced.
 *
 * `rcPlayerLoadBytes` is the same swap for a host that already holds the document — a file the user
 * dropped, a response it fetched itself, bytes it generated — so it need not mint a URL for the
 * player to fetch back. It takes an `ArrayBuffer` or any `ArrayBufferView` (`Uint8Array`,
 * `DataView`, a Node `Buffer`) and copies it synchronously, so the host may reuse or detach its
 * buffer as soon as the call returns. Anything else throws a `TypeError` *before* the marker moves,
 * so a host that passed the wrong thing is told so rather than left waiting on `loading`. The bytes
 * cross into Wasm as base64, the same way a fetched document does.
 *
 * `?theme` and `?namedValues` are *not* re-read — they belong to the page, and a host that needs
 * different ones should navigate. Only the document changes.
 *
 * The same swap is reachable by `postMessage`, which is what makes the player embeddable from
 * another origin — a page including `rc-cmp-player.js` from a CDN cannot touch this `window`. Its
 * parent sends `{type: 'rc-player-hello', id}` (answered in kind, with the contract version) and
 * `{type: 'rc-player-load', id, bytes | src}`, answered by `{type: 'rc-player-state', id, state,
 * error?}` once the document is `ready` or in `error`. Messages are accepted only from
 * `window.parent`, from any origin: whoever frames the page can already choose its `?src=`. The
 * origin that last spoke is remembered, and from then on everything the player posts — the
 * readiness messages and the document's host actions and debug messages — goes there as well as to
 * this page's own origin, where it always went. A parent that never speaks hears nothing new.
 *
 * The handshake is a one-slot mailbox rather than an event listener because this module reaches the
 * browser exclusively through `js(...)` (no `kotlinx-browser` dependency): [awaitDocumentSwap]
 * parks a resolver here, and a call that arrives while the player is busy loading is held in
 * `pending` until the next waiter arms. Requests are last-one-wins, which is what a host driving
 * one render at a time wants.
 */
private fun installDocumentSwap(): Unit =
  js(
    """{
      window.__rcPlayerSwap = { pending: null, resolve: null };
      // The origin of the parent that last sent a message; see `__rcPlayerPost`.
      var host = { origin: null };
      window.__rcPlayerPost = function (message) {
        window.parent.postMessage(message, window.location.origin);
        if (host.origin && host.origin !== window.location.origin) {
          window.parent.postMessage(message, host.origin);
        }
      };
      var deliver = function (request) {
        var root = document.documentElement;
        root.dataset.rcPlayerState = 'loading';
        delete root.dataset.rcPlayerError;
        var swap = window.__rcPlayerSwap;
        if (swap.resolve) {
          var resolve = swap.resolve;
          swap.resolve = null;
          resolve(request);
        } else {
          swap.pending = request;
        }
      };
      window.rcPlayerLoad = function (source) {
        deliver({ url: String(source) });
      };
      var base64Of = function (data) {
        var bytes;
        // A brand check, not `instanceof`: a same-origin parent calling in hands over an
        // ArrayBuffer from its own realm, which is not an instance of this page's ArrayBuffer.
        if (Object.prototype.toString.call(data) === '[object ArrayBuffer]') {
          bytes = new Uint8Array(data);
        } else if (ArrayBuffer.isView(data)) {
          bytes = new Uint8Array(data.buffer, data.byteOffset, data.byteLength);
        } else {
          throw new TypeError('rcPlayerLoadBytes expects an ArrayBuffer or an ArrayBufferView');
        }
        var chunks = [], chunkSize = 0x8000;
        for (var i = 0; i < bytes.length; i += chunkSize) {
          chunks.push(String.fromCharCode.apply(null, bytes.subarray(i, i + chunkSize)));
        }
        return btoa(chunks.join(''));
      };
      window.rcPlayerLoadBytes = function (data) {
        deliver({ base64: base64Of(data) });
      };
      window.addEventListener('message', function (event) {
        if (window.parent === window || event.source !== window.parent) return;
        var data = event.data;
        if (!data || typeof data !== 'object' || typeof data.type !== 'string') return;
        var id = data.id == null ? null : String(data.id);
        if (data.type === 'rc-player-hello') {
          host.origin = event.origin;
          event.source.postMessage(
            { type: 'rc-player-hello', id: id, contractVersion: window.rcPlayerContractVersion },
            event.origin);
        } else if (data.type === 'rc-player-load') {
          host.origin = event.origin;
          try {
            if (data.bytes != null) deliver({ base64: base64Of(data.bytes), id: id });
            else if (data.src != null) deliver({ url: String(data.src), id: id });
            else throw new TypeError('rc-player-load needs bytes or src');
          } catch (error) {
            event.source.postMessage(
              { type: 'rc-player-state', id: id, state: 'error', error: String(error.message || error) },
              event.origin);
          }
        }
      });
    }"""
  )

private fun nextDocumentSwap(): Promise<JsAny> =
  js(
    """new Promise(function (resolve) {
      var swap = window.__rcPlayerSwap;
      if (swap.pending !== null) {
        var pending = swap.pending;
        swap.pending = null;
        resolve(pending);
      } else {
        swap.resolve = resolve;
      }
    })"""
  )

private fun swapUrl(request: JsAny): JsString? =
  js("request.url === undefined ? null : request.url")

private fun swapBase64(request: JsAny): JsString? =
  js("request.base64 === undefined ? null : request.base64")

private fun swapId(request: JsAny): JsString? = js("request.id == null ? null : request.id")

/** The next document a host handed over, and the `rc-player-load` id it came with, if any. */
private suspend fun awaitDocumentSwap(): Pair<DocumentSource, String?> =
  suspendCancellableCoroutine { continuation ->
    nextDocumentSwap()
      .then { request ->
        val base64 = swapBase64(request)?.toString()
        val source =
          if (base64 != null) DocumentSource.Bytes(Base64.decode(base64))
          else DocumentSource.Url(swapUrl(request).toString())
        if (continuation.isActive) continuation.resume(source to swapId(request)?.toString())
        null
      }
      .catch { failure ->
        if (continuation.isActive) {
          continuation.resumeWithException(IllegalStateException(failure.toString()))
        }
        null
      }
  }

private fun queryParameter(name: String): String? =
  queryParameterFromLocation(name).toString().takeUnless { it == "null" }

private fun queryParameterFromLocation(name: String): JsString? =
  js("new URL(window.location.href).searchParams.get(name)")

private fun namedValuesFromLocation(): Map<String, RcNamedValue> {
  val flat = flattenNamedValuesFromLocation()?.toString().orEmpty()
  if (flat.isEmpty()) return emptyMap()
  return buildMap {
    flat.split('\u0001').forEach { row ->
      val fields = row.split('\u0000')
      if (fields.size != 3) return@forEach
      val name = decodeUriComponent(fields[1])
      val value = decodeUriComponent(fields[2])
      val namedValue =
        when (fields[0]) {
          "string" -> RcNamedValue.Text(value)
          "float",
          "dp" -> value.toFloatOrNull()?.let(RcNamedValue::FloatValue)
          "int" -> value.toIntOrNull()?.let(RcNamedValue::Integer)
          "bool" -> RcNamedValue.Integer(if (value == "true") 1 else 0)
          "color" -> value.removePrefix("#").toULongOrNull(16)?.toInt()?.let(RcNamedValue::Color)
          "long" -> value.toLongOrNull()?.let(RcNamedValue::LongValue)
          else -> null
        }
      if (name.isNotEmpty() && namedValue != null) put("USER:$name", namedValue)
    }
  }
}

private fun flattenNamedValuesFromLocation(): JsString? =
  js(
    """(function () {
      try {
        var raw = new URL(window.location.href).searchParams.get('namedValues') || '[]';
        var values = JSON.parse(raw);
        if (!Array.isArray(values)) return null;
        return values.map(function (value) {
          return [String(value.kind || ''), encodeURIComponent(String(value.name || '')),
            encodeURIComponent(String(value.value == null ? '' : value.value))].join('\u0000');
        }).join('\u0001');
      } catch (error) { return null; }
    })()"""
  )

private fun decodeUriComponent(value: String): String = decodeUriComponentJs(value).toString()

private fun decodeUriComponentJs(value: String): JsString = js("decodeURIComponent(value)")

/**
 * The embed contract's version — see docs/design/RC_PLAYER_EMBED.md.
 *
 * Bump on any change a host could observe: a query parameter's meaning, the `data-rc-player-state`
 * values, the `postMessage` payloads, or `window.rcPlayerLoad`'s behaviour. Adding a parameter, a
 * message type or a `window` function (`rcPlayerLoadBytes`) is additive and does not bump it; a
 * host feature-detects those.
 */
private const val RC_PLAYER_EMBED_CONTRACT_VERSION: Int = 1

private fun publishContractVersion(version: Int): Unit =
  js(
    "(window.rcPlayerContractVersion = version, " +
      "document.documentElement.dataset.rcPlayerContract = String(version))"
  )

/**
 * Mark the document `ready` and say so: the legacy string message, and the structured reply a
 * `postMessage` host correlates by [hostId] (see [installDocumentSwap]).
 */
private fun postReady(hostId: String?): Unit =
  js(
    "(document.documentElement.dataset.rcPlayerState = 'ready', " +
      "window.__rcPlayerPost('cp-rc-wasm-ready'), " +
      "window.__rcPlayerPost({ type: 'rc-player-state', id: hostId, state: 'ready' }))"
  )

private fun reportFailure(message: String, hostId: String?): Unit =
  js(
    "(document.documentElement.dataset.rcPlayerState = 'error', " +
      "document.documentElement.dataset.rcPlayerError = message, " +
      "console.error('[rc-player-wasm] ' + message), " +
      "window.__rcPlayerPost('cp-rc-wasm-error:' + message), " +
      "window.__rcPlayerPost({ type: 'rc-player-state', id: hostId, state: 'error', " +
      "error: message }))"
  )

private fun postPlayerEvent(event: RcPlayerEvent) {
  when (event) {
    is RcPlayerEvent.DebugMessage -> postDebugMessage(event.message, event.value, event.flags)
    is RcPlayerEvent.HostAction -> postHostAction(event.actionId)
    is RcPlayerEvent.HostActionMetadata -> postHostMetadataAction(event.actionId, event.metadata)
    is RcPlayerEvent.HostNamedAction ->
      when (val value = event.value) {
        RcHostActionValue.None -> postHostNamedActionNone(event.name)
        is RcHostActionValue.FloatValue -> postHostNamedActionFloat(event.name, value.value)
        is RcHostActionValue.IntegerValue -> postHostNamedActionInt(event.name, value.value)
        is RcHostActionValue.TextValue -> postHostNamedActionText(event.name, value.value)
        is RcHostActionValue.FloatListValue ->
          postHostNamedActionFloatList(event.name, value.value.joinToString(","))
      }
  }
}

private fun postDebugMessage(message: String, value: Float, flags: Int): Unit =
  js(
    "(document.documentElement.dataset.rcPlayerDebugMessage = message, " +
      "document.documentElement.dataset.rcPlayerDebugValue = String(value), " +
      "document.documentElement.dataset.rcPlayerDebugFlags = String(flags), " +
      "console.debug('[rc-player-wasm] ' + message + ' ' + String(value)), " +
      "window.__rcPlayerPost({ type: 'cp-rc-debug-message', message: message, " +
      "value: value, flags: flags }))"
  )

private fun postHostAction(actionId: Int): Unit =
  js(
    "(document.documentElement.dataset.rcPlayerAction = String(actionId), " +
      "document.documentElement.dataset.rcPlayerActionTrace = " +
      "(document.documentElement.dataset.rcPlayerActionTrace ? " +
      "document.documentElement.dataset.rcPlayerActionTrace + ',' : '') + String(actionId), " +
      "window.__rcPlayerPost({ type: 'cp-rc-host-action', actionId: actionId }))"
  )

private fun postHostMetadataAction(actionId: Int, metadata: String): Unit =
  js(
    "(document.documentElement.dataset.rcPlayerAction = String(actionId), " +
      "document.documentElement.dataset.rcPlayerActionTrace = " +
      "(document.documentElement.dataset.rcPlayerActionTrace ? " +
      "document.documentElement.dataset.rcPlayerActionTrace + ',' : '') + String(actionId), " +
      "document.documentElement.dataset.rcPlayerMetadata = metadata, " +
      "window.__rcPlayerPost({ type: 'cp-rc-host-action', actionId: actionId, " +
      "metadata: metadata }))"
  )

private fun postHostNamedActionNone(name: String): Unit =
  js(
    "(document.documentElement.dataset.rcPlayerNamedAction = name, " +
      "document.documentElement.dataset.rcPlayerNamedActionValue = 'none', " +
      "window.__rcPlayerPost({ type: 'cp-rc-host-named-action', name: name, " +
      "valueType: 'none', value: null }))"
  )

private fun postHostNamedActionFloat(name: String, value: Float): Unit =
  js(
    "(document.documentElement.dataset.rcPlayerNamedAction = name, " +
      "document.documentElement.dataset.rcPlayerNamedActionValue = 'float:' + String(value), " +
      "window.__rcPlayerPost({ type: 'cp-rc-host-named-action', name: name, " +
      "valueType: 'float', value: value }))"
  )

private fun postHostNamedActionInt(name: String, value: Int): Unit =
  js(
    "(document.documentElement.dataset.rcPlayerNamedAction = name, " +
      "document.documentElement.dataset.rcPlayerNamedActionValue = 'int:' + String(value), " +
      "window.__rcPlayerPost({ type: 'cp-rc-host-named-action', name: name, " +
      "valueType: 'int', value: value }))"
  )

private fun postHostNamedActionText(name: String, value: String): Unit =
  js(
    "(document.documentElement.dataset.rcPlayerNamedAction = name, " +
      "document.documentElement.dataset.rcPlayerNamedActionValue = 'string:' + value, " +
      "window.__rcPlayerPost({ type: 'cp-rc-host-named-action', name: name, " +
      "valueType: 'string', value: value }))"
  )

private fun postHostNamedActionFloatList(name: String, encoded: String): Unit =
  js(
    "(document.documentElement.dataset.rcPlayerNamedAction = name, " +
      "document.documentElement.dataset.rcPlayerNamedActionValue = 'float-array:' + encoded, " +
      "window.__rcPlayerPost({ type: 'cp-rc-host-named-action', name: name, " +
      "valueType: 'float-array', " +
      "value: encoded === '' ? [] : encoded.split(',').map(Number) }))"
  )
