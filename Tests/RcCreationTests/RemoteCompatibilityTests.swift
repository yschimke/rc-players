import Foundation
@_spi(RemoteComposeController) import RcNativePlayerCore
import Testing

@testable import RcCreation

/// Code written against 4.0 still compiles, with deprecation warnings, and writes what the new
/// spelling writes.
@Suite struct RemoteCompatibilityTests {
  private func texts(_ document: RemoteDocument) throws -> [NativeSwiftTextSnapshot] {
    func walk(_ node: NativeSwiftNodeSnapshot) -> [NativeSwiftTextSnapshot] {
      (node.text.map { [$0] } ?? []) + node.children.flatMap(walk)
    }
    let session = try NativeSwiftDocumentSession.open(data: document.encodedData())
    return walk(try session.snapshot().root)
  }

  /// A `for` loop of modified views: 4.0's way to repeat content.
  @available(*, deprecated)
  @Test func forLoopsOfModifiedViewsStillBuild() throws {
    let labels = ["One", "Two", "Three"]
    let looped = RemoteDocument(width: 300, height: 100) {
      RemoteHStack(spacing: 4) {
        for label in labels { RemoteText(label).font(.caption).padding(2).flexWeight(1) }
      }
    }
    let forEach = RemoteDocument(width: 300, height: 100) {
      RemoteHStack(spacing: 4) {
        RemoteForEach(labels) { label in RemoteText(label).font(.caption).padding(2).flexWeight(1) }
      }
    }
    #expect(try texts(looped).map(\.value) == labels)
    #expect(try looped.encode() == forEach.encode())
  }

  @available(*, deprecated)
  @Test func fourPointZeroFontSpellingsStillBuild() throws {
    let style: RemoteTextStyle = RemoteTextStyle(size: 20, weight: .semibold)
    #expect(style.size == 20 && style.weight == .semibold)
    let old = RemoteDocument(width: 200, height: 100) {
      RemoteVStack {
        RemoteText("Fixed").font(size: 18, weight: .bold)
        RemoteText("Style").font(style)
      }
    }
    let new = RemoteDocument(width: 200, height: 100) {
      RemoteVStack {
        RemoteText("Fixed").font(.system(size: 18, weight: .bold))
        RemoteText("Style").font(.textStyle(size: 20, weight: .semibold))
      }
    }
    #expect(try old.encode() == new.encode())
  }
}
