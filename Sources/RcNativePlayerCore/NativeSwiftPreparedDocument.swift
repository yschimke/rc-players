import Foundation

/// A decoded, immutable Remote Compose document ready to back any number of independent
/// ``NativeSwiftDocumentSession`` instances.
///
/// Mirrors CMP's `RcPreparedDocument`: a feed or list playing one template across many cards can
/// decode the wire bytes once (off the main thread) and open lightweight per-card sessions via
/// `ParsedDocument` is only mutated while `NativeSwiftDocumentDecoder.decode` builds it and is
/// strictly read-only thereafter, so sharing one prepared document across threads/sessions is safe.
public struct NativeSwiftPreparedDocument: @unchecked Sendable {
  let parsed: ParsedDocument

  /// Decodes wire bytes into a reusable prepared document.
  public static func decode(
    data: Data, toleratingRootlessData: Bool = false
  ) throws -> NativeSwiftPreparedDocument {
    NativeSwiftPreparedDocument(
      parsed: try NativeSwiftDocumentDecoder.decode(
        data, toleratingRootlessData: toleratingRootlessData))
  }

  /// The document's declared width in wire units.
  public var width: Int { parsed.width }

  /// The document's declared height in wire units.
  public var height: Int { parsed.height }

  /// The document's named variable declarations.
  @_spi(RemoteComposeController)
  public var namedVariableDeclarations: NativeSwiftNamedVariableDeclarations {
    NativeSwiftNamedVariableDeclarations(variables: parsed.namedVariables)
  }
}

/// Thread-safe bounded LRU cache of ``NativeSwiftPreparedDocument`` instances keyed by wire bytes.
///
/// Mirrors CMP's `RcPreparedDocumentCache` (`clear()` and `trimToSize(_:)` included) so hosts
/// rendering scrolling feeds share one decode per template and can shed memory under pressure.
public final class NativeSwiftPreparedDocumentCache: @unchecked Sendable {
  private struct Key: Hashable {
    let data: Data
    let toleratingRootlessData: Bool
  }

  private let lock = NSLock()
  private let capacity: Int
  private var entries: [Key: NativeSwiftPreparedDocument] = [:]
  private var order: [Key] = []

  /// Creates a cache holding at most `capacity` prepared documents (clamped to at least 1).
  public init(capacity: Int = 32) {
    self.capacity = max(capacity, 1)
  }

  /// The number of prepared documents currently cached.
  public var count: Int {
    lock.lock()
    defer { lock.unlock() }
    return entries.count
  }

  /// Returns the cached prepared document for `data`, decoding and inserting it on a miss.
  ///
  /// Decoding runs outside the cache lock so concurrent callers for different templates do not
  /// serialize behind one another.
  public func document(
    for data: Data, toleratingRootlessData: Bool = false
  ) throws -> NativeSwiftPreparedDocument {
    let key = Key(data: data, toleratingRootlessData: toleratingRootlessData)
    lock.lock()
    if let cached = entries[key] {
      touchLocked(key)
      lock.unlock()
      return cached
    }
    lock.unlock()

    let prepared = try NativeSwiftPreparedDocument.decode(
      data: data, toleratingRootlessData: toleratingRootlessData)

    lock.lock()
    if let existing = entries[key] {
      touchLocked(key)
      lock.unlock()
      return existing
    }
    entries[key] = prepared
    order.append(key)
    trimLocked(to: capacity)
    lock.unlock()
    return prepared
  }

  /// Evicts every cached document.
  public func clear() {
    lock.lock()
    entries.removeAll(keepingCapacity: false)
    order.removeAll(keepingCapacity: false)
    lock.unlock()
  }

  /// Evicts least-recently-used documents until at most `maxCount` remain.
  public func trimToSize(_ maxCount: Int) {
    lock.lock()
    trimLocked(to: max(maxCount, 0))
    lock.unlock()
  }

  private func touchLocked(_ key: Key) {
    if let index = order.firstIndex(of: key) {
      order.remove(at: index)
    }
    order.append(key)
  }

  private func trimLocked(to limit: Int) {
    while order.count > limit {
      let oldest = order.removeFirst()
      entries.removeValue(forKey: oldest)
    }
  }
}
