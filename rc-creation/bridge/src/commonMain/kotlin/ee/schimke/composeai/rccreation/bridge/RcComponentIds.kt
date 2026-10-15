package ee.schimke.composeai.rccreation.bridge

import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.componentId
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * PROTOTYPE: stable component ids for things a host names: an editor's authored nodes, say.
 *
 * A document's own component ids are positional, so an insertion renumbers everything after it and
 * nothing outside the document can say which component is which. A host that needs to (to map a
 * player's per-component geometry back to its own nodes, or to carry state across edits) gives each
 * of its keys an id from here and applies it with [component]. The same key keeps its id for the
 * life of this registry.
 *
 * Ids count down from [first], well clear of the `-1_000_000`-and-below range a document allocates
 * for itself.
 *
 * Deliberately not snapshot state. Several remote compositions can share a registry (a widget's
 * content and its background are two), each composing in its own snapshot on its own thread; with
 * snapshot maps both would see the same size and hand out the same id. An assignment is visible to
 * every thread at once, and survives a composition that is discarded.
 */
@OptIn(ExperimentalAtomicApi::class)
public class RcComponentIds(private val first: Int = -2_000_000) {
  private class Assigned(val ids: Map<Any, Int>, val keys: Map<Int, Any>)

  private val assigned = AtomicReference(Assigned(emptyMap(), emptyMap()))

  /** The id [key] has, assigning the next free one the first time it is asked for. */
  public fun idFor(key: Any): Int {
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
  public fun keyFor(id: Int): Any? = assigned.load().keys[id]

  /** [modifier] with the component id [key] has. */
  public fun component(modifier: RemoteModifier, key: Any): RemoteModifier =
    modifier.componentId(idFor(key))
}
