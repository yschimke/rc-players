package ee.schimke.composeai.rcplayer.protocol

/** One field in a portable input form; choice options are literal values, not free-text hints. */
public data class RcInputField(
  val key: String,
  val label: String,
  val value: String = "",
  val options: List<String> = emptyList(),
  val multiline: Boolean = false,
  val submit: String = "",
  val kind: RcInputKind = if (options.isEmpty()) RcInputKind.TEXT else RcInputKind.CHOICE,
  val minimum: Double? = null,
  val maximum: Double? = null,
  val step: Double? = null,
  val enabled: Boolean = true,
  val submitLabel: String = "Send",
  val chrome: RcInputChrome = RcInputChrome.DEFAULT,
)

/** Small document producer shared by interactive hosts. Widget hosts author their own link UI. */
public fun rcInputDocument(fields: List<RcInputField>, width: Int = 360): RcDocument {
  require(fields.isNotEmpty() && fields.map { it.key }.distinct().size == fields.size)
  require(
    fields.all {
      it.key.isNotBlank() && it.options.all { option -> option.isNotEmpty() && '\n' !in option }
    }
  )
  fields.forEach { it.requireValid() }
  require(width > 0)
  fun height(field: RcInputField): Int =
    if (field.multiline) 136
    else if (field.submit.isNotEmpty() || field.kind == RcInputKind.RANGE) 112 else 80
  val end = RcNoArg(RcOpcodes.CONTAINER_END)
  val operations = mutableListOf<RcOperation>()
  operations += RcRootLayout(1)
  operations += RcColumnLayout(3, 0, 1, 4, RcFloatWord.literal(4f))
  operations += RcWidthModifier(RcDimensionType.FILL, RcFloatWord.literal(1f))
  operations += RcHeightModifier(RcDimensionType.WRAP, RcFloatWord.literal(0f))
  operations += RcLayoutContent(4)
  fields.forEachIndexed { index, field ->
    operations += rcInputComponent(field, 10 + index, 100 + index * 16)
    operations += RcWidthModifier(RcDimensionType.FILL, RcFloatWord.literal(1f))
    operations +=
      RcHeightModifier(
        RcDimensionType.EXACT,
        RcFloatWord.literal(height(field).toFloat()),
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
            RcHeaderValue.IntValue(fields.sumOf { height(it) + 4 }),
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
