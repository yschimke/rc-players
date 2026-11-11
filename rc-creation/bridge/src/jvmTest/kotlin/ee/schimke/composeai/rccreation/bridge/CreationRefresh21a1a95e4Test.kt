package ee.schimke.composeai.rccreation.bridge

import androidx.compose.remote.creation.compose.capture.RemoteCreationDisplayInfo
import androidx.compose.remote.creation.compose.capture.captureCommonRemoteDocument
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.remoteComponent
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.graphicsLayer
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.shapes.RemoteCircleShape
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.RemoteCompositingStrategy
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemoteState
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rememberNamedRemoteColor
import androidx.compose.remote.creation.compose.state.rememberNamedRemoteDp
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.text.RemoteFontVariation
import androidx.compose.runtime.Composable
import ee.schimke.composeai.rcplayer.protocol.RcBoxLayout
import ee.schimke.composeai.rcplayer.protocol.RcColorConstant
import ee.schimke.composeai.rcplayer.protocol.RcColorExpression
import ee.schimke.composeai.rcplayer.protocol.RcComponentValue
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcNamedVariable
import ee.schimke.composeai.rcplayer.protocol.RcOperation
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * The API the fork took from androidx-main `1448d6497..21a1a95e4`, after AndroidX's own tests for
 * it (`RemoteStateDomainTest`, `RemoteFontVariationTest`, `RemoteComponentTest`,
 * `RemoteColorTest`).
 */
class CreationRefresh21a1a95e4Test {
  private fun capture(content: @Composable @RemoteComposable () -> Unit): ByteArray = runBlocking {
    captureCommonRemoteDocument(RemoteCreationDisplayInfo(360, 480, 160), content = content)
  }

  private fun operations(content: @Composable @RemoteComposable () -> Unit): List<RcOperation> =
    RcDocumentCodec.decode(capture(content)).operations

  // de4a9909e: custom RemoteState.Domain namespaces.

  private object HomeDomain : RemoteState.Domain("HOME")

  @Test
  fun domainsNameAndPrefixTheirVariables() {
    val home = RemoteState.Domain("HOME")
    assertEquals("USER", RemoteState.Domain.User.toString())
    assertEquals("SYSTEM", RemoteState.Domain.System.toString())
    assertEquals("", RemoteState.Domain.None.toString())
    assertEquals("HOME", home.toString())
    assertEquals("HOME", HomeDomain.toString())

    assertEquals("USER:shutter.is_open", RemoteState.Domain.User.prefixed("shutter.is_open"))
    assertEquals("SYSTEM:heart_rate_bpm", RemoteState.Domain.System.prefixed("heart_rate_bpm"))
    assertEquals("raw_value", RemoteState.Domain.None.prefixed("raw_value"))
    assertEquals("HOME:sun.elevation", home.prefixed("sun.elevation"))
  }

  @Test
  fun domainsAreEqualByNamespace() {
    val home = RemoteState.Domain("HOME")
    assertEquals<RemoteState.Domain>(RemoteState.Domain.User, RemoteState.Domain("USER"))
    assertEquals(RemoteState.Domain.User.hashCode(), RemoteState.Domain("USER").hashCode())
    assertEquals<RemoteState.Domain>(RemoteState.Domain.System, RemoteState.Domain("SYSTEM"))
    assertEquals<RemoteState.Domain>(RemoteState.Domain.None, RemoteState.Domain(null))
    assertEquals(RemoteState.Domain.None.hashCode(), RemoteState.Domain(null).hashCode())
    assertEquals(home, RemoteState.Domain("HOME"))
    assertEquals(home, HomeDomain)
    assertEquals(home.hashCode(), HomeDomain.hashCode())
    assertNotEquals<RemoteState.Domain>(RemoteState.Domain.User, home)
    assertNotEquals<RemoteState.Domain>(RemoteState.Domain.System, home)
    assertNotEquals<RemoteState.Domain>(RemoteState.Domain.None, home)
  }

  @Test
  fun aDomainNamespaceIsNeitherEmptyNorQualified() {
    assertFailsWith<IllegalArgumentException> { RemoteState.Domain("") }
    assertFailsWith<IllegalArgumentException> { RemoteState.Domain("HOME:SUB") }
    assertFailsWith<IllegalArgumentException> { RemoteState.Domain(":HOME") }
  }

  @Test
  fun namedStatesAcrossDomainsWriteDistinctQualifiedVariables() {
    val names = operations {
      val card = RemoteFloat.createNamedRemoteFloat("temperature", 21.5f)
      val home = RemoteFloat.createNamedRemoteFloat("temperature", 14f, RemoteState.Domain("HOME"))
      // The same (domain, name) read again is the same variable, whichever instance names it.
      val homeAgain = RemoteFloat.createNamedRemoteFloat("temperature", 14f, HomeDomain)
      val raw = RemoteFloat.createNamedRemoteFloat("temperature", 20f, RemoteState.Domain.None)
      RemoteBox(
        RemoteModifier.size(40.rdp)
          .graphicsLayer(translationX = card + home, translationY = homeAgain + raw),
      ) {}
    }
      .filterIsInstance<RcNamedVariable>()
      .map { it.name }
    assertEquals(listOf("HOME:temperature", "USER:temperature", "temperature"), names.sorted())
  }

  // 6979e8551: RemoteFontVariation.weight / width / slant.

  @Test
  fun fontVariationFactoriesNameTheirAxes() {
    assertEquals(RemoteFontVariation.Setting("wght", 500f.rf), RemoteFontVariation.weight(500.rf))
    assertEquals(RemoteFontVariation.Setting("wdth", 80f.rf), RemoteFontVariation.width(80f.rf))
    assertEquals(
      RemoteFontVariation.Setting("slnt", (-10f).rf),
      RemoteFontVariation.slant((-10f).rf),
    )
    assertNotEquals(RemoteFontVariation.weight(400f.rf), RemoteFontVariation.width(400f.rf))

    val named = RemoteFloat.createNamedRemoteFloat("weight", 600f)
    val setting = RemoteFontVariation.weight(named)
    assertEquals("wght", setting.axisName)
    assertSame(named, setting.value)
    assertEquals("wdth", RemoteFontVariation.width(named).axisName)
    assertEquals("slnt", RemoteFontVariation.slant(named).axisName)
  }

  // 02bc68077: RemoteComponent.positionInParent / positionInRoot.

  @Test
  fun aComponentsPositionsAreItsOwnComponentValues() {
    val operations = operations {
      RemoteBox(RemoteModifier.size(40.rdp)) {
        val component = remoteComponent()
        val inParent = component.positionInParent
        val inRoot = component.positionInRoot
        RemoteBox(
          RemoteModifier.size(10.rdp)
            .graphicsLayer(
              translationX = inParent.x + inRoot.x,
              translationY = inParent.y + inRoot.y,
            ),
        ) {}
      }
    }
    val boxes = operations.filterIsInstance<RcBoxLayout>()
    val outer = boxes[boxes.size - 2].componentId
    val values = operations.filterIsInstance<RcComponentValue>()
    assertEquals(
      setOf(
        RcComponentValue.LOCAL_X,
        RcComponentValue.LOCAL_Y,
        RcComponentValue.ROOT_X,
        RcComponentValue.ROOT_Y,
      ),
      values.map { it.type }.toSet(),
    )
    assertEquals(setOf(outer), values.map { it.componentId }.toSet())
  }

  // af1bac731: named colours and dps default to a Remote* value, constant or not.

  @Test
  fun aNamedColorWithAConstantDefaultIsANamedConstant() {
    val operations = operations {
      val fill = rememberNamedRemoteColor("theme.surface", RemoteColor(0xFF1C1B1F.toInt()))
      RemoteBox(RemoteModifier.size(40.rdp).background(fill)) {}
    }
    val named = operations.filterIsInstance<RcNamedVariable>().single()
    assertEquals("USER:theme.surface", named.name)
    assertEquals(RcNamedVariable.COLOR_TYPE, named.type)
    assertEquals(
      0xFF1C1B1F.toInt(),
      operations.filterIsInstance<RcColorConstant>().single { it.id == named.id }.argb,
    )
  }

  @Test
  fun aNamedColorWithADynamicDefaultNamesAnExpressionOfIt() {
    val operations = operations {
      val red = RemoteFloat.createNamedRemoteFloat("red", 0.2f)
      val fill =
        RemoteColor.createNamedRemoteColor(
          "accent",
          RemoteColor.rgb(red, 0.4f.rf, 0.6f.rf),
          RemoteState.Domain("HOME"),
        )
      RemoteBox(RemoteModifier.size(40.rdp).background(fill)) {}
    }
    val named = operations.filterIsInstance<RcNamedVariable>().single { it.name == "HOME:accent" }
    assertEquals(RcNamedVariable.COLOR_TYPE, named.type)
    // AndroidX writes the name on an id-to-id interpolation of the default with itself.
    val alias = operations.filterIsInstance<RcColorExpression>().single { it.outId == named.id }
    assertEquals(alias.first, alias.second)
    assertTrue(operations.filterIsInstance<RcColorExpression>().any { it.outId == alias.first })
  }

  @Test
  fun aNamedDpTakesARemoteDpDefault() {
    val named = operations {
      val inset = rememberNamedRemoteDp("inset", 8.rdp)
      RemoteBox(RemoteModifier.size(40.rdp).padding(inset)) {}
    }
      .filterIsInstance<RcNamedVariable>()
      .single()
    assertEquals("USER:inset", named.name)
    assertEquals(RcNamedVariable.FLOAT_TYPE, named.type)
  }

  // af1bac731: the graphics layer scope holds Remote* types and writes what the arguments do.

  @Test
  fun theGraphicsLayerScopeWritesWhatTheArgumentsDo() {
    val scoped = capture {
      RemoteBox(
        RemoteModifier.size(40.rdp).graphicsLayer {
          alpha = 0.5f.rf
          shape = RemoteCircleShape
          compositingStrategy = RemoteCompositingStrategy.Offscreen
        },
      ) {}
    }
    val arguments = capture {
      RemoteBox(
        RemoteModifier.size(40.rdp)
          .graphicsLayer(
            alpha = 0.5f.rf,
            shape = RemoteCircleShape,
            compositingStrategy = RemoteCompositingStrategy.Offscreen,
          ),
      ) {}
    }
    assertContentEquals(arguments, scoped)
  }
}
