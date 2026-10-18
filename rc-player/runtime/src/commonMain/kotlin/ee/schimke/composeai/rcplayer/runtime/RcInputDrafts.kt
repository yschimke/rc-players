package ee.schimke.composeai.rcplayer.runtime

/**
 * Host-owned drafts. Retain one instance per card across document replacement and live updates.
 * Only explicit [reset] discards an edit; incoming snapshots never overwrite an existing draft.
 * This object is confined to the host UI thread and contains no persistence or action transport.
 */
public class RcInputDrafts {
  private val defaults: MutableMap<String, String> = mutableMapOf()
  private val values: MutableMap<String, String> = mutableMapOf()

  public fun value(key: String, documentValue: String): String {
    defaults[key] = documentValue
    return values[key] ?: documentValue
  }

  public fun update(key: String, value: String) {
    require(key.isNotBlank()) { "An input needs a stable host key" }
    values[key] = value
  }

  public fun snapshot(): Map<String, String> = defaults + values

  public fun reset(key: String) {
    values.remove(key)
  }
}
