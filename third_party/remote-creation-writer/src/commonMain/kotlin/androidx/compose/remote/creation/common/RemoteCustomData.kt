package androidx.compose.remote.creation.common

/** `Custom`'s property type, as AndroidX's `remote-core` names it. */
public object Custom {
  /** Resolved property passed to a platform custom component. */
  public class CustomProperty
  private constructor(
    public val id: Short,
    public val dataType: Short,
    public val intValue: Int,
    public val floatValue: Float,
  ) {
    public constructor(id: Short, dataType: Short, value: Int) : this(id, dataType, value, 0f)

    public constructor(id: Short, dataType: Short, value: Float) : this(id, dataType, 0, value)

    public companion object {
      public const val INT_PROP: Short = 0
      public const val FLOAT_PROP: Short = 1
      public const val STRING_PROP: Short = 2
      public const val FLOAT_RETURN: Short = 3
      public const val TEXT_RETURN: Short = 4
      public const val INT_RETURN: Short = 5
      public const val COLOR_RETURN: Short = 6
      public const val COLOR_ID_PROP: Short = 7
      public const val COLOR_PROP: Short = 8
      public const val INT_ID_PROP: Short = 9
    }
  }
}
