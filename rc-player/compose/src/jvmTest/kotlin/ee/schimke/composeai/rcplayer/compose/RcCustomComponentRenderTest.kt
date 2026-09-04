package ee.schimke.composeai.rcplayer.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import ee.schimke.composeai.rcplayer.protocol.RcColorConstant
import ee.schimke.composeai.rcplayer.protocol.RcCustomLayout
import ee.schimke.composeai.rcplayer.protocol.RcCustomProperty
import ee.schimke.composeai.rcplayer.protocol.RcDimensionType
import ee.schimke.composeai.rcplayer.protocol.RcDocument
import ee.schimke.composeai.rcplayer.protocol.RcFloatConstant
import ee.schimke.composeai.rcplayer.protocol.RcFloatWord
import ee.schimke.composeai.rcplayer.protocol.RcHeader
import ee.schimke.composeai.rcplayer.protocol.RcHeightModifier
import ee.schimke.composeai.rcplayer.protocol.RcIntegerConstant
import ee.schimke.composeai.rcplayer.protocol.RcLayoutContent
import ee.schimke.composeai.rcplayer.protocol.RcNoArg
import ee.schimke.composeai.rcplayer.protocol.RcOpcodes
import ee.schimke.composeai.rcplayer.protocol.RcRootLayout
import ee.schimke.composeai.rcplayer.protocol.RcTextData
import ee.schimke.composeai.rcplayer.protocol.RcVersion
import ee.schimke.composeai.rcplayer.protocol.RcWidthModifier
import kotlin.test.Test

class RcCustomComponentRenderTest {
  @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
  @Test
  fun hostComponentReadsLivePropertiesAndWritesReturnChannels() =
    runSkikoComposeUiTest(size = Size(80f, 20f), density = Density(1f)) {
      val registry =
        RcCustomComponentRegistry(
          "slot:counter" to
            { component, modifier ->
              BasicText(
                "${component.text(LABEL)}:${component.float(VALUE)}",
                modifier.clickable { component.returnFloat(RESULT, 7f) },
              )
            }
        )

      setContent { RcComposePlayer(document(), customComponents = registry) }
      waitForIdle()
      onNodeWithText("Count:1.0").assertExists().performClick()
      waitForIdle()
      onNodeWithText("Count:7.0").assertExists()
    }

  @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
  @Test
  fun hostComponentResolvesIdBackedIntegerAndColourProperties() =
    runSkikoComposeUiTest(size = Size(200f, 20f), density = Density(1f)) {
      // Both properties carry an *id* rather than a value, so what the component reads is whatever
      // the document holds under it — the case a theme or a host override goes through.
      val registry =
        RcCustomComponentRegistry(
          "slot:ids" to
            { component, modifier ->
              BasicText(
                "${component.integer(VALUE)}/${component.color(LABEL).toUInt().toString(16)}",
                modifier,
              )
            }
        )
      val end = RcNoArg(RcOpcodes.CONTAINER_END)
      val document =
        RcDocument(
          RcHeader(RcVersion(1, 0, 0), legacyWidth = 200, legacyHeight = 20, modern = false),
          listOf(
            RcTextData(CONFIG_ID, "slot:ids"),
            RcIntegerConstant(VALUE_ID, 42),
            RcColorConstant(COLOR_ID, 0xff1a73e8.toInt()),
            RcRootLayout(1),
            RcLayoutContent(2),
            RcCustomLayout(
              componentId = 3,
              animationId = 0,
              configId = CONFIG_ID,
              properties =
                listOf(
                  RcCustomProperty.intId(VALUE, VALUE_ID),
                  RcCustomProperty.colorId(LABEL, COLOR_ID),
                ),
            ),
            RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(200f)),
            RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(20f)),
            end,
            end,
            end,
          ),
        )

      setContent { RcComposePlayer(document, customComponents = registry) }
      waitForIdle()
      onNodeWithText("42/ff1a73e8").assertExists()
    }

  private fun document(): RcDocument {
    val reference = RcFloatWord(0x7fc00000 or VALUE_ID)
    val end = RcNoArg(RcOpcodes.CONTAINER_END)
    return RcDocument(
      RcHeader(RcVersion(1, 0, 0), legacyWidth = 80, legacyHeight = 20, modern = false),
      listOf(
        RcFloatConstant(VALUE_ID, RcFloatWord.literal(1f)),
        RcTextData(CONFIG_ID, "slot:counter"),
        RcTextData(LABEL_ID, "Count"),
        RcRootLayout(1),
        RcLayoutContent(2),
        RcCustomLayout(
          componentId = 3,
          animationId = 30,
          configId = CONFIG_ID,
          properties =
            listOf(
              RcCustomProperty.float(VALUE, reference),
              RcCustomProperty.floatReturn(RESULT, reference),
              RcCustomProperty.string(LABEL, LABEL_ID),
            ),
        ),
        RcWidthModifier(RcDimensionType.EXACT, RcFloatWord.literal(80f)),
        RcHeightModifier(RcDimensionType.EXACT, RcFloatWord.literal(20f)),
        end,
        end,
        end,
      ),
    )
  }

  private companion object {
    const val COLOR_ID = 45
    const val VALUE = 1
    const val RESULT = 2
    const val LABEL = 3
    const val VALUE_ID = 30
    const val CONFIG_ID = 40
    const val LABEL_ID = 41
  }
}
