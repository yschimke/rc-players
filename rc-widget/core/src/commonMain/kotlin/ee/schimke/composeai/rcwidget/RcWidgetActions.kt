package ee.schimke.composeai.rcwidget

/**
 * The numeric host actions of one launcher-widget document, by id.
 *
 * Android's `RemoteViews.DrawInstructions` player exposes only numeric host actions to its
 * `PendingIntent` bridge: a tap on an action with id `n` sends the `PendingIntent` the widget set
 * for view id `n` (`setOnClickPendingIntent(n, …)`). A capture registers each tap's payload here
 * and writes its id into the document; the app then attaches one `PendingIntent` per entry, and
 * decodes the payload when one comes back.
 *
 * Ids are derived from the payload, so a template captured twice registers the same ids. Id 0 is
 * never given: AndroidX's authoring API reads it as a named action.
 */
public class RcWidgetActionIds {
  private val actions = linkedMapOf<Int, String>()

  /** Every registered payload, by id, in registration order. */
  public val entries: Map<Int, String>
    get() = actions.toMap()

  /** The id for [payload]: the same id each time it is registered. */
  public fun register(payload: String): Int {
    var id = (payload.hashCode() and Int.MAX_VALUE).takeUnless { it == 0 } ?: 1
    while (true) {
      val previous = actions[id]
      if (previous == null || previous == payload) {
        actions[id] = payload
        return id
      }
      id = if (id == Int.MAX_VALUE) 1 else id + 1
    }
  }
}

/**
 * The metadata a launcher widget's player delivers with a tap, in the `remotecompose_metadata`
 * fill-in extra: the tap's static [actionPayload] and, when the capture asked for it, a subject the
 * action is about, that subject's value as the document showed it when tapped, and the document's
 * clock time. The dynamic parts are Remote Compose strings evaluated by the player at interaction
 * time, so a PIN typed into the widget, or a value it showed, reaches the app.
 */
public data class RcWidgetActionMetadata(
  val actionPayload: String,
  val subject: String? = null,
  val currentValue: String? = null,
  val documentTime: String? = null,
) {
  public companion object {
    /** Separates the payload from the dynamic context. */
    public const val METADATA_MARKER: String = "\n--RC-METADATA-V1--\n"

    /** Separates the subject from its value. */
    public const val VALUE_MARKER: String = "\n--RC-VALUE--\n"

    /** Separates the value from the document time. */
    public const val TIME_MARKER: String = "\n--RC-TIME--\n"

    /**
     * The delivered [metadata], whether it is an envelope (payload, markers, context) or a bare
     * payload, as an older capture wrote it. Null for no metadata.
     */
    public fun decode(metadata: String?): RcWidgetActionMetadata? {
      if (metadata == null) return null
      if (!metadata.contains(METADATA_MARKER)) return RcWidgetActionMetadata(metadata)
      val actionPayload = metadata.substringBefore(METADATA_MARKER)
      val context = metadata.substringAfter(METADATA_MARKER)
      if (!context.contains(VALUE_MARKER) || !context.contains(TIME_MARKER)) {
        return RcWidgetActionMetadata(actionPayload)
      }
      val subject = context.substringBefore(VALUE_MARKER).ifEmpty { null }
      val valueAndTime = context.substringAfter(VALUE_MARKER)
      return RcWidgetActionMetadata(
        actionPayload = actionPayload,
        subject = subject,
        currentValue = valueAndTime.substringBeforeLast(TIME_MARKER),
        documentTime = valueAndTime.substringAfterLast(TIME_MARKER),
      )
    }

    /**
     * The envelope [decode] reads, with constant parts: for tests and for a host that fakes one.
     */
    public fun encode(metadata: RcWidgetActionMetadata): String =
      if (
        metadata.currentValue == null && metadata.documentTime == null && metadata.subject == null
      )
        metadata.actionPayload
      else
        metadata.actionPayload +
          METADATA_MARKER +
          (metadata.subject ?: "") +
          VALUE_MARKER +
          (metadata.currentValue ?: "") +
          TIME_MARKER +
          (metadata.documentTime ?: "")
  }
}
