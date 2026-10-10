import Foundation

#if canImport(FoundationNetworking)
  import FoundationNetworking
#endif

/// Apple's URLSession is the default transport for referenced native-player images.
/// Hosts can supply a session for their own caching, authentication or URLProtocol adapters.
public final class NativeSwiftImageLoader: Sendable {
  public static let shared = NativeSwiftImageLoader()
  private let session: URLSession
  public let maximumBytes: Int

  public init(session: URLSession = .shared, maximumBytes: Int = 8 * 1024 * 1024) {
    self.session = session
    self.maximumBytes = maximumBytes
  }

  public func load(reference: String, encoding: Int) async throws -> Data {
    try Task.checkCancellation()
    guard maximumBytes > 0 else { throw NativeSwiftImageLoadingError.invalidLimit }
    if encoding == NativeSwiftBitmapEncoding.file {
      let url =
        reference.hasPrefix("file:") ? URL(string: reference) : URL(fileURLWithPath: reference)
      guard let url, url.isFileURL else { throw NativeSwiftImageLoadingError.invalidReference }
      let task = Task.detached { [maximumBytes] in
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }
        var result = Data()
        while true {
          try Task.checkCancellation()
          let bytes = handle.readData(ofLength: min(64 * 1024, maximumBytes - result.count + 1))
          if bytes.isEmpty { return result }
          result.append(bytes)
          guard result.count <= maximumBytes else { throw NativeSwiftImageLoadingError.tooLarge }
        }
      }
      let data = try await withTaskCancellationHandler {
        try await task.value
      } onCancel: {
        task.cancel()
      }
      try Task.checkCancellation()
      return data
    }
    guard encoding == NativeSwiftBitmapEncoding.url else {
      throw NativeSwiftImageLoadingError.unsupportedEncoding(encoding)
    }
    guard let url = URL(string: reference), ["https", "http"].contains(url.scheme?.lowercased()),
      url.host != nil
    else { throw NativeSwiftImageLoadingError.invalidReference }
    let pending = NativeImageRequestCancellation()
    let data: Data = try await withTaskCancellationHandler {
      try await withCheckedThrowingContinuation { continuation in
        let task = session.dataTask(with: url) { [maximumBytes] data, response, error in
          if let error {
            continuation.resume(throwing: error)
            return
          }
          guard let response = response as? HTTPURLResponse else {
            continuation.resume(throwing: NativeSwiftImageLoadingError.invalidResponse)
            return
          }
          guard (200..<300).contains(response.statusCode) else {
            continuation.resume(
              throwing: NativeSwiftImageLoadingError.httpStatus(response.statusCode))
            return
          }
          guard let data, data.count <= maximumBytes,
            response.expectedContentLength <= Int64(maximumBytes)
          else {
            continuation.resume(throwing: NativeSwiftImageLoadingError.tooLarge)
            return
          }
          continuation.resume(returning: data)
        }
        pending.start(task)
      }
    } onCancel: {
      pending.cancel()
    }
    try Task.checkCancellation()
    return data
  }
}

public enum NativeSwiftImageLoadingError: Error, Equatable, Sendable {
  case invalidLimit
  case invalidReference
  case invalidResponse
  case unsupportedEncoding(Int)
  case httpStatus(Int)
  case tooLarge
}

/// Cancellation can arrive before URLSession has created its task.
private final class NativeImageRequestCancellation: @unchecked Sendable {
  private let lock = NSLock()
  private var task: URLSessionDataTask?
  private var cancelled = false

  func start(_ task: URLSessionDataTask) {
    lock.lock()
    self.task = task
    let cancelled = cancelled
    lock.unlock()
    if cancelled { task.cancel() }
    task.resume()
  }

  func cancel() {
    lock.lock()
    cancelled = true
    let task = task
    lock.unlock()
    task?.cancel()
  }
}
