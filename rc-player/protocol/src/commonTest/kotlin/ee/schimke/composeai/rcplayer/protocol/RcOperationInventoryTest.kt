package ee.schimke.composeai.rcplayer.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RcOperationInventoryTest {
  @Test
  fun everyImplementedManifestEntryHasExactlyOneCodecAndViceVersa() {
    val decodable =
      RcOperationInventory.entries
        .filter {
          it.status == RcOperationStatus.IMPLEMENTED ||
            it.status == RcOperationStatus.PARSE_ONLY ||
            it.status == RcOperationStatus.IMPLEMENTED_UPSTREAM_UNAVAILABLE
        }
        .map { it.opcode }
        .toSet()
    val codecs = RcDocumentCodec.supportedOperations.map { it.opcode }.toSet()

    assertEquals(decodable, codecs)
    assertEquals(codecs.size, RcDocumentCodec.supportedOperations.size)
  }

  @Test
  fun inventoryIsUniqueOrderedAndHasAnExplicitDisposition() {
    val entries = RcOperationInventory.entries

    assertEquals(entries.sortedBy { it.opcode }, entries)
    assertEquals(entries.size, entries.map { it.opcode }.distinct().size)
    assertEquals(entries.size, entries.map { it.constantName }.distinct().size)
    assertTrue(entries.all { it.cluster in 0..8 })
  }

  @Test
  fun inventoryHasNoTemporaryParseOnlyOperations() {
    assertTrue(RcOperationInventory.entries.none { it.status == RcOperationStatus.PARSE_ONLY })
  }

  @Test
  fun profilesExcludeUnavailableReservedAndParseOnlyOperations() {
    val unavailable = setOf(4, 132, 162, 195)
    val reserved = (251..255).toSet()

    assertTrue(unavailable.none(RcOperationProfiles.ANDROIDX_JAVA_ALPHA16::supports))
    assertTrue(reserved.none(RcOperationProfiles.ANDROIDX_JAVA_ALPHA16::supports))
    assertTrue(unavailable.none(RcOperationProfiles.CMP_WASM_ALPHA16::supports))
    assertTrue(reserved.none(RcOperationProfiles.CMP_WASM_ALPHA16::supports))
    assertTrue(unavailable.none(RcOperationProfiles.CMP_MACOS_ALPHA16::supports))
    assertTrue(reserved.none(RcOperationProfiles.CMP_MACOS_ALPHA16::supports))
    assertTrue(unavailable.none(RcOperationProfiles.CMP_DESKTOP_ALPHA16::supports))
    assertTrue(reserved.none(RcOperationProfiles.CMP_DESKTOP_ALPHA16::supports))
    assertFalse(RcOperationProfiles.CMP_WASM_ALPHA16.supports(RcOpcodes.MODIFIER_GRAPHICS_LAYER))
    assertTrue(
      RcOperationInventory.entries
        .filter { it.status == RcOperationStatus.PARSE_ONLY }
        .none { RcOperationProfiles.CMP_WASM_ALPHA16.supports(it.opcode) }
    )
    assertEquals(
      RcOperationInventory.entries.count {
        it.status == RcOperationStatus.IMPLEMENTED ||
          it.status == RcOperationStatus.IMPLEMENTED_UPSTREAM_UNAVAILABLE
      } - 1,
      RcOperationProfiles.CMP_WASM_ALPHA16.opcodes.size,
    )
  }

  @Test
  fun drawTextOnCircleIsInTheCmpProfilesButNotTheAndroidXOne() {
    // The two halves of yschimke/wear-m3-catalog#321, as an assertion. This player implements the
    // operation, so it belongs in the CMP profiles; the authoritative Java registry still has no
    // reader for it, so advertising it to a producer targeting that player would hand back a
    // document the producer cannot read.
    val entry = RcOperationInventory.byOpcode.getValue(RcOpcodes.DRAW_TEXT_ON_CIRCLE)

    assertEquals(RcOperationStatus.IMPLEMENTED_UPSTREAM_UNAVAILABLE, entry.status)
    assertTrue(RcOperationProfiles.CMP_IOS_ALPHA16.supports(RcOpcodes.DRAW_TEXT_ON_CIRCLE))
    assertTrue(RcOperationProfiles.CMP_MACOS_ALPHA16.supports(RcOpcodes.DRAW_TEXT_ON_CIRCLE))
    assertTrue(RcOperationProfiles.CMP_DESKTOP_ALPHA16.supports(RcOpcodes.DRAW_TEXT_ON_CIRCLE))
    assertTrue(RcOperationProfiles.CMP_WASM_ALPHA16.supports(RcOpcodes.DRAW_TEXT_ON_CIRCLE))
    assertFalse(RcOperationProfiles.ANDROIDX_JAVA_ALPHA16.supports(RcOpcodes.DRAW_TEXT_ON_CIRCLE))
  }

  @Test
  fun appleAndDesktopProfilesAreDistinctContractsWithSharedCoverageToday() {
    assertEquals(
      RcOperationProfiles.CMP_IOS_ALPHA16.opcodes,
      RcOperationProfiles.CMP_MACOS_ALPHA16.opcodes,
    )
    assertEquals(
      RcOperationProfiles.CMP_IOS_ALPHA16.opcodes,
      RcOperationProfiles.CMP_DESKTOP_ALPHA16.opcodes,
    )
    assertEquals("cmp-ios-alpha16", RcOperationProfiles.CMP_IOS_ALPHA16.name)
    assertEquals("cmp-macos-alpha16", RcOperationProfiles.CMP_MACOS_ALPHA16.name)
    assertEquals("cmp-desktop-alpha16", RcOperationProfiles.CMP_DESKTOP_ALPHA16.name)
  }
}
