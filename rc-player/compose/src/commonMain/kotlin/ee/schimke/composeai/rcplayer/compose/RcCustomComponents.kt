package ee.schimke.composeai.rcplayer.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ee.schimke.composeai.rcplayer.protocol.RcCustomLayout
import ee.schimke.composeai.rcplayer.protocol.RcCustomProperty
import ee.schimke.composeai.rcplayer.runtime.RcPlayerState

/** Host Compose content used to render one Remote Compose custom component. */
public typealias RcCustomContent =
  @Composable (component: RcCustomComponent, modifier: Modifier) -> Unit

/**
 * Host renderers for Remote Compose custom components, keyed by their document-authored config
 * name.
 *
 * The registry is platform-neutral. Its content runs in the same Compose tree as the player, so a
 * renderer may use ordinary Compose UI or recursively host another [RcComposePlayer].
 */
public class RcCustomComponentRegistry(renderers: Map<String, RcCustomContent>) {
  private val renderers: Map<String, RcCustomContent> = renderers.toMap()

  public constructor(vararg renderers: Pair<String, RcCustomContent>) : this(renderers.toMap()) {
    require(this.renderers.size == renderers.size) { "Duplicate custom component config name" }
  }

  public val names: Set<String>
    get() = renderers.keys

  internal fun content(name: String): RcCustomContent? = renderers[name]

  public companion object {
    public val Empty: RcCustomComponentRegistry = RcCustomComponentRegistry(emptyMap())
  }
}

/** A custom component instance and its properties resolved against the live player state. */
public class RcCustomComponent
internal constructor(
  public val config: String,
  public val componentId: Int,
  public val properties: List<RcCustomProperty>,
  private val state: RcPlayerState,
  private val invalidate: () -> Unit,
) {
  /** Returns the property with author-defined [type], if present. */
  public fun property(type: Int): RcCustomProperty? = properties.firstOrNull { it.type == type }

  public fun hasProperty(type: Int): Boolean = property(type) != null

  /** Resolves a float literal or NaN-boxed variable reference. */
  public fun float(type: Int, default: Float = 0f): Float {
    val property =
      property(type)?.takeIf { it.dataType == RcCustomProperty.FLOAT_PROP } ?: return default
    return state.resolve(property.floatValue)
  }

  /** Reads an integer property. */
  public fun integer(type: Int, default: Int = 0): Int =
    property(type)?.takeIf { it.dataType == RcCustomProperty.INT_PROP }?.intValue ?: default

  /** Resolves a string property through the document text table. */
  public fun text(type: Int, default: String = ""): String {
    val property =
      property(type)?.takeIf { it.dataType == RcCustomProperty.STRING_PROP } ?: return default
    return state.text(property.intValue) ?: default
  }

  /** Writes [value] to a declared float return channel. */
  public fun returnFloat(type: Int, value: Float): Boolean {
    val target =
      property(type)
        ?.takeIf { it.dataType == RcCustomProperty.FLOAT_RETURN }
        ?.floatValue
        ?.referencedId ?: return false
    state.setFloat(target, value)
    invalidate()
    return true
  }

  /** Writes [value] to a declared text return channel. */
  public fun returnText(type: Int, value: String): Boolean {
    val target =
      property(type)?.takeIf { it.dataType == RcCustomProperty.TEXT_RETURN }?.intValue
        ?: return false
    state.setText(target, value)
    invalidate()
    return true
  }
}

internal fun RcCustomLayout.component(
  config: String,
  state: RcPlayerState,
  invalidate: () -> Unit,
): RcCustomComponent = RcCustomComponent(config, componentId, properties, state, invalidate)
