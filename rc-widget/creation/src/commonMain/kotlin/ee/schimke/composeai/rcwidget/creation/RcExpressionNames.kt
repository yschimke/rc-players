package ee.schimke.composeai.rcwidget.creation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.currentCompositeKeyHashCode
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * A name for the named expression this call site registers in the document being captured: [prefix]
 * and a suffix unique in the document. Two call sites in one document never share it, and every
 * capture of the same content names it the same, so a document's bytes are reproducible: a
 * template's hash changes only when the design does, which is what lets a server serve templates by
 * content address and a client cache them by it.
 *
 * Inside [ProvideRcExpressionNames] (the root of every single-template document) the suffix is the
 * call site's place in the order the document is composed. Outside it, the suffix is the call
 * site's place in the composition, which no sibling shares.
 */
@Composable
public fun rememberRcExpressionName(prefix: String): String {
  val names = LocalRcExpressionNames.current
  val key = currentCompositeKeyHashCode
  return remember(prefix, key) {
    // The composite key's type differs by platform (an Int on some, a Long on others), so common
    // code names it through its hash.
    prefix + (names?.next()?.toString() ?: key.hashCode().toUInt().toString(16))
  }
}

/** The expressions named so far in one capture, in composition order. */
public class RcExpressionNames {
  private var count = 0

  internal fun next(): Int = count++
}

private val LocalRcExpressionNames = staticCompositionLocalOf<RcExpressionNames?> { null }

/**
 * [content] with its expressions named by composition order ([rememberRcExpressionName]), so the
 * names don't depend on how deep the content is drawn: content drawn on its own or wrapped by a
 * design names them alike. The outermost call counts for the whole document; a nested one keeps
 * counting, so two parts of one document never share a name.
 */
@Composable
public fun ProvideRcExpressionNames(content: @Composable () -> Unit) {
  if (LocalRcExpressionNames.current != null) {
    content()
    return
  }
  CompositionLocalProvider(
    LocalRcExpressionNames provides remember { RcExpressionNames() },
    content,
  )
}
