package ee.schimke.composeai.rcplayer.protocol

import kotlin.test.*

class RcInputConformanceTest {
  @Test
  fun sharedContractValidationVectors() {
    val fixture = requireNotNull(javaClass.getResourceAsStream("/input-controls-v1.tsv"))
    fixture
      .bufferedReader()
      .readLines()
      .filter { !it.startsWith("#") && it.isNotEmpty() }
      .forEach { line ->
        val f = line.split('|')
        assertEquals(
          f[6].toBoolean(),
          rcInputValueIsValid(
            "rc:input/${f[0]}:v1",
            f[1].replace("~", ""),
            f[2].split(';'),
            f[3].takeIf { it.isNotEmpty() }?.toDouble(),
            f[4].takeIf { it.isNotEmpty() }?.toDouble(),
            f[5].takeIf { it.isNotEmpty() }?.toDouble(),
          ),
          line,
        )
      }
  }

  @Test
  fun typedFactoriesAndEmbeddedFragmentsUseTheSameContract() {
    val fields =
      listOf(
        RcInputs.text("message", "Message"),
        RcInputs.choice("room", "Room", listOf("Kitchen")),
        RcInputs.toggle("urgent", "Urgent"),
        RcInputs.number("temperature", "Temperature", 21.5, 0.0, 30.0, 0.5),
        RcInputs.range("brightness", "Brightness", 50.0),
      )
    val document = rcInputDocument(fields)
    assertEquals(document, RcDocumentCodec.decode(RcDocumentCodec.encode(document)))
    assertEquals(
      RcInput.configs,
      document.operations
        .filterIsInstance<RcCustomLayout>()
        .map { control ->
          document.operations
            .filterIsInstance<RcTextData>()
            .single { it.id == control.configId }
            .text
        }
        .toSet(),
    )
    val fragment = rcInputComponent(fields[2], 800, 900)
    val component = fragment.filterIsInstance<RcCustomLayout>().single()
    assertEquals(800, component.componentId)
    assertEquals(
      component.properties.single { it.type == RcInput.VALUE }.intValue,
      component.properties.single { it.type == RcInput.VALUE_RETURN }.intValue,
    )
    val disabled =
      rcInputComponent(fields[2].copy(enabled = false, submitLabel = "Apply"), 800, 900)
        .filterIsInstance<RcCustomLayout>()
        .single()
    assertEquals(0, disabled.properties.single { it.type == RcInput.ENABLED }.intValue)
    assertEquals(909, disabled.properties.single { it.type == RcInput.SUBMIT_LABEL }.intValue)
    assertFailsWith<IllegalArgumentException> {
      RcInputs.choice("x", "X", listOf("Kitchen", "Kitchen"))
    }
    assertFailsWith<IllegalArgumentException> { RcInputs.number("x", "X", Double.NaN) }
    assertFailsWith<IllegalArgumentException> { RcInputs.range("x", "X", 0.0, 0.0, 1.0, 0.3) }
  }
}
