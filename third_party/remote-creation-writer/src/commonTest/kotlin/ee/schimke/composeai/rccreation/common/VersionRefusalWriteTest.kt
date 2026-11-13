package ee.schimke.composeai.rccreation.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * What AndroidX's writer refuses below the API level that added it, and this one with it: a winding
 * rule below level 7, a glyph spacing below level 8, a density behaviour below level 8
 * (`docs/design/VERSION_QUIRKS.md`). `:rc-player-edit`'s `validateFor` reports the same three.
 */
class VersionRefusalWriteTest {
  private fun writer(profile: Profile, density: Int = CoreDocument.DENSITY_BEHAVIOR_LEGACY) =
    RemoteDocumentWriter(CreationDisplayInfo(100, 100, 160, density), null, profile)

  private val path = floatArrayOf(10f, 0f, 0f, 10f, 10f, 10f)

  @Test
  fun aWindingRuleIsRefusedBelowLevelSeven() {
    val error =
      assertFailsWith<RuntimeException> {
        writer(RcPlatformProfiles.WIDGETS_V6).addPathData(path, 1)
      }
    assertEquals("winding not supported in API level < 7", error.message)
    writer(RcPlatformProfiles.WIDGETS_V6).addPathData(path, 0)
    writer(RcPlatformProfiles.WIDGETS_V7).addPathData(path, 1)
    writer(RcPlatformProfiles.ANDROIDX).addPathData(path, 1)
  }

  @Test
  fun aGlyphSpacingIsRefusedBelowLevelEight() {
    for (profile in listOf(RcPlatformProfiles.WIDGETS_V6, RcPlatformProfiles.ANDROIDX7)) {
      val message = "glyphSpacing not supported in API level < 8"
      val spaced = writer(profile)
      assertEquals(
        message,
        assertFailsWith<RuntimeException> { spaced.drawBitmapFontTextRun(1, 2, 0, 1, 0f, 0f, 1.5f) }
          .message,
      )
      assertEquals(
        message,
        assertFailsWith<RuntimeException> {
            spaced.drawBitmapFontTextRunOnPath(1, 2, 3, 0, 1, 0f, 1.5f)
          }
          .message,
      )
      assertEquals(
        message,
        assertFailsWith<RuntimeException> { spaced.bitmapTextMeasure(1, 2, 0, 1.5f) }.message,
      )
    }
    val level8 = writer(RcPlatformProfiles.ANDROIDX8)
    level8.drawBitmapFontTextRun(1, 2, 0, 1, 0f, 0f, 1.5f)
    level8.drawBitmapFontTextRunOnPath(1, 2, 3, 0, 1, 0f, 1.5f)
    level8.bitmapTextMeasure(1, 2, 0, 1.5f)
  }

  @Test
  fun aDensityBehaviourIsRefusedBelowLevelEight() {
    for (profile in
      listOf(
        RcPlatformProfiles.WIDGETS_V6,
        RcPlatformProfiles.WIDGETS_V7,
        RcPlatformProfiles.ANDROIDX7,
      )) {
      assertFailsWith<IllegalArgumentException>(profile.toString()) {
        writer(profile, CoreDocument.DENSITY_BEHAVIOR_DP)
      }
    }
    writer(RcPlatformProfiles.ANDROIDX8, CoreDocument.DENSITY_BEHAVIOR_DP)
  }
}
