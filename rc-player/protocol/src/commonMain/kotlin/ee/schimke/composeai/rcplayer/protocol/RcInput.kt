package ee.schimke.composeai.rcplayer.protocol

/** Versioned custom-component contract; uses LAYOUT_CUSTOM, STRING_PROP and TEXT_RETURN. */
public object RcInput {
  public const val TEXT: String = "rc:input/text:v1"
  public const val CHOICE: String = "rc:input/choice:v1"
  public const val TOGGLE: String = "rc:input/toggle:v1"
  public const val NUMBER: String = "rc:input/number:v1"
  public const val RANGE: String = "rc:input/range:v1"
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
  /** Optional finite decimal bounds/step, STRING_PROP; absent/empty means unconstrained. */
  public const val MIN: Int = 8
  public const val MAX: Int = 9
  public const val STEP: Int = 10
  /** INT_PROP: zero disables this field; absent or one enables it subject to host policy. */
  public const val ENABLED: Int = 11
  /** Optional STRING_PROP action button caption; absent/empty retains the v1 default Send. */
  public const val SUBMIT_LABEL: Int = 12
  /** INT_PROP: DEFAULT (0, also absent/unknown) or NONE (1), preserving accessible labels. */
  public const val CHROME: Int = 13
  public val configs: Set<String> = setOf(TEXT, CHOICE, TOGGLE, NUMBER, RANGE)
}
