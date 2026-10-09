# Shared elements named by their `AnimationSpec`

Evidence for the CMP fix that takes a component's shared-element identity from its `AnimationSpec`
modifier, as AndroidX `LayoutComponent.inflate` does.

`RemoteModifier.animationSpec(id, true)` — what the creation library and the Compose UI builder
write for a shared element — leaves the component's own animation id at `-1` and carries the id on
the spec. The player read only the component's id, so no Kotlin-authored shared element was ever
matched: a `StateLayout` switch cross-faded two copies instead of moving one.

`corners-before-after.png` is `RcStateLayoutTransitionTest.anElementNamedOnlyByItsAnimationSpecMorphs`'
document (60×60, drawn ×3), one frame every 32 ms after the tap. Before: the outgoing square stays
in the top-left while the incoming one sits in the bottom-right, and the old one disappears when
the cross-fade ends. After: one square travels between the corners over the spec's 300 ms.

`ui-builder-padded.png` is a UI-builder export whose shared square is placed by `padding` (8 dp in
one state, 120 dp in the other), with the AndroidX View player for reference. Padding is part of a
component in Remote Compose, so it is part of what morphs, in both players. After the fix the CMP
player morphs the padded bounds rather than cross-fading, and neither player moves the square
cleanly. A shared element that should travel is placed by its parent, not by its own padding.
