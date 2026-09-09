package ee.schimke.composeai.rcplayer.compose

import ee.schimke.composeai.rcplayer.protocol.RcBitmapData
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcDrawToBitmap
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcPaintData
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
    val operations =
      listOf<ee.schimke.composeai.rcplayer.protocol.RcOperation>(
        RcBitmapData(1, 4096, 2048, RcBitmapData.TYPE_PNG_8888, 0, byteArrayOf(0, 0, 0, 0))
      ) + List(100) { RcDrawToBitmap(1, 0, 0) }

    assertTrue(RcDocument(header, operations).composeSupportReport().fullyRenderable)
  }

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
