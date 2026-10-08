/*
 * rc-cmp-player.js — include the CMP Remote Compose player in an HTML page.
 *
 *   <script src="https://cdn.example/rc-player/rc-cmp-player.js"></script>
 *   <rc-cmp-player src="/documents/watch-face.rc" width="400" height="400"></rc-cmp-player>
 *
 *   const player = RcCmp.createPlayer(container, { width: 400, height: 400 });
 *   await player.loadFromArrayBuffer(bytes);   // resolves once the document is on screen
 *
 * The player itself is the Compose Multiplatform renderer compiled to Wasm, which runs as its own
 * page (`index.html` beside this file) inside an iframe; this script owns that iframe and the embed
 * contract it speaks (docs/design/RC_PLAYER_EMBED.md). It talks to the page only by `postMessage`
 * — `rc-player-hello` until the page answers, then `rc-player-load` per document, each settled by an
 * `rc-player-state` reply carrying its id — so the player may be served from **any origin**, a CDN
 * included, and the page including it needs no access to the iframe. The API mirrors the
 * TypeScript player's `RC.createPlayer`, so a page can drive both the same way.
 *
 * Documents always cross as bytes. `loadFromUrl` fetches on *this* page, with this page's
 * credentials and origin, so a document beside the page needs no CORS headers for the player.
 *
 * By default the player page is resolved next to this script. Wherever it is served from, it must
 * come back as `text/html`, with `.wasm` as `application/wasm`, and be allowed in a frame.
 *
 * Plain script, no dependencies, no build step; defines `window.RcCmp` and `<rc-cmp-player>`.
 */
(function () {
  'use strict';

  // Resolved now: `document.currentScript` is only set while this file is first evaluated.
  var scriptBase =
    (document.currentScript && document.currentScript.src) || document.baseURI || location.href;
  var DEFAULT_PLAYER_URL = new URL('index.html', scriptBase).href;

  var HELLO_INTERVAL_MS = 100;
  var START_TIMEOUT_MS = 60000;
  var LOAD_TIMEOUT_MS = 60000;

  var instanceCounter = 0;

  function superseded() {
    var error = new Error('superseded by a newer load');
    error.name = 'AbortError';
    return error;
  }

  /** A fresh copy of the bytes, which this script owns and may transfer to the player. */
  function copyBytes(data) {
    // A brand check, not `instanceof`, so a buffer from another frame's realm is accepted too.
    if (Object.prototype.toString.call(data) === '[object ArrayBuffer]') {
      return new Uint8Array(data).slice();
    }
    if (ArrayBuffer.isView(data)) {
      return new Uint8Array(data.buffer, data.byteOffset, data.byteLength).slice();
    }
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
    var instance = 'rc-cmp-' + ++instanceCounter;
    var playerOrigin = new URL(options.playerUrl || DEFAULT_PLAYER_URL, location.href).origin;

    var iframe = null; // replaced on every navigation, so its window identifies the page
    var page = 0; // navigation counter; a navigation cancels every wait on the old page
    var generation = 0; // load counter; a newer load cancels an older one's wait
    var started = null; // promise: the current page answered `rc-player-hello`
    var onHello = null; // resolver for `started`, keyed to the hello id
    var pending = null; // { id, resolve, reject } of the load awaiting its `rc-player-state`
    var current = null; // the last document requested, as bytes, kept for reloads
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
      return url.href;
    }

    function applySize() {
      if (!iframe) return;
      if (options.width != null) iframe.style.width = options.width + 'px';
      if (options.height != null) iframe.style.height = options.height + 'px';
    }

    function settlePending(error) {
      if (!pending) return;
      var settling = pending;
      pending = null;
      settling.reject(error);
    }

    function onMessage(event) {
      if (!iframe || event.source !== iframe.contentWindow || event.origin !== playerOrigin) return;
      var data = event.data;
      if (!data || typeof data !== 'object') return;
      if (data.type === 'rc-player-hello') {
        if (onHello && data.id === onHello.id) onHello.resolve(data);
      } else if (data.type === 'rc-player-state') {
        if (!pending || data.id !== pending.id) return; // an earlier load's answer
        var settling = pending;
        pending = null;
        if (data.state === 'ready') settling.resolve();
        else settling.reject(new Error(data.error || 'the player reported an error'));
      } else if (
        options.onEvent &&
        typeof data.type === 'string' &&
        data.type.indexOf('cp-rc-') === 0
      ) {
        options.onEvent(data);
      }
    }
    window.addEventListener('message', onMessage);

    /**
     * (Re)start the player page in a new iframe and say hello until it answers. The page only
     * installs its listener once the Wasm module is running, so the first hellos go unheard; and a
     * hello posted while the frame still holds `about:blank` is dropped by the target origin.
     */
    function navigate() {
      page += 1;
      var forPage = page;
      settlePending(superseded());
      var next = document.createElement('iframe');
      next.title = options.title || 'Remote Compose player';
      next.style.border = '0';
      next.style.display = 'block';
      next.style.background = 'transparent';
      next.setAttribute('allowtransparency', 'true');
      next.src = playerUrl();
      if (iframe) iframe.replaceWith(next);
      else container.appendChild(next);
      iframe = next;
      applySize();

      var helloId = instance + '.hello.' + forPage;
      started = new Promise(function (resolve, reject) {
        var deadline = Date.now() + START_TIMEOUT_MS;
        onHello = { id: helloId, resolve: resolve };
        (function hello() {
          if (destroyed || forPage !== page) return reject(superseded());
          if (!onHello || onHello.id !== helloId) return; // answered
          if (Date.now() > deadline) {
            return reject(
              new Error(
                'rc-cmp-player: the player page at ' +
                  playerOrigin +
                  ' did not answer within ' +
                  START_TIMEOUT_MS / 1000 +
                  's; check it is served as text/html (and .wasm as application/wasm) and may ' +
                  'be framed',
              ),
            );
          }
          try {
            next.contentWindow.postMessage({ type: 'rc-player-hello', id: helloId }, playerOrigin);
          } catch (e) {
            // The frame is between documents; the next tick tries again.
          }
          setTimeout(hello, HELLO_INTERVAL_MS);
        })();
      }).then(function (reply) {
        if (onHello && onHello.id === helloId) onHello = null;
        return reply;
      });
      // A rejection here is reported by whichever load awaits it.
      started.catch(function () {});
      return started;
    }

    function load(bytes) {
      if (destroyed) return Promise.reject(new Error('rc-cmp-player: destroyed'));
      current = bytes;
      generation += 1;
      var forGeneration = generation;
      var forPage = page;
      settlePending(superseded());
      return started
        .then(function () {
          if (destroyed || forGeneration !== generation || forPage !== page) throw superseded();
          var id = instance + '.load.' + forGeneration;
          return new Promise(function (resolve, reject) {
            var timer = setTimeout(function () {
              if (pending && pending.id === id) {
                pending = null;
                reject(
                  new Error(
                    'rc-cmp-player: the document did not finish loading within ' +
                      LOAD_TIMEOUT_MS / 1000 +
                      's',
                  ),
                );
              }
            }, LOAD_TIMEOUT_MS);
            pending = {
              id: id,
              resolve: function () {
                clearTimeout(timer);
                resolve();
              },
              reject: function (error) {
                clearTimeout(timer);
                reject(error);
              },
            };
            // Our own copy each time, transferred rather than cloned; `current` stays intact for
            // a reload.
            var payload = bytes.slice();
            iframe.contentWindow.postMessage(
              { type: 'rc-player-load', id: id, bytes: payload.buffer },
              playerOrigin,
              [payload.buffer],
            );
          });
        })
        .then(function () {
          return { iframe: iframe };
        })
        // A separate stage, so the player's own `error` above reaches `onError` too: a rejection
        // handler never sees what its sibling fulfilment handler throws.
        .then(
          function (info) {
            if (options.onLoad) options.onLoad(info);
            return info;
          },
          function (error) {
            if (error && error.name !== 'AbortError' && options.onError) options.onError(error);
            throw error;
          },
        );
    }

    function reloadCurrent() {
      navigate();
      if (current) return load(current);
      return started.then(function () {});
    }

    function failed(error) {
      if (options.onError) options.onError(error);
      return Promise.reject(error);
    }

    var handle = {
      /** The current iframe; replaced when the player page restarts (theme, lenient). */
      get iframe() {
        return iframe;
      },
      /** Resolves when the player page is up and ready for documents. */
      ready: function () {
        return started.then(function () {});
      },
      /** Fetched here, on the including page, then handed over as bytes. */
      loadFromUrl: function (url) {
        var forGeneration = ++generation;
        settlePending(superseded());
        return fetch(new URL(url, location.href).href).then(
          function (response) {
            if (!response.ok) return failed(new Error('HTTP ' + response.status + ' for ' + url));
            return response.arrayBuffer().then(function (buffer) {
              // A newer request made while this one was downloading wins.
              if (forGeneration !== generation) throw superseded();
              return load(new Uint8Array(buffer));
            });
          },
          function (error) {
            return failed(error);
          },
        );
      },
      loadFromArrayBuffer: function (data) {
        var bytes;
        try {
          bytes = copyBytes(data);
        } catch (error) {
          return Promise.reject(error);
        }
        return load(bytes);
      },
      loadFromBase64: function (base64) {
        var binary = atob(base64);
        var bytes = new Uint8Array(binary.length);
        for (var i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
        return load(bytes);
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
        settlePending(superseded());
        window.removeEventListener('message', onMessage);
        if (iframe) iframe.remove();
        iframe = null;
      },
    };

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
