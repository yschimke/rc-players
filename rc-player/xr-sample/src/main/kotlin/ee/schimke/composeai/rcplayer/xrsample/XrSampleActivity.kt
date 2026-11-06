package ee.schimke.composeai.rcplayer.xrsample

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.xr.arcore.RenderViewpoint
import androidx.xr.runtime.Config
import androidx.xr.runtime.DeviceTrackingMode
import androidx.xr.runtime.Session
import androidx.xr.runtime.SessionConfigureSuccess
import androidx.xr.runtime.SessionCreateSuccess
import androidx.xr.scenecore.Entity
import androidx.xr.scenecore.SpatialCapability
import androidx.xr.scenecore.scene
import ee.schimke.composeai.rcplayer.compose.LocalRcPlayerExtensions
import ee.schimke.composeai.rcplayer.compose.RcComposePlayer
import ee.schimke.composeai.rcplayer.compose.RcPlayerExtensions
import ee.schimke.composeai.rcplayer.threed.Rc3DExtension
import ee.schimke.composeai.rcplayer.threed.Rc3DOptions
import ee.schimke.composeai.rcplayer.threed.Rc3DRenderer
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.launch

/**
 * Plays [RcXrSampleDocument] on the main panel. On an XR device that allows 3D content, its rigid
 * meshes are handed to SceneCore by [RcSceneCoreHost] and float in the room around the panel, and
 * deforming ones are rendered per eye into a stereo surface over the player, following the head
 * (which needs head tracking, and so the `HEAD_TRACKING` permission); without head tracking they
 * stay on the panel. Everywhere else (a phone, or Home Space without 3D content) the player draws
 * every mesh on the panel through the GPU path: the level-1 fallback in
 * docs/design/RC_3D_EXTENSION.md.
 */
class XrSampleActivity : ComponentActivity() {
  private val canShow3D = mutableStateOf(false)
  private val host = mutableStateOf<RcSceneCoreHost?>(null)

  /** The player's bounds in the window's pixels, which on XR are the main panel's pixels. */
  @Volatile private var playerBounds: Rect? = null

  /** Both eyes, once device tracking is configured; null until then, or if it is refused. */
  @Volatile private var viewpoints: Pair<RenderViewpoint, RenderViewpoint>? = null

  private var session: Session? = null

  private val headTracking =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) session?.let(::startDeviceTracking)
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val bytes = RcXrSampleDocument.bytes()
    setContent {
      XrSampleScreen(bytes, host.value.takeIf { canShow3D.value }) { playerBounds = it }
    }
    lifecycleScope.launch {
      // Fails, rather than throws, on a device without XR: the player then draws everything.
      val result =
        Session.create(this@XrSampleActivity, EmptyCoroutineContext, this@XrSampleActivity)
      val session = (result as? SessionCreateSuccess)?.session ?: return@launch
      this@XrSampleActivity.session = session
      val panel = session.scene.mainPanelEntity
      host.value =
        RcSceneCoreHost(
          session,
          panel,
          lifecycleScope,
          viewport = {
            playerBounds?.let { bounds ->
              val px = panel.sizeInPixels
              val meters = panel.size
              RcXrViewport.of(
                bounds.left,
                bounds.top,
                bounds.right,
                bounds.bottom,
                px.width,
                px.height,
                meters.width,
                meters.height,
              )
            }
          },
          eyes = { eyePositions(session, panel) },
          contentAllowed = { canShow3D.value },
        )
      if (checkSelfPermission(HEAD_TRACKING) == PackageManager.PERMISSION_GRANTED) {
        startDeviceTracking(session)
      } else {
        headTracking.launch(HEAD_TRACKING)
      }
      canShow3D.value = SpatialCapability.SPATIAL_3D_CONTENT in session.scene.spatialCapabilities
      session.scene.addSpatialCapabilitiesChangedListener { capabilities ->
        canShow3D.value = SpatialCapability.SPATIAL_3D_CONTENT in capabilities
      }
      // 3D content needs Full Space; Home Space keeps to panels.
      session.scene.requestFullSpace()
    }
  }

  /**
   * Turn on device tracking, which the eyes' poses come from. Any failure leaves [viewpoints] null,
   * and deforming meshes on the panel.
   */
  private fun startDeviceTracking(session: Session) {
    val configured = runCatching {
      session.configure(
        Config.Builder(session.config).setDeviceTracking(DeviceTrackingMode.SPATIAL).build(),
      )
    }
      .getOrNull()
    if (configured !is SessionConfigureSuccess) return
    viewpoints =
      runCatching { RenderViewpoint.left(session) to RenderViewpoint.right(session) }.getOrNull()
  }

  /** Each eye's latest pose, from perception space into [panel]'s. */
  private fun eyePositions(session: Session, panel: Entity): RcEyePositions? {
    val (left, right) = viewpoints ?: return null
    return runCatching {
      val perception = session.scene.perceptionSpace
      val l = perception.transformPoseTo(left.state.value.pose, panel).translation
      val r = perception.transformPoseTo(right.state.value.pose, panel).translation
      RcEyePositions(l.x, l.y, l.z, r.x, r.y, r.z)
    }
      .getOrNull()
  }

  override fun onDestroy() {
    host.value?.close()
    host.value = null
    viewpoints = null
    session = null
    super.onDestroy()
  }

  private companion object {
    /** Android XR's permission for the head's (and so the eyes') pose. */
    const val HEAD_TRACKING = "android.permission.HEAD_TRACKING"
  }
}

@Composable
private fun XrSampleScreen(
  bytes: ByteArray,
  host: RcSceneCoreHost?,
  onPlayerBounds: (Rect) -> Unit,
) {
  val extensions =
    remember(host) {
      val options =
        if (host != null) Rc3DOptions(drawInPlayer = false, sceneListener = host)
        else Rc3DOptions(renderer = Rc3DRenderer.Gpu)
      RcPlayerExtensions(Rc3DExtension(options))
    }
  Column(Modifier.fillMaxSize().background(Color(0xFF101418)).padding(16.dp)) {
    CompositionLocalProvider(LocalRcPlayerExtensions provides extensions) {
      RcComposePlayer(
        bytes,
        Modifier.fillMaxWidth().aspectRatio(1f).onGloballyPositioned {
          onPlayerBounds(it.boundsInWindow())
        },
      )
    }
    BasicText(
      if (host != null) {
        "Rigid meshes are handed to SceneCore and float in the room around this panel. " +
          "Deforming ones are drawn per eye into a stereo surface over the player, or stay " +
          "flat on the panel without head tracking."
      } else {
        "No XR 3D content here: the player draws every mesh on the panel."
      },
      Modifier.padding(top = 12.dp),
      style = TextStyle(color = Color(0xFFB0B8C4)),
    )
  }
}
