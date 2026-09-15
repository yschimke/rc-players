import CoreGraphics
import CryptoKit
import Foundation

#if canImport(FoundationNetworking)
  import FoundationNetworking
#endif

/// A named font requested by a Remote Compose document.
public struct RemoteComposeDownloadableFontRequest: Hashable, Sendable {
  public let family: String

  public init(family: String) {
    self.family = family
  }
}

/// Validated font bytes and a stable identity suitable for a renderer font cache.
public struct RemoteComposeDownloadedFont: Sendable {
  public let family: String
  public let identity: String
  public let data: Data

  public init(family: String, identity: String, data: Data) {
    self.family = family
    self.identity = identity
    self.data = data
  }
}

/// Host-selected source for document-declared downloadable fonts.
public protocol RemoteComposeDownloadableFontResolving: AnyObject, Sendable {
  func resolve(_ request: RemoteComposeDownloadableFontRequest) async throws
    -> RemoteComposeDownloadedFont
}

public enum RemoteComposeDownloadableFontError: Error, Equatable, LocalizedError, Sendable {
  case invalidFamily
  case invalidResponse
  case responseTooLarge(actual: Int, maximum: Int)
  case noCompatibleFont(family: String)
  case untrustedFontURL
  case familyMismatch(expected: String, actual: String)
  case corruptFont(family: String)

  public var errorDescription: String? {
    switch self {
    case .invalidFamily: return "The downloadable font family name is invalid"
    case .invalidResponse: return "The downloadable font server returned an invalid response"
    case .responseTooLarge(let actual, let maximum):
      return "The downloadable font response is \(actual) bytes; the limit is \(maximum)"
    case .noCompatibleFont(let family):
      return "Google Fonts returned no Apple-compatible font for \(family)"
    case .untrustedFontURL: return "Google Fonts returned a font URL from an untrusted host"
    case .familyMismatch(let expected, let actual):
      return "The font resolver returned \(actual) for the requested family \(expected)"
    case .corruptFont(let family): return "The downloaded font for \(family) is invalid"
    }
  }
}

/// Google Fonts CSS API resolver using URLSession, bounded responses, and an in-memory cache.
///
/// Supplying this resolver is an explicit opt-in to network font loading. The player only sends
/// family names carrying the `google:` source marker in the document.
public actor RemoteComposeGoogleFontsResolver: RemoteComposeDownloadableFontResolving {
  public struct Limits: Equatable, Sendable {
    public var maximumStylesheetBytes: Int
    public var maximumFontBytes: Int
    public var maximumCachedFonts: Int

    public init(
      maximumStylesheetBytes: Int = 256 * 1024,
      maximumFontBytes: Int = 8 * 1024 * 1024,
      maximumCachedFonts: Int = 16
    ) {
      self.maximumStylesheetBytes = maximumStylesheetBytes
      self.maximumFontBytes = maximumFontBytes
      self.maximumCachedFonts = maximumCachedFonts
    }

    public static let `default` = Limits()
  }

  private let session: URLSession
  private let limits: Limits
  private var cache: [String: RemoteComposeDownloadedFont] = [:]
  private var cacheOrder: [String] = []

  public init(session: URLSession = .shared, limits: Limits = .default) {
    self.session = session
    self.limits = limits
  }

  public func resolve(_ request: RemoteComposeDownloadableFontRequest) async throws
    -> RemoteComposeDownloadedFont
  {
    let family = try Self.validatedFamily(request.family)
    let key = family.lowercased()
    if let cached = cache[key] { return cached }
    guard
      limits.maximumStylesheetBytes > 0, limits.maximumFontBytes > 0,
      limits.maximumCachedFonts > 0
    else { throw RemoteComposeDownloadableFontError.invalidResponse }

    var components = URLComponents(string: "https://fonts.googleapis.com/css2")!
    components.queryItems = [URLQueryItem(name: "family", value: family)]
    var stylesheetRequest = URLRequest(url: components.url!)
    // A legacy user agent makes the CSS API return sfnt/TrueType bytes that CoreText can register,
    // rather than browser-specific WOFF2 subsets.
    stylesheetRequest.setValue(
      "Mozilla/5.0 (Linux; Android 4.0.3; Galaxy Nexus Build/IML74K) "
        + "AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Mobile Safari/534.30",
      forHTTPHeaderField: "User-Agent")
    let stylesheetData = try await fetch(
      stylesheetRequest, maximumBytes: limits.maximumStylesheetBytes,
      allowedHost: "fonts.googleapis.com")
    guard let stylesheet = String(data: stylesheetData, encoding: .utf8) else {
      throw RemoteComposeDownloadableFontError.invalidResponse
    }
    guard let fontURL = Self.fontURL(from: stylesheet, relativeTo: components.url!) else {
      throw RemoteComposeDownloadableFontError.noCompatibleFont(family: family)
    }
    guard fontURL.scheme == "https", fontURL.host?.lowercased() == "fonts.gstatic.com" else {
      throw RemoteComposeDownloadableFontError.untrustedFontURL
    }
    let data = try await fetch(
      URLRequest(url: fontURL), maximumBytes: limits.maximumFontBytes,
      allowedHost: "fonts.gstatic.com")
    guard let provider = CGDataProvider(data: data as CFData), CGFont(provider) != nil else {
      throw RemoteComposeDownloadableFontError.corruptFont(family: family)
    }
    let digest = SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    let result = RemoteComposeDownloadedFont(
      family: family, identity: "google:\(key)#sha256:\(digest)", data: data)
    insert(result, for: key)
    return result
  }

  private func fetch(_ request: URLRequest, maximumBytes: Int, allowedHost: String) async throws
    -> Data
  {
    let (data, response) = try await session.data(for: request)
    guard let response = response as? HTTPURLResponse, response.statusCode == 200,
      response.url?.scheme == "https", response.url?.host?.lowercased() == allowedHost
    else {
      throw RemoteComposeDownloadableFontError.invalidResponse
    }
    if response.expectedContentLength > Int64(maximumBytes) {
      throw RemoteComposeDownloadableFontError.responseTooLarge(
        actual: Int(clamping: response.expectedContentLength), maximum: maximumBytes)
    }
    guard data.count <= maximumBytes else {
      throw RemoteComposeDownloadableFontError.responseTooLarge(
        actual: data.count, maximum: maximumBytes)
    }
    return data
  }

  private func insert(_ font: RemoteComposeDownloadedFont, for key: String) {
    if cache[key] == nil { cacheOrder.append(key) }
    cache[key] = font
    while cacheOrder.count > limits.maximumCachedFonts {
      cache.removeValue(forKey: cacheOrder.removeFirst())
    }
  }

  static func validatedFamily(_ value: String) throws -> String {
    let family = value.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !family.isEmpty, family.count <= 100,
      family.unicodeScalars.allSatisfy({
        CharacterSet.alphanumerics.contains($0) || $0 == " " || $0 == "-" || $0 == "."
      })
    else { throw RemoteComposeDownloadableFontError.invalidFamily }
    return family
  }

  static func fontURL(from stylesheet: String, relativeTo baseURL: URL) -> URL? {
    let pattern = #"url\((?:['\"])?([^)'\"]+)(?:['\"])?\)\s*format\((?:['\"])?(?:truetype|opentype)(?:['\"])?\)"#
    guard let expression = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive])
    else { return nil }
    let range = NSRange(stylesheet.startIndex..<stylesheet.endIndex, in: stylesheet)
    for match in expression.matches(in: stylesheet, range: range) {
      guard let valueRange = Range(match.range(at: 1), in: stylesheet) else { continue }
      let value = String(stylesheet[valueRange]).trimmingCharacters(in: .whitespacesAndNewlines)
      if let url = URL(string: value, relativeTo: baseURL)?.absoluteURL { return url }
    }
    let fallback = #"url\((?:['\"])?([^)'\"]+\.(?:ttf|otf))(?:['\"])?\)"#
    guard let expression = try? NSRegularExpression(pattern: fallback, options: [.caseInsensitive])
    else { return nil }
    for match in expression.matches(in: stylesheet, range: range) {
      guard let valueRange = Range(match.range(at: 1), in: stylesheet) else { continue }
      return URL(
        string: String(stylesheet[valueRange]).trimmingCharacters(in: .whitespacesAndNewlines),
        relativeTo: baseURL)?.absoluteURL
    }
    let unlabeled = #"url\((?:['\"])?([^)'\"]+)(?:['\"])?\)\s*;"#
    guard
      let expression = try? NSRegularExpression(pattern: unlabeled, options: [.caseInsensitive])
    else { return nil }
    for match in expression.matches(in: stylesheet, range: range) {
      guard let valueRange = Range(match.range(at: 1), in: stylesheet) else { continue }
      return URL(
        string: String(stylesheet[valueRange]).trimmingCharacters(in: .whitespacesAndNewlines),
        relativeTo: baseURL)?.absoluteURL
    }
    return nil
  }
}
