import RcWireOperations

/// An enum whose cases a document can switch between: ``RemoteSwitch`` shows one view per
/// case, and a `RemoteVariable` of it holds the current case.
///
/// ```swift
/// enum Tab: RemoteStateValue { case day, week, month }
/// let tab = RemoteVariable("tab", default: Tab.week)
/// ```
///
/// On the wire a case is its position in `allCases`, which is what the host writes by name.
public protocol RemoteStateValue: CaseIterable, Hashable, Sendable, RemoteVariableValue
where AllCases: RandomAccessCollection {}

extension RemoteStateValue {
  /// This case's position in `allCases`: its value on the wire.
  public var ordinal: Int {
    Self.allCases.distance(from: Self.allCases.startIndex, to: Self.allCases.firstIndex(of: self)!)
  }
}

extension RemoteInt {
  /// The variable's live case, as its position in `allCases`.
  public init<State: RemoteStateValue>(_ variable: RemoteVariable<State>) {
    self.init(
      node: .variable(
        name: variable.name,
        declaration: .state(
          type: String(reflecting: State.self), ordinal: variable.defaultValue.ordinal)))
  }
}

extension RemoteVariable where Value: RemoteStateValue {
  /// Whether the variable holds `state`: `tab == .week`.
  public static func == (lhs: Self, rhs: Value) -> RemoteBool {
    RemoteInt(lhs) == RemoteInt(rhs.ordinal)
  }

  public static func != (lhs: Self, rhs: Value) -> RemoteBool {
    RemoteInt(lhs) != RemoteInt(rhs.ordinal)
  }
}

extension RemoteAction {
  /// Writes `state` to `variable`: what a tab or a segment does when tapped.
  public static func set<State: RemoteStateValue>(
    _ variable: RemoteVariable<State>, to state: State
  ) -> RemoteAction {
    RemoteAction(kind: .setIntTo(RemoteInt(variable).node, RemoteInt(state.ordinal)))
  }
}

/// One view per state, of which the player shows the one the current state selects: a Swift
/// `switch` or `if` over a value the player holds (AndroidX's `RemoteStateLayout`).
///
/// ```swift
/// RemoteSwitch(tab) { tab in
///   switch tab {
///   case .day: DayView()
///   case .week: WeekView()
///   case .month: MonthView()
///   }
/// }
///
/// RemoteSwitch(isSignedIn) { isSignedIn in
///   if isSignedIn { AccountView() } else { SignInButton() }
/// }
/// ```
///
/// The closure runs once for every state while the document is written, so the compiler checks
/// the `switch` covers every case, and each state's view is in the document from the start. As
/// the document plays, the host or an action changes the state and the player switches to that
/// state's view (CMP animates the change). Each state's content is one child, wrapped in a box,
/// so a state may have several views.
///
/// On the wire this is a `StateLayout` whose index is the state's integer: a `Bool`'s 0 or 1, an
/// enum case's position, or an `Int` itself.
public struct RemoteSwitch<Content: RemoteView>: RemoteView, RemotePrimitive {
  public typealias Body = Never

  let index: RemoteInt
  let alignment: RemoteAlignment
  let states: [Content]

  /// One view for `false` and one for `true`: `if`/`else` over a condition the player evaluates.
  public init(
    _ condition: RemoteBool, alignment: RemoteAlignment = .topLeading,
    @RemoteViewBuilder content: (Bool) -> Content
  ) {
    index = condition.int
    self.alignment = alignment
    states = [content(false), content(true)]
  }

  public init(
    _ variable: RemoteVariable<Bool>, alignment: RemoteAlignment = .topLeading,
    @RemoteViewBuilder content: (Bool) -> Content
  ) {
    self.init(RemoteBool(variable), alignment: alignment, content: content)
  }

  /// One view for each case of `State`, in `allCases` order.
  public init<State: RemoteStateValue>(
    _ variable: RemoteVariable<State>, alignment: RemoteAlignment = .topLeading,
    @RemoteViewBuilder content: (State) -> Content
  ) {
    index = RemoteInt(variable)
    self.alignment = alignment
    states = State.allCases.map(content)
  }

  /// One view for each integer in `range`: the integer, less the range's start, is the index.
  /// An index outside the states shows the first state on both the CMP and native players; use
  /// `clamped(to:)` to choose another.
  public init(
    _ selection: RemoteInt, in range: Range<Int>, alignment: RemoteAlignment = .topLeading,
    @RemoteViewBuilder content: (Int) -> Content
  ) {
    index = range.lowerBound == 0 ? selection : selection - RemoteInt(range.lowerBound)
    self.alignment = alignment
    states = range.map(content)
  }

  package func write(to writer: RemoteWriter) throws(RemoteEncodingError) {
    let componentID = writer.newComponentID()
    let layout = StateLayout(
      componentId: componentID, animationId: IdEncoding.noId,
      horizontalPositioning: alignment.horizontal.wire,
      verticalPositioning: alignment.vertical.wire, indexId: try writer.intID(index.node))
    let alignment = alignment
    try writer.writeLayout(
      .stateLayout(layout), kind: .zStack,
      content: RemoteForEach(states) { state in RemoteZStack(alignment: alignment) { state } })
  }
}

/// A view shown while `condition` holds and another while it does not, as `if`/`else` over a
/// value the player evaluates: shorthand for a two-state ``RemoteSwitch``.
///
/// ```swift
/// RemoteIf(RemoteBool(isOn)) {
///   RemoteText("On")
/// } else: {
///   RemoteText("Off")
/// }
/// ```
public struct RemoteIf<Then: RemoteView, Else: RemoteView>: RemoteView {
  let condition: RemoteBool
  let then: Then
  let otherwise: Else

  public init(
    _ condition: RemoteBool, @RemoteViewBuilder then: () -> Then,
    @RemoteViewBuilder else otherwise: () -> Else
  ) {
    self.condition = condition
    self.then = then()
    self.otherwise = otherwise()
  }

  public var body: some RemoteView {
    let view = self
    return RemoteSwitch(condition) { holds in
      if holds { view.then } else { view.otherwise }
    }
  }
}

extension RemoteIf where Else == EmptyRemoteView {
  /// A view shown only while `condition` holds.
  public init(_ condition: RemoteBool, @RemoteViewBuilder then: () -> Then) {
    self.init(condition, then: then, else: { EmptyRemoteView() })
  }
}
