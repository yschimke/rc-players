package ee.schimke.composeai.rccreation.bridge

import androidx.compose.remote.creation.compose.modifier.MaxExplicitComponentId
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.componentId
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Stable component ids for things a host names: an editor's authored nodes, say.
 *
 * A document's own component ids are positional, so an insertion renumbers everything after it and
 * nothing outside the document can say which component is which. A host that needs to (to map a
 * player's per-component geometry back to its own nodes, or to carry state across edits) gives each
 * of its keys an id from here and applies it with [component]. The same key keeps its id for the
 * life of this registry.
 *
 * Ids count down from [first], which is at most `MaxExplicitComponentId`: below both ranges a
 * document allocates for itself, so an explicit id never meets an allocated one.
 *
 * Deliberately not snapshot state. Several remote compositions can share a registry (a widget's
 * content and its background are two), each composing in its own snapshot on its own thread; with
 * snapshot maps both would see the same size and hand out the same id. An assignment is visible to
 * every thread at once, and survives a composition that is discarded.
 */
@ExperimentalRcCreationApi
@OptIn(ExperimentalAtomicApi::class)
public class RcComponentIds<K : Any>(private val first: Int = MaxExplicitComponentId) {
  init {
    require(first <= MaxExplicitComponentId) {
      "Explicit component ids are at most $MaxExplicitComponentId; first was $first"
    }
  }

  private class Assigned<K>(val ids: Map<K, Int>, val keys: Map<Int, K>)

  private val assigned = AtomicReference(Assigned<K>(emptyMap(), emptyMap()))

  /** The id [key] has, assigning the next free one the first time it is asked for. */
  public fun idFor(key: K): Int {
    while (true) {
      val current = assigned.load()
      current.ids[key]?.let {
        return it
      }
      val id = first - current.ids.size
      if (
        assigned.compareAndSet(
          current,
          Assigned(current.ids + (key to id), current.keys + (id to key)),
        )
      )
        return id
    }
  }

  /** The key [id] was assigned to, or null for an id this registry did not assign. */
  public fun keyFor(id: Int): K? = assigned.load().keys[id]

  /** [modifier] with the component id [key] has. */
  public fun component(modifier: RemoteModifier, key: K): RemoteModifier =
    modifier.componentId(idFor(key))
}
