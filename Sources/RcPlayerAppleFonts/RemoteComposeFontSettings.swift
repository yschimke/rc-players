import CoreGraphics
import CoreText
import Foundation

/// Applies a text run's font variation axes and OpenType layout features to a resolved face.
///
/// A Remote Compose `CoreText` carries both in one tag/value list; the player splits it (the
/// native core's `NativeSwiftFontSettings`) and hands the two halves here. An axis is applied only
/// where the face declares it, for the reason `RemoteComposeFontVariation` gives for `wght`; a
/// feature the face lacks is simply not shaped, so features are applied as given.
public enum RemoteComposeFontSettings {
  /// The `CTFont` axis identifier for a four-character OpenType tag — its bytes, big-endian — or
  /// nil when `tag` is not four ASCII characters.
  public static func axisIdentifier(_ tag: String) -> UInt32? {
    let bytes = Array(tag.utf8)
    guard bytes.count == 4, bytes.allSatisfy({ $0 >= 0x20 && $0 < 0x7f }) else { return nil }
    return bytes.reduce(UInt32(0)) { $0 << 8 | UInt32($1) }
  }

  /// The identifiers of the variation axes the face declares; empty for a static face.
  public static func declaredAxes(_ descriptor: CTFontDescriptor) -> Set<UInt32> {
    let font = CTFontCreateWithFontDescriptor(descriptor, 0, nil)
    guard let axes = CTFontCopyVariationAxes(font) as? [[CFString: Any]] else { return [] }
    return Set(
      axes.compactMap {
        ($0[kCTFontVariationAxisIdentifierKey as CFString] as? NSNumber)?.uint32Value
      })
  }

  /// The face with `axes` set over any variation it already carries, such as the run's `wght`, or
  /// nil when the face declares none of them. A later axis with the same tag wins.
  public static func descriptor(
    _ descriptor: CTFontDescriptor, applyingAxes axes: [(tag: String, value: CGFloat)]
  ) -> CTFontDescriptor? {
    let declared = declaredAxes(descriptor)
    let applicable = axes.compactMap { axis -> (UInt32, CGFloat)? in
      guard axis.value.isFinite, let identifier = axisIdentifier(axis.tag),
        declared.contains(identifier)
      else { return nil }
      return (identifier, axis.value)
    }
    guard !applicable.isEmpty else { return nil }
    var variation =
      CTFontDescriptorCopyAttribute(descriptor, kCTFontVariationAttribute) as? [NSNumber: NSNumber]
      ?? [:]
    for (identifier, value) in applicable {
      variation[NSNumber(value: identifier)] = NSNumber(value: Double(value))
    }
    return CTFontDescriptorCreateCopyWithAttributes(
      descriptor, [kCTFontVariationAttribute: variation as CFDictionary] as CFDictionary)
  }

  /// The face with `features` added to the OpenType feature settings it already carries, or nil
  /// when there are none to add. 0 turns a feature off, 1 on, and a higher value picks an
  /// alternate.
  public static func descriptor(
    _ descriptor: CTFontDescriptor, applyingFeatures features: [(tag: String, value: Int)]
  ) -> CTFontDescriptor? {
    let settings: [[CFString: Any]] = features.compactMap { feature in
      guard axisIdentifier(feature.tag) != nil else { return nil }
      return [
        kCTFontOpenTypeFeatureTag: feature.tag as CFString,
        kCTFontOpenTypeFeatureValue: NSNumber(value: max(feature.value, 0)),
      ]
    }
    guard !settings.isEmpty else { return nil }
    let existing =
      CTFontDescriptorCopyAttribute(descriptor, kCTFontFeatureSettingsAttribute)
      as? [[CFString: Any]] ?? []
    return CTFontDescriptorCreateCopyWithAttributes(
      descriptor,
      [kCTFontFeatureSettingsAttribute: (existing + settings) as CFArray] as CFDictionary)
  }

  /// The face with both applied: `axes` where it declares them, then `features`. Unchanged when
  /// neither applies.
  public static func descriptor(
    _ descriptor: CTFontDescriptor,
    applyingAxes axes: [(tag: String, value: CGFloat)],
    features: [(tag: String, value: Int)]
  ) -> CTFontDescriptor {
    let varied = self.descriptor(descriptor, applyingAxes: axes) ?? descriptor
    return self.descriptor(varied, applyingFeatures: features) ?? varied
  }
}
