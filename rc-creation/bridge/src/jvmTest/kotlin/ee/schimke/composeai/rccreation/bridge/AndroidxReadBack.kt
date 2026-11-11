package ee.schimke.composeai.rccreation.bridge

import androidx.compose.remote.core.CoreDocument
import androidx.compose.remote.core.Operation
import androidx.compose.remote.core.RemoteComposeBuffer
import java.io.ByteArrayInputStream

/**
 * What AndroidX's own reader (`remote-core`, the release this repository depends on) makes of a
 * document.
 */
internal fun androidxOperations(bytes: ByteArray): List<Operation> {
  val document = CoreDocument()
  document.initFromBuffer(RemoteComposeBuffer.fromInputStream(ByteArrayInputStream(bytes)))
  return document.operations
}

/** Every operation of [this] list and, depth first, of the containers in it. */
internal fun List<Operation>.flattened(): List<Operation> = flatMap { operation ->
  listOf(operation) + operation.children().flattened()
}

/** A container's operations: its `mList`, whatever the class declares it as. */
internal fun Operation.children(): List<Operation> {
  var type: Class<*>? = javaClass
  while (type != null) {
    val field = type.declaredFields.firstOrNull { it.name == "mList" }
    if (field != null) {
      field.isAccessible = true
      @Suppress("UNCHECKED_CAST")
      return (field.get(this) as? List<Operation>).orEmpty()
    }
    type = type.superclass
  }
  return emptyList()
}

/** A field AndroidX's operation keeps to itself, read for an assertion. */
internal inline fun <reified T> Operation.field(name: String): T {
  var type: Class<*>? = javaClass
  while (type != null) {
    val field = type.declaredFields.firstOrNull { it.name == name }
    if (field != null) {
      field.isAccessible = true
      return field.get(this) as T
    }
    type = type.superclass
  }
  error("${javaClass.simpleName} has no field $name")
}

internal inline fun <reified T : Operation> List<Operation>.all(): List<T> =
  flattened().filterIsInstance<T>()

internal inline fun <reified T : Operation> List<Operation>.only(): T = all<T>().single()

/** `RcProfiles.PROFILE_ANDROIDX`. */
internal const val ProfileAndroidx = 0x200

/** `RcProfiles.PROFILE_EXPERIMENTAL`. */
internal const val ProfileExperimental = 0x1
