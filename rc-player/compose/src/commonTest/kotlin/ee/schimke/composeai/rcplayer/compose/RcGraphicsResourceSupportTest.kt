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
