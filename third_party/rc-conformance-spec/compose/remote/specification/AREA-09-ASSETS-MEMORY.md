# AREA-09: Asset Pipeline & Memory Management Specification

**Document Version:** 1.0.0  
**Target Runtimes:** Android View Player, Jetpack Compose Player  
**Normative Status:** Core Specification  

---

## 1. Overview

RemoteCompose documents can package rich visual media, including embedded raster images, downloadable remote network images, and offscreen render buffers. 

To maintain strict performance guarantees on memory-constrained platforms, the player implements an asset pipeline governed by background decoding, pre-computed document preparation, memory quotas, and hardware-accelerated scaling modes.

---

## 2. Bitmap Encoding & Delivery Models

Bitmaps enter the player through the unified **`DATA_BITMAP` (Opcode 101)** operation, implemented in `BitmapData.java`.

### 2.1 Wire Layout & Packaging
* **`mImageId: INT`**: Document-unique asset identifier.
* **`mTypeAndWidth: INT`**: High 16 bits encode `mType` (`short`), low 16 bits encode `mImageWidth` (`short`).
* **`mEncodingAndHeight: INT`**: High 16 bits encode `mEncoding` (`short`), low 16 bits encode `mImageHeight` (`short`).
* **`mBitmap: BUFFER`**: Byte payload representing the image data, URL string, or file path.

### 2.2 Storage Encodings (`mEncoding`)
| Encoding | Value | Description |
| :--- | :--- | :--- |
| `ENCODING_INLINE` | `0` | Image data is compressed or raw byte payload embedded directly in the binary wire buffer. |
| `ENCODING_URL` | `1` | Payload is a UTF-8 URL string for asynchronous remote fetching via the host's `BitmapLoader`. |
| `ENCODING_FILE` | `2` | Payload is a UTF-8 file path string referencing local filesystem storage. |
| `ENCODING_EMPTY` | `3` | Allocates a new blank bitmap buffer initialized to zero of the declared dimensions. |

### 2.3 Pixel & Compression Types (`mType`)
| Type | Value | Description |
| :--- | :--- | :--- |
| `TYPE_PNG_8888` | `0` | Standard PNG compressed format decoded into 32-bit `ARGB_8888` raster. |
| `TYPE_PNG` | `1` | Generic PNG stream (not intended for inline raw buffers). |
| `TYPE_RAW8` | `2` | Uncompressed 8-bit single-channel grayscale or alpha mask buffer. |
| `TYPE_RAW8888` | `3` | Uncompressed 32-bit `ARGB_8888` raw pixel array. |
| `TYPE_PNG_ALPHA_8` | `4` | PNG compressed stream decoded into hardware-optimized single-channel `ALPHA_8`. |

---

## 3. Document Preparation & Pre-Decoding

Large documents containing substantial image data can introduce frame drops if images are decoded synchronously during initial playback.

```
[Raw RemoteDocument Ingested]
              |
              v
[player.shouldPrepare(doc)?]
    ├── Size of images <= 1,000 bytes: Inline playback (prepare skipped)
    └── Size of images > 1,000 bytes: Asynchronous Preparation
              |
              v
[Background Worker Thread: prepareDocument(doc)]
    ├── Pre-decodes all compressed raster payloads into GPU-friendly textures
    ├── Allocates resolved cache table (RemotePreparedDocument)
    └── Signals completion to UI Main Thread
              |
              v
[player.setPreparedDocument(preparedDoc)] -> Zero-jank initial frame
```

1. **`shouldPrepare(doc)`**: Returns `true` if `doc.getDocument().getDocInfo().getSizeOfImages() > 1000` bytes.
2. **`prepareDocument(doc)`**: In background worker, uses `context.getBitmapLoader()` to pre-decode all raster assets into a `RemotePreparedDocument`.
3. **`setPreparedDocument(preparedDoc)`**: Swaps prepared assets into the active player with zero main-thread decode jank.

---

## 4. Image Rendering & Scaling Modes

### 4.1 DrawBitmapScaled (`Opcode 149`)
Renders a cached bitmap using `ImageScaling.java` with standardized geometric policies:

| Scale Mode | Value | Geometric Policy |
| :--- | :--- | :--- |
| `SCALE_NONE` | `0` | No scaling applied; renders at native pixel dimensions. |
| `SCALE_INSIDE` | `1` | Scales down uniformly only if image exceeds target bounds; does not upscale smaller images. |
| `SCALE_FILL_WIDTH` | `2` | Scales uniformly so source width exactly matches target bounds width. |
| `SCALE_FILL_HEIGHT` | `3` | Scales uniformly so source height exactly matches target bounds height. |
| `SCALE_FIT` | `4` | Uniform scale fitting entire image within destination rectangle (`min(scaleX, scaleY)`). |
| `SCALE_CROP` | `5` | Uniform scale covering entire destination rectangle (`max(scaleX, scaleY)`); excess is centered and cropped. |
| `SCALE_FILL_BOUNDS` | `6` | Non-uniform stretch to completely match destination rectangle (`dstWidth` by `dstHeight`). |
| `SCALE_FIXED_SCALE` | `7` | Explicit fixed scaling multiplier applied. |

---

## 5. Offscreen Render Targets (`DRAW_TO_BITMAP`, Opcode 190)

Documents can redirect drawing operations to an offscreen bitmap texture via `DrawToBitmap`:
* **Wire Fields**:
  * `bitmapId: INT`: The bitmap ID to redirect drawing to, or `0` to restore drawing back to the primary canvas.
  * `mode: INT`: Bitmask configuration flags (`MODE_NO_INITIALIZE = 1`).
  * `color: INT`: Initial background / clear color for the bitmap surface.
* **Execution**:
  1. If `bitmapId != 0`, canvas context is redirected to the allocated offscreen bitmap matching `bitmapId`.
  2. If `mode` does not include `MODE_NO_INITIALIZE`, the bitmap is cleared with `color`.
  3. Subsequent paint operations rasterize into the offscreen bitmap buffer.
  4. Issuing `DrawToBitmap(0, ...)` pops the offscreen redirect and restores drawing to the main canvas.

---

## 6. Resource Quotas & Memory Protection (`Limits.java`)

To prevent out-of-memory (OOM) crashes and DoS vulnerabilities caused by untrusted or oversized documents:

### 6.1 Strict Quota Invariants
* **`MAX_BITMAP_MEMORY` (Default: 20 MB / `20 * 1024 * 1024` bytes)**: The total memory consumed by all active bitmaps in a single player instance must not exceed 20 MB:
  $$\sum_{i} (\text{width}_i \times \text{height}_i \times 4) \le \text{MAX\_BITMAP\_MEMORY}$$
  If a document attempts to decode assets beyond this limit, subsequent decodes fail gracefully, and a placeholder checkerboard or error color is rendered.
* **`MAX_IMAGE_DIMENSION` (Default: 8000 px)**: Bitmaps declaring width or height exceeding 8000 pixels are rejected immediately without attempting allocation.
* **`MAX_IMAGE_HEADER_SIZE` (Default: 10,000 bytes)**: Maximum allowable header byte length during bitmap parsing.
* **`MAX_CACHE_ENTRIES` (Default: 20)**: Maximum number of cached items in player-side LRU caches.
* **`ENABLE_IMAGE_URLS` (Default: `false`)**: Security toggle gating remote network fetching of images.
* **`ENABLE_IMAGE_FILES` (Default: `false`)**: Security toggle gating local file-system image loading.

---

## 7. Conformance Requirements

1. **Dimension Clamping**: Attempting to allocate an image with dimension `> MAX_IMAGE_DIMENSION` (8000 px) must throw a recoverable decode error and not crash the process.
2. **Crop Scaling Centering**: `SCALE_CROP (5)` mode must ensure that cropped margins are symmetrically divided between opposite sides (e.g., $(W_{\text{scaled}} - W_{\text{target}}) / 2$).
3. **Memory Recycling**: Replacing an existing bitmap via `updateDocument` or clearing cache entries must immediately release the native memory of the superseded texture.
4. **Offscreen Canvas Restoration**: Any document entering an offscreen buffer with `DRAW_TO_BITMAP` must pair it with a restoration operation (`bitmapId = 0`); the player must automatically restore the main canvas if an unclosed offscreen target exists at end-of-document.
