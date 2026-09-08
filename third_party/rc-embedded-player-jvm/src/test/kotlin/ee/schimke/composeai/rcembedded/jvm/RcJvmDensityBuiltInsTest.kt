/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ee.schimke.composeai.rcembedded.jvm

import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.core.RemoteContext
import androidx.compose.remote.core.SystemClock
import androidx.compose.remote.core.operations.Theme
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins that the density built-ins a document can *read* — `ID_DENSITY` and `ID_FONT_SIZE` — are
 * still readable from the context once [initDrawContext] has finished.
 *
 * ## Why this needs a test of its own
 *
 * Both this player and its Android sibling seeded the two built-ins **before**
 * `CoreDocument.initializeContext`, on the stated reasoning that a document reading them "must
 * resolve at the render density, not the store default". The sequencing defeated the intent:
 * `initializeContext` ends with `context.mRemoteComposeState = document.mRemoteComposeState`, and
 * every `loadFloat` lands in `mRemoteComposeState` (see `StoreBackedRemoteContext.loadFloat`). So
 * the seeded values were written into the context's *original* store and then orphaned when the
 * pointer was repointed at the document's freshly `reset()` one. Both built-ins came back as the
 * store default.
 *
 * Nothing noticed for as long as it did because no document referenced them. A capture made against
 * `RemoteDensity.from(displayInfo)` folds density and font scale into literal constants, so `[27]`
 * and `[33]` never appear in the ops. Only a capture made against `RemoteDensity.Host` writes the
 * expressions — `([33] 14.0 / [27] / 15.0 *)` for a 15sp text — and those documents rendered at the
 * store default however the render was configured, which is what made a `?fontScale=` request come
 * back byte-identical.
 *
 * Asserted on the context rather than on pixels deliberately: this is the seam that was wrong, a
 * pixel test would need skiko's natives, and a font scale that reaches the context but not the
 * glyphs would be a different bug with a different fix.
 */
class RcJvmDensityBuiltInsTest {

  @Test
  fun `font size and density survive context initialization`() {
    val context = initDrawContext(document(), SystemClock(), DENSITY, FONT_SCALE)

    assertEquals(
      "ID_FONT_SIZE must be 14sp at the render density and font scale",
      14f * FONT_SCALE * DENSITY,
      context.getFloat(RemoteContext.ID_FONT_SIZE),
      0.001f,
    )
    assertEquals(
      "ID_DENSITY must be the render density",
      DENSITY,
      context.getFloat(RemoteContext.ID_DENSITY),
      0.001f,
    )
  }

  /**
   * The font scale has to reach the context as a *scale*, not be collapsed into the density. A
   * player that seeded `14 × density` and dropped the scale would pass a test that only checked
   * `ID_DENSITY`, and would still render every document at one text size.
   */
  @Test
  fun `font size tracks the font scale independently of density`() {
    val unscaled = initDrawContext(document(), SystemClock(), DENSITY, fontScale = 1f)
    val scaled = initDrawContext(document(), SystemClock(), DENSITY, fontScale = 2f)

    assertEquals(
      "doubling the font scale must double ID_FONT_SIZE",
      2f * unscaled.getFloat(RemoteContext.ID_FONT_SIZE),
      scaled.getFloat(RemoteContext.ID_FONT_SIZE),
      0.001f,
    )
    assertEquals(
      "ID_DENSITY must not move with the font scale",
      unscaled.getFloat(RemoteContext.ID_DENSITY),
      scaled.getFloat(RemoteContext.ID_DENSITY),
      0.001f,
    )
  }

  private fun initDrawContext(
    document: CoreDocument,
    clock: SystemClock,
    density: Float,
    fontScale: Float,
  ) =
    initDrawContext(
      document = document,
      clock = clock,
      density = density,
      fontScale = fontScale,
      seeds = emptyMap(),
      theme = Theme.LIGHT,
      systemColorLookup = { null },
    )

  /**
   * The smallest document the initializer accepts — a header and nothing else. The assertions are
   * about the context's own built-ins, so the content is irrelevant; what matters is that
   * `initializeContext` runs, since that is the call the seeding had to survive.
   */
  private fun document(): CoreDocument {
    val buffer = RemoteComposeBuffer()
    buffer.header(WIDTH, HEIGHT, DENSITY, /* capabilities= */ 0L)
    val wire = buffer.buffer
    return parseDocument(wire.buffer.copyOf(wire.size()))
  }

  private companion object {
    const val WIDTH = 100
    const val HEIGHT = 100

    /** `id:pixel_5`'s density — a real one, so a regression reads as a plausible number. */
    const val DENSITY = 2.75f
    const val FONT_SCALE = 2f
  }
}
