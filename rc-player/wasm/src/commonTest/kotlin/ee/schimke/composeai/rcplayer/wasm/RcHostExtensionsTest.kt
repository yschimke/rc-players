package ee.schimke.composeai.rcplayer.wasm

import kotlin.test.Test
import kotlin.test.assertEquals

class RcHostExtensionsTest {
  @Test
  fun noParameterTurnsNothingOn() {
    assertEquals(emptySet(), parseExtensionsParameter(null))
    assertEquals(emptySet(), parseExtensionsParameter(""))
    assertEquals(emptySet(), parseExtensionsParameter(" , "))
  }

  @Test
  fun namesAreCaseInsensitiveAndTrimmed() {
    assertEquals(setOf(RcHostExtension.ThreeD), parseExtensionsParameter("3d"))
    assertEquals(setOf(RcHostExtension.ThreeD), parseExtensionsParameter(" 3D "))
  }

  @Test
  fun unknownNamesAreIgnored() {
    assertEquals(setOf(RcHostExtension.ThreeD), parseExtensionsParameter("holograms,3d"))
    assertEquals(emptySet(), parseExtensionsParameter("holograms"))
  }

  @Test
  fun theRendererDefaultsToAuto() {
    assertEquals(Rc3DRendererParameter.Auto, parse3DRendererParameter(null))
    assertEquals(Rc3DRendererParameter.Auto, parse3DRendererParameter("fastest"))
    assertEquals(Rc3DRendererParameter.Software, parse3DRendererParameter("Software"))
    assertEquals(Rc3DRendererParameter.Gpu, parse3DRendererParameter("gpu"))
  }
}
