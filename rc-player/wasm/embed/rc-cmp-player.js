/*
 * rc-cmp-player.js — include the CMP Remote Compose player in an HTML page.
 *
 *   <script src="/rc-player/rc-cmp-player.js"></script>
 *   <rc-cmp-player src="/documents/watch-face.rc" width="400" height="400"></rc-cmp-player>
 *
 *   const player = RcCmp.createPlayer(container, { width: 400, height: 400 });
 *   await player.loadFromArrayBuffer(bytes);   // resolves once the document is on screen
 *
 * The player itself is the Compose Multiplatform renderer compiled to Wasm, which runs as its own
 * page (`index.html` beside this file) inside an iframe; this script owns that iframe and the embed
 * contract it speaks (docs/design/RC_PLAYER_EMBED.md): it starts the page, waits for it to settle,
 * hands documents over with `window.rcPlayerLoad` / `window.rcPlayerLoadBytes`, and turns the
 * `data-rc-player-state` marker into promises. The API mirrors the TypeScript player's
 * `RC.createPlayer`, so a page can drive both the same way.
 *
 * The iframe must be **same-origin** with the page: the documents cross by calling into its
 * `window`. Serve this file and the rest of the distribution from your own site (the npm package's
 * `dist/`, or the GitHub release's zip); by default the player page is resolved next to this script.
 *
 * Plain script, no dependencies, no build step; defines `window.RcCmp` and `<rc-cmp-player>`.
 */
(function () {
  'use strict';

  // Resolved now: `document.currentScript` is only set while this file is first evaluated.
  var scriptBase =
    (document.currentScript && document.currentScript.src) || document.baseURI || location.href;
  var DEFAULT_PLAYER_URL = new URL('index.html', scriptBase).href;

  var POLL_MS = 50;
  var START_TIMEOUT_MS = 60000;
  var LOAD_TIMEOUT_MS = 60000;

  // Each player tags its iframe navigations, so a poll can never mistake the outgoing page — still
  // `ready` until the new one commits — for the page it asked for. The player ignores the parameter.
  var instanceCounter = 0;

  function superseded() {
    var error = new Error('superseded by a newer load');
    error.name = 'AbortError';
    return error;
  }

  function toBytes(data) {
    // A brand check, not `instanceof`, so a buffer from another frame's realm is accepted too.
    if (Object.prototype.toString.call(data) === '[object ArrayBuffer]') return new Uint8Array(data);
    if (ArrayBuffer.isView(data)) return new Uint8Array(data.buffer, data.byteOffset, data.byteLength);
    throw new TypeError('expected an ArrayBuffer or an ArrayBufferView');
  }

  /**
   * Create a CMP player inside `container`.
   *
   * Options (all optional):
   *   width, height     CSS pixels of the player; the document lays out into exactly this box.
   *   theme             'light' | 'dark' | 'auto' (default: follow prefers-color-scheme).
   *   lenient           true draws nothing for unsupported operations instead of refusing.
   *   namedValues       [{ kind, name, value }] overrides, as RC_PLAYER_EMBED.md describes.
   *   fontsBase         directory of the player's `fonts.json`, relative to the player page.
   *   handoffDelayMs    cold-start tail before `ready`; keep the default unless you composite.
   *   playerUrl         the player page; default `index.html` next to this script.
   *   src | buffer      a first document, as a URL or bytes.
   *   onLoad(info)      called after each document is on screen.
   *   onError(error)    called when a document fails to load.
   *   onEvent(message)  the player's structured messages: host actions, debug messages.
   */
  function createPlayer(container, options) {
    options = Object.assign({}, options);
    var id = 'rc-cmp-' + ++instanceCounter;
    var iframe = document.createElement('iframe');
    iframe.title = options.title || 'Remote Compose player';
    iframe.style.border = '0';
    iframe.style.display = 'block';
    iframe.style.background = 'transparent';
    iframe.setAttribute('allowtransparency', 'true');
    container.appendChild(iframe);

    var page = 0; // navigation counter, see `instanceCounter`; a navigation cancels every wait
    var generation = 0; // load counter; a newer load cancels an older one's wait
    var started = null; // promise: the current page is up and has settled once
    var current = null; // the last document requested: { url } or { bytes }
    var destroyed = false;

    function playerUrl() {
      var url = new URL(options.playerUrl || DEFAULT_PLAYER_URL, location.href);
      if (options.theme === 'light' || options.theme === 'dark') {
        url.searchParams.set('theme', options.theme);
      }
      if (options.lenient) url.searchParams.set('lenient', '1');
      if (options.namedValues) url.searchParams.set('namedValues', JSON.stringify(options.namedValues));
      if (options.fontsBase) url.searchParams.set('fontsBase', options.fontsBase);
      if (options.handoffDelayMs != null) {
        url.searchParams.set('handoffDelayMs', String(options.handoffDelayMs));
      }
      url.searchParams.set('rcEmbed', id + '.' + page);
      return url.href;
    }

    /** The player page's surface, or null while the iframe still shows another page. */
    function probe() {
      var win = iframe.contentWindow;
      var doc;
      try {
        doc = win && win.document;
      } catch (e) {
        throw new Error(
          'rc-cmp-player: the player page must be served from the same origin as this page',
        );
      }
      if (!doc || !doc.documentElement) return null;
      var tag;
      try {
        tag = new URL(doc.location.href).searchParams.get('rcEmbed');
      } catch (e) {
        return null;
      }
      if (tag !== id + '.' + page) return null;
      var data = doc.documentElement.dataset;
      return {
        win: win,
        state: data.rcPlayerState,
        error: data.rcPlayerError,
        settled: data.rcPlayerState === 'ready' || data.rcPlayerState === 'error',
        canLoad: typeof win.rcPlayerLoad === 'function',
        canLoadBytes: typeof win.rcPlayerLoadBytes === 'function',
      };
    }

    function poll(isCurrent, test, timeoutMs, timeoutMessage) {
      return new Promise(function (resolve, reject) {
        var deadline = Date.now() + timeoutMs;
        (function tick() {
          if (destroyed || !isCurrent()) return reject(superseded());
          var p;
          try {
            p = probe();
          } catch (e) {
            return reject(e);
          }
          if (p && test(p)) return resolve(p);
          if (Date.now() > deadline) return reject(new Error('rc-cmp-player: ' + timeoutMessage));
          setTimeout(tick, POLL_MS);
        })();
      });
    }

    /**
     * (Re)start the player page. It opens with no `?src=`, so it settles on `error` ("Missing
     * ?src") with the swap functions installed; documents are only handed over after that, or the
     * page's own report of the missing source could overtake them.
     */
    function navigate() {
      page += 1;
      var forPage = page;
      iframe.src = playerUrl();
      started = poll(
        function () {
          return forPage === page;
        },
        function (p) {
          return p.canLoad && p.settled;
        },
        START_TIMEOUT_MS,
        'the player page did not start within ' + START_TIMEOUT_MS / 1000 + 's',
      );
      // A rejection here is reported by whichever load awaits it.
      started.catch(function () {});
      return started;
    }

    function load(request) {
      if (destroyed) return Promise.reject(new Error('rc-cmp-player: destroyed'));
      current = request;
      generation += 1;
      var forGeneration = generation;
      var forPage = page;
      var isCurrent = function () {
        return forGeneration === generation && forPage === page;
      };
      return started
        .then(function () {
          var p = probe();
          if (!isCurrent() || !p) throw superseded();
          if (request.bytes) {
            if (!p.canLoadBytes) {
              throw new Error('rc-cmp-player: this player build has no rcPlayerLoadBytes');
            }
            // Copied synchronously; the marker is `loading` again when this returns.
            p.win.rcPlayerLoadBytes(request.bytes);
          } else {
            p.win.rcPlayerLoad(request.url);
          }
          return poll(
            isCurrent,
            function (q) {
              return q.settled;
            },
            LOAD_TIMEOUT_MS,
            'the document did not finish loading within ' + LOAD_TIMEOUT_MS / 1000 + 's',
          );
        })
        .then(
          function (p) {
            if (p.state === 'error') throw new Error(p.error || 'the player reported an error');
            var info = { iframe: iframe };
            if (options.onLoad) options.onLoad(info);
            return info;
          },
          function (error) {
            if (error && error.name !== 'AbortError' && options.onError) options.onError(error);
            throw error;
          },
        );
    }

    function onMessage(event) {
      if (event.source !== iframe.contentWindow || event.origin !== location.origin) return;
      if (options.onEvent && event.data && typeof event.data === 'object') options.onEvent(event.data);
    }
    window.addEventListener('message', onMessage);

    function applySize() {
      if (options.width != null) iframe.style.width = options.width + 'px';
      if (options.height != null) iframe.style.height = options.height + 'px';
    }

    function reloadCurrent() {
      navigate();
      if (current) return load(current);
      return started.then(function () {});
    }

    var handle = {
      iframe: iframe,
      /** Resolves when the player page is up and ready for documents. */
      ready: function () {
        return started.then(function () {});
      },
      loadFromUrl: function (url) {
        return load({ url: new URL(url, location.href).href });
      },
      loadFromArrayBuffer: function (data) {
        // Copied now, so the caller may reuse the buffer and a reload can resend it.
        return load({ bytes: toBytes(data).slice() });
      },
      loadFromBase64: function (base64) {
        var binary = atob(base64);
        var bytes = new Uint8Array(binary.length);
        for (var i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
        return load({ bytes: bytes });
      },
      /** The iframe's viewport *is* the layout size, so a resize reflows without a reload. */
      resize: function (width, height) {
        options.width = width;
        options.height = height;
        applySize();
      },
      /** `?theme` belongs to the player page, so this restarts it and reloads the document. */
      setTheme: function (theme) {
        options.theme = theme;
        return reloadCurrent();
      },
      /** Same: `?lenient` belongs to the page. */
      setLenient: function (lenient) {
        options.lenient = !!lenient;
        return reloadCurrent();
      },
      destroy: function () {
        destroyed = true;
        window.removeEventListener('message', onMessage);
        iframe.remove();
      },
    };

    applySize();
    navigate();
    if (options.buffer) handle.loadFromArrayBuffer(options.buffer).catch(function () {});
    else if (options.src) handle.loadFromUrl(options.src).catch(function () {});
    return handle;
  }

  var BaseElement = typeof HTMLElement !== 'undefined' ? HTMLElement : function () {};

  /**
   * <rc-cmp-player src width height theme lenient player-url>
   *
   * Dispatches `rc-load` and `rc-error` (detail: the Error) after each document, and `rc-event`
   * (detail: the player's message) for host actions and debug messages.
   */
  class RcCmpPlayerElement extends BaseElement {
    static get observedAttributes() {
      return ['src', 'width', 'height', 'theme', 'lenient'];
    }

    connectedCallback() {
      if (this._handle) return;
      var self = this;
      var shadow = this.shadowRoot || this.attachShadow({ mode: 'open' });
      shadow.innerHTML = '<style>:host{display:inline-block}</style>';
      this._handle = createPlayer(shadow, {
        width: this._number('width', 400),
        height: this._number('height', 400),
        theme: this.getAttribute('theme') || undefined,
        lenient: this.hasAttribute('lenient'),
        playerUrl: this.getAttribute('player-url') || undefined,
        onLoad: function () {
          self.dispatchEvent(new CustomEvent('rc-load'));
        },
        onError: function (error) {
          self.dispatchEvent(new CustomEvent('rc-error', { detail: error }));
        },
        onEvent: function (message) {
          self.dispatchEvent(new CustomEvent('rc-event', { detail: message }));
        },
      });
      var src = this.getAttribute('src');
      if (src) this._handle.loadFromUrl(src).catch(function () {});
    }

    disconnectedCallback() {
      if (this._handle) this._handle.destroy();
      this._handle = null;
    }

    attributeChangedCallback(name, oldValue, value) {
      var handle = this._handle;
      if (!handle || oldValue === value) return;
      if (name === 'src' && value) handle.loadFromUrl(value).catch(function () {});
      else if (name === 'width' || name === 'height') {
        handle.resize(this._number('width', 400), this._number('height', 400));
      } else if (name === 'theme') handle.setTheme(value || 'auto').catch(function () {});
      else if (name === 'lenient') handle.setLenient(value !== null).catch(function () {});
    }

    /** The underlying handle, for loading bytes: `element.player.loadFromArrayBuffer(buffer)`. */
    get player() {
      return this._handle;
    }

    _number(name, fallback) {
      var value = parseInt(this.getAttribute(name) || '', 10);
      return value > 0 ? value : fallback;
    }
  }

  window.RcCmp = {
    createPlayer: createPlayer,
    RcCmpPlayerElement: RcCmpPlayerElement,
    defaultPlayerUrl: DEFAULT_PLAYER_URL,
  };
  if (typeof customElements !== 'undefined' && !customElements.get('rc-cmp-player')) {
    customElements.define('rc-cmp-player', RcCmpPlayerElement);
  }
})();
