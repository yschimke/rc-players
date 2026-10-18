package ee.schimke.composeai.rcplayer.protocol

/** One field in a portable input form; choice options are literal values, not free-text hints. */
public data class RcInputField(
  val key: String,
  val label: String,
  val value: String = "",
  val options: List<String> = emptyList(),
  val multiline: Boolean = false,
  val submit: String = "",
)

/** Small document producer shared by interactive hosts. Widget hosts author their own link UI. */
public fun rcInputDocument(fields: List<RcInputField>, width: Int = 360): RcDocument {
  require(fields.isNotEmpty() && fields.map { it.key }.distinct().size == fields.size)
  require(
    fields.all {
      it.key.isNotBlank() && it.options.all { option -> option.isNotEmpty() && '\n' !in option }
    }
  )
  val end = RcNoArg(RcOpcodes.CONTAINER_END)
  val operations = mutableListOf<RcOperation>()
  val configs = fields.map { if (it.options.isEmpty()) RcInput.TEXT else RcInput.CHOICE }
  fields.forEachIndexed { index, field ->
    val base = 100 + index * 10
    listOf(
        configs[index],
        field.value,
        field.key,
        field.label,
        field.options.joinToString("\n"),
        field.submit,
      )
      .forEachIndexed { offset, text -> operations += RcTextData(base + offset, text) }
  }
  operations += RcRootLayout(1)
  operations += RcColumnLayout(3, 0, 1, 4, RcFloatWord.literal(4f))
  operations += RcWidthModifier(RcDimensionType.FILL, RcFloatWord.literal(1f))
  operations += RcHeightModifier(RcDimensionType.WRAP, RcFloatWord.literal(0f))
  operations += RcLayoutContent(4)
  fields.forEachIndexed { index, field ->
    val base = 100 + index * 10
    operations +=
      RcCustomLayout(
        10 + index,
        0,
        base,
        listOf(
          RcCustomProperty.string(RcInput.VALUE, base + 1),
          RcCustomProperty.textReturn(RcInput.VALUE_RETURN, base + 1),
          RcCustomProperty.string(RcInput.KEY, base + 2),
          RcCustomProperty.string(RcInput.LABEL, base + 3),
          RcCustomProperty.int(RcInput.MULTILINE, if (field.multiline) 1 else 0),
          RcCustomProperty.string(RcInput.OPTIONS, base + 4),
          RcCustomProperty.string(RcInput.SUBMIT, base + 5),
        ),
      )
    operations += RcWidthModifier(RcDimensionType.FILL, RcFloatWord.literal(1f))
    operations +=
      RcHeightModifier(
        RcDimensionType.EXACT,
        RcFloatWord.literal(
          if (field.multiline) 136f else if (field.submit.isNotEmpty()) 112f else 80f
        ),
      )
    operations += end
  }
  repeat(3) { operations += end }
  return RcDocument(
    RcHeader(
      RcVersion(1, 2, 0),
      properties =
        listOf(
          RcHeaderProperty(RcHeader.DOC_WIDTH, RcHeaderValue.IntValue(width)),
          RcHeaderProperty(
            RcHeader.DOC_HEIGHT,
            RcHeaderValue.IntValue(
              fields.sumOf { if (it.multiline) 140 else if (it.submit.isNotEmpty()) 116 else 84 }
            ),
          ),
          RcHeaderProperty(
            14 /* Header.DOC_PROFILES */,
            RcHeaderValue.IntValue(RcWireProfiles.ANDROIDX_EXPERIMENTAL),
          ),
        ),
    ),
    operations,
  )
}
