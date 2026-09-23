import Foundation

/// The ids AndroidX's `RemoteContext` reserves for values the player supplies, not the document.
///
/// Only the ones this core actually loads are named, matching the upstream `RcSystemVariables`
/// contract. A reference to an id the player does not load resolves to 0 and poisons the arithmetic
/// downstream, so naming one here without loading it would be worse than leaving it out.
///
/// `ANIMATION_DELTA_TIME` (31) and `EPOCH_SECOND` (32) are deliberately absent: a delta needs the
/// previous frame, which this core's stateless snapshot does not keep, and the epoch second is an
/// *integer* variable whose expressions are evaluated at decode time, before a host clock can be
/// supplied.
public enum NativeSwiftSystemVariables {
  /// Device pixels per dp, as the player is playing the document.
  public static let density = 27

  /// The host's default text size in pixels — 14sp at the player's density and font scale.
  public static let fontSize = 33

  /// The `sp` behind `fontSize`, before density and font scale. Every player has to agree on it:
  /// a capture divides its text size by this id to recover the `sp` it authored.
  public static let defaultFontSizeSp: Float = 14

  /// Wall-clock seconds within the current hour, including the fractional second.
  public static let continuousSeconds = 1

  /// Wall-clock seconds within the current hour, whole seconds.
  public static let timeInSeconds = 2

  /// Wall-clock minutes within the current day.
  public static let timeInMinutes = 3

  /// Wall-clock hours within the current day.
  public static let timeInHours = 4

  /// Calendar month, 1...12.
  public static let calendarMonth = 9

  /// Whole seconds since the Unix epoch. The reference loads it as an integer; float expressions
  /// read it as a float, with a float's precision.
  public static let epochSecond = 32

  /// The local zone's offset from UTC in seconds.
  public static let offsetToUTC = 10

  /// ISO day of the week, Monday 1 ... Sunday 7.
  public static let weekDay = 11

  /// Day of the month, 1...31.
  public static let dayOfMonth = 12

  /// The last pointer coordinate the host delivered to the document.
  public static let touchX = 13
  public static let touchY = 14

  /// Seconds since the document's first frame, the animation clock a host advances.
  public static let animationTime = 30

  /// Day of the year, 1...366.
  public static let dayOfYear = 34

  /// Calendar year.
  public static let year = 35
}

/// The host's wall clock, which is what a document's calendar and time-of-day variables read.
///
/// `snapshot(timeSeconds:)` is deliberately pure: it advances only by the elapsed time the host
/// hands it, so a test or a corpus capture can hold it still. A document that reads `YEAR` or
/// `TIME_IN_SEC` needs an absolute instant, which the elapsed clock cannot provide, so a host that
/// wants those fields supplies one here — and a host that does not leaves them unset rather than
/// getting the 1970 epoch silently.
public struct NativeSwiftWallClock: Sendable, Equatable {
  public let epochMillis: Int64
  /// The local zone's offset from UTC at that instant. The reference reads the system zone; a
  /// corpus capture freezes both the instant and the zone it was rendered in.
  public let offsetSeconds: Int
  /// The zone itself, when the host has one. It gives an instant other than this one its own
  /// offset — a stored timestamp on the far side of a daylight-saving change is read in that
  /// instant's offset, as the reference reads it in the system zone. Without it, `offsetSeconds`
  /// applies to every instant.
  public let timeZone: TimeZone?

  public init(epochMillis: Int64, offsetSeconds: Int = 0, timeZone: TimeZone? = nil) {
    self.epochMillis = epochMillis
    self.offsetSeconds = offsetSeconds
    self.timeZone = timeZone
  }

  /// The zone's offset at another instant, falling back to this clock's own offset.
  func offsetSeconds(atEpochMillis millis: Int64) -> Int {
    guard let timeZone else { return offsetSeconds }
    return timeZone.secondsFromGMT(for: Date(timeIntervalSince1970: Double(millis) / 1000))
  }

  /// The calendar fields AndroidX's `TimeVariables` publishes, in the local zone.
  struct Fields {
    let year: Int
    let month: Int
    let dayOfMonth: Int
    let dayOfYear: Int
    let hour: Int
    let minute: Int
    let second: Int
    let isoDayOfWeek: Int
    let millisOfSecond: Int

    /// Seconds within the current hour, whole seconds.
    var secondOfHour: Int { minute * 60 + second }
  }

  var fields: Fields {
    // Floor division, not truncation: `-1 ms` is the last millisecond of 1969, and truncating would
    // read it as 1970-01-01T00:00:00.999.
    let localSeconds = Self.floorDiv(epochMillis, 1000) + Int64(offsetSeconds)
    let millis = Int(((epochMillis % 1000) + 1000) % 1000)
    let days = Self.floorDiv(localSeconds, 86400)
    let secondOfDay = Int(localSeconds - days * 86400)
    let civil = Self.civilFromDays(days)
    let dayOfYear = Int(days - Self.daysFromCivil(civil.year, 1, 1)) + 1
    // 1970-01-01 was a Thursday, and ISO numbers Monday as 1. The remainder is normalized because
    // Swift keeps the dividend's sign, which would put a pre-epoch date outside 1...7.
    let isoDayOfWeek = Int(((days + 3) % 7 + 7) % 7) + 1
    return Fields(
      year: Int(civil.year),
      month: Int(civil.month),
      dayOfMonth: Int(civil.day),
      dayOfYear: dayOfYear,
      hour: secondOfDay / 3600,
      minute: (secondOfDay % 3600) / 60,
      second: secondOfDay % 60,
      isoDayOfWeek: isoDayOfWeek,
      millisOfSecond: millis)
  }

  static func floorDiv(_ value: Int64, _ divisor: Int64) -> Int64 {
    let quotient = value / divisor
    return value % divisor < 0 ? quotient - 1 : quotient
  }

  /// Howard Hinnant's civil-from-days, the inverse of `daysFromCivil`.
  private static func civilFromDays(_ days: Int64) -> (year: Int64, month: Int64, day: Int64) {
    let shifted = days + 719468
    let era = (shifted >= 0 ? shifted : shifted - 146096) / 146097
    let dayOfEra = shifted - era * 146097
    let yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146096) / 365
    let year = yearOfEra + era * 400
    let dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    let monthPrime = (5 * dayOfYear + 2) / 153
    let day = dayOfYear - (153 * monthPrime + 2) / 5 + 1
    let month = monthPrime + (monthPrime < 10 ? 3 : -9)
    return (year + (month <= 2 ? 1 : 0), month, day)
  }

  private static func daysFromCivil(_ year: Int64, _ month: Int64, _ day: Int64) -> Int64 {
    let adjustedYear = year - (month <= 2 ? 1 : 0)
    let era = (adjustedYear >= 0 ? adjustedYear : adjustedYear - 399) / 400
    let yearOfEra = adjustedYear - era * 400
    let dayOfYear =
      (153 * (month + (month > 2 ? -3 : 9)) + 2) / 5 + day - 1
    let dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
    return era * 146097 + dayOfEra - 719468
  }
}
