package ee.schimke.composeai.rcplayer.protocol

/** Versioned custom-component contract; uses LAYOUT_CUSTOM, STRING_PROP and TEXT_RETURN. */
public object RcInput {
  public const val TEXT: String = "rc:input/text:v1"
  public const val CHOICE: String = "rc:input/choice:v1"
  public const val VALUE: Int = 1
  public const val VALUE_RETURN: Int = 2
  /** Stable, card-scoped host draft key, not a transient component/text id. */
  public const val KEY: Int = 3
  public const val LABEL: Int = 4
  /** INT_PROP: zero for single line; one for multiline (Enter inserts a newline). */
  public const val MULTILINE: Int = 5
  /** STRING_PROP: newline-separated, non-empty unique choices; values cannot contain newlines. */
  public const val OPTIONS: Int = 6
  /** Opaque host action descriptor. Never executed by the player itself. */
  public const val SUBMIT: Int = 7
  public val configs: Set<String> = setOf(TEXT, CHOICE)
}
