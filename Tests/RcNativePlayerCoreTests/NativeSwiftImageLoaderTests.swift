import Foundation
import Testing

@testable import RcNativePlayerCore

@Suite struct NativeSwiftImageLoaderTests {
  @Test func fileReferencesAndSizeLimit() async throws {
    let file = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    let expected = Data([1, 2, 3, 4])
    try expected.write(to: file)
    defer { try? FileManager.default.removeItem(at: file) }
    let loader = NativeSwiftImageLoader(maximumBytes: 4)
    #expect(
      try await loader.load(reference: file.path, encoding: NativeSwiftBitmapEncoding.file)
        == expected)
    #expect(
      try await loader.load(
        reference: file.absoluteString, encoding: NativeSwiftBitmapEncoding.file) == expected)
    await #expect(throws: NativeSwiftImageLoadingError.tooLarge) {
      try await NativeSwiftImageLoader(maximumBytes: 3).load(
        reference: file.path, encoding: NativeSwiftBitmapEncoding.file)
    }
  }

  @Test func invalidAndUnsupportedReferences() async {
    await #expect(throws: NativeSwiftImageLoadingError.invalidReference) {
      try await NativeSwiftImageLoader.shared.load(
        reference: "file:///tmp/image.png", encoding: NativeSwiftBitmapEncoding.url)
    }
    await #expect(
      throws: NativeSwiftImageLoadingError.unsupportedEncoding(NativeSwiftBitmapEncoding.empty)
    ) {
      try await NativeSwiftImageLoader.shared.load(
        reference: "", encoding: NativeSwiftBitmapEncoding.empty)
    }
  }

  @Test func cancelledRequestsDoNotReadFiles() async throws {
    let task = Task {
      while !Task.isCancelled { await Task.yield() }
      return try await NativeSwiftImageLoader.shared.load(
        reference: "/does-not-exist", encoding: NativeSwiftBitmapEncoding.file)
    }
    task.cancel()
    await #expect(throws: CancellationError.self) { try await task.value }
  }
}

#if canImport(FoundationNetworking)
  import FoundationNetworking
#endif

private final class ImageTestURLProtocol: URLProtocol {
  override class func canInit(with request: URLRequest) -> Bool { true }
  override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
  override func startLoading() {
    guard let url = request.url else { return }
    let status = url.path == "/missing" ? 404 : 200
    let response = HTTPURLResponse(
      url: url, statusCode: status, httpVersion: nil,
      headerFields: ["Content-Length": "4"])!
    client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
    client?.urlProtocol(self, didLoad: Data([1, 2, 3, 4]))
    client?.urlProtocolDidFinishLoading(self)
  }
  override func stopLoading() {}
}

extension NativeSwiftImageLoaderTests {
  @Test func hostSessionOverrideAndHTTPFailures() async throws {
    let configuration = URLSessionConfiguration.ephemeral
    configuration.protocolClasses = [ImageTestURLProtocol.self]
    let session = URLSession(configuration: configuration)
    defer { session.invalidateAndCancel() }
    let loader = NativeSwiftImageLoader(session: session, maximumBytes: 4)
    #expect(
      try await loader.load(
        reference: "https://images.example/image", encoding: NativeSwiftBitmapEncoding.url)
        == Data([1, 2, 3, 4]))
    await #expect(throws: NativeSwiftImageLoadingError.httpStatus(404)) {
      try await loader.load(
        reference: "https://images.example/missing", encoding: NativeSwiftBitmapEncoding.url)
    }
    await #expect(throws: NativeSwiftImageLoadingError.tooLarge) {
      try await NativeSwiftImageLoader(session: session, maximumBytes: 3).load(
        reference: "https://images.example/image", encoding: NativeSwiftBitmapEncoding.url)
    }
  }
}
