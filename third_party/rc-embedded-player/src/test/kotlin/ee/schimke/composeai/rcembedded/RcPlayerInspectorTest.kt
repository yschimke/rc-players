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

package ee.schimke.composeai.rcembedded

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.remote.player.core.RemoteDocument
import androidx.compose.remote.player.core.platform.AndroidRemoteContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcembedded.player.ExperimentalRemoteDocumentPlayer
import ee.schimke.composeai.rcembedded.player.LocalRcPlayerInspector
import ee.schimke.composeai.rcembedded.player.RcPlayerInspector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The inspection seam, exercised through a **composed** player.
 *
 * `RcPlayerInspector` is the player's zero-cost inspection hook (`a209d5ebe`): null by default, so
 * an ordinary player allocates nothing and pays no layout/draw overhead; installed through
 * `LocalRcPlayerInspector`, it records the Compose-native layout coordinates and exposes the
 * laid-out component tree and the document's state probes.
 *
 * It exists for the conformance harness: the corpus asserts a laid-out `tree` and a set of scalar
 * values, and a composed player is the only place those are real. This test proves the seam carries
 * that data in this tree — a real fixture, composed for real, with the tree read back out of the
 * inspector.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "xhdpi")
class RcPlayerInspectorTest {

  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  private fun renderWithInspector(fixture: String): Pair<RcPlayerInspector, RemoteDocument> {
    val bytes =
      checkNotNull(javaClass.getResourceAsStream("/rc-fixtures/$fixture")) {
          "missing fixture /rc-fixtures/$fixture"
        }
        .use { it.readBytes() }

    val inspector = RcPlayerInspector()
    val remoteDocument = RemoteDocument(bytes)
    composeRule.setContent {
      val documentDensity = Density(DENSITY, LocalDensity.current.fontScale)
      CompositionLocalProvider(
        LocalDensity provides documentDensity,
        LocalRcPlayerInspector provides inspector,
      ) {
        Box(
          Modifier.size(
            with(documentDensity) { WIDTH.toDp() },
            with(documentDensity) { HEIGHT.toDp() },
          )
        ) {
          ExperimentalRemoteDocumentPlayer(
            document = remoteDocument,
            modifier = Modifier.fillMaxSize(),
          )
        }
      }
    }
    composeRule.waitForIdle()

    val root = composeRule.activity.findViewById<ViewGroup>(android.R.id.content)
    root.measure(
      View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY),
    )
    root.layout(0, 0, WIDTH, HEIGHT)
    composeRule.waitForIdle()
    return inspector to remoteDocument
  }

  @Test
  fun theInspectorCapturesTheLaidOutTreeFromAComposedPlayer() {
    val (inspector, remoteDocument) = renderWithInspector("ImageBackgroundRemoteButton-454x200.rc")

    val tree =
      inspector.captureTreeSnapshot(
        document = remoteDocument.document,
        remoteContext = AndroidRemoteContext(),
      )

    assertTrue("the inspector captured no tree at all", tree.isNotEmpty())
    val root = tree.first()
    assertTrue("root geometry is not laid out: $root", root.width > 0f && root.height > 0f)
    assertEquals("root node should not be reported gone", false, root.isGone)
    // Every node carries a real id, kind and visibility, which is what the corpus's tree assertion
    // reads.
    tree.forEach { node ->
      assertTrue("node has no kind: $node", node.kind.isNotEmpty())
      assertNotNull(node.visibility)
    }
  }

  @Test
  fun anUninstalledInspectorIsNull() {
    // The zero-cost claim: with no inspector provided, the local is null and no recording happens.
    // Pinned because the design rests on it — an inspector threaded through every component would
    // be a per-frame cost on documents that never inspect anything.
    var seen: RcPlayerInspector? = null
    composeRule.setContent { seen = LocalRcPlayerInspector.current }
    composeRule.waitForIdle()
    assertEquals(null, seen)
  }

  private companion object {
    const val WIDTH = 454
    const val HEIGHT = 200
    const val DENSITY = 1f
  }
}
