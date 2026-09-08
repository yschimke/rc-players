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

    assertTrue(unavailable.none(RcOperationProfiles.ANDROIDX_JAVA_ALPHA18::supports))
    assertTrue(reserved.none(RcOperationProfiles.ANDROIDX_JAVA_ALPHA18::supports))
    assertTrue(unavailable.none(RcOperationProfiles.CMP_WASM_ALPHA18::supports))
    assertTrue(reserved.none(RcOperationProfiles.CMP_WASM_ALPHA18::supports))
    assertTrue(unavailable.none(RcOperationProfiles.CMP_MACOS_ALPHA18::supports))
    assertTrue(reserved.none(RcOperationProfiles.CMP_MACOS_ALPHA18::supports))
    assertTrue(unavailable.none(RcOperationProfiles.CMP_DESKTOP_ALPHA18::supports))
    assertTrue(reserved.none(RcOperationProfiles.CMP_DESKTOP_ALPHA18::supports))
    assertFalse(RcOperationProfiles.CMP_WASM_ALPHA18.supports(RcOpcodes.MODIFIER_GRAPHICS_LAYER))
    assertTrue(
      RcOperationInventory.entries
        .filter { it.status == RcOperationStatus.PARSE_ONLY }
        .none { RcOperationProfiles.CMP_WASM_ALPHA18.supports(it.opcode) }
    )
    assertEquals(
      RcOperationInventory.entries.count {
        it.status == RcOperationStatus.IMPLEMENTED ||
          it.status == RcOperationStatus.IMPLEMENTED_UPSTREAM_UNAVAILABLE
      } - 1,
      RcOperationProfiles.CMP_WASM_ALPHA18.opcodes.size,
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
    assertTrue(RcOperationProfiles.CMP_IOS_ALPHA18.supports(RcOpcodes.DRAW_TEXT_ON_CIRCLE))
    assertTrue(RcOperationProfiles.CMP_MACOS_ALPHA18.supports(RcOpcodes.DRAW_TEXT_ON_CIRCLE))
    assertTrue(RcOperationProfiles.CMP_DESKTOP_ALPHA18.supports(RcOpcodes.DRAW_TEXT_ON_CIRCLE))
    assertTrue(RcOperationProfiles.CMP_WASM_ALPHA18.supports(RcOpcodes.DRAW_TEXT_ON_CIRCLE))
    assertFalse(RcOperationProfiles.ANDROIDX_JAVA_ALPHA18.supports(RcOpcodes.DRAW_TEXT_ON_CIRCLE))
  }

  @Test
  fun appleAndDesktopProfilesAreDistinctContractsWithSharedCoverageToday() {
    assertEquals(
      RcOperationProfiles.CMP_IOS_ALPHA18.opcodes,
      RcOperationProfiles.CMP_MACOS_ALPHA18.opcodes,
    )
    assertEquals(
      RcOperationProfiles.CMP_IOS_ALPHA18.opcodes,
      RcOperationProfiles.CMP_DESKTOP_ALPHA18.opcodes,
    )
    assertEquals("cmp-ios-alpha18", RcOperationProfiles.CMP_IOS_ALPHA18.name)
    assertEquals("cmp-macos-alpha18", RcOperationProfiles.CMP_MACOS_ALPHA18.name)
    assertEquals("cmp-desktop-alpha18", RcOperationProfiles.CMP_DESKTOP_ALPHA18.name)
  }

  @Test
  fun androidXBaselineAndExperimentalEmbeddedProfilesStayDistinct() {
    assertTrue(RcOperationProfiles.ANDROIDX_JAVA_ALPHA18.supports(RcOpcodes.DRAW_RECT))
    assertTrue(
      RcOperationProfiles.ANDROIDX_EMBEDDED_EXPERIMENTAL_ALPHA18.supports(RcOpcodes.DRAW_RECT)
    )
    assertFalse(RcOperationProfiles.ANDROIDX_JAVA_ALPHA18.supports(RcOpcodes.DATA_BITMAP_FONT))
    assertTrue(
      RcOperationProfiles.ANDROIDX_EMBEDDED_EXPERIMENTAL_ALPHA18.supports(
        RcOpcodes.DATA_BITMAP_FONT
      )
    )
    assertFalse(RcOperationProfiles.ANDROIDX_JAVA_ALPHA18.supports(RcOpcodes.MACRO_CALL))
    assertTrue(
      RcOperationProfiles.ANDROIDX_EMBEDDED_EXPERIMENTAL_ALPHA18.supports(RcOpcodes.MACRO_CALL)
    )
  }

  @Test
  fun supportMatrixHasExactlyOneRowPerOperationAndTarget() {
    assertEquals(
      RcOperationInventory.entries.size * RcOperationTarget.entries.size,
      RcOperationSupportMatrix.entries.size,
    )
    RcOperationTarget.entries.forEach { target ->
      val rows = RcOperationSupportMatrix.byTarget.getValue(target)
      assertEquals(RcOperationInventory.entries.size, rows.size)
      assertEquals(RcOperationInventory.entries.map { it.opcode }, rows.map { it.operation.opcode })
      assertEquals(
        RcOperationProfiles.byTarget.getValue(target).opcodes,
        rows.filter { it.supported }.mapTo(linkedSetOf()) { it.operation.opcode },
      )
    }
  }

  @Test
  fun supportMatrixKeepsCmpOnlyAndWasmSpecificResultsVisible() {
    assertFalse(
      RcOperationSupportMatrix.entry(
          RcOpcodes.DRAW_TEXT_ON_CIRCLE,
          RcOperationTarget.ANDROIDX_JAVA,
        )
        .supported
    )
    assertTrue(
      RcOperationSupportMatrix.entry(
          RcOpcodes.DRAW_TEXT_ON_CIRCLE,
          RcOperationTarget.CMP_MACOS,
        )
        .supported
    )
    assertFalse(
      RcOperationSupportMatrix.entry(
          RcOpcodes.MODIFIER_GRAPHICS_LAYER,
          RcOperationTarget.CMP_WASM,
        )
        .supported
    )
    assertTrue(
      RcOperationSupportMatrix.entry(
          RcOpcodes.MODIFIER_GRAPHICS_LAYER,
          RcOperationTarget.CMP_IOS,
        )
        .supported
    )
  }
}
