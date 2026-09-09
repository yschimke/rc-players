package ee.schimke.composeai.rcplayer.compose

import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDrawToBitmap
import ee.schimke.composeai.rcplayer.protocol.RcFloatFunctionCall
import ee.schimke.composeai.rcplayer.protocol.RcFloatFunctionDefine
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
import ee.schimke.composeai.rcplayer.protocol.RcReferencedOperations
import ee.schimke.composeai.rcplayer.protocol.RcShaderData
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RcGraphicsResourceSupportTest {
  private val header = RcHeader(RcVersion(1, 0, 0), modern = false)

  @Test
  fun acceptsDeclaredShaderAndBitmapTargetResources() {
    val document =
      RcDocument(
        header,
        listOf(
          RcTextData(1, "half4 main(float2 p) { return half4(1); }"),
          RcBitmapData(2, 1, 1, RcBitmapData.TYPE_RAW8888, 0, byteArrayOf(0, 0, 0, 0)),
          RcShaderData(3, 1, bitmapUniforms = mapOf("image" to 2)),
          RcPaintData(listOf(9, 3)),
          RcDrawToBitmap(2, 0, 0),
          RcDrawToBitmap(0, 0, 0),
        ),
      )

    assertTrue(document.composeSupportReport().fullyRenderable)
  }

  @Test
  fun reportsMissingShaderTextBitmapUniformAndDrawTarget() {
    val issues =
      RcDocument(
          header,
          listOf(
            RcShaderData(3, 10),
            RcTextData(1, "uniform shader image; half4 main(float2 p) { return image.eval(p); }"),
            RcShaderData(4, 1, bitmapUniforms = mapOf("image" to 11)),
            RcDrawToBitmap(12, 0, 0),
          ),
        )
        .composeSupportReport()
        .issues

    assertEquals(
      listOf(
        "shader text id 10 is not declared",
        "bitmap uniform 'image' references undeclared bitmap 11",
        "bitmap id 12 is not declared",
      ),
      issues.map { it.detail },
    )
  }

  @Test
  fun refusesAShaderSourceTheRuntimeRejectsWhenAPaintInstallsIt() {
    // BLOCKING rather than SKIPPABLE, per this enum's own definition. SKIPPABLE promises a lenient
    // host plays the document with the operation skipped; nothing skips an unbuildable shader —
    // `applyPaint` reaches `buildRuntimeShader`, whose catch rethrows — so the frame dies on a
    // document `requireRenderable(lenient = true)` had just called playable.
    val report =
      RcDocument(
          header,
          listOf(
            RcTextData(1, "this is not SkSL"),
            RcShaderData(3, 1),
            RcPaintData(listOf(9, 3)),
          ),
        )
        .composeSupportReport()

    assertEquals(
      listOf("shader source is not accepted by the Skia runtime"),
      report.issues.map { it.detail },
    )
    assertEquals(listOf(RcComposeSupportSeverity.BLOCKING), report.issues.map { it.severity })
    // The point of the severity: a lenient host must refuse this rather than crash on the frame.
    assertFalse(report.playable)
  }

  @Test
  fun refusesMoreMutableTargetsThanTheRendererWillAllocate() {
    // Both ceilings belong to the whole document, so no per-operation check can see them, and
    // `RcOffscreenTargetPool` `require`s them mid-draw. 65 distinct declared targets.
    val operations =
      (1..65).flatMap { id ->
        listOf(
          RcBitmapData(id, 1, 1, RcBitmapData.TYPE_RAW8888, 0, byteArrayOf(0, 0, 0, 0)),
          RcDrawToBitmap(id, 0, 0),
        )
      }
    val report = RcDocument(header, operations).composeSupportReport()

    assertEquals(
      listOf("document declares 65 mutable targets; the renderer allocates at most 64"),
      report.issues.map { it.detail },
    )
    assertFalse(report.playable)
  }

  @Test
  fun refusesMutableTargetsThatExceedTheAggregatePixelBudget() {
    // Two 4096x2048 targets are 16,777,216 pixels exactly — the whole budget — so a third of any
    // size is one too many.
    val operations =
      (1..3).flatMap { id ->
        listOf(
          RcBitmapData(id, 4096, 2048, RcBitmapData.TYPE_PNG_8888, 0, byteArrayOf(0, 0, 0, 0)),
          RcDrawToBitmap(id, 0, 0),
        )
      }
    val report = RcDocument(header, operations).composeSupportReport()

    assertEquals(
      listOf("mutable targets total 25165824 pixels; the renderer allocates at most 16777216"),
      report.issues.map { it.detail },
    )
    assertFalse(report.playable)
  }

  @Test
  fun countsOneTargetPerDistinctBitmapHoweverOftenItIsDrawnTo() {
    // The pool allocates on a target's FIRST use and reuses it afterwards, so a document that draws
    // to one target a hundred times allocates one target. A preflight that counted operations
    // instead would refuse a document the renderer draws perfectly well.
    // The bitmap has to be one `decodeInlineImage` can actually build: `DrawToBitmap` resolves its
    // target through `requireNotNull(images[bitmapId])`, so a payload that fails to decode makes
    // the renderer throw on the first draw and this assertion would endorse a document it cannot
    // play — the very thing this file is about.
    val operations =
      listOf<ee.schimke.composeai.rcplayer.protocol.RcOperation>(
        RcBitmapData(1, 2, 2, RcBitmapData.TYPE_RAW8888, 0, ByteArray(2 * 2 * 4))
      ) + List(100) { RcDrawToBitmap(1, 0, 0) }

    assertTrue(RcDocument(header, operations).composeSupportReport().fullyRenderable)
  }

  @Test
  fun sizesAnEncodedTargetByThePixelsItsPayloadDeclares() {
    // The pool charges `source.width * source.height` of the DECODED image, so a PNG whose IHDR is
    // larger than its `RcBitmapData` fields blows the ceiling the declared fields fit inside. One
    // target claiming 1x1 and carrying a 4096x4097 header is over the aggregate budget on its own.
    val operations =
      listOf<ee.schimke.composeai.rcplayer.protocol.RcOperation>(
        RcBitmapData(1, 1, 1, RcBitmapData.TYPE_PNG_8888, 0, pngHeaderFor(4096, 4097)),
        RcDrawToBitmap(1, 0, 0),
      )

    assertEquals(
      listOf("mutable targets total 16781312 pixels; the renderer allocates at most 16777216"),
      RcDocument(header, operations).composeSupportReport().issues.map { it.detail },
    )
  }

  @Test
  fun countsTargetsInAReferencedBlockNothingIncludes() {
    // Another deliberate over-refusal. `RcDocumentLinker` drops a definition no
    // `IncludeReferencedOperations` names, so these 65 targets never allocate — but the wire stream
    // is half of what is scanned, and it is the half that catches a macro body the linked tree
    // would have shown instead. Counting both means counting some draws that never happen.
    val operations =
      listOf<ee.schimke.composeai.rcplayer.protocol.RcOperation>(RcReferencedOperations(7)) +
        (1..65).flatMap { id ->
          listOf(
            RcBitmapData(id, 1, 1, RcBitmapData.TYPE_RAW8888, 0, ByteArray(4)),
            RcDrawToBitmap(id, 0, 0),
          )
        } +
        listOf(RcNoArg(RcOpcodes.CONTAINER_END))

    assertEquals(
      listOf("document declares 65 mutable targets; the renderer allocates at most 64"),
      RcDocument(header, operations).composeSupportReport().issues.map { it.detail },
    )
  }

  @Test
  fun refusesASupersededShaderToo() {
    // A KNOWN over-refusal, asserted so it stays deliberate. The paint installs the second, valid
    // declaration of id 3, so the renderer draws this; the walk records only that some reachable
    // paint names id 3, and condemns the invalid declaration under it.
    //
    // Tracking which declaration is live at the paint is what earlier revisions attempted, and it
    // cannot be done from position alone — a conditional, a theme marker or a canvas boundary each
    // change the answer. Refusing a document the renderer would play is the direction this check
    // errs in; the reverse would be a crashed frame.
    val report =
      RcDocument(
          header,
          listOf(
            RcTextData(1, "this is not SkSL"),
            RcTextData(2, "half4 main(float2 p) { return half4(1); }"),
            RcShaderData(3, 1),
            RcShaderData(3, 2),
            RcPaintData(listOf(9, 3)),
          ),
        )
        .composeSupportReport()

    assertEquals(listOf(RcComposeSupportSeverity.BLOCKING), report.issues.map { it.severity })
  }

  @Test
  fun refusesAShaderThePaintPicksUpFromALaterDeclaration() {
    // `buildRuntimeShader` falls back to the LAST declaration of the id in the wire document when
    // none is live yet, so a paint that precedes its own shader still installs one and still
    // throws. Forward-only bookkeeping called that skippable.
    val report =
      RcDocument(
          header,
          listOf(
            RcTextData(1, "this is not SkSL"),
            RcPaintData(listOf(9, 3)),
            RcShaderData(3, 1),
          ),
        )
        .composeSupportReport()

    assertEquals(listOf(RcComposeSupportSeverity.BLOCKING), report.issues.map { it.severity })
    assertFalse(report.playable)
  }

  @Test
  fun countsATargetWhoseBitmapIsDeclaredInsideAnUnusedBlock() {
    // `decodeInlineImagesUncounted` walks the WIRE stream, so a bitmap parked in a
    // `ReferencedOperations` block nothing includes is still decoded and still usable by an active
    // `DrawToBitmap`. Taking declarations from the linked stream dropped the id, and with it the
    // 65 live targets that reference those ids.
    val operations =
      listOf<ee.schimke.composeai.rcplayer.protocol.RcOperation>(RcReferencedOperations(7)) +
        (1..65).map { id -> RcBitmapData(id, 1, 1, RcBitmapData.TYPE_RAW8888, 0, ByteArray(4)) } +
        listOf(RcNoArg(RcOpcodes.CONTAINER_END)) +
        (1..65).map { id -> RcDrawToBitmap(id, 0, 0) }

    assertEquals(
      listOf("document declares 65 mutable targets; the renderer allocates at most 64"),
      RcDocument(header, operations).composeSupportReport().issues.map { it.detail },
    )
  }

  @Test
  fun countsTargetsInAFunctionBodyNothingCalls() {
    // The same trade once more: `drawOperations` steps over a `FloatFunctionDefine` and draws its
    // children only where a call reaches them, so an uncalled body allocates nothing. Deciding
    // which bodies are called is reachability, and the walk does not do reachability.
    val operations =
      listOf<ee.schimke.composeai.rcplayer.protocol.RcOperation>(
        RcFloatFunctionDefine(9, listOf(1))
      ) +
        (1..65).flatMap { id ->
          listOf(
            RcBitmapData(id, 1, 1, RcBitmapData.TYPE_RAW8888, 0, ByteArray(4)),
            RcDrawToBitmap(id, 0, 0),
          )
        } +
        listOf(RcNoArg(RcOpcodes.CONTAINER_END))

    assertEquals(
      listOf("document declares 65 mutable targets; the renderer allocates at most 64"),
      RcDocument(header, operations).composeSupportReport().issues.map { it.detail },
    )
  }

  @Test
  fun refusesAShaderAConditionalOnlyMightSupersede() {
    // The valid redeclaration sits inside a `ConditionalOperations`, so the renderer may skip it
    // and
    // still be holding the invalid one at the paint. A redeclaration only supersedes when it is
    // certain to run; this one is not, so it widens what might be installed rather than replacing.
    val report =
      RcDocument(
          header,
          listOf(
            RcTextData(1, "this is not SkSL"),
            RcTextData(2, "half4 main(float2 p) { return half4(1); }"),
            RcShaderData(3, 1),
            RcNoArg(RcOpcodes.CONDITIONAL_OPERATIONS),
            RcShaderData(3, 2),
            RcNoArg(RcOpcodes.CONTAINER_END),
            RcPaintData(listOf(9, 3)),
          ),
        )
        .composeSupportReport()

    assertTrue(
      RcComposeSupportSeverity.BLOCKING in report.issues.map { it.severity },
      report.issues.toString(),
    )
  }

  @Test
  fun countsTargetsInAFunctionBodyThatIsCalled() {
    // The mirror of the uncalled case: `RcFloatFunctionCall` draws `definition.children`, so a
    // called body's targets do reach the pool and must be counted where the call is.
    val operations =
      listOf<ee.schimke.composeai.rcplayer.protocol.RcOperation>(
        RcFloatFunctionDefine(9, emptyList())
      ) +
        (1..65).flatMap { id ->
          listOf(
            RcBitmapData(id, 1, 1, RcBitmapData.TYPE_RAW8888, 0, ByteArray(4)),
            RcDrawToBitmap(id, 0, 0),
          )
        } +
        listOf(RcNoArg(RcOpcodes.CONTAINER_END), RcFloatFunctionCall(9, emptyList()))

    assertEquals(
      listOf("document declares 65 mutable targets; the renderer allocates at most 64"),
      RcDocument(header, operations).composeSupportReport().issues.map { it.detail },
    )
  }

  @Test
  fun sizesADuplicateIdByItsLargestDeclaration() {
    // Which declaration of a repeated id the renderer keeps depends on which payload DECODES, and a
    // decoder is what this check does not have: a readable IHDR says nothing about the body behind
    // it. So the largest is taken — the one that can refuse the document rather than the one that
    // can let a crash through.
    val operations =
      listOf<ee.schimke.composeai.rcplayer.protocol.RcOperation>(
        RcBitmapData(1, 1, 1, RcBitmapData.TYPE_RAW8888, 0, ByteArray(4)),
        RcBitmapData(1, 8192, 8192, RcBitmapData.TYPE_PNG_8888, 0, ByteArray(4)),
        RcDrawToBitmap(1, 0, 0),
      )

    assertTrue(
      RcDocument(header, operations).composeSupportReport().issues.any {
        "mutable targets total" in it.detail
      }
    )
  }

  @Test
  fun refusesTargetsWhoseTotalPixelsOverflowALong() {
    // Three targets of Int.MAX_VALUE square: each product fits in a Long and their sum does not.
    // A wrapped total compares below the ceiling, so an unsaturated sum called this renderable.
    val operations =
      (1..3).flatMap { id ->
        listOf<ee.schimke.composeai.rcplayer.protocol.RcOperation>(
          RcBitmapData(
            id,
            1,
            1,
            RcBitmapData.TYPE_PNG_8888,
            0,
            pngHeaderFor(Int.MAX_VALUE, Int.MAX_VALUE),
          ),
          RcDrawToBitmap(id, 0, 0),
        )
      }

    val report = RcDocument(header, operations).composeSupportReport()
    assertTrue(report.issues.any { "mutable targets total" in it.detail }, report.issues.toString())
    assertFalse(report.playable)
  }

  /** A PNG header just long enough for the preflight to read its IHDR dimensions. */
  private fun pngHeaderFor(width: Int, height: Int): ByteArray =
    byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10) +
      byteArrayOf(0, 0, 0, 13) +
      byteArrayOf(73, 72, 68, 82) +
      bigEndianBytes(width) +
      bigEndianBytes(height)

  private fun bigEndianBytes(value: Int): ByteArray =
    byteArrayOf(
      (value ushr 24).toByte(),
      (value ushr 16).toByte(),
      (value ushr 8).toByte(),
      value.toByte(),
    )

  @Test
  fun reportsBackendIntegerUniformVectorLimit() {
    val issue =
      RcDocument(
          header,
          listOf(
            RcTextData(1, "uniform int data[5]; half4 main(float2 p) { return half4(1); }"),
            RcShaderData(3, 1, intUniforms = mapOf("data" to List(5) { it })),
          ),
        )
        .composeSupportReport()
        .issues
        .single()

    assertEquals("integer uniform must contain 1..4 values on the Skia backend", issue.detail)
  }

  @Test
  fun reportsUnsupportedShaderDialectAsSkippableEvidence() {
    val issue =
      RcDocument(header, listOf(RcTextData(1, "not a runtime shader"), RcShaderData(3, 1)))
        .composeSupportReport()
        .issues
        .single()

    assertEquals("shader source is not accepted by the Skia runtime", issue.detail)
    assertEquals(RcComposeSupportSeverity.SKIPPABLE, issue.severity)
  }
}
